package com.fitjournal.app.features.exercises

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
import com.fitjournal.app.databinding.FragmentExerciseFormBinding
import kotlinx.coroutines.launch

class ExerciseFormFragment : Fragment() {
    private var _binding: FragmentExerciseFormBinding? = null
    private val binding get() = requireNotNull(_binding)
    private var rendering = false
    private val model by viewModels<ExerciseFormViewModel> {
        val repository = (requireActivity().application as FitJournalApplication).container.exercises
        ExerciseFormViewModel.Factory(repository)
    }

    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup?, saved: Bundle?): View {
        _binding = FragmentExerciseFormBinding.inflate(inflater, parent, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.formTitle.setText(if (model.isEditing) R.string.exercise_edit else R.string.add_exercise)
        binding.nameInput.doAfterTextChanged { if (!rendering) model.setName(it.toString()) }
        binding.categoryInput.doAfterTextChanged { if (!rendering) model.setCategory(it.toString()) }
        binding.descriptionInput.doAfterTextChanged { if (!rendering) model.setDescription(it.toString()) }
        binding.trackingGroup.setOnCheckedChangeListener { _, checkedId ->
            if (!rendering) {
                model.setMeasurementType(when (checkedId) {
                    R.id.tracking_reps_weight -> "REPS_WEIGHT"
                    R.id.tracking_duration -> "DURATION"
                    else -> ""
                })
            }
        }
        binding.saveExerciseButton.setOnClickListener { model.save() }
        binding.retryButton.setOnClickListener { model.retryLoad() }
        binding.cancelButton.setOnClickListener { findNavController().popBackStack() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.state.collect { state ->
                    if (state.phase == ExerciseFormPhase.SAVED) {
                        if (findNavController().currentDestination?.id == R.id.exerciseFormFragment) {
                            findNavController().popBackStack()
                        }
                    } else {
                        render(state)
                    }
                }
            }
        }
    }

    private fun render(state: ExerciseFormState) {
        rendering = true
        try {
            binding.nameInput.showText(state.name)
            binding.categoryInput.showText(state.category)
            binding.descriptionInput.showText(state.description)
            binding.trackingGroup.check(when (state.measurementType) {
                "REPS_WEIGHT" -> R.id.tracking_reps_weight
                "DURATION" -> R.id.tracking_duration
                else -> View.NO_ID
            })
        } finally {
            rendering = false
        }

        val editable = state.phase == ExerciseFormPhase.READY
        binding.nameInput.isEnabled = editable
        binding.categoryInput.isEnabled = editable
        binding.descriptionInput.isEnabled = editable
        binding.trackingRepsWeight.isEnabled = editable
        binding.trackingDuration.isEnabled = editable
        binding.saveExerciseButton.isEnabled = editable
        binding.cancelButton.isEnabled = state.phase != ExerciseFormPhase.SAVING
        binding.formProgress.isVisible = state.phase in setOf(ExerciseFormPhase.LOADING, ExerciseFormPhase.SAVING)
        binding.retryButton.isVisible = state.phase == ExerciseFormPhase.LOAD_ERROR
        binding.nameLayout.error = if (state.errors.name) getString(R.string.exercise_name_error) else null
        binding.categoryLayout.error = if (state.errors.category) getString(R.string.exercise_category_error) else null
        binding.trackingError.isVisible = state.errors.measurementType
        val errorMessage = when {
            state.phase == ExerciseFormPhase.LOAD_ERROR -> R.string.exercise_load_error
            state.phase == ExerciseFormPhase.NOT_FOUND -> R.string.exercise_not_found
            state.saveFailed -> R.string.exercise_save_error
            else -> null
        }
        binding.formError.isVisible = errorMessage != null
        errorMessage?.let { binding.formError.setText(it) }
    }

    private fun EditText.showText(value: String) {
        if (text.toString() != value) {
            setText(value)
            setSelection(value.length)
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
