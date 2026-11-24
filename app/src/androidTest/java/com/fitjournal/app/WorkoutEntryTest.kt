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
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.util.TreeIterables
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fitjournal.app.core.data.entities.ExerciseEntity
import com.fitjournal.app.core.data.entities.WorkoutEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class WorkoutEntryTest {
    @Test fun addRestoreEditAndDeleteStrengthEntry() {
        val app = ApplicationProvider.getApplicationContext<FitJournalApplication>()
        val title = "Entry test " + UUID.randomUUID().toString().take(8)
        var workoutId = 0L
        val previousUnit = runBlocking { app.container.settings.settings.first().weightUnit }
        try {
            val strengthExercise = runBlocking {
                app.container.settings.setWeightUnit("kg")
                app.container.seedImporter.importOnce()
                app.container.exercises.all.first().first { it.measurementType == "REPS_WEIGHT" }
            }
            workoutId = runBlocking {
                app.container.workouts.create(
                    WorkoutEntity(title = title, sessionDate = System.currentTimeMillis(), isCompleted = true)
                )
            }

            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                onView(withId(R.id.workoutsFragment)).perform(click())
                onView(withId(R.id.workout_search_input)).perform(replaceText(title))
                awaitView(allOf(withId(R.id.workout_name), withText(title)))
                onView(allOf(withId(R.id.workout_name), withText(title))).perform(click())
                awaitView(withId(R.id.add_entry_button))
                onView(withId(R.id.add_entry_button)).perform(click())
                onView(withId(R.id.entry_exercise_picker)).inRoot(isDialog())
                    .perform(selectExercise(strengthExercise))
                onView(withId(R.id.entry_sets_input)).inRoot(isDialog()).perform(replaceText("3"))
                onView(withId(R.id.entry_repetitions_input)).inRoot(isDialog()).perform(replaceText("8"))
                onView(withId(R.id.entry_weight_input)).inRoot(isDialog()).perform(replaceText("40"))

                // The open editor and its unsaved values survive activity recreation.
                scenario.recreate()
                onView(withId(R.id.entry_sets_input)).inRoot(isDialog()).check(matches(withText("3")))
                onView(withId(R.id.entry_repetitions_input)).inRoot(isDialog()).check(matches(withText("8")))
                onView(withId(R.id.entry_weight_input)).inRoot(isDialog()).check(matches(withText("40")))
                onView(withText(R.string.workout_entry_save)).inRoot(isDialog()).perform(click())

                awaitView(withText(strengthExercise.name))
                awaitView(withText("3 sets · 8 reps · 40 kg"))
                val created = awaitEntry(app, workoutId) { it.weightKg == 40.0 }
                assertEquals(3, created.sets)
                assertEquals(8, created.repetitions)

                onView(withContentDescription("Edit ${strengthExercise.name} entry")).perform(click())
                onView(withId(R.id.entry_weight_input)).inRoot(isDialog()).perform(replaceText("45"))
                onView(withText(R.string.workout_entry_save)).inRoot(isDialog()).perform(click())
                awaitView(withText("3 sets · 8 reps · 45 kg"))
                val edited = awaitEntry(app, workoutId) { it.weightKg == 45.0 }
                assertEquals(created.id, edited.id)

                // The saved Room entry is reflected by the completed-only progress dashboard.
                pressBack()
                onView(withId(R.id.progressFragment)).perform(click())
                awaitView(allOf(withId(R.id.progress_record_name), withText(strengthExercise.name)))

                onView(withId(R.id.workoutsFragment)).perform(click())
                onView(withId(R.id.workout_search_input)).perform(replaceText(title))
                awaitView(allOf(withId(R.id.workout_name), withText(title)))
                onView(allOf(withId(R.id.workout_name), withText(title))).perform(click())
                awaitView(withContentDescription("Delete ${strengthExercise.name} entry"))
                onView(withContentDescription("Delete ${strengthExercise.name} entry")).perform(click())
                onView(withText(R.string.workout_entry_delete)).inRoot(isDialog()).perform(click())
                awaitView(withId(R.id.workout_details_empty))
                assertTrue(runBlocking { app.container.entries.byWorkout(workoutId).first().isEmpty() })
            }
        } finally {
            runBlocking {
                if (workoutId != 0L) app.container.workouts.history.first()
                    .firstOrNull { it.id == workoutId }?.let { app.container.workouts.delete(it) }
                app.container.settings.setWeightUnit(previousUnit)
            }
        }
    }

    private fun selectExercise(exercise: ExerciseEntity) = object : ViewAction {
        override fun getConstraints(): Matcher<View> = org.hamcrest.Matchers.any(View::class.java)
        override fun getDescription() = "Select ${exercise.name}"
        override fun perform(uiController: UiController, view: View) {
            val spinner = view as Spinner
            val index = (0 until spinner.adapter.count).first { spinner.adapter.getItem(it) == exercise.name }
            spinner.setSelection(index)
            uiController.loopMainThreadUntilIdle()
        }
    }

    private fun awaitEntry(
        app: FitJournalApplication,
        workoutId: Long,
        predicate: (com.fitjournal.app.core.data.entities.WorkoutEntryEntity) -> Boolean
    ): com.fitjournal.app.core.data.entities.WorkoutEntryEntity {
        val deadline = SystemClock.uptimeMillis() + 10_000
        do {
            runBlocking { app.container.entries.byWorkout(workoutId).first().firstOrNull(predicate) }?.let { return it }
            SystemClock.sleep(50)
        } while (SystemClock.uptimeMillis() < deadline)
        throw AssertionError("Timed out waiting for workout entry")
    }

    private fun awaitView(matcher: Matcher<View>) {
        onView(isRoot()).perform(object : ViewAction {
            override fun getConstraints(): Matcher<View> = isRoot()
            override fun getDescription() = "Wait for a matching displayed view"
            override fun perform(uiController: UiController, view: View) {
                val deadline = SystemClock.uptimeMillis() + 10_000
                do {
                    if (TreeIterables.breadthFirstViewTraversal(view).any {
                            matcher.matches(it) && isDisplayed().matches(it)
                        }) return
                    uiController.loopMainThreadForAtLeast(50)
                } while (SystemClock.uptimeMillis() < deadline)
                throw AssertionError("Timed out waiting for $matcher")
            }
        })
    }
}
