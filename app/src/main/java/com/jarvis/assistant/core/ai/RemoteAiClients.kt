package com.jarvis.assistant.core.ai

import com.jarvis.assistant.domain.model.AiProviderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class RemoteChatMessage(
    val role: String, // "user" or "assistant"
    val content: String
)

interface AiRemoteTransport {
    suspend fun chatOllama(
        baseUrl: String,
        model: String,
        systemPrompt: String,
        messages: List<RemoteChatMessage>
    ): Result<String>

    suspend fun testOllama(baseUrl: String): Result<String>

    suspend fun chatOpenAiCompatible(
        baseUrl: String,
        model: String,
        apiKey: String,
        systemPrompt: String,
        messages: List<RemoteChatMessage>
    ): Result<String>

    suspend fun testOpenAiCompatible(baseUrl: String, apiKey: String): Result<String>

    suspend fun chatGemini(
        model: String,
        apiKey: String,
        systemPrompt: String,
        messages: List<RemoteChatMessage>
    ): Result<String>

    suspend fun testGemini(model: String, apiKey: String): Result<String>

    suspend fun analyzeImage(
        provider: AiProviderType,
        baseUrl: String,
        model: String,
        apiKey: String,
        prompt: String,
        jpegBase64: String
    ): Result<String> = Result.failure(
        UnsupportedOperationException("Image vision analysis is unavailable on this transport.")
    )
}

object EndpointUrlValidator {
    fun validate(rawUrl: String, allowPrivateHttp: Boolean): Result<String> {
        val trimmed = rawUrl.trim().trimEnd('/')
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Endpoint URL cannot be empty."))
        }

        val uri = runCatching { URI(trimmed) }.getOrElse {
            return Result.failure(IllegalArgumentException("Invalid endpoint URL format."))
        }

        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()
        if (scheme.isNullOrBlank() || host.isNullOrBlank()) {
            return Result.failure(IllegalArgumentException("Endpoint URL must include a scheme and host (e.g. https://...)."))
        }

        return when (scheme) {
            "https" -> Result.success(trimmed)
            "http" -> {
                if (allowPrivateHttp && isPrivateOrLoopbackHost(host)) {
                    Result.success(trimmed)
                } else {
                    Result.failure(
                        SecurityException(
                            "Cleartext HTTP is only allowed for local loopback or private LAN hosts. Use https:// for public endpoints."
                        )
                    )
                }
            }
            else -> Result.failure(IllegalArgumentException("Unsupported URL scheme \"$scheme\". Use https:// or local http://."))
        }
    }

    fun isPrivateOrLoopbackHost(host: String): Boolean {
        val normalized = host.lowercase().trim('[', ']')
        if (normalized == "localhost" || normalized == "127.0.0.1" || normalized == "::1" || normalized.endsWith(".local")) {
            return true
        }
        val ipv4Parts = normalized.split(".")
        if (ipv4Parts.size == 4) {
            val octets = ipv4Parts.mapNotNull { it.toIntOrNull() }
            if (octets.size == 4 && octets.all { it in 0..255 }) {
                val (a, b) = octets[0] to octets[1]
                if (a == 10) return true
                if (a == 192 && b == 168) return true
                if (a == 172 && b in 16..31) return true
                if (a == 127) return true
            }
        }
        return runCatching {
            val addr = InetAddress.getByName(normalized)
            addr.isLoopbackAddress || addr.isSiteLocalAddress
        }.getOrDefault(false)
    }
}

class HttpRemoteAiTransport : AiRemoteTransport {

    override suspend fun chatOllama(
        baseUrl: String,
        model: String,
        systemPrompt: String,
        messages: List<RemoteChatMessage>
    ): Result<String> = withContext(Dispatchers.IO) {
        val validUrl = EndpointUrlValidator.validate(baseUrl, allowPrivateHttp = true)
            .getOrElse { return@withContext Result.failure(it) }
        val cleanModel = model.trim()
        if (cleanModel.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Ollama model name cannot be empty."))
        }

        val jsonMessages = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", systemPrompt))
            messages.forEach { msg ->
                put(JSONObject().put("role", msg.role).put("content", msg.content))
            }
        }

        val body = JSONObject()
            .put("model", cleanModel)
            .put("stream", false)
            .put("messages", jsonMessages)
            .toString()

        executeJsonRequest(
            urlString = "$validUrl/api/chat",
            method = "POST",
            headers = emptyMap(),
            body = body
        ).mapCatching { responseText ->
            val json = JSONObject(responseText)
            val err = json.optString("error", "")
            if (err.isNotEmpty()) throw IllegalStateException("Ollama error: $err")
            val content = json.optJSONObject("message")?.optString("content", "").orEmpty().trim()
            if (content.isEmpty()) {
                throw IllegalStateException("Ollama returned an empty response.")
            }
            content
        }
    }

    override suspend fun testOllama(baseUrl: String): Result<String> = withContext(Dispatchers.IO) {
        val validUrl = EndpointUrlValidator.validate(baseUrl, allowPrivateHttp = true)
            .getOrElse { return@withContext Result.failure(it) }

        executeJsonRequest(
            urlString = "$validUrl/api/tags",
            method = "GET",
            headers = emptyMap(),
            body = null
        ).mapCatching { responseText ->
            val modelsArray = JSONObject(responseText).optJSONArray("models") ?: JSONArray()
            val names = buildList {
                for (i in 0 until minOf(modelsArray.length(), 4)) {
                    val name = modelsArray.optJSONObject(i)?.optString("name", "").orEmpty()
                    if (name.isNotBlank()) add(name)
                }
            }
            if (names.isEmpty()) {
                "Connected to Ollama at $validUrl (0 local models pulled yet)."
            } else {
                "Connected to Ollama (${modelsArray.length()} model(s): ${names.joinToString(", ")})."
            }
        }
    }

    override suspend fun chatOpenAiCompatible(
        baseUrl: String,
        model: String,
        apiKey: String,
        systemPrompt: String,
        messages: List<RemoteChatMessage>
    ): Result<String> = withContext(Dispatchers.IO) {
        val validUrl = EndpointUrlValidator.validate(baseUrl, allowPrivateHttp = true)
            .getOrElse { return@withContext Result.failure(it) }
        val cleanModel = model.trim()
        if (cleanModel.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Model ID cannot be empty."))
        }

        val jsonMessages = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", systemPrompt))
            messages.forEach { msg ->
                put(JSONObject().put("role", msg.role).put("content", msg.content))
            }
        }

        val body = JSONObject()
            .put("model", cleanModel)
            .put("temperature", 0.4)
            .put("messages", jsonMessages)
            .toString()

        val headers = buildMap {
            if (apiKey.isNotBlank()) {
                put("Authorization", "Bearer ${apiKey.trim()}")
            }
        }

        executeJsonRequest(
            urlString = "$validUrl/chat/completions",
            method = "POST",
            headers = headers,
            body = body
        ).mapCatching { responseText ->
            val json = JSONObject(responseText)
            val choices = json.optJSONArray("choices")
            val content = choices?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content", "")
                .orEmpty()
                .trim()
            if (content.isEmpty()) {
                throw IllegalStateException("Provider returned an empty message.")
            }
            content
        }
    }

    override suspend fun testOpenAiCompatible(baseUrl: String, apiKey: String): Result<String> =
        withContext(Dispatchers.IO) {
            val validUrl = EndpointUrlValidator.validate(baseUrl, allowPrivateHttp = true)
                .getOrElse { return@withContext Result.failure(it) }
            val headers = buildMap {
                if (apiKey.isNotBlank()) {
                    put("Authorization", "Bearer ${apiKey.trim()}")
                }
            }
            executeJsonRequest(
                urlString = "$validUrl/models",
                method = "GET",
                headers = headers,
                body = null
            ).map { responseText ->
                val count = runCatching {
                    JSONObject(responseText).optJSONArray("data")?.length() ?: 0
                }.getOrDefault(0)
                if (count > 0) {
                    "Connected to endpoint ($count model(s) listed)."
                } else {
                    "Connected to endpoint successfully."
                }
            }
        }

    override suspend fun chatGemini(
        model: String,
        apiKey: String,
        systemPrompt: String,
        messages: List<RemoteChatMessage>
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanModel = model.trim()
        if (cleanModel.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Gemini model name cannot be empty."))
        }
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Gemini API key is missing."))
        }

        val contents = JSONArray().apply {
            messages.forEach { msg ->
                val geminiRole = if (msg.role == "assistant") "model" else "user"
                val parts = JSONArray().put(JSONObject().put("text", msg.content))
                put(JSONObject().put("role", geminiRole).put("parts", parts))
            }
        }

        val payload = JSONObject()
            .put(
                "systemInstruction",
                JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", systemPrompt))
                )
            )
            .put("contents", contents)
            .put("generationConfig", JSONObject().put("temperature", 0.4))
            .toString()

        val encodedModel = URLEncoder.encode(cleanModel, "UTF-8").replace("+", "%20")
        executeJsonRequest(
            urlString = "$GEMINI_BASE_URL/models/$encodedModel:generateContent",
            method = "POST",
            headers = mapOf("x-goog-api-key" to apiKey.trim()),
            body = payload
        ).mapCatching { responseText ->
            val json = JSONObject(responseText)
            val candidates = json.optJSONArray("candidates")
            val parts = candidates?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
            val text = buildString {
                if (parts != null) {
                    for (i in 0 until parts.length()) {
                        val partText = parts.optJSONObject(i)?.optString("text", "").orEmpty()
                        if (partText.isNotEmpty()) append(partText)
                    }
                }
            }.trim()
            if (text.isEmpty()) {
                val blockReason = json.optJSONObject("promptFeedback")?.optString("blockReason", "").orEmpty()
                if (blockReason.isNotEmpty()) {
                    throw IllegalStateException("Gemini blocked this prompt ($blockReason).")
                }
                throw IllegalStateException("Gemini returned an empty response.")
            }
            text
        }
    }

    override suspend fun testGemini(model: String, apiKey: String): Result<String> =
        withContext(Dispatchers.IO) {
            val cleanModel = model.trim()
            if (cleanModel.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("Gemini model name cannot be empty."))
            }
            if (apiKey.isBlank()) {
                return@withContext Result.failure(IllegalStateException("Gemini API key is missing."))
            }
            val encodedModel = URLEncoder.encode(cleanModel, "UTF-8").replace("+", "%20")
            executeJsonRequest(
                urlString = "$GEMINI_BASE_URL/models/$encodedModel",
                method = "GET",
                headers = mapOf("x-goog-api-key" to apiKey.trim()),
                body = null
            ).map { responseText ->
                val display = runCatching {
                    JSONObject(responseText).optString("displayName", cleanModel)
                }.getOrDefault(cleanModel)
                "Connected to Gemini Free-Tier REST ($display)."
            }
        }

    override suspend fun analyzeImage(
        provider: AiProviderType,
        baseUrl: String,
        model: String,
        apiKey: String,
        prompt: String,
        jpegBase64: String
    ): Result<String> = withContext(Dispatchers.IO) {
        if (jpegBase64.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Image payload is empty."))
        }
        val cleanPrompt = prompt.trim().ifEmpty {
            "Describe what is visible in this image or screenshot concisely and extract any important text or details."
        }
        when (provider) {
            AiProviderType.ON_DEVICE -> Result.failure(
                IllegalStateException(
                    "On-Device Engine does not include a cloud vision-language model. Use free Offline OCR / QR / Object Labeling, or select Gemini Free-Tier REST or Local Ollama vision in Settings."
                )
            )
            AiProviderType.LOCAL_OLLAMA -> {
                val validUrl = EndpointUrlValidator.validate(baseUrl, allowPrivateHttp = true)
                    .getOrElse { return@withContext Result.failure(it) }
                val cleanModel = model.trim()
                if (cleanModel.isEmpty()) {
                    return@withContext Result.failure(IllegalArgumentException("Ollama vision model name cannot be empty (e.g., llava or llama3.2-vision)."))
                }
                val messages = JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put("content", cleanPrompt)
                        .put("images", JSONArray().put(jpegBase64))
                )
                val body = JSONObject()
                    .put("model", cleanModel)
                    .put("stream", false)
                    .put("messages", messages)
                    .toString()

                executeJsonRequest(
                    urlString = "$validUrl/api/chat",
                    method = "POST",
                    headers = emptyMap(),
                    body = body
                ).mapCatching { responseText ->
                    val json = JSONObject(responseText)
                    val err = json.optString("error", "")
                    if (err.isNotEmpty()) throw IllegalStateException("Ollama vision error: $err")
                    val content = json.optJSONObject("message")?.optString("content", "").orEmpty().trim()
                    if (content.isEmpty()) throw IllegalStateException("Ollama returned an empty vision response.")
                    content
                }
            }
            AiProviderType.OPENAI_COMPATIBLE -> {
                val validUrl = EndpointUrlValidator.validate(baseUrl, allowPrivateHttp = true)
                    .getOrElse { return@withContext Result.failure(it) }
                val cleanModel = model.trim()
                if (cleanModel.isEmpty()) {
                    return@withContext Result.failure(IllegalArgumentException("Vision model ID cannot be empty."))
                }
                val contentParts = JSONArray()
                    .put(JSONObject().put("type", "text").put("text", cleanPrompt))
                    .put(
                        JSONObject()
                            .put("type", "image_url")
                            .put(
                                "image_url",
                                JSONObject().put("url", "data:image/jpeg;base64,$jpegBase64")
                            )
                    )
                val messages = JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put("content", contentParts)
                )
                val body = JSONObject()
                    .put("model", cleanModel)
                    .put("temperature", 0.3)
                    .put("messages", messages)
                    .toString()
                val headers = buildMap {
                    if (apiKey.isNotBlank()) put("Authorization", "Bearer ${apiKey.trim()}")
                }
                executeJsonRequest(
                    urlString = "$validUrl/chat/completions",
                    method = "POST",
                    headers = headers,
                    body = body
                ).mapCatching { responseText ->
                    val content = JSONObject(responseText)
                        .optJSONArray("choices")
                        ?.optJSONObject(0)
                        ?.optJSONObject("message")
                        ?.optString("content", "")
                        .orEmpty()
                        .trim()
                    if (content.isEmpty()) throw IllegalStateException("Vision provider returned an empty response.")
                    content
                }
            }
            AiProviderType.GEMINI_REST -> {
                val cleanModel = model.trim()
                if (cleanModel.isEmpty()) {
                    return@withContext Result.failure(IllegalArgumentException("Gemini model name cannot be empty."))
                }
                if (apiKey.isBlank()) {
                    return@withContext Result.failure(IllegalStateException("Gemini API key is missing. Add your free key in Settings."))
                }
                val parts = JSONArray()
                    .put(
                        JSONObject().put(
                            "inlineData",
                            JSONObject()
                                .put("mimeType", "image/jpeg")
                                .put("data", jpegBase64)
                        )
                    )
                    .put(JSONObject().put("text", cleanPrompt))
                val payload = JSONObject()
                    .put(
                        "contents",
                        JSONArray().put(
                            JSONObject()
                                .put("role", "user")
                                .put("parts", parts)
                        )
                    )
                    .put("generationConfig", JSONObject().put("temperature", 0.3))
                    .toString()
                val encodedModel = URLEncoder.encode(cleanModel, "UTF-8").replace("+", "%20")
                executeJsonRequest(
                    urlString = "$GEMINI_BASE_URL/models/$encodedModel:generateContent",
                    method = "POST",
                    headers = mapOf("x-goog-api-key" to apiKey.trim()),
                    body = payload
                ).mapCatching { responseText ->
                    val json = JSONObject(responseText)
                    val replyParts = json.optJSONArray("candidates")
                        ?.optJSONObject(0)
                        ?.optJSONObject("content")
                        ?.optJSONArray("parts")
                    val text = buildString {
                        if (replyParts != null) {
                            for (i in 0 until replyParts.length()) {
                                val partText = replyParts.optJSONObject(i)?.optString("text", "").orEmpty()
                                if (partText.isNotEmpty()) append(partText)
                            }
                        }
                    }.trim()
                    if (text.isEmpty()) throw IllegalStateException("Gemini returned an empty image description.")
                    text
                }
            }
        }
    }

    private fun executeJsonRequest(
        urlString: String,
        method: String,
        headers: Map<String, String>,
        body: String?
    ): Result<String> {
        var connection: HttpURLConnection? = null
        return runCatching {
            connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
                headers.forEach { (key, value) -> setRequestProperty(key, value) }
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    OutputStreamWriter(outputStream, StandardCharsets.UTF_8).use { writer ->
                        writer.write(body)
                        writer.flush()
                    }
                }
            }

            val code = connection!!.responseCode
            val stream = if (code in 200..299) {
                connection!!.inputStream
            } else {
                connection!!.errorStream ?: connection!!.inputStream
            }
            val responseText = stream?.let { input ->
                BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).use { it.readText() }
            }.orEmpty()

            if (code !in 200..299) {
                val apiError = extractErrorMessage(responseText)
                val hint = when (code) {
                    401, 403 -> "Authentication failed (HTTP $code). Verify your API key."
                    404 -> "Endpoint or model not found (HTTP 404). Check URL and model name."
                    429 -> "Free-tier rate limit or quota exceeded (HTTP 429). Wait a moment or switch provider."
                    else -> "HTTP $code error"
                }
                val detail = if (apiError.isNotBlank()) " $apiError" else ""
                throw IllegalStateException("$hint$detail".trim())
            }

            responseText
        }.also {
            connection?.disconnect()
        }
    }

    private fun extractErrorMessage(raw: String): String {
        if (raw.isBlank()) return ""
        return runCatching {
            val json = JSONObject(raw)
            when {
                json.has("error") -> {
                    val err = json.get("error")
                    if (err is JSONObject) err.optString("message", raw) else err.toString()
                }
                json.has("message") -> json.optString("message", raw)
                else -> raw.take(180)
            }
        }.getOrElse { raw.take(180) }
    }

    private companion object {
        const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 45_000
    }
}
