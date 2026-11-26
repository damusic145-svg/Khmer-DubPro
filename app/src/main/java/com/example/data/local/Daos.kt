package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ApiKeyDao {
    @Query("SELECT * FROM api_keys ORDER BY priorityOrder ASC, createdAt ASC")
    fun observeAllKeys(): Flow<List<ApiKeyEntity>>

    @Query("SELECT * FROM api_keys WHERE isEnabled = 1 ORDER BY priorityOrder ASC, createdAt ASC")
    suspend fun getEnabledKeys(): List<ApiKeyEntity>

    @Query("SELECT * FROM api_keys WHERE id = :id")
    suspend fun getKeyById(id: Long): ApiKeyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKey(key: ApiKeyEntity): Long

    @Update
    suspend fun updateKey(key: ApiKeyEntity)

    @Query("DELETE FROM api_keys WHERE id = :id")
    suspend fun deleteKeyById(id: Long)
}

@Dao
interface ProjectDao {
    @Query("SELECT * FROM dubbing_projects ORDER BY updatedAt DESC")
    fun observeAllProjects(): Flow<List<DubbingProjectEntity>>

    @Query("SELECT * FROM dubbing_projects WHERE id = :projectId")
    fun observeProject(projectId: Long): Flow<DubbingProjectEntity?>

    @Query("SELECT * FROM dubbing_projects WHERE id = :projectId")
    suspend fun getProjectById(projectId: Long): DubbingProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: DubbingProjectEntity): Long

    @Update
    suspend fun updateProject(project: DubbingProjectEntity)

    @Query(
        "UPDATE dubbing_projects SET stage = :stage, progressPercent = :progress, statusMessage = :message, errorDetail = :errorDetail, updatedAt = :updatedAt WHERE id = :projectId"
    )
    suspend fun updateStage(
        projectId: Long,
        stage: String,
        progress: Int,
        message: String,
        errorDetail: String? = null,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("DELETE FROM dubbing_projects WHERE id = :projectId")
    suspend fun deleteProjectById(projectId: Long)
}

@Dao
interface CharacterDao {
    @Query("SELECT * FROM character_voices WHERE projectId = :projectId ORDER BY id ASC")
    fun observeCharactersForProject(projectId: Long): Flow<List<CharacterVoiceEntity>>

    @Query("SELECT * FROM character_voices WHERE projectId = :projectId ORDER BY id ASC")
    suspend fun getCharactersForProject(projectId: Long): List<CharacterVoiceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCharacters(characters: List<CharacterVoiceEntity>): List<Long>

    @Update
    suspend fun updateCharacter(character: CharacterVoiceEntity)

    @Query("DELETE FROM character_voices WHERE projectId = :projectId")
    suspend fun deleteCharactersForProject(projectId: Long)
}

@Dao
interface SegmentDao {
    @Query("SELECT * FROM dialogue_segments WHERE projectId = :projectId ORDER BY startMs ASC, segmentIndex ASC")
    fun observeSegmentsForProject(projectId: Long): Flow<List<DialogueSegmentEntity>>

    @Query("SELECT * FROM dialogue_segments WHERE projectId = :projectId ORDER BY startMs ASC, segmentIndex ASC")
    suspend fun getSegmentsForProject(projectId: Long): List<DialogueSegmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegments(segments: List<DialogueSegmentEntity>)

    @Update
    suspend fun updateSegment(segment: DialogueSegmentEntity)

    @Query("DELETE FROM dialogue_segments WHERE projectId = :projectId")
    suspend fun deleteSegmentsForProject(projectId: Long)
}

@Dao
interface ExportHistoryDao {
    @Query("SELECT * FROM export_history ORDER BY createdAt DESC")
    fun observeExports(): Flow<List<ExportHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExport(export: ExportHistoryEntity): Long

    @Query("DELETE FROM export_history WHERE id = :id")
    suspend fun deleteExportById(id: Long)

    @Query("DELETE FROM export_history")
    suspend fun clearAllExports()
}
