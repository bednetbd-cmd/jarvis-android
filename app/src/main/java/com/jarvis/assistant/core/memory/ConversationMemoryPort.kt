package com.jarvis.assistant.core.memory

import android.content.Context
import com.jarvis.assistant.data.security.SecureVaultPort
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
    suspend fun setEncryptionEnabled(enabled: Boolean) {}
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
    private val defaultMessages: List<ChatMessage>,
    private val secureVault: SecureVaultPort? = null,
    @Volatile private var encryptAtRest: Boolean = true
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

    override suspend fun setEncryptionEnabled(enabled: Boolean) {
        writeMutex.withLock {
            if (encryptAtRest != enabled) {
                encryptAtRest = enabled
                persistMessages(_messages.value)
            }
        }
    }

    private fun loadInitialMessages(): List<ChatMessage> {
        if (!historyFile.exists()) return defaultMessages
        return runCatching {
            val rawDisk = historyFile.readText()
            if (rawDisk.isBlank()) return defaultMessages
            val jsonText = if (rawDisk.startsWith(ENCRYPTED_PREFIX)) {
                val payload = rawDisk.removePrefix(ENCRYPTED_PREFIX)
                secureVault?.decryptPayload(payload) ?: return defaultMessages
            } else {
                rawDisk
            }
            val array = JSONArray(jsonText)
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
                val jsonString = array.toString()
                val encrypted = if (encryptAtRest) secureVault?.encryptPayload(jsonString) else null
                val payload = if (encrypted != null) "$ENCRYPTED_PREFIX$encrypted" else jsonString
                val tempFile = File(historyFile.parentFile, "${historyFile.name}.tmp")
                tempFile.writeText(payload)
                if (!tempFile.renameTo(historyFile)) {
                    historyFile.writeText(payload)
                    tempFile.delete()
                }
            }
        }
    }

    companion object {
        private const val MAX_STORED_MESSAGES = 200
        private const val ENCRYPTED_PREFIX = "ENC1:"
    }
}
