package com.fitjournal.app

import com.fitjournal.app.core.domain.WeightConverter
import com.fitjournal.app.core.domain.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class WeightConverterTest {
    @Test fun kilogramsAreReturnedUnchanged() {
        assertEquals(82.5, WeightConverter.fromKilograms(82.5, WeightUnit.KILOGRAMS), 0.0)
        assertEquals(82.5, WeightConverter.toKilograms(82.5, WeightUnit.KILOGRAMS), 0.0)
    }

    @Test fun poundsUseTheInternationalDefinition() {
        assertEquals(0.45359237, WeightConverter.toKilograms(1.0, WeightUnit.POUNDS), 1e-12)
        assertEquals(220.462_262, WeightConverter.fromKilograms(100.0, WeightUnit.POUNDS), 1e-6)
        assertEquals(20.0, WeightConverter.toKilograms(44.092_452_4, WeightUnit.POUNDS), 1e-6)
        assertEquals(0.0, WeightConverter.fromKilograms(0.0, WeightUnit.POUNDS), 0.0)
    }

    @Test fun roundTripsPreserveCanonicalKilogramsAndDisplayedPounds() {
        listOf(0.5, 1.0, 2.5, 20.0, 62.5, 100.0, 142.75, 300.0).forEach { kilograms ->
            val pounds = WeightConverter.fromKilograms(kilograms, WeightUnit.POUNDS)
            assertEquals(kilograms, WeightConverter.toKilograms(pounds, WeightUnit.POUNDS), 1e-9)
        }
        listOf(5.0, 45.0, 135.0, 225.0, 315.0).forEach { pounds ->
            val kilograms = WeightConverter.toKilograms(pounds, WeightUnit.POUNDS)
            assertEquals(pounds, WeightConverter.fromKilograms(kilograms, WeightUnit.POUNDS), 1e-9)
        }
    }

    @Test fun unitCodesMatchSavedPreferenceValues() {
        assertSame(WeightUnit.KILOGRAMS, WeightUnit.fromCode("kg"))
        assertSame(WeightUnit.POUNDS, WeightUnit.fromCode("lb"))
        assertSame(WeightUnit.KILOGRAMS, WeightUnit.fromCode("stone"))
        assertEquals("lb", WeightUnit.POUNDS.code)
    }
}
