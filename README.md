# FitJournal — Native Android Workout Tracker

**Course project** · Kotlin · XML Views / RecyclerView · MVVM · Room · Flow · Paging 3 · Preferences DataStore

Deadline: **26 November 2026** · Group: **4 developers**

> **Status: exercise Add/Edit, workout management, workout logging, and progress statistics implemented**. Users can create workouts, log strength or timed exercises, edit/delete entries, and review completed-workout volume, a recent-volume chart, and personal records. Settings persist the theme, weight unit, and default exercise order. Catalogue category-filter and in-screen sort controls remain with Developer 1.

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

> **Latest verification:** `:app:assembleDebug` succeeded with JVM 21, all 42 JUnit tests passed, and all 6 Espresso tests passed on the API 37 emulator.

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

1. Exercise catalogue — Paging 3 list + search, ordered by the saved default sort.
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
- **Catalogue sort.** `ExerciseListViewModel` combines the search text with the saved sort and re-queries the existing `ExerciseDao.page` query. Developer 1 can add in-screen sort/filter controls on top of this default.

### Implementation

- [SettingsFragment](app/src/main/java/com/fitjournal/app/features/settings/SettingsFragment.kt) / [SettingsViewModel](app/src/main/java/com/fitjournal/app/features/settings/SettingsViewModel.kt): toggle groups that reflect saved state, plus loading and save-error states.
- [SettingsRepository](app/src/main/java/com/fitjournal/app/core/preferences/SettingsRepository.kt): validated setters, defaults, and an injectable `DataStore` for tests.
- [ThemePreference](app/src/main/java/com/fitjournal/app/core/preferences/ThemePreference.kt) / [FitJournalApplication](app/src/main/java/com/fitjournal/app/FitJournalApplication.kt): maps the preference to night modes and applies it at startup and on change.
- [ExerciseListViewModel](app/src/main/java/com/fitjournal/app/features/exercises/ExerciseListViewModel.kt): connects the saved sort to the catalogue.

No database schema change or migration was required. The unused `PlaceholderFragment` was removed now that every screen is implemented.

## Deliverables / tracking

See [docs/team-plan.md](docs/team-plan.md), [CONTRIBUTING.md](CONTRIBUTING.md), and [docs/debugging-log.md](docs/debugging-log.md).

## Tests

From an Android Studio terminal:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest  # requires running emulator/device
```

On Windows PowerShell, use `.\gradlew.bat` in place of `./gradlew`.

The current suite contains **42 JUnit tests** and **6 Espresso tests**:

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

All 42 JUnit tests and all 6 Espresso tests passed during verification. Developer 1's catalogue filter/sort controls and JSON duplicate-prevention tests are still to come, and the team still needs 2 documented real debugging cases.

## GitHub

1. Create a GitHub repository `FitJournal` (or use the existing one).
2. Push the starter to the repository's default branch following the steps in your ChatGPT reply.
3. Give all four members **Write** access in GitHub Settings > Collaborators.
4. Protect `main` through Settings > Branches / Rulesets and require Pull Request review.
5. Each teammate creates their own `feature/...` branch.

## Team contributions

Before submission, list all four students, GitHub usernames, owned modules and significant PR URLs here.
