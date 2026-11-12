package com.fitjournal.app.core.domain

object ExerciseValidator {
    fun valid(name: String, category: String): Boolean =
        name.trim().length in 2..80 && category.isNotBlank()
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
