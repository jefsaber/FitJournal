package com.fitjournal.app.core.data

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.fitjournal.app.core.data.entities.ExerciseEntity
import com.fitjournal.app.core.domain.ExerciseValidator
import com.fitjournal.app.core.preferences.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** The marker skips future imports; stable unique codes protect retries before the marker is saved. */
class ExerciseSeedImporter(
    context: Context,
    private val database: FitJournalDatabase,
    private val settings: SettingsRepository,
    private val readSeedJson: suspend () -> String = {
        withContext(Dispatchers.IO) {
            context.assets.open("exercises.json").bufferedReader().use { it.readText() }
        }
    }
) {
    suspend fun importOnce() {
        if (settings.seedImported.first()) return
        val exercises = withContext(Dispatchers.Default) {
            val array = JSONArray(readSeedJson())
            buildList {
                for (index in 0 until array.length()) {
                    val row = array.optJSONObject(index) ?: continue
                    val code = row.text("code")
                    val name = row.text("name")
                    val category = row.text("category")
                    val type = if (row.has("measurementType")) row.text("measurementType") else "REPS_WEIGHT"
                    if (code.isBlank() || !ExerciseValidator.valid(name, category, type)) continue
                    add(ExerciseEntity(
                        code = code, name = name, category = category,
                        measurementType = type, description = row.text("description")
                    ))
                }
            }.distinctBy { it.code }
        }
        require(exercises.size >= 20) { "Expected at least 20 distinct valid exercises in assets/exercises.json" }
        var inserted = 0
        database.withTransaction {
            for (exercise in exercises) {
                if (database.exerciseDao().insertIfAbsent(exercise) != -1L) inserted++
            }
        }
        settings.markSeedImported()
        Log.i("FitJournal.Seed", "Validated ${exercises.size} exercises; inserted $inserted new records")
    }

    private fun JSONObject.text(key: String): String = (opt(key) as? String)?.trim().orEmpty()
}
