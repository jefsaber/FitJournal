package com.fitjournal.app.features.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.fitjournal.app.FitJournalApplication
import com.fitjournal.app.R
import com.fitjournal.app.core.preferences.PreferenceValues
import com.fitjournal.app.databinding.FragmentSettingsBinding
import com.google.android.material.button.MaterialButtonToggleGroup
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = requireNotNull(_binding)
    private val model by viewModels<SettingsViewModel> {
        SettingsViewModel.Factory((requireActivity().application as FitJournalApplication).container.settings)
    }

    private val themeButtons = mapOf(
        R.id.theme_system to ThemeChoice.SYSTEM,
        R.id.theme_light to ThemeChoice.LIGHT,
        R.id.theme_dark to ThemeChoice.DARK
    )
    private val unitButtons = mapOf(
        R.id.unit_kg to PreferenceValues.WEIGHT_KG,
        R.id.unit_lb to PreferenceValues.WEIGHT_LB
    )
    private val sortButtons = mapOf(
        R.id.sort_name_asc to PreferenceValues.SORT_NAME_ASC,
        R.id.sort_name_desc to PreferenceValues.SORT_NAME_DESC
    )

    /** Set while the UI mirrors saved state, so programmatic checks are not written back. */
    private var rendering = false

    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup?, saved: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, parent, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.themeToggle.onSelected(themeButtons, model::selectTheme)
        binding.unitToggle.onSelected(unitButtons, model::selectWeightUnit)
        binding.sortToggle.onSelected(sortButtons, model::selectExerciseSort)
        binding.settingsError.setOnClickListener { model.dismissError() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.state.collect(::render)
            }
        }
    }

    private fun render(state: SettingsUiState) {
        binding.settingsProgress.isVisible = state.loading
        binding.settingsContent.isVisible = !state.loading
        binding.settingsError.isVisible = state.saveFailed
        if (state.loading) return
        rendering = true
        binding.themeToggle.checkValue(themeButtons, state.theme)
        binding.unitToggle.checkValue(unitButtons, state.weightUnit)
        binding.sortToggle.checkValue(sortButtons, state.exerciseSort)
        rendering = false
    }

    private fun <T> MaterialButtonToggleGroup.onSelected(buttons: Map<Int, T>, select: (T) -> Unit) {
        addOnButtonCheckedListener { _, buttonId, isChecked ->
            if (isChecked && !rendering) buttons[buttonId]?.let(select)
        }
    }

    private fun <T> MaterialButtonToggleGroup.checkValue(buttons: Map<Int, T>, value: T) {
        val id = buttons.entries.firstOrNull { it.value == value }?.key ?: return
        if (checkedButtonId != id) check(id)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
