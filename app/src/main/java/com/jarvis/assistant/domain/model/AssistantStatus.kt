package com.jarvis.assistant.domain.model

enum class AssistantVisualState(val label: String) {
    STANDBY("FOUNDATION · STANDBY"),
    LISTENING("VOICE MODULE · NOT IMPLEMENTED"),
    THINKING("AI MODULE · NOT IMPLEMENTED"),
    SPEAKING("TTS MODULE · NOT IMPLEMENTED")
}
