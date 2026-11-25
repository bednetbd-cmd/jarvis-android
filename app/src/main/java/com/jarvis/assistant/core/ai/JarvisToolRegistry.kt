package com.jarvis.assistant.core.ai

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import com.jarvis.assistant.core.automation.AndroidPhoneAutomation
import com.jarvis.assistant.core.automation.PhoneAutomationPort
import com.jarvis.assistant.core.device.DeviceControlPort
import com.jarvis.assistant.core.device.MediaAction
import com.jarvis.assistant.core.device.VolumeAdjustment
import com.jarvis.assistant.core.productivity.ProductivityPort
import com.jarvis.assistant.core.tools.ClipboardPort
import com.jarvis.assistant.core.tools.ScientificCalculator
import com.jarvis.assistant.core.web.UnitConverter
import com.jarvis.assistant.core.web.WebIntelligencePort
import com.jarvis.assistant.data.local.TaskEntity
import org.json.JSONObject
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class JarvisToolDefinition(
    val name: String,
    val description: String,
    val parameters: String
)

data class ParsedToolCall(
    val name: String,
    val arguments: Map<String, String>
)

interface DeviceContextPort {
    fun getCurrentTimeDate(): String
    fun getBatteryStatus(): String
    fun getDeviceDiagnostics(): String
}

class AndroidDeviceContextReader(
    context: Context
) : DeviceContextPort {

    private val appContext = context.applicationContext

    override fun getCurrentTimeDate(): String {
        val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
        val dateStr = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(Date())
        return "Current time: $timeStr. Today's date: $dateStr."
    }

    override fun getBatteryStatus(): String {
        val batteryManager = appContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val level = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        val isCharging = batteryManager?.isCharging == true
        return if (level in 0..100) {
            val chargeText = if (isCharging) "and currently charging" else "and running on battery"
            "Battery is at $level% $chargeText."
        } else {
            "Battery telemetry is unavailable on this device."
        }
    }

    override fun getDeviceDiagnostics(): String {
        val battery = getBatteryStatus()
        return "Device: ${Build.MANUFACTURER} ${Build.MODEL}\n" +
            "Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
            battery
    }
}

class JarvisToolRegistry(
    private val phoneAutomation: PhoneAutomationPort,
    private val deviceContext: DeviceContextPort,
    private val deviceControl: DeviceControlPort? = null,
    private val webIntelligence: WebIntelligencePort? = null,
    private val productivity: ProductivityPort? = null,
    private val clipboardPort: ClipboardPort? = null
) {
    val definitions: List<JarvisToolDefinition> = listOf(
        JarvisToolDefinition(
            name = TOOL_TIME_DATE,
            description = "Get the device's current local time and calendar date.",
            parameters = "{}"
        ),
        JarvisToolDefinition(
            name = TOOL_BATTERY_STATUS,
            description = "Read live battery percentage and charging status.",
            parameters = "{}"
        ),
        JarvisToolDefinition(
            name = TOOL_STORAGE_INFO,
            description = "Read free and total internal storage space.",
            parameters = "{}"
        ),
        JarvisToolDefinition(
            name = TOOL_DEVICE_DIAGNOSTICS,
            description = "Read device model, Android version, battery, and storage status.",
            parameters = "{}"
        ),
        JarvisToolDefinition(
            name = TOOL_CALCULATE_MATH,
            description = "Evaluate an exact arithmetic or scientific expression (sin, cos, tan, sqrt, log, ln, ^, !, pi, e).",
            parameters = "{\"expression\": \"string\"}"
        ),
        JarvisToolDefinition(
            name = TOOL_TOGGLE_FLASHLIGHT,
            description = "Turn camera flashlight on, off, or toggle it.",
            parameters = "{\"enabled\": \"true | false | toggle\"}"
        ),
        JarvisToolDefinition(
            name = TOOL_LAUNCH_APP,
            description = "Launch an installed Android app by name.",
            parameters = "{\"app_name\": \"string\"}"
        ),
        JarvisToolDefinition(
            name = TOOL_LIST_APPS,
            description = "List installed launchable applications on the device.",
            parameters = "{}"
        ),
        JarvisToolDefinition(
            name = TOOL_SET_VOLUME,
            description = "Adjust media volume (up, down, mute, unmute, or percent 0-100).",
            parameters = "{\"action\": \"up|down|mute|unmute|set\", \"percent\": \"integer 0-100\"}"
        ),
        JarvisToolDefinition(
            name = TOOL_SET_BRIGHTNESS,
            description = "Set screen brightness percentage (5-100) when Modify System Settings is granted.",
            parameters = "{\"percent\": \"integer 5-100\"}"
        ),
        JarvisToolDefinition(
            name = TOOL_MEDIA_CONTROL,
            description = "Control active media playback.",
            parameters = "{\"action\": \"play_pause|play|pause|next|previous\"}"
        ),
        JarvisToolDefinition(
            name = TOOL_READ_NOTIFICATIONS,
            description = "Read recent notifications captured while Notification Access is enabled.",
            parameters = "{}"
        ),
        JarvisToolDefinition(
            name = TOOL_FIND_CONTACT,
            description = "Search contacts by display name (requires READ_CONTACTS).",
            parameters = "{\"query\": \"string\"}"
        ),
        JarvisToolDefinition(
            name = TOOL_DRAFT_SMS,
            description = "Open the SMS composer prefilled with recipient and message (never sends automatically).",
            parameters = "{\"recipient\": \"phone or contact name\", \"body\": \"string\"}"
        ),
        JarvisToolDefinition(
            name = TOOL_SHARE_TEXT,
            description = "Open the Android system share sheet for text.",
            parameters = "{\"text\": \"string\"}"
        ),
        JarvisToolDefinition(
            name = TOOL_SET_TIMER,
            description = "Open the Android clock timer with duration in seconds.",
            parameters = "{\"seconds\": \"integer\", \"label\": \"string (optional)\"}"
        ),
        JarvisToolDefinition(
            name = TOOL_SET_ALARM,
            description = "Open the Android alarm screen for an hour (0-23) and minute (0-59).",
            parameters = "{\"hour\": \"integer 0-23\", \"minute\": \"integer 0-59\", \"label\": \"string (optional)\"}"
        ),
        JarvisToolDefinition(
            name = TOOL_OPEN_WIFI,
            description = "Open Android Wi-Fi settings.",
            parameters = "{}"
        ),
        JarvisToolDefinition(
            name = TOOL_OPEN_BLUETOOTH,
            description = "Open Android Bluetooth settings.",
            parameters = "{}"
        ),
        JarvisToolDefinition(
            name = TOOL_OPEN_SETTINGS,
            description = "Open Android main system settings.",
            parameters = "{}"
        ),
        JarvisToolDefinition(
            name = TOOL_OPEN_CAMERA,
            description = "Launch the Android camera app.",
            parameters = "{}"
        ),
        JarvisToolDefinition(
            name = TOOL_WEB_SEARCH,
            description = "Open an Android web search for a query.",
            parameters = "{\"query\": \"string\"}"
        ),
        JarvisToolDefinition(
            name = TOOL_DIAL_NUMBER,
            description = "Open the Android phone dialer prefilled with a number (never places call automatically).",
            parameters = "{\"number\": \"string\"}"
        ),
        JarvisToolDefinition(
            name = TOOL_GET_WEATHER,
            description = "Fetch live weather and today's temperature range for a city using Open-Meteo.",
            parameters = "{\"city\": \"string\"}"
        ),
        JarvisToolDefinition(
            name = TOOL_SEARCH_WIKIPEDIA,
            description = "Look up an encyclopedia summary on Wikipedia.",
            parameters = "{\"query\": \"string\"}"
        ),
        JarvisToolDefinition(
            name = TOOL_GET_NEWS,
            description = "Fetch latest news headlines (world, tech, business, or asia/bangladesh).",
            parameters = "{\"topic\": \"world|tech|business|bangladesh\"}"
        ),
        JarvisToolDefinition(
            name = TOOL_CONVERT_CURRENCY,
            description = "Convert an amount between two 3-letter currency codes (e.g., USD to BDT).",
            parameters = "{\"amount\": \"number\", \"from\": \"3-letter code\", \"to\": \"3-letter code\"}"
        ),
        JarvisToolDefinition(
            name = TOOL_CONVERT_UNITS,
            description = "Convert between standard length, mass, temperature, data, speed, or time units.",
            parameters = "{\"value\": \"number\", \"from_unit\": \"string\", \"to_unit\": \"string\"}"
        ),
        JarvisToolDefinition(
            name = TOOL_YOUTUBE_SEARCH,
            description = "Open a search query in YouTube.",
            parameters = "{\"query\": \"string\"}"
        ),
        JarvisToolDefinition(
            name = TOOL_OPEN_WEBSITE,
            description = "Open an HTTPS/HTTP website or domain in the device browser.",
            parameters = "{\"url\": \"string\"}"
        ),
        JarvisToolDefinition(
            name = TOOL_SAVE_NOTE,
            description = "Save a personal note to the local Room database.",
            parameters = "{\"title\": \"string\", \"content\": \"string\"}"
        ),
        JarvisToolDefinition(
            name = TOOL_ADD_TASK,
            description = "Add an item to the To-Do list, Daily Planner, or Shopping list.",
            parameters = "{\"title\": \"string\", \"category\": \"TODO|PLANNER|SHOPPING\"}"
        ),
        JarvisToolDefinition(
            name = TOOL_SCHEDULE_REMINDER,
            description = "Schedule a persistent local notification reminder in N minutes.",
            parameters = "{\"title\": \"string\", \"delay_minutes\": \"integer\"}"
        ),
        JarvisToolDefinition(
            name = TOOL_DAILY_BRIEFING,
            description = "Build today's summary briefing (date, battery, calendar, tasks, reminders, habits).",
            parameters = "{}"
        ),
        JarvisToolDefinition(
            name = TOOL_LIST_CALENDAR,
            description = "Read upcoming events from the Android Calendar (requires READ_CALENDAR).",
            parameters = "{\"days_ahead\": \"integer (default 1)\"}"
        ),
        JarvisToolDefinition(
            name = TOOL_CLIPBOARD_READ,
            description = "Read current text from the Android Clipboard when requested by the user.",
            parameters = "{}"
        )
    )

    fun buildToolSystemDirective(): String {
        val toolList = definitions.joinToString("\n") { def ->
            "- ${def.name}: ${def.description} Args: ${def.parameters}"
        }
        return buildString {
            appendLine("Available Android device tools:")
            appendLine(toolList)
            appendLine()
            appendLine(
                "Tool calling rule: ONLY if the user's request requires reading device state, computing exact math, " +
                    "or triggering one of the Android actions above, reply ONLY with a single line in this exact format:"
            )
            appendLine("[TOOL_CALL: {\"name\":\"tool_name\",\"args\":{\"key\":\"value\"}}]")
            appendLine("Do not invent fake tool results. If no tool is needed, answer directly in plain text.")
        }
    }

    fun parseToolCall(rawResponse: String): ParsedToolCall? {
        val cleaned = rawResponse.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val markerIndex = cleaned.indexOf("[TOOL_CALL:")
        val jsonCandidate = if (markerIndex >= 0) {
            val afterMarker = cleaned.substring(markerIndex + "[TOOL_CALL:".length)
            val endBracket = afterMarker.lastIndexOf(']')
            if (endBracket > 0) {
                afterMarker.substring(0, endBracket).trim()
            } else {
                afterMarker.trim()
            }
        } else if (cleaned.startsWith("{") && cleaned.contains("\"name\"")) {
            cleaned
        } else {
            return null
        }

        val startBrace = jsonCandidate.indexOf('{')
        val endBrace = jsonCandidate.lastIndexOf('}')
        if (startBrace < 0 || endBrace <= startBrace) return null

        return runCatching {
            val json = JSONObject(jsonCandidate.substring(startBrace, endBrace + 1))
            val name = json.optString("name", "").trim()
            if (name.isEmpty() || definitions.none { it.name == name }) {
                return null
            }
            val argsObj = json.optJSONObject("args") ?: JSONObject()
            val args = buildMap {
                val keys = argsObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = argsObj.opt(key)?.toString()?.trim().orEmpty()
                    if (value.isNotEmpty()) {
                        put(key, value)
                    }
                }
            }
            ParsedToolCall(name = name, arguments = args)
        }.getOrNull()
    }

    suspend fun executeTool(call: ParsedToolCall): Result<String> {
        return when (call.name) {
            TOOL_TIME_DATE -> Result.success(deviceContext.getCurrentTimeDate())
            TOOL_BATTERY_STATUS -> Result.success(deviceContext.getBatteryStatus())
            TOOL_STORAGE_INFO -> {
                val summary = deviceControl?.getStorageSummary()
                    ?: "Storage telemetry is unavailable in this configuration."
                Result.success(summary)
            }
            TOOL_DEVICE_DIAGNOSTICS -> {
                val base = deviceContext.getDeviceDiagnostics()
                val storage = deviceControl?.getStorageSummary()
                Result.success(if (storage != null) "$base\n$storage" else base)
            }
            TOOL_CALCULATE_MATH -> {
                val expression = call.arguments["expression"].orEmpty()
                ScientificCalculator.evaluate(expression).map { value ->
                    "$expression = ${ScientificCalculator.format(value)}"
                }
            }
            TOOL_TOGGLE_FLASHLIGHT -> {
                val control = deviceControl
                    ?: return Result.failure(IllegalStateException("Device control unavailable."))
                val enabled = when (call.arguments["enabled"]?.lowercase(Locale.US)) {
                    "true", "on", "1" -> true
                    "false", "off", "0" -> false
                    else -> null
                }
                control.setFlashlight(enabled)
            }
            TOOL_LAUNCH_APP -> {
                val control = deviceControl
                    ?: return Result.failure(IllegalStateException("Device control unavailable."))
                control.launchApp(call.arguments["app_name"].orEmpty())
            }
            TOOL_LIST_APPS -> {
                val control = deviceControl
                    ?: return Result.failure(IllegalStateException("Device control unavailable."))
                control.listInstalledApps()
            }
            TOOL_SET_VOLUME -> {
                val control = deviceControl
                    ?: return Result.failure(IllegalStateException("Device control unavailable."))
                val pct = call.arguments["percent"]?.toIntOrNull()
                val action = when (call.arguments["action"]?.lowercase(Locale.US)) {
                    "up", "raise", "increase" -> VolumeAdjustment.UP
                    "down", "lower", "decrease" -> VolumeAdjustment.DOWN
                    "mute" -> VolumeAdjustment.MUTE
                    "unmute" -> VolumeAdjustment.UNMUTE
                    else -> if (pct != null) VolumeAdjustment.SET_PERCENT else VolumeAdjustment.UP
                }
                control.adjustVolume(action, pct)
            }
            TOOL_SET_BRIGHTNESS -> {
                val control = deviceControl
                    ?: return Result.failure(IllegalStateException("Device control unavailable."))
                val pct = call.arguments["percent"]?.toIntOrNull()
                    ?: return Result.failure(IllegalArgumentException("Specify brightness percentage (5-100)."))
                control.setBrightnessPercent(pct)
            }
            TOOL_MEDIA_CONTROL -> {
                val control = deviceControl
                    ?: return Result.failure(IllegalStateException("Device control unavailable."))
                val action = when (call.arguments["action"]?.lowercase(Locale.US)) {
                    "play" -> MediaAction.PLAY
                    "pause" -> MediaAction.PAUSE
                    "next" -> MediaAction.NEXT
                    "previous", "prev" -> MediaAction.PREVIOUS
                    else -> MediaAction.PLAY_PAUSE
                }
                control.controlMedia(action)
            }
            TOOL_READ_NOTIFICATIONS -> {
                val control = deviceControl
                    ?: return Result.failure(IllegalStateException("Device control unavailable."))
                control.readRecentNotifications()
            }
            TOOL_FIND_CONTACT -> {
                val control = deviceControl
                    ?: return Result.failure(IllegalStateException("Device control unavailable."))
                control.findContacts(call.arguments["query"].orEmpty()).map { contacts ->
                    if (contacts.isEmpty()) {
                        "No contacts matched that query."
                    } else {
                        "Contacts found:\n" + contacts.joinToString("\n") { "• ${it.first}: ${it.second}" }
                    }
                }
            }
            TOOL_DRAFT_SMS -> {
                val control = deviceControl
                    ?: return Result.failure(IllegalStateException("Device control unavailable."))
                control.draftSms(
                    recipientOrName = call.arguments["recipient"].orEmpty(),
                    body = call.arguments["body"].orEmpty()
                )
            }
            TOOL_SHARE_TEXT -> {
                val control = deviceControl
                    ?: return Result.failure(IllegalStateException("Device control unavailable."))
                control.shareText(call.arguments["text"].orEmpty())
            }
            TOOL_SET_TIMER -> phoneAutomation.executeCommand(
                AndroidPhoneAutomation.ACTION_SET_TIMER,
                call.arguments
            )
            TOOL_SET_ALARM -> phoneAutomation.executeCommand(
                AndroidPhoneAutomation.ACTION_SET_ALARM,
                call.arguments
            )
            TOOL_OPEN_WIFI -> phoneAutomation.executeCommand(AndroidPhoneAutomation.ACTION_OPEN_WIFI)
            TOOL_OPEN_BLUETOOTH -> phoneAutomation.executeCommand(AndroidPhoneAutomation.ACTION_OPEN_BLUETOOTH)
            TOOL_OPEN_SETTINGS -> phoneAutomation.executeCommand(AndroidPhoneAutomation.ACTION_OPEN_SETTINGS)
            TOOL_OPEN_CAMERA -> phoneAutomation.executeCommand(AndroidPhoneAutomation.ACTION_OPEN_CAMERA)
            TOOL_WEB_SEARCH -> phoneAutomation.executeCommand(
                AndroidPhoneAutomation.ACTION_WEB_SEARCH,
                call.arguments
            )
            TOOL_DIAL_NUMBER -> phoneAutomation.executeCommand(
                AndroidPhoneAutomation.ACTION_DIAL,
                call.arguments
            )
            TOOL_GET_WEATHER -> {
                val web = webIntelligence
                    ?: return Result.failure(IllegalStateException("Web intelligence service is unavailable."))
                web.getWeather(call.arguments["city"].orEmpty())
            }
            TOOL_SEARCH_WIKIPEDIA -> {
                val web = webIntelligence
                    ?: return Result.failure(IllegalStateException("Web intelligence service is unavailable."))
                web.searchWikipedia(call.arguments["query"].orEmpty())
            }
            TOOL_GET_NEWS -> {
                val web = webIntelligence
                    ?: return Result.failure(IllegalStateException("Web intelligence service is unavailable."))
                web.getNews(call.arguments["topic"].orEmpty())
            }
            TOOL_CONVERT_CURRENCY -> {
                val web = webIntelligence
                    ?: return Result.failure(IllegalStateException("Web intelligence service is unavailable."))
                val amount = call.arguments["amount"]?.toDoubleOrNull()
                    ?: return Result.failure(IllegalArgumentException("Provide a numeric amount to convert."))
                web.convertCurrency(
                    amount = amount,
                    fromCode = call.arguments["from"].orEmpty(),
                    toCode = call.arguments["to"].orEmpty()
                )
            }
            TOOL_CONVERT_UNITS -> {
                val value = call.arguments["value"]?.toDoubleOrNull()
                    ?: return Result.failure(IllegalArgumentException("Provide a numeric value to convert."))
                UnitConverter.convert(
                    value = value,
                    fromUnit = call.arguments["from_unit"].orEmpty(),
                    toUnit = call.arguments["to_unit"].orEmpty()
                )
            }
            TOOL_YOUTUBE_SEARCH -> {
                val web = webIntelligence
                    ?: return Result.failure(IllegalStateException("Web intelligence service is unavailable."))
                web.searchYouTube(call.arguments["query"].orEmpty())
            }
            TOOL_OPEN_WEBSITE -> {
                val web = webIntelligence
                    ?: return Result.failure(IllegalStateException("Web intelligence service is unavailable."))
                web.openWebsite(call.arguments["url"].orEmpty())
            }
            TOOL_SAVE_NOTE -> {
                val prod = productivity
                    ?: return Result.failure(IllegalStateException("Productivity database is unavailable."))
                prod.addNote(
                    title = call.arguments["title"].orEmpty(),
                    content = call.arguments["content"].orEmpty()
                )
            }
            TOOL_ADD_TASK -> {
                val prod = productivity
                    ?: return Result.failure(IllegalStateException("Productivity database is unavailable."))
                val cat = when (call.arguments["category"]?.uppercase(Locale.US)) {
                    "SHOPPING" -> TaskEntity.CATEGORY_SHOPPING
                    "PLANNER" -> TaskEntity.CATEGORY_PLANNER
                    else -> TaskEntity.CATEGORY_TODO
                }
                prod.addTask(title = call.arguments["title"].orEmpty(), category = cat)
            }
            TOOL_SCHEDULE_REMINDER -> {
                val prod = productivity
                    ?: return Result.failure(IllegalStateException("Productivity scheduler is unavailable."))
                val mins = call.arguments["delay_minutes"]?.toLongOrNull() ?: 15L
                prod.scheduleReminder(title = call.arguments["title"].orEmpty(), delayMinutes = mins)
            }
            TOOL_DAILY_BRIEFING -> {
                val prod = productivity
                    ?: return Result.failure(IllegalStateException("Productivity engine is unavailable."))
                Result.success(prod.buildDailyBriefing())
            }
            TOOL_LIST_CALENDAR -> {
                val prod = productivity
                    ?: return Result.failure(IllegalStateException("Calendar integration is unavailable."))
                val days = call.arguments["days_ahead"]?.toIntOrNull() ?: 1
                prod.readUpcomingCalendarSummary(days)
            }
            TOOL_CLIPBOARD_READ -> {
                val clipboard = clipboardPort
                    ?: return Result.failure(IllegalStateException("Clipboard service is unavailable."))
                val snap = clipboard.readText()
                if (snap.isAvailable) Result.success(snap.text) else Result.failure(IllegalStateException(snap.note))
            }
            else -> Result.failure(UnsupportedOperationException("Unsupported tool: ${call.name}"))
        }
    }

    companion object {
        const val TOOL_TIME_DATE = "get_time_date"
        const val TOOL_BATTERY_STATUS = "get_battery_status"
        const val TOOL_STORAGE_INFO = "get_storage_info"
        const val TOOL_DEVICE_DIAGNOSTICS = "get_device_diagnostics"
        const val TOOL_CALCULATE_MATH = "calculate_math"
        const val TOOL_TOGGLE_FLASHLIGHT = "toggle_flashlight"
        const val TOOL_LAUNCH_APP = "launch_app"
        const val TOOL_LIST_APPS = "list_apps"
        const val TOOL_SET_VOLUME = "set_volume"
        const val TOOL_SET_BRIGHTNESS = "set_brightness"
        const val TOOL_MEDIA_CONTROL = "media_control"
        const val TOOL_READ_NOTIFICATIONS = "read_notifications"
        const val TOOL_FIND_CONTACT = "find_contact"
        const val TOOL_DRAFT_SMS = "draft_sms"
        const val TOOL_SHARE_TEXT = "share_text"
        const val TOOL_SET_TIMER = "set_timer"
        const val TOOL_SET_ALARM = "set_alarm"
        const val TOOL_OPEN_WIFI = "open_wifi"
        const val TOOL_OPEN_BLUETOOTH = "open_bluetooth"
        const val TOOL_OPEN_SETTINGS = "open_settings"
        const val TOOL_OPEN_CAMERA = "open_camera"
        const val TOOL_WEB_SEARCH = "web_search"
        const val TOOL_DIAL_NUMBER = "dial_number"
        const val TOOL_GET_WEATHER = "get_weather"
        const val TOOL_SEARCH_WIKIPEDIA = "search_wikipedia"
        const val TOOL_GET_NEWS = "get_news"
        const val TOOL_CONVERT_CURRENCY = "convert_currency"
        const val TOOL_CONVERT_UNITS = "convert_units"
        const val TOOL_YOUTUBE_SEARCH = "youtube_search"
        const val TOOL_OPEN_WEBSITE = "open_website"
        const val TOOL_SAVE_NOTE = "save_note"
        const val TOOL_ADD_TASK = "add_task"
        const val TOOL_SCHEDULE_REMINDER = "schedule_reminder"
        const val TOOL_DAILY_BRIEFING = "daily_briefing"
        const val TOOL_LIST_CALENDAR = "list_calendar"
        const val TOOL_CLIPBOARD_READ = "read_clipboard"
    }
}
