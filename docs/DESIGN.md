# UI and UX specification

## 1. Who this is for

Khmer speakers in Cambodia, on the phones people actually carry — which includes four-year-old
mid-range devices, not only current flagships. Three consequences run through every screen:

- **Khmer is the default, not a translation.** `values/strings.xml` is English for tooling;
  `values-km/` is what ships as the default locale, listed first in `locales_config.xml`.
  Screen text is written in Khmer in the source rather than looked up from an English original.
- **One-handed.** Primary actions sit at the bottom — a five-item navigation bar and a FAB.
  Nothing essential lives only in a top-right overflow.
- **Minimal animation.** Transitions are the Compose defaults. A month grid that scrolls at 60
  fps on a 3 GB phone is worth more than a custom transition that drops frames on one.

## 2. Typography

Khmer script has taller ascenders and stacked subscript consonants; a line height tuned for
Latin clips them. The theme raises line heights and avoids tight tracking throughout, and the
font scale slider (0.8×–1.6×) multiplies on top of the system setting rather than replacing it.

Numerals are a real choice, not a cosmetic one: Khmer numerals (១២៣) are how dates are written,
Arabic numerals are how phone numbers and prices are written. `localeNumber()` routes every
number the calendar draws through one preference so the choice is applied consistently.

## 3. Colour

Material 3 with a user-chosen seed. Eight accent colours with Khmer names, plus dynamic colour
from the wallpaper on Android 12+. Light, dark and system.

Event colours are separate from the theme accent: a category carries a colour, an event may
override it, and both are drawn as a dot or a leading bar rather than as a filled background —
filled backgrounds fight the theme and hurt contrast at small sizes.

Holidays and weekends tint the day number rather than its background, so a day can be a
holiday, today, and have events without the cell becoming three overlapping fills.

## 4. Screens

| Screen | What it is for |
|---|---|
| **Home / dashboard** | Cards the user can show, hide and reorder: today, the lunar date, what is coming up, upcoming holidays, countdowns, tasks, today's note, plus a stats row. The default landing screen, changeable. |
| **Month** | The six-week grid. Day number, optional lunar day, an event dot, holiday and weekend tint. Tapping a day reveals that day's events below the grid. |
| **Week** | Seven columns with times, for a working week. |
| **Day** | One day in full, with the lunar date and any holiday in the headline. |
| **Agenda** | A rolling list grouped by day, extending as you scroll (90 days initially, out to two years). The screen to open when the question is "what is next". |
| **Search** | Debounced full-text over titles, descriptions and locations. |
| **Event detail** | Everything about one occurrence, with edit, duplicate, delete-this-one and delete-series. |
| **Add / edit event** | One form. Title, time, all-day, repeat, reminders, category, colour, location, notes; a task toggle. |
| **Assistant** | Chat, with a privacy banner and every reply labelled computed or generated. Proposals appear as a card you confirm. |
| **Holidays** | A year at a time, grouped by month, each row showing whether it is a day off and where the date came from. |
| **Settings** | Index → appearance, notifications, categories, backup, AI, about, plus the app lock. |
| **Lock** | PIN keypad with Khmer numerals; biometric prompt when enabled. |

## 5. States

Every list has three states and all three are drawn:

- **Loading** — the dashboard and the grid render their frame immediately and fill in; there
  is no full-screen spinner, because a calendar that flashes empty on every open reads as
  broken.
- **Empty** — `EmptyState` with an icon, a line of Khmer explaining what would appear here, and
  where useful an action. "No events" is not an error.
- **Error** — a message the user can act on. A failed export says why; a failed model load says
  why and leaves the calendar working.

Permission and capability gaps are surfaced where they matter, not at launch:

- Exact alarms: a banner on the notification settings screen, deep-linking to the system
  toggle, re-checked on resume.
- Notification permission: requested once, on first launch, and declining is allowed — the
  calendar works, reminders simply stay in-app.
- AI: the model screen states the device's RAM and the reason a model does not fit, rather
  than hiding it.

## 6. Density and size

Three grid densities (46 / 58 / 72 dp cell height) so the month view can be a glance or a
working surface. Layouts use weights and `fillMaxWidth`, so a small phone, a large phone and a
tablet all work without separate layouts; the accent swatch row scrolls horizontally rather
than clipping on a narrow screen.

## 7. Widgets

Two, both themeable independently of the app: their own light/dark/system choice, a background
opacity slider, and a lunar-date switch.

- **Month** — the grid, a header with the month and lunar date, a dot per busy day, a quick-add
  button. Tapping a day opens that day, not just the app.
- **Agenda** — what is actually next rather than a fixed day, so a widget checked in the evening
  is not a list of things already finished.

Both redraw when calendar data changes — the app watches the Room tables rather than hooking
each write path — and on the system's own 30-minute cycle.

## 8. Accessibility

- Every icon-only control has a Khmer `contentDescription`.
- Font scale respects and multiplies the system setting; nothing is drawn at a fixed sp size
  that ignores it.
- Touch targets are 48 dp or larger; the lock keypad uses 64 dp.
- Colour is never the only carrier of meaning — a holiday shows a badge and a source label, not
  just a tint.
