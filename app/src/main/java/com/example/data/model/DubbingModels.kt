package com.example.data.model

enum class PipelineStage(val stepIndex: Int, val label: String) {
    IMPORTED(0, "Imported"),
    EXTRACTING_AUDIO(1, "Extracting Audio"),
    DETECTING_LANGUAGE(2, "Language & ASR"),
    DIARIZING_SPEAKERS(3, "Character Voices"),
    TRANSLATING_KHMER(4, "Khmer Translation"),
    SYNTHESIZING_TTS(5, "AI Voice Gen"),
    SYNCHRONIZING_AUDIO(6, "Audio Sync"),
    READY(7, "Ready to Preview"),
    EXPORTED(8, "Exported MP4"),
    FAILED(-1, "Needs Attention")
}

enum class KeyStatus(val displayName: String) {
    ACTIVE("Active"),
    RATE_LIMITED("Rate Limited"),
    QUOTA_EXCEEDED("Quota Exceeded"),
    ERROR("Auth / Error"),
    DISABLED("Disabled")
}

enum class SpeakerGender(val label: String) {
    MALE("Male"),
    FEMALE("Female"),
    NON_BINARY("Neutral")
}

enum class AgeGroup(val label: String) {
    CHILD("Child"),
    YOUNG_ADULT("Young Adult"),
    ADULT("Adult"),
    ELDERLY("Elderly")
}

enum class EmotionType(val label: String, val khmerHint: String) {
    NEUTRAL("Neutral", "ធម្មតា"),
    HAPPY("Happy", "រីករាយ"),
    SAD("Sad", "សោកសៅ"),
    ANGRY("Angry", "ខឹងសម្បា"),
    EXCITED("Excited", "រំភើប"),
    FEAR("Fear", "ភ័យខ្លាច"),
    SURPRISE("Surprise", "ភ្ញាក់ផ្អើល")
}

enum class ExportResolution(val label: String, val targetHeight: Int?) {
    ORIGINAL("Original", null),
    RES_720P("720p HD", 720),
    RES_1080P("1080p Full HD", 1080),
    RES_4K("4K Ultra HD", 2160)
}

enum class AudioTrackMode(val label: String) {
    KHMER_DUBBED("Khmer Dubbed Mix"),
    ORIGINAL_ONLY("Original Audio"),
    BILINGUAL_DUAL("Dual Mix (Khmer + Orig)")
}

enum class SubtitleExportMode(val label: String) {
    NONE("No Subtitles"),
    KHMER_SRT("Khmer (.srt)"),
    BILINGUAL_SRT("Bilingual Khmer + Orig (.srt)")
}

enum class ThemePreference(val label: String) {
    DARK_STUDIO("Dark Studio"),
    LIGHT_STUDIO("Light Studio"),
    SYSTEM("System Default")
}

enum class TtsEngineMode(val label: String) {
    GEMINI_AI_WITH_FALLBACK("Gemini AI Voice + On-Device Fallback"),
    ON_DEVICE_KHMER_TTS("On-Device Android Khmer TTS")
}

data class KhmerVoicePreset(
    val id: String,
    val displayName: String,
    val khmerName: String,
    val geminiVoiceName: String,
    val gender: SpeakerGender,
    val ageGroup: AgeGroup,
    val style: String,
    val basePitch: Float,
    val baseRate: Float
)

data class AppSettings(
    val primaryModel: String = "gemini-3.8-flash",
    val ttsModel: String = "gemini-3.1-flash-tts-preview",
    val ttsEngineMode: TtsEngineMode = TtsEngineMode.GEMINI_AI_WITH_FALLBACK,
    val defaultDuckingDb: Int = -18,
    val chunkDurationSeconds: Int = 180,
    val maxRetries: Int = 3,
    val autoAdjustSpeed: Boolean = true,
    val defaultExportResolution: ExportResolution = ExportResolution.RES_1080P,
    val themePreference: ThemePreference = ThemePreference.DARK_STUDIO
)

data class PipelineLogEntry(
    val timestampMs: Long,
    val stage: String,
    val message: String,
    val isError: Boolean = false
)
