package com.jarvis.assistant.ui.screens

import com.jarvis.assistant.domain.model.AssistantVisualState
import com.jarvis.assistant.domain.model.ChatMessage

data class MainUiState(
    val visualState: AssistantVisualState = AssistantVisualState.STANDBY,
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isListening: Boolean = false,
    val isTtsEnabled: Boolean = true,
    val isAiConnected: Boolean = true,
    val isVoiceAvailable: Boolean = false,
    val aiEngineLabel: String = "On-Device Assistant"
)
