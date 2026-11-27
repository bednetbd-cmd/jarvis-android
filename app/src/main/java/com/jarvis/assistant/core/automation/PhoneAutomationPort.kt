package com.jarvis.assistant.core.automation

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import java.util.Locale

interface PhoneAutomationPort {
    suspend fun executeCommand(
        actionName: String,
        params: Map<String, String> = emptyMap()
    ): Result<String>
}

class AndroidPhoneAutomation(
    context: Context
) : PhoneAutomationPort {

    private val appContext = context.applicationContext

    override suspend fun executeCommand(
        actionName: String,
        params: Map<String, String>
    ): Result<String> {
        return when (actionName) {
            ACTION_SET_ALARM -> setAlarm(params)
            ACTION_SET_TIMER -> setTimer(params)
            ACTION_OPEN_WIFI -> launchIntent(
                Intent(Settings.ACTION_WIFI_SETTINGS),
                "Opening Wi-Fi settings."
            )
            ACTION_OPEN_BLUETOOTH -> launchIntent(
                Intent(Settings.ACTION_BLUETOOTH_SETTINGS),
                "Opening Bluetooth settings."
            )
            ACTION_OPEN_SETTINGS -> launchIntent(
                Intent(Settings.ACTION_SETTINGS),
                "Opening device settings."
            )
            ACTION_OPEN_CAMERA -> launchIntent(
                Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
                "Opening camera."
            )
            ACTION_WEB_SEARCH -> webSearch(params["query"].orEmpty())
            ACTION_DIAL -> dialNumber(params["number"].orEmpty())
            else -> Result.failure(UnsupportedOperationException("Unknown action: $actionName"))
        }
    }

    private fun setAlarm(params: Map<String, String>): Result<String> {
        val hour = params["hour"]?.toIntOrNull()?.coerceIn(0, 23)
            ?: return Result.failure(IllegalArgumentException("Please specify a valid alarm hour."))
        val minute = params["minute"]?.toIntOrNull()?.coerceIn(0, 59) ?: 0
        val label = params["label"]?.takeIf { it.isNotBlank() } ?: "JARVIS Alarm"

        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        }
        val formattedTime = String.format(Locale.US, "%02d:%02d", hour, minute)
        return launchIntent(intent, "Setting alarm for $formattedTime ($label).")
    }

    private fun setTimer(params: Map<String, String>): Result<String> {
        val seconds = params["seconds"]?.toIntOrNull()?.coerceAtLeast(1)
            ?: return Result.failure(IllegalArgumentException("Please specify timer duration."))
        val label = params["label"]?.takeIf { it.isNotBlank() } ?: "JARVIS Timer"

        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        }
        return launchIntent(intent, "Setting timer for ${formatDuration(seconds)}.")
    }

    private fun webSearch(query: String): Result<String> {
        if (query.isBlank()) {
            return Result.failure(IllegalArgumentException("Please specify what to search for."))
        }
        val searchIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra(SearchManager.QUERY, query)
        }
        val primary = launchIntent(searchIntent, "Searching the web for \"$query\".")
        if (primary.isSuccess) return primary

        val browserUri = Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")
        return launchIntent(
            Intent(Intent.ACTION_VIEW, browserUri),
            "Opening web search for \"$query\"."
        )
    }

    private fun dialNumber(number: String): Result<String> {
        val cleaned = number.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
        if (cleaned.isBlank()) {
            return Result.failure(IllegalArgumentException("Please provide a phone number to dial."))
        }
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleaned"))
        return launchIntent(intent, "Opening dialer for $cleaned.")
    }

    private fun launchIntent(intent: Intent, successMessage: String): Result<String> {
        return runCatching {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(intent)
            successMessage
        }.recoverCatching { error ->
            throw IllegalStateException(
                "No compatible app found on this device to complete that action.",
                error
            )
        }
    }

    private fun formatDuration(totalSeconds: Int): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val secs = totalSeconds % 60
        return buildList {
            if (hours > 0) add("${hours}h")
            if (minutes > 0) add("${minutes}m")
            if (secs > 0 || isEmpty()) add("${secs}s")
        }.joinToString(" ")
    }

    companion object {
        const val ACTION_SET_ALARM = "set_alarm"
        const val ACTION_SET_TIMER = "set_timer"
        const val ACTION_OPEN_WIFI = "open_wifi_settings"
        const val ACTION_OPEN_BLUETOOTH = "open_bluetooth_settings"
        const val ACTION_OPEN_SETTINGS = "open_settings"
        const val ACTION_OPEN_CAMERA = "open_camera"
        const val ACTION_WEB_SEARCH = "web_search"
        const val ACTION_DIAL = "dial"
    }
}
