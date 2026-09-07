# Changelog

## 1.3.1 — 2026-09-07

A patch release, from continuing to audit settings that are written and never read. Three of
the four fixes below come from that one question: *does anything actually read this?*

### No event could be created after 11pm

A new event opens at the next whole hour. That was computed on a clock time with no date
attached, and a clock time wraps silently at midnight.

At 23:20, "the next whole hour" came out as 00:00 — and, keeping today's date, the editor
opened on a slot twenty-three hours in the past. Between 22:01 and 23:00 it was worse: the
editor opened 23:00 to 00:00, and its own validation then read that end as earlier than the
start and refused to save, with no way forward except editing both times by hand.

The arithmetic now carries the date, so 22:51 gives 23:00 today to midnight tomorrow, and
23:20 moves the whole event to tomorrow morning. Seven tests pin the awkward hours: 22:51,
23:20, exactly on the hour, a date picked from the calendar grid, and an eight-hour default
duration that lands the following morning.

Verified on the emulator at 22:57, which is exactly the minute that could not be saved before.

### The editor and the database disagreed about midnight

Storage has always treated an end at or before the start as an event running into the next
day. The editor refused to save one. The editor now allows it and says so — **បញ្ចប់នៅថ្ងៃបន្ទាប់**
appears under the end row — so a 10pm-to-2am shift is one event rather than a rejection, and
a mis-tapped end time is never a silent 23-hour event.

### The date format setting did nothing

**ការកំណត់ → រូបរាង** has offered a choice of three date orders since 1.1.0. Nothing in the app
read the setting. The function that would have applied it had no callers at all — you could
pick ថ្ងៃ/ខែ/ឆ្នាំ, watch the radio button move, and every date in the app stayed exactly as it
was.

It now reaches the event editor's start and end rows, the event detail headline, the day
headline shared by the month and week screens, and the search results. Each choice in
Appearance shows today's date rendered that way, so you can see the difference before picking
one.

There is a fourth choice now, **ថ្ងៃ ខែ ឆ្នាំ (ខ្មែរ)**, and it is the default. It is the written
Khmer form every screen already drew, so anyone who never opened this setting sees nothing
change; anyone who did pick a numeric order finally gets it.

One detail worth naming: the Khmer month name is looked up, not formatted. A date pattern's
`MMMM` resolves against the phone's locale, so on a device set to English it would have
printed "September" in the middle of an otherwise entirely Khmer screen. There is a test that
sets the JVM locale to US and to France and checks the month stays មករា.

### Searching for "%" returned your whole calendar

`%` and `_` are wildcards to SQLite's `LIKE`. Typed into the search box they went through
unescaped, so `%` matched every event and `_` matched any single character. Both are now
literal, and Khmer substring search — which is why this is a `LIKE` scan and not full-text
search in the first place — is unchanged.

### Tested

130 unit tests pass and lint is clean. Everything above was checked on an Android 8.0 (API 26)
emulator: switching the date format to dd/MM/yyyy and back to Khmer, the live previews in
Appearance, and creating and saving an event at 22:57.

### Privacy

Unchanged: no account, no analytics, no tracking, no ads. Your calendar never leaves the
device. The AI is optional, off by default, and runs entirely on-device.

## 1.3.0 — 2026-09-07

### The dashboard has a top

The Home tab opened straight into a date card. It now opens with a greeting that matches the
time of day — អរុណសួស្តី, ទិវាសួស្តី, សាយណ្ហសួស្តី, រាត្រីសួស្តី, using the same boundaries as the
Khmer parts of the day, so the greeting can never disagree with the line beneath it — the
date, a live clock, and what is next.

"What is next" prefers the next event as a relative time: **Backup-Roundtrip-Test ·
ក្នុងរយៈពេល ៤៩ នាទី**. A count of today's events is what is left to say when nothing is coming.
All-day entries are excluded, because "in 40 minutes" is meaningless for something with no
clock time and the dashboard would otherwise announce a birthday as though it were starting.

Its gradient is a low-alpha wash of the accent the user chose, not a second saturated block.
The work card's colour is doing a job — green means working, amber means a break — and two
competing blocks of colour mean neither reads as a signal.

### Fixed: three settings that did nothing

**Loading states did not exist.** `isLoading` was set on both the dashboard and the calendar
and read by nothing, so the first frame after a cold start was an *empty state* —
គ្មានព្រឹត្តិការណ៍ថ្ងៃនេះ — that corrected itself a moment later. Telling someone they have
nothing on and then taking it back is worse than saying nothing, because the empty state is a
claim. Skeleton placeholders now hold the layout until the data arrives.

**Work notification settings took effect tomorrow.** Alarms were armed at process start and by
the daily worker, and nowhere else. Turning notifications on, editing a shift, or changing the
warning time did nothing at all until the app was next launched. The settings are now observed
the way widget data already was — watch the state, not each write path — so the settings
screen, a restored backup, and anything added later all reach it, and none of them has to
remember to.

**Work alarms could never be cancelled.** Cancellation built a bare `Intent` while the armed
one carried a `data` URI, and `data` counts toward `Intent.filterEquals` — so `FLAG_NO_CREATE`
never matched and every alarm survived. The set of armed request codes also lived only in
memory, which put yesterday's alarms permanently out of reach after a restart. The practical
effect: **turning work notifications off left them firing.** The URI is now derived from the
request code, which makes it reproducible from nothing, and cancellation sweeps the whole code
range.

**One screen counted in Latin.** The event editor carried its own copy of the reminder label
that never learned about Khmer numerals, so it showed "10 នាទី" while notification settings —
the same information — showed "១០ នាទីមុន".

### New: a warning before the shift changes

Optional, off by default, and configurable at 5, 10, 15 or 30 minutes in
**ការកំណត់ → កាលវិភាគការងារ**. Off by default because a warning doubles the day's notifications,
and these share a notification channel with event reminders — a countdown that announces itself
too often gets the whole channel silenced.

### Verified on device

On an Android 15 emulator, against this build:

- The alarm fix, by cycling the setting and reading the armed alarms back out of the system:
  30 min → 17:00 + 17:30; 15 min → 17:15 + 17:30; off → 17:30 only; 5 min → 17:25 + 17:30;
  notifications off → none. Previously the stale alarms simply accumulated.
- The live re-arm, by changing the setting without restarting the app.
- **A full backup round-trip**: export to JSON (title, location, UTC millis, zone and reminder
  all faithful), wipe all app data, restore — the event came back. Restore states that it adds
  to existing data rather than replacing it, and asks first.
- **A full `.ics` round-trip**: export produced valid RFC 5545 with `DTSTART` correctly
  converted to UTC and the reminder as `TRIGGER:-PT30M`; importing it back created the event.
- The greeting, clock, next-event line and skeletons.
- Both widget providers registered, and a widget update with no crash.

## 1.2.0 — 2026-09-07

### The assistant understands a pasted announcement

Before this release the assistant could read a short command — "ថ្ងៃស្អែក ម៉ោង ២ រសៀល ប្រជុំ" —
but not a paragraph. Given one it produced a title that was the whole paragraph, because the
title was built by deleting the date and time from the input and keeping whatever was left.

`LongTextEventExtractor` treats a paragraph as a paragraph: it splits the text into
sentences, scores each for event signal, parses only the one making the announcement, and
builds the title *around the event noun* instead of from the leftovers. Everything else
becomes the description.

Pasting the announcement below now yields the title កិច្ចប្រជុំប្រចាំសប្តាហ៍, tomorrow's date,
2:00 PM, the room បន្ទប់ប្រជុំ A, and the discussion as the description:

> សួស្តីក្រុមការងារ នៅថ្ងៃស្អែកយើងនឹងមានកិច្ចប្រជុំប្រចាំសប្តាហ៍នៅម៉ោង ២ រសៀល នៅបន្ទប់ប្រជុំ A
> ដើម្បីពិភាក្សាអំពីផែនការការងារប្រចាំខែ …

Nothing is invented. "ប្រជុំជាមួយក្រុមការងារថ្ងៃស្អែក" gets tomorrow's date and **no time at
all** — the proposal says ម៉ោងមិនបានបញ្ជាក់ rather than guessing 9:00. Every field the text did
not state is listed back to the user instead of filled in.

The extractor is pure and needs no model: it costs microseconds, works on a device that could
never host a language model, and cannot hallucinate. When a model *is* loaded it is asked as
well, and its answer is validated — title capped at eight words, dates outside a sane window
rejected, malformed output discarded — before it can reach the screen.

Long text is handled by scoring sentences and narrowing to the region around the strongest
one, so a meeting buried in a long email does not take its date from an unrelated line three
paragraphs away. Input is capped rather than allowed to grow without bound.

Text can now reach the assistant by **Share** and **Process Text** from any other app, which
is how a pasted announcement actually arrives.

### Fixed: a whole class of bug caused by Khmer word spacing

Khmer is written without spaces between words. "កិច្ចប្រជុំប្រចាំសប្តាហ៍នៅម៉ោង" is a single
whitespace token holding a noun, a preposition and the word for "hour", so every "take the
next four words" heuristic in the parser silently swallowed an entire clause.

Two visible symptoms, one cause:

- Event titles ran on into the meeting time.
- The room in a pasted announcement was lost. The sentence contains នៅ three times; the first
  introduces a date and the second a time, and only the third is a place. The parser took the
  first one and produced a "location" so long it was then discarded.

`TextBoundaries` cuts by character index at the next marker of a *different kind* — a time, a
date, a place, a person, a purpose — and every affected site now uses it. Latin markers are
only matched when the lexicon already spaces them out, so an English title is not cut in half
at "May" or "at".

### Fixed: Khmer numerals came back as Latin ones

The parser matched against its own ASCII-normalised working string and then sliced values out
of *that*, so a room written "សាលប្រជុំធំជាន់ទី ៣" came back as "…ទី 3" in a calendar that is
otherwise entirely in Khmer numerals. Matching still runs on ASCII digits; the two views are
now index-aligned, and anything handed back to the user is sliced from the string they typed.

### New: the work-time countdown

A live countdown for the working day, on the home dashboard. It knows six states — before
work, working, break, finished, day off, disabled — and shows the time left in the current
block, what comes next and when, the total left today, and a progress ring.

Defaults match an ordinary Cambodian office week: Monday to Friday 7:30–11:30 and
13:30–17:30, Saturday 7:30–11:30, Sunday off.

Every day is editable in **Settings → កាលវិភាគការងារ**, including how many blocks it has: a
shop that opens straight through and a teacher with three separate sessions are equally
ordinary, and neither is expressible if the screen only offers "morning" and "afternoon".
Inverted ranges are refused rather than saved.

Optional notifications at each shift change, off by default. They are inexact alarms on
purpose — a shift notice a minute late costs nothing, and an exact alarm would spend a
battery-relevant resource that event reminders need more.

The card only ticks while it is on screen and the app is in the foreground, and stops
entirely on a day with nothing left to count down to.

### The dashboard is now yours to arrange

Nine cards — today, work, Khmer lunar date, upcoming events, holidays, countdowns, tasks,
today's note, quick actions — each of which can be hidden and moved. A saved order from an
earlier version is kept, and cards added by a newer version are appended to it rather than
replacing it.

Quick actions adds a row for the five things people open the app to do: new event, new task,
new note, assistant, search.

### Fixed: two dialogs that ignored the theme

A platform dialog inherits the *Activity's* theme, and the Activity theme is a fixed light one
because every pixel after the first frame is drawn by Compose. So a user with the app set to
dark got a white date or time picker, and everyone got Material's default teal on a screen
with no teal anywhere else. A `values-night` resource cannot fix it either — that follows the
*system*, and the app's own light/dark setting may disagree with it.

The theme is now chosen in code from the same flag the Compose theme uses, and the buttons are
tinted with the user's live accent.

Separately, the event time picker asked the *device* for 12- or 24-hour rather than the app's
preference, so choosing a clock format in settings changed every time in the calendar except
the one being typed into.

### One shape scale for the whole app

`MaterialTheme.shapes` was never set, so Material's own cards, chips and dialogs rounded to
their defaults while hand-drawn surfaces used ten different radii — on the same screens.
`Tokens.kt` holds the scale that was missing (spacing, radius, elevation) and the theme feeds
the radii to Material so both halves of a screen agree. Gradients moved into the theme under
names that say what they mean — working, resting, done — rather than what colour they are.

### The AI download crash fix moved to where it is enforced

The `android:foregroundServiceType="dataSync"` declaration that fixed the 1.1.0 crash now
lives in `:ai-core`, beside `ModelDownloadWorker`, the only thing that needs it. In the app
module it was correct but unenforced; next to the worker, lint's
`SpecifyForegroundServiceType` check verifies the pair, so the declaration cannot drift away
from the code that depends on it. The merged manifest is unchanged.

### Verified on device

On an Android 15 (API 35) emulator, against this build:

- Both pasted announcements from the specification, end to end through Share.
- A full model download (547 MB), model load and on-device inference — process alive
  throughout, no `FATAL EXCEPTION`.
- The work countdown ticking, with its arithmetic checked against the clock.
- The work schedule screen: display, edit, add a block, reset, and persistence across a
  restart.
- Dashboard reorder and hide.
- The date and time pickers in dark mode.
- **An upgrade from 1.1.0 in place**: an event with its title, times and location survived,
  and so did a customised set of hidden dashboard cards — with the two new cards appended
  rather than replacing them.

## 1.1.0 — 2026-09-07

### Fixed: the app closed when downloading an AI model

**Root cause.** `ModelDownloadWorker` called `setForeground()` with
`FOREGROUND_SERVICE_TYPE_DATA_SYNC`, but WorkManager's `SystemForegroundService` — which the
app inherits from the library's manifest — declared no `foregroundServiceType`. From
Android 14 the system rejects that mismatch:

```
FATAL EXCEPTION: main
java.lang.IllegalArgumentException: foregroundServiceType 0x00000001 is not a subset of
foregroundServiceType attribute 0x00000000 in service element of manifest file
    at androidx.work.impl.foreground.SystemForegroundService.startForeground
```

It is thrown inside `Service.onStartCommand` **on the main thread**, so the worker's
`runCatching { setForeground(info) }` could never catch it — different thread, different call
stack. The process died instantly, which is what the user saw as "the app closes by itself".

**Fix.** `AndroidManifest.xml` re-declares WorkManager's service with
`android:foregroundServiceType="dataSync"` via `tools:node="merge"`.

**Verified** on an Android 15 (API 35) emulator: reproduced the crash on the old build,
confirmed zero `FATAL EXCEPTION` on the new one, and ran a real 547 MB download to completion.

### Hardened the whole download path

Beyond the crash, the download had failure modes that produced a spinner that never ended:

- **Bounded retries.** `Result.retry()` had no ceiling, so a download that could never succeed
  rescheduled with backoff forever while the UI showed "running". Now four attempts, and only
  for genuinely transient failures.
- **Typed failures.** `DownloadFailure` — `NETWORK`, `STORAGE_FULL`, `SERVER`, `CORRUPT`,
  `CANCELLED`, `UNSUPPORTED`, `UNKNOWN` — each with a Khmer message and the action that
  actually resolves it. A retry button against a full disk is a loop, not an action, so
  `STORAGE_FULL` offers to free the partial file instead.
- **Storage preflight**, and a full disk detected mid-write is now terminal rather than retried.
- **Foreground promotion is optional.** If the system refuses it — background-start
  restrictions, OEM policy, Android 15's data-sync quota — the transfer carries on.
- **`getForegroundInfo()` can no longer throw**; it falls back to a notification built inside
  `:ai-core` rather than failing the job.
- **`ExistingWorkPolicy.KEEP`, not `REPLACE`.** Tapping download twice used to cancel the
  running transfer and start again from the same byte.
- **Throttled progress** to about 1.4 updates/second instead of one `setForeground` per percent.
- **Validation before install.** The `.part` file is only renamed into place after its length
  matches the published size.

### Download and model-management UX

- **Confirmation before download**, showing file size, remaining bytes when resuming, and the
  RAM the model needs. Mobile data is metered; a 2 GB download from a mis-tap is a real cost.
- **Live progress** with percent, downloaded/total, speed and estimated time remaining.
- **Pause / resume / cancel-and-discard.** Pause keeps the partial file; resume continues from
  the exact byte via a Range request.
- **Per-model state**: installed, partially downloaded (with how far), or damaged — each with
  the right buttons.
- **Actionable error card** naming the failure, confirming calendar data is untouched, and
  offering retry / discard.
- All numbers in these screens now follow the Khmer-numeral preference; the progress line used
  to read `៣% · 21 MB`, mixing scripts mid-sentence.

### Removed a false integrity check

An earlier version of this work added a magic-byte check on downloaded bundles. Testing
against the real files showed it was wrong: Qwen `.task` bundles are a length-prefixed zip
(`PK` at offset 4) and the Gemma one is a length-prefixed TFLite flatbuffer (`TFL3` at
offset 4). The check rejected correctly downloaded models as damaged. Removed — a check that
fails valid input is worse than no check. Length validation against the exact published
`Content-Length` remains, and `docs/AI_MODELS.md` explains what that does and does not catch.

### Monday is the first day of the week

- The default is now Monday (Cambodian and ISO-8601 practice), changeable to **any** of the
  seven days rather than the previous shortlist of three.
- New `CalendarWeek` in `:calendar-core` is the single source of week arithmetic, used by the
  month grid, week view, agenda, week numbers, both widgets and the recurrence engine.
- **Fixed a real correctness bug:** `RecurrenceExpander` hard-coded Sunday as the week start
  while the UI read the preference, so a fortnightly event could expand to different dates than
  the grid drew. `RecurrenceRule` now carries RFC 5545 `WKST`, which round-trips through the
  `.ics` export and is only emitted when it can change the expansion.

### Two settings that did nothing now do something

- **Week numbers** was persisted and toggleable but read by no screen. The month grid now draws
  a week-number gutter, numbered from the user's own first day of the week with a four-day
  minimum — ISO-8601 numbering under the Monday default.
- **Holiday notifications** was likewise dead. Public holidays now notify at 18:00 the evening
  before, when there is still time to change plans. Observances are deliberately excluded.

### New customisation

- **12/24-hour clock** — system, 12-hour or 24-hour. The 12-hour form uses the Khmer parts of
  the day (`២ រសៀល`) rather than AM/PM.
- **Date format** — D/M/Y, Y-M-D or M/D/Y.
- **Working hours**, used by free-slot suggestions.
- All time rendering now goes through one `CalendarFormats`. It was previously nine copies of
  `"%02d:%02d".format(...)` across screens, notifications, widgets and the assistant — which is
  why a clock preference could not be added without finding all nine.

### Application updates

New update screen under Settings → About:

- Shows the installed version, checks a `version.json` manifest in the project repository,
  and displays the changelog for a newer release.
- Downloads with progress, then **verifies** the file: length, an optional published SHA-256,
  and — the check that matters — that the APK's signing certificate matches the installed
  app's. A mismatched signature is refused before the user reaches Android's installer with an
  error they cannot act on.
- Installation goes through the platform installer with its own confirmation. Nothing is
  installed silently, and nothing is checked in the background: the request happens when the
  user opens the screen or taps the button.
- `REQUEST_INSTALL_PACKAGES` and a `FileProvider` scoped to `cache/updates` only.

### Other fixes

- `NotificationSettingsScreen` used the deprecated `getParcelableExtra`; now `IntentCompat`.
- `androidx.biometric:1.1.0` resolved fragment to 1.2.x, predating the ActivityResult APIs
  `MainActivity` uses; fragment is pinned to 1.8.6. This was a lint-fatal error.
- `backup_rules.xml` / `data_extraction_rules.xml` excluded `models/` from a domain that never
  included it — a lint-fatal no-op, removed.
- Glance widgets only redrew on the system's 30-minute cycle; the Application now watches the
  Room tables and redraws on any data change, debounced.
- `MediaPipeEngine` suppresses the `LlmInference` deprecation with a note pointing at the
  `AiEngine` seam that contains a future migration.

### Tests

67 unit tests, all passing.

- New `CalendarWeekTest` (8): week start, grid alignment across every start day, ISO week
  numbers, weekend definition, preference fallback.
- New `WeekStartRecurrenceTest` (4): `WKST` invisible at interval 1, decisive at interval 2,
  round-tripping through the RRULE, defaulting to Monday per RFC 5545.
- New `ModelIntegrityTest` (7): partial vs damaged vs missing, over-long files, failure-name
  round-trips, progress bounds.

### Privacy — unchanged

No account, no analytics, no tracking, no cloud AI, no API keys, no calendar data leaving the
device. Network is used in exactly three places, all user-initiated: the model download, the
update check, and the update download.

---

## 1.0.0 — 2026-09-07

First release. Khmer calendar with Chhankitek lunar dates, Cambodian holidays, events,
reminders, recurrence, tasks, notes, widgets, backup/restore, `.ics` interchange, and an
optional on-device assistant.
