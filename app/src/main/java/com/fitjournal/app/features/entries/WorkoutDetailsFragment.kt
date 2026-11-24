package com.fitjournal.app.features.entries

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.fitjournal.app.FitJournalApplication
import com.fitjournal.app.R
import com.fitjournal.app.core.data.entities.ExerciseEntity
import com.fitjournal.app.core.data.entities.WorkoutEntryEntity
import com.fitjournal.app.databinding.FragmentWorkoutDetailsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class WorkoutDetailsFragment : Fragment() {
    private var _binding: FragmentWorkoutDetailsBinding? = null
    private val binding get() = requireNotNull(_binding)
    private val container get() = (requireActivity().application as FitJournalApplication).container
    private var workoutId: Long = 0

    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentWorkoutDetailsBinding.inflate(inflater, parent, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        workoutId = requireArguments().getLong(ARG_WORKOUT_ID)
        binding.workoutDetailsRetry.setOnClickListener { findNavController().popBackStack() }
        binding.addEntryButton.setOnClickListener { showEntryForm() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                container.workouts.byId(workoutId).collect { workout ->
                    binding.workoutDetailsProgress.isVisible = false
                    binding.workoutDetailsError.isVisible = workout == null
                    binding.addEntryButton.isVisible = workout != null
                    binding.workoutDetailsTitle.text = workout?.title.orEmpty()
                    binding.workoutDetailsMeta.text = workout?.let {
                        val status = getString(if (it.isCompleted) R.string.workout_status_completed else R.string.workout_status_planned)
                        status + " · " + java.text.DateFormat.getDateInstance().format(java.util.Date(it.sessionDate))
                    }.orEmpty()
                    binding.workoutDetailsNotes.text = workout?.notes.orEmpty()
                    binding.workoutDetailsNotes.isVisible = workout?.notes?.isNotBlank() == true
                    if (workout != null) container.entries.byWorkout(workoutId).collect { entries ->
                        binding.workoutDetailsEntries.removeAllViews()
                        binding.workoutDetailsEmpty.isVisible = entries.isEmpty()
                        entries.forEach { entry ->
                            val exercise = container.exercises.find(entry.exerciseId)
                            val row = layoutInflater.inflate(R.layout.item_workout_entry, binding.workoutDetailsEntries, false)
                            row.findViewById<TextView>(R.id.entry_name).text = exercise?.name ?: getString(R.string.exercise_not_found)
                            row.findViewById<TextView>(R.id.entry_metrics).text = if (exercise?.measurementType == "DURATION") {
                                entry.durationSeconds.toString() + " sec"
                            } else {
                                entry.sets.toString() + " sets · " + entry.repetitions.toString() +
                                    " reps · " + entry.weightKg.toString() + " kg"
                            }
                            binding.workoutDetailsEntries.addView(row)
                        }
                    }
                }
            }
        }
    }

    private fun showEntryForm() {
        viewLifecycleOwner.lifecycleScope.launch {
            val exercises = container.exercises.all.first()
            if (exercises.isEmpty()) {
                Toast.makeText(requireContext(), R.string.workout_entry_no_exercises, Toast.LENGTH_LONG).show()
                return@launch
            }
            val context = requireContext()
            val content = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(48, 8, 48, 0)
            }
            val exercisePicker = Spinner(context)
            exercisePicker.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, exercises.map { it.name })
            content.addView(exercisePicker)
            val sets = input(content, R.string.workout_entry_sets)
            val reps = input(content, R.string.workout_entry_repetitions)
            val weight = input(content, R.string.workout_entry_weight)
            val duration = input(content, R.string.workout_entry_duration)
            fun updateFields() {
                val timed = exercises[exercisePicker.selectedItemPosition].measurementType == "DURATION"
                sets.visibility = if (timed) View.GONE else View.VISIBLE
                reps.visibility = if (timed) View.GONE else View.VISIBLE
                weight.visibility = if (timed) View.GONE else View.VISIBLE
                duration.visibility = if (timed) View.VISIBLE else View.GONE
            }
            exercisePicker.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) = updateFields()
            }
            updateFields()
            val dialog = MaterialAlertDialogBuilder(context)
                .setTitle(R.string.workout_entry_choose_exercise)
                .setView(content)
                .setNegativeButton(R.string.exercise_cancel, null)
                .setPositiveButton(R.string.workout_entry_save, null)
                .create()
            dialog.setOnShowListener {
                dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val exercise = exercises[exercisePicker.selectedItemPosition]
                    val entry = if (exercise.measurementType == "DURATION") {
                        val seconds = duration.text?.toString()?.toLongOrNull()
                        if (seconds == null || seconds <= 0) { duration.error = getString(R.string.workout_entry_invalid); return@setOnClickListener }
                        WorkoutEntryEntity(workoutId = workoutId, exerciseId = exercise.id, durationSeconds = seconds)
                    } else {
                        val setCount = sets.text?.toString()?.toIntOrNull()
                        val repetitionCount = reps.text?.toString()?.toIntOrNull()
                        val kilograms = weight.text?.toString()?.toDoubleOrNull()
                        if (setCount == null || setCount <= 0) { sets.error = getString(R.string.workout_entry_invalid); return@setOnClickListener }
                        if (repetitionCount == null || repetitionCount <= 0) { reps.error = getString(R.string.workout_entry_invalid); return@setOnClickListener }
                        if (kilograms == null || kilograms <= 0) { weight.error = getString(R.string.workout_entry_invalid); return@setOnClickListener }
                        WorkoutEntryEntity(workoutId = workoutId, exerciseId = exercise.id, sets = setCount, repetitions = repetitionCount, weightKg = kilograms)
                    }
                    viewLifecycleOwner.lifecycleScope.launch {
                        runCatching { container.entries.create(entry) }
                            .onSuccess { dialog.dismiss() }
                            .onFailure { Toast.makeText(requireContext(), R.string.workout_entry_save_error, Toast.LENGTH_LONG).show() }
                    }
                }
            }
            dialog.show()
        }
    }

    private fun input(parent: LinearLayout, label: Int): TextInputEditText {
        val wrapper = TextInputLayout(requireContext()).apply { hint = getString(label) }
        val field = TextInputEditText(requireContext()).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        wrapper.addView(field)
        parent.addView(wrapper)
        return field
    }

    override fun onDestroyView() { _binding = null; super.onDestroyView() }

    companion object { const val ARG_WORKOUT_ID = "workoutId" }
}
