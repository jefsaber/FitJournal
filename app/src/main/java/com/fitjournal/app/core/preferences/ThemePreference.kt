package com.fitjournal.app.core.preferences

import androidx.appcompat.app.AppCompatDelegate

/**
 * Maps the saved dark-mode preference to an AppCompat night mode.
 * No saved choice keeps the original behaviour of following the device theme.
 */
object ThemePreference {
    fun nightModeFor(darkMode: Boolean?): Int = when (darkMode) {
        null -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        true -> AppCompatDelegate.MODE_NIGHT_YES
        false -> AppCompatDelegate.MODE_NIGHT_NO
    }

    /** Recreates running activities only when the mode actually changes. */
    fun apply(darkMode: Boolean?) {
        val mode = nightModeFor(darkMode)
        if (AppCompatDelegate.getDefaultNightMode() != mode) AppCompatDelegate.setDefaultNightMode(mode)
    }
}
