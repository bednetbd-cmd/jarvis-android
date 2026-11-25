package com.jarvis.assistant.core.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.jarvis.assistant.domain.model.AiProviderType
import java.util.Locale

enum class BiometricCapability(val label: String) {
    AVAILABLE("Biometrics / Device Credential Ready"),
    NOT_ENROLLED("No fingerprint, face, or screen lock enrolled in Android Settings"),
    UNAVAILABLE("Biometric hardware unavailable on this device")
}

interface BiometricAuthPort {
    fun checkCapability(): BiometricCapability
    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    )
}

class AndroidBiometricAuthenticator(
    context: Context
) : BiometricAuthPort {

    private val appContext = context.applicationContext
    private val allowedAuthenticators =
        BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

    override fun checkCapability(): BiometricCapability {
        val manager = BiometricManager.from(appContext)
        return when (manager.canAuthenticate(allowedAuthenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricCapability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricCapability.NOT_ENROLLED
            else -> BiometricCapability.UNAVAILABLE
        }
    }

    override fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (checkCapability() != BiometricCapability.AVAILABLE) {
            onError(checkCapability().label)
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                    errorCode != BiometricPrompt.ERROR_CANCELED
                ) {
                    onError(errString.toString())
                }
            }

            override fun onAuthenticationFailed() {
                // System prompt keeps retry open; no-op avoids premature abort.
            }
        }

        val prompt = BiometricPrompt(activity, executor, callback)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(allowedAuthenticators)
            .build()

        prompt.authenticate(promptInfo)
    }
}

enum class SensitiveActionType {
    PHONE_CALL,
    SMS_DRAFT,
    CLEAR_MEMORY,
    CLEAR_API_KEY
}

data class PendingSensitiveAction(
    val type: SensitiveActionType,
    val title: String,
    val description: String,
    val commandPayload: String? = null,
    val providerType: AiProviderType? = null
)

object SensitiveActionDetector {

    fun inspectChatCommand(command: String): PendingSensitiveAction? {
        val trimmed = command.trim()
        val lower = trimmed.lowercase(Locale.US)

        if (lower.startsWith("call ") || lower.startsWith("dial ")) {
            val target = trimmed.substringAfter(' ').trim()
            if (target.isNotEmpty()) {
                return PendingSensitiveAction(
                    type = SensitiveActionType.PHONE_CALL,
                    title = "Confirm Phone Dialer Action",
                    description = "JARVIS will open the Android Phone dialer for \"$target\". Confirm before proceeding.",
                    commandPayload = trimmed
                )
            }
        }

        if (lower.startsWith("text ") || lower.startsWith("sms ") || lower.startsWith("message ")) {
            val target = trimmed.substringAfter(' ').trim()
            if (target.isNotEmpty()) {
                return PendingSensitiveAction(
                    type = SensitiveActionType.SMS_DRAFT,
                    title = "Confirm SMS Draft Action",
                    description = "JARVIS will open your SMS app to draft a message for \"$target\". Confirm before proceeding.",
                    commandPayload = trimmed
                )
            }
        }

        return null
    }

    fun parseBatchCommands(input: String): List<String>? {
        val trimmed = input.trim()
        val lower = trimmed.lowercase(Locale.US)
        val body = when {
            lower.startsWith("batch:") -> trimmed.substringAfter(':')
            lower.startsWith("multi-step:") -> trimmed.substringAfter(':')
            lower.startsWith("steps:") -> trimmed.substringAfter(':')
            else -> return null
        }.trim()

        val steps = body.split(';', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(6)

        return steps.takeIf { it.isNotEmpty() }
    }
}
