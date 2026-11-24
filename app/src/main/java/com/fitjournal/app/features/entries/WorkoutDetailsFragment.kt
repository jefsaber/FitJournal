package com.fitjournal.app.features.entries

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.fitjournal.app.FitJournalApplication
import com.fitjournal.app.R
import com.fitjournal.app.databinding.FragmentWorkoutDetailsBinding
import kotlinx.coroutines.launch

class WorkoutDetailsFragment : Fragment() {
    private var _binding: FragmentWorkoutDetailsBinding? = null
    private val binding get() = requireNotNull(_binding)

    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentWorkoutDetailsBinding.inflate(inflater, parent, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val workoutId = requireArguments().getLong(ARG_WORKOUT_ID)
        val container = (requireActivity().application as FitJournalApplication).container
        binding.workoutDetailsRetry.setOnClickListener { findNavController().popBackStack() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                container.workouts.byId(workoutId).collect { workout ->
                    binding.workoutDetailsProgress.isVisible = false
                    binding.workoutDetailsError.isVisible = workout == null
                    binding.workoutDetailsTitle.text = workout?.title.orEmpty()
                    binding.workoutDetailsMeta.text = workout?.let {
                        val status = getString(if (it.isCompleted) R.string.workout_status_completed else R.string.workout_status_planned)
                        status + " · " + java.text.DateFormat.getDateInstance().format(java.util.Date(it.sessionDate))
                    }.orEmpty()
                    binding.workoutDetailsNotes.text = workout?.notes.orEmpty()
                    binding.workoutDetailsNotes.isVisible = workout?.notes?.isNotBlank() == true
                    if (workout != null) {
                        container.entries.byWorkout(workoutId).collect { entries ->
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
    }

    override fun onDestroyView() { _binding = null; super.onDestroyView() }

    companion object { const val ARG_WORKOUT_ID = "workoutId" }
}
