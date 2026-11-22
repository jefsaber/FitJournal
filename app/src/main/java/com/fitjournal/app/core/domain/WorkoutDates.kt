package com.fitjournal.app.core.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Workouts are calendar sessions, so `sessionDate` stores the epoch millis of the local
 * start of the chosen day. These helpers keep every conversion in one tested place.
 */
object WorkoutDates {
    fun toEpochMillis(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    fun toLocalDate(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

    /** MaterialDatePicker selections are UTC midnight; read them back as a calendar date. */
    fun fromPickerSelection(utcMillis: Long): LocalDate =
        Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()

    fun toPickerSelection(date: LocalDate): Long =
        date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}

enum class WorkoutStatusFilter { ALL, PLANNED, COMPLETED }

enum class WorkoutPeriodFilter { ANY_TIME, UPCOMING, LAST_7_DAYS, LAST_30_DAYS }

/** History filter state; [dateRange] turns the period into a `[from, to)` millis window. */
data class WorkoutHistoryFilter(
    val search: String = "",
    val status: WorkoutStatusFilter = WorkoutStatusFilter.ALL,
    val period: WorkoutPeriodFilter = WorkoutPeriodFilter.ANY_TIME
) {
    val isDefault: Boolean get() = this == WorkoutHistoryFilter()

    /** Upcoming sessions read best soonest-first; history reads best newest-first. */
    val ascending: Boolean get() = period == WorkoutPeriodFilter.UPCOMING

    fun dateRange(today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Pair<Long, Long> {
        val tomorrow = WorkoutDates.toEpochMillis(today.plusDays(1), zone)
        return when (period) {
            WorkoutPeriodFilter.ANY_TIME -> Long.MIN_VALUE to Long.MAX_VALUE
            WorkoutPeriodFilter.UPCOMING -> WorkoutDates.toEpochMillis(today, zone) to Long.MAX_VALUE
            WorkoutPeriodFilter.LAST_7_DAYS -> WorkoutDates.toEpochMillis(today.minusDays(6), zone) to tomorrow
            WorkoutPeriodFilter.LAST_30_DAYS -> WorkoutDates.toEpochMillis(today.minusDays(29), zone) to tomorrow
        }
    }
}
