package com.jarvis.assistant.core.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.jarvis.assistant.core.ai.AiRemoteTransport
import com.jarvis.assistant.core.ai.EndpointUrlValidator
import com.jarvis.assistant.data.device.SystemTelemetryPort
import com.jarvis.assistant.data.preferences.JarvisPreferencesPort
import com.jarvis.assistant.data.security.AndroidKeystoreVault
import com.jarvis.assistant.data.security.SecureVaultPort
import com.jarvis.assistant.domain.model.AiProviderConfig
import com.jarvis.assistant.domain.model.AiProviderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.URI
import java.util.Base64
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.roundToInt

enum class VisionScanMode(val label: String) {
    OCR("OCR / Document"),
    QR_BARCODE("QR / Barcode"),
    OBJECT_LABEL("Objects / Scene"),
    AI_DESCRIBE("AI Describe / Screenshot")
}

data class VisionScanOutput(
    val mode: VisionScanMode,
    val summary: String,
    val rawText: String = "",
    val detectedUrls: List<String> = emptyList(),
    val labels: List<Pair<String, Float>> = emptyList()
)

data class VisionConsentInfo(
    val provider: AiProviderType,
    val requiresRemoteUpload: Boolean,
    val destinationLabel: String,
    val disclosureMessage: String
)

interface VisionAnalysisPort {
    suspend fun loadScaledBitmapFromUri(uri: Uri, maxDimension: Int = 1280): Result<Bitmap>
    suspend fun scanBitmapOffline(bitmap: Bitmap, mode: VisionScanMode): Result<VisionScanOutput>
    suspend fun describeConsentRequirement(): VisionConsentInfo
    suspend fun analyzeWithConsent(
        bitmap: Bitmap,
        prompt: String,
        uploadConsentGranted: Boolean
    ): Result<VisionScanOutput>
}

object VisionFormatter {
    fun formatOcrResult(text: String): VisionScanOutput {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) {
            return VisionScanOutput(
                mode = VisionScanMode.OCR,
                summary = "No readable text detected in this image. Try better lighting, sharper focus, or moving closer to the document.",
                rawText = ""
            )
        }
        return VisionScanOutput(
            mode = VisionScanMode.OCR,
            summary = "Offline OCR Extracted Text:\n$cleaned",
            rawText = cleaned
        )
    }

    fun formatBarcodeResult(items: List<Pair<String, String>>): VisionScanOutput {
        val cleaned = items.filter { it.second.isNotBlank() }
        if (cleaned.isEmpty()) {
            return VisionScanOutput(
                mode = VisionScanMode.QR_BARCODE,
                summary = "No QR code or barcode detected in this frame."
            )
        }
        val safeUrls = cleaned
            .map { it.second.trim() }
            .filter { value ->
                val lower = value.lowercase(Locale.US)
                lower.startsWith("https://") || lower.startsWith("http://")
            }
            .distinct()

        val formatted = buildString {
            appendLine("Offline QR / Barcode Scan (${cleaned.size} found):")
            cleaned.forEach { (typeLabel, value) ->
                appendLine("• [$typeLabel] $value")
            }
        }.trim()

        return VisionScanOutput(
            mode = VisionScanMode.QR_BARCODE,
            summary = formatted,
            rawText = cleaned.joinToString("\n") { it.second },
            detectedUrls = safeUrls
        )
    }

    fun formatObjectLabels(labels: List<Pair<String, Float>>): VisionScanOutput {
        if (labels.isEmpty()) {
            return VisionScanOutput(
                mode = VisionScanMode.OBJECT_LABEL,
                summary = "No high-confidence object labels detected locally."
            )
        }
        val sorted = labels.sortedByDescending { it.second }.take(8)
        val formatted = buildString {
            appendLine("Offline Object & Scene Labels (ML Kit):")
            sorted.forEach { (name, confidence) ->
                val pct = (confidence * 100f).roundToInt().coerceIn(0, 100)
                appendLine("• $name ($pct%)")
            }
        }.trim()

        return VisionScanOutput(
            mode = VisionScanMode.OBJECT_LABEL,
            summary = formatted,
            rawText = sorted.joinToString(", ") { it.first },
            labels = sorted
        )
    }
}

class VisionConsentPolicy(
    private val preferencesRepository: JarvisPreferencesPort,
    private val secureVault: SecureVaultPort,
    private val telemetryProvider: SystemTelemetryPort,
    private val remoteTransport: AiRemoteTransport
) {
    suspend fun describeConsent(): VisionConsentInfo {
        val config = preferencesRepository.preferences.first().aiProviderConfig
        return buildConsentDisclosure(config)
    }

    fun buildConsentDisclosure(config: AiProviderConfig): VisionConsentInfo {
        return when (config.activeProvider) {
            AiProviderType.ON_DEVICE -> VisionConsentInfo(
                provider = AiProviderType.ON_DEVICE,
                requiresRemoteUpload = false,
                destinationLabel = "100% On-Device (No Upload)",
                disclosureMessage = "On-Device Mode runs offline ML Kit OCR + Object Labeling locally and never uploads your image. To get full natural-language AI screenshot or scene descriptions, connect Gemini Free-Tier REST or a local Ollama vision model in Settings."
            )
            AiProviderType.LOCAL_OLLAMA -> {
                val host = runCatching { URI(config.ollamaBaseUrl.trim()).host.orEmpty() }.getOrDefault("")
                val isLocal = host.isNotBlank() && EndpointUrlValidator.isPrivateOrLoopbackHost(host)
                VisionConsentInfo(
                    provider = AiProviderType.LOCAL_OLLAMA,
                    requiresRemoteUpload = !isLocal,
                    destinationLabel = "Ollama (${config.ollamaModel} @ ${config.ollamaBaseUrl})",
                    disclosureMessage = "This will send the compressed image to your configured Ollama server (${config.ollamaBaseUrl}, model: ${config.ollamaModel}). Ensure a vision-capable model such as llava or llama3.2-vision is selected."
                )
            }
            AiProviderType.OPENAI_COMPATIBLE -> VisionConsentInfo(
                provider = AiProviderType.OPENAI_COMPATIBLE,
                requiresRemoteUpload = true,
                destinationLabel = "${config.openAiModel} @ ${config.openAiBaseUrl}",
                disclosureMessage = "Your image will be uploaded over HTTPS to ${config.openAiBaseUrl} using model ${config.openAiModel}. Only proceed if you consent to sending this image to that provider."
            )
            AiProviderType.GEMINI_REST -> VisionConsentInfo(
                provider = AiProviderType.GEMINI_REST,
                requiresRemoteUpload = true,
                destinationLabel = "Google Gemini REST (${config.geminiModel})",
                disclosureMessage = "Your image will be uploaded over HTTPS to Google's Gemini API (${config.geminiModel}) using your saved free-tier API key. Only proceed if you consent to uploading this image."
            )
        }
    }

    suspend fun analyzePreparedImageWithConsent(
        jpegBase64: String,
        prompt: String,
        uploadConsentGranted: Boolean,
        localFallbackSummary: (() -> String)? = null
    ): Result<VisionScanOutput> {
        val config = preferencesRepository.preferences.first().aiProviderConfig
        if (config.activeProvider == AiProviderType.ON_DEVICE) {
            val fallback = localFallbackSummary?.invoke().orEmpty().trim()
            val message = if (fallback.isNotEmpty()) {
                "$fallback\n\n(Note: On-Device Engine provides offline OCR & object labels without uploading. Connect Gemini REST or local Ollama vision in Settings for full generative image/screenshot descriptions.)"
            } else {
                "On-Device Engine runs offline OCR, QR, and object labeling without uploading. Connect Gemini REST or local Ollama (e.g. llava) in Settings for generative AI image description."
            }
            return Result.success(
                VisionScanOutput(
                    mode = VisionScanMode.AI_DESCRIBE,
                    summary = message,
                    rawText = fallback
                )
            )
        }

        if (!uploadConsentGranted) {
            return Result.failure(
                SecurityException("Explicit user consent is required before sending an image to ${config.activeProvider.displayName}.")
            )
        }

        val (baseUrl, model, token) = when (config.activeProvider) {
            AiProviderType.ON_DEVICE -> Triple("", "", "")
            AiProviderType.LOCAL_OLLAMA -> Triple(config.ollamaBaseUrl, config.ollamaModel, "")
            AiProviderType.OPENAI_COMPATIBLE -> Triple(
                config.openAiBaseUrl,
                config.openAiModel,
                secureVault.getSecret(AndroidKeystoreVault.KEY_OPENAI_TOKEN).orEmpty()
            )
            AiProviderType.GEMINI_REST -> Triple(
                "",
                config.geminiModel,
                secureVault.getSecret(AndroidKeystoreVault.KEY_GEMINI_TOKEN).orEmpty()
            )
        }

        val isLoopback = runCatching {
            val host = URI(baseUrl.trim()).host.orEmpty()
            host.equals("localhost", ignoreCase = true) || host == "127.0.0.1" || host == "::1"
        }.getOrDefault(false)

        if (!isLoopback && !telemetryProvider.currentSnapshot().isOnline) {
            return Result.failure(
                IllegalStateException("Device is offline. Connect to Wi-Fi/Cellular to send this image to ${config.activeProvider.displayName}, or use offline ML Kit tools.")
            )
        }

        return remoteTransport.analyzeImage(
            provider = config.activeProvider,
            baseUrl = baseUrl,
            model = model,
            apiKey = token,
            prompt = prompt,
            jpegBase64 = jpegBase64
        ).map { description ->
            VisionScanOutput(
                mode = VisionScanMode.AI_DESCRIBE,
                summary = "AI Vision (${config.activeProvider.displayName}):\n$description",
                rawText = description
            )
        }
    }
}

class AndroidVisionAnalyzer(
    context: Context,
    private val consentPolicy: VisionConsentPolicy
) : VisionAnalysisPort {

    private val appContext = context.applicationContext
    private val textRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }
    private val barcodeScanner by lazy {
        BarcodeScanning.getClient()
    }
    private val imageLabeler by lazy {
        ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
    }

    override suspend fun loadScaledBitmapFromUri(uri: Uri, maxDimension: Int): Result<Bitmap> =
        withContext(Dispatchers.IO) {
            runCatching {
                val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                appContext.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, boundsOpts)
                } ?: throw IllegalStateException("Unable to read selected image URI.")

                var sampleSize = 1
                val largest = max(boundsOpts.outWidth, boundsOpts.outHeight)
                while (largest / sampleSize > maxDimension) {
                    sampleSize *= 2
                }

                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                appContext.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, decodeOpts)
                } ?: throw IllegalStateException("Failed to decode selected image.")
            }
        }

    override suspend fun scanBitmapOffline(bitmap: Bitmap, mode: VisionScanMode): Result<VisionScanOutput> =
        withContext(Dispatchers.Default) {
            runCatching {
                val inputImage = InputImage.fromBitmap(bitmap, 0)
                when (mode) {
                    VisionScanMode.OCR -> {
                        val visionText = textRecognizer.process(inputImage).awaitResult()
                        VisionFormatter.formatOcrResult(visionText.text)
                    }
                    VisionScanMode.QR_BARCODE -> {
                        val barcodes = barcodeScanner.process(inputImage).awaitResult()
                        val pairs = barcodes.mapNotNull { barcode ->
                            val value = barcode.rawValue ?: barcode.displayValue
                            if (value.isNullOrBlank()) null else barcodeTypeLabel(barcode.valueType) to value
                        }
                        VisionFormatter.formatBarcodeResult(pairs)
                    }
                    VisionScanMode.OBJECT_LABEL -> {
                        val labels = imageLabeler.process(inputImage).awaitResult()
                            .map { it.text to it.confidence }
                        VisionFormatter.formatObjectLabels(labels)
                    }
                    VisionScanMode.AI_DESCRIBE -> {
                        val textResult = textRecognizer.process(inputImage).awaitResult().text.trim()
                        val labels = imageLabeler.process(inputImage).awaitResult()
                            .map { it.text to it.confidence }
                        val parts = buildList {
                            if (labels.isNotEmpty()) add(VisionFormatter.formatObjectLabels(labels).summary)
                            if (textResult.isNotEmpty()) add(VisionFormatter.formatOcrResult(textResult).summary)
                        }
                        val combined = if (parts.isEmpty()) {
                            "No local text or object labels detected."
                        } else {
                            parts.joinToString("\n\n")
                        }
                        VisionScanOutput(
                            mode = VisionScanMode.AI_DESCRIBE,
                            summary = combined,
                            rawText = textResult,
                            labels = labels
                        )
                    }
                }
            }
        }

    override suspend fun describeConsentRequirement(): VisionConsentInfo =
        consentPolicy.describeConsent()

    override suspend fun analyzeWithConsent(
        bitmap: Bitmap,
        prompt: String,
        uploadConsentGranted: Boolean
    ): Result<VisionScanOutput> {
        val consentInfo = consentPolicy.describeConsent()
        if (consentInfo.provider == AiProviderType.ON_DEVICE) {
            val localOffline = scanBitmapOffline(bitmap, VisionScanMode.AI_DESCRIBE).getOrNull()
            return consentPolicy.analyzePreparedImageWithConsent(
                jpegBase64 = "",
                prompt = prompt,
                uploadConsentGranted = false,
                localFallbackSummary = { localOffline?.summary.orEmpty() }
            )
        }
        if (!uploadConsentGranted) {
            return Result.failure(
                SecurityException("Explicit user consent is required before uploading an image.")
            )
        }
        val base64 = encodeScaledJpegBase64(bitmap, maxDimension = 1024)
        return consentPolicy.analyzePreparedImageWithConsent(
            jpegBase64 = base64,
            prompt = prompt,
            uploadConsentGranted = true
        )
    }

    private suspend fun encodeScaledJpegBase64(bitmap: Bitmap, maxDimension: Int): String =
        withContext(Dispatchers.Default) {
            val width = bitmap.width
            val height = bitmap.height
            val scale = if (max(width, height) > maxDimension) {
                maxDimension.toFloat() / max(width, height).toFloat()
            } else {
                1f
            }
            val target = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    bitmap,
                    max(1, (width * scale).roundToInt()),
                    max(1, (height * scale).roundToInt()),
                    true
                )
            } else {
                bitmap
            }
            val stream = ByteArrayOutputStream()
            target.compress(Bitmap.CompressFormat.JPEG, 82, stream)
            if (target !== bitmap) {
                target.recycle()
            }
            Base64.getEncoder().encodeToString(stream.toByteArray())
        }

    private fun barcodeTypeLabel(type: Int): String = when (type) {
        Barcode.TYPE_URL -> "URL"
        Barcode.TYPE_WIFI -> "Wi-Fi"
        Barcode.TYPE_EMAIL -> "Email"
        Barcode.TYPE_PHONE -> "Phone"
        Barcode.TYPE_SMS -> "SMS"
        Barcode.TYPE_CONTACT_INFO -> "Contact"
        Barcode.TYPE_PRODUCT -> "Product"
        else -> "Text/QR"
    }
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result ->
        if (continuation.isActive) continuation.resume(result)
    }
    addOnFailureListener { error ->
        if (continuation.isActive) continuation.resumeWithException(error)
    }
    addOnCanceledListener {
        continuation.cancel()
    }
}
