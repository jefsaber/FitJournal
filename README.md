# FitJournal — Native Android Workout Tracker

**Course project** · Kotlin · XML Views / RecyclerView · MVVM · Room · Flow · Paging 3 · Preferences DataStore

Deadline: **26 November 2026** · Group: **4 developers**

> **Status: exercise Add/Edit workflow implemented**. Navigation, database contracts, the initial exercise catalogue, JSON import, and the preferences repository are present. Exercises can now be created and edited with validation and draft restoration. Catalogue filter/sort controls, workout forms, workout logging, charts, and settings UI remain to be completed.

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

> **Latest verification:** `:app:assembleDebug` succeeded with JVM 21, all 11 JUnit tests passed, and both Espresso tests passed on the API 37 emulator.

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
3. Workout history — Flow-powered empty/list state (scaffold).
4. Add/edit workout — TODO developer 2.
5. Workout details/logging — TODO developer 3.
6. Progress dashboard — TODO developer 3.
7. Settings — TODO developer 4.

Navigation uses a four-tab `BottomNavigationView` and three child destinations.

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

## Deliverables / tracking

See [docs/team-plan.md](docs/team-plan.md), [CONTRIBUTING.md](CONTRIBUTING.md), and [docs/debugging-log.md](docs/debugging-log.md).

## Tests

From an Android Studio terminal after generating the wrapper:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest  # requires running emulator/device
```

On Windows PowerShell, use `.\gradlew.bat` in place of `./gradlew`.

The current suite contains **11 JUnit tests** and **2 Espresso tests**:

- [ExerciseFormViewModelTest](app/src/test/java/com/fitjournal/app/ExerciseFormViewModelTest.kt): 8 tests covering validation boundaries, supported tracking types, invalid submissions, trimmed creation, repeated save taps, editing without changing identity, restored drafts, save retry, and missing records.
- [ValidatorsTest](app/src/test/java/com/fitjournal/app/ValidatorsTest.kt): 3 existing exercise/workout validation and volume calculation tests.
- [ExerciseFormTest](app/src/androidTest/java/com/fitjournal/app/ExerciseFormTest.kt): creates an exercise, verifies draft restoration after activity recreation, finds it in the catalogue, edits it, and verifies its ID and code are preserved. It removes only the records it creates.
- [MainActivityTest](app/src/androidTest/java/com/fitjournal/app/MainActivityTest.kt): existing catalogue visibility smoke test.

All 11 JUnit tests and both Espresso tests passed during verification. The team still needs **at least 3 Espresso UI tests** in total, tests for the remaining modules, and 2 documented real debugging cases.

## GitHub

1. Create a GitHub repository `FitJournal` (or use the existing one).
2. Push the starter to the repository's default branch following the steps in your ChatGPT reply.
3. Give all four members **Write** access in GitHub Settings > Collaborators.
4. Protect `main` through Settings > Branches / Rulesets and require Pull Request review.
5. Each teammate creates their own `feature/...` branch.

## Team contributions

Before submission, list all four students, GitHub usernames, owned modules and significant PR URLs here.
