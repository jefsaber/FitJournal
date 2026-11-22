package com.fitjournal.app.core.data.dao

import androidx.room.*
import com.fitjournal.app.core.data.entities.WorkoutEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM workouts ORDER BY sessionDate DESC, id DESC")
    fun observeAll(): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM workouts WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<WorkoutEntity?>

    @Query("SELECT * FROM workouts WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): WorkoutEntity?

    /** History with search, status and a `[fromMillis, toMillis)` date window. */
    @Query("""SELECT * FROM workouts
        WHERE (:search = '' OR title LIKE '%' || :search || '%' OR notes LIKE '%' || :search || '%')
        AND (:status = 'ALL'
            OR (:status = 'COMPLETED' AND isCompleted = 1)
            OR (:status = 'PLANNED' AND isCompleted = 0))
        AND sessionDate >= :fromMillis AND sessionDate < :toMillis
        ORDER BY
          CASE WHEN :ascending = 1 THEN sessionDate END ASC,
          CASE WHEN :ascending = 0 THEN sessionDate END DESC,
          id DESC""")
    fun observeFiltered(
        search: String,
        status: String,
        fromMillis: Long,
        toMillis: Long,
        ascending: Boolean
    ): Flow<List<WorkoutEntity>>

    @Query("SELECT COUNT(*) FROM workouts")
    fun countFlow(): Flow<Int>

    @Insert suspend fun insert(workout: WorkoutEntity): Long
    @Update suspend fun update(workout: WorkoutEntity)
    @Delete suspend fun delete(workout: WorkoutEntity)
}
