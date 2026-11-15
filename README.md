# Expense Tracker (offline-first, Android)

Kotlin · Jetpack Compose · Material 3 · Room · CameraX · ML Kit text recognition.

Everything runs on the device. The manifest has **no INTERNET permission**, Room is the only
data source, and receipt OCR uses ML Kit's bundled model, so the app behaves identically in
airplane mode.

## Open and run

1. Open the `ExpenseTracker` folder in a recent Android Studio (one that supports AGP 9).
2. Let Gradle sync, then run the `app` configuration on a device or emulator (API 26+).
3. Unit tests: `./gradlew test`.

Receipt scanning needs a real camera for good results. On an emulator use
"Choose a photo instead" with a receipt image in the gallery.

## Architecture (MVVM, unidirectional data flow)

```
 Compose screen ──user events──▶ ViewModel ──suspend writes──▶ ExpenseRepository ──▶ Room DAO ──▶ SQLite
       ▲                            │                                                   │
       └──── StateFlow<UiState> ◀───┴──────────── Flow<List<…>> (re-emits on change) ◀──┘
```

- **UI (`ui/*`)**: stateless-ish composables. Each screen collects one `StateFlow<UiState>`
  with `collectAsStateWithLifecycle()` and calls ViewModel functions for events.
- **ViewModel**: turns Room `Flow`s into an immutable `UiState` with
  `flatMapLatest`/`combine` + `stateIn(WhileSubscribed(5s))`. One-off events (e.g. "saved, go
  back") go through a `Channel`, not through state.
- **Repository (`data/ExpenseRepository`)**: the single source of truth. Converts calendar
  months to epoch ranges and buckets monthly totals.
- **Room (`data/local`)**: two tables, reactive queries.
- **DI**: a small manual `AppContainer` on the `Application`; ViewModels are created with
  `viewModelFactory { initializer { … } }`.

Because every read is a Room `Flow`, a write anywhere (saving an expense) automatically
refreshes the list, the donut and the bar chart. No screen ever asks for a refresh.

### State flow per screen

| Screen | State source | Events |
|---|---|---|
| Expenses | `selectedMonth` → `observeExpenses(month)` → `ExpenseListUiState` (grouped by day) | previous/next month |
| Add / Edit | `form: MutableStateFlow` ⨉ `observeCategories()` → `AddEditUiState` | field changes, `save()`, `delete()`, `onReceiptScanned()` |
| Summary | `selectedMonth` → `combine(categoryTotals, monthlyTotals)` → `SummaryUiState` | month switch, tap a bar |
| Scan | `ScanUiState` (processing → result → selected candidate) | photo captured / picked, candidate chosen, retake |

### Receipt scan flow

```
Add/Edit ──navigate──▶ Scan: CameraX ImageCapture (in memory) ──▶ ML Kit TextRecognizer
                                                                     │ lines + bounding boxes
                                                                     ▼
                             review sheet ◀── ReceiptParser (pure Kotlin: rows → amounts → ranking)
                                  │ user confirms or picks another candidate
                                  ▼
Add/Edit ◀── previousBackStackEntry.savedStateHandle ── amount (+ merchant guess) pre-filled
```

The scan never saves anything by itself: it only pre-fills the form, and the user reviews
the amount before saving. The photo is never written to disk.

## Database schema

```
categories                              expenses
──────────                              ────────
id         INTEGER PK AUTOINCREMENT     id          INTEGER PK AUTOINCREMENT
name       TEXT  UNIQUE                 amountMinor INTEGER   -- paise/cents, exact sums
emoji      TEXT                         categoryId  INTEGER   -- FK → categories.id (RESTRICT), indexed
colorSlot  INTEGER -- chart palette     merchant    TEXT
                      index 0..7        note        TEXT
                                        occurredAt  INTEGER   -- epoch millis, indexed
                                        createdAt   INTEGER
```

Eight default categories are seeded when the database is first created.

## Project layout

```
app/src/main/java/com/example/expensetracker/
├── ExpenseApp.kt                 Application + AppContainer (manual DI)
├── MainActivity.kt
├── data/
│   ├── ExpenseRepository.kt
│   ├── local/                    Entities.kt, ExpenseDao.kt, CategoryDao.kt, AppDatabase.kt
│   └── ocr/                      ReceiptParser.kt (pure), ReceiptTextRecognizer.kt (ML Kit)
├── util/                         Money.kt, Dates.kt
└── ui/
    ├── theme/Theme.kt            Material 3 theme + chart palette
    ├── navigation/AppNavHost.kt  routes, bottom bar, scan-result hand-off
    ├── components/               MonthSwitcher.kt, Charts.kt (donut + bar chart, no chart library)
    ├── expenses/                 list screen + ViewModel
    ├── edit/                     add/edit screen + ViewModel
    ├── summary/                  monthly summary screen + ViewModel
    └── scan/                     CameraX + review sheet + ViewModel
```

## What has and has not been verified

- `ReceiptParser` and `Money` were compiled and their 7 unit tests passed on a JVM.
- All Kotlin files were parsed by the Kotlin compiler with no syntax errors.
- The full Android build runs in CI (`ci.yml`); its first run is the real compile check. If a
  version fails to resolve, every version lives in `gradle/libs.versions.toml`.

## CI/CD (GitHub Actions)

| Workflow | Runs on | Does |
|---|---|---|
| `ci.yml` | every push to `main`, every PR | wrapper validation, unit tests, Android lint, debug APK (downloadable from the run), reports |
| `release.yml` | tag `v*` (e.g. `v1.0.0`) | tests, release APK + AAB, GitHub Release with notes and files attached |
| `dependabot.yml` | weekly | PRs to bump Gradle libraries/plugins and action versions |

Cut a release:

```bash
git tag v1.0.0 && git push origin v1.0.0
```

Tags with a `-` (e.g. `v1.1.0-beta1`) are published as pre-releases.

### Signing releases (optional)

Without secrets, release builds are produced **unsigned** (fine for testing the pipeline,
not installable as-is). To sign, create a keystore once and add four repository secrets
(Settings → Secrets and variables → Actions):

```bash
keytool -genkeypair -v -keystore release.jks -alias expense -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 release.jks   # paste output as SIGNING_KEYSTORE_BASE64
```

| Secret | Value |
|---|---|
| `SIGNING_KEYSTORE_BASE64` | base64 of `release.jks` |
| `SIGNING_STORE_PASSWORD` | keystore password |
| `SIGNING_KEY_ALIAS` | `expense` (or your alias) |
| `SIGNING_KEY_PASSWORD` | key password |

Keep `release.jks` backed up and out of git: losing it means you can't update the app on Play.

## Known limits / next steps

- Amounts assume a 2-decimal currency (INR, USD, EUR…), shown in the device locale's currency.
- OCR uses the Latin-script model. For Devanagari receipts add
  `com.google.mlkit:text-recognition-devanagari` and its recognizer options.
- Categories are seeded; there is no category editor yet (`CategoryDao.insert` is ready for one).
- No backup/sync. To add it, keep Room as the source of truth and sync behind the repository.
