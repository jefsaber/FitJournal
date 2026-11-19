package com.fitjournal.app.features.workouts

import com.fitjournal.app.R
import com.fitjournal.app.core.ui.PlaceholderFragment

/** Feature implementation remains with its assigned module owner. */
class WorkoutFormFragment : PlaceholderFragment() {
    override val title get() = getString(R.string.workout_form_title)
    override val details get() = getString(R.string.workout_form_description)
    override val iconRes = R.drawable.ic_calendar
}
