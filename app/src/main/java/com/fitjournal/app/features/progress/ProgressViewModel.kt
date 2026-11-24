package com.fitjournal.app.features.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fitjournal.app.core.data.models.LoggedWorkoutEntry
import com.fitjournal.app.core.data.repositories.WorkoutEntryRepository
import com.fitjournal.app.core.data.repositories.WorkoutRepository
import com.fitjournal.app.core.domain.ProgressCalculator
import com.fitjournal.app.core.domain.WeightUnit
import com.fitjournal.app.core.preferences.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class PersonalRecord(
    val exerciseName: String,
    val measurementType: String,
    /** Kilograms for strength records; seconds for duration records. */
    val canonicalValue: Double
)

data class WorkoutVolumePoint(
    val title: String,
    val sessionDate: Long,
    val volumeKg: Double
)

data class ProgressState(
    val completedWorkouts: Int = 0,
    val totalVolumeKg: Double = 0.0,
    val records: List<PersonalRecord> = emptyList(),
    val recentVolume: List<WorkoutVolumePoint> = emptyList(),
    val weightUnit: WeightUnit = WeightUnit.KILOGRAMS,
    val loading: Boolean = true,
    val loadFailed: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModel(
    workouts: WorkoutRepository,
    entries: WorkoutEntryRepository,
    settings: SettingsRepository
) : ViewModel() {
    private val reload = MutableStateFlow(0)

    val state = reload.flatMapLatest {
        combine(workouts.history, entries.allDetailed, settings.settings) { workoutList, entryList, appSettings ->
            val completed = workoutList.filter { it.isCompleted }
            val completedIds = completed.mapTo(mutableSetOf()) { it.id }
            val completedEntries = entryList.filter { it.entry.workoutId in completedIds }
            val metrics = completedEntries.map(LoggedWorkoutEntry::metric)
            val grouped = completedEntries.groupBy { it.entry.exerciseId }
            val records = grouped.values.mapNotNull { logged ->
                val first = logged.first()
                ProgressCalculator.personalRecord(first.measurementType, logged.map(LoggedWorkoutEntry::metric))
                    ?.let { PersonalRecord(first.exerciseName, first.measurementType, it) }
            }.sortedBy { it.exerciseName.lowercase() }
            val entriesByWorkout = completedEntries.groupBy { it.entry.workoutId }
            val recent = completed.sortedByDescending { it.sessionDate }.take(7).reversed().map { workout ->
                WorkoutVolumePoint(
                    workout.title,
                    workout.sessionDate,
                    ProgressCalculator.totalVolumeKg(entriesByWorkout[workout.id].orEmpty().map(LoggedWorkoutEntry::metric))
                )
            }
            ProgressState(
                completedWorkouts = completed.size,
                totalVolumeKg = ProgressCalculator.totalVolumeKg(metrics),
                records = records,
                recentVolume = recent,
                weightUnit = WeightUnit.fromCode(appSettings.weightUnit),
                loading = false
            )
        }.catch { emit(ProgressState(loading = false, loadFailed = true)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressState())

    fun retry() = reload.update { it + 1 }

    class Factory(
        private val workouts: WorkoutRepository,
        private val entries: WorkoutEntryRepository,
        private val settings: SettingsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ProgressViewModel::class.java))
            return ProgressViewModel(workouts, entries, settings) as T
        }
    }
}

private fun LoggedWorkoutEntry.metric() = ProgressCalculator.EntryMetric(
    measurementType = measurementType,
    sets = entry.sets,
    repetitions = entry.repetitions,
    weightKg = entry.weightKg,
    durationSeconds = entry.durationSeconds
)
