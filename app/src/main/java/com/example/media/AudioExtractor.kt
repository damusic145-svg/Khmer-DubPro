package com.example.media

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

data class ExtractedAudioResult(
    val wavFile: File,
    val sampleRate: Int,
    val channels: Int,
    val durationMs: Long,
    val waveformBars: List<Float>
)

data class AudioChunk(
    val index: Int,
    val file: File,
    val startMs: Long,
    val durationMs: Long
)

object AudioExtractor {
    private const val TAG = "AudioExtractor"
    const val TARGET_SAMPLE_RATE = 24000

    suspend fun extractAudioToWav(
        videoFile: File,
        outputWavFile: File,
        onProgress: (Int) -> Unit = {}
    ): ExtractedAudioResult = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(videoFile.absolutePath)
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }

            if (audioTrackIndex == -1 || audioFormat == null) {
                // Video has no audio track — generate silent 24kHz mono WAV matching video duration
                val silentSamples = ShortArray(TARGET_SAMPLE_RATE * 2)
                writeWavFile(outputWavFile, silentSamples, TARGET_SAMPLE_RATE, 1)
                return@withContext ExtractedAudioResult(
                    wavFile = outputWavFile,
                    sampleRate = TARGET_SAMPLE_RATE,
                    channels = 1,
                    durationMs = 2000L,
                    waveformBars = List(80) { 0.05f }
                )
            }

            extractor.selectTrack(audioTrackIndex)
            val mime = audioFormat.getString(MediaFormat.KEY_MIME)!!
            val sourceDurationUs = if (audioFormat.containsKey(MediaFormat.KEY_DURATION)) {
                audioFormat.getLong(MediaFormat.KEY_DURATION)
            } else 1L

            var decodedSampleRate = audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var decodedChannels = audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(audioFormat, null, null, 0)
            codec.start()

            val rawPcmTemp = File(outputWavFile.parentFile, "raw_pcm.tmp")
            var totalBytesWritten = 0L
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false

            FileOutputStream(rawPcmTemp).use { pcmOut ->
                while (!outputDone) {
                    if (!inputDone) {
                        val inIdx = codec.dequeueInputBuffer(10_000L)
                        if (inIdx >= 0) {
                            val inBuf = codec.getInputBuffer(inIdx)!!
                            val sampleSize = extractor.readSampleData(inBuf, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(
                                    inIdx,
                                    0,
                                    0,
                                    0L,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                inputDone = true
                            } else {
                                val pts = extractor.sampleTime
                                codec.queueInputBuffer(inIdx, 0, sampleSize, pts, 0)
                                extractor.advance()
                                if (sourceDurationUs > 0) {
                                    onProgress(((pts * 100L) / sourceDurationUs).toInt().coerceIn(0, 95))
                                }
                            }
                        }
                    }

                    val outIdx = codec.dequeueOutputBuffer(info, 10_000L)
                    when {
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val newFmt = codec.outputFormat
                            if (newFmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                                decodedSampleRate = newFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            }
                            if (newFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                                decodedChannels = newFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            }
                        }
                        outIdx >= 0 -> {
                            val outBuf = codec.getOutputBuffer(outIdx)
                            if (outBuf != null && info.size > 0) {
                                val chunk = ByteArray(info.size)
                                outBuf.position(info.offset)
                                outBuf.limit(info.offset + info.size)
                                outBuf.get(chunk)
                                pcmOut.write(chunk)
                                totalBytesWritten += chunk.size
                            }
                            codec.releaseOutputBuffer(outIdx, false)
                            if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                outputDone = true
                            }
                        }
                    }
                }
            }

            // Downmix & resample to mono 24kHz 16-bit WAV for fast Gemini upload + crisp DSP mixing
            val mono24k = resampleToMono24k(rawPcmTemp, decodedSampleRate, decodedChannels)
            rawPcmTemp.delete()

            writeWavFile(outputWavFile, mono24k, TARGET_SAMPLE_RATE, 1)
            val durationMs = (mono24k.size * 1000L) / TARGET_SAMPLE_RATE
            val waveform = computeWaveformBars(mono24k, 80)
            onProgress(100)

            ExtractedAudioResult(
                wavFile = outputWavFile,
                sampleRate = TARGET_SAMPLE_RATE,
                channels = 1,
                durationMs = durationMs,
                waveformBars = waveform
            )
        } catch (e: Exception) {
            Log.e(TAG, "Audio extraction failed", e)
            throw e
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    suspend fun splitWavIntoChunks(
        wavFile: File,
        chunkDir: File,
        chunkDurationSec: Int
    ): List<AudioChunk> = withContext(Dispatchers.IO) {
        chunkDir.mkdirs()
        val samples = readWavSamples(wavFile)
        val totalSamples = samples.size
        val chunkSamples = (chunkDurationSec.coerceIn(30, 600) * TARGET_SAMPLE_RATE)
        if (totalSamples <= chunkSamples) {
            return@withContext listOf(
                AudioChunk(
                    index = 0,
                    file = wavFile,
                    startMs = 0L,
                    durationMs = (totalSamples * 1000L) / TARGET_SAMPLE_RATE
                )
            )
        }

        val chunks = mutableListOf<AudioChunk>()
        var offset = 0
        var idx = 0
        while (offset < totalSamples) {
            val end = minOf(offset + chunkSamples, totalSamples)
            val slice = samples.copyOfRange(offset, end)
            val chunkFile = File(chunkDir, "chunk_$idx.wav")
            writeWavFile(chunkFile, slice, TARGET_SAMPLE_RATE, 1)
            chunks.add(
                AudioChunk(
                    index = idx,
                    file = chunkFile,
                    startMs = (offset * 1000L) / TARGET_SAMPLE_RATE,
                    durationMs = (slice.size * 1000L) / TARGET_SAMPLE_RATE
                )
            )
            offset = end
            idx++
        }
        chunks
    }

    private fun resampleToMono24k(rawPcm: File, srcRate: Int, srcChannels: Int): ShortArray {
        val bytes = rawPcm.readBytes()
        val totalFrames = bytes.size / (2 * srcChannels.coerceAtLeast(1))
        if (totalFrames <= 0) return ShortArray(0)

        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val monoSrc = ShortArray(totalFrames)
        for (i in 0 until totalFrames) {
            var sum = 0
            for (ch in 0 until srcChannels.coerceAtLeast(1)) {
                sum += bb.short.toInt()
            }
            monoSrc[i] = (sum / srcChannels.coerceAtLeast(1)).toShort()
        }

        if (srcRate == TARGET_SAMPLE_RATE) return monoSrc

        val ratio = srcRate.toDouble() / TARGET_SAMPLE_RATE.toDouble()
        val outLength = ((totalFrames / ratio).toInt()).coerceAtLeast(1)
        val out = ShortArray(outLength)
        for (i in 0 until outLength) {
            val srcPos = i * ratio
            val idx0 = srcPos.toInt().coerceIn(0, totalFrames - 1)
            val idx1 = (idx0 + 1).coerceIn(0, totalFrames - 1)
            val frac = srcPos - idx0
            val sample = monoSrc[idx0] * (1.0 - frac) + monoSrc[idx1] * frac
            out[i] = sample.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return out
    }

    fun computeWaveformBars(samples: ShortArray, barCount: Int): List<Float> {
        if (samples.isEmpty()) return List(barCount) { 0.08f }
        val step = maxOf(1, samples.size / barCount)
        val bars = (0 until barCount).map { b ->
            val start = b * step
            val end = minOf(start + step, samples.size)
            var sumSq = 0.0
            var count = 0
            var i = start
            while (i < end) {
                val s = samples[i].toDouble() / 32768.0
                sumSq += s * s
                count++
                i += maxOf(1, step / 64)
            }
            if (count == 0) 0.05f else sqrt(sumSq / count).toFloat()
        }
        val maxVal = bars.maxOrNull()?.takeIf { it > 0.001f } ?: 1f
        return bars.map { (it / maxVal).coerceIn(0.06f, 1.0f) }
    }

    fun readWavSamples(wavFile: File): ShortArray {
        if (!wavFile.exists() || wavFile.length() <= 44L) return ShortArray(0)
        RandomAccessFile(wavFile, "r").use { raf ->
            val all = ByteArray(wavFile.length().toInt())
            raf.readFully(all)
            // Find "data" chunk header safely
            var dataOffset = 44
            var pos = 12
            while (pos + 8 < all.size) {
                val id = String(all, pos, 4, Charsets.US_ASCII)
                val size = ByteBuffer.wrap(all, pos + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
                if (id == "data") {
                    dataOffset = pos + 8
                    break
                }
                pos += 8 + maxOf(0, size)
            }
            val pcmLen = (all.size - dataOffset).coerceAtLeast(0)
            val sampleCount = pcmLen / 2
            val bb = ByteBuffer.wrap(all, dataOffset, sampleCount * 2).order(ByteOrder.LITTLE_ENDIAN)
            return ShortArray(sampleCount) { bb.short }
        }
    }

    fun readWavSampleRate(wavFile: File): Int {
        if (!wavFile.exists() || wavFile.length() < 28L) return TARGET_SAMPLE_RATE
        val header = ByteArray(28)
        wavFile.inputStream().use { it.read(header) }
        return ByteBuffer.wrap(header, 24, 4).order(ByteOrder.LITTLE_ENDIAN).int
            .takeIf { it in 8000..96000 } ?: TARGET_SAMPLE_RATE
    }

    fun writeWavFile(file: File, samples: ShortArray, sampleRate: Int, channels: Int) {
        val dataSize = samples.size * 2
        val totalSize = 36 + dataSize
        val byteRate = sampleRate * channels * 2
        FileOutputStream(file).use { out ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray(Charsets.US_ASCII))
            header.putInt(totalSize)
            header.put("WAVE".toByteArray(Charsets.US_ASCII))
            header.put("fmt ".toByteArray(Charsets.US_ASCII))
            header.putInt(16)
            header.putShort(1.toShort())
            header.putShort(channels.toShort())
            header.putInt(sampleRate)
            header.putInt(byteRate)
            header.putShort((channels * 2).toShort())
            header.putShort(16.toShort())
            header.put("data".toByteArray(Charsets.US_ASCII))
            header.putInt(dataSize)
            out.write(header.array())

            val pcmBytes = ByteArray(dataSize)
            ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(samples)
            out.write(pcmBytes)
        }
    }

    fun resampleSamples(samples: ShortArray, srcRate: Int, dstRate: Int): ShortArray {
        if (srcRate == dstRate || samples.isEmpty()) return samples
        val ratio = srcRate.toDouble() / dstRate.toDouble()
        val outLen = (samples.size / ratio).toInt().coerceAtLeast(1)
        val out = ShortArray(outLen)
        for (i in 0 until outLen) {
            val pos = i * ratio
            val i0 = pos.toInt().coerceIn(0, samples.size - 1)
            val i1 = (i0 + 1).coerceIn(0, samples.size - 1)
            val frac = pos - i0
            out[i] = (samples[i0] * (1.0 - frac) + samples[i1] * frac)
                .toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return out
    }

    fun measureWavDurationMs(wavFile: File): Long {
        if (!wavFile.exists()) return 0L
        val rate = readWavSampleRate(wavFile)
        val samples = readWavSamples(wavFile)
        return (samples.size * 1000L) / rate.coerceAtLeast(8000)
    }
}
