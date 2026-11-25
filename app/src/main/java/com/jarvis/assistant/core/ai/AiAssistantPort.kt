package com.jarvis.assistant.core.ai

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import com.jarvis.assistant.core.automation.AndroidPhoneAutomation
import com.jarvis.assistant.core.automation.PhoneAutomationPort
import com.jarvis.assistant.domain.model.ChatMessage
import com.jarvis.assistant.domain.model.MessageSender
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

interface AiAssistantPort {
    val isConfigured: Boolean
    val engineLabel: String

    suspend fun generateReply(
        prompt: String,
        history: List<ChatMessage>
    ): Result<String>
}

class OnDeviceJarvisAssistant(
    context: Context,
    private val phoneAutomation: PhoneAutomationPort
) : AiAssistantPort {

    private val appContext = context.applicationContext
    override val isConfigured: Boolean = true
    override val engineLabel: String = "On-Device Assistant + Android Intents"

    override suspend fun generateReply(
        prompt: String,
        history: List<ChatMessage>
    ): Result<String> {
        val cleaned = prompt.trim()
        val lower = cleaned.lowercase(Locale.getDefault())

        // 1. Greetings & identity
        if (lower in GREETINGS || lower.startsWith("hey jarvis") || lower.startsWith("hello jarvis")) {
            return Result.success(
                "At your service. Voice input, spoken replies, local memory, math, diagnostics, and phone actions are online. Ask \"help\" to see all commands."
            )
        }

        if (lower.contains("who are you") || lower.contains("what are you")) {
            return Result.success(
                "I am JARVIS running in free On-Device Mode. I handle voice commands, calculations, time/date, live battery diagnostics, and Android phone actions offline—no paid APIs required."
            )
        }

        // 2. Help / capabilities
        if (lower == "help" || lower.contains("what can you do") || lower.contains("commands")) {
            return Result.success(
                "Here is what I can do right now:\n" +
                    "• Voice: Tap the mic to speak, or toggle speaker replies in the top bar\n" +
                    "• Time & Date: \"What time is it?\" or \"Today's date\"\n" +
                    "• Device Status: \"Battery status\" or \"Device info\"\n" +
                    "• Math: \"Calculate (24 * 6) + 15\" or \"15% of 200\"\n" +
                    "• Timers & Alarms: \"Set timer 5 minutes\" or \"Set alarm 7:30 am\"\n" +
                    "• Phone Actions: \"Open Wi-Fi\", \"Open Bluetooth\", \"Open camera\", \"Open settings\", \"Search <topic>\", \"Call <number>\""
            )
        }

        // 3. Time & Date
        if (containsAny(lower, "what time", "current time", "time is it", "time now") || lower == "time") {
            val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
            return Result.success("The current time is $timeStr.")
        }

        if (containsAny(lower, "what date", "today's date", "todays date", "what day", "current date") || lower == "date") {
            val dateStr = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(Date())
            return Result.success("Today is $dateStr.")
        }

        // 4. Battery & Device Diagnostics
        if (containsAny(lower, "battery", "charge", "power level")) {
            return Result.success(readBatteryStatus())
        }

        if (containsAny(lower, "device info", "phone info", "system status", "diagnostics")) {
            return Result.success(readDeviceDiagnostics())
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

        // 6. Phone Actions (Settings, Camera, Search, Dialer)
        if (containsAny(lower, "open wi-fi", "open wifi", "wi-fi settings", "wifi settings")) {
            return phoneAutomation.executeCommand(AndroidPhoneAutomation.ACTION_OPEN_WIFI)
        }
        if (containsAny(lower, "open bluetooth", "bluetooth settings")) {
            return phoneAutomation.executeCommand(AndroidPhoneAutomation.ACTION_OPEN_BLUETOOTH)
        }
        if (containsAny(lower, "open settings", "device settings")) {
            return phoneAutomation.executeCommand(AndroidPhoneAutomation.ACTION_OPEN_SETTINGS)
        }
        if (containsAny(lower, "open camera", "take a photo", "launch camera")) {
            return phoneAutomation.executeCommand(AndroidPhoneAutomation.ACTION_OPEN_CAMERA)
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
            return phoneAutomation.executeCommand(
                AndroidPhoneAutomation.ACTION_DIAL,
                mapOf("number" to target)
            )
        }

        // 7. Recent memory recall
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

        // 8. Math / Percentage evaluation
        evaluatePercentage(lower)?.let { answer ->
            return Result.success("The answer is $answer.")
        }

        val mathCandidate = extractMathExpression(lower)
        if (mathCandidate != null) {
            MathExpressionEvaluator.evaluate(mathCandidate)?.let { value ->
                return Result.success("$mathCandidate = ${formatNumber(value)}")
            }
        }

        // 9. Honest offline assistant fallback
        return Result.success(
            "I'm currently running in free On-Device Mode (no external cloud LLM connected). " +
                "I can run calculations, check time/battery/device status, set alarms & timers, open settings/camera, search the web, or handle voice commands. " +
                "Type \"help\" for examples, or say \"search $cleaned\" to look this up online."
        )
    }

    private fun readBatteryStatus(): String {
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

    private fun readDeviceDiagnostics(): String {
        val battery = readBatteryStatus()
        return "Device: ${Build.MANUFACTURER} ${Build.MODEL}\n" +
            "Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
            battery
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
        val formatter = DecimalFormat("#.######")
        return formatter.format(value)
    }

    private fun containsAny(text: String, vararg keywords: String): Boolean {
        return keywords.any { text.contains(it) }
    }

    companion object {
        private val GREETINGS = setOf(
            "hi", "hello", "hey", "jarvis", "good morning", "good afternoon", "good evening"
        )
    }
}

private object MathExpressionEvaluator {
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
