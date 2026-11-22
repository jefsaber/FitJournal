package com.fitjournal.app.features.workouts

import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.fitjournal.app.FitJournalApplication
import com.fitjournal.app.R
import com.fitjournal.app.core.domain.WorkoutDateError
import com.fitjournal.app.core.domain.WorkoutDates
import com.fitjournal.app.databinding.FragmentWorkoutFormBinding
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.CompositeDateValidator
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class WorkoutFormFragment : Fragment() {
    private var _binding: FragmentWorkoutFormBinding? = null
    private val binding get() = requireNotNull(_binding)
    private var rendering = false
    private val model by viewModels<WorkoutFormViewModel> {
        val repository = (requireActivity().application as FitJournalApplication).container.workouts
        WorkoutFormViewModel.Factory(repository)
    }

    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup?, saved: Bundle?): View {
        _binding = FragmentWorkoutFormBinding.inflate(inflater, parent, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.workoutFormTitle.setText(if (model.isEditing) R.string.workout_edit else R.string.add_workout)
        binding.deleteWorkoutButton.isVisible = model.isEditing
        binding.workoutTitleInput.doAfterTextChanged { if (!rendering) model.setTitle(it.toString()) }
        binding.workoutNotesInput.doAfterTextChanged { if (!rendering) model.setNotes(it.toString()) }
        binding.workoutCompletedSwitch.setOnCheckedChangeListener { _, checked ->
            if (!rendering) model.setCompleted(checked)
        }
        binding.workoutDateInput.setOnClickListener { showDatePicker() }
        binding.workoutDateLayout.setEndIconOnClickListener { showDatePicker() }
        // A picker that survived recreation needs its listener again.
        (childFragmentManager.findFragmentByTag(DATE_PICKER_TAG) as? MaterialDatePicker<*>)?.let(::listenTo)

        binding.saveWorkoutButton.setOnClickListener {
            model.save()
            val errors = model.state.value.errors
            val invalidField = when {
                errors.title -> binding.workoutTitleInput
                errors.date != null -> binding.workoutDateInput
                errors.notes -> binding.workoutNotesInput
                else -> null
            }
            invalidField?.let { field ->
                field.requestFocus()
                field.post { field.requestRectangleOnScreen(Rect(0, 0, field.width, field.height), false) }
            }
        }
        binding.deleteWorkoutButton.setOnClickListener { confirmDelete() }
        binding.workoutRetryButton.setOnClickListener { model.retryLoad() }
        binding.workoutCancelButton.setOnClickListener { findNavController().popBackStack() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.state.collect { state ->
                    when (state.phase) {
                        WorkoutFormPhase.SAVED -> leaveForm(toHistory = false)
                        WorkoutFormPhase.DELETED -> leaveForm(toHistory = true)
                        else -> render(state)
                    }
                }
            }
        }
    }

    private fun showDatePicker() {
        if (model.state.value.phase != WorkoutFormPhase.READY) return
        if (childFragmentManager.findFragmentByTag(DATE_PICKER_TAG) != null) return
        val earliest = WorkoutDates.toPickerSelection(model.earliestDate)
        val latest = WorkoutDates.toPickerSelection(model.latestDate)
        val constraints = CalendarConstraints.Builder()
            .setStart(earliest)
            .setEnd(latest)
            .setValidator(CompositeDateValidator.allOf(listOf(
                DateValidatorPointForward.from(earliest),
                DateValidatorPointBackward.before(latest + 1)
            )))
            .build()
        val selection = model.state.value.date?.let(WorkoutDates::toPickerSelection)
            ?: MaterialDatePicker.todayInUtcMilliseconds()
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.workout_date_picker_title)
            .setSelection(selection)
            .setCalendarConstraints(constraints)
            .build()
        listenTo(picker)
        picker.show(childFragmentManager, DATE_PICKER_TAG)
    }

    private fun listenTo(picker: MaterialDatePicker<*>) {
        picker.addOnPositiveButtonClickListener { selection ->
            (selection as? Long)?.let { model.setDate(WorkoutDates.fromPickerSelection(it)) }
        }
    }

    private fun confirmDelete() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.workout_delete_title)
            .setMessage(R.string.workout_delete_message)
            .setNegativeButton(R.string.exercise_cancel, null)
            .setPositiveButton(R.string.workout_delete_confirm) { _, _ -> model.delete() }
            .show()
    }

    private fun leaveForm(toHistory: Boolean) {
        val navigation = findNavController()
        if (navigation.currentDestination?.id != R.id.workoutFormFragment) return
        // After deleting, skip any details screen for the removed workout.
        if (!toHistory || !navigation.popBackStack(R.id.workoutsFragment, false)) navigation.popBackStack()
    }

    private fun render(state: WorkoutFormState) {
        rendering = true
        try {
            binding.workoutTitleInput.showText(state.title)
            binding.workoutNotesInput.showText(state.notes)
            binding.workoutDateInput.showText(state.date?.let(WorkoutDateFormat::full).orEmpty())
            binding.workoutCompletedSwitch.isChecked = state.isCompleted
        } finally {
            rendering = false
        }

        val editable = state.phase == WorkoutFormPhase.READY
        binding.workoutTitleInput.isEnabled = editable
        binding.workoutDateInput.isEnabled = editable
        binding.workoutDateLayout.isEnabled = editable
        binding.workoutNotesInput.isEnabled = editable
        binding.workoutCompletedSwitch.isEnabled = editable
        binding.saveWorkoutButton.isEnabled = editable
        binding.deleteWorkoutButton.isEnabled = editable
        binding.workoutCancelButton.isEnabled =
            state.phase != WorkoutFormPhase.SAVING && state.phase != WorkoutFormPhase.DELETING
        binding.workoutFormProgress.isVisible = state.phase in setOf(
            WorkoutFormPhase.LOADING, WorkoutFormPhase.SAVING, WorkoutFormPhase.DELETING
        )
        binding.workoutRetryButton.isVisible = state.phase == WorkoutFormPhase.LOAD_ERROR
        binding.workoutTitleLayout.error =
            if (state.errors.title) getString(R.string.workout_title_error) else null
        binding.workoutDateLayout.error = when (state.errors.date) {
            WorkoutDateError.MISSING -> getString(R.string.workout_date_missing_error)
            WorkoutDateError.TOO_EARLY -> getString(R.string.workout_date_too_early_error)
            WorkoutDateError.TOO_FAR_AHEAD -> getString(R.string.workout_date_too_far_error)
            WorkoutDateError.COMPLETED_IN_FUTURE -> getString(R.string.workout_date_completed_future_error)
            null -> null
        }
        binding.workoutNotesLayout.error =
            if (state.errors.notes) getString(R.string.workout_notes_error) else null
        val errorMessage = when {
            state.phase == WorkoutFormPhase.LOAD_ERROR -> R.string.workout_load_error
            state.phase == WorkoutFormPhase.NOT_FOUND -> R.string.workout_not_found
            state.saveFailed -> R.string.workout_save_error
            state.deleteFailed -> R.string.workout_delete_error
            else -> null
        }
        binding.workoutFormError.isVisible = errorMessage != null
        errorMessage?.let { binding.workoutFormError.setText(it) }
    }

    private fun EditText.showText(value: String) {
        if (text.toString() != value) {
            setText(value)
            if (isFocusable) setSelection(value.length)
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val DATE_PICKER_TAG = "workout_date_picker"
    }
}
