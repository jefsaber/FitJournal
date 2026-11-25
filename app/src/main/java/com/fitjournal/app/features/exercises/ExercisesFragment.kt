package com.fitjournal.app.features.exercises

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import com.fitjournal.app.FitJournalApplication
import com.fitjournal.app.R
import com.fitjournal.app.databinding.FragmentExercisesBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ExercisesFragment : Fragment() {
    private var _binding: FragmentExercisesBinding? = null
    private val binding get() = requireNotNull(_binding)
    private val model by viewModels<ExerciseListViewModel> {
        val container = (requireActivity().application as FitJournalApplication).container
        ExerciseListViewModel.Factory(container.exercises, container.settings)
    }
    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup?, saved: Bundle?): View {
        _binding = FragmentExercisesBinding.inflate(inflater, parent, false)
        return binding.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val adapter = ExercisePagingAdapter { exercise ->
            findNavController().navigate(
                R.id.exerciseFormFragment,
                bundleOf(ExerciseFormViewModel.ARG_EXERCISE_ID to exercise.id)
            )
        }
        binding.exerciseList.layoutManager = LinearLayoutManager(requireContext())
        binding.exerciseList.adapter = adapter
        binding.retryCatalogueButton.setOnClickListener { adapter.retry() }
        binding.searchInput.setText(model.search.value)
        binding.searchInput.setOnFocusChangeListener { _, focused ->
            if (focused) binding.catalogueAppBar.setExpanded(false, true)
        }
        binding.addExerciseButton.setOnClickListener { findNavController().navigate(R.id.exerciseFormFragment) }
        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                model.search.value = s?.toString().orEmpty()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { model.exercises.collectLatest { adapter.submitData(it) } }
                launch {
                    model.count.collect { count ->
                        binding.exerciseCount.text = resources.getQuantityString(
                            R.plurals.exercise_library_count, count, count
                        )
                    }
                }
                launch {
                    adapter.loadStateFlow.collect { state ->
                        val empty = adapter.itemCount == 0
                        binding.emptyState.isVisible = state.refresh is LoadState.NotLoading && empty
                        binding.catalogueProgress.isVisible = state.refresh is LoadState.Loading && empty
                        binding.catalogueError.isVisible = state.refresh is LoadState.Error && empty
                    }
                }
            }
        }
    }
    override fun onDestroyView() { binding.exerciseList.adapter = null; _binding = null; super.onDestroyView() }
}
