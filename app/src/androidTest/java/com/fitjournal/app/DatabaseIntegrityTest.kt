package com.fitjournal.app

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fitjournal.app.core.data.ExerciseSeedImporter
import com.fitjournal.app.core.data.FitJournalDatabase
import com.fitjournal.app.core.data.entities.ExerciseEntity
import com.fitjournal.app.core.data.entities.WorkoutEntity
import com.fitjournal.app.core.data.entities.WorkoutEntryEntity
import com.fitjournal.app.core.preferences.SettingsRepository
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real Room constraints and seed imports, isolated from the installed application's database. */
@RunWith(AndroidJUnit4::class)
class DatabaseIntegrityTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: FitJournalDatabase
    private lateinit var folder: File
    private val scopes = mutableListOf<CoroutineScope>()

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, FitJournalDatabase::class.java).build()
        folder = File(context.cacheDir, "integrity-" + UUID.randomUUID()).apply { check(mkdirs()) }
    }

    private fun settings(): SettingsRepository {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scopes += scope
        val file = File(folder, UUID.randomUUID().toString() + ".preferences_pb")
        return SettingsRepository(PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }))
    }

    @After fun tearDown() = runBlocking {
        scopes.forEach { it.cancel() }
        scopes.forEach { it.coroutineContext[Job]?.join() }
        database.close()
        folder.deleteRecursively()
        Unit
    }

    private fun assetJson() = context.assets.open("exercises.json").bufferedReader().use { it.readText() }

    @Test fun secondImportSkipsReadingAndDoesNotDuplicateRows() = runBlocking {
        val settings = settings()
        var reads = 0
        val json = assetJson()
        val importer = ExerciseSeedImporter(context, database, settings) { reads++; json }
        importer.importOnce()
        val first = database.exerciseDao().observeAll().first()
        assertTrue(first.size >= 20)
        assertEquals(first.size, first.map { it.code }.distinct().size)
        importer.importOnce()
        assertEquals(first, database.exerciseDao().observeAll().first())
        assertEquals(1, reads)
        assertTrue(settings.seedImported.first())
    }

    @Test fun retryWithoutMarkerPreservesEditedAndUserCreatedExercises() = runBlocking {
        val json = assetJson()
        ExerciseSeedImporter(context, database, settings()) { json }.importOnce()
        val dao = database.exerciseDao()
        val originals = dao.observeAll().first()
        val edited = originals.first().copy(name = "My customized exercise", description = "Keep these cues")
        dao.update(edited)
        val customId = dao.insert(ExerciseEntity(
            code = UUID.randomUUID().toString(), name = "My own exercise", category = "Core"
        ))

        // Fresh marker store simulates a crash after the Room transaction but before the marker write.
        val retrySettings = settings()
        assertFalse(retrySettings.seedImported.first())
        ExerciseSeedImporter(context, database, retrySettings) { json }.importOnce()
        assertEquals(originals.size + 1, dao.observeAll().first().size)
        assertEquals(edited, dao.byId(edited.id))
        assertEquals("My own exercise", dao.byId(customId)?.name)
        assertTrue(retrySettings.seedImported.first())
    }

    @Test fun malformedRowsAreSkippedAndDuplicateCodesDoNotReplaceTheFirstRow() = runBlocking {
        val rows = JSONArray(assetJson())
        val expected = rows.length()
        val firstCode = rows.getJSONObject(0).getString("code")
        val firstName = rows.getJSONObject(0).getString("name")
        rows.put(JSONObject().put("code", "no_name").put("category", "Core"))
        rows.put(JSONObject().put("code", "wrong_type").put("name", "Wrong type")
            .put("category", "Core").put("measurementType", "INVALID"))
        rows.put(JSONObject().put("code", "null_name").put("name", JSONObject.NULL).put("category", "Core"))
        rows.put(JSONObject().put("code", "number_name").put("name", 1234).put("category", "Core"))
        rows.put(JSONObject().put("code", "short_name").put("name", "A").put("category", "Core"))
        rows.put(JSONObject().put("name", "No code").put("category", "Core"))
        rows.put(JSONObject().put("code", firstCode).put("name", "Duplicate should not overwrite").put("category", "Core"))
        val settings = settings()
        ExerciseSeedImporter(context, database, settings) { rows.toString() }.importOnce()
        val stored = database.exerciseDao().observeAll().first()
        assertEquals(expected, stored.size)
        assertEquals(firstName, stored.single { it.code == firstCode }.name)
        assertTrue(settings.seedImported.first())
    }

    @Test fun tooFewDistinctSeedsWritesNothingAndLeavesImportRetryable() = runBlocking {
        val first = JSONArray(assetJson()).getJSONObject(0).toString()
        val duplicates = JSONArray().apply { repeat(24) { put(JSONObject(first)) } }
        val settings = settings()
        try {
            ExerciseSeedImporter(context, database, settings) { duplicates.toString() }.importOnce()
            fail("Twenty-four copies of one code must not satisfy the seed minimum")
        } catch (_: IllegalArgumentException) {
            assertTrue(database.exerciseDao().observeAll().first().isEmpty())
            assertFalse(settings.seedImported.first())
        }
        ExerciseSeedImporter(context, database, settings) { assetJson() }.importOnce()
        assertTrue(database.exerciseDao().observeAll().first().size >= 20)
    }

    @Test fun invalidJsonWritesNothingAndCanBeRetried() = runBlocking {
        val settings = settings()
        try {
            ExerciseSeedImporter(context, database, settings) { "[invalid JSON" }.importOnce()
            fail("Invalid JSON must fail")
        } catch (_: org.json.JSONException) {
            assertTrue(database.exerciseDao().observeAll().first().isEmpty())
            assertFalse(settings.seedImported.first())
        }
        ExerciseSeedImporter(context, database, settings) { assetJson() }.importOnce()
        assertTrue(settings.seedImported.first())
    }

    @Test fun deletingWorkoutCascadesItsEntriesAndPreservesOtherWorkoutAndExercises() = runBlocking {
        val exerciseId = database.exerciseDao().insert(ExerciseEntity(
            code = "cascade_exercise", name = "Test press", category = "Chest"
        ))
        val workout = WorkoutEntity(title = "Delete me", sessionDate = 1L)
        val workoutId = database.workoutDao().insert(workout)
        val otherId = database.workoutDao().insert(workout.copy(title = "Keep me"))
        val dao = database.workoutEntryDao()
        dao.insert(WorkoutEntryEntity(workoutId = workoutId, exerciseId = exerciseId, sets = 3, repetitions = 8, weightKg = 40.0))
        dao.insert(WorkoutEntryEntity(workoutId = workoutId, exerciseId = exerciseId, sets = 2, repetitions = 6, weightKg = 45.0))
        dao.insert(WorkoutEntryEntity(workoutId = otherId, exerciseId = exerciseId, sets = 1, repetitions = 5, weightKg = 30.0))
        assertEquals(2, dao.observeByWorkout(workoutId).first().size)
        database.workoutDao().delete(workout.copy(id = workoutId))
        assertTrue(dao.observeByWorkout(workoutId).first().isEmpty())
        assertEquals(1, dao.observeByWorkout(otherId).first().size)
        assertNotNull(database.exerciseDao().byId(exerciseId))
    }

    @Test fun pagingCombinesSearchCaseInsensitiveCategoryAndNameOrder() = runBlocking {
        val dao = database.exerciseDao()
        listOf(
            ExerciseEntity(code = "alpha", name = "Alpha row", category = "Back"),
            ExerciseEntity(code = "zed", name = "Zed row", category = "back"),
            ExerciseEntity(code = "press", name = "Bench press", category = "Chest"),
            ExerciseEntity(code = "pull", name = "Pull-up", category = "Back")
        ).forEach { dao.insert(it) }
        suspend fun names(search: String, category: String, order: String): List<String> {
            val result = dao.page(search, category, order).load(
                PagingSource.LoadParams.Refresh(key = null, loadSize = 100, placeholdersEnabled = false)
            )
            assertTrue(result is PagingSource.LoadResult.Page)
            return (result as PagingSource.LoadResult.Page).data.map { it.name }
        }
        assertEquals(listOf("Zed row", "Alpha row"), names("row", "Back", "NAME_DESC"))
        assertEquals(listOf("Alpha row", "Zed row"), names("row", "BACK", "NAME_ASC"))
        assertTrue(names("press", "Back", "NAME_ASC").isEmpty())
        assertEquals(listOf("Bench press"), names("press", "ALL", "NAME_ASC"))
    }
}
