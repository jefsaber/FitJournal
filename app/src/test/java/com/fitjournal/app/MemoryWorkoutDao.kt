package com.fitjournal.app

import com.fitjournal.app.core.data.dao.WorkoutDao
import com.fitjournal.app.core.data.entities.WorkoutEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory WorkoutDao mirroring the Room queries, so workout logic runs as plain JUnit. */
class MemoryWorkoutDao : WorkoutDao {
    val rows = MutableStateFlow<List<WorkoutEntity>>(emptyList())
    var inserts = 0
    var updates = 0
    var deletes = 0
    var failWrites = false

    override fun observeAll(): Flow<List<WorkoutEntity>> =
        rows.map { list -> list.sortedWith(compareByDescending<WorkoutEntity> { it.sessionDate }.thenByDescending { it.id }) }

    override fun observeById(id: Long): Flow<WorkoutEntity?> = rows.map { list -> list.find { it.id == id } }

    override suspend fun byId(id: Long) = rows.value.find { it.id == id }

    override fun observeFiltered(
        search: String,
        status: String,
        fromMillis: Long,
        toMillis: Long,
        ascending: Boolean
    ): Flow<List<WorkoutEntity>> = rows.map { list ->
        list.filter {
            (search.isEmpty() || it.title.contains(search, ignoreCase = true) ||
                it.notes.contains(search, ignoreCase = true)) &&
                (status == "ALL" || (status == "COMPLETED") == it.isCompleted) &&
                it.sessionDate >= fromMillis && it.sessionDate < toMillis
        }.sortedWith(
            if (ascending) compareBy<WorkoutEntity> { it.sessionDate }.thenByDescending { it.id }
            else compareByDescending<WorkoutEntity> { it.sessionDate }.thenByDescending { it.id }
        )
    }

    override fun countFlow(): Flow<Int> = rows.map { it.size }

    override suspend fun insert(workout: WorkoutEntity): Long {
        check(!failWrites) { "Simulated database failure" }
        val id = (rows.value.maxOfOrNull { it.id } ?: 0L) + 1L
        rows.value += workout.copy(id = id)
        inserts++
        return id
    }

    override suspend fun update(workout: WorkoutEntity) {
        check(!failWrites) { "Simulated database failure" }
        rows.value = rows.value.map { if (it.id == workout.id) workout else it }
        updates++
    }

    override suspend fun delete(workout: WorkoutEntity) {
        check(!failWrites) { "Simulated database failure" }
        rows.value = rows.value.filterNot { it.id == workout.id }
        deletes++
    }
}
