# Changelog

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
