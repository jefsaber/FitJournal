package com.fitjournal.app.features.workouts

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fitjournal.app.core.data.entities.WorkoutEntity
import com.fitjournal.app.databinding.ItemWorkoutBinding
import java.text.DateFormat
import java.util.Date

class WorkoutAdapter(private val onClick: (WorkoutEntity) -> Unit) : ListAdapter<WorkoutEntity, WorkoutAdapter.Holder>(DIFF) {
    class Holder(val binding: ItemWorkoutBinding) : RecyclerView.ViewHolder(binding.root)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemWorkoutBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = getItem(position)
        holder.binding.workoutName.text = item.title
        holder.binding.workoutDate.text = DateFormat.getDateInstance().format(Date(item.sessionDate))
        holder.binding.root.setOnClickListener { onClick(item) }
    }
    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<WorkoutEntity>() {
            override fun areItemsTheSame(a: WorkoutEntity, b: WorkoutEntity) = a.id == b.id
            override fun areContentsTheSame(a: WorkoutEntity, b: WorkoutEntity) = a == b
        }
    }
}
