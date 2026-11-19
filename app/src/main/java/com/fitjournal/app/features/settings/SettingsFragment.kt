package com.fitjournal.app.features.settings

import com.fitjournal.app.R
import com.fitjournal.app.core.ui.PlaceholderFragment

/** Feature implementation remains with its assigned module owner. */
class SettingsFragment : PlaceholderFragment() {
    override val title get() = getString(R.string.settings_screen_title)
    override val details get() = getString(R.string.settings_screen_description)
    override val iconRes = R.drawable.ic_settings
}
