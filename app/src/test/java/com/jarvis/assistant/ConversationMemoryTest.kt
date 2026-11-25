package com.jarvis.assistant

import com.jarvis.assistant.core.memory.InMemoryConversationStore
import com.jarvis.assistant.domain.model.ChatMessage
import com.jarvis.assistant.domain.model.MessageSender
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationMemoryTest {

    @Test
    fun appendAndClearMessagesWorkCorrectly() = runBlocking {
        val store = InMemoryConversationStore()
        assertEquals(0, store.messages.first().size)

        store.append(
            ChatMessage(
                sender = MessageSender.USER,
                content = "Calculate 12 * 4"
            )
        )
        assertEquals(1, store.messages.first().size)
        assertEquals("Calculate 12 * 4", store.messages.first().first().content)

        store.clear()
        assertEquals(0, store.messages.first().size)
    }
}
