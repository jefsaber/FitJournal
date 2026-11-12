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

    @Insert suspend fun insert(workout: WorkoutEntity): Long
    @Update suspend fun update(workout: WorkoutEntity)
    @Delete suspend fun delete(workout: WorkoutEntity)
}
