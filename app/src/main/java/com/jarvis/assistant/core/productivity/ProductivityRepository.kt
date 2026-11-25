package com.jarvis.assistant.core.productivity

import android.content.Context
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.jarvis.assistant.core.ai.DeviceContextPort
import com.jarvis.assistant.core.device.DeviceControlPort
import com.jarvis.assistant.data.local.HabitEntity
import com.jarvis.assistant.data.local.NoteEntity
import com.jarvis.assistant.data.local.ProductivityDao
import com.jarvis.assistant.data.local.ReminderEntity
import com.jarvis.assistant.data.local.RoutineEntity
import com.jarvis.assistant.data.local.TaskEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

interface ReminderSchedulerPort {
    fun schedule(reminderId: Long, title: String, delayMinutes: Long): String
    fun cancel(workRequestId: String)
}

class WorkManagerReminderScheduler(
    context: Context
) : ReminderSchedulerPort {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    override fun schedule(reminderId: Long, title: String, delayMinutes: Long): String {
        val input = Data.Builder()
            .putLong(ReminderWorker.KEY_REMINDER_ID, reminderId)
            .putString(ReminderWorker.KEY_REMINDER_TITLE, title)
            .build()
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayMinutes.coerceAtLeast(1L), TimeUnit.MINUTES)
            .setInputData(input)
            .build()
        workManager.enqueue(request)
        return request.id.toString()
    }

    override fun cancel(workRequestId: String) {
        val uuid = runCatching { UUID.fromString(workRequestId) }.getOrNull() ?: return
        workManager.cancelWorkById(uuid)
    }
}

interface ProductivityPort {
    val notes: Flow<List<NoteEntity>>
    val tasks: Flow<List<TaskEntity>>
    val reminders: Flow<List<ReminderEntity>>
    val habits: Flow<List<HabitEntity>>
    val routines: Flow<List<RoutineEntity>>

    suspend fun addNote(title: String, content: String, tag: String = "General"): Result<String>
    suspend fun deleteNote(id: Long)
    suspend fun getNotesSummary(): String

    suspend fun addTask(
        title: String,
        category: String = TaskEntity.CATEGORY_TODO,
        priority: String = "NORMAL"
    ): Result<String>
    suspend fun toggleTask(id: Long, isDone: Boolean)
    suspend fun deleteTask(id: Long)
    suspend fun getTasksSummary(categoryFilter: String? = null): String

    suspend fun scheduleReminder(title: String, delayMinutes: Long): Result<String>
    suspend fun deleteReminder(id: Long)
    suspend fun getRemindersSummary(): String

    suspend fun addHabit(title: String): Result<String>
    suspend fun checkInHabit(id: Long): Result<String>
    suspend fun checkInHabitByName(query: String): Result<String>
    suspend fun deleteHabit(id: Long)
    suspend fun getHabitsSummary(): String

    suspend fun addRoutine(
        title: String,
        triggerTimeLabel: String,
        commandSequence: String
    ): Result<String>
    suspend fun deleteRoutine(id: Long)
    suspend fun getRoutinesSummary(): String
    suspend fun resolveRoutineCommands(query: String): Result<Pair<String, List<String>>>

    fun readUpcomingCalendarSummary(daysAhead: Int = 1): Result<String>
    fun openCalendarEventComposer(title: String): Result<String>
    suspend fun buildDailyBriefing(): String
}

class RoomProductivityRepository(
    private val dao: ProductivityDao,
    private val reminderScheduler: ReminderSchedulerPort,
    private val calendarPort: CalendarPort,
    private val deviceContext: DeviceContextPort,
    private val deviceControl: DeviceControlPort? = null
) : ProductivityPort {

    override val notes: Flow<List<NoteEntity>> = dao.observeNotes()
    override val tasks: Flow<List<TaskEntity>> = dao.observeTasks()
    override val reminders: Flow<List<ReminderEntity>> = dao.observeReminders()
    override val habits: Flow<List<HabitEntity>> = dao.observeHabits()
    override val routines: Flow<List<RoutineEntity>> = dao.observeRoutines()

    override suspend fun addNote(title: String, content: String, tag: String): Result<String> {
        val cleanContent = content.trim()
        val cleanTitle = title.trim().ifEmpty { cleanContent.take(36) }
        if (cleanContent.isEmpty() && cleanTitle.isEmpty()) {
            return Result.failure(IllegalArgumentException("Provide text to save as a note."))
        }
        dao.insertNote(
            NoteEntity(
                title = cleanTitle,
                content = cleanContent.ifEmpty { cleanTitle },
                tag = tag.trim().ifEmpty { "General" }
            )
        )
        return Result.success("Saved note \"$cleanTitle\" to Room storage.")
    }

    override suspend fun deleteNote(id: Long) {
        dao.deleteNote(id)
    }

    override suspend fun getNotesSummary(): String {
        val list = dao.getNotesSnapshot()
        if (list.isEmpty()) return "You have no saved notes yet."
        return "Saved Notes (${list.size}):\n" + list.take(8).joinToString("\n") {
            "• ${it.title}: ${it.content.take(80)}"
        }
    }

    override suspend fun addTask(title: String, category: String, priority: String): Result<String> {
        val clean = title.trim()
        if (clean.isEmpty()) {
            return Result.failure(IllegalArgumentException("Provide a task or item description."))
        }
        dao.insertTask(
            TaskEntity(
                title = clean,
                category = category,
                priority = priority
            )
        )
        val label = when (category) {
            TaskEntity.CATEGORY_SHOPPING -> "Shopping List"
            TaskEntity.CATEGORY_PLANNER -> "Daily Planner"
            else -> "To-Do List"
        }
        return Result.success("Added \"$clean\" to $label.")
    }

    override suspend fun toggleTask(id: Long, isDone: Boolean) {
        dao.updateTaskDone(id, isDone)
    }

    override suspend fun deleteTask(id: Long) {
        dao.deleteTask(id)
    }

    override suspend fun getTasksSummary(categoryFilter: String?): String {
        val all = dao.getTasksSnapshot()
        val filtered = if (categoryFilter != null) {
            all.filter { it.category == categoryFilter }
        } else {
            all
        }
        val label = when (categoryFilter) {
            TaskEntity.CATEGORY_SHOPPING -> "Shopping List"
            TaskEntity.CATEGORY_PLANNER -> "Daily Planner"
            TaskEntity.CATEGORY_TODO -> "To-Do List"
            else -> "Tasks & Lists"
        }
        if (filtered.isEmpty()) return "Your $label is empty."
        return "$label (${filtered.count { !it.isDone }} pending):\n" + filtered.take(10).joinToString("\n") {
            val check = if (it.isDone) "[x]" else "[ ]"
            "• $check ${it.title}"
        }
    }

    override suspend fun scheduleReminder(title: String, delayMinutes: Long): Result<String> {
        val clean = title.trim()
        if (clean.isEmpty()) {
            return Result.failure(IllegalArgumentException("Specify what you want to be reminded about."))
        }
        val safeDelay = delayMinutes.coerceAtLeast(1L)
        val triggerAt = System.currentTimeMillis() + safeDelay * 60_000L
        val insertedId = dao.insertReminder(
            ReminderEntity(
                title = clean,
                triggerAtMillis = triggerAt
            )
        )
        val workId = reminderScheduler.schedule(insertedId, clean, safeDelay)
        dao.updateReminder(
            ReminderEntity(
                id = insertedId,
                title = clean,
                triggerAtMillis = triggerAt,
                isCompleted = false,
                workRequestId = workId
            )
        )
        val timeLabel = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(triggerAt))
        return Result.success("Reminder scheduled for $timeLabel (in $safeDelay min): \"$clean\".")
    }

    override suspend fun deleteReminder(id: Long) {
        val existing = dao.getRemindersSnapshot().firstOrNull { it.id == id }
        if (existing != null && existing.workRequestId.isNotBlank()) {
            reminderScheduler.cancel(existing.workRequestId)
        }
        dao.deleteReminder(id)
    }

    override suspend fun getRemindersSummary(): String {
        val list = dao.getRemindersSnapshot().filter { !it.isCompleted }
        if (list.isEmpty()) return "No active pending reminders."
        val fmt = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
        return "Active Reminders (${list.size}):\n" + list.take(8).joinToString("\n") {
            "• ${it.title} (${fmt.format(Date(it.triggerAtMillis))})"
        }
    }

    override suspend fun addHabit(title: String): Result<String> {
        val clean = title.trim()
        if (clean.isEmpty()) {
            return Result.failure(IllegalArgumentException("Provide a habit name to track."))
        }
        dao.insertHabit(HabitEntity(title = clean))
        return Result.success("Tracking habit: \"$clean\".")
    }

    override suspend fun checkInHabit(id: Long): Result<String> {
        val habit = dao.getHabitsSnapshot().firstOrNull { it.id == id }
            ?: return Result.failure(IllegalArgumentException("Habit not found."))
        val today = todayKey()
        if (habit.lastCompletedDay == today) {
            return Result.success("\"${habit.title}\" is already checked in today (Streak: ${habit.streakCount} days).")
        }
        val nextStreak = habit.streakCount + 1
        dao.updateHabit(habit.copy(streakCount = nextStreak, lastCompletedDay = today))
        return Result.success("Checked in \"${habit.title}\"! Current streak: $nextStreak day(s).")
    }

    override suspend fun checkInHabitByName(query: String): Result<String> {
        val clean = query.trim().lowercase(Locale.US)
        val match = dao.getHabitsSnapshot().firstOrNull {
            it.title.lowercase(Locale.US).contains(clean)
        } ?: return Result.failure(IllegalStateException("No tracked habit matched \"$query\"."))
        return checkInHabit(match.id)
    }

    override suspend fun deleteHabit(id: Long) {
        dao.deleteHabit(id)
    }

    override suspend fun getHabitsSummary(): String {
        val list = dao.getHabitsSnapshot()
        if (list.isEmpty()) return "No habits tracked yet."
        val today = todayKey()
        return "Habit Tracker (${list.size}):\n" + list.take(8).joinToString("\n") {
            val doneToday = if (it.lastCompletedDay == today) "Done today" else "Pending today"
            "• ${it.title}: ${it.streakCount}-day streak ($doneToday)"
        }
    }

    override suspend fun addRoutine(
        title: String,
        triggerTimeLabel: String,
        commandSequence: String
    ): Result<String> {
        val cleanTitle = title.trim()
        val cleanCommands = commandSequence.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (cleanTitle.isEmpty() || cleanCommands.isEmpty()) {
            return Result.failure(IllegalArgumentException("Provide a routine name and at least one command."))
        }
        dao.insertRoutine(
            RoutineEntity(
                title = cleanTitle,
                triggerTimeLabel = triggerTimeLabel.trim().ifEmpty { "Anytime" },
                commandSequence = cleanCommands.joinToString("\n")
            )
        )
        return Result.success("Saved routine \"$cleanTitle\" (${cleanCommands.size} step(s)).")
    }

    override suspend fun deleteRoutine(id: Long) {
        dao.deleteRoutine(id)
    }

    override suspend fun getRoutinesSummary(): String {
        val list = dao.getRoutinesSnapshot()
        if (list.isEmpty()) return "No routines saved yet. Create one in Memory -> Productivity Hub."
        return "Saved Routines (${list.size}):\n" + list.joinToString("\n") {
            val steps = it.commandSequence.lines().count { line -> line.isNotBlank() }
            "• ${it.title} (${it.triggerTimeLabel}) · $steps steps"
        }
    }

    override suspend fun resolveRoutineCommands(query: String): Result<Pair<String, List<String>>> {
        val clean = query.trim().lowercase(Locale.US)
        val list = dao.getRoutinesSnapshot()
        val match = list.firstOrNull { it.title.lowercase(Locale.US).contains(clean) }
            ?: return Result.failure(IllegalStateException("No routine matched \"$query\"."))
        val steps = match.commandSequence.lines().map { it.trim() }.filter { it.isNotEmpty() }
        return Result.success(match.title to steps)
    }

    override fun readUpcomingCalendarSummary(daysAhead: Int): Result<String> {
        return calendarPort.readUpcomingEvents(daysAhead).map { events ->
            if (events.isEmpty()) {
                "No upcoming calendar events in the next $daysAhead day(s)."
            } else {
                val fmt = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                "Upcoming Calendar Events (${events.size}):\n" + events.joinToString("\n") { ev ->
                    val loc = if (ev.location.isNotBlank()) " @ ${ev.location}" else ""
                    "• ${ev.title} (${fmt.format(Date(ev.startMillis))}$loc)"
                }
            }
        }
    }

    override fun openCalendarEventComposer(title: String): Result<String> =
        calendarPort.openEventComposer(title)

    override suspend fun buildDailyBriefing(): String {
        val pendingTasks = dao.getTasksSnapshot().filter { !it.isDone && it.category != TaskEntity.CATEGORY_SHOPPING }
        val shoppingCount = dao.getTasksSnapshot().count { !it.isDone && it.category == TaskEntity.CATEGORY_SHOPPING }
        val activeReminders = dao.getRemindersSnapshot().filter { !it.isCompleted }
        val habitsList = dao.getHabitsSnapshot()
        val today = todayKey()
        val pendingHabits = habitsList.filter { it.lastCompletedDay != today }

        val calendarLine = calendarPort.readUpcomingEvents(daysAhead = 1).fold(
            onSuccess = { events ->
                if (events.isEmpty()) {
                    "Calendar: Clear for the next 24 hours."
                } else {
                    val fmt = SimpleDateFormat("h:mm a", Locale.getDefault())
                    "Calendar (${events.size} upcoming): " + events.take(3).joinToString("; ") {
                        "${it.title} at ${fmt.format(Date(it.startMillis))}"
                    }
                }
            },
            onFailure = { "Calendar: Permission not granted (grant in Settings to include events)." }
        )

        return buildString {
            appendLine("JARVIS Daily Briefing:")
            appendLine("• ${deviceContext.getCurrentTimeDate()}")
            appendLine("• ${deviceContext.getBatteryStatus()}")
            deviceControl?.getStorageSummary()?.let { appendLine("• $it") }
            appendLine("• $calendarLine")
            appendLine("• Pending Tasks/Planner: ${pendingTasks.size} · Shopping Items: $shoppingCount · Active Reminders: ${activeReminders.size}")
            if (pendingHabits.isNotEmpty()) {
                appendLine("• Habits pending check-in today: ${pendingHabits.joinToString(", ") { it.title }}")
            } else if (habitsList.isNotEmpty()) {
                appendLine("• All ${habitsList.size} tracked habits checked in today!")
            }
            if (pendingTasks.isNotEmpty()) {
                append("• Top Priorities: ${pendingTasks.take(3).joinToString(", ") { it.title }}")
            }
        }.trim()
    }

    private fun todayKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
}

/**
 * Pure JVM in-memory repository for deterministic unit testing without Android Context or Room runtime.
 */
class InMemoryProductivityRepository(
    private val calendarPort: CalendarPort,
    private val deviceContext: DeviceContextPort,
    private val deviceControl: DeviceControlPort? = null
) : ProductivityPort {
    private var nextId = 1L
    private val _notes = MutableStateFlow<List<NoteEntity>>(emptyList())
    private val _tasks = MutableStateFlow<List<TaskEntity>>(emptyList())
    private val _reminders = MutableStateFlow<List<ReminderEntity>>(emptyList())
    private val _habits = MutableStateFlow<List<HabitEntity>>(emptyList())
    private val _routines = MutableStateFlow<List<RoutineEntity>>(emptyList())

    override val notes: Flow<List<NoteEntity>> = _notes.asStateFlow()
    override val tasks: Flow<List<TaskEntity>> = _tasks.asStateFlow()
    override val reminders: Flow<List<ReminderEntity>> = _reminders.asStateFlow()
    override val habits: Flow<List<HabitEntity>> = _habits.asStateFlow()
    override val routines: Flow<List<RoutineEntity>> = _routines.asStateFlow()

    override suspend fun addNote(title: String, content: String, tag: String): Result<String> {
        val cleanContent = content.trim()
        val cleanTitle = title.trim().ifEmpty { cleanContent.take(36) }
        if (cleanContent.isEmpty() && cleanTitle.isEmpty()) {
            return Result.failure(IllegalArgumentException("Provide note content."))
        }
        _notes.value = listOf(NoteEntity(id = nextId++, title = cleanTitle, content = cleanContent.ifEmpty { cleanTitle }, tag = tag)) + _notes.value
        return Result.success("Saved note \"$cleanTitle\" to Room storage.")
    }

    override suspend fun deleteNote(id: Long) {
        _notes.value = _notes.value.filterNot { it.id == id }
    }

    override suspend fun getNotesSummary(): String {
        val list = _notes.value
        if (list.isEmpty()) return "You have no saved notes yet."
        return "Saved Notes (${list.size}):\n" + list.joinToString("\n") { "• ${it.title}: ${it.content}" }
    }

    override suspend fun addTask(title: String, category: String, priority: String): Result<String> {
        val clean = title.trim()
        if (clean.isEmpty()) return Result.failure(IllegalArgumentException("Provide task title."))
        _tasks.value = listOf(TaskEntity(id = nextId++, title = clean, category = category, priority = priority)) + _tasks.value
        return Result.success("Added \"$clean\" to $category.")
    }

    override suspend fun toggleTask(id: Long, isDone: Boolean) {
        _tasks.value = _tasks.value.map { if (it.id == id) it.copy(isDone = isDone) else it }
    }

    override suspend fun deleteTask(id: Long) {
        _tasks.value = _tasks.value.filterNot { it.id == id }
    }

    override suspend fun getTasksSummary(categoryFilter: String?): String {
        val list = if (categoryFilter != null) _tasks.value.filter { it.category == categoryFilter } else _tasks.value
        if (list.isEmpty()) return "No items found."
        return "Tasks (${list.count { !it.isDone }} pending):\n" + list.joinToString("\n") { "• ${it.title}" }
    }

    override suspend fun scheduleReminder(title: String, delayMinutes: Long): Result<String> {
        val clean = title.trim()
        if (clean.isEmpty()) return Result.failure(IllegalArgumentException("Provide reminder title."))
        val triggerAt = System.currentTimeMillis() + delayMinutes.coerceAtLeast(1L) * 60_000L
        _reminders.value = _reminders.value + ReminderEntity(id = nextId++, title = clean, triggerAtMillis = triggerAt)
        return Result.success("Reminder scheduled in $delayMinutes min: \"$clean\".")
    }

    override suspend fun deleteReminder(id: Long) {
        _reminders.value = _reminders.value.filterNot { it.id == id }
    }

    override suspend fun getRemindersSummary(): String {
        val list = _reminders.value.filter { !it.isCompleted }
        return if (list.isEmpty()) "No active pending reminders." else "Active Reminders (${list.size}):\n" + list.joinToString("\n") { "• ${it.title}" }
    }

    override suspend fun addHabit(title: String): Result<String> {
        val clean = title.trim()
        if (clean.isEmpty()) return Result.failure(IllegalArgumentException("Provide habit name."))
        _habits.value = listOf(HabitEntity(id = nextId++, title = clean)) + _habits.value
        return Result.success("Tracking habit: \"$clean\".")
    }

    override suspend fun checkInHabit(id: Long): Result<String> {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val existing = _habits.value.firstOrNull { it.id == id } ?: return Result.failure(IllegalArgumentException("Not found"))
        if (existing.lastCompletedDay == today) return Result.success("\"${existing.title}\" already checked in today.")
        val updated = existing.copy(streakCount = existing.streakCount + 1, lastCompletedDay = today)
        _habits.value = _habits.value.map { if (it.id == id) updated else it }
        return Result.success("Checked in \"${updated.title}\"! Current streak: ${updated.streakCount} day(s).")
    }

    override suspend fun checkInHabitByName(query: String): Result<String> {
        val match = _habits.value.firstOrNull { it.title.contains(query, ignoreCase = true) }
            ?: return Result.failure(IllegalStateException("Habit not found"))
        return checkInHabit(match.id)
    }

    override suspend fun deleteHabit(id: Long) {
        _habits.value = _habits.value.filterNot { it.id == id }
    }

    override suspend fun getHabitsSummary(): String {
        val list = _habits.value
        return if (list.isEmpty()) "No habits tracked yet." else "Habits (${list.size}):\n" + list.joinToString("\n") { "• ${it.title} (${it.streakCount}d)" }
    }

    override suspend fun addRoutine(title: String, triggerTimeLabel: String, commandSequence: String): Result<String> {
        _routines.value = listOf(RoutineEntity(id = nextId++, title = title.trim(), triggerTimeLabel = triggerTimeLabel, commandSequence = commandSequence)) + _routines.value
        return Result.success("Saved routine \"$title\".")
    }

    override suspend fun deleteRoutine(id: Long) {
        _routines.value = _routines.value.filterNot { it.id == id }
    }

    override suspend fun getRoutinesSummary(): String =
        if (_routines.value.isEmpty()) "No routines saved yet." else _routines.value.joinToString("\n") { "• ${it.title}" }

    override suspend fun resolveRoutineCommands(query: String): Result<Pair<String, List<String>>> {
        val match = _routines.value.firstOrNull { it.title.contains(query, ignoreCase = true) }
            ?: return Result.failure(IllegalStateException("Routine not found"))
        return Result.success(match.title to match.commandSequence.lines().map { it.trim() }.filter { it.isNotEmpty() })
    }

    override fun readUpcomingCalendarSummary(daysAhead: Int): Result<String> =
        calendarPort.readUpcomingEvents(daysAhead).map { list ->
            if (list.isEmpty()) "No upcoming calendar events." else "Upcoming Calendar Events (${list.size}): " + list.joinToString { it.title }
        }

    override fun openCalendarEventComposer(title: String): Result<String> =
        calendarPort.openEventComposer(title)

    override suspend fun buildDailyBriefing(): String = buildString {
        appendLine("JARVIS Daily Briefing:")
        appendLine("• ${deviceContext.getCurrentTimeDate()}")
        appendLine("• ${deviceContext.getBatteryStatus()}")
        deviceControl?.getStorageSummary()?.let { appendLine("• $it") }
        append("• Pending Tasks: ${_tasks.value.count { !it.isDone }} · Active Reminders: ${_reminders.value.count { !it.isCompleted }}")
    }.trim()
}
