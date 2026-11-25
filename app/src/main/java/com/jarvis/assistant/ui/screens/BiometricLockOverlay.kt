package com.jarvis.assistant.ui.screens

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.jarvis.assistant.core.security.BiometricAuthPort
import com.jarvis.assistant.core.security.BiometricCapability
import com.jarvis.assistant.ui.theme.JarvisBackground
import com.jarvis.assistant.ui.theme.JarvisSurface
import com.jarvis.assistant.ui.theme.JarvisTextMuted
import com.jarvis.assistant.ui.theme.JarvisTextPrimary
import com.jarvis.assistant.ui.theme.JarvisTextSecondary
import com.jarvis.assistant.ui.theme.JarvisWarningAmber

@Composable
fun BiometricLockOverlay(
    biometricAuth: BiometricAuthPort?,
    capability: BiometricCapability,
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    val accent = MaterialTheme.colorScheme.primary
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun triggerPrompt() {
        errorMessage = null
        if (biometricAuth == null || activity == null) {
            onUnlocked()
            return
        }
        if (capability != BiometricCapability.AVAILABLE) {
            errorMessage = capability.label
            return
        }
        biometricAuth.authenticate(
            activity = activity,
            title = "Unlock JARVIS Assistant",
            subtitle = "Verify fingerprint, face, or device PIN to access encrypted memory and tools",
            onSuccess = onUnlocked,
            onError = { msg -> errorMessage = msg }
        )
    }

    LaunchedEffect(Unit) {
        triggerPrompt()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(JarvisBackground)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = JarvisSurface,
            border = BorderStroke(1.dp, accent.copy(alpha = 0.65f))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = accent.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, accent),
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = "JARVIS Locked",
                            tint = accent,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }

                Text(
                    text = "JARVIS VAULT LOCKED",
                    style = MaterialTheme.typography.titleMedium,
                    color = accent
                )

                Text(
                    text = "Private conversation memory, Android Keystore keys, and system actions are protected.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = JarvisTextSecondary,
                    textAlign = TextAlign.Center
                )

                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = JarvisWarningAmber,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text(
                        text = capability.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = JarvisTextMuted,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = ::triggerPrompt,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = JarvisBackground
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Fingerprint,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Authenticate to Unlock")
                }
            }
        }
    }
}

private tailrec fun Context.findFragmentActivity(): FragmentActivity? {
    return when (this) {
        is FragmentActivity -> this
        is ContextWrapper -> baseContext.findFragmentActivity()
        else -> null
    }
}
