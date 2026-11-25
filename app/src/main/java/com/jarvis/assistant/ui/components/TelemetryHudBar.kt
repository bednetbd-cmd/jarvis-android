package com.jarvis.assistant.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jarvis.assistant.domain.model.AssistantVisualState
import com.jarvis.assistant.domain.model.DeviceTelemetry
import com.jarvis.assistant.ui.theme.JarvisSurface
import com.jarvis.assistant.ui.theme.JarvisSurfaceBorder
import com.jarvis.assistant.ui.theme.JarvisTextMuted
import com.jarvis.assistant.ui.theme.JarvisTextSecondary
import com.jarvis.assistant.ui.theme.JarvisWarningAmber

@Composable
fun TelemetryHudBar(
    telemetry: DeviceTelemetry,
    visualState: AssistantVisualState,
    providerBadge: String,
    onEmergencyStop: () -> Unit,
    modifier: Modifier = Modifier,
    isVoiceMonitorActive: Boolean = false
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val isBusy = visualState != AssistantVisualState.STANDBY || isVoiceMonitorActive

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = JarvisSurface,
        border = BorderStroke(1.dp, JarvisSurfaceBorder.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (telemetry.isCharging) {
                        Icons.Rounded.BatteryChargingFull
                    } else {
                        Icons.Rounded.BatteryStd
                    },
                    contentDescription = "Battery",
                    tint = if (telemetry.batteryPercent in 0..19 && !telemetry.isCharging) {
                        JarvisWarningAmber
                    } else {
                        primaryColor
                    },
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (telemetry.batteryPercent >= 0) "${telemetry.batteryPercent}%" else "N/A",
                    style = MaterialTheme.typography.labelSmall,
                    color = JarvisTextSecondary
                )

                Spacer(modifier = Modifier.width(14.dp))

                Icon(
                    imageVector = if (telemetry.isOnline) Icons.Rounded.Wifi else Icons.Rounded.WifiOff,
                    contentDescription = "Network",
                    tint = if (telemetry.isOnline) primaryColor else JarvisTextMuted,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = telemetry.networkTransport.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (telemetry.isOnline) JarvisTextSecondary else JarvisTextMuted
                )
            }

            if (isBusy) {
                Surface(
                    onClick = onEmergencyStop,
                    shape = RoundedCornerShape(10.dp),
                    color = JarvisWarningAmber.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, JarvisWarningAmber)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.StopCircle,
                            contentDescription = "Stop JARVIS",
                            tint = JarvisWarningAmber,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "STOP",
                            style = MaterialTheme.typography.labelSmall,
                            color = JarvisWarningAmber
                        )
                    }
                }
            } else {
                Text(
                    text = "CORE · $providerBadge",
                    style = MaterialTheme.typography.labelSmall,
                    color = primaryColor
                )
            }
        }
    }
}
