package com.fitjournal.app.features.progress

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.fitjournal.app.FitJournalApplication
import com.fitjournal.app.R
import com.fitjournal.app.core.domain.WeightConverter
import com.fitjournal.app.core.domain.WeightUnit
import com.fitjournal.app.databinding.FragmentProgressBinding
import java.text.DateFormat
import java.text.DecimalFormat
import java.util.Date
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class ProgressFragment : Fragment() {
    private var _binding: FragmentProgressBinding? = null
    private val binding get() = requireNotNull(_binding)
    private val model by viewModels<ProgressViewModel> {
        val container = (requireActivity().application as FitJournalApplication).container
        ProgressViewModel.Factory(container.workouts, container.entries, container.settings)
    }

    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup?, state: Bundle?): View {
        _binding = FragmentProgressBinding.inflate(inflater, parent, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.progressRetry.setOnClickListener { model.retry() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.state.collect(::render)
            }
        }
    }

    private fun render(state: ProgressState) {
        binding.progressLoading.isVisible = state.loading
        binding.progressLoadError.isVisible = state.loadFailed
        binding.progressRetry.isVisible = state.loadFailed
        binding.progressContent.isVisible = !state.loading && !state.loadFailed
        if (state.loading || state.loadFailed) return

        binding.progressWorkoutCount.text = state.completedWorkouts.toString()
        val displayedVolume = WeightConverter.fromKilograms(state.totalVolumeKg, state.weightUnit)
        binding.progressVolume.text = getString(
            R.string.progress_volume_value, number(displayedVolume), state.weightUnit.code
        )
        renderVolumeChart(state)
        renderRecords(state)
    }

    private fun renderVolumeChart(state: ProgressState) {
        val points = state.recentVolume.filter { it.volumeKg > 0.0 }
        binding.progressRecentEmpty.isVisible = points.isEmpty()
        binding.progressRecentVolume.removeAllViews()
        val maximum = points.maxOfOrNull { it.volumeKg } ?: 1.0
        points.forEach { point ->
            val row = layoutInflater.inflate(R.layout.item_progress_volume, binding.progressRecentVolume, false)
            val date = DateFormat.getDateInstance(DateFormat.SHORT).format(Date(point.sessionDate))
            row.findViewById<TextView>(R.id.progress_volume_name).text = "${point.title} · $date"
            val displayed = WeightConverter.fromKilograms(point.volumeKg, state.weightUnit)
            row.findViewById<TextView>(R.id.progress_volume_value).text = getString(
                R.string.progress_volume_value, number(displayed), state.weightUnit.code
            )
            row.findViewById<ProgressBar>(R.id.progress_volume_bar).progress =
                ((point.volumeKg / maximum) * 100).roundToInt().coerceIn(1, 100)
            binding.progressRecentVolume.addView(row)
        }
    }

    private fun renderRecords(state: ProgressState) {
        binding.progressEmpty.isVisible = state.records.isEmpty()
        binding.progressRecords.removeAllViews()
        state.records.forEach { record ->
            val row = layoutInflater.inflate(R.layout.item_progress_record, binding.progressRecords, false)
            row.findViewById<TextView>(R.id.progress_record_name).text = record.exerciseName
            row.findViewById<TextView>(R.id.progress_record_value).text = if (record.measurementType == DURATION) {
                getString(R.string.progress_record_duration, formatDuration(record.canonicalValue.toLong()))
            } else {
                val displayed = WeightConverter.fromKilograms(record.canonicalValue, state.weightUnit)
                getString(R.string.progress_record_strength, number(displayed), state.weightUnit.code)
            }
            binding.progressRecords.addView(row)
        }
    }

    private fun number(value: Double): String = DecimalFormat("0.##").format(value)

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val DURATION = "DURATION"

        fun formatDuration(seconds: Long): String {
            val hours = seconds / 3_600
            val minutes = (seconds % 3_600) / 60
            val remaining = seconds % 60
            return listOfNotNull(
                hours.takeIf { it > 0 }?.let { "${it}h" },
                minutes.takeIf { it > 0 }?.let { "${it}m" },
                remaining.takeIf { it > 0 || (hours == 0L && minutes == 0L) }?.let { "${it}s" }
            ).joinToString(" ")
        }
    }
}
