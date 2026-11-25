package com.fitjournal.app

import android.content.res.Configuration
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.util.TreeIterables
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fitjournal.app.core.preferences.AppSettings
import com.fitjournal.app.core.preferences.PreferenceValues
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.endsWith
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsTest {
    private val app = ApplicationProvider.getApplicationContext<FitJournalApplication>()
    private val settings get() = app.container.settings
    private lateinit var previous: AppSettings

    @Before fun saveAndResetPreferences() = runBlocking {
        previous = settings.settings.first()
        settings.setDarkMode(null)
        settings.setWeightUnit(PreferenceValues.WEIGHT_KG)
        settings.setExerciseSort(PreferenceValues.SORT_NAME_ASC)
        app.container.seedImporter.importOnce()
    }

    @After fun restorePreferences() = runBlocking {
        settings.setDarkMode(previous.darkMode)
        settings.setWeightUnit(previous.weightUnit)
        settings.setExerciseSort(previous.exerciseSort)
    }

    @Test fun unitAndSortPersistAndUpdateProgressAndCatalogue() {
        val names = runBlocking { app.container.exercises.all.first().map { it.name } }
        val firstAscending = names.minWith(String.CASE_INSENSITIVE_ORDER)
        val firstDescending = names.maxWith(String.CASE_INSENSITIVE_ORDER)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitFirstExercise(firstAscending)

            onView(withId(R.id.settingsFragment)).perform(click())
            awaitView(withId(R.id.unit_lb))
            onView(withId(R.id.unit_lb)).perform(click())
            onView(withId(R.id.sort_name_desc)).perform(click())
            awaitCondition("preferences saved") {
                runBlocking { settings.settings.first() }.let {
                    it.weightUnit == PreferenceValues.WEIGHT_LB && it.exerciseSort == PreferenceValues.SORT_NAME_DESC
                }
            }

            // Saved choices are shown again after the activity is recreated.
            scenario.recreate()
            awaitView(withId(R.id.unit_lb))
            onView(withId(R.id.unit_lb)).check(matches(isChecked()))
            onView(withId(R.id.sort_name_desc)).check(matches(isChecked()))

            // Progress displays weights in pounds.
            onView(withId(R.id.progressFragment)).perform(click())
            awaitView(allOf(withId(R.id.progress_volume), withText(endsWith("lb-reps"))))

            // The catalogue re-sorts using the saved default order.
            onView(withId(R.id.exercisesFragment)).perform(click())
            awaitFirstExercise(firstDescending)

            onView(withId(R.id.settingsFragment)).perform(click())
            awaitView(withId(R.id.sort_name_asc))
            onView(withId(R.id.sort_name_asc)).perform(click())
            onView(withId(R.id.exercisesFragment)).perform(click())
            awaitFirstExercise(firstAscending)
        }
    }

    @Test fun themeChoiceOverridesAndReturnsToDeviceTheme() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.settingsFragment)).perform(click())
            awaitView(withId(R.id.theme_dark))
            onView(withId(R.id.theme_system)).check(matches(isChecked()))

            onView(withId(R.id.theme_dark)).perform(click())
            awaitNightMode(AppCompatDelegate.MODE_NIGHT_YES, Configuration.UI_MODE_NIGHT_YES)
            onView(withId(R.id.theme_dark)).check(matches(isChecked()))

            onView(withId(R.id.theme_light)).perform(click())
            awaitNightMode(AppCompatDelegate.MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_NO)
            onView(withId(R.id.theme_light)).check(matches(isChecked()))

            onView(withId(R.id.theme_system)).perform(click())
            awaitCondition("device theme restored") {
                AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            awaitCondition("system theme saved") { runBlocking { settings.settings.first().darkMode } == null }
        }
    }

    /** Waits for the saved mode and for the recreated activity to use the matching resources. */
    private fun awaitNightMode(mode: Int, uiMode: Int) {
        awaitCondition("night mode $mode") { AppCompatDelegate.getDefaultNightMode() == mode }
        // A fresh root lookup reaches the activity recreated for the new mode.
        awaitCondition("resources with uiMode $uiMode") { root ->
            root.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == uiMode
        }
    }

    private fun awaitFirstExercise(expected: String) {
        awaitCondition("first exercise is $expected") { root ->
            val list = TreeIterables.breadthFirstViewTraversal(root)
                .filterIsInstance<RecyclerView>().firstOrNull { it.id == R.id.exercise_list && it.isShown }
            val first = list?.findViewHolderForAdapterPosition(0)?.itemView
            first?.findViewById<TextView>(R.id.exercise_name)?.text?.toString() == expected
        }
    }

    private fun awaitView(matcher: Matcher<View>) = awaitCondition("$matcher") { root ->
        TreeIterables.breadthFirstViewTraversal(root).any { matcher.matches(it) && isDisplayed().matches(it) }
    }

    private fun awaitCondition(description: String, condition: (View) -> Boolean) {
        onView(isRoot()).perform(object : ViewAction {
            override fun getConstraints(): Matcher<View> = isRoot()
            override fun getDescription() = "Wait for $description"
            override fun perform(uiController: UiController, view: View) {
                val deadline = SystemClock.uptimeMillis() + 10_000
                do {
                    if (condition(view)) return
                    uiController.loopMainThreadForAtLeast(50)
                } while (SystemClock.uptimeMillis() < deadline)
                throw AssertionError("Timed out waiting for $description")
            }
        })
    }
}
