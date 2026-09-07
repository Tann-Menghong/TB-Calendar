# TB-Calendar — ប្រតិទិនខ្មែរ

An offline-first Khmer calendar for Android. Khmer is the primary language, the Chhankitek
lunar calendar sits alongside the Gregorian one, and an optional small language model runs
entirely on the device.

Nothing in this app needs an account, and no calendar data ever leaves the phone.

---

## What it does

**Calendar**
- Month, week, day and agenda views, plus a dashboard you can rearrange.
- Khmer lunar dates (ចន្ទគតិ) — waxing/waning day, lunar month, animal year, era year, and
  the Buddhist Era alongside the Gregorian year.
- Cambodian public holidays and observances, both the fixed-date ones and the ones computed
  from the lunar calendar.
- Khmer numerals throughout, switchable to Arabic numerals.

**Events and reminders**
- Create, edit, duplicate, delete; titles, descriptions, locations, all-day, colour, category.
- Repeating events (daily / weekly / monthly / yearly, with interval, BYDAY, COUNT and UNTIL),
  and "delete just this one" without splitting the series.
- Several reminders per event, delivered by exact alarms and re-armed after reboot.
- Tasks you can tick off, day notes, countdowns, search, conflict detection.

**Customisation**
- Light / dark / system, eight accent colours, dynamic colour on Android 12+.
- Font scale, three grid densities, an optional background image with an opacity control.
- Show or hide lunar dates, Gregorian dates, holidays, event dots, week numbers.
- Notification sound, vibration, default reminder and default duration.
- Two home-screen widgets — month grid and agenda — with their own theme, opacity and
  lunar-date switches.

**On-device assistant** (optional)
- Turns Khmer sentences into event drafts: *"ថ្ងៃស្អែកម៉ោង ២ រសៀល រំលឹកខ្ញុំឱ្យប្រជុំជាមួយក្រុមការងារ"*
  becomes a draft with date, time, title and reminder, which you confirm before it is saved.
- Summarises what is coming up, finds clashes, suggests free slots.
- Runs a quantised model locally through the MediaPipe LLM Inference API. There is no cloud
  API, no API key, and no telemetry.
- **The calendar is fully usable with the assistant switched off.** Every scheduling answer —
  summaries, conflicts, free slots, and the date arithmetic behind a parsed sentence — is
  computed from the database, not generated. The model only helps with wording it cannot get
  from rules.

**Privacy and portability**
- Local-first storage, no account, no analytics.
- Optional PIN and biometric app lock.
- Full JSON backup and restore, and `.ics` import/export, through the Storage Access
  Framework — so a backup goes wherever you choose, with no storage permission.

---

## Requirements

| | |
|---|---|
| Minimum Android | 8.0 (API 26) |
| Target / compile SDK | 36 |
| Recommended for the calendar | any device Android 8.0 will run |
| Required for the assistant | 64-bit ABI (`arm64-v8a`), ≥ 3 GB RAM, ~0.6–2 GB free storage |
| Recommended for the assistant | ≥ 6 GB RAM for the 1.5B model, ≥ 8 GB for Gemma |

The app checks the device before it offers a model, and says why one will not fit rather than
letting you download three gigabytes and fail at load time. See
[docs/AI_MODELS.md](docs/AI_MODELS.md).

---

## Building

```bash
git clone https://github.com/Tann-Menghong/TB-Calendar.git
cd TB-Calendar
./gradlew :app:assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`.

Requires JDK 17+ (Android Studio's bundled JBR works) and an Android SDK with platform 36.
Point `local.properties` at it:

```properties
sdk.dir=C\:\\Users\\you\\AppData\\Local\\Android\\Sdk
```

### Release build

Release signing is optional — without a keystore the release variant still assembles, it is
simply unsigned. To sign it, create `keystore.properties` in the project root:

```properties
storeFile=release.jks
storePassword=…
keyAlias=…
keyPassword=…
```

and generate the keystore:

```bash
keytool -genkeypair -v -keystore release.jks -alias tbcalendar \
  -keyalg RSA -keysize 2048 -validity 10000
```

Then:

```bash
./gradlew :app:assembleRelease
```

Both `keystore.properties` and `release.jks` are gitignored. **Keep a backup of the keystore** —
losing it means you can never ship an update to an already-installed copy.

### Tests

```bash
./gradlew :calendar-core:test :app:testDebugUnitTest
```

`:calendar-core` carries the calendar arithmetic tests — Chhankitek conversion against known
dates, holiday computation, recurrence expansion, and the Khmer natural-language parser.
`:app` tests the occurrence expansion against a real in-memory Room database.

---

## Project layout

```
:calendar-core   Pure JVM. Chhankitek conversion, Khmer New Year reckoning, holidays,
                 recurrence expansion, the Khmer NLU parser. No Android dependencies,
                 so it is testable on the JVM and reusable elsewhere.
:ai-core         Android library. The AiEngine interface, the MediaPipe implementation,
                 the model catalogue, device-capability checks, the download worker,
                 and the rules-first assistant. Knows nothing about the UI.
:app             Room, DataStore, notifications, widgets, and the whole Compose UI.
```

The assistant is deliberately behind an interface (`AiEngine`) with a `NoOpAiEngine` default,
so swapping the runtime or the model does not touch the calendar. See
[docs/AI_MODELS.md](docs/AI_MODELS.md).

## Documentation

- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — modules, data flow, the decisions and why.
- [docs/CALENDAR.md](docs/CALENDAR.md) — the Chhankitek algorithm, its source, its limits.
- [docs/HOLIDAYS.md](docs/HOLIDAYS.md) — which holidays are computed, which are gazetted,
  and how to correct a year.
- [docs/AI_MODELS.md](docs/AI_MODELS.md) — how to replace, add or sideload a model.

## Licence and attribution

The Chhankitek implementation follows the algorithm published by Phylypo Tum (Cam-CC) and the
`momentkh` project. See [docs/CALENDAR.md](docs/CALENDAR.md) for the specific references and
for what this app does and does not claim about its accuracy.
