package com.example.data.repository

import com.example.data.local.ApiKeyDao
import com.example.data.local.ApiKeyEntity
import com.example.data.local.AppDatabase
import com.example.data.local.CharacterVoiceEntity
import com.example.data.local.DialogueSegmentEntity
import com.example.data.local.DubbingProjectEntity
import com.example.data.local.ExportHistoryEntity
import com.example.data.local.SettingsDataStore
import com.example.data.model.AgeGroup
import com.example.data.model.AppSettings
import com.example.data.model.KeyStatus
import com.example.data.model.KhmerVoicePreset
import com.example.data.model.SpeakerGender
import com.example.security.KeystoreCryptoManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

object KhmerVoiceCatalog {
    val presets: List<KhmerVoicePreset> = listOf(
        KhmerVoicePreset(
            id = "kh_male_hero",
            displayName = "Dara (Male Lead)",
            khmerName = "តារា — បុរសវ័យកណ្ដាល",
            geminiVoiceName = "Charon",
            gender = SpeakerGender.MALE,
            ageGroup = AgeGroup.ADULT,
            style = "Warm, resonant, cinematic lead",
            basePitch = 0.94f,
            baseRate = 1.0f
        ),
        KhmerVoicePreset(
            id = "kh_male_youth",
            displayName = "Vannak (Young Male)",
            khmerName = "វណ្ណៈ — យុវជន",
            geminiVoiceName = "Puck",
            gender = SpeakerGender.MALE,
            ageGroup = AgeGroup.YOUNG_ADULT,
            style = "Energetic, expressive, crisp",
            basePitch = 1.06f,
            baseRate = 1.04f
        ),
        KhmerVoicePreset(
            id = "kh_male_elder",
            displayName = "Sokhom (Elder Male)",
            khmerName = "សុខុម — វ័យចំណាស់",
            geminiVoiceName = "Orus",
            gender = SpeakerGender.MALE,
            ageGroup = AgeGroup.ELDERLY,
            style = "Deep, calm, authoritative",
            basePitch = 0.80f,
            baseRate = 0.90f
        ),
        KhmerVoicePreset(
            id = "kh_female_lead",
            displayName = "Bopha (Female Lead)",
            khmerName = "បុប្ផា — ស្ត្រីវ័យកណ្ដាល",
            geminiVoiceName = "Kore",
            gender = SpeakerGender.FEMALE,
            ageGroup = AgeGroup.ADULT,
            style = "Natural, emotional, clear",
            basePitch = 1.08f,
            baseRate = 1.0f
        ),
        KhmerVoicePreset(
            id = "kh_female_youth",
            displayName = "Sreyneang (Young Female)",
            khmerName = "ស្រីនាង — យុវតី",
            geminiVoiceName = "Aoede",
            gender = SpeakerGender.FEMALE,
            ageGroup = AgeGroup.YOUNG_ADULT,
            style = "Bright, melodic, lively",
            basePitch = 1.18f,
            baseRate = 1.05f
        ),
        KhmerVoicePreset(
            id = "kh_female_elder",
            displayName = "Malis (Elder Female)",
            khmerName = "ម្លិះ — ស្ត្រីវ័យចំណាស់",
            geminiVoiceName = "Leda",
            gender = SpeakerGender.FEMALE,
            ageGroup = AgeGroup.ELDERLY,
            style = "Gentle, wise, storytelling",
            basePitch = 0.96f,
            baseRate = 0.90f
        ),
        KhmerVoicePreset(
            id = "kh_child_bright",
            displayName = "Kosal (Child Voice)",
            khmerName = "កុសល — កុមារ",
            geminiVoiceName = "Zephyr",
            gender = SpeakerGender.NON_BINARY,
            ageGroup = AgeGroup.CHILD,
            style = "Playful, light, youthful",
            basePitch = 1.32f,
            baseRate = 1.06f
        ),
        KhmerVoicePreset(
            id = "kh_narrator_doc",
            displayName = "Rithy (Narrator)",
            khmerName = "រិទ្ធី — អ្នកអត្ថាធិប្បាយ",
            geminiVoiceName = "Fenrir",
            gender = SpeakerGender.MALE,
            ageGroup = AgeGroup.ADULT,
            style = "Balanced documentary & news",
            basePitch = 0.90f,
            baseRate = 0.97f
        )
    )

    fun findById(id: String): KhmerVoicePreset =
        presets.firstOrNull { it.id == id } ?: presets.first()

    fun matchVoice(gender: SpeakerGender, ageGroup: AgeGroup): KhmerVoicePreset {
        if (ageGroup == AgeGroup.CHILD) return findById("kh_child_bright")
        return presets.firstOrNull { it.gender == gender && it.ageGroup == ageGroup }
            ?: presets.firstOrNull { it.gender == gender }
            ?: presets.first()
    }
}

class DubbingRepository(
    private val db: AppDatabase,
    private val crypto: KeystoreCryptoManager,
    private val settingsDataStore: SettingsDataStore
) {
    val allKeys: Flow<List<ApiKeyEntity>> = db.apiKeyDao().observeAllKeys()
    val allProjects: Flow<List<DubbingProjectEntity>> = db.projectDao().observeAllProjects()
    val exportHistory: Flow<List<ExportHistoryEntity>> = db.exportHistoryDao().observeExports()
    val settings: Flow<AppSettings> = settingsDataStore.settingsFlow

    fun observeProject(projectId: Long): Flow<DubbingProjectEntity?> =
        db.projectDao().observeProject(projectId)

    fun observeCharacters(projectId: Long): Flow<List<CharacterVoiceEntity>> =
        db.characterDao().observeCharactersForProject(projectId)

    fun observeSegments(projectId: Long): Flow<List<DialogueSegmentEntity>> =
        db.segmentDao().observeSegmentsForProject(projectId)

    suspend fun getProject(projectId: Long): DubbingProjectEntity? =
        db.projectDao().getProjectById(projectId)

    suspend fun getCharacters(projectId: Long): List<CharacterVoiceEntity> =
        db.characterDao().getCharactersForProject(projectId)

    suspend fun getSegments(projectId: Long): List<DialogueSegmentEntity> =
        db.segmentDao().getSegmentsForProject(projectId)

    suspend fun getCurrentSettings(): AppSettings = settingsDataStore.settingsFlow.first()

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) =
        settingsDataStore.updateSettings(transform)

    suspend fun addApiKey(label: String, rawKey: String, priority: Int): Long {
        val cleanKey = rawKey.trim()
        val encrypted = crypto.encrypt(cleanKey)
        return db.apiKeyDao().insertKey(
            ApiKeyEntity(
                label = label.trim().ifEmpty { "Gemini Key" },
                encryptedKey = encrypted.ciphertextBase64,
                iv = encrypted.ivBase64,
                maskedPreview = crypto.maskKey(cleanKey),
                status = KeyStatus.ACTIVE.name,
                priorityOrder = priority,
                isEnabled = true
            )
        )
    }

    suspend fun updateApiKey(id: Long, label: String, newRawKey: String?, priority: Int, isEnabled: Boolean) {
        val existing = db.apiKeyDao().getKeyById(id) ?: return
        val updated = if (!newRawKey.isNullOrBlank()) {
            val clean = newRawKey.trim()
            val enc = crypto.encrypt(clean)
            existing.copy(
                label = label.trim().ifEmpty { existing.label },
                encryptedKey = enc.ciphertextBase64,
                iv = enc.ivBase64,
                maskedPreview = crypto.maskKey(clean),
                priorityOrder = priority,
                isEnabled = isEnabled,
                status = if (isEnabled) KeyStatus.ACTIVE.name else KeyStatus.DISABLED.name,
                lastError = null
            )
        } else {
            existing.copy(
                label = label.trim().ifEmpty { existing.label },
                priorityOrder = priority,
                isEnabled = isEnabled,
                status = if (isEnabled) {
                    if (existing.status == KeyStatus.DISABLED.name) KeyStatus.ACTIVE.name else existing.status
                } else {
                    KeyStatus.DISABLED.name
                }
            )
        }
        db.apiKeyDao().updateKey(updated)
    }

    suspend fun resetKeyStatus(id: Long) {
        val existing = db.apiKeyDao().getKeyById(id) ?: return
        db.apiKeyDao().updateKey(
            existing.copy(
                status = KeyStatus.ACTIVE.name,
                isEnabled = true,
                failureCount = 0,
                lastError = null
            )
        )
    }

    suspend fun deleteApiKey(id: Long) = db.apiKeyDao().deleteKeyById(id)

    suspend fun getDecryptedCandidateKeys(): List<Pair<ApiKeyEntity, String>> {
        val enabled = db.apiKeyDao().getEnabledKeys()
        val ordered = enabled.sortedWith(
            compareBy<ApiKeyEntity>(
                {
                    when (it.status) {
                        KeyStatus.ACTIVE.name -> 0
                        KeyStatus.RATE_LIMITED.name -> 1
                        KeyStatus.ERROR.name -> 2
                        KeyStatus.QUOTA_EXCEEDED.name -> 3
                        else -> 4
                    }
                },
                { it.priorityOrder },
                { it.lastUsedAt ?: 0L }
            )
        )
        return ordered.mapNotNull { entity ->
            runCatching {
                entity to crypto.decrypt(entity.encryptedKey, entity.iv)
            }.getOrNull()
        }
    }

    suspend fun recordKeySuccess(keyId: Long, tokensUsed: Long) {
        val existing = db.apiKeyDao().getKeyById(keyId) ?: return
        db.apiKeyDao().updateKey(
            existing.copy(
                status = KeyStatus.ACTIVE.name,
                requestsCount = existing.requestsCount + 1,
                tokensUsed = existing.tokensUsed + tokensUsed,
                lastUsedAt = System.currentTimeMillis(),
                lastError = null
            )
        )
    }

    suspend fun recordKeyFailure(keyId: Long, status: KeyStatus, errorMessage: String) {
        val existing = db.apiKeyDao().getKeyById(keyId) ?: return
        db.apiKeyDao().updateKey(
            existing.copy(
                status = status.name,
                failureCount = existing.failureCount + 1,
                lastUsedAt = System.currentTimeMillis(),
                lastError = errorMessage.take(240)
            )
        )
    }

    suspend fun insertProject(project: DubbingProjectEntity): Long =
        db.projectDao().insertProject(project)

    suspend fun updateProject(project: DubbingProjectEntity) =
        db.projectDao().updateProject(project.copy(updatedAt = System.currentTimeMillis()))

    suspend fun updateProjectStage(
        projectId: Long,
        stage: com.example.data.model.PipelineStage,
        progress: Int,
        message: String,
        errorDetail: String? = null
    ) {
        db.projectDao().updateStage(
            projectId = projectId,
            stage = stage.name,
            progress = progress.coerceIn(0, 100),
            message = message,
            errorDetail = errorDetail
        )
    }

    suspend fun deleteProject(projectId: Long) = db.projectDao().deleteProjectById(projectId)

    suspend fun replaceProjectAnalysis(
        projectId: Long,
        characters: List<CharacterVoiceEntity>,
        segments: List<DialogueSegmentEntity>
    ): List<CharacterVoiceEntity> {
        db.characterDao().deleteCharactersForProject(projectId)
        db.segmentDao().deleteSegmentsForProject(projectId)
        val insertedCharIds = db.characterDao().insertCharacters(characters)
        val tagToId = characters.zip(insertedCharIds).associate { (ch, id) -> ch.speakerTag to id }
        val resolvedSegments = segments.map { seg ->
            seg.copy(characterId = tagToId[seg.speakerTag] ?: insertedCharIds.firstOrNull() ?: 0L)
        }
        db.segmentDao().insertSegments(resolvedSegments)
        return db.characterDao().getCharactersForProject(projectId)
    }

    suspend fun updateCharacter(character: CharacterVoiceEntity) =
        db.characterDao().updateCharacter(character)

    suspend fun updateSegment(segment: DialogueSegmentEntity) =
        db.segmentDao().updateSegment(segment)

    suspend fun addExportHistory(export: ExportHistoryEntity): Long =
        db.exportHistoryDao().insertExport(export)

    suspend fun deleteExportHistory(id: Long) = db.exportHistoryDao().deleteExportById(id)

    suspend fun clearExportHistory() = db.exportHistoryDao().clearAllExports()
}
