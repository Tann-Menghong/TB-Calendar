# The Khmer calendar in this app

This document says exactly what the calendar arithmetic does, where it comes from, and where
it stops. The app makes no claim to be authoritative; it implements a published algorithm and
tells you its limits.

Implementation: [`calendar-core/.../khmer/Chhankitek.kt`](../calendar-core/src/main/kotlin/com/khmercalendar/core/khmer/Chhankitek.kt)
and [`KhmerNewYear.kt`](../calendar-core/src/main/kotlin/com/khmercalendar/core/khmer/KhmerNewYear.kt).

---

## 1. What is being computed

Cambodia uses two reckonings side by side.

**ចន្ទគតិ (Chhankitek)** — the lunisolar calendar that dates religious and cultural
observances. A date is written as a day within a half-month, a phase, and a lunar month:
*១៥ កើត ខែពិសាខ* is the fifteenth waxing day of Pisakh. Months alternate 29 and 30 days;
the year is 354 days, 355 in a leap-day year (ចន្ទ្រាធិមាស), or 384 in a leap-month year
(អធិកមាស).

**សុរិយគតិ (Soriyakti)** — the solar reckoning that fixes Khmer New Year. It is not
1 April or 13 April by definition; the moment the sun enters Aries is computed each year and
the date follows from it.

The app also shows:

- **ពុទ្ធសករាជ (Buddhist Era)** — Gregorian + 543, rolling at Visak Bochea rather than
  1 January, which is why the BE year shown in March differs from a naive +543.
- **ចុល្លសករាជ (Jolak Sakaraj)** — the Lesser Era year, which rolls at Khmer New Year.
- The **twelve-animal cycle** (ជូត, ឆ្លូវ, ខាល…) and the **ten-era cycle** (ស័ក).

---

## 2. Source of the algorithm

The Chhankitek arithmetic — អាហារគុណ (Aharkun), អាវមាន (Avoman), បូតិថី (Bodithey),
ក្រមធុបុល (Kromthupul), and the Protetin leap-resolution rules — follows:

- **Phylypo Tum**, *Khmer Calendar* (Cam-CC), the published description of the reckoning and
  its constants.
- **`momentkh`** (Sovichet Tep), the JavaScript reference implementation of the same
  algorithm, used here to cross-check.

The Khmer New Year solar reckoning (រាសី / អង្សា / លិប្ដា, ខាន់, ពួយចលិប, ផល, មហាសង្ក្រាន្ត,
ឡើងស័ក) follows the same sources.

The constants are **not free parameters**. `292207/800`, `692`, `11×Aharkun + 25` and the rest
are the reckoning; changing one does not "tune" the calendar, it produces a different calendar.

### Epoch

1 January 1900 = **១ កើត ខែបុស្ស**. Every conversion is an offset from that day.

---

## 3. Two deliberate departures from the reference

Both are documented in the source at the point where they occur.

**A precomputed month table.** The reference walks month by month from the epoch for every
conversion — 3,600 iterations for a date in 2200. This app builds the start day and index of
every lunar month from the epoch to the horizon once, then binary-searches it. Same output,
constant-time lookup, which is what makes a month grid of 42 lunar dates cheap enough to
render while scrolling.

**A month's length is taken from its own start.** The reference computes the length a second
time at the end using the *target's* Buddhist year, which differs from the month's own year
for months straddling the April/May boundary. That can report **១៥ រោច in a 29-day month** —
a date that does not exist. Advancing while `elapsed >= length`, with each length taken at
that month's own start, keeps the sequence self-consistent.

---

## 4. Supported range

```
Chhankitek.MIN_DATE = 1900-01-01   (the epoch; there is no anchor before it)
Chhankitek.MAX_DATE = 2199-12-31   (the horizon of the precomputed table)
```

Outside that range, `toLunar()` throws `IllegalArgumentException`. **Anything rendering a
user-chosen date calls `toLunarOrNull()`** and simply omits the lunar line — the holidays
screen says so explicitly rather than showing a wrong date.

`Chhankitek.isSupported(date)` is the check.

---

## 5. Accuracy — what is and is not claimed

**Claimed:** the implementation reproduces the cited algorithm, and its output is verified
against known dates in `ChhankitekTest`.

**Not claimed:** that the algorithm agrees with every published Cambodian almanac in every
year. The traditional reckoning is arithmetic, not astronomical, and printed almanacs
occasionally differ by a day where a royal decree or a monastic determination has adjusted an
observance. Where a difference is known, this app follows the decree — see
[HOLIDAYS.md](HOLIDAYS.md).

If you find a disagreement with an authoritative almanac, it is a bug worth reporting, with
the date and the source.

---

## 6. Storage and time zones

Events are stored as **UTC milliseconds plus the IANA zone they were created in**. That is
what lets a 08:00 meeting created in Phnom Penh still read 08:00 after the phone lands in
Bangkok, while an instant-based reminder still fires at the right moment.

**All-day events are pinned to UTC midnight**, following the `CalendarContract` convention.
An all-day event is a *date*, not an instant; storing it at local midnight makes it slide a
day the moment the device changes zone. There is a test for exactly this.

Repeating events are **never materialised as rows**. The database stores one row plus an
RFC 5545 `RRULE`, and occurrences are expanded on demand for the visible window. A deleted
single occurrence writes an exception row rather than splitting the series, so a later edit
to the series still reaches every remaining occurrence.
