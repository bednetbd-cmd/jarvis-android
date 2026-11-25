package com.jarvis.assistant.core.web

import java.text.DecimalFormat
import java.util.Locale

data class UnitConversionRequest(
    val value: Double,
    val fromUnit: String,
    val toUnit: String
)

object UnitConverter {

    private val lengthToMeters = mapOf(
        "m" to 1.0, "meter" to 1.0, "meters" to 1.0, "metre" to 1.0, "metres" to 1.0,
        "km" to 1000.0, "kilometer" to 1000.0, "kilometers" to 1000.0,
        "cm" to 0.01, "centimeter" to 0.01, "centimeters" to 0.01,
        "mm" to 0.001, "millimeter" to 0.001, "millimeters" to 0.001,
        "mi" to 1609.344, "mile" to 1609.344, "miles" to 1609.344,
        "yd" to 0.9144, "yard" to 0.9144, "yards" to 0.9144,
        "ft" to 0.3048, "foot" to 0.3048, "feet" to 0.3048,
        "in" to 0.0254, "inch" to 0.0254, "inches" to 0.0254
    )

    private val massToGrams = mapOf(
        "g" to 1.0, "gram" to 1.0, "grams" to 1.0,
        "kg" to 1000.0, "kilogram" to 1000.0, "kilograms" to 1000.0,
        "mg" to 0.001, "milligram" to 0.001, "milligrams" to 0.001,
        "lb" to 453.59237, "lbs" to 453.59237, "pound" to 453.59237, "pounds" to 453.59237,
        "oz" to 28.349523125, "ounce" to 28.349523125, "ounces" to 28.349523125
    )

    private val dataToBytes = mapOf(
        "b" to 1.0, "byte" to 1.0, "bytes" to 1.0,
        "kb" to 1024.0,
        "mb" to 1024.0 * 1024.0,
        "gb" to 1024.0 * 1024.0 * 1024.0,
        "tb" to 1024.0 * 1024.0 * 1024.0 * 1024.0
    )

    private val speedToMps = mapOf(
        "m/s" to 1.0, "mps" to 1.0,
        "km/h" to 1.0 / 3.6, "kph" to 1.0 / 3.6, "kmh" to 1.0 / 3.6,
        "mph" to 0.44704
    )

    private val timeToSeconds = mapOf(
        "s" to 1.0, "sec" to 1.0, "secs" to 1.0, "second" to 1.0, "seconds" to 1.0,
        "min" to 60.0, "mins" to 60.0, "minute" to 60.0, "minutes" to 60.0,
        "h" to 3600.0, "hr" to 3600.0, "hrs" to 3600.0, "hour" to 3600.0, "hours" to 3600.0,
        "d" to 86400.0, "day" to 86400.0, "days" to 86400.0
    )

    private val tempAliases = setOf(
        "c", "celsius", "centigrade",
        "f", "fahrenheit",
        "k", "kelvin"
    )

    fun parseConversionQuery(text: String): UnitConversionRequest? {
        val regex = Regex(
            """(?:convert\s+)?(-?\d+(?:\.\d+)?)\s*([a-zA-Z/]+)\s+(?:to|in|into)\s+([a-zA-Z/]+)\b""",
            RegexOption.IGNORE_CASE
        )
        val match = regex.find(text.trim()) ?: return null
        val value = match.groupValues[1].toDoubleOrNull() ?: return null
        val from = match.groupValues[2].lowercase(Locale.US)
        val to = match.groupValues[3].lowercase(Locale.US)
        if (!isKnownUnit(from) || !isKnownUnit(to)) return null
        return UnitConversionRequest(value = value, fromUnit = from, toUnit = to)
    }

    fun isKnownUnit(unit: String): Boolean {
        val u = unit.lowercase(Locale.US)
        return u in lengthToMeters || u in massToGrams || u in dataToBytes ||
            u in speedToMps || u in timeToSeconds || u in tempAliases
    }

    fun convert(value: Double, fromUnit: String, toUnit: String): Result<String> {
        val from = fromUnit.trim().lowercase(Locale.US)
        val to = toUnit.trim().lowercase(Locale.US)

        if (from in tempAliases && to in tempAliases) {
            val result = convertTemperature(value, from, to)
            return Result.success("${format(value)} ${from.uppercase(Locale.US)} = ${format(result)} ${to.uppercase(Locale.US)}")
        }

        val factorTable = listOf(lengthToMeters, massToGrams, dataToBytes, speedToMps, timeToSeconds)
            .firstOrNull { from in it && to in it }
            ?: return Result.failure(
                IllegalArgumentException("Cannot convert between \"$fromUnit\" and \"$toUnit\".")
            )

        val baseValue = value * factorTable.getValue(from)
        val converted = baseValue / factorTable.getValue(to)
        return Result.success("${format(value)} $from = ${format(converted)} $to")
    }

    private fun convertTemperature(value: Double, from: String, to: String): Double {
        val celsius = when {
            from.startsWith("c") -> value
            from.startsWith("f") -> (value - 32.0) * 5.0 / 9.0
            else -> value - 273.15
        }
        return when {
            to.startsWith("c") -> celsius
            to.startsWith("f") -> (celsius * 9.0 / 5.0) + 32.0
            else -> celsius + 273.15
        }
    }

    private fun format(number: Double): String = DecimalFormat("#,##0.####").format(number)
}
