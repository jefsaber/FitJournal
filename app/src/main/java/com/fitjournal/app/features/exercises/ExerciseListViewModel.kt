package com.fitjournal.app.features.exercises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import com.fitjournal.app.core.data.repositories.ExerciseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest

class ExerciseListViewModel(private val repository: ExerciseRepository) : ViewModel() {
    val search = MutableStateFlow("")
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val exercises = search.flatMapLatest { repository.paged(search = it) }.cachedIn(viewModelScope)
    val count = repository.count

    class Factory(private val repository: ExerciseRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ExerciseListViewModel(repository) as T
    }
}
