package com.fitjournal.app

import androidx.lifecycle.SavedStateHandle
import com.fitjournal.app.core.data.entities.WorkoutEntity
import com.fitjournal.app.core.data.repositories.WorkoutRepository
import com.fitjournal.app.core.domain.WorkoutDateError
import com.fitjournal.app.core.domain.WorkoutDates
import com.fitjournal.app.features.workouts.WorkoutFormPhase
import com.fitjournal.app.features.workouts.WorkoutFormViewModel
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutFormViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val zone = ZoneId.of("Europe/Paris")
    private val today = LocalDate.of(2026, 11, 22)
    private lateinit var dao: MemoryWorkoutDao
    private lateinit var repository: WorkoutRepository

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        dao = MemoryWorkoutDao()
        repository = WorkoutRepository(dao)
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    private fun model(handle: SavedStateHandle = SavedStateHandle()) =
        WorkoutFormViewModel(repository, handle, zone) { today }

    private fun stored(date: LocalDate) = WorkoutDates.toEpochMillis(date, zone)

    @Test fun newWorkoutDefaultsToTodayAndRejectsInvalidTitleWithoutInserting() = runTest(dispatcher) {
        val model = model()
        assertEquals(today, model.state.value.date)
        model.setTitle(" A ")
        model.save()
        advanceUntilIdle()
        assertTrue(model.state.value.errors.title)
        assertEquals(WorkoutFormPhase.READY, model.state.value.phase)
        assertEquals(0, dao.inserts)
    }

    @Test fun createTrimsFieldsStoresLocalStartOfDayAndIgnoresRepeatedSave() = runTest(dispatcher) {
        val model = model()
        model.setTitle("  Push day  ")
        model.setDate(LocalDate.of(2026, 11, 20))
        model.setNotes("  Felt strong ")
        model.setCompleted(true)
        model.save()
        model.save()
        advanceUntilIdle()
        val saved = dao.rows.value.single()
        assertEquals("Push day", saved.title)
        assertEquals("Felt strong", saved.notes)
        assertTrue(saved.isCompleted)
        assertEquals(stored(LocalDate.of(2026, 11, 20)), saved.sessionDate)
        assertEquals(1, dao.inserts)
        assertEquals(WorkoutFormPhase.SAVED, model.state.value.phase)
    }

    @Test fun completedWorkoutCannotBeDatedInTheFutureButPlannedOneCan() = runTest(dispatcher) {
        val model = model()
        model.setTitle("Leg day")
        model.setDate(today.plusDays(3))
        model.setCompleted(true)
        model.save()
        advanceUntilIdle()
        assertEquals(WorkoutDateError.COMPLETED_IN_FUTURE, model.state.value.errors.date)
        assertEquals(0, dao.inserts)

        model.setCompleted(false)
        model.save()
        advanceUntilIdle()
        assertEquals(WorkoutFormPhase.SAVED, model.state.value.phase)
        assertFalse(dao.rows.value.single().isCompleted)
    }

    @Test fun editingLoadsStoredValuesAndUpdatesSameRecord() = runTest(dispatcher) {
        val original = WorkoutEntity(7, "Leg day", stored(LocalDate.of(2026, 11, 18)), "Heavy", false)
        dao.rows.value = listOf(original)
        val model = model(SavedStateHandle(mapOf("workoutId" to 7L)))
        assertEquals(WorkoutFormPhase.LOADING, model.state.value.phase)
        advanceUntilIdle()
        assertEquals("Leg day", model.state.value.title)
        assertEquals(LocalDate.of(2026, 11, 18), model.state.value.date)
        assertEquals("Heavy", model.state.value.notes)

        model.setTitle("Leg day B")
        model.setCompleted(true)
        model.save()
        advanceUntilIdle()
        assertEquals(original.copy(title = "Leg day B", isCompleted = true), dao.rows.value.single())
        assertEquals(0, dao.inserts)
        assertEquals(1, dao.updates)
    }

    @Test fun restoredDraftIsNotOverwrittenWhenWorkoutLoadsAgain() = runTest(dispatcher) {
        dao.rows.value = listOf(WorkoutEntity(7, "Leg day", stored(LocalDate.of(2026, 11, 18))))
        val handle = SavedStateHandle(mapOf("workoutId" to 7L))
        val model = model(handle)
        advanceUntilIdle()
        model.setTitle("Unsaved title")
        model.setDate(LocalDate.of(2026, 11, 1))
        model.setNotes("Draft")

        val restoredHandle = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })
        val restored = model(restoredHandle)
        advanceUntilIdle()
        assertEquals("Unsaved title", restored.state.value.title)
        assertEquals(LocalDate.of(2026, 11, 1), restored.state.value.date)
        assertEquals("Draft", restored.state.value.notes)
    }

    @Test fun failedSaveKeepsDraftAndCanBeRetried() = runTest(dispatcher) {
        val model = model()
        model.setTitle("Morning run")
        dao.failWrites = true
        model.save()
        advanceUntilIdle()
        assertTrue(model.state.value.saveFailed)
        assertEquals("Morning run", model.state.value.title)
        assertEquals(WorkoutFormPhase.READY, model.state.value.phase)

        dao.failWrites = false
        model.save()
        advanceUntilIdle()
        assertEquals(WorkoutFormPhase.SAVED, model.state.value.phase)
        assertEquals(1, dao.rows.value.size)
    }

    @Test fun deleteRemovesLoadedWorkoutAndIsUnavailableForNewWorkouts() = runTest(dispatcher) {
        dao.rows.value = listOf(WorkoutEntity(7, "Leg day", stored(today)))
        val fresh = model()
        fresh.delete()
        advanceUntilIdle()
        assertEquals(0, dao.deletes)

        val editing = model(SavedStateHandle(mapOf("workoutId" to 7L)))
        advanceUntilIdle()
        editing.delete()
        advanceUntilIdle()
        assertEquals(WorkoutFormPhase.DELETED, editing.state.value.phase)
        assertTrue(dao.rows.value.isEmpty())
    }

    @Test fun missingWorkoutCannotBeSavedOrDeleted() = runTest(dispatcher) {
        val model = model(SavedStateHandle(mapOf("workoutId" to 99L)))
        advanceUntilIdle()
        assertEquals(WorkoutFormPhase.NOT_FOUND, model.state.value.phase)
        model.save()
        model.delete()
        advanceUntilIdle()
        assertEquals(0, dao.inserts + dao.updates + dao.deletes)
    }
}
