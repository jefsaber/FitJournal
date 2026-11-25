package com.fitjournal.app

import androidx.appcompat.app.AppCompatDelegate
import com.fitjournal.app.core.preferences.ThemePreference
import com.fitjournal.app.features.settings.ThemeChoice
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemePreferenceTest {
    @Test fun savedDarkModeMapsToNightModes() {
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, ThemePreference.nightModeFor(null))
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, ThemePreference.nightModeFor(true))
        assertEquals(AppCompatDelegate.MODE_NIGHT_NO, ThemePreference.nightModeFor(false))
    }

    @Test fun themeChoicesRoundTripThroughTheStoredValue() {
        ThemeChoice.entries.forEach { choice ->
            assertEquals(choice, ThemeChoice.from(choice.darkMode))
        }
        assertEquals(ThemeChoice.SYSTEM, ThemeChoice.from(null))
    }
}
