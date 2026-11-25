package com.jarvis.assistant.core.productivity

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat

data class CalendarEventSummary(
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val location: String
)

interface CalendarPort {
    fun hasReadPermission(): Boolean
    fun readUpcomingEvents(daysAhead: Int = 1): Result<List<CalendarEventSummary>>
    fun openEventComposer(title: String, startMillis: Long? = null): Result<String>
}

class AndroidCalendarAdapter(
    context: Context
) : CalendarPort {

    private val appContext = context.applicationContext

    override fun hasReadPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun readUpcomingEvents(daysAhead: Int): Result<List<CalendarEventSummary>> {
        if (!hasReadPermission()) {
            return Result.failure(
                SecurityException("Calendar permission (READ_CALENDAR) is required. Grant it in Settings -> Permissions Dashboard.")
            )
        }

        val now = System.currentTimeMillis()
        val endRange = now + daysAhead.coerceIn(1, 14) * 86_400_000L
        val uriBuilder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(uriBuilder, now)
        ContentUris.appendId(uriBuilder, endRange)

        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.EVENT_LOCATION
        )

        return runCatching {
            val events = mutableListOf<CalendarEventSummary>()
            appContext.contentResolver.query(
                uriBuilder.build(),
                projection,
                null,
                null,
                "${CalendarContract.Instances.BEGIN} ASC"
            )?.use { cursor ->
                val titleIdx = cursor.getColumnIndex(CalendarContract.Instances.TITLE)
                val beginIdx = cursor.getColumnIndex(CalendarContract.Instances.BEGIN)
                val endIdx = cursor.getColumnIndex(CalendarContract.Instances.END)
                val locIdx = cursor.getColumnIndex(CalendarContract.Instances.EVENT_LOCATION)
                while (cursor.moveToNext() && events.size < 15) {
                    val title = if (titleIdx >= 0) cursor.getString(titleIdx).orEmpty().trim() else ""
                    if (title.isEmpty()) continue
                    val begin = if (beginIdx >= 0) cursor.getLong(beginIdx) else now
                    val end = if (endIdx >= 0) cursor.getLong(endIdx) else begin
                    val location = if (locIdx >= 0) cursor.getString(locIdx).orEmpty().trim() else ""
                    events.add(CalendarEventSummary(title, begin, end, location))
                }
            }
            events.toList()
        }
    }

    override fun openEventComposer(title: String, startMillis: Long?): Result<String> {
        val cleanTitle = title.trim().ifEmpty { "JARVIS Event" }
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, cleanTitle)
            if (startMillis != null) {
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            appContext.startActivity(intent)
            "Opening Android Calendar to schedule \"$cleanTitle\"."
        }.recoverCatching { error ->
            throw IllegalStateException("No Calendar application found on this device.", error)
        }
    }
}
