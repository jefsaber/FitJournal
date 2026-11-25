package com.fitjournal.app

import android.app.Application
import android.util.Log
import com.fitjournal.app.core.data.AppContainer
import com.fitjournal.app.core.preferences.ThemePreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class FitJournalApplication : Application() {
    val container by lazy { AppContainer(this) }
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        // Read the saved theme once before the first activity is created, so the app never
        // flashes the device theme first. The preferences file is tiny, so this is a short read.
        val savedDarkMode = try {
            runBlocking { container.settings.settings.first().darkMode }
        } catch (error: Exception) {
            Log.e("FitJournal.Theme", "Unable to read the saved theme; following the device", error)
            null
        }
        ThemePreference.apply(savedDarkMode)

        // Later changes from Settings recreate running activities; form drafts survive
        // through their ViewModels' SavedStateHandle.
        applicationScope.launch {
            container.settings.settings
                .map { it.darkMode }
                .distinctUntilChanged()
                .collect(ThemePreference::apply)
        }
    }
}
