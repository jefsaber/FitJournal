package com.fitjournal.app.core.data.dao

import androidx.paging.PagingSource
import androidx.room.*
import com.fitjournal.app.core.data.entities.ExerciseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<ExerciseEntity>>

    @Query("""SELECT * FROM exercises
        WHERE (:search = '' OR name LIKE '%' || :search || '%' OR category LIKE '%' || :search || '%')
        AND (:category = 'ALL' OR category = :category)
        ORDER BY
          CASE WHEN :sort = 'NAME_ASC' THEN name END COLLATE NOCASE ASC,
          CASE WHEN :sort = 'NAME_DESC' THEN name END COLLATE NOCASE DESC,
          id ASC""")
    fun page(search: String, category: String, sort: String): PagingSource<Int, ExerciseEntity>

    @Query("SELECT * FROM exercises WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): ExerciseEntity?

    @Query("SELECT COUNT(*) FROM exercises")
    fun countFlow(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(exercise: ExerciseEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(exercise: ExerciseEntity): Long

    @Update suspend fun update(exercise: ExerciseEntity)
    @Delete suspend fun delete(exercise: ExerciseEntity)
}
