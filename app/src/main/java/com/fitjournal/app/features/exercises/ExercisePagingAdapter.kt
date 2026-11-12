package com.fitjournal.app.features.exercises

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.fitjournal.app.core.data.entities.ExerciseEntity
import com.fitjournal.app.databinding.ItemExerciseBinding

class ExercisePagingAdapter : PagingDataAdapter<ExerciseEntity, ExercisePagingAdapter.Holder>(DIFF) {
    class Holder(val binding: ItemExerciseBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemExerciseBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = getItem(position) ?: return
        holder.binding.exerciseName.text = item.name
        holder.binding.exerciseCategory.text = item.category
        // TODO Developer 1: edit/delete actions, filters and sort UI.
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ExerciseEntity>() {
            override fun areItemsTheSame(a: ExerciseEntity, b: ExerciseEntity) = a.id == b.id
            override fun areContentsTheSame(a: ExerciseEntity, b: ExerciseEntity) = a == b
        }
    }
}
