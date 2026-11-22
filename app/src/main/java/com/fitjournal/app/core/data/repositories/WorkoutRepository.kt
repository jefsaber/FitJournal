package com.fitjournal.app.core.data.repositories

import com.fitjournal.app.core.data.dao.WorkoutDao
import com.fitjournal.app.core.data.entities.WorkoutEntity
import com.fitjournal.app.core.domain.WorkoutHistoryFilter
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

class WorkoutRepository(private val dao: WorkoutDao) {
    val history = dao.observeAll()
    val count: Flow<Int> = dao.countFlow()
    fun byId(id: Long) = dao.observeById(id)

    fun filtered(
        filter: WorkoutHistoryFilter,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): Flow<List<WorkoutEntity>> {
        val (from, to) = filter.dateRange(today, zone)
        return dao.observeFiltered(filter.search.trim(), filter.status.name, from, to, filter.ascending)
    }

    suspend fun find(id: Long) = dao.byId(id)
    suspend fun create(workout: WorkoutEntity) = dao.insert(workout)
    suspend fun update(workout: WorkoutEntity) = dao.update(workout)

    /** Deleting a workout cascades to its entries (see WorkoutEntryEntity foreign key). */
    suspend fun delete(workout: WorkoutEntity) = dao.delete(workout)
}
