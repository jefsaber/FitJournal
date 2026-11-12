package com.fitjournal.app.core.data

import android.content.Context
import androidx.room.Room
import com.fitjournal.app.core.data.repositories.ExerciseRepository
import com.fitjournal.app.core.data.repositories.WorkoutRepository
import com.fitjournal.app.core.data.repositories.WorkoutEntryRepository
import com.fitjournal.app.core.preferences.SettingsRepository

/** Simple app-scoped dependency container; no DI framework needed for the coursework. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val database: FitJournalDatabase by lazy {
        Room.databaseBuilder(appContext, FitJournalDatabase::class.java, "fitjournal.db").build()
    }
    val exercises: ExerciseRepository by lazy { ExerciseRepository(database.exerciseDao()) }
    val workouts: WorkoutRepository by lazy { WorkoutRepository(database.workoutDao()) }
    val entries: WorkoutEntryRepository by lazy { WorkoutEntryRepository(database.workoutEntryDao()) }
    val settings: SettingsRepository by lazy { SettingsRepository(appContext) }
    val seedImporter: ExerciseSeedImporter by lazy { ExerciseSeedImporter(appContext, database, settings) }
}
