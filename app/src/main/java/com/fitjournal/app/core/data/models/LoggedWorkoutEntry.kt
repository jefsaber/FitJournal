package com.fitjournal.app.core.data.models

import androidx.room.Embedded
import com.fitjournal.app.core.data.entities.WorkoutEntryEntity

/** A logged entry together with the catalogue fields needed by logging and progress UI. */
data class LoggedWorkoutEntry(
    @Embedded val entry: WorkoutEntryEntity,
    val exerciseName: String,
    val measurementType: String
)
