package com.fitjournal.app.features.progress

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
import com.fitjournal.app.FitJournalApplication
import com.fitjournal.app.R
import com.fitjournal.app.core.data.entities.ExerciseEntity
import com.fitjournal.app.core.data.entities.WorkoutEntryEntity
import com.fitjournal.app.core.data.entities.WorkoutEntity
import com.fitjournal.app.core.domain.ProgressCalculator
import com.fitjournal.app.databinding.FragmentProgressBinding
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Locale

class ProgressFragment : Fragment() {
    private var _binding: FragmentProgressBinding? = null
    private val binding get() = requireNotNull(_binding)

    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup?, state: Bundle?): View {
        _binding = FragmentProgressBinding.inflate(inflater, parent, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val container = (requireActivity().application as FitJournalApplication).container
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(container.workouts.history, container.entries.all, container.exercises.all) {
                    workouts: List<WorkoutEntity>, entries: List<WorkoutEntryEntity>, exercises: List<ExerciseEntity> ->
                    Triple(workouts, entries, exercises)
                }.collect { (workouts, allEntries, exercises) ->
                    val completed = workouts.filter { it.isCompleted }
                    val ids = completed.map { it.id }.toSet()
                    val entries = allEntries.filter { it.workoutId in ids }
                    binding.progressWorkoutCount.text = completed.size.toString()
                    val volume = entries.sumOf { ProgressCalculator.volumeKg(it.sets, it.repetitions, it.weightKg) }
                    binding.progressVolume.text = String.format(Locale.getDefault(), "%.1f kg-reps", volume)
                    binding.progressRecords.removeAllViews()
                    val records: List<Pair<String, String>> = entries.groupBy { it.exerciseId }.mapNotNull { (id, logged) ->
                        val exercise = exercises.firstOrNull { exercise -> exercise.id == id } ?: return@mapNotNull null
                        val value = if (exercise.measurementType == "DURATION") {
                            (ProgressCalculator.personalRecord(logged.map { it.durationSeconds.toDouble() }) ?: 0.0).toLong().toString() + " sec"
                        } else {
                            String.format(Locale.getDefault(), "%.1f kg", ProgressCalculator.personalRecord(logged.map { it.weightKg }) ?: 0.0)
                        }
                        exercise.name to value
                    }
                    binding.progressEmpty.isVisible = records.isEmpty()
                    records.sortedBy { it.first.lowercase() }.forEach { record ->
                        val row = TextView(requireContext()).apply {
                            text = record.first + " · " + record.second
                            setTextAppearance(R.style.TextAppearance_FitJournal_Body)
                            setPadding(0, 12, 0, 12)
                        }
                        binding.progressRecords.addView(row)
                    }
                }
            }
        }
    }

    override fun onDestroyView() { _binding = null; super.onDestroyView() }
}
