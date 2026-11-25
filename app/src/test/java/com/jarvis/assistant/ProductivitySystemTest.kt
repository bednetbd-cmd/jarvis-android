package com.jarvis.assistant

import com.jarvis.assistant.core.ai.DeviceContextPort
import com.jarvis.assistant.core.ai.JarvisToolRegistry
import com.jarvis.assistant.core.ai.OnDeviceJarvisAssistant
import com.jarvis.assistant.core.ai.ParsedToolCall
import com.jarvis.assistant.core.automation.PhoneAutomationPort
import com.jarvis.assistant.core.productivity.CalendarEventSummary
import com.jarvis.assistant.core.productivity.CalendarPort
import com.jarvis.assistant.core.productivity.InMemoryProductivityRepository
import com.jarvis.assistant.core.productivity.ReminderSchedulerPort
import com.jarvis.assistant.core.productivity.RoomProductivityRepository
import com.jarvis.assistant.data.local.HabitEntity
import com.jarvis.assistant.data.local.NoteEntity
import com.jarvis.assistant.data.local.ProductivityDao
import com.jarvis.assistant.data.local.ReminderEntity
import com.jarvis.assistant.data.local.RoutineEntity
import com.jarvis.assistant.data.local.TaskEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductivitySystemTest {

    @Test
    fun roomRepositoryManagesNotesTasksRemindersHabitsCalendarAndBriefing() = runTest {
        val dao = FakeProductivityDao()
        val scheduler = RecordingReminderScheduler()
        val calendar = FakeCalendarPort(granted = true)
        val repo = RoomProductivityRepository(
            dao = dao,
            reminderScheduler = scheduler,
            calendarPort = calendar,
            deviceContext = FakeDeviceContext()
        )

        // Notes
        val noteRes = repo.addNote("Launch Checklist", "Verify APK build in GitHub Actions", "Work")
        assertTrue(noteRes.isSuccess)
        assertTrue(repo.getNotesSummary().contains("Launch Checklist"))

        // Tasks (TODO, PLANNER, SHOPPING)
        assertTrue(repo.addTask("Write Milestone 8 tests", TaskEntity.CATEGORY_TODO).isSuccess)
        assertTrue(repo.addTask("Sprint review", TaskEntity.CATEGORY_PLANNER).isSuccess)
        assertTrue(repo.addTask("Olive oil", TaskEntity.CATEGORY_SHOPPING).isSuccess)
        val allTasks = repo.tasks.first()
        assertEquals(3, allTasks.size)
        val todoId = allTasks.first { it.category == TaskEntity.CATEGORY_TODO }.id
        repo.toggleTask(todoId, true)
        assertTrue(repo.getTasksSummary(TaskEntity.CATEGORY_SHOPPING).contains("Olive oil"))

        // Reminders (schedules WorkManager UUID and cancels it on delete)
        val remRes = repo.scheduleReminder("Stretch break", 20L)
        assertTrue(remRes.isSuccess)
        assertEquals(1, scheduler.scheduled.size)
        val scheduledRem = repo.reminders.first().single()
        assertEquals(scheduler.lastIssuedId, scheduledRem.workRequestId)
        repo.deleteReminder(scheduledRem.id)
        assertEquals(listOf(scheduler.lastIssuedId), scheduler.cancelledIds)

        // Habits (same-day check-in is idempotent)
        assertTrue(repo.addHabit("Drink 2L water").isSuccess)
        val firstCheckIn = repo.checkInHabitByName("water")
        assertTrue(firstCheckIn.isSuccess)
        assertTrue(firstCheckIn.getOrNull().orEmpty().contains("1 day(s)"))
        val duplicateCheckIn = repo.checkInHabitByName("water")
        assertTrue(duplicateCheckIn.isSuccess)
        assertTrue(duplicateCheckIn.getOrNull().orEmpty().contains("already checked in today"))
        assertEquals(1, repo.habits.first().single().streakCount)

        // Calendar & Daily Briefing
        val calSummary = repo.readUpcomingCalendarSummary(2)
        assertTrue(calSummary.isSuccess)
        assertTrue(calSummary.getOrNull().orEmpty().contains("Architecture Sync"))

        val briefing = repo.buildDailyBriefing()
        assertTrue(briefing.contains("JARVIS Daily Briefing"))
        assertTrue(briefing.contains("Architecture Sync"))
        assertTrue(briefing.contains("Shopping Items: 1"))
        assertTrue(briefing.contains("checked in today"))
    }

    @Test
    fun calendarPermissionDeniedReturnsSecurityFailureAndHonestBriefing() = runTest {
        val dao = FakeProductivityDao()
        val repo = RoomProductivityRepository(
            dao = dao,
            reminderScheduler = RecordingReminderScheduler(),
            calendarPort = FakeCalendarPort(granted = false),
            deviceContext = FakeDeviceContext()
        )

        val calResult = repo.readUpcomingCalendarSummary(1)
        assertTrue(calResult.isFailure)
        assertTrue(calResult.exceptionOrNull() is SecurityException)

        val briefing = repo.buildDailyBriefing()
        assertTrue(briefing.contains("Permission not granted"))
    }

    @Test
    fun assistantHandlesProductivityIntentsAndRoutinesSafely() = runTest {
        val calendar = FakeCalendarPort(granted = true)
        val repo = InMemoryProductivityRepository(
            calendarPort = calendar,
            deviceContext = FakeDeviceContext()
        )
        val assistant = OnDeviceJarvisAssistant(
            phoneAutomation = FakeAutomationPort(),
            deviceContext = FakeDeviceContext(),
            deviceControl = null,
            webIntelligence = null,
            productivity = repo
        )

        val noteReply = assistant.generateReply("Note: Sprint - Prepare demo", emptyList())
        assertTrue(noteReply.isSuccess)
        assertEquals("Sprint", repo.notes.first().single().title)
        assertEquals("Prepare demo", repo.notes.first().single().content)

        assertTrue(assistant.generateReply("Add task Submit report", emptyList()).isSuccess)
        assertTrue(assistant.generateReply("Plan today Architecture review", emptyList()).isSuccess)
        assertTrue(assistant.generateReply("Buy Coffee beans", emptyList()).isSuccess)
        assertEquals(3, repo.tasks.first().size)

        val remindReply = assistant.generateReply("Remind me in 15 minutes to call Alex", emptyList())
        assertTrue(remindReply.isSuccess)
        assertTrue(remindReply.getOrNull().orEmpty().contains("call Alex"))
        assertEquals("call Alex", repo.reminders.first().single().title)

        assertTrue(assistant.generateReply("Add habit Read", emptyList()).isSuccess)
        val checkInReply = assistant.generateReply("Check in habit Read", emptyList())
        assertTrue(checkInReply.isSuccess)
        assertEquals(1, repo.habits.first().single().streakCount)

        val calReply = assistant.generateReply("My calendar", emptyList())
        assertTrue(calReply.isSuccess)
        assertTrue(calReply.getOrNull().orEmpty().contains("Architecture Sync"))

        val eventComposer = assistant.generateReply("Schedule event Design Review", emptyList())
        assertTrue(eventComposer.isSuccess)
        assertEquals("Design Review", calendar.lastComposedTitle)

        // Multi-step routine with recursion guard
        repo.addRoutine(
            title = "Morning",
            triggerTimeLabel = "8:00 AM",
            commandSequence = "What time is it?\nRun routine Morning\nDaily briefing"
        )
        val routineReply = assistant.generateReply("Run routine Morning", emptyList())
        assertTrue(routineReply.isSuccess)
        val text = routineReply.getOrNull().orEmpty()
        assertTrue(text.contains("Routine \"Morning\" completed"))
        assertTrue(text.contains("Current time: 9:30 AM"))
        assertTrue(text.contains("JARVIS Daily Briefing"))
    }

    @Test
    fun toolRegistryExecutesMilestone8ProductivityTools() = runTest {
        val repo = InMemoryProductivityRepository(
            calendarPort = FakeCalendarPort(granted = true),
            deviceContext = FakeDeviceContext()
        )
        val registry = JarvisToolRegistry(
            phoneAutomation = FakeAutomationPort(),
            deviceContext = FakeDeviceContext(),
            productivity = repo
        )

        val saveNote = registry.executeTool(
            ParsedToolCall(
                name = JarvisToolRegistry.TOOL_SAVE_NOTE,
                arguments = mapOf("title" to "Ideas", "content" to "Offline voice mode")
            )
        )
        assertTrue(saveNote.isSuccess)

        val addShopping = registry.executeTool(
            ParsedToolCall(
                name = JarvisToolRegistry.TOOL_ADD_TASK,
                arguments = mapOf("title" to "Green tea", "category" to "SHOPPING")
            )
        )
        assertTrue(addShopping.isSuccess)
        assertEquals(TaskEntity.CATEGORY_SHOPPING, repo.tasks.first().single().category)

        val rem = registry.executeTool(
            ParsedToolCall(
                name = JarvisToolRegistry.TOOL_SCHEDULE_REMINDER,
                arguments = mapOf("title" to "Join standup", "delay_minutes" to "10")
            )
        )
        assertTrue(rem.isSuccess)
        assertEquals(1, repo.reminders.first().size)

        val cal = registry.executeTool(
            ParsedToolCall(
                name = JarvisToolRegistry.TOOL_LIST_CALENDAR,
                arguments = mapOf("days_ahead" to "2")
            )
        )
        assertTrue(cal.isSuccess)
        assertTrue(cal.getOrNull().orEmpty().contains("Architecture Sync"))

        val briefing = registry.executeTool(
            ParsedToolCall(
                name = JarvisToolRegistry.TOOL_DAILY_BRIEFING,
                arguments = emptyMap()
            )
        )
        assertTrue(briefing.isSuccess)
        assertTrue(briefing.getOrNull().orEmpty().contains("JARVIS Daily Briefing"))
    }
}

private class FakeDeviceContext : DeviceContextPort {
    override fun getCurrentTimeDate(): String = "Current time: 9:30 AM. Today's date: Wednesday."
    override fun getBatteryStatus(): String = "Battery is at 88% and running on battery."
    override fun getDeviceDiagnostics(): String = "Device: Test Phone"
}

private class FakeAutomationPort : PhoneAutomationPort {
    override suspend fun executeCommand(actionName: String, params: Map<String, String>): Result<String> =
        Result.success("Executed $actionName")
}

private class FakeCalendarPort(
    private val granted: Boolean
) : CalendarPort {
    var lastComposedTitle: String? = null

    override fun hasReadPermission(): Boolean = granted

    override fun readUpcomingEvents(daysAhead: Int): Result<List<CalendarEventSummary>> {
        if (!granted) {
            return Result.failure(SecurityException("Calendar permission required."))
        }
        val now = System.currentTimeMillis()
        return Result.success(
            listOf(
                CalendarEventSummary(
                    title = "Architecture Sync",
                    startMillis = now + 3_600_000L,
                    endMillis = now + 5_400_000L,
                    location = "Room 4B"
                )
            )
        )
    }

    override fun openEventComposer(title: String, startMillis: Long?): Result<String> {
        lastComposedTitle = title
        return Result.success("Opening Android Calendar for $title")
    }
}

private class RecordingReminderScheduler : ReminderSchedulerPort {
    var scheduled = mutableListOf<Triple<Long, String, Long>>()
    var cancelledIds = mutableListOf<String>()
    var lastIssuedId: String = ""
    private var counter = 0

    override fun schedule(reminderId: Long, title: String, delayMinutes: Long): String {
        counter++
        lastIssuedId = "work-req-$counter"
        scheduled.add(Triple(reminderId, title, delayMinutes))
        return lastIssuedId
    }

    override fun cancel(workRequestId: String) {
        cancelledIds.add(workRequestId)
    }
}

private class FakeProductivityDao : ProductivityDao {
    private var nextId = 1L
    private val notesFlow = MutableStateFlow<List<NoteEntity>>(emptyList())
    private val tasksFlow = MutableStateFlow<List<TaskEntity>>(emptyList())
    private val remindersFlow = MutableStateFlow<List<ReminderEntity>>(emptyList())
    private val habitsFlow = MutableStateFlow<List<HabitEntity>>(emptyList())
    private val routinesFlow = MutableStateFlow<List<RoutineEntity>>(emptyList())

    override fun observeNotes(): Flow<List<NoteEntity>> = notesFlow.asStateFlow()
    override suspend fun getNotesSnapshot(): List<NoteEntity> = notesFlow.value
    override suspend fun insertNote(note: NoteEntity): Long {
        val id = if (note.id == 0L) nextId++ else note.id
        notesFlow.value = listOf(note.copy(id = id)) + notesFlow.value.filterNot { it.id == id }
        return id
    }
    override suspend fun deleteNote(id: Long) {
        notesFlow.value = notesFlow.value.filterNot { it.id == id }
    }

    override fun observeTasks(): Flow<List<TaskEntity>> = tasksFlow.asStateFlow()
    override suspend fun getTasksSnapshot(): List<TaskEntity> = tasksFlow.value
    override suspend fun insertTask(task: TaskEntity): Long {
        val id = if (task.id == 0L) nextId++ else task.id
        tasksFlow.value = listOf(task.copy(id = id)) + tasksFlow.value.filterNot { it.id == id }
        return id
    }
    override suspend fun updateTaskDone(id: Long, isDone: Boolean) {
        tasksFlow.value = tasksFlow.value.map { if (it.id == id) it.copy(isDone = isDone) else it }
    }
    override suspend fun deleteTask(id: Long) {
        tasksFlow.value = tasksFlow.value.filterNot { it.id == id }
    }

    override fun observeReminders(): Flow<List<ReminderEntity>> = remindersFlow.asStateFlow()
    override suspend fun getRemindersSnapshot(): List<ReminderEntity> = remindersFlow.value
    override suspend fun insertReminder(reminder: ReminderEntity): Long {
        val id = if (reminder.id == 0L) nextId++ else reminder.id
        remindersFlow.value = remindersFlow.value.filterNot { it.id == id } + reminder.copy(id = id)
        return id
    }
    override suspend fun updateReminder(reminder: ReminderEntity) {
        remindersFlow.value = remindersFlow.value.map { if (it.id == reminder.id) reminder else it }
    }
    override suspend fun markReminderCompleted(id: Long) {
        remindersFlow.value = remindersFlow.value.map { if (it.id == id) it.copy(isCompleted = true) else it }
    }
    override suspend fun deleteReminder(id: Long) {
        remindersFlow.value = remindersFlow.value.filterNot { it.id == id }
    }

    override fun observeHabits(): Flow<List<HabitEntity>> = habitsFlow.asStateFlow()
    override suspend fun getHabitsSnapshot(): List<HabitEntity> = habitsFlow.value
    override suspend fun insertHabit(habit: HabitEntity): Long {
        val id = if (habit.id == 0L) nextId++ else habit.id
        habitsFlow.value = listOf(habit.copy(id = id)) + habitsFlow.value.filterNot { it.id == id }
        return id
    }
    override suspend fun updateHabit(habit: HabitEntity) {
        habitsFlow.value = habitsFlow.value.map { if (it.id == habit.id) habit else it }
    }
    override suspend fun deleteHabit(id: Long) {
        habitsFlow.value = habitsFlow.value.filterNot { it.id == id }
    }

    override fun observeRoutines(): Flow<List<RoutineEntity>> = routinesFlow.asStateFlow()
    override suspend fun getRoutinesSnapshot(): List<RoutineEntity> = routinesFlow.value
    override suspend fun insertRoutine(routine: RoutineEntity): Long {
        val id = if (routine.id == 0L) nextId++ else routine.id
        routinesFlow.value = listOf(routine.copy(id = id)) + routinesFlow.value.filterNot { it.id == id }
        return id
    }
    override suspend fun deleteRoutine(id: Long) {
        routinesFlow.value = routinesFlow.value.filterNot { it.id == id }
    }
}
