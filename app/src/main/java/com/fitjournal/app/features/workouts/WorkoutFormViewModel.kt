package com.fitjournal.app.features.workouts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.fitjournal.app.core.data.entities.WorkoutEntity
import com.fitjournal.app.core.data.repositories.WorkoutRepository
import com.fitjournal.app.core.domain.WorkoutDates
import com.fitjournal.app.core.domain.WorkoutValidationErrors
import com.fitjournal.app.core.domain.WorkoutValidator
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class WorkoutFormPhase { LOADING, READY, LOAD_ERROR, NOT_FOUND, SAVING, SAVED, DELETING, DELETED }

data class WorkoutFormState(
    val title: String = "",
    val date: LocalDate? = null,
    val notes: String = "",
    val isCompleted: Boolean = false,
    val phase: WorkoutFormPhase = WorkoutFormPhase.READY,
    val errors: WorkoutValidationErrors = WorkoutValidationErrors(),
    val saveFailed: Boolean = false,
    val deleteFailed: Boolean = false
)

class WorkoutFormViewModel(
    private val repository: WorkoutRepository,
    private val savedState: SavedStateHandle,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val today: () -> LocalDate = { LocalDate.now(zone) }
) : ViewModel() {
    private val workoutId = savedState.get<Long>(ARG_WORKOUT_ID) ?: 0L
    val isEditing = workoutId != 0L
    private var original: WorkoutEntity? = null

    private val _state = MutableStateFlow(
        WorkoutFormState(
            title = savedState[KEY_TITLE] ?: "",
            // New workouts default to today; edits receive the stored date once loaded.
            date = savedState.get<Long>(KEY_DATE)?.let(LocalDate::ofEpochDay)
                ?: if (isEditing) null else today(),
            notes = savedState[KEY_NOTES] ?: "",
            isCompleted = savedState[KEY_COMPLETED] ?: false,
            phase = when {
                savedState.get<Boolean>(KEY_DELETED) == true -> WorkoutFormPhase.DELETED
                savedState.get<Boolean>(KEY_SAVED) == true -> WorkoutFormPhase.SAVED
                isEditing -> WorkoutFormPhase.LOADING
                else -> WorkoutFormPhase.READY
            }
        )
    )
    val state = _state.asStateFlow()

    /** Bounds offered by the date picker; validation enforces the same rules. */
    val earliestDate: LocalDate get() = WorkoutValidator.EARLIEST_DATE
    val latestDate: LocalDate get() = today().plusDays(WorkoutValidator.MAX_DAYS_AHEAD)

    init {
        if (isEditing && _state.value.phase == WorkoutFormPhase.LOADING) loadWorkout()
    }

    private fun loadWorkout() {
        _state.update { it.copy(phase = WorkoutFormPhase.LOADING) }
        viewModelScope.launch {
            try {
                val workout = repository.find(workoutId)
                original = workout
                if (workout == null) {
                    _state.update { it.copy(phase = WorkoutFormPhase.NOT_FOUND) }
                    return@launch
                }
                // Only seed the draft once, so a restored draft is never overwritten.
                if (savedState.get<Boolean>(KEY_INITIALIZED) != true) {
                    savedState[KEY_TITLE] = workout.title
                    savedState[KEY_DATE] = WorkoutDates.toLocalDate(workout.sessionDate, zone).toEpochDay()
                    savedState[KEY_NOTES] = workout.notes
                    savedState[KEY_COMPLETED] = workout.isCompleted
                    savedState[KEY_INITIALIZED] = true
                }
                _state.update {
                    it.copy(
                        title = savedState[KEY_TITLE] ?: workout.title,
                        date = savedState.get<Long>(KEY_DATE)?.let(LocalDate::ofEpochDay),
                        notes = savedState[KEY_NOTES] ?: workout.notes,
                        isCompleted = savedState[KEY_COMPLETED] ?: workout.isCompleted,
                        phase = WorkoutFormPhase.READY
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _state.update { it.copy(phase = WorkoutFormPhase.LOAD_ERROR) }
            }
        }
    }

    fun retryLoad() {
        if (_state.value.phase == WorkoutFormPhase.LOAD_ERROR) loadWorkout()
    }

    fun setTitle(value: String) = edit {
        savedState[KEY_TITLE] = value
        it.copy(title = value, errors = it.errors.copy(title = false))
    }

    fun setDate(value: LocalDate) = edit {
        savedState[KEY_DATE] = value.toEpochDay()
        it.copy(date = value, errors = it.errors.copy(date = null))
    }

    fun setNotes(value: String) = edit {
        savedState[KEY_NOTES] = value
        it.copy(notes = value, errors = it.errors.copy(notes = false))
    }

    fun setCompleted(value: Boolean) = edit {
        savedState[KEY_COMPLETED] = value
        // Completion affects whether a future date is allowed, so re-check that rule.
        it.copy(isCompleted = value, errors = it.errors.copy(date = null))
    }

    private fun edit(transform: (WorkoutFormState) -> WorkoutFormState) {
        if (_state.value.phase != WorkoutFormPhase.READY) return
        _state.update { transform(it).copy(saveFailed = false, deleteFailed = false) }
    }

    fun save() {
        val draft = _state.value
        if (draft.phase != WorkoutFormPhase.READY) return
        val errors = WorkoutValidator.validate(draft.title, draft.date, draft.notes, draft.isCompleted, today())
        _state.update { it.copy(errors = errors, saveFailed = false, deleteFailed = false) }
        if (errors.hasErrors) return

        val workout = (original ?: WorkoutEntity(title = "", sessionDate = 0L)).copy(
            title = draft.title.trim(),
            sessionDate = WorkoutDates.toEpochMillis(requireNotNull(draft.date), zone),
            notes = draft.notes.trim(),
            isCompleted = draft.isCompleted
        )
        _state.update { it.copy(phase = WorkoutFormPhase.SAVING) }
        viewModelScope.launch {
            try {
                if (isEditing) repository.update(workout) else repository.create(workout)
                savedState[KEY_SAVED] = true
                _state.update { it.copy(phase = WorkoutFormPhase.SAVED) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _state.update { it.copy(phase = WorkoutFormPhase.READY, saveFailed = true) }
            }
        }
    }

    /** Deletes the loaded workout; its entries are removed by the cascading foreign key. */
    fun delete() {
        val workout = original ?: return
        if (!isEditing || _state.value.phase != WorkoutFormPhase.READY) return
        _state.update { it.copy(phase = WorkoutFormPhase.DELETING, saveFailed = false, deleteFailed = false) }
        viewModelScope.launch {
            try {
                repository.delete(workout)
                savedState[KEY_DELETED] = true
                _state.update { it.copy(phase = WorkoutFormPhase.DELETED) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _state.update { it.copy(phase = WorkoutFormPhase.READY, deleteFailed = true) }
            }
        }
    }

    class Factory(private val repository: WorkoutRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            require(modelClass.isAssignableFrom(WorkoutFormViewModel::class.java))
            return WorkoutFormViewModel(repository, extras.createSavedStateHandle()) as T
        }
    }

    companion object {
        const val ARG_WORKOUT_ID = "workoutId"
        private const val KEY_TITLE = "title"
        private const val KEY_DATE = "dateEpochDay"
        private const val KEY_NOTES = "notes"
        private const val KEY_COMPLETED = "completed"
        private const val KEY_INITIALIZED = "draftInitialized"
        private const val KEY_SAVED = "saved"
        private const val KEY_DELETED = "deleted"
    }
}
