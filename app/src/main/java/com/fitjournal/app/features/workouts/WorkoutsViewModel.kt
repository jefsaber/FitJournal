package com.fitjournal.app.features.workouts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.fitjournal.app.core.data.entities.WorkoutEntity
import com.fitjournal.app.core.data.repositories.WorkoutRepository
import com.fitjournal.app.core.domain.WorkoutHistoryFilter
import com.fitjournal.app.core.domain.WorkoutPeriodFilter
import com.fitjournal.app.core.domain.WorkoutStatusFilter
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** What the history screen shows: matching workouts plus whether any exist at all. */
data class WorkoutHistoryState(
    val workouts: List<WorkoutEntity> = emptyList(),
    val totalCount: Int = 0,
    val filter: WorkoutHistoryFilter = WorkoutHistoryFilter(),
    val loading: Boolean = true
) {
    val showNoWorkouts: Boolean get() = !loading && totalCount == 0
    val showNoMatches: Boolean get() = !loading && totalCount > 0 && workouts.isEmpty()
}

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutsViewModel(
    repository: WorkoutRepository,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val today: () -> LocalDate = { LocalDate.now(zone) }
) : ViewModel() {
    private val _filter = MutableStateFlow(
        WorkoutHistoryFilter(
            search = savedState[KEY_SEARCH] ?: "",
            status = savedState.get<String>(KEY_STATUS)
                ?.let { runCatching { WorkoutStatusFilter.valueOf(it) }.getOrNull() } ?: WorkoutStatusFilter.ALL,
            period = savedState.get<String>(KEY_PERIOD)
                ?.let { runCatching { WorkoutPeriodFilter.valueOf(it) }.getOrNull() } ?: WorkoutPeriodFilter.ANY_TIME
        )
    )
    val filter: StateFlow<WorkoutHistoryFilter> = _filter.asStateFlow()

    /** Kept for other modules that need the full, unfiltered list. */
    val history = repository.history.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val state: StateFlow<WorkoutHistoryState> = combine(
        _filter.flatMapLatest { filter -> repository.filtered(filter, today(), zone).map { filter to it } },
        repository.count
    ) { (filter, workouts), total ->
        WorkoutHistoryState(workouts, total, filter, loading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WorkoutHistoryState(filter = _filter.value))

    fun setSearch(value: String) = updateFilter { it.copy(search = value) }
    fun setStatus(value: WorkoutStatusFilter) = updateFilter { it.copy(status = value) }
    fun setPeriod(value: WorkoutPeriodFilter) = updateFilter { it.copy(period = value) }
    fun clearFilters() = updateFilter { WorkoutHistoryFilter() }

    private fun updateFilter(transform: (WorkoutHistoryFilter) -> WorkoutHistoryFilter) {
        _filter.update(transform)
        val current = _filter.value
        savedState[KEY_SEARCH] = current.search
        savedState[KEY_STATUS] = current.status.name
        savedState[KEY_PERIOD] = current.period.name
    }

    class Factory(private val repository: WorkoutRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            require(modelClass.isAssignableFrom(WorkoutsViewModel::class.java))
            return WorkoutsViewModel(repository, extras.createSavedStateHandle()) as T
        }
    }

    private companion object {
        const val KEY_SEARCH = "historySearch"
        const val KEY_STATUS = "historyStatus"
        const val KEY_PERIOD = "historyPeriod"
    }
}
