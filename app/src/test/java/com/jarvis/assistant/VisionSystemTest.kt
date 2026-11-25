package com.jarvis.assistant

import com.jarvis.assistant.core.ai.AiRemoteTransport
import com.jarvis.assistant.core.ai.RemoteChatMessage
import com.jarvis.assistant.core.vision.VisionConsentPolicy
import com.jarvis.assistant.core.vision.VisionFormatter
import com.jarvis.assistant.core.vision.VisionScanMode
import com.jarvis.assistant.data.device.SystemTelemetryPort
import com.jarvis.assistant.data.security.AndroidKeystoreVault
import com.jarvis.assistant.data.security.InMemorySecureVault
import com.jarvis.assistant.domain.model.AiProviderConfig
import com.jarvis.assistant.domain.model.AiProviderType
import com.jarvis.assistant.domain.model.DeviceTelemetry
import com.jarvis.assistant.domain.model.JarvisPreferences
import com.jarvis.assistant.domain.model.NetworkTransport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisionSystemTest {

    @Test
    fun visionFormatterExtractsSafeQrUrlsAndFormatsOcrAndObjects() {
        val ocrEmpty = VisionFormatter.formatOcrResult("   ")
        assertEquals("", ocrEmpty.rawText)
        assertTrue(ocrEmpty.summary.contains("No readable text"))

        val ocrPresent = VisionFormatter.formatOcrResult("Invoice #1042\nTotal: $49.00")
        assertEquals(VisionScanMode.OCR, ocrPresent.mode)
        assertTrue(ocrPresent.summary.contains("Invoice #1042"))

        val barcodes = VisionFormatter.formatBarcodeResult(
            listOf(
                "URL" to "https://developer.android.com/jetpack",
                "Text/QR" to "javascript:alert(1)",
                "Wi-Fi" to "WIFI:S:HomeNet;;"
            )
        )
        assertEquals(listOf("https://developer.android.com/jetpack"), barcodes.detectedUrls)
        assertTrue(barcodes.summary.contains("3 found"))

        val labels = VisionFormatter.formatObjectLabels(
            listOf("Desk" to 0.72f, "Laptop" to 0.94f)
        )
        assertEquals("Laptop, Desk", labels.rawText)
        assertTrue(labels.summary.contains("Laptop (94%)"))
        assertTrue(labels.summary.contains("Desk (72%)"))
    }

    @Test
    fun consentPolicyRefusesRemoteUploadWithoutExplicitUserConsent() = runTest {
        val vault = InMemorySecureVault().apply {
            saveSecret(AndroidKeystoreVault.KEY_GEMINI_TOKEN, "sample-gemini-key")
        }
        val prefs = FakePreferencesRepository(
            JarvisPreferences(
                aiProviderConfig = AiProviderConfig(activeProvider = AiProviderType.GEMINI_REST)
            )
        )
        val transport = RecordingVisionTransport()
        val policy = VisionConsentPolicy(
            preferencesRepository = prefs,
            secureVault = vault,
            telemetryProvider = VisionTestTelemetry(isOnline = true),
            remoteTransport = transport
        )

        val disclosure = policy.describeConsent()
        assertTrue(disclosure.requiresRemoteUpload)
        assertTrue(disclosure.disclosureMessage.contains("Google's Gemini API"))

        val blocked = policy.analyzePreparedImageWithConsent(
            jpegBase64 = "ZmFrZQ==",
            prompt = "Describe this screenshot",
            uploadConsentGranted = false
        )
        assertTrue(blocked.isFailure)
        assertTrue(blocked.exceptionOrNull() is SecurityException)
        assertEquals(0, transport.imageCallCount)
    }

    @Test
    fun consentPolicyUploadsOnlyAfterConsentAndHonorsOfflineCheck() = runTest {
        val vault = InMemorySecureVault().apply {
            saveSecret(AndroidKeystoreVault.KEY_GEMINI_TOKEN, "sample-gemini-key")
        }
        val prefs = FakePreferencesRepository(
            JarvisPreferences(
                aiProviderConfig = AiProviderConfig(
                    activeProvider = AiProviderType.GEMINI_REST,
                    geminiModel = "gemini-2.5-flash"
                )
            )
        )
        val transport = RecordingVisionTransport()

        val offlinePolicy = VisionConsentPolicy(
            preferencesRepository = prefs,
            secureVault = vault,
            telemetryProvider = VisionTestTelemetry(isOnline = false),
            remoteTransport = transport
        )
        val offlineAttempt = offlinePolicy.analyzePreparedImageWithConsent(
            jpegBase64 = "ZmFrZQ==",
            prompt = "Describe",
            uploadConsentGranted = true
        )
        assertTrue(offlineAttempt.isFailure)
        assertTrue(offlineAttempt.exceptionOrNull()?.message.orEmpty().contains("offline", ignoreCase = true))

        val onlinePolicy = VisionConsentPolicy(
            preferencesRepository = prefs,
            secureVault = vault,
            telemetryProvider = VisionTestTelemetry(isOnline = true),
            remoteTransport = transport
        )
        val approved = onlinePolicy.analyzePreparedImageWithConsent(
            jpegBase64 = "ZmFrZQ==",
            prompt = "Explain this screenshot",
            uploadConsentGranted = true
        )
        assertTrue(approved.isSuccess)
        assertEquals(1, transport.imageCallCount)
        assertEquals("gemini-2.5-flash", transport.lastModel)
        assertEquals("ZmFrZQ==", transport.lastBase64)
        assertTrue(approved.getOrNull()?.summary.orEmpty().contains("Receipt for coffee"))
    }

    @Test
    fun onDevicePolicyStaysLocalWithoutRemoteUpload() = runTest {
        val prefs = FakePreferencesRepository(
            JarvisPreferences(
                aiProviderConfig = AiProviderConfig(activeProvider = AiProviderType.ON_DEVICE)
            )
        )
        val transport = RecordingVisionTransport()
        val policy = VisionConsentPolicy(
            preferencesRepository = prefs,
            secureVault = InMemorySecureVault(),
            telemetryProvider = VisionTestTelemetry(isOnline = false),
            remoteTransport = transport
        )

        val disclosure = policy.describeConsent()
        assertFalse(disclosure.requiresRemoteUpload)

        val result = policy.analyzePreparedImageWithConsent(
            jpegBase64 = "",
            prompt = "Describe",
            uploadConsentGranted = false,
            localFallbackSummary = { "Offline Object & Scene Labels (ML Kit):\n• Book (91%)" }
        )
        assertTrue(result.isSuccess)
        assertEquals(0, transport.imageCallCount)
        assertTrue(result.getOrNull()?.summary.orEmpty().contains("Book (91%)"))
    }
}

private class RecordingVisionTransport : AiRemoteTransport {
    var imageCallCount = 0
    var lastModel: String = ""
    var lastBase64: String = ""

    override suspend fun chatOllama(
        baseUrl: String,
        model: String,
        systemPrompt: String,
        messages: List<RemoteChatMessage>
    ): Result<String> = Result.success("")

    override suspend fun testOllama(baseUrl: String): Result<String> = Result.success("")

    override suspend fun chatOpenAiCompatible(
        baseUrl: String,
        model: String,
        apiKey: String,
        systemPrompt: String,
        messages: List<RemoteChatMessage>
    ): Result<String> = Result.success("")

    override suspend fun testOpenAiCompatible(baseUrl: String, apiKey: String): Result<String> =
        Result.success("")

    override suspend fun chatGemini(
        model: String,
        apiKey: String,
        systemPrompt: String,
        messages: List<RemoteChatMessage>
    ): Result<String> = Result.success("")

    override suspend fun testGemini(model: String, apiKey: String): Result<String> =
        Result.success("")

    override suspend fun analyzeImage(
        provider: AiProviderType,
        baseUrl: String,
        model: String,
        apiKey: String,
        prompt: String,
        jpegBase64: String
    ): Result<String> {
        imageCallCount++
        lastModel = model
        lastBase64 = jpegBase64
        return Result.success("Receipt for coffee totaling $4.50.")
    }
}

private class VisionTestTelemetry(
    isOnline: Boolean
) : SystemTelemetryPort {
    private val snapshot = DeviceTelemetry(
        batteryPercent = 82,
        isOnline = isOnline,
        networkTransport = if (isOnline) NetworkTransport.WIFI else NetworkTransport.OFFLINE
    )
    override val telemetry: Flow<DeviceTelemetry> = MutableStateFlow(snapshot).asStateFlow()
    override fun currentSnapshot(): DeviceTelemetry = snapshot
}
