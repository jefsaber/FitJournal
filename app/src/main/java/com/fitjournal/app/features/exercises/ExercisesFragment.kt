package com.fitjournal.app.features.exercises

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import kotlinx.coroutines.launch

class ExercisesFragment : Fragment() {
    private var _binding: FragmentExercisesBinding? = null
    private val binding get() = requireNotNull(_binding)
    private val model by viewModels<ExerciseListViewModel> {
        val repo = (requireActivity().application as FitJournalApplication).container.exercises
        ExerciseListViewModel.Factory(repo)
    }
    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup?, saved: Bundle?): View {
        _binding = FragmentExercisesBinding.inflate(inflater, parent, false)
        return binding.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val adapter = ExercisePagingAdapter()
        binding.exerciseList.layoutManager = LinearLayoutManager(requireContext())
        binding.exerciseList.adapter = adapter
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
                launch { model.exercises.collect { adapter.submitData(it) } }
                launch { adapter.loadStateFlow.collect { state ->
                    binding.emptyState.visibility = if (state.refresh is LoadState.NotLoading && adapter.itemCount == 0) View.VISIBLE else View.GONE
                } }
            }
        }
    }
    override fun onDestroyView() { binding.exerciseList.adapter = null; _binding = null; super.onDestroyView() }
}
