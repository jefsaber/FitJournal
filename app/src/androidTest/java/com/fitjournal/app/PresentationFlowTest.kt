package com.fitjournal.app

import android.os.SystemClock
import android.view.View
import android.widget.Spinner
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.espresso.util.TreeIterables
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Presentation route through every destination, using the actual forms and Room-backed screens. */
@RunWith(AndroidJUnit4::class)
class PresentationFlowTest {
    @Test fun exerciseWorkoutLoggingProgressSettingsAndRelaunch() {
        val app = ApplicationProvider.getApplicationContext<FitJournalApplication>()
        val previous = runBlocking { app.container.settings.settings.first() }
        val suffix = UUID.randomUUID().toString().take(8)
        val exerciseName = "AA Presentation Press $suffix"
        val workoutName = "Presentation Session $suffix"
        try {
            runBlocking {
                app.container.settings.setWeightUnit("kg")
                app.container.settings.setExerciseSort("NAME_ASC")
            }
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.recreate()
                onView(withId(R.id.add_exercise_button)).perform(click())
                onView(withId(R.id.name_input)).perform(scrollTo(), clearText(), typeText(exerciseName), closeSoftKeyboard())
                onView(withId(R.id.category_input)).perform(scrollTo(), clearText(), typeText("Chest"), closeSoftKeyboard())
                scenario.recreate()
                onView(withId(R.id.name_input)).check(matches(withText(exerciseName)))
                onView(withId(R.id.save_exercise_button)).perform(scrollTo(), click())
                awaitView(withId(R.id.exercise_list))
                onView(withId(R.id.workoutsFragment)).perform(click())
                awaitView(withId(R.id.add_workout_button))
                scenario.recreate()
                onView(withId(R.id.add_workout_button)).perform(click())
                onView(withId(R.id.workout_title_input)).perform(scrollTo(), clearText(), typeText(workoutName), closeSoftKeyboard())
                onView(withId(R.id.workout_completed_switch)).perform(scrollTo(), click())
                scenario.recreate()
                onView(withId(R.id.workout_title_input)).check(matches(withText(workoutName)))
                onView(withId(R.id.save_workout_button)).perform(scrollTo(), click())
                awaitView(withId(R.id.workout_list))
                onView(withId(R.id.workout_search_input)).perform(replaceText(workoutName), closeSoftKeyboard())
                awaitView(allOf(withId(R.id.workout_name), withText(workoutName)))
                onView(allOf(withId(R.id.workout_name), withText(workoutName))).perform(click())
                awaitView(withId(R.id.add_entry_button))
                scenario.recreate()
                onView(withId(R.id.add_entry_button)).perform(click())
                onView(withId(R.id.entry_exercise_picker)).inRoot(isDialog()).perform(object : ViewAction {
                    override fun getConstraints(): Matcher<View> = isAssignableFrom(Spinner::class.java)
                    override fun getDescription() = "Select the presentation exercise"
                    override fun perform(uiController: UiController, view: View) {
                        val picker = view as Spinner
                        picker.setSelection((0 until picker.adapter.count).first {
                            picker.adapter.getItem(it) == exerciseName
                        })
                        uiController.loopMainThreadUntilIdle()
                    }
                })
                onView(withId(R.id.entry_sets_input)).inRoot(isDialog()).perform(clearText(), typeText("3"), closeSoftKeyboard())
                onView(withId(R.id.entry_repetitions_input)).inRoot(isDialog()).perform(clearText(), typeText("8"), closeSoftKeyboard())
                onView(withId(R.id.entry_weight_input)).inRoot(isDialog()).perform(clearText(), typeText("40"), closeSoftKeyboard())
                scenario.recreate()
                onView(withId(R.id.entry_weight_input)).inRoot(isDialog()).check(matches(withText("40")))
                onView(withText(R.string.workout_entry_save)).inRoot(isDialog()).perform(click())
                awaitView(withText("3 sets · 8 reps · 40 kg"))
                pressBack()
                onView(withId(R.id.progressFragment)).perform(click())
                awaitView(allOf(withId(R.id.progress_record_name), withText(exerciseName)), displayed = false)
                onView(allOf(withId(R.id.progress_record_name), withText(exerciseName))).perform(scrollTo())
                scenario.recreate()
                onView(allOf(withId(R.id.progress_record_name), withText(exerciseName))).check(matches(withText(exerciseName)))
                onView(withId(R.id.settingsFragment)).perform(click())
                awaitView(withId(R.id.unit_lb))
                onView(withId(R.id.unit_lb)).perform(click())
                scenario.recreate()
                onView(withId(R.id.unit_lb)).check(matches(isChecked()))
                onView(withId(R.id.workoutsFragment)).perform(click())
                onView(withId(R.id.workout_search_input)).perform(replaceText(workoutName), closeSoftKeyboard())
                awaitView(allOf(withId(R.id.workout_name), withText(workoutName)))
                onView(allOf(withId(R.id.workout_name), withText(workoutName))).perform(click())
                awaitView(withText("3 sets · 8 reps · 88.18 lb"))
            }

            // A new activity reads the persisted Room and DataStore values.
            ActivityScenario.launch(MainActivity::class.java).use {
                onView(withId(R.id.workoutsFragment)).perform(click())
                awaitView(withId(R.id.workout_search_input))
                onView(withId(R.id.workout_search_input)).perform(replaceText(workoutName), closeSoftKeyboard())
                awaitView(allOf(withId(R.id.workout_name), withText(workoutName)))
                onView(allOf(withId(R.id.workout_name), withText(workoutName))).perform(click())
                awaitView(withText("3 sets · 8 reps · 88.18 lb"))
                val workout = runBlocking { app.container.workouts.history.first().single { it.title == workoutName } }
                val entry = runBlocking { app.container.entries.byWorkout(workout.id).first().single() }
                assertEquals(40.0, entry.weightKg, 0.000001)
                assertEquals("lb", runBlocking { app.container.settings.settings.first().weightUnit })
            }
        } finally {
            runBlocking {
                app.container.workouts.history.first().filter { it.title == workoutName }
                    .forEach { app.container.workouts.delete(it) }
                app.container.exercises.all.first().filter { it.name == exerciseName }
                    .forEach { app.container.exercises.delete(it) }
                app.container.settings.setWeightUnit(previous.weightUnit)
                app.container.settings.setExerciseSort(previous.exerciseSort)
            }
        }
    }

    private fun awaitView(matcher: Matcher<View>, displayed: Boolean = true) {
        onView(isRoot()).perform(object : ViewAction {
            override fun getConstraints(): Matcher<View> = isRoot()
            override fun getDescription() = "Wait for $matcher"
            override fun perform(uiController: UiController, view: View) {
                val deadline = SystemClock.uptimeMillis() + 10_000
                do {
                    if (TreeIterables.breadthFirstViewTraversal(view).any {
                            matcher.matches(it) && (!displayed || isDisplayed().matches(it))
                        }) return
                    uiController.loopMainThreadForAtLeast(50)
                } while (SystemClock.uptimeMillis() < deadline)
                throw AssertionError("Timed out waiting for $matcher")
            }
        })
    }
}
