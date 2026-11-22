package com.fitjournal.app

import android.os.SystemClock
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.espresso.util.TreeIterables
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fitjournal.app.core.domain.WorkoutDates
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutFormTest {
    @Test fun createRestoreFilterEditAndDeleteWorkout() {
        val application = ApplicationProvider.getApplicationContext<FitJournalApplication>()
        val repository = application.container.workouts
        val title = "Test workout " + UUID.randomUUID().toString().take(8)
        val editedTitle = "$title updated"
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                onView(withId(R.id.workoutsFragment)).perform(click())
                awaitView(withId(R.id.add_workout_button))
                onView(withId(R.id.add_workout_button)).perform(click())

                // New workouts default to today; fill the rest of the form.
                onView(withId(R.id.workout_title_input)).perform(scrollTo(), replaceText(title), closeSoftKeyboard())
                onView(withId(R.id.workout_completed_switch)).perform(scrollTo(), click())
                onView(withId(R.id.workout_notes_input)).perform(scrollTo(), replaceText("Felt strong"), closeSoftKeyboard())

                // The draft survives activity recreation.
                scenario.recreate()
                onView(withId(R.id.workout_title_input)).check(matches(withText(title)))
                onView(withId(R.id.workout_completed_switch)).check(matches(isChecked()))
                onView(withId(R.id.workout_notes_input)).check(matches(withText("Felt strong")))
                onView(withId(R.id.save_workout_button)).perform(scrollTo(), click())

                // It appears in history and matches the Completed + Last 7 days filters.
                awaitView(withId(R.id.workout_list))
                onView(withId(R.id.workout_search_input)).perform(replaceText(title), closeSoftKeyboard())
                onView(withId(R.id.status_completed)).perform(click())
                onView(withId(R.id.period_last_7)).perform(click())
                awaitView(allOf(withId(R.id.workout_name), withText(title)))
                val created = runBlocking { repository.history.first().single { it.title == title } }
                assertTrue(created.isCompleted)
                assertEquals("Felt strong", created.notes)
                assertEquals(LocalDate.now(), WorkoutDates.toLocalDate(created.sessionDate))

                // The Planned filter hides it.
                onView(withId(R.id.status_planned)).perform(click())
                awaitView(withId(R.id.workouts_no_matches))
                onView(withId(R.id.clear_filters_button)).perform(click())
                onView(withId(R.id.workout_search_input)).perform(replaceText(title), closeSoftKeyboard())

                // Edit keeps the same record.
                awaitView(withContentDescription("Edit $title"))
                onView(withContentDescription("Edit $title")).perform(click())
                awaitView(allOf(withId(R.id.workout_title_input), withText(title)))
                onView(withId(R.id.workout_title_input)).perform(scrollTo(), replaceText(editedTitle), closeSoftKeyboard())
                onView(withId(R.id.save_workout_button)).perform(scrollTo(), click())
                awaitView(withId(R.id.workout_list))
                onView(withId(R.id.workout_search_input)).perform(replaceText(editedTitle), closeSoftKeyboard())
                awaitView(allOf(withId(R.id.workout_name), withText(editedTitle)))
                val updated = runBlocking { repository.history.first().single { it.title == editedTitle } }
                assertEquals(created.id, updated.id)

                // Delete after confirming.
                onView(withContentDescription("Edit $editedTitle")).perform(click())
                awaitView(allOf(withId(R.id.workout_title_input), withText(editedTitle)))
                onView(withId(R.id.delete_workout_button)).perform(scrollTo(), click())
                onView(withText(R.string.workout_delete_confirm)).inRoot(isDialog()).perform(click())
                awaitView(withId(R.id.workouts_no_matches))
                assertNull(runBlocking { repository.find(created.id) })
            }
        } finally {
            // Remove only this test's records, preserving any existing emulator data.
            runBlocking {
                repository.history.first().filter { it.title == title || it.title == editedTitle }
                    .forEach { repository.delete(it) }
            }
        }
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
