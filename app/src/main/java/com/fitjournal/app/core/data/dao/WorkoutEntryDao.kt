package com.fitjournal.app.core.data.dao

import androidx.room.*
import com.fitjournal.app.core.data.entities.WorkoutEntryEntity
import com.fitjournal.app.core.data.models.LoggedWorkoutEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutEntryDao {
    @Query("SELECT * FROM workout_entries WHERE workoutId = :workoutId ORDER BY id ASC")
    fun observeByWorkout(workoutId: Long): Flow<List<WorkoutEntryEntity>>

    @Query("SELECT * FROM workout_entries ORDER BY id DESC")
    fun observeAll(): Flow<List<WorkoutEntryEntity>>

    @Query("""SELECT workout_entries.*, exercises.name AS exerciseName,
        exercises.measurementType AS measurementType
        FROM workout_entries
        INNER JOIN exercises ON exercises.id = workout_entries.exerciseId
        WHERE workout_entries.workoutId = :workoutId
        ORDER BY workout_entries.id ASC""")
    fun observeDetailedByWorkout(workoutId: Long): Flow<List<LoggedWorkoutEntry>>

    @Query("""SELECT workout_entries.*, exercises.name AS exerciseName,
        exercises.measurementType AS measurementType
        FROM workout_entries
        INNER JOIN exercises ON exercises.id = workout_entries.exerciseId
        ORDER BY workout_entries.id DESC""")
    fun observeAllDetailed(): Flow<List<LoggedWorkoutEntry>>

    @Insert suspend fun insert(entry: WorkoutEntryEntity): Long
    @Update suspend fun update(entry: WorkoutEntryEntity)
    @Delete suspend fun delete(entry: WorkoutEntryEntity)
}
