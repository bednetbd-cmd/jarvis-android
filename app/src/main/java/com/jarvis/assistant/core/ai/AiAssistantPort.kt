package com.jarvis.assistant.core.ai

import com.jarvis.assistant.domain.model.ChatMessage

/**
 * Contract for future AI reasoning integration (e.g., on-device or free-tier model provider).
 * Currently unimplemented in the foundation phase.
 */
interface AiAssistantPort {
    val isConfigured: Boolean

    suspend fun generateReply(
        prompt: String,
        history: List<ChatMessage>
    ): Result<String>
}

class UnimplementedAiAssistant : AiAssistantPort {
    override val isConfigured: Boolean = false

    override suspend fun generateReply(
        prompt: String,
        history: List<ChatMessage>
    ): Result<String> {
        return Result.failure(
            UnsupportedOperationException(
                "AI engine is not connected yet. This is the UI & architecture foundation."
            )
        )
    }
}
