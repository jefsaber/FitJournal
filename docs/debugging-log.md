# FitJournal debugging log

These two incidents were observed during development in the Android Studio assistant session on **19 November 2026**. The error excerpts and fixes below are real. Original commit/PR links and student attribution still need to be supplied by the team; no breakpoint sessions or screenshots are invented here.

## Case 1 — Java and Kotlin targeted different JVM versions

- **Date:** 19 November 2026.
- **Owner:** Developer 1, assisted by Codex.
- **Branch/commit/PR:** Original reference not supplied; add the actual link before submission.
- **Symptom:** Running the app failed before launch at `:app:kaptGenerateStubsDebugKotlin`.
- **Reproduction:** Build the configuration where Java targets 17 and Kotlin/kapt targets 21.

Observed Gradle error:

```text
Execution failed for task ':app:kaptGenerateStubsDebugKotlin'.
Inconsistent JVM-target compatibility detected for tasks
'compileDebugJavaWithJavac' (17) and
'kaptGenerateStubsDebugKotlin' (21).
```

**Inspection and root cause:** [app/build.gradle.kts](../app/build.gradle.kts) explicitly set Java source/target compatibility to 17 while the reported Kotlin stub task targeted 21. The two related compilation tasks produced incompatible target levels.

**Fix:** At the developer's request, use JVM 21 consistently: Java source and target compatibility are `VERSION_21`, Kotlin `jvmTarget` is `JVM_21`, and `jvmToolchain(21)` selects the compiler toolchain.

**Breakpoint evidence:** Not applicable to an app runtime breakpoint: Gradle failed before the activity could launch. The relevant inspected values are the Java/Kotlin targets shown above.

**Verification:** `:app:assembleDebug` succeeded after aligning the targets, and the app was subsequently run successfully. The final verification counts are recorded in the README.

## Case 2 — Clearing a toolbar icon with resource ID zero crashed startup

- **Date:** 19 November 2026.
- **Owner:** Developer 1, assisted by Codex.
- **Branch/commit/PR:** Original reference not supplied; add the actual link before submission.
- **Symptom:** The new app toolbar compiled successfully, but deploying the app returned to the launcher.
- **Reproduction:** On a top-level destination, call `setNavigationIcon(0)` when intending to remove the toolbar navigation icon.

Observed Logcat excerpt:

```text
E/AndroidRuntime: FATAL EXCEPTION: main
java.lang.RuntimeException: Unable to start activity
ComponentInfo{com.fitjournal.app/com.fitjournal.app.MainActivity}
Caused by: android.content.res.Resources$NotFoundException: Resource ID #0x0
    at androidx.appcompat.widget.Toolbar.setNavigationIcon(Toolbar.java:1038)
    at com.fitjournal.app.MainActivity.onCreate$lambda$2(MainActivity.kt:43)
```

**Inspection and root cause:** The destination listener used `setNavigationIcon(if (mainScreen) 0 else R.drawable.ic_back)`. At startup, `mainScreen` was true, selecting resource ID zero. This toolbar overload attempted to load that ID as a drawable.

**Fix:** Assign a nullable Drawable instead. Top-level destinations use `navigationIcon = null`; child destinations load `ic_back` through `AppCompatResources`. The logo follows the same explicit nullable-Drawable pattern.

**Breakpoint evidence:** The original diagnosis used Logcat and source inspection, not a recorded breakpoint session. If the course requires a debugger capture, the team should capture one at the destination listener and record `destination.id`, `mainScreen`, and `navigationIcon`; do not present this suggested capture as already performed.

**Verification:** The corrected app launched on the API 37 emulator. Catalogue and form screens were visually checked, and the activity smoke test plus the exercise Add/Edit Espresso test passed after the fix. Subsequent full-suite results are recorded in the README.

## Before presentation

- [ ] Replace role-only attribution with the students' names if required.
- [ ] Add the original commit/PR URLs for the fixes.
- [ ] Add any required breakpoint screenshots from an actual debugging session.
