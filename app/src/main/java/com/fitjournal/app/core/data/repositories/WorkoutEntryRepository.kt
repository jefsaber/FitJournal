package com.fitjournal.app.core.data.repositories

import com.fitjournal.app.core.data.dao.WorkoutEntryDao
import com.fitjournal.app.core.data.entities.WorkoutEntryEntity

class WorkoutEntryRepository(private val dao: WorkoutEntryDao) {
    fun byWorkout(id: Long) = dao.observeByWorkout(id)
    fun detailedByWorkout(id: Long) = dao.observeDetailedByWorkout(id)
    val all = dao.observeAll()
    val allDetailed = dao.observeAllDetailed()
    suspend fun create(entry: WorkoutEntryEntity) = dao.insert(entry)
    suspend fun update(entry: WorkoutEntryEntity) = dao.update(entry)
    suspend fun delete(entry: WorkoutEntryEntity) = dao.delete(entry)
}
