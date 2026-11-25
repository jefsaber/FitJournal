package com.fitjournal.app.features.exercises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import com.fitjournal.app.core.data.repositories.ExerciseRepository
import com.fitjournal.app.core.preferences.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

class ExerciseListViewModel(
    private val repository: ExerciseRepository,
    settings: SettingsRepository
) : ViewModel() {
    val search = MutableStateFlow("")

    /** Default order saved in Settings; the list re-queries whenever it changes. */
    private val sort = settings.settings.map { it.exerciseSort }.distinctUntilChanged()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val exercises = combine(search, sort) { query, order -> query to order }
        .flatMapLatest { (query, order) -> repository.paged(search = query, sort = order) }
        .cachedIn(viewModelScope)
    val count = repository.count

    class Factory(
        private val repository: ExerciseRepository,
        private val settings: SettingsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ExerciseListViewModel(repository, settings) as T
    }
}
