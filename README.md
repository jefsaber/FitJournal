# FitJournal — Native Android Workout Tracker

**Course project** · Kotlin · XML Views / RecyclerView · MVVM · Room · Flow · Paging 3 · Preferences DataStore

Deadline: **26 November 2026** · Group: **4 developers**

> **Status: foundation scaffold**. Navigation, the database contracts, initial exercise catalogue, JSON import, and preferences repository are present. Workout CRUD forms, workout logging, charts, settings UI, and most tests are intentionally TODO for teammates. This is NOT the finished assignment.

## Requirements / setup

- Android Studio with SDK API 36 installed
- JDK 17+ configured for Gradle
- Gradle 8.13 / Android Gradle plugin 8.13.2
- Android emulator / device API 26+

### Open the project

1. Unzip or clone the project and choose **Open** on the `FitJournal/` folder in Android Studio.
2. **Bootstrap the official Gradle wrapper once:** the starter contains wrapper properties but not the executable wrapper files (the creation environment could not download them). On **Windows**, run `powershell -ExecutionPolicy Bypass -File tools\bootstrap-gradle-wrapper.ps1` in the project directory. On **macOS/Linux**, run `bash tools/bootstrap-gradle-wrapper.sh`. These scripts download the *official* Gradle 8.13 wrapper scripts and binary from the pinned Gradle GitHub tag; inspect the scripts before running them. Commit the resulting `gradlew`, `gradlew.bat`, and `gradle/wrapper/gradle-wrapper.jar` files so that teammates will not need to bootstrap again. Alternatively, with an installed `gradle` executable, run `gradle wrapper --gradle-version 8.13`.
3. Sync Gradle, then **Run > Run 'app'**.
4. The first launch imports 24 exercises from `app/src/main/assets/exercises.json` and shows them in the **Exercises** catalogue. The import does not overwrite existing entries.

> Build verification was not possible in the creation environment (Android SDK, Gradle and network are unavailable). The first teammate to open the project must run `./gradlew :app:assembleDebug` and fix any environment-specific problems before merging the foundation branch.

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
2. Add/edit exercise — TODO developer 1.
3. Workout history — Flow-powered empty/list state (scaffold).
4. Add/edit workout — TODO developer 2.
5. Workout details/logging — TODO developer 3.
6. Progress dashboard — TODO developer 3.
7. Settings — TODO developer 4.

Navigation uses a four-tab `BottomNavigationView` and three child destinations.

## Deliverables / tracking

See [docs/team-plan.md](docs/team-plan.md), [CONTRIBUTING.md](CONTRIBUTING.md), and [docs/debugging-log.md](docs/debugging-log.md).

## Tests

From an Android Studio terminal after generating the wrapper:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest  # requires running emulator/device
```

The starter includes 3 JUnit tests and 1 Espresso smoke test. The team must extend to **at least 8 meaningful JUnit** and **3 Espresso UI tests**, plus document 2 real debugging cases.

## GitHub

1. Create a GitHub repository `FitJournal` (or use the existing one).
2. Push the starter to the repository's default branch following the steps in your ChatGPT reply.
3. Give all four members **Write** access in GitHub Settings > Collaborators.
4. Protect `main` through Settings > Branches / Rulesets and require Pull Request review.
5. Each teammate creates their own `feature/...` branch.

## Team contributions

Before submission, list all four students, GitHub usernames, owned modules and significant PR URLs here.
