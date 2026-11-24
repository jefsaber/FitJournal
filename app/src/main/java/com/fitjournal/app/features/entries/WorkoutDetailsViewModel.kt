package com.fitjournal.app.features.entries

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.fitjournal.app.core.data.entities.ExerciseEntity
import com.fitjournal.app.core.data.entities.WorkoutEntity
import com.fitjournal.app.core.data.entities.WorkoutEntryEntity
import com.fitjournal.app.core.data.models.LoggedWorkoutEntry
import com.fitjournal.app.core.data.repositories.ExerciseRepository
import com.fitjournal.app.core.data.repositories.WorkoutEntryRepository
import com.fitjournal.app.core.data.repositories.WorkoutRepository
import com.fitjournal.app.core.domain.WeightConverter
import com.fitjournal.app.core.domain.WeightUnit
import com.fitjournal.app.core.domain.WorkoutEntryValidator
import com.fitjournal.app.core.preferences.SettingsRepository
import java.math.BigDecimal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EntryFieldErrors(
    val sets: Boolean = false,
    val repetitions: Boolean = false,
    val weight: Boolean = false,
    val duration: Boolean = false
) {
    val hasErrors: Boolean get() = sets || repetitions || weight || duration
}

data class WorkoutEntryDraft(
    val entryId: Long = 0,
    val exerciseId: Long,
    val sets: String = "",
    val repetitions: String = "",
    val weight: String = "",
    val duration: String = "",
    val weightUnit: WeightUnit = WeightUnit.KILOGRAMS,
    val errors: EntryFieldErrors = EntryFieldErrors()
) {
    val isEditing: Boolean get() = entryId != 0L
}

data class WorkoutDetailsState(
    val workout: WorkoutEntity? = null,
    val entries: List<LoggedWorkoutEntry> = emptyList(),
    val exercises: List<ExerciseEntity> = emptyList(),
    val weightUnit: WeightUnit = WeightUnit.KILOGRAMS,
    val editor: WorkoutEntryDraft? = null,
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val saving: Boolean = false,
    val deletingEntryId: Long? = null,
    val operationFailed: Boolean = false
) {
    val notFound: Boolean get() = !loading && !loadFailed && workout == null
}

private data class WorkoutDetailsContent(
    val workout: WorkoutEntity? = null,
    val entries: List<LoggedWorkoutEntry> = emptyList(),
    val exercises: List<ExerciseEntity> = emptyList(),
    val loading: Boolean = false,
    val loadFailed: Boolean = false
)

private data class EntryOperation(
    val saving: Boolean = false,
    val deletingEntryId: Long? = null,
    val failed: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutDetailsViewModel(
    private val workoutId: Long,
    workoutRepository: WorkoutRepository,
    private val entryRepository: WorkoutEntryRepository,
    exerciseRepository: ExerciseRepository,
    settingsRepository: SettingsRepository,
    private val savedState: SavedStateHandle
) : ViewModel() {
    private val reload = MutableStateFlow(0)
    private val draft = MutableStateFlow(restoreDraft())
    private val operation = MutableStateFlow(EntryOperation())

    private val content = reload.flatMapLatest {
        combine(
            workoutRepository.byId(workoutId),
            entryRepository.detailedByWorkout(workoutId),
            exerciseRepository.all
        ) { workout, entries, exercises ->
            WorkoutDetailsContent(workout, entries, exercises)
        }.catch { emit(WorkoutDetailsContent(loadFailed = true)) }
    }

    val state = combine(content, settingsRepository.settings, draft, operation) { content, settings, editor, action ->
        WorkoutDetailsState(
            workout = content.workout,
            entries = content.entries,
            exercises = content.exercises,
            weightUnit = WeightUnit.fromCode(settings.weightUnit),
            editor = editor,
            loading = content.loading,
            loadFailed = content.loadFailed,
            saving = action.saving,
            deletingEntryId = action.deletingEntryId,
            operationFailed = action.failed
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutDetailsState())

    fun retryLoad() = reload.update { it + 1 }

    fun openNewEntry() {
        if (operation.value.saving || draft.value != null) return
        val current = state.value
        val exercise = current.exercises.firstOrNull() ?: return
        setDraft(WorkoutEntryDraft(exerciseId = exercise.id, weightUnit = current.weightUnit))
    }

    fun openExistingEntry(item: LoggedWorkoutEntry) {
        if (operation.value.saving || draft.value != null) return
        val unit = state.value.weightUnit
        val entry = item.entry
        setDraft(
            WorkoutEntryDraft(
                entryId = entry.id,
                exerciseId = entry.exerciseId,
                sets = entry.sets.takeIf { it > 0 }?.toString().orEmpty(),
                repetitions = entry.repetitions.takeIf { it > 0 }?.toString().orEmpty(),
                weight = entry.weightKg.takeIf { it > 0.0 }
                    ?.let { editableNumber(WeightConverter.fromKilograms(it, unit)) }.orEmpty(),
                duration = entry.durationSeconds.takeIf { it > 0L }?.toString().orEmpty(),
                weightUnit = unit
            )
        )
    }

    fun closeEditor() {
        if (!operation.value.saving) setDraft(null)
    }

    fun selectExercise(exerciseId: Long) = editDraft {
        if (it.isEditing) it else it.copy(exerciseId = exerciseId, errors = EntryFieldErrors())
    }

    fun setSets(value: String) = editDraft { it.copy(sets = value, errors = it.errors.copy(sets = false)) }
    fun setRepetitions(value: String) = editDraft {
        it.copy(repetitions = value, errors = it.errors.copy(repetitions = false))
    }
    fun setWeight(value: String) = editDraft { it.copy(weight = value, errors = it.errors.copy(weight = false)) }
    fun setDuration(value: String) = editDraft {
        it.copy(duration = value, errors = it.errors.copy(duration = false))
    }

    private fun editDraft(transform: (WorkoutEntryDraft) -> WorkoutEntryDraft) {
        if (operation.value.saving) return
        draft.value?.let { setDraft(transform(it)) }
        operation.update { it.copy(failed = false) }
    }

    fun saveEntry() {
        val editor = draft.value ?: return
        if (operation.value.saving) return
        val exercise = state.value.exercises.firstOrNull { it.id == editor.exerciseId } ?: return
        val entry = if (exercise.measurementType == DURATION) {
            val seconds = editor.duration.trim().toLongOrNull()
            val errors = EntryFieldErrors(duration = seconds == null || WorkoutEntryValidator.durationHasErrors(seconds))
            if (errors.hasErrors) {
                setDraft(editor.copy(errors = errors))
                return
            }
            WorkoutEntryEntity(
                id = editor.entryId,
                workoutId = workoutId,
                exerciseId = exercise.id,
                durationSeconds = requireNotNull(seconds)
            )
        } else {
            val sets = editor.sets.trim().toIntOrNull()
            val repetitions = editor.repetitions.trim().toIntOrNull()
            val displayWeight = editor.weight.trim().replace(',', '.').toDoubleOrNull()
            val kilograms = displayWeight?.let { WeightConverter.toKilograms(it, editor.weightUnit) }
            val validation = WorkoutEntryValidator.validateStrength(sets, repetitions, kilograms)
            val errors = EntryFieldErrors(validation.sets, validation.repetitions, validation.weight)
            if (errors.hasErrors) {
                setDraft(editor.copy(errors = errors))
                return
            }
            WorkoutEntryEntity(
                id = editor.entryId,
                workoutId = workoutId,
                exerciseId = exercise.id,
                sets = requireNotNull(sets),
                repetitions = requireNotNull(repetitions),
                weightKg = requireNotNull(kilograms)
            )
        }

        operation.value = EntryOperation(saving = true)
        viewModelScope.launch {
            try {
                if (entry.id == 0L) entryRepository.create(entry) else entryRepository.update(entry)
                setDraft(null)
                operation.value = EntryOperation()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                operation.value = EntryOperation(failed = true)
            }
        }
    }

    fun deleteEntry(item: LoggedWorkoutEntry) {
        if (operation.value.deletingEntryId != null || operation.value.saving) return
        operation.value = EntryOperation(deletingEntryId = item.entry.id)
        viewModelScope.launch {
            try {
                entryRepository.delete(item.entry)
                operation.value = EntryOperation()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                operation.value = EntryOperation(failed = true)
            }
        }
    }

    fun clearOperationError() = operation.update { it.copy(failed = false) }

    private fun setDraft(value: WorkoutEntryDraft?) {
        draft.value = value
        savedState[KEY_EDITOR_OPEN] = value != null
        savedState[KEY_ENTRY_ID] = value?.entryId ?: 0L
        savedState[KEY_EXERCISE_ID] = value?.exerciseId ?: 0L
        savedState[KEY_SETS] = value?.sets.orEmpty()
        savedState[KEY_REPETITIONS] = value?.repetitions.orEmpty()
        savedState[KEY_WEIGHT] = value?.weight.orEmpty()
        savedState[KEY_DURATION] = value?.duration.orEmpty()
        savedState[KEY_WEIGHT_UNIT] = value?.weightUnit?.code ?: WeightUnit.KILOGRAMS.code
    }

    private fun restoreDraft(): WorkoutEntryDraft? {
        if (savedState.get<Boolean>(KEY_EDITOR_OPEN) != true) return null
        val exerciseId = savedState.get<Long>(KEY_EXERCISE_ID) ?: return null
        if (exerciseId == 0L) return null
        return WorkoutEntryDraft(
            entryId = savedState[KEY_ENTRY_ID] ?: 0L,
            exerciseId = exerciseId,
            sets = savedState[KEY_SETS] ?: "",
            repetitions = savedState[KEY_REPETITIONS] ?: "",
            weight = savedState[KEY_WEIGHT] ?: "",
            duration = savedState[KEY_DURATION] ?: "",
            weightUnit = WeightUnit.fromCode(savedState[KEY_WEIGHT_UNIT] ?: WeightUnit.KILOGRAMS.code)
        )
    }

    class Factory(
        private val workoutId: Long,
        private val workouts: WorkoutRepository,
        private val entries: WorkoutEntryRepository,
        private val exercises: ExerciseRepository,
        private val settings: SettingsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            require(modelClass.isAssignableFrom(WorkoutDetailsViewModel::class.java))
            return WorkoutDetailsViewModel(
                workoutId, workouts, entries, exercises, settings, extras.createSavedStateHandle()
            ) as T
        }
    }

    private companion object {
        const val DURATION = "DURATION"
        const val KEY_EDITOR_OPEN = "entryEditorOpen"
        const val KEY_ENTRY_ID = "entryDraftId"
        const val KEY_EXERCISE_ID = "entryDraftExerciseId"
        const val KEY_SETS = "entryDraftSets"
        const val KEY_REPETITIONS = "entryDraftRepetitions"
        const val KEY_WEIGHT = "entryDraftWeight"
        const val KEY_DURATION = "entryDraftDuration"
        const val KEY_WEIGHT_UNIT = "entryDraftWeightUnit"

        fun editableNumber(value: Double): String = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
    }
}
