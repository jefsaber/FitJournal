package com.fitjournal.app.core.data.dao

import androidx.room.*
import com.fitjournal.app.core.data.entities.WorkoutEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutEntryDao {
    @Query("SELECT * FROM workout_entries WHERE workoutId = :workoutId ORDER BY id ASC")
    fun observeByWorkout(workoutId: Long): Flow<List<WorkoutEntryEntity>>

    @Query("SELECT * FROM workout_entries ORDER BY id DESC")
    fun observeAll(): Flow<List<WorkoutEntryEntity>>

    @Insert suspend fun insert(entry: WorkoutEntryEntity): Long
    @Update suspend fun update(entry: WorkoutEntryEntity)
    @Delete suspend fun delete(entry: WorkoutEntryEntity)
}
