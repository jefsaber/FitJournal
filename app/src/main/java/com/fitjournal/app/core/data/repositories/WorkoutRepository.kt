package com.fitjournal.app.core.data.repositories

import com.fitjournal.app.core.data.dao.WorkoutDao
import com.fitjournal.app.core.data.entities.WorkoutEntity

class WorkoutRepository(private val dao: WorkoutDao) {
    val history = dao.observeAll()
    fun byId(id: Long) = dao.observeById(id)
    suspend fun create(workout: WorkoutEntity) = dao.insert(workout)
    suspend fun update(workout: WorkoutEntity) = dao.update(workout)
    suspend fun delete(workout: WorkoutEntity) = dao.delete(workout)
}
