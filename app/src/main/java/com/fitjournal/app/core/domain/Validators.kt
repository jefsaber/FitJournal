package com.fitjournal.app.core.domain

data class ExerciseValidationErrors(
    val name: Boolean = false,
    val category: Boolean = false,
    val measurementType: Boolean = false
) {
    val hasErrors: Boolean get() = name || category || measurementType
}

object ExerciseValidator {
    fun validate(
        name: String,
        category: String,
        measurementType: String = "REPS_WEIGHT"
    ) = ExerciseValidationErrors(
        name = name.trim().length !in 2..80,
        category = category.isBlank(),
        measurementType = measurementType !in setOf("REPS_WEIGHT", "DURATION")
    )

    fun valid(name: String, category: String, measurementType: String = "REPS_WEIGHT"): Boolean =
        !validate(name, category, measurementType).hasErrors
}

object WorkoutValidator {
    fun valid(title: String): Boolean = title.trim().length in 2..80
}

object ProgressCalculator {
    /** Total load volume in kg-reps; time-based entries are excluded. */
    fun volumeKg(sets: Int, repetitions: Int, weightKg: Double): Double {
        require(sets >= 0 && repetitions >= 0 && weightKg >= 0)
        return sets * repetitions * weightKg
    }
}
