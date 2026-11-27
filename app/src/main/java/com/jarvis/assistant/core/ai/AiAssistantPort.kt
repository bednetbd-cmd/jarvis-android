package com.jarvis.assistant.core.ai

import android.content.Context
import com.jarvis.assistant.core.automation.AndroidPhoneAutomation
import com.jarvis.assistant.core.automation.PhoneAutomationPort
import com.jarvis.assistant.core.device.AndroidDeviceController
import com.jarvis.assistant.core.device.DeviceControlPort
import com.jarvis.assistant.core.device.MediaAction
import com.jarvis.assistant.core.device.VolumeAdjustment
import com.jarvis.assistant.core.productivity.ProductivityPort
import com.jarvis.assistant.core.security.SensitiveActionDetector
import com.jarvis.assistant.core.tools.ClipboardPort
import com.jarvis.assistant.core.tools.ScientificCalculator
import com.jarvis.assistant.core.web.AndroidWebHttpTransport
import com.jarvis.assistant.core.web.FreeWebIntelligenceService
import com.jarvis.assistant.core.web.UnitConverter
import com.jarvis.assistant.core.web.WebIntelligencePort
import com.jarvis.assistant.data.device.AndroidSystemTelemetryProvider
import com.jarvis.assistant.data.device.SystemTelemetryPort
import com.jarvis.assistant.data.local.TaskEntity
import com.jarvis.assistant.data.preferences.JarvisPreferencesPort
import com.jarvis.assistant.data.security.AndroidKeystoreVault
import com.jarvis.assistant.data.security.SecureVaultPort
import com.jarvis.assistant.domain.model.AiProviderConfig
import com.jarvis.assistant.domain.model.AiProviderType
import com.jarvis.assistant.domain.model.ChatMessage
import com.jarvis.assistant.domain.model.MessageSender
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import java.net.URI
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

interface AiAssistantPort {
    val isConfigured: Boolean
    val engineLabel: String

    suspend fun generateReply(
        prompt: String,
        history: List<ChatMessage>
    ): Result<String>

    suspend fun testActiveConnection(): Result<String> = Result.success(engineLabel)
}

class OnDeviceJarvisAssistant(
    private val phoneAutomation: PhoneAutomationPort,
    private val deviceContext: DeviceContextPort,
    private val deviceControl: DeviceControlPort? = null,
    private val webIntelligence: WebIntelligencePort? = null,
    private val productivity: ProductivityPort? = null,
    private val clipboardPort: ClipboardPort? = null
) : AiAssistantPort {

    constructor(
        context: Context,
        phoneAutomation: PhoneAutomationPort
    ) : this(
        phoneAutomation = phoneAutomation,
        deviceContext = AndroidDeviceContextReader(context),
        deviceControl = AndroidDeviceController(context),
        webIntelligence = FreeWebIntelligenceService(
            context = context,
            http = AndroidWebHttpTransport(),
            telemetryProvider = AndroidSystemTelemetryProvider(context)
        )
    )

    override val isConfigured: Boolean = true
    override val engineLabel: String = "On-Device Engine + Android Intents"

    override suspend fun generateReply(
        prompt: String,
        history: List<ChatMessage>
    ): Result<String> {
        val cleaned = prompt.trim()
        val lower = cleaned.lowercase(Locale.getDefault())

        // 1. Greetings & identity (English + Bangla)
        if (lower in GREETINGS || lower.startsWith("hey jarvis") || lower.startsWith("hello jarvis") ||
            containsAny(lower, "হ্যালো জার্ভিস", "হাই জার্ভিস", "সালাম জার্ভিস")
        ) {
            return Result.success(
                "At your service. Voice input, spoken replies, local memory, math, diagnostics, text summarization, and phone actions are online. Ask \"help\" to see all commands."
            )
        }

        if (lower.contains("who are you") || lower.contains("what are you") || containsAny(lower, "তুমি কে", "আপনি কে")) {
            return Result.success(
                "I am JARVIS running in free On-Device Mode. I handle voice commands, calculations, extractive summarization, task checklists, live battery diagnostics, and Android phone actions offline—or connect a free Gemini, Groq, or local Ollama model in Settings."
            )
        }

        // 2. Help / capabilities
        if (lower == "help" || lower.contains("what can you do") || lower.contains("commands") || containsAny(lower, "সাহায্য", "কি করতে পারো")) {
            return Result.success(
                "Here is what I can do right now:\n" +
                    "• Voice: Tap the mic to speak (English or Bangla bn-BD), enable Hey JARVIS wake word, or say \"stop speaking\"\n" +
                    "• Time & Date: \"What time is it?\" or \"Today's date\" (বাংলা: \"সময় কত\", \"আজকের তারিখ\")\n" +
                    "• Device & Storage: \"Battery status\", \"Storage info\", or \"Device info\"\n" +
                    "• Hardware & Media: \"Flashlight on/off\", \"Volume 60%\", \"Volume up/down\", \"Brightness 70%\", \"Play pause\", \"Next track\"\n" +
                    "• Apps & Phone: \"Open YouTube\", \"List apps\", \"Open Wi-Fi\", \"Open camera\", \"Set timer 5 minutes\", \"Set alarm 7:30 am\"\n" +
                    "• Contacts, SMS & Notifications: \"Find contact <name>\", \"Call <name or number>\", \"Text <name> saying <msg>\", \"Read notifications\", \"Share <text>\"\n" +
                    "• Web Intelligence: \"Weather in Dhaka\", \"Latest news\", \"Tech news\", \"Wikipedia <topic>\", \"YouTube <query>\", \"Open website <url>\"\n" +
                    "• Productivity & Calendar: \"Daily briefing\", \"Note: <text>\", \"Add task <task>\", \"Buy <item>\", \"Remind me in 10 min to <task>\", \"Check in habit <name>\", \"My calendar\"\n" +
                    "• Converters & Translation: \"Convert 100 USD to BDT\", \"Convert 10 km to miles\", \"Translate thank you to Bangla\"\n" +
                    "• Advanced Tools: \"Calculate sin(30) + sqrt(144)\", \"Summarize clipboard\", \"Batch: Battery status; Daily briefing\", \"Summarize: <text>\", \"Plan: <goal>\""
            )
        }

        // 3. Time & Date (English + Bangla)
        if (containsAny(lower, "what time", "current time", "time is it", "time now", "সময় কত", "কয়টা বাজে", "এখন সময়") || lower == "time") {
            return Result.success(deviceContext.getCurrentTimeDate())
        }

        if (containsAny(lower, "what date", "today's date", "todays date", "what day", "current date", "আজকের তারিখ", "আজ কি বার") || lower == "date") {
            return Result.success(deviceContext.getCurrentTimeDate())
        }

        // 4. Battery, Storage & Device Diagnostics (English + Bangla)
        if (containsAny(lower, "storage info", "storage status", "free space", "check storage", "স্টোরেজ কত", "ফাঁকা জায়গা")) {
            val storage = deviceControl?.getStorageSummary()
                ?: "Storage telemetry is unavailable in this configuration."
            return Result.success(storage)
        }

        if (containsAny(lower, "battery", "charge", "power level", "ব্যাটারি", "চার্জ")) {
            return Result.success(deviceContext.getBatteryStatus())
        }

        if (containsAny(lower, "device info", "phone info", "system status", "diagnostics", "ফোনের তথ্য")) {
            val base = deviceContext.getDeviceDiagnostics()
            val storage = deviceControl?.getStorageSummary()
            return Result.success(if (storage != null) "$base\n$storage" else base)
        }

        // 5. Timers & Alarms
        if (lower.contains("timer")) {
            parseTimerSeconds(lower)?.let { seconds ->
                return phoneAutomation.executeCommand(
                    AndroidPhoneAutomation.ACTION_SET_TIMER,
                    mapOf("seconds" to seconds.toString(), "label" to "JARVIS Timer")
                )
            }
            return Result.success("Try saying \"Set timer 5 minutes\" or \"Set timer 45 seconds\".")
        }

        if (lower.contains("alarm")) {
            parseAlarmTime(lower)?.let { (hour, minute) ->
                return phoneAutomation.executeCommand(
                    AndroidPhoneAutomation.ACTION_SET_ALARM,
                    mapOf(
                        "hour" to hour.toString(),
                        "minute" to minute.toString(),
                        "label" to "JARVIS Alarm"
                    )
                )
            }
            return Result.success("Try saying \"Set alarm 7:30 am\" or \"Set alarm 18:00\".")
        }

        // 6. Hardware, Media, Notifications, Contacts, SMS, Share & App Launching
        if (containsAny(lower, "flashlight", "torch", "ফ্ল্যাশলাইট", "টর্চ")) {
            val control = deviceControl
                ?: return Result.failure(IllegalStateException("Device hardware controller is unavailable."))
            val targetState = when {
                containsAny(lower, "off", "disable", "অফ", "বন্ধ") -> false
                containsAny(lower, "on", "enable", "অন", "চালু") -> true
                else -> null
            }
            return control.setFlashlight(targetState)
        }

        if (containsAny(lower, "volume", "mute media", "unmute media", "ভলিউম", "সাউন্ড")) {
            val control = deviceControl
                ?: return Result.failure(IllegalStateException("Device audio controller is unavailable."))
            val pctMatch = Regex("""(\d{1,3})\s*%?""").find(lower)?.groupValues?.get(1)?.toIntOrNull()
            return when {
                lower.contains("unmute") -> control.adjustVolume(VolumeAdjustment.UNMUTE)
                lower.contains("mute") -> control.adjustVolume(VolumeAdjustment.MUTE)
                containsAny(lower, "up", "increase", "raise", "louder", "বাড়াও") ->
                    control.adjustVolume(VolumeAdjustment.UP)
                containsAny(lower, "down", "decrease", "lower", "quieter", "কমাও") ->
                    control.adjustVolume(VolumeAdjustment.DOWN)
                pctMatch != null -> control.adjustVolume(VolumeAdjustment.SET_PERCENT, pctMatch)
                else -> Result.success("Try \"Volume 60%\", \"Volume up\", \"Volume down\", or \"Mute media\".")
            }
        }

        if (containsAny(lower, "brightness", "ব্রাইটনেস", "আলো")) {
            val control = deviceControl
                ?: return Result.failure(IllegalStateException("Device brightness controller is unavailable."))
            val pctMatch = Regex("""(\d{1,3})\s*%?""").find(lower)?.groupValues?.get(1)?.toIntOrNull()
            return if (pctMatch != null) {
                control.setBrightnessPercent(pctMatch)
            } else {
                Result.success("Specify a brightness level, e.g. \"Set brightness 70%\".")
            }
        }

        if (containsAny(lower, "play pause", "play/pause", "pause music", "play music", "pause media", "play media", "next track", "next song", "previous track", "previous song")) {
            val control = deviceControl
                ?: return Result.failure(IllegalStateException("Media playback controller is unavailable."))
            val mediaAction = when {
                containsAny(lower, "next track", "next song") -> MediaAction.NEXT
                containsAny(lower, "previous track", "previous song") -> MediaAction.PREVIOUS
                lower.contains("pause") && !lower.contains("play") -> MediaAction.PAUSE
                lower.contains("play") && !lower.contains("pause") -> MediaAction.PLAY
                else -> MediaAction.PLAY_PAUSE
            }
            return control.controlMedia(mediaAction)
        }

        if (containsAny(lower, "open notification access", "notification settings", "notification access")) {
            val control = deviceControl
                ?: return Result.failure(IllegalStateException("Device controller is unavailable."))
            return control.openNotificationListenerSettings()
        }

        if (containsAny(lower, "read notifications", "check notifications", "my notifications", "নোটিফিকেশন পড়ো", "নোটিফিকেশন দেখাও")) {
            val control = deviceControl
                ?: return Result.failure(IllegalStateException("Device controller is unavailable."))
            return control.readRecentNotifications()
        }

        if (lower.startsWith("find contact ") || lower.startsWith("search contact ") || lower.startsWith("lookup contact ")) {
            val control = deviceControl
                ?: return Result.failure(IllegalStateException("Contacts controller is unavailable."))
            val query = cleaned.substringAfter("contact ").trim()
            return control.findContacts(query).map { contacts ->
                if (contacts.isEmpty()) {
                    "No contacts found matching \"$query\"."
                } else {
                    "Contacts matching \"$query\":\n" + contacts.joinToString("\n") { "• ${it.first}: ${it.second}" }
                }
            }
        }

        if (lower.startsWith("text ") || lower.startsWith("sms ") || lower.startsWith("message ")) {
            val control = deviceControl
                ?: return Result.failure(IllegalStateException("SMS controller is unavailable."))
            val afterVerb = cleaned.substringAfter(' ').trim()
            val (recipient, body) = when {
                afterVerb.contains(" saying ", ignoreCase = true) -> {
                    val idx = afterVerb.indexOf(" saying ", ignoreCase = true)
                    afterVerb.substring(0, idx).trim() to afterVerb.substring(idx + " saying ".length).trim()
                }
                afterVerb.contains(":") -> {
                    afterVerb.substringBefore(':').trim() to afterVerb.substringAfter(':').trim()
                }
                else -> afterVerb to ""
            }
            return control.draftSms(recipient, body)
        }

        if (lower.startsWith("share ")) {
            val control = deviceControl
                ?: return Result.failure(IllegalStateException("Share controller is unavailable."))
            return control.shareText(cleaned.substringAfter(' ').trim())
        }

        if (containsAny(lower, "list apps", "installed apps", "show apps")) {
            val control = deviceControl
                ?: return Result.failure(IllegalStateException("App launcher is unavailable."))
            return control.listInstalledApps()
        }

        // 7. Phone Actions (Settings, Camera, Installed Apps, Search, Dialer - English + Bangla)
        if (containsAny(lower, "open wi-fi", "open wifi", "wi-fi settings", "wifi settings", "ওয়াইফাই খোলো", "ওয়াইফাই সেটিংস")) {
            return phoneAutomation.executeCommand(AndroidPhoneAutomation.ACTION_OPEN_WIFI)
        }
        if (containsAny(lower, "open bluetooth", "bluetooth settings", "ব্লুটুথ খোলো", "ব্লুটুথ সেটিংস")) {
            return phoneAutomation.executeCommand(AndroidPhoneAutomation.ACTION_OPEN_BLUETOOTH)
        }
        if (containsAny(lower, "open settings", "device settings", "সেটিংস খোলো")) {
            return phoneAutomation.executeCommand(AndroidPhoneAutomation.ACTION_OPEN_SETTINGS)
        }
        if (containsAny(lower, "open camera", "take a photo", "launch camera", "ক্যামেরা খোলো", "ছবি তোলো")) {
            return phoneAutomation.executeCommand(AndroidPhoneAutomation.ACTION_OPEN_CAMERA)
        }

        if (lower.startsWith("open website ") || lower.startsWith("visit ") || lower.startsWith("browse ")) {
            val target = cleaned.substringAfter(' ').trim()
                .removePrefix("website ")
                .trim()
            val web = webIntelligence
                ?: return Result.failure(IllegalStateException("Web browser launcher is unavailable."))
            return web.openWebsite(target)
        }

        if (lower.startsWith("open ") || lower.startsWith("launch ")) {
            val appQuery = cleaned.substringAfter(' ').trim()
            if (looksLikeWebUrl(appQuery) && webIntelligence != null) {
                return webIntelligence.openWebsite(appQuery)
            }
            val control = deviceControl
            if (control != null && appQuery.isNotEmpty()) {
                return control.launchApp(appQuery)
            }
        }

        // 7b. Free Web Intelligence, Converters & Translation
        UnitConverter.parseConversionQuery(cleaned)?.let { req ->
            return UnitConverter.convert(req.value, req.fromUnit, req.toUnit)
        }

        parseCurrencyConversion(cleaned)?.let { (amount, fromCode, toCode) ->
            val web = webIntelligence
                ?: return Result.failure(IllegalStateException("Currency service is unavailable."))
            return web.convertCurrency(amount, fromCode, toCode)
        }

        if (containsAny(lower, "weather", "forecast", "আবহাওয়া")) {
            val web = webIntelligence
                ?: return Result.failure(IllegalStateException("Weather service is unavailable."))
            val city = extractWeatherCity(cleaned)
            return web.getWeather(city)
        }

        if (containsAny(lower, "news", "headlines", "খবর")) {
            val web = webIntelligence
                ?: return Result.failure(IllegalStateException("News service is unavailable."))
            return web.getNews(cleaned)
        }

        if (lower.startsWith("wikipedia ") || lower.startsWith("wiki ") ||
            lower.startsWith("who is ") || lower.startsWith("tell me about ")
        ) {
            val web = webIntelligence
                ?: return Result.failure(IllegalStateException("Wikipedia service is unavailable."))
            val query = when {
                lower.startsWith("wikipedia ") -> cleaned.substring("wikipedia ".length)
                lower.startsWith("wiki ") -> cleaned.substring("wiki ".length)
                lower.startsWith("who is ") -> cleaned.substring("who is ".length)
                else -> cleaned.substring("tell me about ".length)
            }.trim().trimEnd('?')
            return web.searchWikipedia(query)
        }

        if (lower.startsWith("youtube ") || lower.startsWith("search youtube ") ||
            lower.startsWith("search on youtube ") || lower.startsWith("play on youtube ")
        ) {
            val web = webIntelligence
                ?: return Result.failure(IllegalStateException("YouTube search launcher is unavailable."))
            val query = when {
                lower.startsWith("search on youtube ") -> cleaned.substring("search on youtube ".length)
                lower.startsWith("play on youtube ") -> cleaned.substring("play on youtube ".length)
                lower.startsWith("search youtube ") -> cleaned.substring("search youtube ".length)
                else -> cleaned.substring("youtube ".length)
            }.trim()
            return web.searchYouTube(query)
        }

        if (lower.startsWith("translate ")) {
            val web = webIntelligence
                ?: return Result.failure(IllegalStateException("Translation helper is unavailable."))
            val body = cleaned.substring("translate ".length).trim()
            val match = Regex("""^(.+?)\s+(?:to|into)\s+([a-zA-Z]+)$""", RegexOption.IGNORE_CASE).find(body)
            val phrase = match?.groupValues?.get(1)?.trim() ?: body
            val targetLang = match?.groupValues?.get(2)?.trim() ?: "bangla"
            return Result.success(web.translateOfflineOrHint(phrase, targetLang))
        }

        // 7c. Personal Productivity, Room Storage, Reminders, Habits, Calendar & Routines
        if (containsAny(lower, "daily briefing", "morning briefing", "my briefing")) {
            val prod = productivity
                ?: return Result.failure(IllegalStateException("Productivity engine is unavailable."))
            return Result.success(prod.buildDailyBriefing())
        }

        if (lower.startsWith("note:") || lower.startsWith("save note ") || lower.startsWith("add note ")) {
            val prod = productivity
                ?: return Result.failure(IllegalStateException("Notes storage is unavailable."))
            val body = when {
                lower.startsWith("note:") -> cleaned.substringAfter(':')
                lower.startsWith("save note ") -> cleaned.substring("save note ".length)
                else -> cleaned.substring("add note ".length)
            }.trim()
            val (title, content) = if (body.contains(" - ")) {
                body.substringBefore(" - ").trim() to body.substringAfter(" - ").trim()
            } else {
                body.take(36) to body
            }
            return prod.addNote(title = title, content = content)
        }

        if (containsAny(lower, "list notes", "my notes", "show notes")) {
            val prod = productivity
                ?: return Result.failure(IllegalStateException("Notes storage is unavailable."))
            return Result.success(prod.getNotesSummary())
        }

        if (lower.startsWith("remind me")) {
            val prod = productivity
                ?: return Result.failure(IllegalStateException("Reminder scheduler is unavailable."))
            val parsed = parseReminderIntent(cleaned)
            return if (parsed != null) {
                prod.scheduleReminder(title = parsed.first, delayMinutes = parsed.second)
            } else {
                Result.success("Specify when to remind you, e.g. \"Remind me in 15 minutes to check the oven\".")
            }
        }

        if (containsAny(lower, "my reminders", "list reminders", "show reminders")) {
            val prod = productivity
                ?: return Result.failure(IllegalStateException("Reminder scheduler is unavailable."))
            return Result.success(prod.getRemindersSummary())
        }

        if (lower.startsWith("buy ") || lower.startsWith("add to shopping ") || lower.startsWith("shopping:")) {
            val prod = productivity
                ?: return Result.failure(IllegalStateException("Shopping list is unavailable."))
            val item = when {
                lower.startsWith("buy ") -> cleaned.substring("buy ".length)
                lower.startsWith("add to shopping ") -> cleaned.substring("add to shopping ".length)
                else -> cleaned.substringAfter(':')
            }.trim()
            return prod.addTask(title = item, category = TaskEntity.CATEGORY_SHOPPING)
        }

        if (containsAny(lower, "shopping list", "my shopping")) {
            val prod = productivity
                ?: return Result.failure(IllegalStateException("Shopping list is unavailable."))
            return Result.success(prod.getTasksSummary(TaskEntity.CATEGORY_SHOPPING))
        }

        if (lower.startsWith("add task ") || lower.startsWith("todo ") || lower.startsWith("todo:") || lower.startsWith("plan today ")) {
            val prod = productivity
                ?: return Result.failure(IllegalStateException("Task storage is unavailable."))
            val (category, title) = when {
                lower.startsWith("plan today ") -> TaskEntity.CATEGORY_PLANNER to cleaned.substring("plan today ".length).trim()
                lower.startsWith("todo:") -> TaskEntity.CATEGORY_TODO to cleaned.substringAfter(':').trim()
                lower.startsWith("todo ") -> TaskEntity.CATEGORY_TODO to cleaned.substring("todo ".length).trim()
                else -> TaskEntity.CATEGORY_TODO to cleaned.substring("add task ".length).trim()
            }
            return prod.addTask(title = title, category = category)
        }

        if (containsAny(lower, "my tasks", "show tasks", "list tasks", "to-do list", "todo list", "daily planner")) {
            val prod = productivity
                ?: return Result.failure(IllegalStateException("Task storage is unavailable."))
            return Result.success(prod.getTasksSummary())
        }

        if (lower.startsWith("add habit ") || lower.startsWith("track habit ")) {
            val prod = productivity
                ?: return Result.failure(IllegalStateException("Habit tracker is unavailable."))
            val habitName = cleaned.substringAfter("habit ").trim()
            return prod.addHabit(habitName)
        }

        if (lower.startsWith("check in habit ") || lower.startsWith("complete habit ")) {
            val prod = productivity
                ?: return Result.failure(IllegalStateException("Habit tracker is unavailable."))
            val habitName = cleaned.substringAfter("habit ").trim()
            return prod.checkInHabitByName(habitName)
        }

        if (containsAny(lower, "my habits", "show habits", "habit streak")) {
            val prod = productivity
                ?: return Result.failure(IllegalStateException("Habit tracker is unavailable."))
            return Result.success(prod.getHabitsSummary())
        }

        if (containsAny(lower, "my calendar", "today's events", "todays events", "upcoming events", "calendar events")) {
            val prod = productivity
                ?: return Result.failure(IllegalStateException("Calendar service is unavailable."))
            return prod.readUpcomingCalendarSummary(daysAhead = 2)
        }

        if (lower.startsWith("schedule event ") || lower.startsWith("add event ") || lower.startsWith("create event ")) {
            val prod = productivity
                ?: return Result.failure(IllegalStateException("Calendar service is unavailable."))
            val eventTitle = cleaned.substringAfter("event ").trim()
            return prod.openCalendarEventComposer(eventTitle)
        }

        if (containsAny(lower, "my routines", "list routines", "show routines")) {
            val prod = productivity
                ?: return Result.failure(IllegalStateException("Routine manager is unavailable."))
            return Result.success(prod.getRoutinesSummary())
        }

        if (lower.startsWith("run routine ")) {
            val prod = productivity
                ?: return Result.failure(IllegalStateException("Routine manager is unavailable."))
            val routineQuery = cleaned.substring("run routine ".length).trim()
            val (routineName, steps) = prod.resolveRoutineCommands(routineQuery)
                .getOrElse { return Result.failure(it) }
            val stepOutputs = mutableListOf<String>()
            for (step in steps.take(6)) {
                if (step.lowercase(Locale.US).startsWith("run routine")) continue
                val stepResult = generateReply(step, history)
                val formatted = stepResult.fold(
                    onSuccess = { it },
                    onFailure = { "Error: ${it.message ?: "failed"}" }
                )
                stepOutputs.add("[$step]\n$formatted")
            }
            return Result.success("Routine \"$routineName\" completed:\n\n" + stepOutputs.joinToString("\n\n"))
        }

        if (lower.startsWith("search ") || lower.startsWith("google ")) {
            val query = cleaned.substringAfter(' ').trim()
            return phoneAutomation.executeCommand(
                AndroidPhoneAutomation.ACTION_WEB_SEARCH,
                mapOf("query" to query)
            )
        }

        if (lower.startsWith("call ") || lower.startsWith("dial ")) {
            val target = cleaned.substringAfter(' ').trim()
            val resolvedNumber = if (target.any { it.isLetter() } && deviceControl != null) {
                val contacts = deviceControl.findContacts(target)
                    .getOrElse { return Result.failure(it) }
                contacts.firstOrNull()?.second
                    ?: return Result.failure(IllegalStateException("No contact found matching \"$target\"."))
            } else {
                target
            }
            return phoneAutomation.executeCommand(
                AndroidPhoneAutomation.ACTION_DIAL,
                mapOf("number" to resolvedNumber)
            )
        }

        // 7. Offline Text Utilities (Summarization, Planning, Bullets, Word Count)
        if (lower.startsWith("summarize:") || lower.startsWith("summary:")) {
            val sourceText = cleaned.substringAfter(':').trim()
            return Result.success(summarizeOffline(sourceText))
        }

        if (lower.startsWith("plan:") || lower.startsWith("checklist:")) {
            val goal = cleaned.substringAfter(':').trim()
            return Result.success(buildOfflinePlan(goal))
        }

        if (lower.startsWith("bullets:")) {
            val sourceText = cleaned.substringAfter(':').trim()
            return Result.success(toBulletPoints(sourceText))
        }

        if (lower.startsWith("word count:") || lower.startsWith("analyze text:")) {
            val sourceText = cleaned.substringAfter(':').trim()
            return Result.success(analyzeTextStats(sourceText))
        }

        // 8. Recent memory recall
        if (containsAny(lower, "what did i say", "what did i ask", "last message")) {
            val previousUserMessage = history
                .dropLast(1)
                .lastOrNull { it.sender == MessageSender.USER }
            return if (previousUserMessage != null) {
                Result.success("Your previous message was: \"${previousUserMessage.content}\"")
            } else {
                Result.success("You haven't sent an earlier message in the current history.")
            }
        }

        // 8b. Multi-step Batch execution & Clipboard assistant
        SensitiveActionDetector.parseBatchCommands(cleaned)?.let { steps ->
            val outputs = mutableListOf<String>()
            for ((index, step) in steps.withIndex()) {
                currentCoroutineContext().ensureActive()
                val stepLower = step.lowercase(Locale.US)
                if (stepLower.startsWith("batch:") || stepLower.startsWith("multi-step:") || stepLower.startsWith("steps:")) {
                    continue
                }
                val stepReply = generateReply(step, history).fold(
                    onSuccess = { it },
                    onFailure = { "Error: ${it.message ?: "failed"}" }
                )
                outputs.add("Step ${index + 1} [$step]:\n$stepReply")
            }
            return Result.success("Batch Execution (${outputs.size} steps):\n\n" + outputs.joinToString("\n\n"))
        }

        if (containsAny(lower, "read clipboard", "check clipboard", "summarize clipboard", "analyze clipboard", "bullets clipboard")) {
            val clipboard = clipboardPort
                ?: return Result.failure(IllegalStateException("Clipboard assistant is unavailable."))
            val snapshot = clipboard.readText()
            if (!snapshot.isAvailable) {
                return Result.success(snapshot.note)
            }
            return when {
                lower.contains("summarize") -> Result.success(summarizeOffline(snapshot.text))
                lower.contains("bullet") -> Result.success(toBulletPoints(snapshot.text))
                lower.contains("analyze") -> Result.success(analyzeTextStats(snapshot.text))
                else -> Result.success("Clipboard Content:\n${snapshot.text}")
            }
        }

        // 9. Math / Scientific Calculator / Percentage evaluation
        evaluatePercentage(lower)?.let { answer ->
            return Result.success("The answer is $answer.")
        }

        val explicitCalcCandidate = when {
            lower.startsWith("calculate ") -> cleaned.substring("calculate ".length).trim()
            lower.startsWith("compute ") -> cleaned.substring("compute ".length).trim()
            lower.startsWith("eval ") -> cleaned.substring("eval ".length).trim()
            ScientificCalculator.looksLikeExpression(cleaned) -> cleaned
            else -> null
        }
        if (!explicitCalcCandidate.isNullOrBlank()) {
            val sciResult = ScientificCalculator.evaluate(explicitCalcCandidate)
            if (sciResult.isSuccess) {
                val formatted = ScientificCalculator.format(sciResult.getOrThrow())
                return Result.success("$explicitCalcCandidate = $formatted")
            }
        }

        val mathCandidate = extractMathExpression(lower)
        if (mathCandidate != null) {
            ScientificCalculator.evaluate(mathCandidate).getOrNull()?.let { value ->
                return Result.success("$mathCandidate = ${ScientificCalculator.format(value)}")
            }
            MathExpressionEvaluator.evaluate(mathCandidate)?.let { value ->
                return Result.success("$mathCandidate = ${formatNumber(value)}")
            }
        }

        // 10. Honest offline assistant fallback
        return Result.success(
            "I'm currently running in free On-Device Mode (no external generative LLM connected). " +
                "I can evaluate math, check time/battery/device status, summarize text (\"Summarize: ...\"), plan checklists (\"Plan: ...\"), set alarms & timers, open settings/camera, or search the web. " +
                "Open Settings -> AI Intelligence Engine to connect a free Gemini, Groq/OpenAI-compatible, or local Ollama model, or say \"search $cleaned\"."
        )
    }

    private fun summarizeOffline(sourceText: String): String {
        if (sourceText.isBlank()) {
            return "Provide text after \"Summarize:\" to create an extractive offline summary."
        }
        val sentences = sourceText
            .split(Regex("(?<=[.!?])\\s+|\\n+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (sentences.size <= 2) {
            return "Summary: ${sentences.joinToString(" ")}"
        }

        val wordFrequencies = sourceText.lowercase(Locale.getDefault())
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length > 3 && it !in STOP_WORDS }
            .groupingBy { it }
            .eachCount()

        val scored = sentences.mapIndexed { index, sentence ->
            val words = sentence.lowercase(Locale.getDefault())
                .split(Regex("[^a-z0-9]+"))
                .filter { it.length > 3 }
            val score = words.sumOf { wordFrequencies[it] ?: 0 } + if (index == 0) 2 else 0
            Triple(index, sentence, score)
        }

        val topSentences = scored
            .sortedByDescending { it.third }
            .take(minOf(3, maxOf(2, sentences.size / 2)))
            .sortedBy { it.first }
            .map { it.second }

        return "Offline Summary:\n" + topSentences.joinToString("\n") { "• $it" }
    }

    private fun buildOfflinePlan(goal: String): String {
        if (goal.isBlank()) {
            return "Provide a goal after \"Plan:\" (e.g., \"Plan: prepare weekly sprint review\")."
        }
        return "Action Plan for \"$goal\":\n" +
            "1. Define exact outcome & deadline for $goal.\n" +
            "2. Break into 2-3 small deliverables you can finish today.\n" +
            "3. Gather required files, notes, or contacts.\n" +
            "4. Set a reminder or timer to review progress.\n" +
            "(Tip: Connect a remote AI provider in Settings for deep domain planning.)"
    }

    private fun toBulletPoints(sourceText: String): String {
        if (sourceText.isBlank()) {
            return "Provide text after \"Bullets:\" to format it into bullet points."
        }
        val parts = sourceText
            .split(Regex("[.\\n;]+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return parts.joinToString("\n") { "• $it" }
    }

    private fun analyzeTextStats(sourceText: String): String {
        if (sourceText.isBlank()) {
            return "Provide text after \"Word count:\" to inspect length."
        }
        val words = sourceText.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val sentences = sourceText.split(Regex("[.!?]+")).filter { it.isNotBlank() }
        return "Text Analysis:\n" +
            "• Words: ${words.size}\n" +
            "• Characters: ${sourceText.length}\n" +
            "• Sentences: ${sentences.size}"
    }

    private fun parseTimerSeconds(text: String): Int? {
        val regex = Regex("""(\d+)\s*(h|hr|hrs|hour|hours|m|min|mins|minute|minutes|s|sec|secs|second|seconds)\b""")
        val matches = regex.findAll(text).toList()
        if (matches.isEmpty()) return null

        var totalSeconds = 0
        for (match in matches) {
            val amount = match.groupValues[1].toIntOrNull() ?: continue
            val unit = match.groupValues[2]
            totalSeconds += when {
                unit.startsWith("h") -> amount * 3600
                unit.startsWith("m") -> amount * 60
                else -> amount
            }
        }
        return totalSeconds.takeIf { it > 0 }
    }

    private fun parseAlarmTime(text: String): Pair<Int, Int>? {
        val regex = Regex("""\b(\d{1,2})(?::(\d{2}))?\s*(am|pm)?\b""")
        val match = regex.find(text) ?: return null
        var hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: 0
        val meridiem = match.groupValues[3]

        if (minute !in 0..59) return null
        if (meridiem == "pm" && hour in 1..11) hour += 12
        if (meridiem == "am" && hour == 12) hour = 0
        if (hour !in 0..23) return null

        return hour to minute
    }

    private fun evaluatePercentage(text: String): String? {
        val regex = Regex("""(\d+(?:\.\d+)?)\s*%\s*of\s*(\d+(?:\.\d+)?)""")
        val match = regex.find(text) ?: return null
        val pct = match.groupValues[1].toDoubleOrNull() ?: return null
        val base = match.groupValues[2].toDoubleOrNull() ?: return null
        return formatNumber((pct / 100.0) * base)
    }

    private fun extractMathExpression(text: String): String? {
        val stripped = text
            .replace("what is", "")
            .replace("what's", "")
            .replace("calculate", "")
            .replace("compute", "")
            .replace("equals", "")
            .replace("plus", "+")
            .replace("minus", "-")
            .replace("times", "*")
            .replace("multiplied by", "*")
            .replace("divided by", "/")
            .replace("x", "*")
            .replace("?", "")
            .trim()

        if (stripped.isEmpty()) return null
        val validChars = stripped.all { it.isDigit() || it in "+-*/(). " }
        val hasOperator = stripped.any { it in "+-*/" }
        val hasDigit = stripped.any { it.isDigit() }
        return if (validChars && hasOperator && hasDigit) stripped else null
    }

    private fun formatNumber(value: Double): String {
        val formatter = DecimalFormat("#.######", DecimalFormatSymbols(Locale.US))
        return formatter.format(value)
    }

    private fun parseReminderIntent(text: String): Pair<String, Long>? {
        val delayMatch = Regex(
            """in\s+(\d+)\s*(?:m|min|mins|minute|minutes|h|hr|hrs|hour|hours)\b""",
            RegexOption.IGNORE_CASE
        ).find(text) ?: return null

        val rawNum = delayMatch.groupValues[1].toLongOrNull() ?: return null
        val isHours = delayMatch.value.contains("h", ignoreCase = true)
        val minutes = if (isHours) rawNum * 60L else rawNum
        val withoutPrefix = text
            .replace(Regex("""^remind\s+me\s*""", RegexOption.IGNORE_CASE), "")
            .replace(delayMatch.value, "")
            .replace(Regex("""^\s*to\s+""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+to\s+$""", RegexOption.IGNORE_CASE), "")
            .trim()
        val title = withoutPrefix.ifEmpty { "Scheduled reminder" }
        return title to minutes.coerceAtLeast(1L)
    }

    private fun parseCurrencyConversion(text: String): Triple<Double, String, String>? {
        val regex = Regex(
            """(?:convert\s+)?(\d+(?:\.\d+)?)\s*([a-zA-Z]{3})\s+(?:to|in|into)\s+([a-zA-Z]{3})\b""",
            RegexOption.IGNORE_CASE
        )
        val match = regex.find(text.trim()) ?: return null
        val amount = match.groupValues[1].toDoubleOrNull() ?: return null
        val from = match.groupValues[2].uppercase(Locale.US)
        val to = match.groupValues[3].uppercase(Locale.US)
        if (UnitConverter.isKnownUnit(from) && UnitConverter.isKnownUnit(to)) return null
        return Triple(amount, from, to)
    }

    private fun extractWeatherCity(text: String): String {
        val cleaned = text.trim().trimEnd('?', '.')
        val inMatch = Regex("""(?:weather|forecast|আবহাওয়া)\s+(?:in|for|at|of)\s+(.+)$""", RegexOption.IGNORE_CASE)
            .find(cleaned)
        if (inMatch != null) return inMatch.groupValues[1].trim()

        return cleaned
            .replace(Regex("""^(?:what(?:'s| is)\s+(?:the\s+)?)?(?:current\s+)?(?:weather|forecast|আবহাওয়া)\s*""", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    private fun looksLikeWebUrl(text: String): Boolean {
        val lower = text.trim().lowercase(Locale.US)
        if (lower.startsWith("https://") || lower.startsWith("http://")) return true
        return !lower.contains(' ') && Regex("""^[a-z0-9.-]+\.[a-z]{2,}(/.*)?$""").matches(lower)
    }

    private fun containsAny(text: String, vararg keywords: String): Boolean {
        return keywords.any { text.contains(it) }
    }

    companion object {
        private val GREETINGS = setOf(
            "hi", "hello", "hey", "jarvis", "good morning", "good afternoon", "good evening"
        )
        private val STOP_WORDS = setOf(
            "this", "that", "with", "from", "have", "will", "would", "could", "should",
            "about", "there", "their", "which", "when", "where", "what", "into", "your"
        )
    }
}

class MultiProviderAiRouter(
    private val preferencesRepository: JarvisPreferencesPort,
    private val secureVault: SecureVaultPort,
    private val telemetryProvider: SystemTelemetryPort,
    private val onDeviceAssistant: OnDeviceJarvisAssistant,
    private val toolRegistry: JarvisToolRegistry,
    private val remoteTransport: AiRemoteTransport,
    private val deviceContext: DeviceContextPort
) : AiAssistantPort {

    override val isConfigured: Boolean = true
    override val engineLabel: String = "Multi-Provider AI Router"

    override suspend fun generateReply(
        prompt: String,
        history: List<ChatMessage>
    ): Result<String> {
        val prefs = preferencesRepository.preferences.first()
        val config = prefs.aiProviderConfig

        if (config.activeProvider == AiProviderType.ON_DEVICE) {
            return onDeviceAssistant.generateReply(prompt, history)
        }

        val networkError = checkConnectivityPrecondition(config)
        if (networkError != null) {
            return Result.failure(IllegalStateException(networkError))
        }

        val systemPrompt = buildSystemPrompt(config)
        val conversationTurns = buildConversationTurns(prompt, history).toMutableList()

        val initialReply = dispatchToRemote(config, systemPrompt, conversationTurns)
            .getOrElse { return Result.failure(it) }

        val toolCall = toolRegistry.parseToolCall(initialReply) ?: return Result.success(initialReply)

        val toolResult = toolRegistry.executeTool(toolCall)
        val toolOutputText = toolResult.fold(
            onSuccess = { "Tool '${toolCall.name}' succeeded: $it" },
            onFailure = { "Tool '${toolCall.name}' failed: ${it.message ?: "unknown error"}" }
        )

        conversationTurns.add(RemoteChatMessage(role = "assistant", content = initialReply))
        conversationTurns.add(
            RemoteChatMessage(
                role = "user",
                content = "$toolOutputText\nRespond concisely to the user based strictly on this real result."
            )
        )

        val followUp = dispatchToRemote(config, systemPrompt, conversationTurns)
        return followUp.recover {
            // If the second model round-trip fails (e.g., transient rate limit), still report the real tool outcome honestly.
            toolResult.getOrElse { toolOutputText }
        }
    }

    override suspend fun testActiveConnection(): Result<String> {
        val config = preferencesRepository.preferences.first().aiProviderConfig
        if (config.activeProvider == AiProviderType.ON_DEVICE) {
            return Result.success("On-Device Engine is ready (offline mode, zero external dependencies).")
        }

        val networkError = checkConnectivityPrecondition(config)
        if (networkError != null) {
            return Result.failure(IllegalStateException(networkError))
        }

        return when (config.activeProvider) {
            AiProviderType.ON_DEVICE -> Result.success("On-Device Engine ready.")
            AiProviderType.LOCAL_OLLAMA -> remoteTransport.testOllama(config.ollamaBaseUrl)
            AiProviderType.OPENAI_COMPATIBLE -> {
                val token = secureVault.getSecret(AndroidKeystoreVault.KEY_OPENAI_TOKEN).orEmpty()
                if (token.isBlank() && !isPrivateEndpoint(config.openAiBaseUrl)) {
                    return Result.failure(IllegalStateException("Save your API key in Settings before testing this endpoint."))
                }
                remoteTransport.testOpenAiCompatible(config.openAiBaseUrl, token)
            }
            AiProviderType.GEMINI_REST -> {
                val token = secureVault.getSecret(AndroidKeystoreVault.KEY_GEMINI_TOKEN).orEmpty()
                if (token.isBlank()) {
                    return Result.failure(IllegalStateException("Save your free Gemini API key in Settings first."))
                }
                remoteTransport.testGemini(config.geminiModel, token)
            }
        }
    }

    private suspend fun dispatchToRemote(
        config: AiProviderConfig,
        systemPrompt: String,
        messages: List<RemoteChatMessage>
    ): Result<String> {
        return when (config.activeProvider) {
            AiProviderType.ON_DEVICE -> onDeviceAssistant.generateReply(
                prompt = messages.lastOrNull()?.content.orEmpty(),
                history = emptyList()
            )
            AiProviderType.LOCAL_OLLAMA -> remoteTransport.chatOllama(
                baseUrl = config.ollamaBaseUrl,
                model = config.ollamaModel,
                systemPrompt = systemPrompt,
                messages = messages
            )
            AiProviderType.OPENAI_COMPATIBLE -> {
                val apiKey = secureVault.getSecret(AndroidKeystoreVault.KEY_OPENAI_TOKEN).orEmpty()
                if (apiKey.isBlank() && !isPrivateEndpoint(config.openAiBaseUrl)) {
                    return Result.failure(
                        IllegalStateException(
                            "No API key configured for ${config.openAiBaseUrl}. Add your free-tier key in Settings -> AI Intelligence Engine, or switch to On-Device Engine."
                        )
                    )
                }
                remoteTransport.chatOpenAiCompatible(
                    baseUrl = config.openAiBaseUrl,
                    model = config.openAiModel,
                    apiKey = apiKey,
                    systemPrompt = systemPrompt,
                    messages = messages
                )
            }
            AiProviderType.GEMINI_REST -> {
                val apiKey = secureVault.getSecret(AndroidKeystoreVault.KEY_GEMINI_TOKEN).orEmpty()
                if (apiKey.isBlank()) {
                    return Result.failure(
                        IllegalStateException(
                            "No Gemini API key configured. Get a free key from Google AI Studio and save it in Settings -> AI Intelligence Engine, or switch to On-Device Engine."
                        )
                    )
                }
                remoteTransport.chatGemini(
                    model = config.geminiModel,
                    apiKey = apiKey,
                    systemPrompt = systemPrompt,
                    messages = messages
                )
            }
        }
    }

    private fun checkConnectivityPrecondition(config: AiProviderConfig): String? {
        val isOnline = telemetryProvider.currentSnapshot().isOnline
        if (isOnline) return null

        val localUrl = when (config.activeProvider) {
            AiProviderType.LOCAL_OLLAMA -> config.ollamaBaseUrl
            AiProviderType.OPENAI_COMPATIBLE -> config.openAiBaseUrl
            else -> ""
        }
        val host = runCatching { URI(localUrl.trim()).host.orEmpty() }.getOrDefault("")
        val isLoopback = host.equals("localhost", ignoreCase = true) ||
            host == "127.0.0.1" ||
            host == "::1"

        return if (isLoopback) {
            null
        } else {
            "Device is offline (${config.activeProvider.displayName} requires a network connection). Connect to Wi-Fi/Cellular or switch to On-Device Engine in Settings."
        }
    }

    private fun isPrivateEndpoint(url: String): Boolean {
        val host = runCatching { URI(url.trim()).host.orEmpty() }.getOrDefault("")
        return host.isNotBlank() && EndpointUrlValidator.isPrivateOrLoopbackHost(host)
    }

    private fun buildSystemPrompt(config: AiProviderConfig): String {
        return buildString {
            appendLine(config.personality.systemPromptDirective)
            appendLine()
            appendLine("Live device context:")
            appendLine(deviceContext.getCurrentTimeDate())
            appendLine(deviceContext.getBatteryStatus())
            appendLine()
            append(toolRegistry.buildToolSystemDirective())
        }
    }

    private fun buildConversationTurns(
        prompt: String,
        history: List<ChatMessage>
    ): List<RemoteChatMessage> {
        val relevant = history
            .filter { it.sender == MessageSender.USER || it.sender == MessageSender.ASSISTANT }
            .takeLast(MAX_CONTEXT_MESSAGES)
            .map { msg ->
                RemoteChatMessage(
                    role = if (msg.sender == MessageSender.USER) "user" else "assistant",
                    content = msg.content
                )
            }
            .toMutableList()

        val cleanedPrompt = prompt.trim()
        val last = relevant.lastOrNull()
        if (cleanedPrompt.isNotEmpty() && !(last?.role == "user" && last.content.trim() == cleanedPrompt)) {
            relevant.add(RemoteChatMessage(role = "user", content = cleanedPrompt))
        }
        return relevant
    }

    private companion object {
        const val MAX_CONTEXT_MESSAGES = 12
    }
}

internal object MathExpressionEvaluator {
    fun evaluate(expression: String): Double? {
        return runCatching {
            val parser = Parser(expression)
            val result = parser.parseExpression()
            if (parser.isAtEnd() && result.isFinite()) result else null
        }.getOrNull()
    }

    private class Parser(private val input: String) {
        private var pos = 0

        fun isAtEnd(): Boolean {
            skipWhitespace()
            return pos >= input.length
        }

        fun parseExpression(): Double {
            var value = parseTerm()
            while (true) {
                skipWhitespace()
                value = when {
                    match('+') -> value + parseTerm()
                    match('-') -> value - parseTerm()
                    else -> return value
                }
            }
        }

        private fun parseTerm(): Double {
            var value = parseFactor()
            while (true) {
                skipWhitespace()
                value = when {
                    match('*') -> value * parseFactor()
                    match('/') -> {
                        val divisor = parseFactor()
                        if (divisor == 0.0) throw ArithmeticException("Division by zero")
                        value / divisor
                    }
                    else -> return value
                }
            }
        }

        private fun parseFactor(): Double {
            skipWhitespace()
            if (match('+')) return parseFactor()
            if (match('-')) return -parseFactor()
            if (match('(')) {
                val inner = parseExpression()
                if (!match(')')) throw IllegalArgumentException("Missing closing parenthesis")
                return inner
            }
            val start = pos
            while (pos < input.length && (input[pos].isDigit() || input[pos] == '.')) {
                pos++
            }
            if (start == pos) throw IllegalArgumentException("Unexpected character")
            return input.substring(start, pos).toDouble()
        }

        private fun match(expected: Char): Boolean {
            skipWhitespace()
            if (pos < input.length && input[pos] == expected) {
                pos++
                return true
            }
            return false
        }

        private fun skipWhitespace() {
            while (pos < input.length && input[pos].isWhitespace()) {
                pos++
            }
        }
    }
}
