package com.jarvis.assistant.domain.model

enum class AssistantVisualState(val label: String) {
    STANDBY("ONLINE · ON-DEVICE"),
    LISTENING("LISTENING..."),
    THINKING("PROCESSING..."),
    SPEAKING("SPEAKING...")
}
