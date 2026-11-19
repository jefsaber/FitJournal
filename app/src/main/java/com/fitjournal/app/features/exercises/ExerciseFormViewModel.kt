package com.fitjournal.app.features.exercises

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.fitjournal.app.core.data.entities.ExerciseEntity
import com.fitjournal.app.core.data.repositories.ExerciseRepository
import com.fitjournal.app.core.domain.ExerciseValidationErrors
import com.fitjournal.app.core.domain.ExerciseValidator
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ExerciseFormPhase { LOADING, READY, LOAD_ERROR, NOT_FOUND, SAVING, SAVED }

data class ExerciseFormState(
    val name: String = "",
    val category: String = "",
    val measurementType: String = "REPS_WEIGHT",
    val description: String = "",
    val phase: ExerciseFormPhase = ExerciseFormPhase.READY,
    val errors: ExerciseValidationErrors = ExerciseValidationErrors(),
    val saveFailed: Boolean = false
)

class ExerciseFormViewModel(
    private val repository: ExerciseRepository,
    private val savedState: SavedStateHandle
) : ViewModel() {
    private val exerciseId = savedState.get<Long>(ARG_EXERCISE_ID) ?: 0L
    val isEditing = exerciseId != 0L
    private var original: ExerciseEntity? = null
    private val draftCode = savedState.get<String>("draftCode")
        ?: UUID.randomUUID().toString().also { savedState["draftCode"] = it }
    private val _state = MutableStateFlow(
        ExerciseFormState(
            name = savedState["name"] ?: "",
            category = savedState["category"] ?: "",
            measurementType = savedState["measurementType"] ?: "REPS_WEIGHT",
            description = savedState["description"] ?: "",
            phase = when {
                savedState.get<Boolean>("saved") == true -> ExerciseFormPhase.SAVED
                isEditing -> ExerciseFormPhase.LOADING
                else -> ExerciseFormPhase.READY
            }
        )
    )
    val state = _state.asStateFlow()

    init {
        if (isEditing && _state.value.phase != ExerciseFormPhase.SAVED) loadExercise()
    }

    private fun loadExercise() {
        _state.update { it.copy(phase = ExerciseFormPhase.LOADING) }
        viewModelScope.launch {
            try {
                val exercise = repository.find(exerciseId)
                original = exercise
                if (exercise == null) {
                    _state.update { it.copy(phase = ExerciseFormPhase.NOT_FOUND) }
                } else {
                    if (savedState.get<Boolean>("draftInitialized") != true) {
                        savedState["name"] = exercise.name
                        savedState["category"] = exercise.category
                        savedState["measurementType"] = exercise.measurementType
                        savedState["description"] = exercise.description
                        savedState["draftInitialized"] = true
                    }
                    _state.update {
                        it.copy(
                            name = savedState["name"] ?: exercise.name,
                            category = savedState["category"] ?: exercise.category,
                            measurementType = savedState["measurementType"] ?: exercise.measurementType,
                            description = savedState["description"] ?: exercise.description,
                            phase = ExerciseFormPhase.READY
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _state.update { it.copy(phase = ExerciseFormPhase.LOAD_ERROR) }
            }
        }
    }

    fun retryLoad() {
        if (_state.value.phase == ExerciseFormPhase.LOAD_ERROR) loadExercise()
    }

    fun setName(value: String) = edit("name", value) {
        it.copy(name = value, errors = it.errors.copy(name = false))
    }
    fun setCategory(value: String) = edit("category", value) {
        it.copy(category = value, errors = it.errors.copy(category = false))
    }
    fun setMeasurementType(value: String) = edit("measurementType", value) {
        it.copy(measurementType = value, errors = it.errors.copy(measurementType = false))
    }
    fun setDescription(value: String) = edit("description", value) { it.copy(description = value) }

    private fun edit(key: String, value: String, transform: (ExerciseFormState) -> ExerciseFormState) {
        if (_state.value.phase != ExerciseFormPhase.READY) return
        savedState[key] = value
        _state.update { transform(it).copy(saveFailed = false) }
    }

    fun save() {
        val draft = _state.value
        if (draft.phase != ExerciseFormPhase.READY) return
        val errors = ExerciseValidator.validate(draft.name, draft.category, draft.measurementType)
        _state.update { it.copy(errors = errors, saveFailed = false) }
        if (errors.hasErrors) return

        val exercise = (original ?: ExerciseEntity(code = draftCode, name = "", category = "")).copy(
            name = draft.name.trim(),
            category = draft.category.trim(),
            measurementType = draft.measurementType,
            description = draft.description.trim()
        )
        _state.update { it.copy(phase = ExerciseFormPhase.SAVING) }
        viewModelScope.launch {
            try {
                if (isEditing) repository.update(exercise) else repository.create(exercise)
                savedState["saved"] = true
                _state.update { it.copy(phase = ExerciseFormPhase.SAVED) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _state.update { it.copy(phase = ExerciseFormPhase.READY, saveFailed = true) }
            }
        }
    }

    class Factory(private val repository: ExerciseRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            require(modelClass.isAssignableFrom(ExerciseFormViewModel::class.java))
            return ExerciseFormViewModel(repository, extras.createSavedStateHandle()) as T
        }
    }

    companion object {
        const val ARG_EXERCISE_ID = "exerciseId"
    }
}
