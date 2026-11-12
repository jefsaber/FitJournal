package com.fitjournal.app.core.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "exercises", indices = [Index(value = ["code"], unique = true)])
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,   // Stable unique key for JSON seeds; user-added values use a UUID.
    val name: String,
    val category: String,
    val measurementType: String = "REPS_WEIGHT",
    val description: String = ""
)
