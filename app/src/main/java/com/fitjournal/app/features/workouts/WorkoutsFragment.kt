package com.fitjournal.app.features.workouts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.fitjournal.app.FitJournalApplication
import com.fitjournal.app.R
import com.fitjournal.app.core.domain.WorkoutHistoryFilter
import com.fitjournal.app.core.domain.WorkoutPeriodFilter
import com.fitjournal.app.core.domain.WorkoutStatusFilter
import com.fitjournal.app.databinding.FragmentWorkoutsBinding
import kotlinx.coroutines.launch

class WorkoutsFragment : Fragment() {
    private var _binding: FragmentWorkoutsBinding? = null
    private val binding get() = requireNotNull(_binding)
    private var rendering = false
    private val model by viewModels<WorkoutsViewModel> {
        WorkoutsViewModel.Factory((requireActivity().application as FitJournalApplication).container.workouts)
    }

    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup?, saved: Bundle?): View {
        _binding = FragmentWorkoutsBinding.inflate(inflater, parent, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val adapter = WorkoutAdapter(
            onOpen = { item ->
                findNavController().navigate(
                    R.id.workoutDetailsFragment, bundleOf(WorkoutFormViewModel.ARG_WORKOUT_ID to item.id)
                )
            },
            onEdit = { item -> openForm(item.id) }
        )
        binding.workoutList.layoutManager = LinearLayoutManager(requireContext())
        binding.workoutList.adapter = adapter
        binding.addWorkoutButton.setOnClickListener { openForm(0L) }
        binding.clearFiltersButton.setOnClickListener { model.clearFilters() }

        renderFilter(model.filter.value)
        binding.workoutSearchInput.doAfterTextChanged { if (!rendering) model.setSearch(it.toString()) }
        binding.workoutSearchInput.setOnFocusChangeListener { _, focused ->
            if (focused) binding.workoutsAppBar.setExpanded(false, true)
        }
        binding.statusChips.setOnCheckedStateChangeListener { _, checked ->
            if (rendering) return@setOnCheckedStateChangeListener
            model.setStatus(when (checked.firstOrNull()) {
                R.id.status_planned -> WorkoutStatusFilter.PLANNED
                R.id.status_completed -> WorkoutStatusFilter.COMPLETED
                else -> WorkoutStatusFilter.ALL
            })
        }
        binding.periodChips.setOnCheckedStateChangeListener { _, checked ->
            if (rendering) return@setOnCheckedStateChangeListener
            model.setPeriod(when (checked.firstOrNull()) {
                R.id.period_upcoming -> WorkoutPeriodFilter.UPCOMING
                R.id.period_last_7 -> WorkoutPeriodFilter.LAST_7_DAYS
                R.id.period_last_30 -> WorkoutPeriodFilter.LAST_30_DAYS
                else -> WorkoutPeriodFilter.ANY_TIME
            })
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.state.collect { state ->
                    adapter.submitList(state.workouts)
                    renderFilter(state.filter)
                    binding.workoutCount.text = when {
                        state.loading -> getString(R.string.workouts_history_caption)
                        state.filter.isDefault -> resources.getQuantityString(
                            R.plurals.workout_count, state.totalCount, state.totalCount
                        )
                        else -> getString(R.string.workout_count_filtered, state.workouts.size, state.totalCount)
                    }
                    binding.workoutsProgress.isVisible = state.loading
                    binding.workoutsEmpty.isVisible = state.showNoWorkouts
                    binding.workoutsNoMatches.isVisible = state.showNoMatches
                }
            }
        }
    }

    private fun openForm(workoutId: Long) {
        findNavController().navigate(
            R.id.workoutFormFragment, bundleOf(WorkoutFormViewModel.ARG_WORKOUT_ID to workoutId)
        )
    }

    private fun renderFilter(filter: WorkoutHistoryFilter) {
        rendering = true
        try {
            if (binding.workoutSearchInput.text.toString() != filter.search) {
                binding.workoutSearchInput.setText(filter.search)
            }
            binding.statusChips.check(when (filter.status) {
                WorkoutStatusFilter.ALL -> R.id.status_all
                WorkoutStatusFilter.PLANNED -> R.id.status_planned
                WorkoutStatusFilter.COMPLETED -> R.id.status_completed
            })
            binding.periodChips.check(when (filter.period) {
                WorkoutPeriodFilter.ANY_TIME -> R.id.period_any
                WorkoutPeriodFilter.UPCOMING -> R.id.period_upcoming
                WorkoutPeriodFilter.LAST_7_DAYS -> R.id.period_last_7
                WorkoutPeriodFilter.LAST_30_DAYS -> R.id.period_last_30
            })
        } finally {
            rendering = false
        }
    }

    override fun onDestroyView() { binding.workoutList.adapter = null; _binding = null; super.onDestroyView() }
}
