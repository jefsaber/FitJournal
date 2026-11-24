package com.fitjournal.app.core.domain

import java.time.LocalDate

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

enum class WorkoutDateError { MISSING, TOO_EARLY, TOO_FAR_AHEAD, COMPLETED_IN_FUTURE }

data class WorkoutValidationErrors(
    val title: Boolean = false,
    val date: WorkoutDateError? = null,
    val notes: Boolean = false
) {
    val hasErrors: Boolean get() = title || date != null || notes
}

object WorkoutValidator {
    const val MAX_NOTES_LENGTH = 500
    const val MAX_DAYS_AHEAD = 365L
    val EARLIEST_DATE: LocalDate = LocalDate.of(2000, 1, 1)

    fun valid(title: String): Boolean = title.trim().length in 2..80

    /**
     * Sessions may be planned up to a year ahead, but a session marked as completed
     * cannot be dated in the future.
     */
    fun validate(
        title: String,
        date: LocalDate?,
        notes: String,
        isCompleted: Boolean,
        today: LocalDate
    ) = WorkoutValidationErrors(
        title = !valid(title),
        date = when {
            date == null -> WorkoutDateError.MISSING
            date.isBefore(EARLIEST_DATE) -> WorkoutDateError.TOO_EARLY
            date.isAfter(today.plusDays(MAX_DAYS_AHEAD)) -> WorkoutDateError.TOO_FAR_AHEAD
            isCompleted && date.isAfter(today) -> WorkoutDateError.COMPLETED_IN_FUTURE
            else -> null
        },
        notes = notes.trim().length > MAX_NOTES_LENGTH
    )
}

object ProgressCalculator {
    /** Total load volume in kg-reps; time-based entries are excluded. */
    fun volumeKg(sets: Int, repetitions: Int, weightKg: Double): Double {
        require(sets >= 0 && repetitions >= 0 && weightKg >= 0)
        return sets * repetitions * weightKg
    }

    /** Highest logged weight for strength entries or longest duration for timed entries. */
    fun personalRecord(values: List<Double>): Double? =
        values.filter { it.isFinite() && it >= 0.0 }.maxOrNull()
}
