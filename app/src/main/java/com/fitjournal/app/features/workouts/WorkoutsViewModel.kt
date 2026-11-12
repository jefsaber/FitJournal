package com.fitjournal.app.features.workouts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fitjournal.app.core.data.repositories.WorkoutRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class WorkoutsViewModel(repository: WorkoutRepository) : ViewModel() {
    val history = repository.history.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    class Factory(private val repository: WorkoutRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = WorkoutsViewModel(repository) as T
    }
}
