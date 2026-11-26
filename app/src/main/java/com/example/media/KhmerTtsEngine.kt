package com.example.media

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.data.model.EmotionType
import com.example.data.model.KhmerVoicePreset
import com.example.data.model.TtsEngineMode
import com.example.network.GeminiApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt

data class TtsSynthesisResult(
    val wavFile: File,
    val durationMs: Long,
    val appliedSpeedRatio: Float,
    val engineUsed: String
)

class KhmerTtsEngine(
    private val context: Context,
    private val geminiApiClient: GeminiApiClient
) {
    companion object {
        private const val TAG = "KhmerTtsEngine"
    }

    suspend fun synthesizeSegment(
        text: String,
        voicePreset: KhmerVoicePreset,
        emotion: EmotionType,
        pitchOffset: Float,
        speedMultiplier: Float,
        targetDurationMs: Long,
        autoAdjustSpeed: Boolean,
        engineMode: TtsEngineMode,
        ttsModelId: String,
        maxRetries: Int,
        outputWavFile: File
    ): TtsSynthesisResult = withContext(Dispatchers.IO) {
        outputWavFile.parentFile?.mkdirs()
        var engineUsed = "Gemini AI Voice"
        var success = false

        if (engineMode == TtsEngineMode.GEMINI_AI_WITH_FALLBACK) {
            val geminiAudio = geminiApiClient.synthesizeKhmerSpeech(
                khmerText = text,
                voiceName = voicePreset.geminiVoiceName,
                emotion = emotion,
                ttsModelId = ttsModelId,
                maxRetries = maxRetries
            )
            if (geminiAudio != null && geminiAudio.size > 100) {
                outputWavFile.writeBytes(geminiAudio)
                success = true
            }
        }

        if (!success) {
            engineUsed = "Android Khmer TTS"
            val effectivePitch = (voicePreset.basePitch * pitchOffset * emotionPitchFactor(emotion))
                .coerceIn(0.6f, 1.6f)
            val effectiveRate = (voicePreset.baseRate * speedMultiplier * emotionRateFactor(emotion))
                .coerceIn(0.7f, 1.5f)
            val ok = synthesizeWithAndroidTts(
                text = text,
                pitch = effectivePitch,
                rate = effectiveRate,
                outputFile = outputWavFile
            )
            if (!ok || !outputWavFile.exists() || outputWavFile.length() <= 44L) {
                throw IllegalStateException("Khmer TTS synthesis failed for segment: ${text.take(32)}")
            }
        }

        // Normalize sample rate to 24kHz mono and optionally time-stretch via SOLA to fit dialogue slot
        val srcRate = AudioExtractor.readWavSampleRate(outputWavFile)
        var samples = AudioExtractor.readWavSamples(outputWavFile)
        if (srcRate != AudioExtractor.TARGET_SAMPLE_RATE) {
            samples = AudioExtractor.resampleSamples(samples, srcRate, AudioExtractor.TARGET_SAMPLE_RATE)
        }

        val rawDurationMs = (samples.size * 1000L) / AudioExtractor.TARGET_SAMPLE_RATE
        var appliedRatio = 1.0f

        if (autoAdjustSpeed && targetDurationMs >= 600L && rawDurationMs > 0L) {
            val desiredRatio = rawDurationMs.toFloat() / targetDurationMs.toFloat()
            if (desiredRatio > 1.08f || desiredRatio < 0.85f) {
                appliedRatio = desiredRatio.coerceIn(0.84f, 1.28f)
                samples = timeScalePitchPreserving(samples, AudioExtractor.TARGET_SAMPLE_RATE, appliedRatio)
            }
        }

        AudioExtractor.writeWavFile(outputWavFile, samples, AudioExtractor.TARGET_SAMPLE_RATE, 1)
        val finalDurationMs = (samples.size * 1000L) / AudioExtractor.TARGET_SAMPLE_RATE

        TtsSynthesisResult(
            wavFile = outputWavFile,
            durationMs = finalDurationMs,
            appliedSpeedRatio = appliedRatio,
            engineUsed = engineUsed
        )
    }

    private suspend fun synthesizeWithAndroidTts(
        text: String,
        pitch: Float,
        rate: Float,
        outputFile: File
    ): Boolean {
        val result = withTimeoutOrNull(25_000L) {
            suspendCancellableCoroutine { cont ->
                var ttsRef: TextToSpeech? = null
                val utteranceId = UUID.randomUUID().toString()
                val tts = TextToSpeech(context.applicationContext) { status ->
                    val engine = ttsRef ?: return@TextToSpeech
                    if (status != TextToSpeech.SUCCESS) {
                        runCatching { engine.shutdown() }
                        if (cont.isActive) cont.resume(false)
                        return@TextToSpeech
                    }

                    val khmerLocale = Locale("km", "KH")
                    val langResult = engine.setLanguage(khmerLocale)
                    if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.w(TAG, "km-KH voice data unavailable, using default engine locale")
                        engine.setLanguage(Locale.getDefault())
                    }
                    engine.setPitch(pitch)
                    engine.setSpeechRate(rate)
                    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(id: String?) {}
                        override fun onDone(id: String?) {
                            if (id == utteranceId) {
                                runCatching { engine.shutdown() }
                                if (cont.isActive) cont.resume(true)
                            }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(id: String?) {
                            runCatching { engine.shutdown() }
                            if (cont.isActive) cont.resume(false)
                        }

                        override fun onError(id: String?, errorCode: Int) {
                            runCatching { engine.shutdown() }
                            if (cont.isActive) cont.resume(false)
                        }
                    })

                    val params = Bundle()
                    val synthStatus = engine.synthesizeToFile(text, params, outputFile, utteranceId)
                    if (synthStatus != TextToSpeech.SUCCESS) {
                        runCatching { engine.shutdown() }
                        if (cont.isActive) cont.resume(false)
                    }
                }
                ttsRef = tts
                cont.invokeOnCancellation {
                    runCatching {
                        ttsRef?.stop()
                        ttsRef?.shutdown()
                    }
                }
            }
        }
        return result == true
    }

    /**
     * Pitch-preserving SOLA (Synchronized Overlap-Add) time scaling so accelerated/slowed
     * Khmer speech stays natural without chipmunk pitch artifacts.
     */
    private fun timeScalePitchPreserving(
        input: ShortArray,
        sampleRate: Int,
        speedRatio: Float
    ): ShortArray {
        if (input.size < 2048 || speedRatio in 0.97f..1.03f) return input
        val windowSize = (sampleRate * 0.045).roundToInt().coerceAtLeast(512)
        val overlap = windowSize / 3
        val synStep = windowSize - overlap
        val anaStep = (synStep * speedRatio).roundToInt().coerceAtLeast(64)

        val estFrames = (input.size - windowSize) / anaStep + 2
        val out = FloatArray((estFrames * synStep + windowSize).coerceAtLeast(windowSize))
        val norm = FloatArray(out.size)

        val hann = FloatArray(windowSize) { i ->
            (0.5 * (1.0 - cos(2.0 * PI * i / (windowSize - 1)))).toFloat()
        }

        var inPos = 0
        var outPos = 0
        var maxOutWritten = 0
        while (inPos + windowSize <= input.size && outPos + windowSize <= out.size) {
            for (i in 0 until windowSize) {
                val w = hann[i]
                out[outPos + i] += input[inPos + i] * w
                norm[outPos + i] += w
            }
            maxOutWritten = outPos + windowSize
            inPos += anaStep
            outPos += synStep
        }

        if (maxOutWritten == 0) return input
        return ShortArray(maxOutWritten) { i ->
            val n = norm[i]
            val v = if (n > 1e-4f) out[i] / n else out[i]
            v.roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    private fun emotionPitchFactor(emotion: EmotionType): Float = when (emotion) {
        EmotionType.HAPPY -> 1.07f
        EmotionType.EXCITED -> 1.12f
        EmotionType.SURPRISE -> 1.10f
        EmotionType.SAD -> 0.91f
        EmotionType.ANGRY -> 0.95f
        EmotionType.FEAR -> 1.08f
        EmotionType.NEUTRAL -> 1.0f
    }

    private fun emotionRateFactor(emotion: EmotionType): Float = when (emotion) {
        EmotionType.HAPPY -> 1.04f
        EmotionType.EXCITED -> 1.10f
        EmotionType.SURPRISE -> 1.06f
        EmotionType.SAD -> 0.90f
        EmotionType.ANGRY -> 1.08f
        EmotionType.FEAR -> 1.10f
        EmotionType.NEUTRAL -> 1.0f
    }
}
