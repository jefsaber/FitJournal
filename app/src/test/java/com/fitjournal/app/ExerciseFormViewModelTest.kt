package com.fitjournal.app

import androidx.lifecycle.SavedStateHandle
import androidx.paging.PagingSource
import com.fitjournal.app.core.data.dao.ExerciseDao
import com.fitjournal.app.core.data.entities.ExerciseEntity
import com.fitjournal.app.core.data.repositories.ExerciseRepository
import com.fitjournal.app.core.domain.ExerciseValidator
import com.fitjournal.app.features.exercises.ExerciseFormPhase
import com.fitjournal.app.features.exercises.ExerciseFormViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
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
class ExerciseFormViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var dao: MemoryExerciseDao
    private lateinit var repository: ExerciseRepository

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        dao = MemoryExerciseDao()
        repository = ExerciseRepository(dao)
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun validationRejectsWhitespaceAndEnforcesTrimmedNameBoundaries() {
        assertFalse(ExerciseValidator.valid("   ", "Chest"))
        assertFalse(ExerciseValidator.valid("A", "Chest"))
        assertTrue(ExerciseValidator.valid(" Ab ", "Chest"))
        assertTrue(ExerciseValidator.valid("A".repeat(80), "Chest"))
        assertFalse(ExerciseValidator.valid("A".repeat(81), "Chest"))
        assertFalse(ExerciseValidator.valid("Push-up", "  "))
    }

    @Test fun validationAllowsOnlySupportedTrackingTypes() {
        assertTrue(ExerciseValidator.valid("Plank", "Core", "DURATION"))
        assertTrue(ExerciseValidator.valid("Squat", "Legs", "REPS_WEIGHT"))
        assertFalse(ExerciseValidator.valid("Squat", "Legs", "UNKNOWN"))
    }

    @Test fun invalidFormReportsFieldErrorsWithoutInserting() = runTest(dispatcher) {
        val model = ExerciseFormViewModel(repository, SavedStateHandle())
        model.save()
        advanceUntilIdle()
        assertTrue(model.state.value.errors.name)
        assertTrue(model.state.value.errors.category)
        assertEquals(0, dao.inserts)
        assertEquals(ExerciseFormPhase.READY, model.state.value.phase)
    }

    @Test fun createTrimsFieldsAndIgnoresRepeatedSaveWhileBusy() = runTest(dispatcher) {
        val model = ExerciseFormViewModel(repository, SavedStateHandle())
        model.setName("  Plank  ")
        model.setCategory(" Core ")
        model.setMeasurementType("DURATION")
        model.setDescription(" Hold steady. ")
        model.save()
        model.save()
        advanceUntilIdle()
        val saved = dao.rows.value.single()
        assertEquals("Plank", saved.name)
        assertEquals("Core", saved.category)
        assertEquals("DURATION", saved.measurementType)
        assertEquals("Hold steady.", saved.description)
        assertTrue(saved.code.isNotBlank())
        assertEquals(1, dao.inserts)
        assertEquals(ExerciseFormPhase.SAVED, model.state.value.phase)
    }

    @Test fun editingPreservesSeedIdAndCodeWithoutCreatingAnotherRecord() = runTest(dispatcher) {
        val seed = ExerciseEntity(42, "seed_plank", "Plank", "Core", "DURATION", "Original")
        dao.rows.value = listOf(seed)
        val model = ExerciseFormViewModel(repository, SavedStateHandle(mapOf("exerciseId" to 42L)))
        advanceUntilIdle()
        assertEquals("Plank", model.state.value.name)
        model.setName("Side plank")
        model.setDescription("Updated")
        model.save()
        advanceUntilIdle()
        assertEquals(seed.copy(name = "Side plank", description = "Updated"), dao.rows.value.single())
        assertEquals(0, dao.inserts)
        assertEquals(1, dao.updates)
    }

    @Test fun restoredDraftIsNotOverwrittenWhenExistingExerciseLoadsAgain() = runTest(dispatcher) {
        dao.rows.value = listOf(ExerciseEntity(42, "seed_plank", "Plank", "Core"))
        val handle = SavedStateHandle(mapOf("exerciseId" to 42L))
        val model = ExerciseFormViewModel(repository, handle)
        advanceUntilIdle()
        model.setName("Unsaved changes")
        model.setCategory("Custom category")
        model.setDescription("Draft")
        val restoredHandle = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })
        val restored = ExerciseFormViewModel(repository, restoredHandle)
        advanceUntilIdle()
        assertEquals("Unsaved changes", restored.state.value.name)
        assertEquals("Custom category", restored.state.value.category)
        assertEquals("Draft", restored.state.value.description)
    }

    @Test fun failedSaveKeepsDraftAndCanBeRetried() = runTest(dispatcher) {
        val model = ExerciseFormViewModel(repository, SavedStateHandle())
        model.setName("Push-up")
        model.setCategory("Chest")
        dao.failInsert = true
        model.save()
        advanceUntilIdle()
        assertTrue(model.state.value.saveFailed)
        assertEquals("Push-up", model.state.value.name)
        assertEquals(ExerciseFormPhase.READY, model.state.value.phase)
        dao.failInsert = false
        model.save()
        advanceUntilIdle()
        assertEquals(ExerciseFormPhase.SAVED, model.state.value.phase)
        assertEquals(1, dao.rows.value.size)
    }

    @Test fun missingExerciseCannotBeSavedAsANewRecord() = runTest(dispatcher) {
        val model = ExerciseFormViewModel(repository, SavedStateHandle(mapOf("exerciseId" to 99L)))
        advanceUntilIdle()
        assertEquals(ExerciseFormPhase.NOT_FOUND, model.state.value.phase)
        model.save()
        advanceUntilIdle()
        assertEquals(0, dao.inserts)
        assertEquals(0, dao.updates)
    }

    private class MemoryExerciseDao : ExerciseDao {
        val rows = MutableStateFlow<List<ExerciseEntity>>(emptyList())
        var inserts = 0
        var updates = 0
        var failInsert = false
        override fun observeAll() = rows
        override fun countFlow() = rows.map { it.size }
        override fun page(search: String, category: String, sort: String): PagingSource<Int, ExerciseEntity> =
            error("Paging is not used by the form")
        override suspend fun byId(id: Long) = rows.value.find { it.id == id }
        override suspend fun insert(exercise: ExerciseEntity): Long {
            check(!failInsert) { "Simulated database failure" }
            check(rows.value.none { it.code == exercise.code })
            val id = (rows.value.maxOfOrNull { it.id } ?: 0L) + 1L
            rows.value += exercise.copy(id = id)
            inserts++
            return id
        }
        override suspend fun insertIfAbsent(exercise: ExerciseEntity): Long =
            if (rows.value.any { it.code == exercise.code }) -1L else insert(exercise)
        override suspend fun update(exercise: ExerciseEntity) {
            rows.value = rows.value.map { if (it.id == exercise.id) exercise else it }
            updates++
        }
        override suspend fun delete(exercise: ExerciseEntity) {
            rows.value = rows.value.filterNot { it.id == exercise.id }
        }
    }
}
