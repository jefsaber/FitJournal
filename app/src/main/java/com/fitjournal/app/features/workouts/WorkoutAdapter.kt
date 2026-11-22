package com.fitjournal.app.features.workouts

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fitjournal.app.R
import com.fitjournal.app.core.data.entities.WorkoutEntity
import com.fitjournal.app.core.domain.WorkoutDates
import com.fitjournal.app.databinding.ItemWorkoutBinding
import java.time.LocalDate

class WorkoutAdapter(
    private val onOpen: (WorkoutEntity) -> Unit,
    private val onEdit: (WorkoutEntity) -> Unit
) : ListAdapter<WorkoutEntity, WorkoutAdapter.Holder>(DIFF) {
    class Holder(val binding: ItemWorkoutBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemWorkoutBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = getItem(position)
        val context = holder.binding.root.context
        val date = WorkoutDates.toLocalDate(item.sessionDate)
        val dateLabel = WorkoutDateFormat.relative(context, date, LocalDate.now())
        val statusLabel = context.getString(
            if (item.isCompleted) R.string.workout_status_completed else R.string.workout_status_planned
        )
        with(holder.binding) {
            workoutName.text = item.title
            workoutDate.text = dateLabel
            workoutStatus.text = statusLabel
            workoutStatus.setBackgroundResource(
                if (item.isCompleted) R.drawable.bg_badge else R.drawable.bg_badge_planned
            )
            workoutStatus.setTextColor(ContextCompat.getColor(
                context, if (item.isCompleted) R.color.primary else R.color.text_secondary
            ))
            workoutIcon.setImageResource(if (item.isCompleted) R.drawable.ic_check else R.drawable.ic_calendar)
            workoutNotes.text = item.notes
            workoutNotes.isVisible = item.notes.isNotBlank()
            root.contentDescription = context.getString(
                R.string.workout_item_description, item.title, dateLabel, statusLabel
            )
            root.setOnClickListener { onOpen(item) }
            editWorkoutButton.contentDescription = context.getString(R.string.workout_edit_action, item.title)
            editWorkoutButton.setOnClickListener { onEdit(item) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<WorkoutEntity>() {
            override fun areItemsTheSame(a: WorkoutEntity, b: WorkoutEntity) = a.id == b.id
            override fun areContentsTheSame(a: WorkoutEntity, b: WorkoutEntity) = a == b
        }
    }
}
