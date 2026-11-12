package com.fitjournal.app.core.data

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.fitjournal.app.core.data.entities.ExerciseEntity
import com.fitjournal.app.core.preferences.SettingsRepository
import kotlinx.coroutines.flow.first
import org.json.JSONArray

/** Idempotent import. Unique `code` + IGNORE also protects against crashes before the DataStore flag. */
class ExerciseSeedImporter(
    private val context: Context,
    private val database: FitJournalDatabase,
    private val settings: SettingsRepository
) {
    suspend fun importOnce() {
        if (settings.seedImported.first()) return
        val json = context.assets.open("exercises.json").bufferedReader().use { it.readText() }
        val array = JSONArray(json)
        var valid = 0
        var inserted = 0
        database.withTransaction {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val code = obj.optString("code").trim()
                val name = obj.optString("name").trim()
                val category = obj.optString("category").trim()
                val type = obj.optString("measurementType", "REPS_WEIGHT")
                if (code.isBlank() || name.isBlank() || category.isBlank() ||
                    type !in setOf("REPS_WEIGHT", "DURATION")) continue
                valid++
                val id = database.exerciseDao().insertIfAbsent(
                    ExerciseEntity(code = code, name = name, category = category,
                        measurementType = type, description = obj.optString("description"))
                )
                if (id != -1L) inserted++
            }
            require(valid >= 20) { "Expected at least 20 valid exercises in assets/exercises.json" }
        }
        settings.markSeedImported()
        Log.i("FitJournal.Seed", "Validated $valid exercises; inserted $inserted new records")
    }
}
