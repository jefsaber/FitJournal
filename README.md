# FitJournal — Native Android Workout Tracker

**Course project** · Kotlin · XML Views / RecyclerView · MVVM · Room · Flow · Paging 3 · Preferences DataStore

Deadline: **26 November 2026** · Group: **4 developers**

> **Status: exercise Add/Edit and workout management implemented**. Navigation, database contracts, the initial exercise catalogue, JSON import, and the preferences repository are present. Exercises can be created and edited with validation and draft restoration. Workouts can be created, edited, and deleted, and the workout history can be searched and filtered by status and date. Catalogue filter/sort controls, workout logging (entries), charts, and settings UI remain to be completed.

## Requirements / setup

- Android Studio with SDK API 36 installed
- JDK 21 installed; Java and Kotlin target JVM 21 using `jvmToolchain(21)`
- Gradle 8.13 / Android Gradle plugin 8.13.2
- Android emulator / device API 26+

### Open the project

1. Unzip or clone the project and choose **Open** on the `FitJournal/` folder in Android Studio.
2. **Bootstrap the official Gradle wrapper once:** the starter contains wrapper properties but not the executable wrapper files (the creation environment could not download them). On **Windows**, run `powershell -ExecutionPolicy Bypass -File tools\bootstrap-gradle-wrapper.ps1` in the project directory. On **macOS/Linux**, run `bash tools/bootstrap-gradle-wrapper.sh`. These scripts download the *official* Gradle 8.13 wrapper scripts and binary from the pinned Gradle GitHub tag; inspect the scripts before running them. Commit the resulting `gradlew`, `gradlew.bat`, and `gradle/wrapper/gradle-wrapper.jar` files so that teammates will not need to bootstrap again. Alternatively, with an installed `gradle` executable, run `gradle wrapper --gradle-version 8.13`.
3. Sync Gradle, then **Run > Run 'app'**.
4. The first launch imports 24 exercises from `app/src/main/assets/exercises.json` and shows them in the **Exercises** catalogue. The import does not overwrite existing entries.

> **Latest verification:** `:app:assembleDebug` succeeded with JVM 21, all 24 JUnit tests passed, and all 3 Espresso tests passed on the API 37 emulator.

## Architecture

See [docs/architecture.md](docs/architecture.md) for the diagram and Room relationships.

- `core/data/entities` — `ExerciseEntity`, `WorkoutEntity`, `WorkoutEntryEntity`.
- `core/data/dao` — Room DAO interfaces.
- `core/data/repositories` — repository boundaries.
- `core/preferences` — DataStore API for theme, weight unit, default sort.
- `core/data/ExerciseSeedImporter` — once-only JSON initialization, idempotent insertion.
- `features/...` — screens and ViewModels by responsibility.

## Current screens (7 navigation destinations)

1. Exercise catalogue — initial Paging 3 list + search (scaffold).
2. Add/edit exercise — implemented by Developer 1: validation, Room persistence, loading/error states, and draft restoration.
3. Workout history — implemented by Developer 2: search, status and date filters, live counts, and empty/no-match states.
4. Add/edit workout — implemented by Developer 2: validation, date picker, completion status, notes, deletion, and draft restoration.
5. Workout details/logging — TODO developer 3.
6. Progress dashboard — TODO developer 3.
7. Settings — TODO developer 4.

Navigation uses a four-tab `BottomNavigationView` and three child destinations.

## App design

The app uses a shared **Material 3** theme with emerald accents, warm neutral surfaces, rounded cards, consistent typography, and custom vector icons. Light and dark palettes follow the device theme automatically; an in-app theme preference control is still pending.

- The branded app bar provides a back button on detail/form screens. Bottom navigation appears on the four main screens and hides while the keyboard is open.
- The exercise catalogue includes a collapsing feature banner, search with a clear action, a live library count, tracking-type labels, and styled loading, empty, and retry states.
- Add/Edit Exercise groups fields into basics, tracking, and coaching notes, with inline validation and automatic scrolling to the first invalid field.
- Workout history uses the same hero, search field, and card styling, with filter chips, **Completed**/**Planned** status badges, a notes preview, and an edit action on each card.
- Add/Edit Workout follows the exercise form layout: session basics, status, and notes cards with inline validation and a confirmed delete action.
- Unfinished features are clearly marked **Coming soon**, with a working shortcut to the exercise library.
- The shared shell handles system-bar, display-cutout, and keyboard insets. The launcher icon matches the in-app branding.

Shared styles live in [themes.xml](app/src/main/res/values/themes.xml), with palette tokens in [colors.xml](app/src/main/res/values/colors.xml) and [night colors](app/src/main/res/values-night/colors.xml). The UI remains XML Views with view binding.

## Exercise management — Developer 1

### Add and edit exercises

1. Open the **Exercises** tab and tap **Add exercise**.
2. Enter an exercise name and muscle group/category, select **Repetitions and weight** or **Duration**, and optionally enter a description.
3. Tap **Save exercise** to persist the exercise in Room and return to the catalogue.
4. Tap an existing catalogue row to load and edit that exercise.
5. Tap **Cancel** to leave without saving.

Names must contain **2–80 characters after trimming**. The category must not be blank, and the tracking type must be one of the two supported options. Validation errors appear beside the relevant fields.

The form preserves drafts across screen recreation using a ViewModel and `SavedStateHandle`. Save failures retain the draft for retry; load failures offer **Retry**, and missing exercises show an explanatory message. Save is disabled while loading or saving to prevent repeated submissions.

Editing preserves the exercise ID and unique `code`, including for seeded exercises. New exercises receive a UUID code. The existing schema uses `category` and `measurementType`; this workflow does not change the shared database schema.

### Implementation

- [ExerciseFormFragment](app/src/main/java/com/fitjournal/app/features/exercises/ExerciseFormFragment.kt) — view binding, field errors, and navigation.
- [ExerciseFormViewModel](app/src/main/java/com/fitjournal/app/features/exercises/ExerciseFormViewModel.kt) — draft state, loading, validation, and repository calls.
- [Form layout](app/src/main/res/layout/fragment_exercise_form.xml) — scrollable XML form.
- [ExerciseValidator](app/src/main/java/com/fitjournal/app/core/domain/Validators.kt) — validation shared by the form and unit tests.
- [ExerciseRepository](app/src/main/java/com/fitjournal/app/core/data/repositories/ExerciseRepository.kt) — existing Room persistence boundary.

**Remaining Developer 1 work:** expose category filtering and sorting in the catalogue and add dedicated JSON duplicate-prevention tests. The Add/Edit workflow is complete; the full exercise module still has these remaining tasks.

## Workout management — Developer 2

Branch: `feature/workouts`.

### Create, edit, and delete workouts

1. Open the **Workouts** tab and tap **Add workout**.
2. Enter a title. The session date defaults to **today**; tap the date field or calendar icon to choose another day.
3. Optionally switch on **Mark as completed** and add session notes.
4. Tap **Save workout** to persist it in Room and return to the history.
5. Tap the **edit** (pencil) button on a workout card to change it, or tap **Delete workout** in the form and confirm. Deleting a workout also removes its logged entries (cascading foreign key).
6. Tapping the card itself opens the workout details screen (Developer 3) with the `workoutId` argument.

Validation rules:

- The title must contain **2–80 characters after trimming**.
- The date is required and must fall between **1 January 2000** and **one year from today**; the date picker enforces the same range.
- A workout marked **completed cannot be dated in the future**; planned sessions can.
- Notes are optional and limited to **500 characters** (with a live counter).

Like the exercise form, the workout form keeps its draft across rotation and process recreation using a ViewModel and `SavedStateHandle`. Save and delete failures keep the draft and show a message; load failures offer **Retry**; missing workouts show an explanatory message. Buttons are disabled while loading, saving, or deleting to prevent repeated submissions.

### Proper dates

`sessionDate` stores the epoch milliseconds of the **local start of the chosen day**. All conversions are in [WorkoutDates](app/src/main/java/com/fitjournal/app/core/domain/WorkoutDates.kt), including the UTC-midnight values used by `MaterialDatePicker`, so a chosen day never shifts across time zones or daylight-saving changes. Dates are displayed in the device's locale: the full date in the form (for example, *Sunday, November 22, 2026*) and **Today** / **Yesterday** / **Tomorrow** or a medium date in the history.

### History search and filters

- **Search** matches the workout title or notes.
- **Status:** All, Planned, or Completed.
- **Date:** Any time, Upcoming (today onwards, soonest first), Last 7 days, or Last 30 days (both including today).
- Filters combine, survive rotation, and update live from Room through Flow. The header shows *N workouts logged* or *Showing X of Y workouts*. An empty history and a filter with no matches show different messages, and the latter offers **Clear filters**.

### Implementation

- [WorkoutsFragment](app/src/main/java/com/fitjournal/app/features/workouts/WorkoutsFragment.kt) / [WorkoutsViewModel](app/src/main/java/com/fitjournal/app/features/workouts/WorkoutsViewModel.kt) — history list, filter state, and empty/no-match states.
- [WorkoutAdapter](app/src/main/java/com/fitjournal/app/features/workouts/WorkoutAdapter.kt) — workout cards with relative dates, status badges, and edit actions.
- [WorkoutFormFragment](app/src/main/java/com/fitjournal/app/features/workouts/WorkoutFormFragment.kt) / [WorkoutFormViewModel](app/src/main/java/com/fitjournal/app/features/workouts/WorkoutFormViewModel.kt) — form, date picker, delete confirmation, and draft state.
- [WorkoutValidator](app/src/main/java/com/fitjournal/app/core/domain/Validators.kt) — title, date, and notes rules shared by the form and tests.
- [WorkoutDates / WorkoutHistoryFilter](app/src/main/java/com/fitjournal/app/core/domain/WorkoutDates.kt) — date conversion and filter date windows.
- [WorkoutDao](app/src/main/java/com/fitjournal/app/core/data/dao/WorkoutDao.kt) / [WorkoutRepository](app/src/main/java/com/fitjournal/app/core/data/repositories/WorkoutRepository.kt) — filtered history query, lookup by ID, and count. The `workouts` table schema and navigation IDs are unchanged; the `workoutFormFragment` destination gained an optional `workoutId` argument.

## Deliverables / tracking

See [docs/team-plan.md](docs/team-plan.md), [CONTRIBUTING.md](CONTRIBUTING.md), and [docs/debugging-log.md](docs/debugging-log.md).

## Tests

From an Android Studio terminal after generating the wrapper:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest  # requires running emulator/device
```

On Windows PowerShell, use `.\gradlew.bat` in place of `./gradlew`.

The current suite contains **24 JUnit tests** and **3 Espresso tests**:

- [ExerciseFormViewModelTest](app/src/test/java/com/fitjournal/app/ExerciseFormViewModelTest.kt): 8 tests covering validation boundaries, supported tracking types, invalid submissions, trimmed creation, repeated save taps, editing without changing identity, restored drafts, save retry, and missing records.
- [WorkoutFormViewModelTest](app/src/test/java/com/fitjournal/app/WorkoutFormViewModelTest.kt): 8 tests covering the default date, invalid titles, trimmed creation stored at the local start of day, repeated save taps, completed-in-future rejection, editing the same record, restored drafts, save retry, deletion, and missing records.
- [WorkoutHistoryTest](app/src/test/java/com/fitjournal/app/WorkoutHistoryTest.kt): 5 tests covering workout validation boundaries, date round-trips (including a daylight-saving day), filter date windows, combined search/status/period filtering with ordering and filter restoration, and empty versus no-match states. Uses the in-memory [MemoryWorkoutDao](app/src/test/java/com/fitjournal/app/MemoryWorkoutDao.kt).
- [ValidatorsTest](app/src/test/java/com/fitjournal/app/ValidatorsTest.kt): 3 existing exercise/workout validation and volume calculation tests.
- [ExerciseFormTest](app/src/androidTest/java/com/fitjournal/app/ExerciseFormTest.kt): creates an exercise, verifies draft restoration after activity recreation, finds it in the catalogue, edits it, and verifies its ID and code are preserved. It removes only the records it creates.
- [WorkoutFormTest](app/src/androidTest/java/com/fitjournal/app/WorkoutFormTest.kt): creates a completed workout, verifies draft restoration after activity recreation, finds it with the Completed and Last 7 days filters, checks that the Planned filter hides it, edits it while preserving its ID, and deletes it through the confirmation dialog. It removes only the records it creates.
- [MainActivityTest](app/src/androidTest/java/com/fitjournal/app/MainActivityTest.kt): existing catalogue visibility smoke test.

All 24 JUnit tests and all 3 Espresso tests passed during verification. The team still needs tests for the remaining modules and 2 documented real debugging cases.

## GitHub

1. Create a GitHub repository `FitJournal` (or use the existing one).
2. Push the starter to the repository's default branch following the steps in your ChatGPT reply.
3. Give all four members **Write** access in GitHub Settings > Collaborators.
4. Protect `main` through Settings > Branches / Rulesets and require Pull Request review.
5. Each teammate creates their own `feature/...` branch.

## Team contributions

Before submission, list all four students, GitHub usernames, owned modules and significant PR URLs here.
