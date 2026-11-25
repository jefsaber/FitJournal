package com.fitjournal.app

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.fitjournal.app.core.preferences.AppSettings
import com.fitjournal.app.core.preferences.PreferenceValues
import com.fitjournal.app.core.preferences.SettingsRepository
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsRepositoryTest {
    @get:Rule val folder = TemporaryFolder()
    private val scopes = mutableListOf<CoroutineScope>()

    private val file: File by lazy { File(folder.root, "settings.preferences_pb") }

    /** Each call opens the same file with a fresh DataStore, like a new app process would. */
    private fun openStore() = CoroutineScope(Dispatchers.IO + SupervisorJob()).let { scope ->
        scopes += scope
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
    }

    private fun closeStores() {
        scopes.forEach { it.cancel() }
        scopes.clear()
    }

    @After fun tearDown() = closeStores()

    @Test fun defaultsFollowDeviceThemeKilogramsAndAscendingSort() = runBlocking {
        val settings = SettingsRepository(openStore()).settings.first()
        assertEquals(AppSettings(darkMode = null, weightUnit = "kg", exerciseSort = "NAME_ASC"), settings)
    }

    @Test fun rejectsUnsupportedValuesWithoutChangingStoredSettings() = runBlocking {
        val repository = SettingsRepository(openStore())
        repository.setWeightUnit("lb")
        repository.setExerciseSort("NAME_DESC")

        assertThrows(IllegalArgumentException::class.java) { runBlocking { repository.setWeightUnit("stone") } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repository.setWeightUnit("LB") } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repository.setExerciseSort("DATE") } }

        val settings = repository.settings.first()
        assertEquals("lb", settings.weightUnit)
        assertEquals("NAME_DESC", settings.exerciseSort)
    }

    @Test fun preferencesPersistAcrossStoreInstances() = runBlocking {
        SettingsRepository(openStore()).apply {
            setDarkMode(true)
            setWeightUnit("lb")
            setExerciseSort("NAME_DESC")
        }
        closeStores()

        val reopened = SettingsRepository(openStore())
        assertEquals(AppSettings(darkMode = true, weightUnit = "lb", exerciseSort = "NAME_DESC"), reopened.settings.first())
        assertFalse(reopened.seedImported.first())
    }

    @Test fun clearingDarkModeReturnsToDeviceTheme() = runBlocking {
        val repository = SettingsRepository(openStore())
        repository.setDarkMode(false)
        assertEquals(false, repository.settings.first().darkMode)
        repository.setDarkMode(null)
        assertNull(repository.settings.first().darkMode)
    }

    @Test fun unknownStoredValuesFallBackToDefaults() = runBlocking {
        val store = openStore()
        store.edit {
            it[stringPreferencesKey("weight_unit")] = "stone"
            it[stringPreferencesKey("exercise_sort")] = "RANDOM"
        }
        val settings = SettingsRepository(store).settings.first()
        assertEquals(PreferenceValues.WEIGHT_KG, settings.weightUnit)
        assertEquals(PreferenceValues.SORT_NAME_ASC, settings.exerciseSort)
    }
}
