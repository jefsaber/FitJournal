package com.fitjournal.app

import com.fitjournal.app.core.domain.ExerciseValidator
import com.fitjournal.app.core.domain.ProgressCalculator
import com.fitjournal.app.core.domain.WorkoutEntryValidator
import com.fitjournal.app.core.domain.WorkoutValidator
import org.junit.Assert.*
import org.junit.Test

class ValidatorsTest {
    @Test fun exerciseRejectsBlankName() { assertFalse(ExerciseValidator.valid("", "Chest")) }
    @Test fun workoutRejectsTooShortTitle() { assertFalse(WorkoutValidator.valid("A")) }
    @Test fun volumeUsesSetsTimesRepsTimesWeight() {
        assertEquals(720.0, ProgressCalculator.volumeKg(3, 8, 30.0), 0.001)
    }
    @Test fun strengthEntryRequiresPositiveSetsRepsAndFiniteWeight() {
        assertTrue(WorkoutEntryValidator.strengthHasErrors(0, 8, 20.0))
        assertTrue(WorkoutEntryValidator.strengthHasErrors(3, 0, 20.0))
        assertTrue(WorkoutEntryValidator.strengthHasErrors(3, 8, 0.0))
        assertTrue(WorkoutEntryValidator.strengthHasErrors(3, 8, Double.NaN))
        assertFalse(WorkoutEntryValidator.strengthHasErrors(3, 8, 20.0))
    }
    @Test fun durationEntryRequiresPositiveSeconds() {
        assertTrue(WorkoutEntryValidator.durationHasErrors(0))
        assertFalse(WorkoutEntryValidator.durationHasErrors(60))
    }
    @Test fun personalRecordReturnsMaximumValidMetric() {
        assertEquals(100.0, ProgressCalculator.personalRecord(listOf(80.0, Double.NaN, -2.0, 100.0))!!, 0.001)
    }
    @Test fun personalRecordReturnsNullWithoutValidMetrics() {
        assertNull(ProgressCalculator.personalRecord(listOf(Double.NaN, -1.0)))
    }
}
