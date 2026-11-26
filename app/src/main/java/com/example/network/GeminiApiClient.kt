package com.example.network

import android.util.Base64
import android.util.Log
import com.example.data.model.AgeGroup
import com.example.data.model.EmotionType
import com.example.data.model.KeyStatus
import com.example.data.model.SpeakerGender
import com.example.data.repository.DubbingRepository
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.min

data class AnalyzedCharacterDto(
    val speakerTag: String,
    val displayName: String,
    val gender: SpeakerGender,
    val ageGroup: AgeGroup,
    val voiceStyle: String,
    val dominantEmotion: EmotionType
)

data class AnalyzedSegmentDto(
    val startMs: Long,
    val endMs: Long,
    val speakerTag: String,
    val originalLanguage: String,
    val originalText: String,
    val emotion: EmotionType,
    val speakingPace: Float
)

data class ChunkAnalysisResult(
    val detectedLanguages: List<String>,
    val storySummary: String,
    val characters: List<AnalyzedCharacterDto>,
    val segments: List<AnalyzedSegmentDto>
)

data class TranslatedSegmentDto(
    val segmentIndex: Int,
    val khmerText: String,
    val emotion: EmotionType,
    val recommendedPace: Float
)

class GeminiApiException(message: String, val httpCode: Int? = null) : Exception(message)

class GeminiApiClient(
    private val repository: DubbingRepository
) {
    companion object {
        private const val TAG = "GeminiApiClient"
        private const val BASE_URL = "https://generativelanguage.googleapis.com"
        private const val INLINE_MAX_BYTES = 8 * 1024 * 1024L
        private const val AI_TIMEOUT_MS = 60_000L
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val defaultFirebaseFlashModel by lazy {
        Firebase.ai.generativeModel("gemini-3.8-flash")
    }

    suspend fun validateApiKey(rawKey: String, modelId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val cleanModel = modelId.trim().removePrefix("models/")
            val payload = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().put("text", "Reply with OK"))
                        })
                    })
                })
                put("generationConfig", JSONObject().put("maxOutputTokens", 8))
            }
            val request = Request.Builder()
                .url("$BASE_URL/v1beta/models/$cleanModel:generateContent")
                .addHeader("x-goog-api-key", rawKey.trim())
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    Result.success("Verified with $cleanModel")
                } else {
                    val errMsg = parseApiError(bodyStr, response.code)
                    Result.failure(GeminiApiException(errMsg, response.code))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun analyzeAudioChunk(
        audioFile: File,
        chunkIndex: Int,
        chunkOffsetMs: Long,
        chunkDurationMs: Long,
        priorStorySummary: String,
        existingSubtitleHints: String,
        modelId: String,
        maxRetries: Int
    ): ChunkAnalysisResult = withContext(Dispatchers.IO) {
        val cleanModel = modelId.trim().removePrefix("models/").ifBlank { "gemini-3.8-flash" }
        val promptText = buildString {
            append("You are an expert multilingual cinema ASR, speaker diarization, and voice casting analyst. ")
            append("Analyze this audio track (chunk #${chunkIndex + 1}, duration ${chunkDurationMs}ms, timeline offset ${chunkOffsetMs}ms). ")
            if (priorStorySummary.isNotBlank()) {
                append("Prior storyline context from earlier scenes: \"$priorStorySummary\". ")
            }
            if (existingSubtitleHints.isNotBlank()) {
                append("Embedded subtitle track hints from the container: $existingSubtitleHints. ")
            }
            append("Tasks: (1) Auto-detect all spoken languages. ")
            append("(2) Transcribe every spoken utterance accurately with relative timestamps startMs and endMs inside [0, $chunkDurationMs]. ")
            append("(3) Perform speaker diarization: assign consistent speakerTag IDs like SPEAKER_1, SPEAKER_2, etc., separating overlapping or multi-character dialogue. ")
            append("(4) Profile each speaker's gender (MALE, FEMALE, NON_BINARY), ageGroup (CHILD, YOUNG_ADULT, ADULT, ELDERLY), speaking style, and dominant emotion (NEUTRAL, HAPPY, SAD, ANGRY, EXCITED, FEAR, SURPRISE). ")
            append("(5) Write a concise updated storySummary capturing plot, character relationships, and tone.")
        }

        val candidates = repository.getDecryptedCandidateKeys()
        if (candidates.isEmpty()) {
            return@withContext analyzeAudioWithFirebaseAi(
                audioFile = audioFile,
                promptText = promptText,
                chunkOffsetMs = chunkOffsetMs,
                chunkDurationMs = chunkDurationMs,
                modelId = cleanModel
            )
        }

        try {
            val useInline = audioFile.length() <= INLINE_MAX_BYTES
            executeWithFailover(candidates = candidates, maxRetries = maxRetries) { apiKey ->
                val audioPart = if (useInline) {
                    val base64 = Base64.encodeToString(audioFile.readBytes(), Base64.NO_WRAP)
                    JSONObject().put(
                        "inlineData",
                        JSONObject().apply {
                            put("mimeType", "audio/wav")
                            put("data", base64)
                        }
                    )
                } else {
                    val fileUri = uploadAudioFile(apiKey, audioFile, "audio/wav")
                    JSONObject().put(
                        "fileData",
                        JSONObject().apply {
                            put("mimeType", "audio/wav")
                            put("fileUri", fileUri)
                        }
                    )
                }

                val requestBody = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(audioPart)
                                put(JSONObject().put("text", promptText))
                            })
                        })
                    })
                    put("generationConfig", JSONObject().apply {
                        put("temperature", 0.2)
                        put("responseMimeType", "application/json")
                        put("responseSchema", buildRestAnalysisSchema())
                    })
                }

                val (responseText, tokens) = postGenerateContent(cleanModel, apiKey, requestBody)
                val parsed = parseChunkAnalysis(responseText, chunkOffsetMs, chunkDurationMs)
                parsed to tokens
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Custom key pool failed for audio analysis, falling back to Firebase AI Logic", e)
            analyzeAudioWithFirebaseAi(
                audioFile = audioFile,
                promptText = promptText,
                chunkOffsetMs = chunkOffsetMs,
                chunkDurationMs = chunkDurationMs,
                modelId = cleanModel
            )
        }
    }

    private suspend fun analyzeAudioWithFirebaseAi(
        audioFile: File,
        promptText: String,
        chunkOffsetMs: Long,
        chunkDurationMs: Long,
        modelId: String
    ): ChunkAnalysisResult {
        val firebaseSchema = Schema.obj(
            properties = mapOf(
                "detectedLanguages" to Schema.array(items = Schema.string()),
                "storySummary" to Schema.string(),
                "characters" to Schema.array(
                    items = Schema.obj(
                        properties = mapOf(
                            "speakerTag" to Schema.string(),
                            "displayName" to Schema.string(),
                            "gender" to Schema.enumeration(listOf("MALE", "FEMALE", "NON_BINARY")),
                            "ageGroup" to Schema.enumeration(listOf("CHILD", "YOUNG_ADULT", "ADULT", "ELDERLY")),
                            "voiceStyle" to Schema.string(),
                            "dominantEmotion" to Schema.enumeration(EmotionType.entries.map { it.name })
                        )
                    )
                ),
                "segments" to Schema.array(
                    items = Schema.obj(
                        properties = mapOf(
                            "startMs" to Schema.integer(),
                            "endMs" to Schema.integer(),
                            "speakerTag" to Schema.string(),
                            "originalLanguage" to Schema.string(),
                            "originalText" to Schema.string(),
                            "emotion" to Schema.enumeration(EmotionType.entries.map { it.name }),
                            "speakingPace" to Schema.double()
                        )
                    )
                )
            )
        )

        val config = generationConfig {
            temperature = 0.2f
            responseMimeType = "application/json"
            responseSchema = firebaseSchema
        }
        val model = Firebase.ai.generativeModel(
            modelName = resolveFirebaseModelName(modelId),
            generationConfig = config
        )

        val audioBytes = audioFile.readBytes()
        if (audioBytes.size > 18 * 1024 * 1024) {
            throw GeminiApiException("Audio chunk exceeds 18MB Firebase AI inline limit. Reduce Chunk Window in Settings.")
        }

        val response = withTimeout(AI_TIMEOUT_MS) {
            model.generateContent(
                content {
                    inlineData(audioBytes, "audio/wav")
                    text(promptText)
                }
            )
        }
        val jsonText = response.text ?: throw GeminiApiException("Firebase AI returned empty audio analysis.")
        return parseChunkAnalysis(jsonText, chunkOffsetMs, chunkDurationMs)
    }

    suspend fun translateDialogueToKhmer(
        storySummary: String,
        charactersDescription: String,
        segments: List<Pair<Int, String>>,
        modelId: String,
        maxRetries: Int
    ): List<TranslatedSegmentDto> = withContext(Dispatchers.IO) {
        val cleanModel = modelId.trim().removePrefix("models/").ifBlank { "gemini-3.8-flash" }
        val prompt = buildString {
            append("You are an award-winning Khmer film & drama localization director. ")
            append("Translate the following dialogue lines into natural, expressive, idiomatic Khmer (ភាសាខ្មែរ) suitable for live voice dubbing. ")
            append("Rules:\n")
            append("1. Preserve meaning, character relationships, age-appropriate Khmer pronouns (ខ្ញុំ, បង, អូន, លោក, អ្នក, ពូ, មីង), and emotional nuance.\n")
            append("2. Translate according to storyline & scene context — NEVER literal word-for-word.\n")
            append("3. Match the target duration (ms) of each line so the spoken Khmer fits naturally within the actor's mouth/speech window.\n")
            append("4. Use authentic Khmer script and clean punctuation.\n\n")
            append("Storyline Context: $storySummary\n")
            append("Characters: $charactersDescription\n\n")
            append("Dialogue Lines:\n")
            segments.forEach { (idx, desc) ->
                append("[$idx] $desc\n")
            }
        }

        val candidates = repository.getDecryptedCandidateKeys()
        if (candidates.isEmpty()) {
            return@withContext translateWithFirebaseAi(prompt, cleanModel)
        }

        try {
            executeWithFailover(candidates = candidates, maxRetries = maxRetries) { apiKey ->
                val schema = JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", JSONObject().apply {
                        put("translations", JSONObject().apply {
                            put("type", "ARRAY")
                            put("items", JSONObject().apply {
                                put("type", "OBJECT")
                                put("properties", JSONObject().apply {
                                    put("segmentIndex", JSONObject().put("type", "INTEGER"))
                                    put("khmerText", JSONObject().put("type", "STRING"))
                                    put("emotion", emotionRestSchema())
                                    put("recommendedPace", JSONObject().put("type", "NUMBER"))
                                })
                                put("required", JSONArray(listOf("segmentIndex", "khmerText", "emotion", "recommendedPace")))
                            })
                        })
                    })
                    put("required", JSONArray(listOf("translations")))
                }

                val requestBody = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().put("text", prompt))
                            })
                        })
                    })
                    put("generationConfig", JSONObject().apply {
                        put("temperature", 0.35)
                        put("responseMimeType", "application/json")
                        put("responseSchema", schema)
                    })
                }

                val (responseText, tokens) = postGenerateContent(cleanModel, apiKey, requestBody)
                parseTranslationsJson(responseText) to tokens
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Custom key pool failed for translation, falling back to Firebase AI Logic", e)
            translateWithFirebaseAi(prompt, cleanModel)
        }
    }

    private suspend fun translateWithFirebaseAi(prompt: String, modelId: String): List<TranslatedSegmentDto> {
        val translationSchema = Schema.obj(
            properties = mapOf(
                "translations" to Schema.array(
                    items = Schema.obj(
                        properties = mapOf(
                            "segmentIndex" to Schema.integer(),
                            "khmerText" to Schema.string(),
                            "emotion" to Schema.enumeration(EmotionType.entries.map { it.name }),
                            "recommendedPace" to Schema.double()
                        )
                    )
                )
            )
        )
        val config = generationConfig {
            temperature = 0.35f
            responseMimeType = "application/json"
            responseSchema = translationSchema
        }
        val model = Firebase.ai.generativeModel(
            modelName = resolveFirebaseModelName(modelId),
            generationConfig = config
        )
        val response = withTimeout(AI_TIMEOUT_MS) {
            model.generateContent(prompt)
        }
        val text = response.text ?: throw GeminiApiException("Firebase AI returned empty Khmer translation.")
        return parseTranslationsJson(text)
    }

    suspend fun synthesizeKhmerSpeech(
        khmerText: String,
        voiceName: String,
        emotion: EmotionType,
        ttsModelId: String,
        maxRetries: Int
    ): ByteArray? = withContext(Dispatchers.IO) {
        val cleanModel = ttsModelId.trim().removePrefix("models/").ifBlank { "gemini-3.1-flash-tts-preview" }
        val styledPrompt = "Speak naturally in Khmer with a ${emotion.label.lowercase()} tone: $khmerText"

        val candidates = repository.getDecryptedCandidateKeys()
        if (candidates.isEmpty()) {
            return@withContext synthesizeWithFirebaseAi(styledPrompt, voiceName, cleanModel)
        }

        try {
            executeWithFailover(candidates = candidates, maxRetries = maxRetries) { apiKey ->
                val requestBody = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().put("text", styledPrompt))
                            })
                        })
                    })
                    put("generationConfig", JSONObject().apply {
                        put("responseModalities", JSONArray(listOf("AUDIO")))
                        put("speechConfig", JSONObject().apply {
                            put("voiceConfig", JSONObject().apply {
                                put("prebuiltVoiceConfig", JSONObject().apply {
                                    put("voiceName", voiceName)
                                })
                            })
                        })
                    })
                }

                val request = Request.Builder()
                    .url("$BASE_URL/v1beta/models/$cleanModel:generateContent")
                    .addHeader("x-goog-api-key", apiKey)
                    .post(requestBody.toString().toRequestBody(jsonMediaType))
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val bodyStr = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw GeminiApiException(parseApiError(bodyStr, response.code), response.code)
                    }
                    val root = JSONObject(bodyStr)
                    val tokens = root.optJSONObject("usageMetadata")?.optLong("totalTokenCount", 0L) ?: 0L
                    val parts = root.optJSONArray("candidates")
                        ?.optJSONObject(0)
                        ?.optJSONObject("content")
                        ?.optJSONArray("parts")
                    var wavBytes: ByteArray? = null
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val inline = parts.optJSONObject(i)?.optJSONObject("inlineData")
                            val dataB64 = inline?.optString("data").orEmpty()
                            val mime = inline?.optString("mimeType").orEmpty()
                            if (dataB64.isNotBlank()) {
                                val raw = Base64.decode(dataB64, Base64.DEFAULT)
                                wavBytes = normalizeAudioToWav(raw, mime)
                                break
                            }
                        }
                    }
                    wavBytes to tokens
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Custom key TTS failed, trying Firebase AI TTS: ${e.message}")
            synthesizeWithFirebaseAi(styledPrompt, voiceName, cleanModel)
        }
    }

    @OptIn(PublicPreviewAPI::class)
    private suspend fun synthesizeWithFirebaseAi(
        styledPrompt: String,
        voiceName: String,
        ttsModelId: String
    ): ByteArray? {
        return try {
            val config = generationConfig {
                responseModalities = listOf(ResponseModality.AUDIO)
                speechConfig = SpeechConfig(Voice(voiceName))
            }
            val model = Firebase.ai.generativeModel(
                modelName = ttsModelId.ifBlank { "gemini-3.1-flash-tts-preview" },
                generationConfig = config
            )
            val response = withTimeout(AI_TIMEOUT_MS) {
                model.generateContent(styledPrompt)
            }
            val audioPart = response.candidates
                .firstOrNull()
                ?.content
                ?.parts
                ?.filterIsInstance<InlineDataPart>()
                ?.firstOrNull()
            audioPart?.let { normalizeAudioToWav(it.inlineData, it.mimeType) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Firebase AI TTS unavailable, falling back to Android TTS: ${e.message}")
            null
        }
    }

    private fun resolveFirebaseModelName(requested: String): String {
        val clean = requested.trim()
        return when {
            clean.isBlank() || clean == "gemini-3.1-flash-preview" -> "gemini-3.8-flash"
            clean == "gemini-2.5-flash" || clean == "gemini-2.0-flash" -> "gemini-3.8-flash"
            else -> clean
        }
    }

    private fun normalizeAudioToWav(raw: ByteArray, mime: String): ByteArray {
        return if (mime.contains("wav", ignoreCase = true)) {
            raw
        } else {
            val rate = Regex("rate=(\\d+)").find(mime)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 24000
            pcm16ToWav(raw, sampleRate = rate, channels = 1)
        }
    }

    private fun parseTranslationsJson(jsonText: String): List<TranslatedSegmentDto> {
        val root = JSONObject(jsonText)
        val arr = root.optJSONArray("translations") ?: JSONArray()
        return (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            TranslatedSegmentDto(
                segmentIndex = obj.optInt("segmentIndex", i),
                khmerText = obj.optString("khmerText", "").trim(),
                emotion = parseEmotion(obj.optString("emotion")),
                recommendedPace = obj.optDouble("recommendedPace", 1.0).toFloat().coerceIn(0.8f, 1.3f)
            )
        }
    }

    private suspend fun <T> executeWithFailover(
        candidates: List<Pair<com.example.data.local.ApiKeyEntity, String>>,
        maxRetries: Int,
        block: suspend (apiKey: String) -> Pair<T, Long>
    ): T {
        var lastError: Exception? = null
        val maxAttempts = maxOf(candidates.size, maxRetries + 1)

        for (attempt in 0 until maxAttempts) {
            val (keyEntity, rawKey) = candidates[attempt % candidates.size]
            try {
                val (result, tokens) = block(rawKey)
                repository.recordKeySuccess(keyEntity.id, tokens)
                return result
            } catch (e: GeminiApiException) {
                lastError = e
                val code = e.httpCode
                val status = when {
                    code == 429 -> KeyStatus.RATE_LIMITED
                    code == 401 || code == 403 -> KeyStatus.ERROR
                    e.message?.contains("quota", ignoreCase = true) == true -> KeyStatus.QUOTA_EXCEEDED
                    else -> KeyStatus.ERROR
                }
                repository.recordKeyFailure(keyEntity.id, status, e.message ?: "HTTP $code")
                Log.w(TAG, "Key '${keyEntity.label}' failed ($status), rotating to next key...", e)
                delay(min(1500L * (attempt + 1), 4000L))
            } catch (e: IOException) {
                lastError = e
                repository.recordKeyFailure(keyEntity.id, KeyStatus.ERROR, e.message ?: "Network error")
                delay(min(1500L * (attempt + 1), 4000L))
            }
        }
        throw lastError ?: GeminiApiException("All Gemini API keys failed after $maxAttempts attempts.")
    }

    private fun postGenerateContent(model: String, apiKey: String, payload: JSONObject): Pair<String, Long> {
        val request = Request.Builder()
            .url("$BASE_URL/v1beta/models/$model:generateContent")
            .addHeader("x-goog-api-key", apiKey)
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw GeminiApiException(parseApiError(bodyStr, response.code), response.code)
            }
            val root = JSONObject(bodyStr)
            val tokens = root.optJSONObject("usageMetadata")?.optLong("totalTokenCount", 0L) ?: 0L
            val text = root.optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text")
                ?.trim()
                .orEmpty()
            if (text.isBlank()) {
                throw GeminiApiException("Gemini returned an empty response body.")
            }
            return text to tokens
        }
    }

    private fun uploadAudioFile(apiKey: String, file: File, mimeType: String): String {
        val uploadReq = Request.Builder()
            .url("$BASE_URL/upload/v1beta/files?uploadType=media")
            .addHeader("x-goog-api-key", apiKey)
            .addHeader("X-Goog-Upload-Protocol", "raw")
            .addHeader("Content-Type", mimeType)
            .post(file.asRequestBody(mimeType.toMediaType()))
            .build()

        httpClient.newCall(uploadReq).execute().use { response ->
            val bodyStr = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw GeminiApiException(parseApiError(bodyStr, response.code), response.code)
            }
            val fileObj = JSONObject(bodyStr).optJSONObject("file")
                ?: throw GeminiApiException("Files API response missing file metadata.")
            return fileObj.getString("uri")
        }
    }

    private fun parseApiError(bodyStr: String, code: Int): String {
        return try {
            val err = JSONObject(bodyStr).optJSONObject("error")
            val msg = err?.optString("message").orEmpty()
            if (msg.isNotBlank()) "HTTP $code: $msg" else "HTTP $code error"
        } catch (_: Exception) {
            "HTTP $code error"
        }
    }

    private fun buildRestAnalysisSchema(): JSONObject = JSONObject().apply {
        put("type", "OBJECT")
        put("properties", JSONObject().apply {
            put("detectedLanguages", JSONObject().apply {
                put("type", "ARRAY")
                put("items", JSONObject().put("type", "STRING"))
            })
            put("storySummary", JSONObject().put("type", "STRING"))
            put("characters", JSONObject().apply {
                put("type", "ARRAY")
                put("items", JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", JSONObject().apply {
                        put("speakerTag", JSONObject().put("type", "STRING"))
                        put("displayName", JSONObject().put("type", "STRING"))
                        put("gender", JSONObject().apply {
                            put("type", "STRING")
                            put("enum", JSONArray(listOf("MALE", "FEMALE", "NON_BINARY")))
                        })
                        put("ageGroup", JSONObject().apply {
                            put("type", "STRING")
                            put("enum", JSONArray(listOf("CHILD", "YOUNG_ADULT", "ADULT", "ELDERLY")))
                        })
                        put("voiceStyle", JSONObject().put("type", "STRING"))
                        put("dominantEmotion", emotionRestSchema())
                    })
                    put("required", JSONArray(listOf("speakerTag", "displayName", "gender", "ageGroup", "voiceStyle", "dominantEmotion")))
                })
            })
            put("segments", JSONObject().apply {
                put("type", "ARRAY")
                put("items", JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", JSONObject().apply {
                        put("startMs", JSONObject().put("type", "INTEGER"))
                        put("endMs", JSONObject().put("type", "INTEGER"))
                        put("speakerTag", JSONObject().put("type", "STRING"))
                        put("originalLanguage", JSONObject().put("type", "STRING"))
                        put("originalText", JSONObject().put("type", "STRING"))
                        put("emotion", emotionRestSchema())
                        put("speakingPace", JSONObject().put("type", "NUMBER"))
                    })
                    put("required", JSONArray(listOf("startMs", "endMs", "speakerTag", "originalLanguage", "originalText", "emotion", "speakingPace")))
                })
            })
        })
        put("required", JSONArray(listOf("detectedLanguages", "storySummary", "characters", "segments")))
    }

    private fun parseChunkAnalysis(
        jsonText: String,
        chunkOffsetMs: Long,
        chunkDurationMs: Long
    ): ChunkAnalysisResult {
        val root = JSONObject(jsonText)
        val langsArr = root.optJSONArray("detectedLanguages") ?: JSONArray()
        val langs = (0 until langsArr.length()).mapNotNull { langsArr.optString(it).takeIf(String::isNotBlank) }
        val summary = root.optString("storySummary", "")

        val charsArr = root.optJSONArray("characters") ?: JSONArray()
        val characters = (0 until charsArr.length()).map { i ->
            val obj = charsArr.getJSONObject(i)
            AnalyzedCharacterDto(
                speakerTag = obj.optString("speakerTag", "SPEAKER_${i + 1}"),
                displayName = obj.optString("displayName", "Speaker ${i + 1}"),
                gender = runCatching { SpeakerGender.valueOf(obj.optString("gender")) }
                    .getOrDefault(SpeakerGender.MALE),
                ageGroup = runCatching { AgeGroup.valueOf(obj.optString("ageGroup")) }
                    .getOrDefault(AgeGroup.ADULT),
                voiceStyle = obj.optString("voiceStyle", "Natural"),
                dominantEmotion = parseEmotion(obj.optString("dominantEmotion"))
            )
        }

        val segsArr = root.optJSONArray("segments") ?: JSONArray()
        val segments = (0 until segsArr.length()).mapNotNull { i ->
            val obj = segsArr.getJSONObject(i)
            val text = obj.optString("originalText", "").trim()
            if (text.isBlank()) return@mapNotNull null
            val rawStart = obj.optLong("startMs", 0L).coerceIn(0L, chunkDurationMs)
            val rawEnd = obj.optLong("endMs", rawStart + 1500L).coerceIn(rawStart + 350L, chunkDurationMs + 500L)
            AnalyzedSegmentDto(
                startMs = chunkOffsetMs + rawStart,
                endMs = chunkOffsetMs + rawEnd,
                speakerTag = obj.optString("speakerTag", "SPEAKER_1"),
                originalLanguage = obj.optString("originalLanguage", langs.firstOrNull() ?: "Auto"),
                originalText = text,
                emotion = parseEmotion(obj.optString("emotion")),
                speakingPace = obj.optDouble("speakingPace", 1.0).toFloat().coerceIn(0.75f, 1.35f)
            )
        }

        return ChunkAnalysisResult(
            detectedLanguages = langs.ifEmpty { listOf("Auto-detected") },
            storySummary = summary,
            characters = characters,
            segments = segments
        )
    }

    private fun emotionRestSchema(): JSONObject = JSONObject().apply {
        put("type", "STRING")
        put("enum", JSONArray(EmotionType.entries.map { it.name }))
    }

    private fun parseEmotion(raw: String): EmotionType =
        runCatching { EmotionType.valueOf(raw.trim().uppercase()) }.getOrDefault(EmotionType.NEUTRAL)

    private fun pcm16ToWav(pcmData: ByteArray, sampleRate: Int, channels: Int): ByteArray {
        val byteRate = sampleRate * channels * 2
        val dataSize = pcmData.size
        val totalSize = 36 + dataSize
        val out = ByteArrayOutputStream(44 + dataSize)
        out.write("RIFF".toByteArray(Charsets.US_ASCII))
        out.write(intToLittleEndian(totalSize))
        out.write("WAVE".toByteArray(Charsets.US_ASCII))
        out.write("fmt ".toByteArray(Charsets.US_ASCII))
        out.write(intToLittleEndian(16))
        out.write(shortToLittleEndian(1))
        out.write(shortToLittleEndian(channels.toShort()))
        out.write(intToLittleEndian(sampleRate))
        out.write(intToLittleEndian(byteRate))
        out.write(shortToLittleEndian((channels * 2).toShort()))
        out.write(shortToLittleEndian(16))
        out.write("data".toByteArray(Charsets.US_ASCII))
        out.write(intToLittleEndian(dataSize))
        out.write(pcmData)
        return out.toByteArray()
    }

    private fun intToLittleEndian(value: Int): ByteArray = byteArrayOf(
        (value and 0xff).toByte(),
        ((value shr 8) and 0xff).toByte(),
        ((value shr 16) and 0xff).toByte(),
        ((value shr 24) and 0xff).toByte()
    )

    private fun shortToLittleEndian(value: Short): ByteArray = byteArrayOf(
        (value.toInt() and 0xff).toByte(),
        ((value.toInt() shr 8) and 0xff).toByte()
    )
}
