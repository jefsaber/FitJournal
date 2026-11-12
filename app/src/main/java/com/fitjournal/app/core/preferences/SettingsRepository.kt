package com.fitjournal.app.core.preferences

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore(name = "fitjournal_settings")

data class AppSettings(
    val darkMode: Boolean = false,
    val weightUnit: String = "kg",
    val exerciseSort: String = "NAME_ASC"
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val WEIGHT_UNIT = stringPreferencesKey("weight_unit")
        val EXERCISE_SORT = stringPreferencesKey("exercise_sort")
        val SEED_IMPORTED = booleanPreferencesKey("seed_imported_v1")
    }

    val settings: Flow<AppSettings> = context.settingsStore.data.map { prefs ->
        AppSettings(
            darkMode = prefs[Keys.DARK_MODE] ?: false,
            weightUnit = prefs[Keys.WEIGHT_UNIT] ?: "kg",
            exerciseSort = prefs[Keys.EXERCISE_SORT] ?: "NAME_ASC"
        )
    }

    suspend fun setDarkMode(value: Boolean) {
        context.settingsStore.edit { it[Keys.DARK_MODE] = value }
    }
    suspend fun setWeightUnit(value: String) {
        require(value == "kg" || value == "lb")
        context.settingsStore.edit { it[Keys.WEIGHT_UNIT] = value }
    }
    suspend fun setExerciseSort(value: String) {
        require(value in setOf("NAME_ASC", "NAME_DESC"))
        context.settingsStore.edit { it[Keys.EXERCISE_SORT] = value }
    }

    // Internal importer marker. Not counted as one of the three user-facing preferences.
    val seedImported: Flow<Boolean> = context.settingsStore.data.map { it[Keys.SEED_IMPORTED] ?: false }
    suspend fun markSeedImported() { context.settingsStore.edit { it[Keys.SEED_IMPORTED] = true } }
}
