package com.jarvis.assistant.core.web

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.jarvis.assistant.data.device.SystemTelemetryPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.DecimalFormat
import java.util.Locale
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

interface WebHttpTransport {
    suspend fun getText(url: String): Result<String>
}

interface WebIntelligencePort {
    suspend fun getWeather(city: String): Result<String>
    suspend fun searchWikipedia(query: String): Result<String>
    suspend fun getNews(topic: String): Result<String>
    suspend fun convertCurrency(amount: Double, fromCode: String, toCode: String): Result<String>
    fun searchYouTube(query: String): Result<String>
    fun openWebsite(urlOrDomain: String): Result<String>
    fun translateOfflineOrHint(phrase: String, targetLanguage: String): String
}

class AndroidWebHttpTransport : WebHttpTransport {
    override suspend fun getText(url: String): Result<String> = withContext(Dispatchers.IO) {
        if (!url.startsWith("https://")) {
            return@withContext Result.failure(SecurityException("Only HTTPS URLs are permitted for web intelligence."))
        }
        var connection: HttpURLConnection? = null
        runCatching {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12_000
                readTimeout = 20_000
                setRequestProperty("Accept", "application/json, application/xml, text/xml;q=0.9")
                setRequestProperty("User-Agent", "JARVIS-Android/1.0 (https://github.com/bednetbd-cmd/jarvis-android)")
            }
            val code = connection!!.responseCode
            val stream = if (code in 200..299) connection!!.inputStream else connection!!.errorStream
            val body = stream?.let {
                BufferedReader(InputStreamReader(it, StandardCharsets.UTF_8)).use { reader -> reader.readText() }
            }.orEmpty()
            if (code !in 200..299) {
                throw IllegalStateException("Web service returned HTTP $code.")
            }
            body
        }.also {
            connection?.disconnect()
        }
    }
}

object RssXmlParser {
    fun parseHeadlines(xml: String, limit: Int = 5): Result<List<Pair<String, String>>> {
        if (xml.isBlank()) {
            return Result.failure(IllegalStateException("RSS feed returned empty content."))
        }
        return runCatching {
            val factory = DocumentBuilderFactory.newInstance().apply {
                setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
                isXIncludeAware = false
                isExpandEntityReferences = false
            }
            val doc = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
            val itemNodes = doc.getElementsByTagName("item")
            val results = mutableListOf<Pair<String, String>>()
            for (i in 0 until minOf(itemNodes.length, limit)) {
                val element = itemNodes.item(i) as? Element ?: continue
                val title = element.getElementsByTagName("title").item(0)?.textContent?.trim().orEmpty()
                val link = element.getElementsByTagName("link").item(0)?.textContent?.trim().orEmpty()
                if (title.isNotEmpty()) {
                    results.add(title to link)
                }
            }
            results.toList()
        }
    }
}

class FreeWebIntelligenceService(
    private val http: WebHttpTransport,
    private val telemetryProvider: SystemTelemetryPort,
    private val urlLauncher: (String) -> Result<Unit>
) : WebIntelligencePort {

    constructor(
        context: Context,
        http: WebHttpTransport,
        telemetryProvider: SystemTelemetryPort
    ) : this(
        http = http,
        telemetryProvider = telemetryProvider,
        urlLauncher = { url ->
            runCatching {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.applicationContext.startActivity(intent)
            }
        }
    )

    override suspend fun getWeather(city: String): Result<String> {
        val cleanCity = city.trim()
        if (cleanCity.isEmpty()) {
            return Result.failure(IllegalArgumentException("Specify a city name for weather lookup (e.g. \"Weather in Dhaka\")."))
        }
        if (!telemetryProvider.currentSnapshot().isOnline) {
            return Result.failure(IllegalStateException("Device is offline. Connect to Wi-Fi/Cellular to fetch live weather."))
        }

        val encoded = encode(cleanCity)
        val geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=1&language=en&format=json"
        val geoBody = http.getText(geoUrl).getOrElse { return Result.failure(it) }
        val geoJson = runCatching { JSONObject(geoBody) }.getOrElse {
            return Result.failure(IllegalStateException("Unable to parse Open-Meteo location response."))
        }
        val firstResult = geoJson.optJSONArray("results")?.optJSONObject(0)
            ?: return Result.failure(IllegalStateException("No location found matching \"$cleanCity\"."))

        val lat = firstResult.optDouble("latitude")
        val lon = firstResult.optDouble("longitude")
        val name = firstResult.optString("name", cleanCity)
        val country = firstResult.optString("country", "")

        val forecastUrl =
            "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,relative_humidity_2m,apparent_temperature,wind_speed_10m,weather_code" +
                "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max&timezone=auto&forecast_days=1"

        val weatherBody = http.getText(forecastUrl).getOrElse { return Result.failure(it) }
        return runCatching {
            val root = JSONObject(weatherBody)
            val current = root.getJSONObject("current")
            val daily = root.optJSONObject("daily")
            val temp = current.optDouble("temperature_2m")
            val feelsLike = current.optDouble("apparent_temperature")
            val humidity = current.optInt("relative_humidity_2m")
            val wind = current.optDouble("wind_speed_10m")
            val code = current.optInt("weather_code", -1)
            val maxTemp = daily?.optJSONArray("temperature_2m_max")?.optDouble(0)
            val minTemp = daily?.optJSONArray("temperature_2m_min")?.optDouble(0)
            val rainProb = daily?.optJSONArray("precipitation_probability_max")?.optInt(0)

            val placeLabel = if (country.isNotBlank()) "$name, $country" else name
            val condition = describeWmoCode(code)
            buildString {
                appendLine("Weather in $placeLabel (Open-Meteo):")
                appendLine("• Condition: $condition")
                appendLine("• Temperature: ${formatNum(temp)}°C (Feels like ${formatNum(feelsLike)}°C)")
                appendLine("• Humidity: $humidity% · Wind: ${formatNum(wind)} km/h")
                if (maxTemp != null && minTemp != null && !maxTemp.isNaN() && !minTemp.isNaN()) {
                    append("• Today's Range: ${formatNum(minTemp)}°C – ${formatNum(maxTemp)}°C")
                    if (rainProb != null) append(" · Max rain chance: $rainProb%")
                }
            }.trim()
        }
    }

    override suspend fun searchWikipedia(query: String): Result<String> {
        val clean = query.trim()
        if (clean.isEmpty()) {
            return Result.failure(IllegalArgumentException("Provide a topic for Wikipedia lookup."))
        }
        if (!telemetryProvider.currentSnapshot().isOnline) {
            return Result.failure(IllegalStateException("Device is offline. Connect to Wi-Fi/Cellular to search Wikipedia."))
        }

        val slug = encode(clean.replace(' ', '_'))
        val directResult = http.getText("https://en.wikipedia.org/api/rest_v1/page/summary/$slug")
        val summaryJson = directResult.getOrNull()?.let { runCatching { JSONObject(it) }.getOrNull() }
        if (summaryJson != null && summaryJson.optString("extract", "").isNotBlank()) {
            val title = summaryJson.optString("title", clean)
            val extract = summaryJson.optString("extract").trim()
            return Result.success("Wikipedia · $title:\n$extract")
        }

        val searchUrl = "https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=${encode(clean)}&format=json&srlimit=1"
        val searchBody = http.getText(searchUrl).getOrElse { return Result.failure(it) }
        val topTitle = runCatching {
            JSONObject(searchBody)
                .optJSONObject("query")
                ?.optJSONArray("search")
                ?.optJSONObject(0)
                ?.optString("title", "")
                .orEmpty()
        }.getOrDefault("")

        if (topTitle.isBlank()) {
            return Result.failure(IllegalStateException("No Wikipedia article found for \"$clean\"."))
        }

        val matchedSlug = encode(topTitle.replace(' ', '_'))
        val matchedBody = http.getText("https://en.wikipedia.org/api/rest_v1/page/summary/$matchedSlug")
            .getOrElse { return Result.failure(it) }
        return runCatching {
            val json = JSONObject(matchedBody)
            val title = json.optString("title", topTitle)
            val extract = json.optString("extract", "").trim()
            if (extract.isEmpty()) throw IllegalStateException("Wikipedia summary was empty for \"$title\".")
            "Wikipedia · $title:\n$extract"
        }
    }

    override suspend fun getNews(topic: String): Result<String> {
        if (!telemetryProvider.currentSnapshot().isOnline) {
            return Result.failure(IllegalStateException("Device is offline. Connect to Wi-Fi/Cellular to read live news."))
        }

        val lower = topic.trim().lowercase(Locale.US)
        val (label, feedUrl) = when {
            lower.contains("tech") || lower.contains("ai") || lower.contains("science") ->
                "Technology" to "https://feeds.bbci.co.uk/news/technology/rss.xml"
            lower.contains("bangladesh") || lower.contains("asia") || lower.contains("dhaka") ->
                "Asia / Bangladesh" to "https://feeds.bbci.co.uk/news/world/asia/rss.xml"
            lower.contains("business") || lower.contains("finance") ->
                "Business" to "https://feeds.bbci.co.uk/news/business/rss.xml"
            else ->
                "World Headlines" to "https://feeds.bbci.co.uk/news/world/rss.xml"
        }

        val xml = http.getText(feedUrl).getOrElse { return Result.failure(it) }
        val headlines = RssXmlParser.parseHeadlines(xml, limit = 5)
            .getOrElse { return Result.failure(it) }
        if (headlines.isEmpty()) {
            return Result.failure(IllegalStateException("No headlines available from the RSS feed right now."))
        }
        return Result.success(
            "Latest $label (BBC RSS):\n" + headlines.joinToString("\n") { "• ${it.first}" }
        )
    }

    override suspend fun convertCurrency(
        amount: Double,
        fromCode: String,
        toCode: String
    ): Result<String> {
        val from = fromCode.trim().uppercase(Locale.US)
        val to = toCode.trim().uppercase(Locale.US)
        if (from.length != 3 || to.length != 3) {
            return Result.failure(IllegalArgumentException("Use 3-letter currency codes (e.g. USD, EUR, BDT, GBP, INR)."))
        }
        if (!telemetryProvider.currentSnapshot().isOnline) {
            return Result.failure(IllegalStateException("Device is offline. Connect to Wi-Fi/Cellular to fetch live exchange rates."))
        }

        val fmt = DecimalFormat("#,##0.##")
        if (from == to) {
            return Result.success("${fmt.format(amount)} $from = ${fmt.format(amount)} $to")
        }

        // Primary: Frankfurter (free ECB rates); Fallback: open.er-api.com (covers BDT and global currencies for $0)
        val frankfurterUrl = "https://api.frankfurter.app/latest?amount=$amount&from=$from&to=$to"
        val frankfurterAttempt = http.getText(frankfurterUrl).mapCatching { body ->
            val json = JSONObject(body)
            val rates = json.getJSONObject("rates")
            if (!rates.has(to)) throw IllegalStateException("Currency $to not in ECB feed.")
            val converted = rates.getDouble(to)
            val date = json.optString("date", "latest")
            "${fmt.format(amount)} $from = ${fmt.format(converted)} $to (Frankfurter ECB · $date)"
        }
        if (frankfurterAttempt.isSuccess) return frankfurterAttempt

        val erApiUrl = "https://open.er-api.com/v6/latest/$from"
        return http.getText(erApiUrl).mapCatching { body ->
            val json = JSONObject(body)
            if (json.optString("result") != "success") {
                throw IllegalStateException("Exchange rate unavailable for $from.")
            }
            val rates = json.getJSONObject("rates")
            if (!rates.has(to)) {
                throw IllegalStateException("Currency \"$to\" is not supported.")
            }
            val rate = rates.getDouble(to)
            val converted = amount * rate
            "${fmt.format(amount)} $from = ${fmt.format(converted)} $to (Open Exchange Rates)"
        }
    }

    override fun searchYouTube(query: String): Result<String> {
        val clean = query.trim()
        if (clean.isEmpty()) {
            return Result.failure(IllegalArgumentException("Provide a topic or video name to search on YouTube."))
        }
        val searchUrl = "https://www.youtube.com/results?search_query=${URLEncoder.encode(clean, "UTF-8")}"
        return urlLauncher(searchUrl).map {
            "Opening YouTube search for \"$clean\"."
        }.recoverCatching { error ->
            throw IllegalStateException("No browser or YouTube app available to open search.", error)
        }
    }

    override fun openWebsite(urlOrDomain: String): Result<String> {
        val raw = urlOrDomain.trim()
        if (raw.isEmpty()) {
            return Result.failure(IllegalArgumentException("Specify a website URL or domain (e.g., wikipedia.org)."))
        }
        val lower = raw.lowercase(Locale.US)
        if (lower.startsWith("javascript:") || lower.startsWith("file:") || lower.startsWith("content:") || lower.startsWith("intent:")) {
            return Result.failure(SecurityException("Unsupported or unsafe URL scheme."))
        }
        val normalized = if (lower.startsWith("https://") || lower.startsWith("http://")) {
            raw
        } else {
            "https://$raw"
        }
        val parsed = runCatching { URL(normalized) }.getOrNull()
            ?: return Result.failure(IllegalArgumentException("Invalid website address: \"$raw\"."))
        if (parsed.protocol !in setOf("http", "https") || parsed.host.isNullOrBlank() || !parsed.host.contains('.')) {
            return Result.failure(IllegalArgumentException("Provide a valid domain or URL (e.g., example.com)."))
        }
        return urlLauncher(normalized).map {
            "Opening website $normalized."
        }.recoverCatching { error ->
            throw IllegalStateException("No web browser available to open $normalized.", error)
        }
    }

    override fun translateOfflineOrHint(phrase: String, targetLanguage: String): String {
        val clean = phrase.trim().lowercase(Locale.US)
        val target = targetLanguage.trim().lowercase(Locale.US)
        val isBanglaTarget = target.contains("bangla") || target.contains("bengali") || target == "bn"

        val enToBn = mapOf(
            "hello" to "হ্যালো / নমস্কার",
            "hi" to "হাই",
            "how are you" to "আপনি কেমন আছেন?",
            "i am fine" to "আমি ভালো আছি",
            "thank you" to "ধন্যবাদ",
            "good morning" to "শুভ সকাল",
            "good night" to "শুভ রাত্রি",
            "yes" to "হ্যাঁ",
            "no" to "না",
            "please" to "দয়া করে",
            "sorry" to "দুঃখিত",
            "help" to "সাহায্য",
            "water" to "পানি / জল"
        )
        val bnToEn = enToBn.entries.associate { (en, bn) -> bn.lowercase(Locale.US) to en }

        val match = if (isBanglaTarget) {
            enToBn[clean]
        } else {
            bnToEn[clean]
        }

        return if (match != null) {
            "Offline Phrasebook Translation: \"$phrase\" -> $match"
        } else {
            "\"$phrase\" is not in the small built-in offline phrasebook. Connect a free Gemini, Groq/OpenAI-compatible, or local Ollama model in Settings for full multi-language translation."
        }
    }

    private fun describeWmoCode(code: Int): String = when (code) {
        0 -> "Clear sky"
        1, 2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Foggy"
        51, 53, 55 -> "Drizzle"
        61, 63, 65 -> "Rain"
        71, 73, 75 -> "Snow"
        80, 81, 82 -> "Rain showers"
        95, 96, 99 -> "Thunderstorm"
        else -> "Mixed conditions"
    }

    private fun encode(text: String): String =
        URLEncoder.encode(text, "UTF-8").replace("+", "%20")

    private fun formatNum(value: Double): String = DecimalFormat("#0.#").format(value)
}
