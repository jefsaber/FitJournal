# Module ownership and first PRs

- **Developer 4 — foundation + settings:** establishes main project skeleton, bottom navigation, Room database, preferences; first branch `feature/foundation`; after first merge work on `feature/settings`.
- **Developer 1 — exercises:** `feature/exercises`; exercise CRUD form, edit/delete, input validation, JSON seeding verification, Paging 3 list filtering/sorting, tests.
- **Developer 2 — workouts:** `feature/workouts`; workout create/edit/delete, history filters, proper dates, tests.
- **Developer 3 — entries/progress:** `feature/workout-entries`; attach exercises to workouts, entry forms, charts, calculations, tests.

## Integration order

1. Merge `feature/foundation` once all four agree on Entity fields and API signatures.
2. Develop modules in parallel using DAO/repository contracts from the foundation.
3. Merge `feature/exercises` and `feature/workouts` before connecting WorkoutEntry UI.
4. Integrate `feature/workout-entries` and `feature/settings`; freeze features by November 23.
5. Run all tests, rehearse, submit on November 26, 2026.

## Test allocation (minimum target: 8 unit + 3 Espresso)

- Dev 1: two exercise/JSON JUnit, one exercise form Espresso.
- Dev 2: two workout JUnit, one workout form Espresso.
- Dev 3: two progress calculation JUnit, one workout logging Espresso.
- Dev 4: two preferences/sorting JUnit, help own test infrastructure.

The starter currently includes **three** pure JUnit tests and **one** smoke Espresso test. Remaining tests are TODO and must be written.
