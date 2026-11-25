package com.jarvis.assistant.core.memory

import android.content.Context
import com.jarvis.assistant.domain.model.ChatMessage
import com.jarvis.assistant.domain.model.MessageSender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

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

class FileConversationStore(
    context: Context,
    private val defaultMessages: List<ChatMessage>
) : ConversationMemoryPort {

    private val historyFile = File(context.applicationContext.filesDir, "jarvis_history.json")
    private val writeMutex = Mutex()
    private val _messages = MutableStateFlow(loadInitialMessages())
    override val messages: Flow<List<ChatMessage>> = _messages.asStateFlow()

    override suspend fun append(message: ChatMessage) {
        writeMutex.withLock {
            val updated = (_messages.value + message).takeLast(MAX_STORED_MESSAGES)
            _messages.value = updated
            persistMessages(updated)
        }
    }

    override suspend fun clear() {
        writeMutex.withLock {
            _messages.value = defaultMessages
            persistMessages(defaultMessages)
        }
    }

    private fun loadInitialMessages(): List<ChatMessage> {
        if (!historyFile.exists()) return defaultMessages
        return runCatching {
            val raw = historyFile.readText()
            if (raw.isBlank()) return defaultMessages
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val sender = runCatching {
                        MessageSender.valueOf(item.getString("sender"))
                    }.getOrDefault(MessageSender.SYSTEM_NOTICE)
                    add(
                        ChatMessage(
                            id = item.getString("id"),
                            sender = sender,
                            content = item.getString("content"),
                            timestampMillis = item.getLong("timestampMillis")
                        )
                    )
                }
            }.ifEmpty { defaultMessages }
        }.getOrDefault(defaultMessages)
    }

    private suspend fun persistMessages(list: List<ChatMessage>) {
        withContext(Dispatchers.IO) {
            runCatching {
                val array = JSONArray()
                list.forEach { msg ->
                    array.put(
                        JSONObject().apply {
                            put("id", msg.id)
                            put("sender", msg.sender.name)
                            put("content", msg.content)
                            put("timestampMillis", msg.timestampMillis)
                        }
                    )
                }
                historyFile.writeText(array.toString())
            }
        }
    }

    companion object {
        private const val MAX_STORED_MESSAGES = 200
    }
}
