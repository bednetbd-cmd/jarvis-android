package com.jarvis.assistant.domain.model

enum class JarvisAccentTheme(
    val displayName: String,
    val primaryHex: Long,
    val secondaryHex: Long,
    val glowHex: Long
) {
    ARC_CYAN(
        displayName = "Arc Cyan",
        primaryHex = 0xFF00E5FF,
        secondaryHex = 0xFF38BDF8,
        glowHex = 0xFF40EEFF
    ),
    COBALT_BLUE(
        displayName = "Cobalt HUD",
        primaryHex = 0xFF3B82F6,
        secondaryHex = 0xFF60A5FA,
        glowHex = 0xFF93C5FD
    ),
    STARK_GOLD(
        displayName = "Mark Gold",
        primaryHex = 0xFFF59E0B,
        secondaryHex = 0xFFFBBF24,
        glowHex = 0xFFFDE68A
    ),
    EMERALD_HUD(
        displayName = "Tactical Emerald",
        primaryHex = 0xFF10B981,
        secondaryHex = 0xFF34D399,
        glowHex = 0xFF6EE7B7
    )
}

data class JarvisPreferences(
    val ttsEnabled: Boolean = true,
    val speechRate: Float = 1.02f,
    val speechPitch: Float = 1.0f,
    val speechLanguageTag: String = "en-US",
    val preferOfflineVoice: Boolean = false,
    val accentTheme: JarvisAccentTheme = JarvisAccentTheme.ARC_CYAN,
    val showWaveform: Boolean = true,
    val biometricLockEnabled: Boolean = false,
    val confirmSensitiveActions: Boolean = true,
    val encryptConversationHistory: Boolean = true,
    val aiProviderConfig: AiProviderConfig = AiProviderConfig()
) {
    companion object {
        fun clampSpeechRate(value: Float): Float = value.coerceIn(0.6f, 1.8f)
        fun clampSpeechPitch(value: Float): Float = value.coerceIn(0.7f, 1.5f)
    }
}
