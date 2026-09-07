# Changelog

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
