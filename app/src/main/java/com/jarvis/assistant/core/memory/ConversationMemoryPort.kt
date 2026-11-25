package com.jarvis.assistant.core.memory

import com.jarvis.assistant.domain.model.ChatMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Contract for conversation persistence.
 * Uses volatile in-memory storage in the foundation phase; swap with Room or DataStore later.
 */
interface ConversationMemoryPort {
    val messages: Flow<List<ChatMessage>>
    suspend fun append(message: ChatMessage)
    suspend fun clear()
}

class InMemoryConversationStore(
    initialMessages: List<ChatMessage> = emptyList()
) : ConversationMemoryPort {
    private val _messages = MutableStateFlow(initialMessages)
    override val messages: Flow<List<ChatMessage>> = _messages.asStateFlow()

    override suspend fun append(message: ChatMessage) {
        _messages.update { current -> current + message }
    }

    override suspend fun clear() {
        _messages.value = emptyList()
    }
}
