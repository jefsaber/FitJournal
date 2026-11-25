package com.fitjournal.app.features.exercises

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.paging.cachedIn
import com.fitjournal.app.core.data.repositories.ExerciseRepository
import com.fitjournal.app.core.preferences.PreferenceValues
import com.fitjournal.app.core.preferences.SettingsRepository
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ExerciseListViewModel(
    private val repository: ExerciseRepository,
    private val settings: SettingsRepository,
    private val savedState: SavedStateHandle
) : ViewModel() {
    val search = savedState.getStateFlow("exerciseSearch", "")
    val category = savedState.getStateFlow("exerciseCategory", ALL_CATEGORIES)

    val categories = repository.all.map { exercises ->
        exercises.map { it.category }.distinctBy { it.lowercase(Locale.ROOT) }
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** The catalogue control updates the same persistent order used by Settings. */
    val sort = settings.settings.map { it.exerciseSort }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _savingSort = MutableStateFlow(false)
    val savingSort = _savingSort.asStateFlow()
    private val _sortSaveFailed = MutableStateFlow(false)
    val sortSaveFailed = _sortSaveFailed.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val exercises = combine(search, category, sort) { query, group, order -> Triple(query, group, order) }
        .filter { it.third != null }
        .flatMapLatest { (query, group, order) ->
            repository.paged(search = query, category = group, sort = requireNotNull(order))
        }.cachedIn(viewModelScope)
    val count = repository.count

    fun setSearch(value: String) { savedState["exerciseSearch"] = value }
    fun setCategory(value: String) { savedState["exerciseCategory"] = value }

    fun setSort(value: String) {
        require(value in PreferenceValues.EXERCISE_SORTS)
        if (_savingSort.value || value == sort.value) return
        _sortSaveFailed.value = false
        _savingSort.value = true
        viewModelScope.launch {
            try {
                settings.setExerciseSort(value)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _sortSaveFailed.value = true
            } finally {
                _savingSort.value = false
            }
        }
    }

    class Factory(
        private val repository: ExerciseRepository,
        private val settings: SettingsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            require(modelClass.isAssignableFrom(ExerciseListViewModel::class.java))
            return ExerciseListViewModel(repository, settings, extras.createSavedStateHandle()) as T
        }
    }

    companion object {
        const val ALL_CATEGORIES = "ALL"
    }
}
