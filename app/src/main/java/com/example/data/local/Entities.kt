package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.model.AgeGroup
import com.example.data.model.EmotionType
import com.example.data.model.KeyStatus
import com.example.data.model.PipelineStage
import com.example.data.model.SpeakerGender

@Entity(tableName = "api_keys")
data class ApiKeyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val encryptedKey: String,
    val iv: String,
    val maskedPreview: String,
    val status: String = KeyStatus.ACTIVE.name,
    val requestsCount: Int = 0,
    val tokensUsed: Long = 0L,
    val failureCount: Int = 0,
    val lastUsedAt: Long? = null,
    val lastError: String? = null,
    val priorityOrder: Int = 0,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "dubbing_projects")
data class DubbingProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val sourceUri: String,
    val localVideoPath: String,
    val thumbnailPath: String? = null,
    val extractedAudioPath: String? = null,
    val mixedAudioPath: String? = null,
    val exportedVideoPath: String? = null,
    val subtitleSrtPath: String? = null,
    val durationMs: Long = 0L,
    val videoWidth: Int = 1920,
    val videoHeight: Int = 1080,
    val fps: Float = 30f,
    val detectedLanguagesCsv: String = "",
    val hasEmbeddedSubtitles: Boolean = false,
    val storySummary: String = "",
    val stage: String = PipelineStage.IMPORTED.name,
    val progressPercent: Int = 0,
    val statusMessage: String = "Ready to analyze",
    val duckingDb: Int = -18,
    val originalAudioGain: Float = 0.15f,
    val dubbedAudioGain: Float = 1.0f,
    val waveformCsv: String = "",
    val errorDetail: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "character_voices",
    foreignKeys = [
        ForeignKey(
            entity = DubbingProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("projectId")]
)
data class CharacterVoiceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val speakerTag: String,
    val displayName: String,
    val gender: String = SpeakerGender.MALE.name,
    val ageGroup: String = AgeGroup.ADULT.name,
    val voiceStyle: String = "Natural",
    val dominantEmotion: String = EmotionType.NEUTRAL.name,
    val assignedVoiceId: String = "kh_male_narrator",
    val pitchOffset: Float = 1.0f,
    val speedMultiplier: Float = 1.0f,
    val colorIndex: Int = 0,
    val segmentCount: Int = 0
)

@Entity(
    tableName = "dialogue_segments",
    foreignKeys = [
        ForeignKey(
            entity = DubbingProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("projectId"), Index("characterId")]
)
data class DialogueSegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val characterId: Long,
    val speakerTag: String,
    val segmentIndex: Int,
    val startMs: Long,
    val endMs: Long,
    val originalLanguage: String,
    val originalText: String,
    val khmerText: String = "",
    val emotion: String = EmotionType.NEUTRAL.name,
    val speakingPace: Float = 1.0f,
    val ttsAudioPath: String? = null,
    val ttsDurationMs: Long = 0L,
    val syncOffsetMs: Long = 0L,
    val speedRatio: Float = 1.0f,
    val isManuallyEdited: Boolean = false
)

@Entity(tableName = "export_history")
data class ExportHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val projectTitle: String,
    val outputPath: String,
    val mediaStoreUri: String? = null,
    val subtitlePath: String? = null,
    val resolutionLabel: String,
    val audioModeLabel: String,
    val subtitleModeLabel: String,
    val fileSizeBytes: Long = 0L,
    val durationMs: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)
