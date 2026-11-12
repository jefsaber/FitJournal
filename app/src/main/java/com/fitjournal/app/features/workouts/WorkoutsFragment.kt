package com.fitjournal.app.features.workouts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.fitjournal.app.FitJournalApplication
import com.fitjournal.app.R
import com.fitjournal.app.databinding.FragmentWorkoutsBinding
import kotlinx.coroutines.launch

class WorkoutsFragment : Fragment() {
    private var _binding: FragmentWorkoutsBinding? = null
    private val binding get() = requireNotNull(_binding)
    private val model by viewModels<WorkoutsViewModel> {
        WorkoutsViewModel.Factory((requireActivity().application as FitJournalApplication).container.workouts)
    }
    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup?, saved: Bundle?): View {
        _binding = FragmentWorkoutsBinding.inflate(inflater, parent, false)
        return binding.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val adapter = WorkoutAdapter { item ->
            findNavController().navigate(R.id.workoutDetailsFragment, bundleOf("workoutId" to item.id))
        }
        binding.workoutList.layoutManager = LinearLayoutManager(requireContext())
        binding.workoutList.adapter = adapter
        binding.addWorkoutButton.setOnClickListener { findNavController().navigate(R.id.workoutFormFragment) }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.history.collect { list ->
                    adapter.submitList(list)
                    binding.workoutsEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }
    override fun onDestroyView() { binding.workoutList.adapter = null; _binding = null; super.onDestroyView() }
}
