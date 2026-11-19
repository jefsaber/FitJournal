package com.fitjournal.app.core.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.fitjournal.app.R
import com.fitjournal.app.databinding.FragmentPlaceholderBinding

/** Shared presentation for features still being implemented by their module owners. */
abstract class PlaceholderFragment : Fragment() {
    abstract val title: String
    abstract val details: String
    open val iconRes: Int = R.drawable.ic_dumbbell
    private var _binding: FragmentPlaceholderBinding? = null

    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup?, saved: Bundle?): View {
        _binding = FragmentPlaceholderBinding.inflate(inflater, parent, false)
        return requireNotNull(_binding).root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = requireNotNull(_binding)
        binding.placeholderTitle.text = title
        binding.placeholderDescription.text = details
        binding.placeholderIcon.setImageResource(iconRes)
        binding.placeholderAction.setOnClickListener {
            val navigation = findNavController()
            if (!navigation.popBackStack(R.id.exercisesFragment, false)) {
                navigation.navigate(R.id.exercisesFragment)
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
