package com.fitjournal.app.core.data.repositories

import androidx.paging.Pager
import androidx.paging.PagingConfig
import com.fitjournal.app.core.data.dao.ExerciseDao
import com.fitjournal.app.core.data.entities.ExerciseEntity
import kotlinx.coroutines.flow.Flow

class ExerciseRepository(private val dao: ExerciseDao) {
    val all: Flow<List<ExerciseEntity>> = dao.observeAll()
    val count: Flow<Int> = dao.countFlow()

    fun paged(search: String = "", category: String = "ALL", sort: String = "NAME_ASC") =
        Pager(PagingConfig(pageSize = 20, enablePlaceholders = false)) {
            dao.page(search.trim(), category, sort)
        }.flow

    suspend fun find(id: Long) = dao.byId(id)
    suspend fun create(exercise: ExerciseEntity) = dao.insert(exercise)
    suspend fun update(exercise: ExerciseEntity) = dao.update(exercise)
    suspend fun delete(exercise: ExerciseEntity) = dao.delete(exercise)
}
