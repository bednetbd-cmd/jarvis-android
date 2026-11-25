package com.jarvis.assistant.domain.model

import java.util.UUID

enum class MessageSender {
    USER,
    ASSISTANT,
    SYSTEM_NOTICE
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: MessageSender,
    val content: String,
    val timestampMillis: Long = System.currentTimeMillis()
)
