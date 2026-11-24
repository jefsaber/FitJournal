package com.fitjournal.app.core.domain

enum class WeightUnit(val code: String) {
    KILOGRAMS("kg"),
    POUNDS("lb");

    companion object {
        fun fromCode(value: String): WeightUnit = entries.firstOrNull { it.code == value } ?: KILOGRAMS
    }
}

/** Canonical persistence is always kilograms; conversion happens only at UI boundaries. */
object WeightConverter {
    const val KILOGRAMS_PER_POUND = 0.45359237

    fun fromKilograms(kilograms: Double, unit: WeightUnit): Double = when (unit) {
        WeightUnit.KILOGRAMS -> kilograms
        WeightUnit.POUNDS -> kilograms / KILOGRAMS_PER_POUND
    }

    fun toKilograms(value: Double, unit: WeightUnit): Double = when (unit) {
        WeightUnit.KILOGRAMS -> value
        WeightUnit.POUNDS -> value * KILOGRAMS_PER_POUND
    }
}
