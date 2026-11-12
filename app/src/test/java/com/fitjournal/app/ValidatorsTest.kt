package com.fitjournal.app

import com.fitjournal.app.core.domain.ExerciseValidator
import com.fitjournal.app.core.domain.ProgressCalculator
import com.fitjournal.app.core.domain.WorkoutValidator
import org.junit.Assert.*
import org.junit.Test

class ValidatorsTest {
    @Test fun exerciseRejectsBlankName() {
        assertFalse(ExerciseValidator.valid("", "Chest"))
    }
    @Test fun workoutRejectsTooShortTitle() {
        assertFalse(WorkoutValidator.valid("A"))
    }
    @Test fun volumeUsesSetsTimesRepsTimesWeight() {
        assertEquals(720.0, ProgressCalculator.volumeKg(3, 8, 30.0), 0.001)
    }
}
