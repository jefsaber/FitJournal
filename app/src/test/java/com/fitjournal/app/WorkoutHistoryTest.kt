package com.fitjournal.app

import androidx.lifecycle.SavedStateHandle
import com.fitjournal.app.core.data.entities.WorkoutEntity
import com.fitjournal.app.core.data.repositories.WorkoutRepository
import com.fitjournal.app.core.domain.WorkoutDateError
import com.fitjournal.app.core.domain.WorkoutDates
import com.fitjournal.app.core.domain.WorkoutHistoryFilter
import com.fitjournal.app.core.domain.WorkoutPeriodFilter
import com.fitjournal.app.core.domain.WorkoutStatusFilter
import com.fitjournal.app.core.domain.WorkoutValidator
import com.fitjournal.app.features.workouts.WorkoutsViewModel
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Validation, date conversion and history filtering for the workouts module. */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutHistoryTest {
    private val dispatcher = StandardTestDispatcher()
    private val zone = ZoneId.of("Europe/Paris")
    private val today = LocalDate.of(2026, 11, 22)

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun workout(id: Long, title: String, date: LocalDate, completed: Boolean, notes: String = "") =
        WorkoutEntity(id, title, WorkoutDates.toEpochMillis(date, zone), notes, completed)

    @Test fun validatorEnforcesTitleNotesAndDateBoundaries() {
        fun errors(title: String = "Leg day", date: LocalDate? = today, notes: String = "", completed: Boolean = false) =
            WorkoutValidator.validate(title, date, notes, completed, today)

        assertFalse(errors().hasErrors)
        assertTrue(errors(title = "  ").title)
        assertFalse(errors(title = "A".repeat(80)).title)
        assertTrue(errors(title = "A".repeat(81)).title)
        assertFalse(errors(notes = "n".repeat(500)).notes)
        assertTrue(errors(notes = "n".repeat(501)).notes)
        assertEquals(WorkoutDateError.MISSING, errors(date = null).date)
        assertEquals(WorkoutDateError.TOO_EARLY, errors(date = LocalDate.of(1999, 12, 31)).date)
        assertNull(errors(date = today.plusDays(365)).date)
        assertEquals(WorkoutDateError.TOO_FAR_AHEAD, errors(date = today.plusDays(366)).date)
        assertNull(errors(date = today, completed = true).date)
        assertEquals(WorkoutDateError.COMPLETED_IN_FUTURE, errors(date = today.plusDays(1), completed = true).date)
    }

    @Test fun datesRoundTripThroughStorageAndPickerAcrossDaylightSaving() {
        // 29 March 2026 is a DST change day in Paris; the local day must still round-trip.
        val dstDay = LocalDate.of(2026, 3, 29)
        assertEquals(dstDay, WorkoutDates.toLocalDate(WorkoutDates.toEpochMillis(dstDay, zone), zone))
        assertEquals(dstDay, WorkoutDates.fromPickerSelection(WorkoutDates.toPickerSelection(dstDay)))
        assertEquals(1_795_305_600_000L, WorkoutDates.toPickerSelection(today))
    }

    @Test fun periodFiltersProduceInclusiveDayWindows() {
        val start = { date: LocalDate -> WorkoutDates.toEpochMillis(date, zone) }
        assertEquals(Long.MIN_VALUE to Long.MAX_VALUE, WorkoutHistoryFilter().dateRange(today, zone))
        assertEquals(
            start(today) to Long.MAX_VALUE,
            WorkoutHistoryFilter(period = WorkoutPeriodFilter.UPCOMING).dateRange(today, zone)
        )
        assertEquals(
            start(LocalDate.of(2026, 11, 16)) to start(LocalDate.of(2026, 11, 23)),
            WorkoutHistoryFilter(period = WorkoutPeriodFilter.LAST_7_DAYS).dateRange(today, zone)
        )
        assertEquals(
            start(LocalDate.of(2026, 10, 24)) to start(LocalDate.of(2026, 11, 23)),
            WorkoutHistoryFilter(period = WorkoutPeriodFilter.LAST_30_DAYS).dateRange(today, zone)
        )
    }

    @Test fun historyCombinesSearchStatusAndPeriodAndDistinguishesNoMatches() = runTest(dispatcher) {
        val dao = MemoryWorkoutDao()
        dao.rows.value = listOf(
            workout(1, "Push day", today.minusDays(2), completed = true, notes = "Felt strong"),
            workout(2, "Leg day", today.plusDays(3), completed = false),
            workout(3, "Long run", today.minusDays(40), completed = true),
            workout(4, "Mobility", today.plusDays(1), completed = false)
        )
        val handle = SavedStateHandle()
        val model = WorkoutsViewModel(WorkoutRepository(dao), handle, zone) { today }
        backgroundScope.launch { model.state.collect {} }
        advanceUntilIdle()

        fun titles() = model.state.value.workouts.map { it.title }
        assertEquals(listOf("Leg day", "Mobility", "Push day", "Long run"), titles())
        assertEquals(4, model.state.value.totalCount)

        model.setStatus(WorkoutStatusFilter.COMPLETED)
        advanceUntilIdle()
        assertEquals(listOf("Push day", "Long run"), titles())

        model.setPeriod(WorkoutPeriodFilter.LAST_7_DAYS)
        advanceUntilIdle()
        assertEquals(listOf("Push day"), titles())

        model.setSearch("strong")
        advanceUntilIdle()
        assertEquals(listOf("Push day"), titles())

        model.setSearch("leg")
        advanceUntilIdle()
        assertTrue(model.state.value.showNoMatches)
        assertFalse(model.state.value.showNoWorkouts)

        // Filters survive process death through SavedStateHandle.
        val restored = WorkoutsViewModel(WorkoutRepository(dao), handle, zone) { today }
        assertEquals(
            WorkoutHistoryFilter("leg", WorkoutStatusFilter.COMPLETED, WorkoutPeriodFilter.LAST_7_DAYS),
            restored.filter.value
        )

        model.clearFilters()
        model.setStatus(WorkoutStatusFilter.PLANNED)
        model.setPeriod(WorkoutPeriodFilter.UPCOMING)
        advanceUntilIdle()
        // Upcoming sessions are listed soonest first.
        assertEquals(listOf("Mobility", "Leg day"), titles())
    }

    @Test fun emptyHistoryIsReportedSeparatelyFromNoMatches() = runTest(dispatcher) {
        val model = WorkoutsViewModel(WorkoutRepository(MemoryWorkoutDao()), SavedStateHandle(), zone) { today }
        assertTrue(model.state.value.loading)
        backgroundScope.launch { model.state.collect {} }
        advanceUntilIdle()
        assertTrue(model.state.value.showNoWorkouts)
        assertFalse(model.state.value.showNoMatches)
    }
}
