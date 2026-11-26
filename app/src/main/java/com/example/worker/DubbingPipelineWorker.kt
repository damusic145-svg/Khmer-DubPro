package com.example.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.local.CharacterVoiceEntity
import com.example.data.local.DialogueSegmentEntity
import com.example.data.local.SettingsDataStore
import com.example.data.model.PipelineStage
import com.example.data.repository.DubbingRepository
import com.example.data.repository.KhmerVoiceCatalog
import com.example.media.AudioExtractor
import com.example.media.AudioSynchronizer
import com.example.media.KhmerTtsEngine
import com.example.network.AnalyzedCharacterDto
import com.example.network.AnalyzedSegmentDto
import com.example.network.GeminiApiClient
import com.example.security.KeystoreCryptoManager
import java.io.File
import java.util.Locale

class DubbingPipelineWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_PROJECT_ID = "project_id"
        const val KEY_EMBEDDED_SUBS = "embedded_subs"
        const val CHANNEL_ID = "dubbing_pipeline_channel"
        private const val NOTIFICATION_ID = 4102
        private const val TAG = "DubbingPipelineWorker"
    }

    override suspend fun doWork(): Result {
        val projectId = inputData.getLong(KEY_PROJECT_ID, -1L)
        if (projectId <= 0L) return Result.failure()
        val embeddedSubs = inputData.getString(KEY_EMBEDDED_SUBS).orEmpty()

        val db = AppDatabase.getInstance(applicationContext)
        val repository = DubbingRepository(
            db = db,
            crypto = KeystoreCryptoManager(),
            settingsDataStore = SettingsDataStore(applicationContext)
        )
        val geminiClient = GeminiApiClient(repository)
        val ttsEngine = KhmerTtsEngine(applicationContext, geminiClient)

        val project = repository.getProject(projectId) ?: return Result.failure()
        val projectDir = File(project.localVideoPath).parentFile
            ?: File(applicationContext.filesDir, "projects/$projectId").apply { mkdirs() }
        val settings = repository.getCurrentSettings()

        setForeground(createForegroundInfo("Starting AI Khmer dubbing...", 4))

        return try {
            // Stage 1: Extract Audio & Waveform
            updateProgress(repository, projectId, PipelineStage.EXTRACTING_AUDIO, 10, "Extracting audio track & waveform...")
            val videoFile = File(project.localVideoPath)
            val extractedWav = File(projectDir, "extracted_audio.wav")
            val extracted = AudioExtractor.extractAudioToWav(videoFile, extractedWav) { pct ->
                val overall = 8 + (pct * 0.14).toInt()
                setProgressAsync(workDataOf("progress" to overall))
            }

            val waveformCsv = extracted.waveformBars.joinToString(",") {
                String.format(Locale.US, "%.3f", it)
            }
            repository.updateProject(
                project.copy(
                    extractedAudioPath = extractedWav.absolutePath,
                    durationMs = if (project.durationMs > 0L) project.durationMs else extracted.durationMs,
                    waveformCsv = waveformCsv,
                    stage = PipelineStage.DETECTING_LANGUAGE.name,
                    progressPercent = 24,
                    statusMessage = "Audio extracted (${extracted.durationMs / 1000}s)"
                )
            )

            // Stage 2 & 3: Chunked Multimodal ASR, Language Detection & Speaker Diarization
            updateProgress(repository, projectId, PipelineStage.DETECTING_LANGUAGE, 28, "Detecting languages & dialogue...")
            val chunkDir = File(projectDir, "chunks")
            val chunks = AudioExtractor.splitWavIntoChunks(
                wavFile = extractedWav,
                chunkDir = chunkDir,
                chunkDurationSec = settings.chunkDurationSeconds
            )

            val allLanguages = linkedSetOf<String>()
            val mergedCharacters = linkedMapOf<String, AnalyzedCharacterDto>()
            val allSegments = mutableListOf<AnalyzedSegmentDto>()
            val summaryBuilder = StringBuilder()

            chunks.forEachIndexed { idx, chunk ->
                val pct = 28 + ((idx * 22) / maxOf(1, chunks.size))
                updateProgress(
                    repository,
                    projectId,
                    if (idx == 0) PipelineStage.DETECTING_LANGUAGE else PipelineStage.DIARIZING_SPEAKERS,
                    pct,
                    "Analyzing scene chunk ${idx + 1}/${chunks.size}..."
                )
                val analysis = geminiClient.analyzeAudioChunk(
                    audioFile = chunk.file,
                    chunkIndex = idx,
                    chunkOffsetMs = chunk.startMs,
                    chunkDurationMs = chunk.durationMs,
                    priorStorySummary = summaryBuilder.toString().takeLast(900),
                    existingSubtitleHints = if (idx == 0) embeddedSubs else "",
                    modelId = settings.primaryModel,
                    maxRetries = settings.maxRetries
                )
                allLanguages.addAll(analysis.detectedLanguages)
                if (analysis.storySummary.isNotBlank()) {
                    if (summaryBuilder.isNotEmpty()) summaryBuilder.append(" ")
                    summaryBuilder.append(analysis.storySummary)
                }
                analysis.characters.forEach { ch ->
                    mergedCharacters.putIfAbsent(ch.speakerTag, ch)
                }
                allSegments.addAll(analysis.segments)
            }

            if (mergedCharacters.isEmpty()) {
                mergedCharacters["SPEAKER_1"] = AnalyzedCharacterDto(
                    speakerTag = "SPEAKER_1",
                    displayName = "Narrator",
                    gender = com.example.data.model.SpeakerGender.MALE,
                    ageGroup = com.example.data.model.AgeGroup.ADULT,
                    voiceStyle = "Natural",
                    dominantEmotion = com.example.data.model.EmotionType.NEUTRAL
                )
            }

            // Stage 4: Context-Aware Storyline Khmer Translation
            updateProgress(repository, projectId, PipelineStage.TRANSLATING_KHMER, 54, "Translating dialogue into natural Khmer...")
            val storySummary = summaryBuilder.toString().ifBlank {
                "Video dialogue in ${allLanguages.joinToString(", ")}"
            }
            val charSummaryText = mergedCharacters.values.joinToString("; ") {
                "${it.speakerTag} (${it.displayName}, ${it.gender.label}, ${it.ageGroup.label}, ${it.voiceStyle})"
            }

            val translatedMap = mutableMapOf<Int, Pair<String, Float>>()
            val batchSize = 25
            allSegments.chunked(batchSize).forEachIndexed { batchIdx, batch ->
                val baseIndex = batchIdx * batchSize
                val batchInput = batch.mapIndexed { localIdx, seg ->
                    val globalIdx = baseIndex + localIdx
                    val dur = (seg.endMs - seg.startMs).coerceAtLeast(500L)
                    globalIdx to "Speaker=${seg.speakerTag}, Lang=${seg.originalLanguage}, Emotion=${seg.emotion.name}, TargetDurationMs=$dur, Text=\"${seg.originalText}\""
                }
                val results = geminiClient.translateDialogueToKhmer(
                    storySummary = storySummary,
                    charactersDescription = charSummaryText,
                    segments = batchInput,
                    modelId = settings.primaryModel,
                    maxRetries = settings.maxRetries
                )
                results.forEach { tr ->
                    translatedMap[tr.segmentIndex] = tr.khmerText to tr.recommendedPace
                }
            }

            // Persist Characters & Segments
            val segCounts = allSegments.groupingBy { it.speakerTag }.eachCount()
            val characterEntities = mergedCharacters.values.mapIndexed { idx, ch ->
                val matchedPreset = KhmerVoiceCatalog.matchVoice(ch.gender, ch.ageGroup)
                CharacterVoiceEntity(
                    projectId = projectId,
                    speakerTag = ch.speakerTag,
                    displayName = ch.displayName,
                    gender = ch.gender.name,
                    ageGroup = ch.ageGroup.name,
                    voiceStyle = ch.voiceStyle,
                    dominantEmotion = ch.dominantEmotion.name,
                    assignedVoiceId = matchedPreset.id,
                    pitchOffset = 1.0f,
                    speedMultiplier = 1.0f,
                    colorIndex = idx,
                    segmentCount = segCounts[ch.speakerTag] ?: 0
                )
            }

            val segmentEntities = allSegments.mapIndexed { idx, seg ->
                val tr = translatedMap[idx]
                DialogueSegmentEntity(
                    projectId = projectId,
                    characterId = 0L,
                    speakerTag = seg.speakerTag,
                    segmentIndex = idx,
                    startMs = seg.startMs,
                    endMs = seg.endMs,
                    originalLanguage = seg.originalLanguage,
                    originalText = seg.originalText,
                    khmerText = tr?.first?.ifBlank { seg.originalText } ?: seg.originalText,
                    emotion = seg.emotion.name,
                    speakingPace = tr?.second ?: seg.speakingPace
                )
            }

            val persistedChars = repository.replaceProjectAnalysis(projectId, characterEntities, segmentEntities)
            val charByTag = persistedChars.associateBy { it.speakerTag }
            val persistedSegments = repository.getSegments(projectId)

            val latestProject = repository.getProject(projectId) ?: project
            repository.updateProject(
                latestProject.copy(
                    detectedLanguagesCsv = allLanguages.joinToString(", "),
                    storySummary = storySummary,
                    stage = PipelineStage.SYNTHESIZING_TTS.name,
                    progressPercent = 70,
                    statusMessage = "Generating Khmer AI character voices..."
                )
            )

            // Stage 5: Khmer AI Voice Generation per Segment
            val ttsDir = File(projectDir, "tts").apply { mkdirs() }
            persistedSegments.forEachIndexed { idx, seg ->
                val pct = 70 + ((idx * 20) / maxOf(1, persistedSegments.size))
                updateProgress(
                    repository,
                    projectId,
                    PipelineStage.SYNTHESIZING_TTS,
                    pct,
                    "Synthesizing Khmer voice ${idx + 1}/${persistedSegments.size}..."
                )
                val charEntity = charByTag[seg.speakerTag] ?: persistedChars.first()
                val preset = KhmerVoiceCatalog.findById(charEntity.assignedVoiceId)
                val emotion = runCatching {
                    com.example.data.model.EmotionType.valueOf(seg.emotion)
                }.getOrDefault(com.example.data.model.EmotionType.NEUTRAL)

                val outWav = File(ttsDir, "seg_${seg.id}.wav")
                val ttsRes = ttsEngine.synthesizeSegment(
                    text = seg.khmerText.ifBlank { seg.originalText },
                    voicePreset = preset,
                    emotion = emotion,
                    pitchOffset = charEntity.pitchOffset,
                    speedMultiplier = charEntity.speedMultiplier,
                    targetDurationMs = (seg.endMs - seg.startMs).coerceAtLeast(500L),
                    autoAdjustSpeed = settings.autoAdjustSpeed,
                    engineMode = settings.ttsEngineMode,
                    ttsModelId = settings.ttsModel,
                    maxRetries = settings.maxRetries,
                    outputWavFile = outWav
                )
                repository.updateSegment(
                    seg.copy(
                        ttsAudioPath = ttsRes.wavFile.absolutePath,
                        ttsDurationMs = ttsRes.durationMs,
                        speedRatio = ttsRes.appliedSpeedRatio
                    )
                )
            }

            // Stage 6: Audio Synchronization & Ducking Mix
            updateProgress(repository, projectId, PipelineStage.SYNCHRONIZING_AUDIO, 93, "Synchronizing Khmer audio & preserving background SFX...")
            val finalSegments = repository.getSegments(projectId)
            val mixedWav = File(projectDir, "mixed_dubbed.wav")
            val currentProj = repository.getProject(projectId) ?: latestProject
            AudioSynchronizer.mixSynchronizedTrack(
                originalAudioWav = extractedWav,
                segments = finalSegments,
                duckingDb = currentProj.duckingDb,
                originalBaseGain = currentProj.originalAudioGain,
                dubbedGain = currentProj.dubbedAudioGain,
                outputMixedWav = mixedWav
            )

            repository.updateProject(
                currentProj.copy(
                    mixedAudioPath = mixedWav.absolutePath,
                    stage = PipelineStage.READY.name,
                    progressPercent = 100,
                    statusMessage = "Khmer dubbing ready for preview & export",
                    errorDetail = null
                )
            )
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Pipeline failed for project $projectId", e)
            repository.updateProjectStage(
                projectId = projectId,
                stage = PipelineStage.FAILED,
                progress = 0,
                message = "Pipeline paused: ${e.localizedMessage ?: "Unknown error"}",
                errorDetail = e.localizedMessage ?: e.javaClass.simpleName
            )
            Result.failure(workDataOf("error" to (e.localizedMessage ?: "Pipeline failed")))
        }
    }

    private suspend fun updateProgress(
        repository: DubbingRepository,
        projectId: Long,
        stage: PipelineStage,
        progress: Int,
        message: String
    ) {
        repository.updateProjectStage(projectId, stage, progress, message)
        setForeground(createForegroundInfo(message, progress))
    }

    private fun createForegroundInfo(message: String, progress: Int): ForegroundInfo {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                applicationContext.getString(R.string.notification_channel_dubbing),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = applicationContext.getString(R.string.notification_channel_dubbing_desc)
            }
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(applicationContext.getString(R.string.app_name))
            .setContentText(message)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }
}
