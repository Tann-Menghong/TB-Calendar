# Architecture

## 1. Modules

```
:calendar-core   Pure JVM library. No Android dependency.
:ai-core         Android library. Depends on :calendar-core.
:app             Application. Depends on both.
```

The dependency arrows only point one way. `:calendar-core` cannot reach the database and
`:ai-core` cannot reach the UI, which is enforced by the compiler rather than by convention.

**`:calendar-core`** — Chhankitek conversion, Khmer New Year reckoning, the holiday table,
RFC 5545 recurrence expansion, Khmer terms and numerals, and the rule-based Khmer
natural-language parser. Being plain JVM means all of it is testable without Robolectric or a
device, which is why the calendar arithmetic has the densest tests in the project.

**`:ai-core`** — the `AiEngine` seam, the MediaPipe implementation, the model catalogue and
store, device-capability checks, the download worker, and `CalendarTools` (the exact,
non-generated answers the assistant gives). See [AI_MODELS.md](AI_MODELS.md).

**`:app`** — Room, DataStore, notifications, widgets, and the Compose UI.

## 2. Layers inside `:app`

```
ui/            Compose screens + ViewModels (one per feature area)
domain/        EventOccurrence, EventDraftModel, the UTC↔wall-clock conversion
data/repo/     EventRepository — the single door to calendar data
data/db/       Room entities, DAOs, database
data/prefs/    AppSettings + SettingsStore (DataStore)
data/backup/   JSON backup, .ics import/export
notify/        Reminder scheduling, receivers, the re-arm worker
widget/        Glance widgets
```

State flows one way: DAO `Flow` → repository → ViewModel `StateFlow` → Compose. Nothing
writes to the database except through `EventRepository`.

## 3. Decisions worth knowing about

**Manual DI (`AppContainer`), not Hilt.** The graph is about a dozen objects with no
scoping beyond "one per process". Hilt would add an annotation processor, a build-time cost on
every module, and a layer of indirection to read through, in exchange for solving a problem
this app does not have. ViewModels are created with
`viewModelFactory { initializer { … } }`, which needs no processor either.

**One activity.** Navigation state, the theme and the app lock live in one place, and the
external intents the app handles — a reminder notification, a widget tap, a launcher shortcut,
an `.ics` file — all translate into a destination in one function.

**Occurrences are expanded, not materialised.** A repeating event is one row plus an `RRULE`.
The DAO returns candidate rows overlapping the visible window and the repository expands them.
Materialising rows would make "edit the whole series" a migration, and a yearly event with no
end date an infinite table.

**Deleting one occurrence writes an exception row.** Not a split series — so a later edit to
the series still reaches every remaining occurrence.

**UTC millis plus the creating zone.** An 08:00 meeting created in Phnom Penh still reads
08:00 in Bangkok, while its reminder still fires at the right instant. All-day events are
pinned to UTC midnight, following the `CalendarContract` convention; storing them at local
midnight makes them slide a day on a zone change. There is a test for that.

**A rolling 14-day exact-alarm window, re-armed daily.** `AlarmManager` has a per-app limit
and exact alarms are a scarce, battery-relevant resource. Arming everything forever would hit
the limit; arming two weeks and re-arming from a daily `WorkManager` job does not, and
survives reboot, time-zone change and app update through `SystemEventReceiver`.

**WorkManager's initializer is removed from the manifest**, and `KhmerCalendarApp` implements
`Configuration.Provider` instead. Nothing scheduled needs to exist before the user has opened
the app once, and skipping the initializer keeps a cold start off the database.

**`SCHEDULE_EXACT_ALARM`, not `USE_EXACT_ALARM`.** The latter is auto-granted but is reserved
for alarm-clock apps; a calendar asking for it is a Play policy problem. The former is
user-grantable, and the notification settings screen deep-links to the system toggle and
re-checks on resume.

**Settings are one immutable snapshot.** `AppSettings` is a single data class read from one
`Flow`, so the theme, the grid and the dashboard always recompose from a consistent value
rather than a half-applied set of separate preferences.

**The PIN is a salted SHA-256.** The lock guards a private calendar on a shared phone, not a
bank account, and the data already sits in the app sandbox. A salted hash defeats reading the
PIN out of the preferences file, which is the threat that actually applies; anything stronger
needs a key the app cannot keep secret from itself.

## 4. Database

Five tables, Room, schema version 1, schemas exported to `app/schemas/`.

| Table | |
|---|---|
| `events` | Title, description, location, `startUtcMillis`, `endUtcMillis`, `allDay`, `zoneId`, `rrule`, `categoryId`, `colorArgb`, `isTask`, `isCompleted`, timestamps. |
| `categories` | Name, colour, sort order, `isBuiltIn`. Five are seeded on first run and all are renameable; built-in ones cannot be deleted. |
| `reminders` | Minutes before, per event. Several per event. |
| `event_exceptions` | A date removed from a repeating series. |
| `day_notes` | One free-text note per date. |

## 5. Tests

| | |
|---|---|
| `ChhankitekTest` | Lunar conversion against known dates, epoch, range, leap months and leap days. |
| `KhmerHolidaysTest` | Fixed dates, computed lunar dates, kind/source split, gazetted override precedence. |
| `RecurrenceTest` | Daily/weekly/monthly/yearly expansion, interval, BYDAY, COUNT, UNTIL. |
| `NaturalLanguageEventParserTest` | Khmer date/time/title extraction. |
| `EventRepositoryTest` | Occurrence expansion against a real in-memory Room database, including the all-day time-zone case. |

```bash
./gradlew :calendar-core:test :app:testDebugUnitTest
```

`:app` tests run under Robolectric pinned to SDK 34 with native SQLite — see
`app/src/test/resources/robolectric.properties` for why.
