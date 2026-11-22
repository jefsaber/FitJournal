package com.fitjournal.app.features.workouts

import android.content.Context
import com.fitjournal.app.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Locale-aware workout date labels shared by the form and the history list. */
object WorkoutDateFormat {
    private val full = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
    private val medium = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

    fun full(date: LocalDate): String = date.format(full)

    /** "Today", "Yesterday" or "Tomorrow" when close, otherwise a medium date. */
    fun relative(context: Context, date: LocalDate, today: LocalDate = LocalDate.now()): String =
        when (date) {
            today -> context.getString(R.string.workout_date_today)
            today.minusDays(1) -> context.getString(R.string.workout_date_yesterday)
            today.plusDays(1) -> context.getString(R.string.workout_date_tomorrow)
            else -> date.format(medium)
        }
}
