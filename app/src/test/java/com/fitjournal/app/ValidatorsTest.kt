package com.fitjournal.app

import com.fitjournal.app.core.domain.ExerciseValidator
import com.fitjournal.app.core.domain.ProgressCalculator
import com.fitjournal.app.core.domain.WorkoutValidator
import org.junit.Assert.*
import org.junit.Test

class ValidatorsTest {
    @Test fun exerciseRejectsBlankName() { assertFalse(ExerciseValidator.valid("", "Chest")) }
    @Test fun workoutRejectsTooShortTitle() { assertFalse(WorkoutValidator.valid("A")) }
    @Test fun volumeUsesSetsTimesRepsTimesWeight() {
        assertEquals(720.0, ProgressCalculator.volumeKg(3, 8, 30.0), 0.001)
    }
    @Test fun strengthEntryRequiresPositiveSetsRepsAndWeight() {
        assertTrue(EntryValidator.validateStrength(0, 8, 20.0))
        assertTrue(EntryValidator.validateStrength(3, 0, 20.0))
        assertTrue(EntryValidator.validateStrength(3, 8, 0.0))
        assertFalse(EntryValidator.validateStrength(3, 8, 20.0))
    }
    @Test fun durationEntryRequiresPositiveSeconds() {
        assertTrue(EntryValidator.invalidDuration(0))
        assertFalse(EntryValidator.invalidDuration(60))
    }
    @Test fun personalRecordUsesMaximumAndIgnoresInvalidValues() {
        assertEquals(100.0, ProgressCalculator.personalRecord(listOf(80.0, Double.NaN, -2.0, 100.0))!!, 0.001)
    }
}

object EntryValidator {
    fun validateStrength(sets: Int, repetitions: Int, weightKg: Double): Boolean =
        sets <= 0 || repetitions <= 0 || !weightKg.isFinite() || weightKg <= 0.0
    fun invalidDuration(seconds: Long): Boolean = seconds <= 0
}
