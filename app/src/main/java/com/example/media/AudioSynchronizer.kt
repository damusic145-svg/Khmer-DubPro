package com.example.media

import com.example.data.local.DialogueSegmentEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.tanh

object AudioSynchronizer {

    /**
     * Mixes the original background audio (ducked dynamically during dialogue windows so
     * music and SFX remain intact) with all character-specific Khmer TTS WAV clips.
     * Supports overlapping multi-speaker lines with soft-clipping headroom protection.
     */
    suspend fun mixSynchronizedTrack(
        originalAudioWav: File,
        segments: List<DialogueSegmentEntity>,
        duckingDb: Int,
        originalBaseGain: Float,
        dubbedGain: Float,
        outputMixedWav: File
    ): File = withContext(Dispatchers.IO) {
        outputMixedWav.parentFile?.mkdirs()
        val rate = AudioExtractor.TARGET_SAMPLE_RATE
        val bgSamples = AudioExtractor.readWavSamples(originalAudioWav)

        var maxSampleNeeded = bgSamples.size
        segments.forEach { seg ->
            val path = seg.ttsAudioPath
            if (path != null && File(path).exists()) {
                val startSample = (((seg.startMs + seg.syncOffsetMs).coerceAtLeast(0L)) * rate / 1000L).toInt()
                val clipLen = AudioExtractor.readWavSamples(File(path)).size
                maxSampleNeeded = maxOf(maxSampleNeeded, startSample + clipLen)
            }
        }

        if (maxSampleNeeded <= 0) {
            AudioExtractor.writeWavFile(outputMixedWav, ShortArray(rate), rate, 1)
            return@withContext outputMixedWav
        }

        // Ducking envelope buffer: 1.0 outside dialogue, attenuated smoothly during speech
        val duckFactor = 10.0.pow(duckingDb.coerceIn(-40, 0) / 20.0).toFloat()
        val envelope = FloatArray(maxSampleNeeded) { 1.0f }
        val rampSamples = (rate * 0.080f).toInt().coerceAtLeast(1) // 80ms smooth fade

        for (seg in segments) {
            val startIdx = (((seg.startMs + seg.syncOffsetMs).coerceAtLeast(0L)) * rate / 1000L)
                .toInt().coerceIn(0, maxSampleNeeded - 1)
            val endIdx = ((seg.endMs + seg.syncOffsetMs).coerceAtLeast(seg.startMs + 200L) * rate / 1000L)
                .toInt().coerceIn(startIdx, maxSampleNeeded - 1)

            for (i in startIdx..endIdx) {
                envelope[i] = minOf(envelope[i], duckFactor)
            }
            // Attack ramp before speech
            val attackStart = (startIdx - rampSamples).coerceAtLeast(0)
            for (i in attackStart until startIdx) {
                val t = (i - attackStart).toFloat() / rampSamples.toFloat()
                val valRamp = 1.0f - (1.0f - duckFactor) * t
                envelope[i] = minOf(envelope[i], valRamp)
            }
            // Release ramp after speech
            val releaseEnd = (endIdx + rampSamples).coerceAtMost(maxSampleNeeded - 1)
            for (i in endIdx..releaseEnd) {
                val t = (i - endIdx).toFloat() / rampSamples.toFloat()
                val valRamp = duckFactor + (1.0f - duckFactor) * t
                envelope[i] = minOf(envelope[i], valRamp)
            }
        }

        val mixBuffer = FloatArray(maxSampleNeeded)
        for (i in bgSamples.indices) {
            mixBuffer[i] = (bgSamples[i] / 32768f) * originalBaseGain * envelope[i]
        }

        // Layer all synthesized Khmer speech segments (overlapping speakers naturally mix!)
        for (seg in segments) {
            val wavPath = seg.ttsAudioPath ?: continue
            val clipFile = File(wavPath)
            if (!clipFile.exists()) continue

            val clipRate = AudioExtractor.readWavSampleRate(clipFile)
            var clipSamples = AudioExtractor.readWavSamples(clipFile)
            if (clipRate != rate) {
                clipSamples = AudioExtractor.resampleSamples(clipSamples, clipRate, rate)
            }
            val startIdx = (((seg.startMs + seg.syncOffsetMs).coerceAtLeast(0L)) * rate / 1000L).toInt()
            for (j in clipSamples.indices) {
                val targetIdx = startIdx + j
                if (targetIdx in mixBuffer.indices) {
                    mixBuffer[targetIdx] += (clipSamples[j] / 32768f) * dubbedGain
                }
            }
        }

        // Soft limiter using tanh to prevent harsh clipping when multiple voices overlap
        val outSamples = ShortArray(maxSampleNeeded) { i ->
            val v = mixBuffer[i]
            val limited = if (kotlin.math.abs(v) > 0.90f) tanh(v.toDouble()).toFloat() else v
            (limited * 32767f).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        AudioExtractor.writeWavFile(outputMixedWav, outSamples, rate, 1)
        outputMixedWav
    }
}
