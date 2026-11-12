package com.fitjournal.app.core.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.fitjournal.app.core.data.dao.ExerciseDao
import com.fitjournal.app.core.data.dao.WorkoutDao
import com.fitjournal.app.core.data.dao.WorkoutEntryDao
import com.fitjournal.app.core.data.entities.ExerciseEntity
import com.fitjournal.app.core.data.entities.WorkoutEntity
import com.fitjournal.app.core.data.entities.WorkoutEntryEntity

@Database(entities = [ExerciseEntity::class, WorkoutEntity::class, WorkoutEntryEntity::class], version = 1, exportSchema = false)
abstract class FitJournalDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun workoutEntryDao(): WorkoutEntryDao
}
