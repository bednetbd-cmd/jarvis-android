package com.jarvis.assistant

import com.jarvis.assistant.core.ai.DeviceContextPort
import com.jarvis.assistant.core.ai.JarvisToolRegistry
import com.jarvis.assistant.core.ai.OnDeviceJarvisAssistant
import com.jarvis.assistant.core.ai.ParsedToolCall
import com.jarvis.assistant.core.automation.PhoneAutomationPort
import com.jarvis.assistant.core.web.FreeWebIntelligenceService
import com.jarvis.assistant.core.web.RssXmlParser
import com.jarvis.assistant.core.web.UnitConverter
import com.jarvis.assistant.core.web.WebHttpTransport
import com.jarvis.assistant.data.device.SystemTelemetryPort
import com.jarvis.assistant.domain.model.DeviceTelemetry
import com.jarvis.assistant.domain.model.NetworkTransport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebIntelligenceTest {

    @Test
    fun unitConverterHandlesLengthTemperatureMassDataAndRejectsIncompatible() {
        val kmToMi = UnitConverter.convert(10.0, "km", "miles")
        assertTrue(kmToMi.isSuccess)
        assertTrue(kmToMi.getOrNull().orEmpty().contains("6.2137"))

        val fToC = UnitConverter.convert(212.0, "F", "C")
        assertTrue(fToC.isSuccess)
        assertTrue(fToC.getOrNull().orEmpty().contains("100"))

        val cToK = UnitConverter.convert(0.0, "celsius", "kelvin")
        assertTrue(cToK.isSuccess)
        assertTrue(cToK.getOrNull().orEmpty().contains("273.15"))

        val kgToLb = UnitConverter.convert(5.0, "kg", "lb")
        assertTrue(kgToLb.isSuccess)
        assertTrue(kgToLb.getOrNull().orEmpty().contains("11.0231"))

        val gbToMb = UnitConverter.convert(2.0, "GB", "MB")
        assertTrue(gbToMb.isSuccess)
        assertTrue(gbToMb.getOrNull().orEmpty().contains("2,048"))

        val incompatible = UnitConverter.convert(10.0, "kg", "meters")
        assertTrue(incompatible.isFailure)

        val parsed = UnitConverter.parseConversionQuery("Convert 100 sec to min")
        assertNotNull(parsed)
        assertEquals(100.0, parsed?.value ?: 0.0, 0.001)
        assertEquals("sec", parsed?.fromUnit)
        assertEquals("min", parsed?.toUnit)
    }

    @Test
    fun rssXmlParserExtractsHeadlinesAndBlocksDoctypeXxe() {
        val validRss = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>BBC News</title>
                <item>
                  <title>Quantum Milestone Reached</title>
                  <link>https://www.bbc.com/news/1</link>
                </item>
                <item>
                  <title>Android Kotlin Tooling Advances</title>
                  <link>https://www.bbc.com/news/2</link>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val parsed = RssXmlParser.parseHeadlines(validRss, limit = 5)
        assertTrue(parsed.isSuccess)
        assertEquals(2, parsed.getOrNull()?.size)
        assertEquals("Quantum Milestone Reached", parsed.getOrNull()?.first()?.first)

        val maliciousXxe = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE foo [ <!ENTITY xxe SYSTEM "file:///etc/passwd"> ]>
            <rss version="2.0"><channel><item><title>&xxe;</title></item></channel></rss>
        """.trimIndent()

        val xxeResult = RssXmlParser.parseHeadlines(maliciousXxe)
        assertTrue(xxeResult.isFailure)
    }

    @Test
    fun weatherNewsWikipediaAndCurrencyWorkViaFreePublicEndpoints() = runTest {
        val transport = FakeWebHttpTransport(
            mapOf(
                "geocoding-api.open-meteo.com" to Result.success(
                    """{"results":[{"name":"Dhaka","country":"Bangladesh","latitude":23.8103,"longitude":90.4125}]}"""
                ),
                "api.open-meteo.com" to Result.success(
                    """{
                        "current":{"temperature_2m":31.4,"apparent_temperature":34.2,"relative_humidity_2m":68,"wind_speed_10m":11.5,"weather_code":1},
                        "daily":{"temperature_2m_max":[33.0],"temperature_2m_min":[25.2],"precipitation_probability_max":[20]}
                    }"""
                ),
                "en.wikipedia.org/api/rest_v1/page/summary/Artificial_intelligence" to Result.success(
                    """{"title":"Artificial intelligence","extract":"Artificial intelligence is the capability of computational systems to perform tasks typically associated with human intelligence."}"""
                ),
                "feeds.bbci.co.uk/news/technology/rss.xml" to Result.success(
                    """<rss version="2.0"><channel><item><title>Open Models Improve</title><link>https://bbc.com/tech</link></item></channel></rss>"""
                ),
                "api.frankfurter.app" to Result.success(
                    """{"date":"2026-11-25","rates":{"EUR":92.5}}"""
                ),
                "open.er-api.com" to Result.success(
                    """{"result":"success","rates":{"BDT":119.5}}"""
                )
            )
        )
        var launchedUrl: String? = null
        val service = FreeWebIntelligenceService(
            http = transport,
            telemetryProvider = FakeWebTelemetry(isOnline = true),
            urlLauncher = { url ->
                launchedUrl = url
                Result.success(Unit)
            }
        )

        val weather = service.getWeather("Dhaka")
        assertTrue(weather.isSuccess)
        assertTrue(weather.getOrNull().orEmpty().contains("Dhaka, Bangladesh"))
        assertTrue(weather.getOrNull().orEmpty().contains("31.4°C"))
        assertTrue(weather.getOrNull().orEmpty().contains("Partly cloudy"))

        val wiki = service.searchWikipedia("Artificial intelligence")
        assertTrue(wiki.isSuccess)
        assertTrue(wiki.getOrNull().orEmpty().contains("Artificial intelligence"))

        val news = service.getNews("tech news")
        assertTrue(news.isSuccess)
        assertTrue(news.getOrNull().orEmpty().contains("Open Models Improve"))

        val eur = service.convertCurrency(100.0, "USD", "EUR")
        assertTrue(eur.isSuccess)
        assertTrue(eur.getOrNull().orEmpty().contains("92.5 EUR"))

        val bdt = service.convertCurrency(100.0, "USD", "BDT")
        assertTrue(bdt.isSuccess)
        assertTrue(bdt.getOrNull().orEmpty().contains("11,950 BDT"))

        val yt = service.searchYouTube("Kotlin Compose")
        assertTrue(yt.isSuccess)
        assertTrue(launchedUrl.orEmpty().contains("youtube.com/results?search_query=Kotlin+Compose"))

        val site = service.openWebsite("wikipedia.org")
        assertTrue(site.isSuccess)
        assertEquals("https://wikipedia.org", launchedUrl)

        val unsafeSite = service.openWebsite("javascript:alert(1)")
        assertTrue(unsafeSite.isFailure)

        val translated = service.translateOfflineOrHint("thank you", "Bangla")
        assertTrue(translated.contains("ধন্যবাদ"))
    }

    @Test
    fun offlineModeRefusesLiveWebRequestsHonestly() = runTest {
        val service = FreeWebIntelligenceService(
            http = FakeWebHttpTransport(emptyMap()),
            telemetryProvider = FakeWebTelemetry(isOnline = false),
            urlLauncher = { Result.success(Unit) }
        )

        assertTrue(service.getWeather("London").isFailure)
        assertTrue(service.getNews("world").isFailure)
        assertTrue(service.searchWikipedia("Mars").isFailure)
        assertTrue(service.convertCurrency(10.0, "USD", "BDT").isFailure)
    }

    @Test
    fun assistantAndToolRegistryExecuteMilestone6CommandsEndToEnd() = runTest {
        val transport = FakeWebHttpTransport(
            mapOf(
                "geocoding-api.open-meteo.com" to Result.success(
                    """{"results":[{"name":"London","country":"United Kingdom","latitude":51.5,"longitude":-0.12}]}"""
                ),
                "api.open-meteo.com" to Result.success(
                    """{"current":{"temperature_2m":12.0,"apparent_temperature":10.5,"relative_humidity_2m":75,"wind_speed_10m":14.0,"weather_code":3}}"""
                ),
                "open.er-api.com" to Result.success(
                    """{"result":"success","rates":{"BDT":120.0}}"""
                )
            )
        )
        var launchedUrl: String? = null
        val webService = FreeWebIntelligenceService(
            http = transport,
            telemetryProvider = FakeWebTelemetry(isOnline = true),
            urlLauncher = { url ->
                launchedUrl = url
                Result.success(Unit)
            }
        )
        val contextPort = object : DeviceContextPort {
            override fun getCurrentTimeDate(): String = "Current time: 3:00 PM."
            override fun getBatteryStatus(): String = "Battery is at 80%."
            override fun getDeviceDiagnostics(): String = "Device: Test"
        }
        val automationPort = object : PhoneAutomationPort {
            override suspend fun executeCommand(actionName: String, params: Map<String, String>): Result<String> =
                Result.success("Executed $actionName")
        }

        val assistant = OnDeviceJarvisAssistant(automationPort, contextPort, null, webService)
        val weatherReply = assistant.generateReply("Weather in London", emptyList())
        assertTrue(weatherReply.isSuccess)
        assertTrue(weatherReply.getOrNull().orEmpty().contains("London, United Kingdom"))

        val unitReply = assistant.generateReply("Convert 5 km to meters", emptyList())
        assertTrue(unitReply.isSuccess)
        assertTrue(unitReply.getOrNull().orEmpty().contains("5,000"))

        val currencyReply = assistant.generateReply("Convert 10 USD to BDT", emptyList())
        assertTrue(currencyReply.isSuccess)
        assertTrue(currencyReply.getOrNull().orEmpty().contains("1,200 BDT"))

        val websiteReply = assistant.generateReply("Open website example.com", emptyList())
        assertTrue(websiteReply.isSuccess)
        assertEquals("https://example.com", launchedUrl)

        val registry = JarvisToolRegistry(automationPort, contextPort, null, webService)
        val toolUnit = registry.executeTool(
            ParsedToolCall(
                name = JarvisToolRegistry.TOOL_CONVERT_UNITS,
                arguments = mapOf("value" to "100", "from_unit" to "c", "to_unit" to "f")
            )
        )
        assertTrue(toolUnit.isSuccess)
        assertTrue(toolUnit.getOrNull().orEmpty().contains("212"))
    }
}

private class FakeWebHttpTransport(
    private val responsesBySubstring: Map<String, Result<String>>
) : WebHttpTransport {
    override suspend fun getText(url: String): Result<String> {
        val match = responsesBySubstring.entries.firstOrNull { url.contains(it.key) }
        return match?.value ?: Result.failure(IllegalStateException("404 for $url"))
    }
}

private class FakeWebTelemetry(
    isOnline: Boolean
) : SystemTelemetryPort {
    private val snapshot = DeviceTelemetry(
        batteryPercent = 85,
        isOnline = isOnline,
        networkTransport = if (isOnline) NetworkTransport.WIFI else NetworkTransport.OFFLINE
    )
    override val telemetry: Flow<DeviceTelemetry> = MutableStateFlow(snapshot).asStateFlow()
    override fun currentSnapshot(): DeviceTelemetry = snapshot
}
