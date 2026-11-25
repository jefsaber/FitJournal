package com.fitjournal.app

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.fitjournal.app.core.data.dao.ExerciseDao
import com.fitjournal.app.core.data.entities.ExerciseEntity
import com.fitjournal.app.core.data.repositories.ExerciseRepository
import com.fitjournal.app.core.preferences.SettingsRepository
import com.fitjournal.app.features.exercises.ExerciseListViewModel
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseCatalogueViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val viewModels = ViewModelStore()
    private lateinit var dao: CatalogueDao
    private lateinit var store: MemoryPreferences
    private lateinit var settings: SettingsRepository

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        dao = CatalogueDao()
        store = MemoryPreferences()
        settings = SettingsRepository(store)
    }

    @After fun tearDown() {
        viewModels.clear()
        Dispatchers.resetMain()
    }

    private fun model(handle: SavedStateHandle = SavedStateHandle()) =
        ExerciseListViewModel(ExerciseRepository(dao), settings, handle).also {
            viewModels.put("catalogue-" + viewModels.keys().size, it)
        }

    @Test fun queryCombinesSearchCategoryAndChangesFromSettings() = runTest(dispatcher) {
        settings.setExerciseSort("NAME_DESC")
        val model = model()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.exercises.collect() }
        advanceUntilIdle()
        model.setSearch(" row ")
        model.setCategory("Back")
        advanceUntilIdle()
        assertEquals(Triple("row", "Back", "NAME_DESC"), dao.queries.last())
        settings.setExerciseSort("NAME_ASC")
        advanceUntilIdle()
        assertEquals(Triple("row", "Back", "NAME_ASC"), dao.queries.last())
        model.setCategory(ExerciseListViewModel.ALL_CATEGORIES)
        advanceUntilIdle()
        assertEquals(Triple("row", "ALL", "NAME_ASC"), dao.queries.last())
    }

    @Test fun categoriesComeFromLiveDataAndIgnoreCaseDuplicates() = runTest(dispatcher) {
        val model = model()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.categories.collect() }
        dao.rows.value = listOf(
            ExerciseEntity(code = "1", name = "Squat", category = "Legs"),
            ExerciseEntity(code = "2", name = "Row", category = "Back"),
            ExerciseEntity(code = "3", name = "Row variation", category = "back")
        )
        advanceUntilIdle()
        assertEquals(listOf("Back", "Legs"), model.categories.value)
        dao.rows.value += ExerciseEntity(code = "4", name = "Plank", category = "Core")
        advanceUntilIdle()
        assertEquals(listOf("Back", "Core", "Legs"), model.categories.value)
    }

    @Test fun searchAndCategorySurviveSavedStateRestoration() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        val model = model(handle)
        model.setSearch("plank")
        model.setCategory("Core")
        val restored = model(SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) }))
        advanceUntilIdle()
        assertEquals("plank", restored.search.value)
        assertEquals("Core", restored.category.value)
    }

    @Test fun sortSavesToSharedPreferenceAndFailureCanBeRetried() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()
        store.failWrites = true
        model.setSort("NAME_DESC")
        advanceUntilIdle()
        assertTrue(model.sortSaveFailed.value)
        assertFalse(model.savingSort.value)
        assertEquals("NAME_ASC", model.sort.value)
        store.failWrites = false
        model.setSort("NAME_DESC")
        advanceUntilIdle()
        assertFalse(model.sortSaveFailed.value)
        assertEquals("NAME_DESC", model.sort.value)
        assertEquals("NAME_DESC", settings.settings.first().exerciseSort)
    }

    private class MemoryPreferences : DataStore<Preferences> {
        override val data = MutableStateFlow<Preferences>(emptyPreferences())
        var failWrites = false
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            if (failWrites) throw IOException("Simulated write failure")
            return transform(data.value).also { data.value = it }
        }
    }

    private class CatalogueDao : ExerciseDao {
        val rows = MutableStateFlow<List<ExerciseEntity>>(emptyList())
        val queries = mutableListOf<Triple<String, String, String>>()
        override fun observeAll() = rows
        override fun countFlow() = rows.map { it.size }
        override fun page(search: String, category: String, sort: String): PagingSource<Int, ExerciseEntity> {
            queries += Triple(search, category, sort)
            return object : PagingSource<Int, ExerciseEntity>() {
                override fun getRefreshKey(state: PagingState<Int, ExerciseEntity>): Int? = null
                override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ExerciseEntity> =
                    LoadResult.Page(emptyList(), null, null)
            }
        }
        override suspend fun byId(id: Long) = rows.value.find { it.id == id }
        override suspend fun insertIfAbsent(exercise: ExerciseEntity): Long = error("Not used")
        override suspend fun insert(exercise: ExerciseEntity): Long = error("Not used")
        override suspend fun update(exercise: ExerciseEntity) = Unit
        override suspend fun delete(exercise: ExerciseEntity) = Unit
    }
}
