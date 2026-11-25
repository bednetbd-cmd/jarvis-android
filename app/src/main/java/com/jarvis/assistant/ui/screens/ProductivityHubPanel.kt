package com.jarvis.assistant.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jarvis.assistant.data.local.TaskEntity
import com.jarvis.assistant.ui.theme.JarvisBackground
import com.jarvis.assistant.ui.theme.JarvisSuccessGreen
import com.jarvis.assistant.ui.theme.JarvisSurface
import com.jarvis.assistant.ui.theme.JarvisSurfaceBorder
import com.jarvis.assistant.ui.theme.JarvisSurfaceVariant
import com.jarvis.assistant.ui.theme.JarvisTextMuted
import com.jarvis.assistant.ui.theme.JarvisTextPrimary
import com.jarvis.assistant.ui.theme.JarvisTextSecondary
import com.jarvis.assistant.ui.theme.JarvisWarningAmber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class ProductivitySection(val label: String) {
    TASKS("Tasks & Lists"),
    NOTES("Notes"),
    REMINDERS("Reminders"),
    HABITS("Habits"),
    CALENDAR_ROUTINES("Calendar & Routines")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProductivityHubPanel(
    viewModel: MainViewModel,
    onRunCommand: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val productivity = viewModel.productivity
    val accent = MaterialTheme.colorScheme.primary

    if (productivity == null) {
        Column(
            modifier = modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Productivity database is unavailable in this environment.",
                style = MaterialTheme.typography.bodyMedium,
                color = JarvisTextMuted
            )
        }
        return
    }

    val notes by productivity.notes.collectAsStateWithLifecycle(initialValue = emptyList())
    val tasks by productivity.tasks.collectAsStateWithLifecycle(initialValue = emptyList())
    val reminders by productivity.reminders.collectAsStateWithLifecycle(initialValue = emptyList())
    val habits by productivity.habits.collectAsStateWithLifecycle(initialValue = emptyList())
    val routines by productivity.routines.collectAsStateWithLifecycle(initialValue = emptyList())

    var section by remember { mutableStateOf(ProductivitySection.TASKS) }
    var taskCategory by remember { mutableStateOf(TaskEntity.CATEGORY_TODO) }
    var taskDraft by remember { mutableStateOf("") }
    var noteTitleDraft by remember { mutableStateOf("") }
    var noteBodyDraft by remember { mutableStateOf("") }
    var reminderTitleDraft by remember { mutableStateOf("") }
    var reminderMinutesDraft by remember { mutableStateOf("15") }
    var habitDraft by remember { mutableStateOf("") }
    var routineTitleDraft by remember { mutableStateOf("") }
    var routineStepsDraft by remember { mutableStateOf("Daily briefing\nWeather in Dhaka") }
    var calendarEventTitleDraft by remember { mutableStateOf("") }

    var hasCalendarPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var calendarRefreshKey by remember { mutableStateOf(0) }
    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCalendarPerm = granted
        calendarRefreshKey++
        viewModel.showNotice(
            if (granted) "Calendar access granted." else "Calendar permission denied."
        )
    }

    val calendarSummary = remember(hasCalendarPerm, calendarRefreshKey) {
        if (!hasCalendarPerm) null else productivity.readUpcomingCalendarSummary(2)
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = accent,
        unfocusedBorderColor = JarvisSurfaceBorder,
        focusedTextColor = JarvisTextPrimary,
        unfocusedTextColor = JarvisTextPrimary,
        focusedLabelColor = accent,
        unfocusedLabelColor = JarvisTextMuted,
        cursorColor = accent
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = JarvisSurface,
                border = BorderStroke(1.dp, accent.copy(alpha = 0.55f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                        Text(
                            text = "DAILY BRIEFING & ROOM HUB",
                            style = MaterialTheme.typography.labelMedium,
                            color = accent
                        )
                        Text(
                            text = "${tasks.count { !it.isDone }} pending tasks · ${reminders.count { !it.isCompleted }} reminders · ${notes.size} notes · ${habits.size} habits",
                            style = MaterialTheme.typography.bodySmall,
                            color = JarvisTextSecondary
                        )
                    }
                    Button(
                        onClick = { onRunCommand("Daily briefing") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent,
                            contentColor = JarvisBackground
                        )
                    ) {
                        Icon(Icons.Rounded.Today, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Briefing")
                    }
                }
            }
        }

        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ProductivitySection.entries.forEach { itemSection ->
                    FilterChip(
                        selected = section == itemSection,
                        onClick = { section = itemSection },
                        label = { Text(itemSection.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accent.copy(alpha = 0.2f),
                            selectedLabelColor = accent,
                            containerColor = JarvisSurfaceVariant,
                            labelColor = JarvisTextSecondary
                        )
                    )
                }
            }
        }

        when (section) {
            ProductivitySection.TASKS -> {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = JarvisSurface,
                        border = BorderStroke(1.dp, JarvisSurfaceBorder)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(
                                    TaskEntity.CATEGORY_TODO to "To-Do",
                                    TaskEntity.CATEGORY_PLANNER to "Daily Planner",
                                    TaskEntity.CATEGORY_SHOPPING to "Shopping List"
                                ).forEach { (cat, label) ->
                                    FilterChip(
                                        selected = taskCategory == cat,
                                        onClick = { taskCategory = cat },
                                        label = { Text(label) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = accent.copy(alpha = 0.2f),
                                            selectedLabelColor = accent,
                                            containerColor = JarvisSurfaceVariant,
                                            labelColor = JarvisTextSecondary
                                        )
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = taskDraft,
                                    onValueChange = { taskDraft = it },
                                    label = { Text("Add item...") },
                                    singleLine = true,
                                    colors = fieldColors,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        if (taskDraft.isNotBlank()) {
                                            viewModel.addTask(taskDraft, taskCategory)
                                            taskDraft = ""
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = accent,
                                        contentColor = JarvisBackground
                                    )
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = "Add")
                                }
                            }
                        }
                    }
                }

                val filteredTasks = tasks.filter { it.category == taskCategory }
                if (filteredTasks.isEmpty()) {
                    item { EmptySectionHint("No items in this list yet.") }
                } else {
                    items(filteredTasks, key = { it.id }) { task ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = JarvisSurface,
                            border = BorderStroke(1.dp, JarvisSurfaceBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = task.isDone,
                                    onCheckedChange = { checked -> viewModel.toggleTask(task.id, checked) },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = accent,
                                        uncheckedColor = JarvisTextMuted,
                                        checkmarkColor = JarvisBackground
                                    )
                                )
                                Text(
                                    text = task.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (task.isDone) JarvisTextMuted else JarvisTextPrimary,
                                    textDecoration = if (task.isDone) TextDecoration.LineThrough else TextDecoration.None,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { viewModel.deleteTask(task.id) }) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = "Delete task",
                                        tint = JarvisTextMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }

            ProductivitySection.NOTES -> {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = JarvisSurface,
                        border = BorderStroke(1.dp, JarvisSurfaceBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = noteTitleDraft,
                                onValueChange = { noteTitleDraft = it },
                                label = { Text("Note title (optional)") },
                                singleLine = true,
                                colors = fieldColors,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = noteBodyDraft,
                                onValueChange = { noteBodyDraft = it },
                                label = { Text("Note content") },
                                colors = fieldColors,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = {
                                    if (noteBodyDraft.isNotBlank() || noteTitleDraft.isNotBlank()) {
                                        viewModel.addNote(noteTitleDraft, noteBodyDraft)
                                        noteTitleDraft = ""
                                        noteBodyDraft = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = accent,
                                    contentColor = JarvisBackground
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Save Note to Room DB")
                            }
                        }
                    }
                }

                if (notes.isEmpty()) {
                    item { EmptySectionHint("No saved notes yet.") }
                } else {
                    items(notes, key = { it.id }) { note ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = JarvisSurface,
                            border = BorderStroke(1.dp, JarvisSurfaceBorder)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = note.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = accent,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = { viewModel.deleteNote(note.id) }) {
                                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete note", tint = JarvisTextMuted)
                                    }
                                }
                                Text(
                                    text = note.content,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = JarvisTextSecondary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedButton(
                                    onClick = { onRunCommand("Summarize: ${note.content}") },
                                    border = BorderStroke(1.dp, accent.copy(alpha = 0.5f)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = accent)
                                ) {
                                    Text("Summarize in Chat")
                                }
                            }
                        }
                    }
                }
            }

            ProductivitySection.REMINDERS -> {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = JarvisSurface,
                        border = BorderStroke(1.dp, JarvisSurfaceBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = reminderTitleDraft,
                                onValueChange = { reminderTitleDraft = it },
                                label = { Text("Reminder message") },
                                singleLine = true,
                                colors = fieldColors,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = reminderMinutesDraft,
                                onValueChange = { reminderMinutesDraft = it.filter(Char::isDigit) },
                                label = { Text("Delay in minutes (WorkManager notification)") },
                                singleLine = true,
                                colors = fieldColors,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = {
                                    val mins = reminderMinutesDraft.toLongOrNull() ?: 15L
                                    if (reminderTitleDraft.isNotBlank()) {
                                        viewModel.scheduleReminder(reminderTitleDraft, mins)
                                        reminderTitleDraft = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = accent,
                                    contentColor = JarvisBackground
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Schedule Reminder")
                            }
                        }
                    }
                }

                if (reminders.isEmpty()) {
                    item { EmptySectionHint("No reminders scheduled yet.") }
                } else {
                    items(reminders, key = { it.id }) { rem ->
                        val timeText = remember(rem.triggerAtMillis) {
                            SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                                .format(Date(rem.triggerAtMillis))
                        }
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = JarvisSurface,
                            border = BorderStroke(1.dp, JarvisSurfaceBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = rem.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (rem.isCompleted) JarvisTextMuted else JarvisTextPrimary
                                    )
                                    Text(
                                        text = if (rem.isCompleted) "Completed · $timeText" else "Scheduled for $timeText",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (rem.isCompleted) JarvisSuccessGreen else accent
                                    )
                                }
                                IconButton(onClick = { viewModel.deleteReminder(rem.id) }) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete reminder", tint = JarvisTextMuted)
                                }
                            }
                        }
                    }
                }
            }

            ProductivitySection.HABITS -> {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = JarvisSurface,
                        border = BorderStroke(1.dp, JarvisSurfaceBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = habitDraft,
                                onValueChange = { habitDraft = it },
                                label = { Text("New habit (e.g. Read 20 mins)") },
                                singleLine = true,
                                colors = fieldColors,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (habitDraft.isNotBlank()) {
                                        viewModel.addHabit(habitDraft)
                                        habitDraft = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = accent,
                                    contentColor = JarvisBackground
                                )
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = "Add habit")
                            }
                        }
                    }
                }

                if (habits.isEmpty()) {
                    item { EmptySectionHint("No habits tracked yet.") }
                } else {
                    val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                    items(habits, key = { it.id }) { habit ->
                        val doneToday = habit.lastCompletedDay == todayKey
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = JarvisSurface,
                            border = BorderStroke(1.dp, JarvisSurfaceBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = habit.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = JarvisTextPrimary
                                    )
                                    Text(
                                        text = "${habit.streakCount}-day streak · ${if (doneToday) "Checked in today" else "Pending today"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (doneToday) JarvisSuccessGreen else JarvisWarningAmber
                                    )
                                }
                                OutlinedButton(
                                    onClick = { viewModel.checkInHabit(habit.id) },
                                    enabled = !doneToday,
                                    border = BorderStroke(1.dp, accent.copy(alpha = 0.6f)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = accent)
                                ) {
                                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (doneToday) "Done" else "Check In")
                                }
                                IconButton(onClick = { viewModel.deleteHabit(habit.id) }) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete habit", tint = JarvisTextMuted)
                                }
                            }
                        }
                    }
                }
            }

            ProductivitySection.CALENDAR_ROUTINES -> {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = JarvisSurface,
                        border = BorderStroke(1.dp, JarvisSurfaceBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "ANDROID CALENDAR",
                                style = MaterialTheme.typography.labelMedium,
                                color = accent
                            )
                            if (!hasCalendarPerm) {
                                Text(
                                    text = "Grant READ_CALENDAR to view upcoming events in your Daily Briefing and Productivity Hub.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = JarvisTextSecondary
                                )
                                OutlinedButton(
                                    onClick = { calendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR) },
                                    border = BorderStroke(1.dp, accent.copy(alpha = 0.7f)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = accent)
                                ) {
                                    Text("Grant Calendar Permission")
                                }
                            } else {
                                Text(
                                    text = calendarSummary?.getOrNull() ?: "No calendar events loaded.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = JarvisTextPrimary
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = calendarEventTitleDraft,
                                    onValueChange = { calendarEventTitleDraft = it },
                                    label = { Text("New Calendar Event Title") },
                                    singleLine = true,
                                    colors = fieldColors,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        viewModel.openCalendarComposer(calendarEventTitleDraft)
                                        calendarEventTitleDraft = ""
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = accent,
                                        contentColor = JarvisBackground
                                    )
                                ) {
                                    Text("Create")
                                }
                            }
                        }
                    }
                }

                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = JarvisSurface,
                        border = BorderStroke(1.dp, JarvisSurfaceBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "MULTI-STEP ROUTINES",
                                style = MaterialTheme.typography.labelMedium,
                                color = accent
                            )
                            OutlinedTextField(
                                value = routineTitleDraft,
                                onValueChange = { routineTitleDraft = it },
                                label = { Text("Routine Name (e.g. Morning Brief)") },
                                singleLine = true,
                                colors = fieldColors,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = routineStepsDraft,
                                onValueChange = { routineStepsDraft = it },
                                label = { Text("Commands (one per line)") },
                                colors = fieldColors,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = {
                                    if (routineTitleDraft.isNotBlank() && routineStepsDraft.isNotBlank()) {
                                        viewModel.addRoutine(routineTitleDraft, "Custom", routineStepsDraft)
                                        routineTitleDraft = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = accent,
                                    contentColor = JarvisBackground
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Save Routine")
                            }
                        }
                    }
                }

                items(routines, key = { it.id }) { routine ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = JarvisSurface,
                        border = BorderStroke(1.dp, JarvisSurfaceBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = routine.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = JarvisTextPrimary
                                )
                                Text(
                                    text = routine.commandSequence.replace('\n', ' · '),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = JarvisTextMuted,
                                    maxLines = 2
                                )
                            }
                            OutlinedButton(
                                onClick = { onRunCommand("Run routine ${routine.title}") },
                                border = BorderStroke(1.dp, accent.copy(alpha = 0.6f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = accent)
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("Run")
                            }
                            IconButton(onClick = { viewModel.deleteRoutine(routine.id) }) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete routine", tint = JarvisTextMuted)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptySectionHint(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = JarvisSurface,
        border = BorderStroke(1.dp, JarvisSurfaceBorder.copy(alpha = 0.6f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = JarvisTextMuted,
            modifier = Modifier.padding(16.dp)
        )
    }
}
