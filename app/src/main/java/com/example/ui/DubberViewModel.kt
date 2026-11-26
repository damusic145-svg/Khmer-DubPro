package com.example.ui

import android.app.Application
import android.media.MediaPlayer
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.data.local.ApiKeyEntity
import com.example.data.local.AppDatabase
import com.example.data.local.CharacterVoiceEntity
import com.example.data.local.DialogueSegmentEntity
import com.example.data.local.DubbingProjectEntity
import com.example.data.local.ExportHistoryEntity
import com.example.data.local.SettingsDataStore
import com.example.data.model.AppSettings
import com.example.data.model.AudioTrackMode
import com.example.data.model.EmotionType
import com.example.data.model.ExportResolution
import com.example.data.model.KeyStatus
import com.example.data.model.KhmerVoicePreset
import com.example.data.model.PipelineStage
import com.example.data.model.SubtitleExportMode
import com.example.data.repository.DubbingRepository
import com.example.data.repository.KhmerVoiceCatalog
import com.example.media.AudioSynchronizer
import com.example.media.ExportOutputResult
import com.example.media.KhmerTtsEngine
import com.example.media.VideoExporter
import com.example.media.VideoInspector
import com.example.network.GeminiApiClient
import com.example.security.KeystoreCryptoManager
import com.example.worker.DubbingPipelineWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

sealed interface ActionUiState {
    data object Idle : ActionUiState
    data class Loading(val message: String) : ActionUiState
    data class Success(val message: String) : ActionUiState
    data class Error(val message: String) : ActionUiState
}

sealed interface ExportUiState {
    data object Idle : ExportUiState
    data class Exporting(val progressPercent: Int, val stageLabel: String) : ExportUiState
    data class Success(val result: ExportOutputResult) : ExportUiState
    data class Error(val message: String) : ExportUiState
}

sealed interface AuditionUiState {
    data object Idle : AuditionUiState
    data class Synthesizing(val targetId: String) : AuditionUiState
    data class Playing(val targetId: String, val engineUsed: String) : AuditionUiState
    data class Error(val message: String) : AuditionUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
class DubberViewModel(application: Application) : AndroidViewModel(application) {

    private val crypto = KeystoreCryptoManager()
    private val db = AppDatabase.getInstance(application)
    private val settingsDataStore = SettingsDataStore(application)
    val repository = DubbingRepository(db, crypto, settingsDataStore)
    private val geminiClient = GeminiApiClient(repository)
    private val ttsEngine = KhmerTtsEngine(application, geminiClient)

    val allProjects: StateFlow<List<DubbingProjectEntity>> = repository.allProjects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allKeys: StateFlow<List<ApiKeyEntity>> = repository.allKeys
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val exportHistory: StateFlow<List<ExportHistoryEntity>> = repository.exportHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<AppSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    private val _activeProjectId = MutableStateFlow<Long?>(null)
    val activeProjectId: StateFlow<Long?> = _activeProjectId.asStateFlow()

    val activeProject: StateFlow<DubbingProjectEntity?> = _activeProjectId
        .flatMapLatest { id -> if (id == null) flowOf(null) else repository.observeProject(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val activeCharacters: StateFlow<List<CharacterVoiceEntity>> = _activeProjectId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.observeCharacters(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeSegments: StateFlow<List<DialogueSegmentEntity>> = _activeProjectId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.observeSegments(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _importState = MutableStateFlow<ActionUiState>(ActionUiState.Idle)
    val importState: StateFlow<ActionUiState> = _importState.asStateFlow()

    private val _keyTestState = MutableStateFlow<ActionUiState>(ActionUiState.Idle)
    val keyTestState: StateFlow<ActionUiState> = _keyTestState.asStateFlow()

    private val _exportState = MutableStateFlow<ExportUiState>(ExportUiState.Idle)
    val exportState: StateFlow<ExportUiState> = _exportState.asStateFlow()

    private val _auditionState = MutableStateFlow<AuditionUiState>(AuditionUiState.Idle)
    val auditionState: StateFlow<AuditionUiState> = _auditionState.asStateFlow()

    private val _syncActionState = MutableStateFlow<ActionUiState>(ActionUiState.Idle)
    val syncActionState: StateFlow<ActionUiState> = _syncActionState.asStateFlow()

    private val _snackbar = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbar: SharedFlow<String> = _snackbar.asSharedFlow()

    private var mediaPlayer: MediaPlayer? = null

    fun selectProject(projectId: Long) {
        _activeProjectId.value = projectId
        _exportState.value = ExportUiState.Idle
    }

    fun resetImportState() {
        _importState.value = ActionUiState.Idle
    }

    fun resetKeyTestState() {
        _keyTestState.value = ActionUiState.Idle
    }

    fun importVideoAndStart(
        uri: Uri,
        customTitle: String?,
        duckingDb: Int,
        autoStartPipeline: Boolean,
        onCreated: (Long) -> Unit
    ) {
        viewModelScope.launch {
            _importState.value = ActionUiState.Loading("Inspecting video tracks & subtitles...")
            try {
                val folderKey = UUID.randomUUID().toString().take(8)
                val info = VideoInspector.importAndInspectVideo(getApplication(), uri, folderKey)
                val finalTitle = customTitle?.trim()?.takeIf { it.isNotBlank() } ?: info.title
                val projectId = repository.insertProject(
                    DubbingProjectEntity(
                        title = finalTitle,
                        sourceUri = uri.toString(),
                        localVideoPath = info.localVideoFile.absolutePath,
                        thumbnailPath = info.thumbnailFile?.absolutePath,
                        durationMs = info.durationMs,
                        videoWidth = info.width,
                        videoHeight = info.height,
                        fps = info.fps,
                        hasEmbeddedSubtitles = info.hasEmbeddedSubtitles,
                        duckingDb = duckingDb,
                        stage = PipelineStage.IMPORTED.name,
                        progressPercent = 0,
                        statusMessage = "Imported (${info.width}x${info.height})"
                    )
                )
                _activeProjectId.value = projectId
                _importState.value = ActionUiState.Success("Imported $finalTitle")
                if (autoStartPipeline) {
                    enqueueDubbingWorker(projectId, info.embeddedSubtitleHints)
                }
                onCreated(projectId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _importState.value = ActionUiState.Error(e.localizedMessage ?: "Video import failed")
            }
        }
    }

    fun startOrRetryPipeline(projectId: Long, embeddedSubs: String = "") {
        viewModelScope.launch {
            repository.updateProjectStage(
                projectId = projectId,
                stage = PipelineStage.EXTRACTING_AUDIO,
                progress = 2,
                message = "Queuing AI Khmer dubbing pipeline..."
            )
            enqueueDubbingWorker(projectId, embeddedSubs)
        }
    }

    private fun enqueueDubbingWorker(projectId: Long, embeddedSubs: String) {
        val request = OneTimeWorkRequestBuilder<DubbingPipelineWorker>()
            .setInputData(
                workDataOf(
                    DubbingPipelineWorker.KEY_PROJECT_ID to projectId,
                    DubbingPipelineWorker.KEY_EMBEDDED_SUBS to embeddedSubs.take(4000)
                )
            )
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag("dubbing_$projectId")
            .build()

        WorkManager.getInstance(getApplication()).enqueueUniqueWork(
            "dubbing_pipeline_$projectId",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun deleteProject(projectId: Long) {
        viewModelScope.launch {
            val proj = repository.getProject(projectId)
            if (proj != null) {
                runCatching { File(proj.localVideoPath).parentFile?.deleteRecursively() }
            }
            repository.deleteProject(projectId)
            if (_activeProjectId.value == projectId) {
                _activeProjectId.value = null
            }
            _snackbar.emit("Project removed")
        }
    }

    fun updateCharacterVoice(
        character: CharacterVoiceEntity,
        voiceId: String,
        pitchOffset: Float,
        speedMultiplier: Float
    ) {
        viewModelScope.launch {
            repository.updateCharacter(
                character.copy(
                    assignedVoiceId = voiceId,
                    pitchOffset = pitchOffset.coerceIn(0.75f, 1.35f),
                    speedMultiplier = speedMultiplier.coerceIn(0.75f, 1.30f)
                )
            )
            _snackbar.emit("Updated voice for ${character.displayName}")
        }
    }

    fun updateSegmentKhmerText(
        segment: DialogueSegmentEntity,
        newKhmerText: String,
        newEmotion: EmotionType
    ) {
        viewModelScope.launch {
            repository.updateSegment(
                segment.copy(
                    khmerText = newKhmerText.trim(),
                    emotion = newEmotion.name,
                    isManuallyEdited = true
                )
            )
            _snackbar.emit("Saved Khmer dialogue edit")
        }
    }

    fun retranslateSegmentWithGemini(segment: DialogueSegmentEntity) {
        viewModelScope.launch {
            _syncActionState.value = ActionUiState.Loading("Re-translating line with Gemini...")
            try {
                val proj = repository.getProject(segment.projectId)
                val chars = repository.getCharacters(segment.projectId)
                val charDesc = chars.joinToString("; ") { "${it.speakerTag} (${it.displayName}, ${it.gender})" }
                val currentSettings = repository.getCurrentSettings()
                val dur = (segment.endMs - segment.startMs).coerceAtLeast(500L)
                val res = geminiClient.translateDialogueToKhmer(
                    storySummary = proj?.storySummary.orEmpty(),
                    charactersDescription = charDesc,
                    segments = listOf(
                        segment.segmentIndex to "Speaker=${segment.speakerTag}, Lang=${segment.originalLanguage}, Emotion=${segment.emotion}, TargetDurationMs=$dur, Text=\"${segment.originalText}\""
                    ),
                    modelId = currentSettings.primaryModel,
                    maxRetries = currentSettings.maxRetries
                )
                val translated = res.firstOrNull()
                if (translated != null && translated.khmerText.isNotBlank()) {
                    repository.updateSegment(
                        segment.copy(
                            khmerText = translated.khmerText,
                            emotion = translated.emotion.name,
                            speakingPace = translated.recommendedPace,
                            isManuallyEdited = false
                        )
                    )
                    _syncActionState.value = ActionUiState.Success("Re-translated line into Khmer")
                } else {
                    _syncActionState.value = ActionUiState.Error("Gemini returned empty translation")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _syncActionState.value = ActionUiState.Error(e.localizedMessage ?: "Re-translation failed")
            }
        }
    }

    fun resynthesizeSegment(segment: DialogueSegmentEntity) {
        viewModelScope.launch {
            _auditionState.value = AuditionUiState.Synthesizing("seg_${segment.id}")
            try {
                val proj = repository.getProject(segment.projectId) ?: return@launch
                val chars = repository.getCharacters(segment.projectId)
                val charEntity = chars.firstOrNull { it.id == segment.characterId }
                    ?: chars.firstOrNull { it.speakerTag == segment.speakerTag }
                    ?: chars.firstOrNull()
                val preset = KhmerVoiceCatalog.findById(charEntity?.assignedVoiceId ?: "kh_male_hero")
                val currentSettings = repository.getCurrentSettings()
                val emotion = runCatching { EmotionType.valueOf(segment.emotion) }.getOrDefault(EmotionType.NEUTRAL)

                val projectDir = File(proj.localVideoPath).parentFile ?: getApplication<Application>().filesDir
                val outWav = File(File(projectDir, "tts").apply { mkdirs() }, "seg_${segment.id}.wav")

                val res = ttsEngine.synthesizeSegment(
                    text = segment.khmerText.ifBlank { segment.originalText },
                    voicePreset = preset,
                    emotion = emotion,
                    pitchOffset = charEntity?.pitchOffset ?: 1.0f,
                    speedMultiplier = charEntity?.speedMultiplier ?: 1.0f,
                    targetDurationMs = (segment.endMs - segment.startMs).coerceAtLeast(500L),
                    autoAdjustSpeed = currentSettings.autoAdjustSpeed,
                    engineMode = currentSettings.ttsEngineMode,
                    ttsModelId = currentSettings.ttsModel,
                    maxRetries = currentSettings.maxRetries,
                    outputWavFile = outWav
                )
                repository.updateSegment(
                    segment.copy(
                        ttsAudioPath = res.wavFile.absolutePath,
                        ttsDurationMs = res.durationMs,
                        speedRatio = res.appliedSpeedRatio
                    )
                )
                playWavFile(res.wavFile, "seg_${segment.id}", res.engineUsed)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _auditionState.value = AuditionUiState.Error(e.localizedMessage ?: "Voice synthesis failed")
            }
        }
    }

    fun auditionVoicePreset(
        preset: KhmerVoicePreset,
        sampleKhmerText: String,
        emotion: EmotionType
    ) {
        viewModelScope.launch {
            _auditionState.value = AuditionUiState.Synthesizing(preset.id)
            try {
                val currentSettings = repository.getCurrentSettings()
                val cacheDir = File(getApplication<Application>().cacheDir, "auditions").apply { mkdirs() }
                val outWav = File(cacheDir, "${preset.id}_${emotion.name}.wav")
                val res = ttsEngine.synthesizeSegment(
                    text = sampleKhmerText,
                    voicePreset = preset,
                    emotion = emotion,
                    pitchOffset = 1.0f,
                    speedMultiplier = 1.0f,
                    targetDurationMs = 0L,
                    autoAdjustSpeed = false,
                    engineMode = currentSettings.ttsEngineMode,
                    ttsModelId = currentSettings.ttsModel,
                    maxRetries = currentSettings.maxRetries,
                    outputWavFile = outWav
                )
                playWavFile(res.wavFile, preset.id, res.engineUsed)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _auditionState.value = AuditionUiState.Error(e.localizedMessage ?: "Audition failed")
            }
        }
    }

    fun playExistingSegmentAudio(segment: DialogueSegmentEntity) {
        val path = segment.ttsAudioPath
        if (path != null && File(path).exists()) {
            playWavFile(File(path), "seg_${segment.id}", "Cached Dub")
        } else {
            resynthesizeSegment(segment)
        }
    }

    private fun playWavFile(file: File, targetId: String, engineUsed: String) {
        stopAudioPreview()
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    _auditionState.value = AuditionUiState.Idle
                }
                setOnErrorListener { _, _, _ ->
                    _auditionState.value = AuditionUiState.Error("Playback failed")
                    true
                }
                prepare()
                start()
            }
            _auditionState.value = AuditionUiState.Playing(targetId, engineUsed)
        } catch (e: Exception) {
            _auditionState.value = AuditionUiState.Error(e.localizedMessage ?: "Unable to play audio")
        }
    }

    fun stopAudioPreview() {
        runCatching {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        }
        mediaPlayer = null
        if (_auditionState.value is AuditionUiState.Playing) {
            _auditionState.value = AuditionUiState.Idle
        }
    }

    fun nudgeSegmentTiming(segment: DialogueSegmentEntity, deltaMs: Long) {
        viewModelScope.launch {
            val newOffset = (segment.syncOffsetMs + deltaMs).coerceIn(-1500L, 1500L)
            repository.updateSegment(segment.copy(syncOffsetMs = newOffset))
        }
    }

    fun rebuildSynchronizedMix(
        projectId: Long,
        duckingDb: Int,
        originalBaseGain: Float,
        dubbedGain: Float
    ) {
        viewModelScope.launch {
            _syncActionState.value = ActionUiState.Loading("Mixing Khmer voices & ducking background audio...")
            try {
                val proj = repository.getProject(projectId) ?: return@launch
                val extractedPath = proj.extractedAudioPath
                if (extractedPath.isNullOrBlank() || !File(extractedPath).exists()) {
                    _syncActionState.value = ActionUiState.Error("Original extracted audio not found. Run AI Analysis first.")
                    return@launch
                }
                val segments = repository.getSegments(projectId)
                val projectDir = File(proj.localVideoPath).parentFile ?: getApplication<Application>().filesDir
                val mixedOut = File(projectDir, "mixed_dubbed.wav")

                AudioSynchronizer.mixSynchronizedTrack(
                    originalAudioWav = File(extractedPath),
                    segments = segments,
                    duckingDb = duckingDb,
                    originalBaseGain = originalBaseGain,
                    dubbedGain = dubbedGain,
                    outputMixedWav = mixedOut
                )
                repository.updateProject(
                    proj.copy(
                        duckingDb = duckingDb,
                        originalAudioGain = originalBaseGain,
                        dubbedAudioGain = dubbedGain,
                        mixedAudioPath = mixedOut.absolutePath,
                        stage = PipelineStage.READY.name,
                        statusMessage = "Synchronized mix updated"
                    )
                )
                _syncActionState.value = ActionUiState.Success("Synchronized audio track rebuilt")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _syncActionState.value = ActionUiState.Error(e.localizedMessage ?: "Audio sync mix failed")
            }
        }
    }

    fun exportProjectMp4(
        projectId: Long,
        resolution: ExportResolution,
        audioMode: AudioTrackMode,
        subtitleMode: SubtitleExportMode
    ) {
        viewModelScope.launch {
            _exportState.value = ExportUiState.Exporting(2, "Preparing Media3 composition...")
            try {
                val proj = repository.getProject(projectId)
                    ?: throw IllegalStateException("Project not found")
                val sourceVideo = File(proj.localVideoPath)
                if (!sourceVideo.exists()) {
                    throw IllegalStateException("Source video file is missing")
                }
                val segments = repository.getSegments(projectId)
                val mixedWav = proj.mixedAudioPath?.let(::File)?.takeIf { it.exists() }
                val exportDir = File(getApplication<Application>().filesDir, "exports")

                val result = VideoExporter.exportDubbedMp4(
                    context = getApplication(),
                    sourceVideoFile = sourceVideo,
                    mixedAudioWavFile = mixedWav,
                    segments = segments,
                    outputTitle = proj.title,
                    resolution = resolution,
                    audioMode = audioMode,
                    subtitleMode = subtitleMode,
                    outputDir = exportDir,
                    onProgress = { pct ->
                        _exportState.value = ExportUiState.Exporting(
                            progressPercent = pct,
                            stageLabel = if (pct < 95) "Encoding MP4 (${resolution.label})..." else "Saving to device Gallery..."
                        )
                    }
                )

                repository.updateProject(
                    proj.copy(
                        exportedVideoPath = result.outputFile.absolutePath,
                        subtitleSrtPath = result.subtitleFile?.absolutePath,
                        stage = PipelineStage.EXPORTED.name,
                        statusMessage = "Exported ${resolution.label} MP4"
                    )
                )
                repository.addExportHistory(
                    ExportHistoryEntity(
                        projectId = projectId,
                        projectTitle = proj.title,
                        outputPath = result.outputFile.absolutePath,
                        mediaStoreUri = result.mediaStoreUri?.toString(),
                        subtitlePath = result.subtitleFile?.absolutePath,
                        resolutionLabel = resolution.label,
                        audioModeLabel = audioMode.label,
                        subtitleModeLabel = subtitleMode.label,
                        fileSizeBytes = result.fileSizeBytes,
                        durationMs = proj.durationMs
                    )
                )
                _exportState.value = ExportUiState.Success(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _exportState.value = ExportUiState.Error(e.localizedMessage ?: "MP4 export failed")
            }
        }
    }

    fun addApiKey(label: String, rawKey: String, priority: Int, validateFirst: Boolean) {
        viewModelScope.launch {
            val clean = rawKey.trim()
            if (clean.length < 16) {
                _keyTestState.value = ActionUiState.Error("Please enter a valid Gemini API key.")
                return@launch
            }
            if (validateFirst) {
                _keyTestState.value = ActionUiState.Loading("Validating key with Gemini API...")
                val model = repository.getCurrentSettings().primaryModel
                val check = geminiClient.validateApiKey(clean, model)
                if (check.isFailure) {
                    _keyTestState.value = ActionUiState.Error(
                        check.exceptionOrNull()?.localizedMessage ?: "Key validation failed"
                    )
                    return@launch
                }
            }
            repository.addApiKey(label, clean, priority)
            _keyTestState.value = ActionUiState.Success("API key encrypted in Android Keystore & added")
        }
    }

    fun updateApiKey(id: Long, label: String, newRawKey: String?, priority: Int, isEnabled: Boolean) {
        viewModelScope.launch {
            repository.updateApiKey(id, label, newRawKey, priority, isEnabled)
            _snackbar.emit("API key updated")
        }
    }

    fun testExistingKey(keyEntity: ApiKeyEntity) {
        viewModelScope.launch {
            _keyTestState.value = ActionUiState.Loading("Testing '${keyEntity.label}'...")
            val decrypted = runCatching { crypto.decrypt(keyEntity.encryptedKey, keyEntity.iv) }.getOrNull()
            if (decrypted == null) {
                _keyTestState.value = ActionUiState.Error("Keystore decryption failed for '${keyEntity.label}'")
                return@launch
            }
            val model = repository.getCurrentSettings().primaryModel
            val res = geminiClient.validateApiKey(decrypted, model)
            if (res.isSuccess) {
                repository.resetKeyStatus(keyEntity.id)
                _keyTestState.value = ActionUiState.Success("'${keyEntity.label}' verified active on $model")
            } else {
                val msg = res.exceptionOrNull()?.localizedMessage ?: "Validation failed"
                repository.recordKeyFailure(keyEntity.id, KeyStatus.ERROR, msg)
                _keyTestState.value = ActionUiState.Error(msg)
            }
        }
    }

    fun resetKeyStatus(id: Long) {
        viewModelScope.launch {
            repository.resetKeyStatus(id)
            _snackbar.emit("Key status reset to Active")
        }
    }

    fun deleteApiKey(id: Long) {
        viewModelScope.launch {
            repository.deleteApiKey(id)
            _snackbar.emit("API key removed")
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            repository.updateSettings(transform)
        }
    }

    fun deleteExportHistory(id: Long) {
        viewModelScope.launch {
            repository.deleteExportHistory(id)
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopAudioPreview()
    }
}
