package com.fitjournal.app.features.entries

import com.fitjournal.app.R
import com.fitjournal.app.core.ui.PlaceholderFragment

/** Feature implementation remains with its assigned module owner. */
class WorkoutDetailsFragment : PlaceholderFragment() {
    override val title get() = getString(R.string.workout_details_title)
    override val details get() = getString(R.string.workout_details_description)
    override val iconRes = R.drawable.ic_dumbbell
}
