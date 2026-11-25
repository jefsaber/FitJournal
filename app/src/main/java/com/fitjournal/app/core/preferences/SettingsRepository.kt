package com.fitjournal.app.core.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore(name = "fitjournal_settings")

/** Supported values for the user-facing preferences, shared by the repository, UI and tests. */
object PreferenceValues {
    const val WEIGHT_KG = "kg"
    const val WEIGHT_LB = "lb"
    const val SORT_NAME_ASC = "NAME_ASC"
    const val SORT_NAME_DESC = "NAME_DESC"

    val WEIGHT_UNITS = setOf(WEIGHT_KG, WEIGHT_LB)
    val EXERCISE_SORTS = setOf(SORT_NAME_ASC, SORT_NAME_DESC)

    fun weightUnitOrDefault(value: String?): String = value?.takeIf { it in WEIGHT_UNITS } ?: WEIGHT_KG
    fun exerciseSortOrDefault(value: String?): String = value?.takeIf { it in EXERCISE_SORTS } ?: SORT_NAME_ASC
}

/**
 * [darkMode] is `null` until the user chooses light or dark explicitly; the app then follows
 * the device theme. The stored value itself stays a Boolean.
 */
data class AppSettings(
    val darkMode: Boolean? = null,
    val weightUnit: String = PreferenceValues.WEIGHT_KG,
    val exerciseSort: String = PreferenceValues.SORT_NAME_ASC
)

class SettingsRepository(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.settingsStore)

    private object Keys {
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val WEIGHT_UNIT = stringPreferencesKey("weight_unit")
        val EXERCISE_SORT = stringPreferencesKey("exercise_sort")
        val SEED_IMPORTED = booleanPreferencesKey("seed_imported_v1")
    }

    /** A corrupt or unreadable preferences file falls back to defaults instead of crashing the UI. */
    private val preferences: Flow<Preferences> = store.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    val settings: Flow<AppSettings> = preferences.map { prefs ->
        AppSettings(
            darkMode = prefs[Keys.DARK_MODE],
            weightUnit = PreferenceValues.weightUnitOrDefault(prefs[Keys.WEIGHT_UNIT]),
            exerciseSort = PreferenceValues.exerciseSortOrDefault(prefs[Keys.EXERCISE_SORT])
        )
    }

    /** `true`/`false` forces dark/light; `null` removes the choice so the device theme applies. */
    suspend fun setDarkMode(value: Boolean?) {
        store.edit { if (value == null) it.remove(Keys.DARK_MODE) else it[Keys.DARK_MODE] = value }
    }
    suspend fun setWeightUnit(value: String) {
        require(value in PreferenceValues.WEIGHT_UNITS) { "Unsupported weight unit: $value" }
        store.edit { it[Keys.WEIGHT_UNIT] = value }
    }
    suspend fun setExerciseSort(value: String) {
        require(value in PreferenceValues.EXERCISE_SORTS) { "Unsupported exercise sort: $value" }
        store.edit { it[Keys.EXERCISE_SORT] = value }
    }

    // Internal importer marker. Not counted as one of the three user-facing preferences.
    val seedImported: Flow<Boolean> = preferences.map { it[Keys.SEED_IMPORTED] ?: false }
    suspend fun markSeedImported() { store.edit { it[Keys.SEED_IMPORTED] = true } }
}
