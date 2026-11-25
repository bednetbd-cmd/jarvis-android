package com.jarvis.assistant.ui.screens

import com.jarvis.assistant.core.security.BiometricCapability
import com.jarvis.assistant.core.security.PendingSensitiveAction
import com.jarvis.assistant.core.tools.ContextualSuggestion
import com.jarvis.assistant.core.voice.VoiceDiagnostics
import com.jarvis.assistant.core.voice.VoiceMonitorStatus
import com.jarvis.assistant.domain.model.AssistantVisualState
import com.jarvis.assistant.domain.model.ChatMessage
import com.jarvis.assistant.domain.model.DeviceTelemetry
import com.jarvis.assistant.domain.model.JarvisPreferences

enum class WorkbenchTab(val label: String) {
    CALCULATOR("Calculator"),
    FILES("Files (SAF)"),
    PDF("PDF Reader"),
    CLIPBOARD_BATCH("Clipboard & Batch")
}

data class MainUiState(
    val visualState: AssistantVisualState = AssistantVisualState.STANDBY,
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val memorySearchQuery: String = "",
    val isListening: Boolean = false,
    val isSpeaking: Boolean = false,
    val rmsLevel: Float = 0f,
    val partialTranscript: String = "",
    val preferences: JarvisPreferences = JarvisPreferences(),
    val telemetry: DeviceTelemetry = DeviceTelemetry(),
    val isAiConnected: Boolean = true,
    val isVoiceAvailable: Boolean = false,
    val voiceDiagnostics: VoiceDiagnostics = VoiceDiagnostics(),
    val voiceMonitorStatus: VoiceMonitorStatus = VoiceMonitorStatus(),
    val aiEngineLabel: String = "On-Device Engine",
    val isTestingAi: Boolean = false,
    val aiTestStatus: String? = null,
    val aiTestSuccess: Boolean? = null,
    val isAppLocked: Boolean = false,
    val biometricCapability: BiometricCapability = BiometricCapability.UNAVAILABLE,
    val pendingSensitiveAction: PendingSensitiveAction? = null,
    val contextualSuggestions: List<ContextualSuggestion> = emptyList()
) {
    val isTtsEnabled: Boolean
        get() = preferences.ttsEnabled

    val providerBadgeLabel: String
        get() = preferences.aiProviderConfig.activeProvider.badgeLabel

    val filteredMessages: List<ChatMessage>
        get() {
            val query = memorySearchQuery.trim()
            if (query.isEmpty()) return messages
            return messages.filter { it.content.contains(query, ignoreCase = true) }
        }
}
