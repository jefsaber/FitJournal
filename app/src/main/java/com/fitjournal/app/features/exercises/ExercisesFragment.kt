package com.fitjournal.app.features.exercises

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
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
import com.fitjournal.app.core.preferences.PreferenceValues
import com.fitjournal.app.databinding.FragmentExercisesBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class ExercisesFragment : Fragment() {
    private var _binding: FragmentExercisesBinding? = null
    private val binding get() = requireNotNull(_binding)
    private var categoryOptions = emptyList<String>()
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
        binding.searchInput.doAfterTextChanged { model.setSearch(it.toString()) }

        binding.categoryFilter.setOnItemClickListener { _, _, position, _ ->
            val category = if (position == 0) ExerciseListViewModel.ALL_CATEGORIES
                else categoryOptions.getOrNull(position - 1) ?: return@setOnItemClickListener
            model.setCategory(category)
            binding.catalogueAppBar.setExpanded(false, true)
        }
        binding.catalogueSort.setAdapter(ArrayAdapter(
            requireContext(), android.R.layout.simple_dropdown_item_1line,
            listOf(getString(R.string.settings_sort_name_asc), getString(R.string.settings_sort_name_desc))
        ))
        binding.catalogueSort.setOnItemClickListener { _, _, position, _ ->
            model.setSort(if (position == 0) PreferenceValues.SORT_NAME_ASC else PreferenceValues.SORT_NAME_DESC)
        }

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
                    combine(model.categories, model.category) { options, selected -> options to selected }
                        .collect { (options, selected) ->
                            if (options != categoryOptions || binding.categoryFilter.adapter == null) {
                                categoryOptions = options
                                binding.categoryFilter.setAdapter(ArrayAdapter(
                                    requireContext(), android.R.layout.simple_dropdown_item_1line,
                                    listOf(getString(R.string.exercise_category_all)) + options
                                ))
                            }
                            binding.categoryFilter.setText(
                                if (selected == ExerciseListViewModel.ALL_CATEGORIES)
                                    getString(R.string.exercise_category_all) else selected, false
                            )
                        }
                }
                launch {
                    combine(model.sort, model.savingSort, model.sortSaveFailed) { sort, saving, failed ->
                        Triple(sort, saving, failed)
                    }.collect { (sort, saving, failed) ->
                        binding.catalogueSort.setText(
                            if (sort == PreferenceValues.SORT_NAME_DESC) getString(R.string.settings_sort_name_desc)
                            else getString(R.string.settings_sort_name_asc), false
                        )
                        binding.catalogueSortLayout.isEnabled = sort != null && !saving
                        binding.catalogueSortError.isVisible = failed
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

    override fun onDestroyView() {
        binding.exerciseList.adapter = null
        categoryOptions = emptyList()
        _binding = null
        super.onDestroyView()
    }
}
