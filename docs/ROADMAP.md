# Performance notes and roadmap

## 1. What was done for performance, and why

**Startup does no work the first screen does not need.**
- WorkManager's `androidx.startup` initializer is removed from the manifest and the
  `Application` implements `Configuration.Provider` instead, so a cold start does not
  initialise WorkManager or touch the database.
- Arming reminders and enqueuing the daily sync happen on a background scope after
  `onCreate` returns, not inside it.
- Category seeding runs from Room's `onCreate` callback on an IO dispatcher; first launch
  renders an empty calendar rather than waiting.

**The lunar calendar is a table lookup, not a walk.**
The reference algorithm iterates month by month from 1900 for every conversion. This
implementation precomputes every lunar month start once and binary-searches it. A month grid
needs 42 lunar dates; at 3,600 iterations each that would be visible on a mid-range phone,
and it is what would have made scrolling stutter.

**Repeating events are expanded, never materialised.**
The DAO returns only rows whose window overlaps the visible range; expansion happens in
memory over that subset. No table grows with the number of occurrences, and a yearly event
with no end date costs one row.

**Queries are windowed everywhere.**
The month view asks for 42 days, the agenda for 90 and extends as you scroll to a 730-day cap,
the dashboard for 60. Nothing does `SELECT *` over the whole table except backup and search.

**Reminders use a rolling 14-day window.**
`AlarmManager` limits how many exact alarms an app may hold, and exact alarms are
battery-relevant. Arming two weeks and re-arming from a daily `WorkManager` job stays under the
limit, keeps the alarm list small, and survives reboot, time-zone change and app update.

**Background work is battery-respecting.**
One daily deferrable job. The only other background work is the model download, which is a
user-initiated foreground job with a progress notification and is cancellable.

**The AI model is loaded on demand and unloaded on leaving.**
A bundle is hundreds of megabytes to gigabytes of resident memory — the difference between
the process surviving in the background and being killed. Nothing loads a model unless the
user asks.

**Release builds are minified and shrunk** (R8 + resource shrinking), and `abiFilters` keeps
the APK to `arm64-v8a` and `x86_64` rather than carrying dead 32-bit copies of the native
inference libraries.

**Settings are one snapshot.** A single `AppSettings` value from a single `Flow` means a theme
change recomposes once, not once per preference.

## 2. Measured

| | |
|---|---|
| Release APK | 60 MB — dominated by the MediaPipe native libraries. |
| Debug APK | 82 MB (unminified). |
| Unit tests | 53, across calendar arithmetic, holidays, recurrence, the Khmer parser, and repository occurrence expansion. |
| Minimum Android | 8.0 (API 26), chosen so `java.time` is native rather than desugared — the calendar leans on it heavily. |

## 3. Worth doing next, on performance

- **Baseline profile.** The release build already generates an ART profile; a proper
  Macrobenchmark-driven baseline profile would cut first-frame time further on cold start.
- **Drop MediaPipe from a "lite" flavour.** Most of the 60 MB is the inference runtime. A
  product flavour without `:ai-core` would ship a ~6 MB calendar for users who will never turn
  the assistant on. `AiEngineFactory` already degrades to `NoOpAiEngine`, so nothing else
  changes.
- **Per-ABI APK splits or an AAB**, which would roughly halve what any one device downloads.
- **Paged agenda queries.** The rolling window is capped at two years; a user with a decade of
  imported history would benefit from real paging.

## 4. Feature roadmap

**Near term**
- Month-view swipe between months with prefetch of the adjacent grids.
- Recurring-event editing choices ("this and following", not only "this one" / "all").
- Widget for a single upcoming event, and a resizable day widget.
- Khmer voice input for the assistant, using the platform recogniser (still on-device where
  the device supports it).
- Per-category visibility filters in the month and agenda views.

**Medium term**
- Two-way sync with a user-supplied CalDAV server — strictly opt-in, strictly a server the
  user names, keeping the no-account default intact.
- Lunar-calendar recurrence: an anniversary that repeats on a lunar date rather than a
  Gregorian one, which is how memorial days are actually reckoned.
- Multiple named calendars beyond categories, with independent colours and visibility.
- Export to PDF for a printed month.

**Longer term**
- A migration to the LiteRT-LM engine API when the `.task` ecosystem settles; the `AiEngine`
  seam exists precisely so this is contained.
- A Khmer-tuned small model. Every phone-sized model is weak in Khmer today, and the model
  picker says so per model; a fine-tune on Khmer scheduling language would improve the one
  case that matters here.
- Wear OS complication.

## 5. Explicitly not planned

- **An account system.** Local-first is the product, not a limitation to be fixed.
- **Analytics or crash reporting that phones home.** Neither is worth the privacy claim the
  app makes.
- **Cloud AI.** The assistant runs locally or not at all.
- **Ads.**
