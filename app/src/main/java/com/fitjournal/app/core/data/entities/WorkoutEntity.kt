package com.fitjournal.app.core.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workouts")
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val sessionDate: Long,
    val notes: String = "",
    val isCompleted: Boolean = false
)
