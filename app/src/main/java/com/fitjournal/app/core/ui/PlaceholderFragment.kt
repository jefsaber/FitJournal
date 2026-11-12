package com.fitjournal.app.core.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.fitjournal.app.databinding.FragmentPlaceholderBinding

/** Thin placeholder for module owners; replace with real stateful layouts/ViewModels. */
abstract class PlaceholderFragment : Fragment() {
    abstract val title: String
    abstract val details: String
    private var _binding: FragmentPlaceholderBinding? = null
    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup?, saved: Bundle?): View {
        _binding = FragmentPlaceholderBinding.inflate(inflater, parent, false)
        return requireNotNull(_binding).root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        requireNotNull(_binding).placeholderTitle.text = title
        requireNotNull(_binding).placeholderDescription.text = details
    }
    override fun onDestroyView() { _binding = null; super.onDestroyView() }
}
