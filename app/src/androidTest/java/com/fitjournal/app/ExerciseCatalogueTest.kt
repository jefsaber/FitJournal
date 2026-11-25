package com.fitjournal.app

import android.os.SystemClock
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isPlatformPopup
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.espresso.util.TreeIterables
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fitjournal.app.core.data.entities.ExerciseEntity
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matcher
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExerciseCatalogueTest {
    @Test fun categoryAndSearchCombineSortPersistsAndControlsRestore() {
        val app = ApplicationProvider.getApplicationContext<FitJournalApplication>()
        val settings = app.container.settings
        val previous = runBlocking { settings.settings.first() }
        val prefix = "Catalogue " + UUID.randomUUID().toString().take(8)
        val category = "Core " + prefix.takeLast(8)
        val alpha = "$prefix Alpha"
        val zulu = "$prefix Zulu"
        val ids = mutableListOf<Long>()
        try {
            runBlocking {
                app.container.seedImporter.importOnce()
                settings.setExerciseSort("NAME_DESC")
                listOf(
                    ExerciseEntity(code = UUID.randomUUID().toString(), name = alpha, category = category),
                    ExerciseEntity(code = UUID.randomUUID().toString(), name = zulu, category = category),
                    ExerciseEntity(code = UUID.randomUUID().toString(), name = "$prefix Middle", category = "Back")
                ).forEach { ids += app.container.exercises.create(it) }
            }
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                awaitCondition("saved descending order") { root ->
                    root.findViewById<TextView>(R.id.catalogue_sort)?.text?.toString() ==
                        app.getString(R.string.settings_sort_name_desc)
                }
                onView(withId(R.id.search_input)).perform(replaceText(prefix), closeSoftKeyboard())
                awaitFirst(zulu)
                onView(withId(R.id.category_filter)).perform(click())
                onView(withText(category)).inRoot(isPlatformPopup()).perform(click())
                awaitCondition("only the selected category") { root ->
                    val list = root.findViewById<RecyclerView>(R.id.exercise_list)
                    list?.adapter?.itemCount == 2 &&
                        list.findViewHolderForAdapterPosition(0)?.itemView
                            ?.findViewById<TextView>(R.id.exercise_name)?.text?.toString() == zulu
                }
                onView(withId(R.id.catalogue_sort)).perform(click())
                onView(withText(R.string.settings_sort_name_asc)).inRoot(isPlatformPopup()).perform(click())
                awaitFirst(alpha)
                assertEquals("NAME_ASC", runBlocking { settings.settings.first().exerciseSort })

                scenario.recreate()
                onView(withId(R.id.search_input)).check(matches(withText(prefix)))
                onView(withId(R.id.category_filter)).check(matches(withText(category)))
                awaitFirst(alpha)

                onView(withId(R.id.settingsFragment)).perform(click())
                awaitCondition("Settings reflects catalogue choice") { root ->
                    root.findViewById<View>(R.id.sort_name_asc)?.isShown == true
                }
                onView(withId(R.id.sort_name_asc)).check(matches(isChecked()))
                onView(withId(R.id.sort_name_desc)).perform(click())
                onView(withId(R.id.exercisesFragment)).perform(click())
                awaitFirst(zulu)
                onView(withId(R.id.category_filter)).check(matches(withText(category)))
                onView(withId(R.id.catalogue_sort)).check(matches(withText(R.string.settings_sort_name_desc)))
            }
        } finally {
            runBlocking {
                ids.forEach { id -> app.container.exercises.find(id)?.let { app.container.exercises.delete(it) } }
                settings.setExerciseSort(previous.exerciseSort)
            }
        }
    }

    private fun awaitFirst(name: String) = awaitCondition("first exercise $name") { root ->
        val list = root.findViewById<RecyclerView>(R.id.exercise_list)
        list?.findViewHolderForAdapterPosition(0)?.itemView
            ?.findViewById<TextView>(R.id.exercise_name)?.text?.toString() == name &&
            list.itemAnimator?.isRunning != true && !list.hasPendingAdapterUpdates()
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
                val text = TreeIterables.breadthFirstViewTraversal(view).filterIsInstance<TextView>()
                    .joinToString { it.text }
                throw AssertionError("Timed out waiting for $description. Text: $text")
            }
        })
    }
}
