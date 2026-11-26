package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.AppSettings
import com.example.data.model.ExportResolution
import com.example.data.model.ThemePreference
import com.example.data.model.TtsEngineMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "dubber_settings")

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val PRIMARY_MODEL = stringPreferencesKey("primary_model")
        val TTS_MODEL = stringPreferencesKey("tts_model")
        val TTS_ENGINE_MODE = stringPreferencesKey("tts_engine_mode")
        val DEFAULT_DUCKING_DB = intPreferencesKey("default_ducking_db")
        val CHUNK_DURATION_SEC = intPreferencesKey("chunk_duration_sec")
        val MAX_RETRIES = intPreferencesKey("max_retries")
        val AUTO_ADJUST_SPEED = booleanPreferencesKey("auto_adjust_speed")
        val DEFAULT_EXPORT_RES = stringPreferencesKey("default_export_res")
        val THEME_PREFERENCE = stringPreferencesKey("theme_preference")
    }

    val settingsFlow: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            primaryModel = prefs[Keys.PRIMARY_MODEL] ?: "gemini-3.8-flash",
            ttsModel = prefs[Keys.TTS_MODEL] ?: "gemini-3.1-flash-tts-preview",
            ttsEngineMode = prefs[Keys.TTS_ENGINE_MODE]
                ?.let { runCatching { TtsEngineMode.valueOf(it) }.getOrNull() }
                ?: TtsEngineMode.GEMINI_AI_WITH_FALLBACK,
            defaultDuckingDb = prefs[Keys.DEFAULT_DUCKING_DB] ?: -18,
            chunkDurationSeconds = prefs[Keys.CHUNK_DURATION_SEC] ?: 180,
            maxRetries = prefs[Keys.MAX_RETRIES] ?: 3,
            autoAdjustSpeed = prefs[Keys.AUTO_ADJUST_SPEED] ?: true,
            defaultExportResolution = prefs[Keys.DEFAULT_EXPORT_RES]
                ?.let { runCatching { ExportResolution.valueOf(it) }.getOrNull() }
                ?: ExportResolution.RES_1080P,
            themePreference = prefs[Keys.THEME_PREFERENCE]
                ?.let { runCatching { ThemePreference.valueOf(it) }.getOrNull() }
                ?: ThemePreference.DARK_STUDIO
        )
    }

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        context.settingsDataStore.edit { prefs ->
            val current = AppSettings(
                primaryModel = prefs[Keys.PRIMARY_MODEL] ?: "gemini-3.8-flash",
                ttsModel = prefs[Keys.TTS_MODEL] ?: "gemini-3.1-flash-tts-preview",
                ttsEngineMode = prefs[Keys.TTS_ENGINE_MODE]
                    ?.let { runCatching { TtsEngineMode.valueOf(it) }.getOrNull() }
                    ?: TtsEngineMode.GEMINI_AI_WITH_FALLBACK,
                defaultDuckingDb = prefs[Keys.DEFAULT_DUCKING_DB] ?: -18,
                chunkDurationSeconds = prefs[Keys.CHUNK_DURATION_SEC] ?: 180,
                maxRetries = prefs[Keys.MAX_RETRIES] ?: 3,
                autoAdjustSpeed = prefs[Keys.AUTO_ADJUST_SPEED] ?: true,
                defaultExportResolution = prefs[Keys.DEFAULT_EXPORT_RES]
                    ?.let { runCatching { ExportResolution.valueOf(it) }.getOrNull() }
                    ?: ExportResolution.RES_1080P,
                themePreference = prefs[Keys.THEME_PREFERENCE]
                    ?.let { runCatching { ThemePreference.valueOf(it) }.getOrNull() }
                    ?: ThemePreference.DARK_STUDIO
            )
            val updated = transform(current)
            prefs[Keys.PRIMARY_MODEL] = updated.primaryModel.trim().ifEmpty { "gemini-3.8-flash" }
            prefs[Keys.TTS_MODEL] = updated.ttsModel.trim().ifEmpty { "gemini-3.1-flash-tts-preview" }
            prefs[Keys.TTS_ENGINE_MODE] = updated.ttsEngineMode.name
            prefs[Keys.DEFAULT_DUCKING_DB] = updated.defaultDuckingDb
            prefs[Keys.CHUNK_DURATION_SEC] = updated.chunkDurationSeconds
            prefs[Keys.MAX_RETRIES] = updated.maxRetries
            prefs[Keys.AUTO_ADJUST_SPEED] = updated.autoAdjustSpeed
            prefs[Keys.DEFAULT_EXPORT_RES] = updated.defaultExportResolution.name
            prefs[Keys.THEME_PREFERENCE] = updated.themePreference.name
        }
    }
}
