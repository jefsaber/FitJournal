# FitJournal — Native Android Workout Tracker

**Course project** · Kotlin · XML Views / RecyclerView · MVVM · Room · Flow · Paging 3 · Preferences DataStore

Deadline: **26 November 2026** · Group: **4 developers**

> **Status: exercise Add/Edit, workout management, workout logging, and progress statistics implemented**. Users can create workouts, log strength or timed exercises, edit/delete entries, and review completed-workout volume, a recent-volume chart, and personal records. Settings persist the theme, weight unit, and default exercise order. The catalogue now combines search, muscle-group filtering, and an in-screen sort control synchronized with Settings.

## Requirements / setup

- Android Studio with SDK API 36 installed
- JDK 21 installed; Java and Kotlin target JVM 21 using `jvmToolchain(21)`
- Gradle 8.13 / Android Gradle plugin 8.13.2
- Android emulator / device API 26+

### Open the project

1. Unzip or clone the project and choose **Open** on the `FitJournal/` folder in Android Studio.
2. The pinned official Gradle 8.13 wrapper is included; no system Gradle installation is required.
3. Sync Gradle, then **Run > Run 'app'**.
4. The first launch imports 24 exercises from `app/src/main/assets/exercises.json` and shows them in the **Exercises** catalogue. The import does not overwrite existing entries.

> **Verified 25 November 2026:** the JVM 21 debug build succeeded; **46 local JUnit tests** and **16 instrumentation tests** passed on the API 37 emulator. Instrumentation consists of **9 Espresso UI tests + 7 Room/import tests**, run in a separate validation installation.

## Architecture

See [docs/architecture.md](docs/architecture.md) for the diagram and Room relationships.

- `core/data/entities` — `ExerciseEntity`, `WorkoutEntity`, `WorkoutEntryEntity`.
- `core/data/models` — joined read models such as `LoggedWorkoutEntry`.
- `core/data/dao` — Room DAO interfaces.
- `core/data/repositories` — repository boundaries.
- `core/preferences` — DataStore API for theme, weight unit, default sort.
- `core/data/ExerciseSeedImporter` — once-only JSON initialization, idempotent insertion.
- `features/...` — screens and ViewModels by responsibility.

## Current screens (7 navigation destinations)

1. Exercise catalogue — Paging 3 list with search, live muscle-group filtering, and persistent A–Z/Z–A sorting.
2. Add/edit exercise — implemented by Developer 1: validation, Room persistence, loading/error states, and draft restoration.
3. Workout history — implemented by Developer 2: search, status and date filters, live counts, and empty/no-match states.
4. Add/edit workout — implemented by Developer 2: validation, date picker, completion status, notes, deletion, and draft restoration.
5. Workout details/logging — implemented by Developer 3: exercise selection, type-aware entry CRUD, validation, unit conversion, and restored drafts.
6. Progress dashboard — implemented by Developer 3: completed-workout totals, recent strength-volume chart, and personal records.
7. Settings — implemented by Developer 4: theme, weight unit, and default exercise order.

Navigation uses a four-tab `BottomNavigationView` and three child destinations.

## App design

The app uses a shared **Material 3** theme with emerald accents, warm neutral surfaces, rounded cards, consistent typography, and custom vector icons. Light and dark palettes follow the device theme by default; **Settings > Appearance** can force light or dark, or return to the device theme.

- The branded app bar provides a back button on detail/form screens. Bottom navigation appears on the four main screens and hides while the keyboard is open.
- The exercise catalogue includes a collapsing feature banner, search with a clear action, a live library count, tracking-type labels, and styled loading, empty, and retry states.
- Add/Edit Exercise groups fields into basics, tracking, and coaching notes, with inline validation and automatic scrolling to the first invalid field.
- Workout history uses the same hero, search field, and card styling, with filter chips, **Completed**/**Planned** status badges, a notes preview, and an edit action on each card.
- Add/Edit Workout follows the exercise form layout: session basics, status, and notes cards with inline validation and a confirmed delete action.
- Workout details use the same cards and typography, with a type-aware Material entry dialog for strength and duration exercises.
- Progress presents completed-workout totals, recent strength volume, and personal records in the selected weight unit.
- The shared shell handles system-bar, display-cutout, and keyboard insets. The launcher icon matches the in-app branding.
- Compact landscape catalogue/workout layouts keep controls and list rows on screen. The landscape toolbar is shorter and bottom navigation uses icons with accessible destination labels.

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

### Catalogue filters, sorting, and seed safety

- **Muscle group** lists categories from Room, with an **All groups** option. Category matching and category deduplication ignore case.
- **Sort by** offers **Name A–Z** and **Name Z–A**. It updates the persistent Settings preference, so both screens stay synchronized after restart.
- Search and category selection combine with the selected order and survive activity recreation through `SavedStateHandle`.
- If saving the sort fails, the previous order stays active and an inline message offers retry by choosing an order again.
- JSON import validates supported field types and the shared exercise rules, and requires at least **20 distinct valid codes** before insertion.
- A single Room transaction inserts missing codes with conflict `IGNORE`; the DataStore marker skips later imports. Retrying after a missing marker preserves existing records, edited seeds, and user-created exercises.
- Importer tests use an in-memory Room database and isolated preference files, including malformed rows, duplicate codes, missing-marker retry, and failed-import recovery.

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

## Workout logging and statistics — Developer 3

Branch: `feature/workout-entries`.

### Log workout exercises

1. Tap a workout card in **Workouts** to open its details.
2. Tap **Add exercise to workout** and choose an exercise from the existing catalogue.
3. For a `REPS_WEIGHT` exercise, enter positive sets, repetitions, and weight. For a `DURATION` exercise, enter a positive duration in seconds.
4. Save the entry. Use **Edit** to change its applicable metrics or **Delete** and confirm to remove it.

The entry editor uses `WorkoutDetailsViewModel` and `SavedStateHandle`, so the selected exercise and unsaved values survive activity recreation. Saving and deleting are guarded against repeated taps; validation stays inline, and a failed save keeps the draft open for retry. Room joins entries to exercises for live names and measurement types instead of performing one lookup per row.

Weights are always stored canonically in **kilograms**. The entry editor, saved-entry rows, dashboard totals, recent-volume chart, and strength records react to `SettingsRepository.weightUnit`: pounds are converted at the UI boundary and converted back to kilograms before persistence. Timed entries never contribute to strength volume.

### Progress rules

The dashboard intentionally includes **completed workouts only**. This rule is applied consistently to the completed-workout count, total training volume, recent chart, and personal records.

- Training volume: `sets × repetitions × weightKg`, summed only for strength entries.
- Strength personal record: highest logged weight for that exercise.
- Duration personal record: longest logged duration for that exercise.
- Recent chart: strength volume for the last seven completed workouts, displayed oldest to newest; workouts without strength volume are omitted from the bars.

### Implementation

- [WorkoutDetailsFragment](app/src/main/java/com/fitjournal/app/features/entries/WorkoutDetailsFragment.kt) / [WorkoutDetailsViewModel](app/src/main/java/com/fitjournal/app/features/entries/WorkoutDetailsViewModel.kt) — live workout contents, restored entry drafts, type-aware validation, and entry CRUD.
- [ProgressFragment](app/src/main/java/com/fitjournal/app/features/progress/ProgressFragment.kt) / [ProgressViewModel](app/src/main/java/com/fitjournal/app/features/progress/ProgressViewModel.kt) — completed-only totals, chart points, records, loading, and retry states.
- [WorkoutEntryDao](app/src/main/java/com/fitjournal/app/core/data/dao/WorkoutEntryDao.kt) / [WorkoutEntryRepository](app/src/main/java/com/fitjournal/app/core/data/repositories/WorkoutEntryRepository.kt) — live joined entry projections and persistence operations.
- [WorkoutEntryValidator / ProgressCalculator](app/src/main/java/com/fitjournal/app/core/domain/Validators.kt) — entry validation, volume, duration exclusion, and record calculations.
- [WeightUnits](app/src/main/java/com/fitjournal/app/core/domain/WeightUnits.kt) — canonical kg/lb conversion shared with the saved weight-unit preference.

Developer 3 required no database schema change or migration.

## Settings and integration — Developer 4

Branch: `feature/app-settings`.

### Preferences

| Preference | Control | Stored value | Affects |
| --- | --- | --- | --- |
| Appearance | Device / Light / Dark | `dark_mode` Boolean; removed for **Device** | Whole app, applied before the first screen and on every change |
| Weight unit | Kilograms / Pounds | `weight_unit` = `kg` or `lb` | Entry editor, logged entries, Progress totals, chart, and records |
| Exercise library order | Name A–Z / Name Z–A | `exercise_sort` = `NAME_ASC` or `NAME_DESC` | Exercise catalogue order |

All three are stored in Preferences DataStore and survive restarting the app. The JSON import marker is internal and not a user-facing preference.

### Demonstrate

1. Open **Settings** and choose **Pounds (lb)**. Open **Progress**: totals and records now read `lb`. Logged weights in workout details also show pounds, while Room still stores kilograms.
2. Choose **Name Z–A**, then open **Exercises**: the catalogue re-sorts immediately.
3. Choose **Dark** or **Light**: the app switches theme immediately and keeps it after a restart. **Device** goes back to following the system setting.
4. Force-stop and relaunch the app: all three choices are still selected and applied.

### Design decisions

- **Theme and the device setting.** The stored preference is still a Boolean, but it is absent until the user picks Light or Dark, so a fresh install keeps the original follow-the-device behavior. `FitJournalApplication` reads it once before the first activity starts, so the app does not flash the wrong theme, then applies later changes through `AppCompatDelegate.setDefaultNightMode`.
- **Drafts across theme changes.** A theme change recreates the activity. Every form keeps its draft in `SavedStateHandle`. In-app theme changes happen from Settings, a top-level tab, and the bottom navigation is hidden on form screens. A theme change that reaches an open form is therefore a device theme change, which is a normal recreation covered by the form tests' `scenario.recreate()` checks.
- **Validation.** Setters reject unsupported values with `IllegalArgumentException`. Unknown or unreadable stored values fall back to defaults instead of crashing.
- **Weights.** `WeightConverter` uses the international pound (0.45359237 kg). Conversion happens only at UI boundaries; `weightKg` in Room is always kilograms.
- **Catalogue sort.** `ExerciseListViewModel` combines search, selected category, and the saved order for the existing paged DAO query. Changing the order in either the catalogue or Settings updates the same DataStore preference.

### Implementation

- [SettingsFragment](app/src/main/java/com/fitjournal/app/features/settings/SettingsFragment.kt) / [SettingsViewModel](app/src/main/java/com/fitjournal/app/features/settings/SettingsViewModel.kt): toggle groups that reflect saved state, plus loading and save-error states.
- [SettingsRepository](app/src/main/java/com/fitjournal/app/core/preferences/SettingsRepository.kt): validated setters, defaults, and an injectable `DataStore` for tests.
- [ThemePreference](app/src/main/java/com/fitjournal/app/core/preferences/ThemePreference.kt) / [FitJournalApplication](app/src/main/java/com/fitjournal/app/FitJournalApplication.kt): maps the preference to night modes and applies it at startup and on change.
- [ExerciseListViewModel](app/src/main/java/com/fitjournal/app/features/exercises/ExerciseListViewModel.kt): connects the saved sort to the catalogue.

No database schema change or migration was required. The unused `PlaceholderFragment` was removed now that every screen is implemented.

## Submission deliverables

| Required deliverable | Location |
| --- | --- |
| GitHub repository with the Android Studio project | [jefsaber/FitJournal](https://github.com/jefsaber/FitJournal) |
| Complete README | This file: setup, architecture, features, validation, tests, team, and demonstration |
| Simple architecture diagram | [Application flow and entity relationships](docs/architecture.md) |
| JUnit and Espresso tests | [Local JUnit sources](app/src/test/java/com/fitjournal/app) and [instrumentation/Espresso sources](app/src/androidTest/java/com/fitjournal/app) |
| Short project presentation | [PowerPoint deck (.pptx)](docs/FitJournal-Presentation.pptx), [browser version](docs/presentation.html), [20-minute schedule](docs/code-and-presentation-guide.md), and individual speaker notes |
| Working application for the live demonstration | Run the `app` configuration in Android Studio; use the workflow below |

Open `docs/FitJournal-Presentation.pptx` in PowerPoint for five editable slides with speaker notes and no presentation timings. A browser version is also available at `docs/presentation.html`; its **Print / Save as PDF** control exports all six slides. The schedule is **6 minutes architecture, 8 minutes live demonstration, 6 minutes Q&A**.

To produce the normal debug APK:

```powershell
.\gradlew.bat :app:assembleDebug
```

The standard generated location is `app/build/outputs/apk/debug/app-debug.apk`. Keep the normal app installed and rehearse on the same device/emulator used for the presentation.

Before submitting, publish the final documentation/source changes, confirm the repository is accessible to the grader, and upload the exported presentation if the submission portal requests a separate slide file.

Additional records: [team plan](docs/team-plan.md), [contribution workflow](CONTRIBUTING.md), and [debugging log](docs/debugging-log.md).

## Tests

From an Android Studio terminal:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest  # requires running emulator/device
```

On Windows PowerShell, use `.\gradlew.bat` in place of `./gradlew`.

The current suite contains **46 local JUnit tests** and **16 instrumentation tests** (9 Espresso UI tests and 7 Room/import tests):

- [ExerciseCatalogueViewModelTest](app/src/test/java/com/fitjournal/app/ExerciseCatalogueViewModelTest.kt): 4 tests covering combined query state, live/case-insensitive categories, saved-state restoration, and sort-write failure/retry.
- [ExerciseFormViewModelTest](app/src/test/java/com/fitjournal/app/ExerciseFormViewModelTest.kt): 8 tests covering validation boundaries, supported tracking types, invalid submissions, trimmed creation, repeated save taps, editing without changing identity, restored drafts, save retry, and missing records.
- [WorkoutFormViewModelTest](app/src/test/java/com/fitjournal/app/WorkoutFormViewModelTest.kt): 8 tests covering the default date, invalid titles, trimmed creation stored at the local start of day, repeated save taps, completed-in-future rejection, editing the same record, restored drafts, save retry, deletion, and missing records.
- [WorkoutHistoryTest](app/src/test/java/com/fitjournal/app/WorkoutHistoryTest.kt): 5 tests covering workout validation boundaries, date round-trips (including a daylight-saving day), filter date windows, combined search/status/period filtering with ordering and filter restoration, and empty versus no-match states. Uses the in-memory [MemoryWorkoutDao](app/src/test/java/com/fitjournal/app/MemoryWorkoutDao.kt).
- [ValidatorsTest](app/src/test/java/com/fitjournal/app/ValidatorsTest.kt): 10 tests covering exercise/workout validation, strength and duration entry validation, volume calculation and duration exclusion, strength/duration personal records, invalid record metrics, and kg/lb round trips.
- [SettingsRepositoryTest](app/src/test/java/com/fitjournal/app/SettingsRepositoryTest.kt): 5 tests covering defaults, rejection of unsupported values, persistence across DataStore instances, returning to the device theme, and fallback for unknown stored values.
- [WeightConverterTest](app/src/test/java/com/fitjournal/app/WeightConverterTest.kt): 4 tests covering kilogram identity, the international pound factor, kg→lb→kg and lb→kg→lb round trips, and unit-code parsing.
- [ThemePreferenceTest](app/src/test/java/com/fitjournal/app/ThemePreferenceTest.kt): 2 tests covering the night-mode mapping and theme-choice round trips.
- [ExerciseFormTest](app/src/androidTest/java/com/fitjournal/app/ExerciseFormTest.kt): creates an exercise, verifies draft restoration after activity recreation, finds it in the catalogue, edits it, and verifies its ID and code are preserved. It removes only the records it creates.
- [WorkoutFormTest](app/src/androidTest/java/com/fitjournal/app/WorkoutFormTest.kt): creates a completed workout, verifies draft restoration after activity recreation, finds it with the Completed and Last 7 days filters, checks that the Planned filter hides it, edits it while preserving its ID, and deletes it through the confirmation dialog. It removes only the records it creates.
- [WorkoutEntryTest](app/src/androidTest/java/com/fitjournal/app/WorkoutEntryTest.kt): opens a workout, selects a catalogue exercise, restores an unsaved entry across activity recreation, creates and edits the strength entry, then confirms deletion. It verifies Room values and removes only its own records.
- [SettingsTest](app/src/androidTest/java/com/fitjournal/app/SettingsTest.kt): selects pounds and Z–A, checks the choices survive activity recreation, shows `lb` on Progress, and re-sorts the catalogue both ways. A second test switches Dark → Light → Device and checks the recreated activity's night configuration. Previous preferences are restored afterwards.
- [MainActivityTest](app/src/androidTest/java/com/fitjournal/app/MainActivityTest.kt): existing catalogue visibility smoke test.
- [PresentationFlowTest](app/src/androidTest/java/com/fitjournal/app/PresentationFlowTest.kt): traverses all seven destinations, enters text through the actual keyboard in each form, recreates screens/drafts, creates a completed workout and entry through the UI, checks its record, switches to pounds, and relaunches while verifying that Room still stores 40 kg.
- [LandscapeNavigationTest](app/src/androidTest/java/com/fitjournal/app/LandscapeNavigationTest.kt): performs a real landscape configuration change and verifies that search, filters, add actions, navigation, and list rows remain usable.
- [ExerciseCatalogueTest](app/src/androidTest/java/com/fitjournal/app/ExerciseCatalogueTest.kt): combines search and category, changes the saved sort, restores controls after recreation, and verifies synchronization with Settings.
- [DatabaseIntegrityTest](app/src/androidTest/java/com/fitjournal/app/DatabaseIntegrityTest.kt): 7 real Room/import tests covering one-time import, edited/user records during retry, malformed rows, unique-code minimum, invalid JSON recovery, cascading workout deletion, and combined paging query behavior.

All 46 local JUnit tests and all 16 instrumentation tests passed during verification. Two observed development incidents are recorded in [the debugging log](docs/debugging-log.md); the team must attach its actual author/PR references and any required debugger captures.

### Isolated clean-install verification

Use a separate debug application ID to preserve the normal app's workouts and preferences:

```powershell
.\gradlew.bat :app:assembleDebug :app:connectedDebugAndroidTest -Pfitjournal.isolatedValidation=true
```

This installs `com.fitjournal.app.validation`. Gradle instrumented runs use `animationsDisabled = true` to avoid transition-related input races. Tests create and clean their own records; Room/import integrity tests use entirely in-memory databases and temporary DataStores. Build normally afterwards to produce the standard `com.fitjournal.app` APK:

```powershell
.\gradlew.bat :app:assembleDebug
```

## Presentation demonstration

1. In Settings, select **Kilograms**, **Name A–Z**, and the desired appearance.
2. Create a strength exercise named **Presentation press**, category **Chest**. Find it using search and the muscle-group filter; demonstrate both sort directions.
3. Create a workout named **Presentation session**, dated today and marked completed.
4. Open that workout and log **3 sets × 8 repetitions × 40 kg** for Presentation press. This contributes **960 kg-reps** and a **40 kg** personal record.
5. Add a timed exercise, for example Plank for **60 seconds**. Explain that it contributes a duration record and no strength volume.
6. Open Progress and show completed-only totals, recent volume, and records.
7. Switch to pounds in Settings and return to Progress/details. Explain that display units change while canonical kilograms remain in Room.
8. Demonstrate rotation and back navigation, then restart the app and show that the records and preferences persist.
9. Show the test results, [architecture diagram](docs/architecture.md), and [two real debugging cases](docs/debugging-log.md).

Expected numeric values in this example describe a fresh demonstration dataset; other completed workouts also contribute to the overall totals.

## GitHub

Repository: [https://github.com/jefsaber/FitJournal](https://github.com/jefsaber/FitJournal).

Use the team's feature-branch and Pull Request workflow described in [CONTRIBUTING.md](CONTRIBUTING.md). Before submitting the URL, ensure the final source, README, diagrams, tests, and presentation are committed and pushed to the branch the grader will review. If the repository is private, grant the grader access.

## Team contributions

| Role | Student name | Module responsibility |
| --- | --- | --- |
| Developer 1 | Jef Saber | Exercise catalogue, Add/Edit, filters, seed initialization |
| Developer 2 | Paul Tekle | Workout creation, editing, history, deletion |
| Developer 3 | Anna Merheb | Workout logging, volume, personal records, Progress |
| Developer 4 | David Sfeir | Settings, theme/units/order integration, shared application infrastructure |

Add individual GitHub profiles or significant PR links if required by the course.

Read the [code and presentation guide](docs/code-and-presentation-guide.md) for a plain-language architecture walkthrough, files to study for each member, the 20-minute presentation plan (6 minutes architecture, 8 minutes live demonstration, 6 minutes Q&A), demonstration steps, and likely questions.

See [remaining-work.md](remaining-work.md) for the current completion record and the remaining presentation-only follow-up.
