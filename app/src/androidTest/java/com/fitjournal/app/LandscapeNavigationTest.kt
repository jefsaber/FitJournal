package com.fitjournal.app

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.SystemClock
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.espresso.util.TreeIterables
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fitjournal.app.core.data.entities.WorkoutEntity
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LandscapeNavigationTest {
    @Test fun rotatedListsKeepSearchFiltersActionsAndRowsVisible() {
        val app = ApplicationProvider.getApplicationContext<FitJournalApplication>()
        val title = "Landscape " + UUID.randomUUID().toString().take(8)
        val workoutId = runBlocking {
            app.container.seedImporter.importOnce()
            app.container.workouts.create(WorkoutEntity(title = title, sessionDate = System.currentTimeMillis()))
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
                awaitLandscapeActivity(scenario)
                awaitView(withId(R.id.category_filter))
                onView(withId(R.id.search_input)).check(matches(isDisplayed()))
                onView(withId(R.id.category_filter)).check(matches(isDisplayed()))
                onView(withId(R.id.catalogue_sort)).check(matches(isDisplayed()))
                onView(withId(R.id.add_exercise_button)).check(matches(isDisplayed()))
                onView(withId(R.id.search_input)).perform(replaceText("bench"), closeSoftKeyboard())
                awaitView(withId(R.id.exercise_name))

                awaitView(allOf(withId(R.id.workoutsFragment), isCompletelyDisplayed()))
                onView(withId(R.id.workoutsFragment)).perform(click())
                awaitView(allOf(withId(R.id.workout_name), withText(title)))
                onView(withId(R.id.workout_search_input)).check(matches(isDisplayed()))
                onView(withId(R.id.status_planned)).check(matches(isDisplayed()))
                onView(withId(R.id.period_last_30)).check(matches(isDisplayed()))
                onView(withId(R.id.add_workout_button)).check(matches(isDisplayed()))
                onView(withId(R.id.add_workout_button)).perform(click())
                awaitView(withId(R.id.workout_title_input))
                onView(withId(R.id.workout_title_input)).perform(scrollTo(), replaceText("Landscape draft"), closeSoftKeyboard())
                pressBack()
                awaitView(withId(R.id.add_workout_button))
                awaitView(allOf(withId(R.id.exercisesFragment), isCompletelyDisplayed()))
                onView(withId(R.id.exercisesFragment)).perform(click())
                onView(withId(R.id.search_input)).check(matches(withText("bench")))
                scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
            }
        } finally {
            runBlocking { app.container.workouts.find(workoutId)?.let { app.container.workouts.delete(it) } }
        }
    }

    private fun awaitLandscapeActivity(scenario: ActivityScenario<MainActivity>) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        do {
            var ready = false
            scenario.onActivity { activity ->
                ready = activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
                    activity.window.decorView.hasWindowFocus()
            }
            if (ready) return
            SystemClock.sleep(50)
        } while (SystemClock.uptimeMillis() < deadline)
        throw AssertionError("The recreated landscape activity did not gain window focus")
    }

    private fun awaitView(matcher: Matcher<View>) {
        onView(isRoot()).perform(object : ViewAction {
            override fun getConstraints(): Matcher<View> = isRoot()
            override fun getDescription() = "Wait for landscape view $matcher"
            override fun perform(uiController: UiController, view: View) {
                val deadline = SystemClock.uptimeMillis() + 10_000
                do {
                    if (view.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
                        TreeIterables.breadthFirstViewTraversal(view).any {
                            matcher.matches(it) && isDisplayed().matches(it)
                        }) return
                    uiController.loopMainThreadForAtLeast(50)
                } while (SystemClock.uptimeMillis() < deadline)
                val text = TreeIterables.breadthFirstViewTraversal(view)
                    .filterIsInstance<android.widget.TextView>().joinToString { it.text }
                throw AssertionError("Landscape control or list row was not accessible: $matcher. Visible hierarchy text: $text")
            }
        })
    }
}
