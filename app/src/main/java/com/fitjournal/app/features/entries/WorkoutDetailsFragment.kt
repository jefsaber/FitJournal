package com.fitjournal.app.features.entries

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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
import com.fitjournal.app.core.data.models.LoggedWorkoutEntry
import com.fitjournal.app.core.domain.WeightConverter
import com.fitjournal.app.core.domain.WeightUnit
import com.fitjournal.app.databinding.DialogWorkoutEntryBinding
import com.fitjournal.app.databinding.FragmentWorkoutDetailsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.DateFormat
import java.text.DecimalFormat
import java.util.Date
import kotlinx.coroutines.launch

class WorkoutDetailsFragment : Fragment() {
    private var _binding: FragmentWorkoutDetailsBinding? = null
    private val binding get() = requireNotNull(_binding)
    private var entryDialog: AlertDialog? = null
    private var entryDialogBinding: DialogWorkoutEntryBinding? = null
    private var renderingEditor = false

    private val appContainer get() = (requireActivity().application as FitJournalApplication).container
    private val model by viewModels<WorkoutDetailsViewModel> {
        WorkoutDetailsViewModel.Factory(
            requireArguments().getLong(ARG_WORKOUT_ID),
            appContainer.workouts,
            appContainer.entries,
            appContainer.exercises,
            appContainer.settings
        )
    }

    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup?, state: Bundle?): View {
        _binding = FragmentWorkoutDetailsBinding.inflate(inflater, parent, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.workoutDetailsRetry.setOnClickListener {
            if (model.state.value.loadFailed) model.retryLoad() else findNavController().popBackStack()
        }
        binding.addEntryButton.setOnClickListener {
            if (model.state.value.exercises.isEmpty()) {
                Toast.makeText(requireContext(), R.string.workout_entry_no_exercises, Toast.LENGTH_LONG).show()
            } else {
                model.openNewEntry()
            }
        }
        binding.workoutEntryOperationError.setOnClickListener { model.clearOperationError() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.state.collect(::render)
            }
        }
    }

    private fun render(state: WorkoutDetailsState) {
        binding.workoutDetailsProgress.isVisible = state.loading
        binding.workoutDetailsError.isVisible = state.loadFailed || state.notFound
        binding.workoutDetailsError.setText(
            if (state.loadFailed) R.string.workout_load_error else R.string.workout_not_found
        )
        binding.workoutDetailsRetry.isVisible = state.loadFailed || state.notFound
        binding.addEntryButton.isVisible = !state.loading && state.workout != null
        binding.addEntryButton.isEnabled = !state.saving && state.deletingEntryId == null
        binding.workoutEntryOperationError.isVisible = state.operationFailed

        state.workout?.let { workout ->
            binding.workoutDetailsTitle.text = workout.title
            val status = getString(
                if (workout.isCompleted) R.string.workout_status_completed else R.string.workout_status_planned
            )
            binding.workoutDetailsMeta.text = "$status · ${DateFormat.getDateInstance().format(Date(workout.sessionDate))}"
            binding.workoutDetailsNotes.text = workout.notes
            binding.workoutDetailsNotes.isVisible = workout.notes.isNotBlank()
        }

        binding.workoutDetailsEntries.removeAllViews()
        binding.workoutDetailsEmpty.isVisible = state.workout != null && !state.loading && state.entries.isEmpty()
        state.entries.forEach { item -> addEntryRow(item, state) }
        syncEditor(state)
    }

    private fun addEntryRow(item: LoggedWorkoutEntry, state: WorkoutDetailsState) {
        val row = layoutInflater.inflate(R.layout.item_workout_entry, binding.workoutDetailsEntries, false)
        row.findViewById<TextView>(R.id.entry_name).text = item.exerciseName
        row.findViewById<TextView>(R.id.entry_metrics).text = formatMetrics(item, state.weightUnit)
        row.findViewById<View>(R.id.edit_entry_button).apply {
            contentDescription = getString(R.string.workout_entry_edit_description, item.exerciseName)
            isEnabled = state.deletingEntryId == null && !state.saving
            setOnClickListener { model.openExistingEntry(item) }
        }
        row.findViewById<View>(R.id.delete_entry_button).apply {
            contentDescription = getString(R.string.workout_entry_delete_description, item.exerciseName)
            isEnabled = state.deletingEntryId == null && !state.saving
            setOnClickListener { confirmDelete(item) }
        }
        binding.workoutDetailsEntries.addView(row)
    }

    private fun formatMetrics(item: LoggedWorkoutEntry, unit: WeightUnit): String {
        val entry = item.entry
        return if (item.measurementType == DURATION) {
            getString(R.string.workout_entry_duration_value, formatDuration(entry.durationSeconds))
        } else {
            val weight = WeightConverter.fromKilograms(entry.weightKg, unit)
            getString(
                R.string.workout_entry_strength_value,
                entry.sets,
                entry.repetitions,
                DecimalFormat("0.##").format(weight),
                unit.code
            )
        }
    }

    private fun confirmDelete(item: LoggedWorkoutEntry) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.workout_entry_delete_title)
            .setMessage(R.string.workout_entry_delete_message)
            .setNegativeButton(R.string.exercise_cancel, null)
            .setPositiveButton(R.string.workout_entry_delete) { _, _ -> model.deleteEntry(item) }
            .show()
    }

    private fun syncEditor(state: WorkoutDetailsState) {
        val editor = state.editor
        if (editor == null) {
            entryDialog?.dismiss()
            return
        }
        if (entryDialog == null) createEntryDialog(state)
        renderEditor(state)
    }

    private fun createEntryDialog(state: WorkoutDetailsState) {
        val editor = state.editor ?: return
        val dialogBinding = DialogWorkoutEntryBinding.inflate(layoutInflater)
        entryDialogBinding = dialogBinding
        dialogBinding.entryExercisePicker.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_dropdown_item, state.exercises.map { it.name }
        )
        val initialSelection = state.exercises.indexOfFirst { it.id == editor.exerciseId }.coerceAtLeast(0)
        dialogBinding.entryExercisePicker.setSelection(initialSelection, false)
        bindEditorChanges(dialogBinding)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (editor.isEditing) R.string.workout_entry_edit else R.string.workout_entry_choose_exercise)
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.exercise_cancel) { _, _ -> model.closeEditor() }
            .setPositiveButton(R.string.workout_entry_save, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { model.saveEntry() }
        }
        dialog.setOnCancelListener { model.closeEditor() }
        dialog.setOnDismissListener {
            entryDialog = null
            entryDialogBinding = null
            model.closeEditor()
        }
        entryDialog = dialog
        dialog.show()
    }

    private fun bindEditorChanges(dialog: DialogWorkoutEntryBinding) {
        dialog.entryExercisePicker.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!renderingEditor) model.state.value.exercises.getOrNull(position)?.let {
                    model.selectExercise(it.id)
                }
            }
        }
        dialog.entrySetsInput.doAfterTextChanged { if (!renderingEditor) model.setSets(it.toString()) }
        dialog.entryRepetitionsInput.doAfterTextChanged {
            if (!renderingEditor) model.setRepetitions(it.toString())
        }
        dialog.entryWeightInput.doAfterTextChanged { if (!renderingEditor) model.setWeight(it.toString()) }
        dialog.entryDurationInput.doAfterTextChanged { if (!renderingEditor) model.setDuration(it.toString()) }
    }

    private fun renderEditor(state: WorkoutDetailsState) {
        val editor = state.editor ?: return
        val dialog = entryDialog ?: return
        val fields = entryDialogBinding ?: return
        renderingEditor = true
        try {
            val selection = state.exercises.indexOfFirst { it.id == editor.exerciseId }.coerceAtLeast(0)
            if (fields.entryExercisePicker.selectedItemPosition != selection) {
                fields.entryExercisePicker.setSelection(selection)
            }
            fields.entryExercisePicker.isEnabled = !editor.isEditing && !state.saving
            fields.entrySetsInput.showText(editor.sets)
            fields.entryRepetitionsInput.showText(editor.repetitions)
            fields.entryWeightInput.showText(editor.weight)
            fields.entryDurationInput.showText(editor.duration)

            val timed = state.exercises.getOrNull(selection)?.measurementType == DURATION
            fields.entrySetsLayout.isVisible = !timed
            fields.entryRepetitionsLayout.isVisible = !timed
            fields.entryWeightLayout.isVisible = !timed
            fields.entryDurationLayout.isVisible = timed
            fields.entryWeightLayout.hint = getString(R.string.workout_entry_weight, editor.weightUnit.code)
            fields.entrySetsLayout.error = errorText(editor.errors.sets)
            fields.entryRepetitionsLayout.error = errorText(editor.errors.repetitions)
            fields.entryWeightLayout.error = errorText(editor.errors.weight)
            fields.entryDurationLayout.error = errorText(editor.errors.duration)

            listOf(
                fields.entrySetsInput, fields.entryRepetitionsInput,
                fields.entryWeightInput, fields.entryDurationInput
            ).forEach { it.isEnabled = !state.saving }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = !state.saving
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.isEnabled = !state.saving
        } finally {
            renderingEditor = false
        }
    }

    private fun errorText(hasError: Boolean): String? =
        getString(R.string.workout_entry_invalid).takeIf { hasError }

    private fun EditText.showText(value: String) {
        if (text.toString() != value) {
            setText(value)
            setSelection(value.length)
        }
    }

    override fun onDestroyView() {
        entryDialog?.setOnDismissListener(null)
        entryDialog?.dismiss()
        entryDialog = null
        entryDialogBinding = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val ARG_WORKOUT_ID = "workoutId"
        private const val DURATION = "DURATION"

        private fun formatDuration(seconds: Long): String {
            val hours = seconds / 3_600
            val minutes = (seconds % 3_600) / 60
            val remaining = seconds % 60
            return listOfNotNull(
                hours.takeIf { it > 0 }?.let { "${it}h" },
                minutes.takeIf { it > 0 }?.let { "${it}m" },
                remaining.takeIf { it > 0 || (hours == 0L && minutes == 0L) }?.let { "${it}s" }
            ).joinToString(" ")
        }
    }
}
