package com.jarvis.assistant.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val tag: String = "General",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val category: String = CATEGORY_TODO, // TODO, PLANNER, SHOPPING
    val isDone: Boolean = false,
    val dueAtMillis: Long? = null,
    val priority: String = "NORMAL",
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val CATEGORY_TODO = "TODO"
        const val CATEGORY_PLANNER = "PLANNER"
        const val CATEGORY_SHOPPING = "SHOPPING"
    }
}

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val triggerAtMillis: Long,
    val isCompleted: Boolean = false,
    val workRequestId: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val streakCount: Int = 0,
    val lastCompletedDay: String = "", // yyyy-MM-dd
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val triggerTimeLabel: String = "Morning",
    val commandSequence: String, // newline-separated commands
    val createdAt: Long = System.currentTimeMillis()
)
