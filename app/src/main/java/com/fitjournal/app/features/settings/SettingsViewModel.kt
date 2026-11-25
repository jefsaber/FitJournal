package com.fitjournal.app.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fitjournal.app.core.preferences.AppSettings
import com.fitjournal.app.core.preferences.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Light/dark choice offered in the UI; [SYSTEM] clears the stored Boolean. */
enum class ThemeChoice(val darkMode: Boolean?) {
    SYSTEM(null), LIGHT(false), DARK(true);

    companion object {
        fun from(darkMode: Boolean?): ThemeChoice = entries.first { it.darkMode == darkMode }
    }
}

data class SettingsUiState(
    val loading: Boolean = true,
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    val weightUnit: String = AppSettings().weightUnit,
    val exerciseSort: String = AppSettings().exerciseSort,
    val saveFailed: Boolean = false
)

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {
    private val saveFailed = MutableStateFlow(false)

    val state: StateFlow<SettingsUiState> = combine(repository.settings, saveFailed) { settings, failed ->
        SettingsUiState(
            loading = false,
            theme = ThemeChoice.from(settings.darkMode),
            weightUnit = settings.weightUnit,
            exerciseSort = settings.exerciseSort,
            saveFailed = failed
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun selectTheme(choice: ThemeChoice) {
        if (!state.value.loading && state.value.theme == choice) return
        save { repository.setDarkMode(choice.darkMode) }
    }

    fun selectWeightUnit(unit: String) {
        if (!state.value.loading && state.value.weightUnit == unit) return
        save { repository.setWeightUnit(unit) }
    }

    fun selectExerciseSort(sort: String) {
        if (!state.value.loading && state.value.exerciseSort == sort) return
        save { repository.setExerciseSort(sort) }
    }

    fun dismissError() { saveFailed.value = false }

    private fun save(write: suspend () -> Unit) {
        saveFailed.value = false
        viewModelScope.launch {
            try {
                write()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                saveFailed.value = true
            }
        }
    }

    class Factory(private val repository: SettingsRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(repository) as T
    }
}
