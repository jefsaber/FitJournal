package com.fitjournal.app

import android.os.SystemClock
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
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

@RunWith(AndroidJUnit4::class)
class ExerciseFormTest {
    @Test fun addExercisePreservesDraftAcrossRecreationAndCanBeEdited() {
        val application = ApplicationProvider.getApplicationContext<FitJournalApplication>()
        val repository = application.container.exercises
        val name = "Test exercise " + UUID.randomUUID().toString().take(8)
        val editedName = "$name updated"
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                onView(withId(R.id.add_exercise_button)).perform(click())
                onView(withId(R.id.name_input)).perform(replaceText(name), closeSoftKeyboard())
                onView(withId(R.id.category_input)).perform(replaceText("Core"), closeSoftKeyboard())
                onView(withId(R.id.tracking_duration)).perform(scrollTo(), click())
                onView(withId(R.id.description_input)).perform(replaceText("Hold steady"), closeSoftKeyboard())
                scenario.recreate()
                onView(withId(R.id.name_input)).check(matches(withText(name)))
                onView(withId(R.id.category_input)).check(matches(withText("Core")))
                onView(withId(R.id.tracking_duration)).check(matches(isChecked()))
                onView(withId(R.id.description_input)).check(matches(withText("Hold steady")))
                onView(withId(R.id.save_exercise_button)).perform(scrollTo(), click())
                awaitView(withId(R.id.exercise_list))
                onView(withId(R.id.search_input)).perform(replaceText(name), closeSoftKeyboard())
                awaitView(allOf(withId(R.id.exercise_name), withText(name)))

                val created = runBlocking { repository.all.first().single { it.name == name } }
                awaitCatalogueIdle()
                onView(withContentDescription("Edit $name")).perform(click())
                awaitView(allOf(withId(R.id.name_input), withText(name)))
                onView(withId(R.id.tracking_duration)).check(matches(isChecked()))
                onView(withId(R.id.description_input)).check(matches(withText("Hold steady")))
                onView(withId(R.id.name_input)).perform(replaceText(editedName), closeSoftKeyboard())
                onView(withId(R.id.save_exercise_button)).perform(scrollTo(), click())
                awaitView(withId(R.id.exercise_list))
                onView(withId(R.id.search_input)).perform(replaceText(editedName), closeSoftKeyboard())
                awaitView(allOf(withId(R.id.exercise_name), withText(editedName)))
                val updated = runBlocking { repository.all.first().single { it.name == editedName } }
                assertEquals(created.id, updated.id)
                assertEquals(created.code, updated.code)
            }
        } finally {
            // Remove only this test's records, preserving any existing emulator data.
            runBlocking {
                repository.all.first().filter { it.name == name || it.name == editedName }
                    .forEach { repository.delete(it) }
            }
        }
    }

    private fun awaitCatalogueIdle() {
        onView(withId(R.id.exercise_list)).perform(object : ViewAction {
            override fun getConstraints(): Matcher<View> = isAssignableFrom(RecyclerView::class.java)
            override fun getDescription() = "Wait for catalogue layout and item animations"
            override fun perform(uiController: UiController, view: View) {
                val list = view as RecyclerView
                val deadline = SystemClock.uptimeMillis() + 10_000
                while (list.isComputingLayout || list.hasPendingAdapterUpdates() ||
                    list.itemAnimator?.isRunning == true) {
                    check(SystemClock.uptimeMillis() < deadline) { "Catalogue did not become idle" }
                    uiController.loopMainThreadForAtLeast(50)
                }
            }
        })
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
                val fields = TreeIterables.breadthFirstViewTraversal(view)
                    .filterIsInstance<android.widget.TextView>()
                    .joinToString { "${it.id}: ${it.text} (shown=${it.isShown})" }
                throw AssertionError("Timed out waiting for $matcher. Fields: $fields")
            }
        })
    }
}
