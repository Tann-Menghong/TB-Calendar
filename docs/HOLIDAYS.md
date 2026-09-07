# Cambodian holidays

Implementation: [`calendar-core/.../holiday/KhmerHolidays.kt`](../calendar-core/src/main/kotlin/com/khmercalendar/core/holiday/KhmerHolidays.kt)

Holidays are **computed**, not listed year by year, so the calendar stays correct in years
nobody has hand-entered. The exception is the handful of dates set by annual sub-decree,
which are listed and win over the computed value.

---

## 1. The three sources

Every holiday the app shows carries a `HolidaySource`, and the holidays screen displays it:

| Source | Khmer label | Meaning |
|---|---|---|
| `FIXED` | កាលបរិច្ឆេទថេរ | A fixed Gregorian date. 7 January, 1 May, 9 November. |
| `LUNAR` | គណនាចន្ទគតិ | Computed from the Chhankitek reckoning — Visak Bochea, Pchum Ben, the Water Festival, Khmer New Year. |
| `GAZETTED` | អនុក្រឹត្យ | A date published in a sub-decree that differs from the computed one. |

They are also split by `HolidayKind`:

- `PUBLIC` (ឈប់សម្រាក) — a day off under the current schedule.
- `OBSERVANCE` (ពិធីបុណ្យ) — widely observed but not a day off.

The list reflects the **2020 reform**, which cut the public-holiday schedule to its present
size. Days it removed but which are still observed — Meak Bochea, International Women's Day —
are kept as `OBSERVANCE` rather than deleted, because people still plan around them.

---

## 2. What is fixed

| Date | | |
|---|---|---|
| 1 Jan | ទិវាចូលឆ្នាំសាកល | International New Year |
| 7 Jan | ទិវាជ័យជម្នះ ៧ មករា | Victory over Genocide Day |
| 8 Mar | ទិវានារីអន្តរជាតិ | International Women's Day *(observance)* |
| 1 May | ទិវាពលកម្មអន្តរជាតិ | International Labour Day |
| 14 May | បុណ្យចម្រើនព្រះជន្មព្រះមហាក្សត្រ | King Norodom Sihamoni's Birthday |
| 18 Jun | ទិវាព្រះរាជសម្ភពសម្តេចព្រះមហាក្សត្រី | Birthday of the Queen Mother |
| 24 Sep | ទិវារដ្ឋធម្មនុញ្ញ | Constitution Day |
| 15 Oct | ពិធីគោរពព្រះវិញ្ញាណក្ខន្ធព្រះបរមរតនកោដ្ឋ | Commemoration of the King Father |
| 29 Oct | ព្រះរាជពិធីគ្រងព្រះបរមរាជសម្បត្តិ | Coronation Day |
| 9 Nov | ទិវាបុណ្យឯករាជ្យជាតិ | Independence Day |

## 3. What is computed

| Holiday | Rule |
|---|---|
| បុណ្យចូលឆ្នាំថ្មី (Khmer New Year) | The solar reckoning in `KhmerNewYear.kt` — មហាសង្ក្រាន្ត through ឡើងស័ក, three or four days depending on the year. |
| ពិធីបុណ្យមាឃបូជា (Meak Bochea) | Full moon of ខែមាឃ *(observance)* |
| ពិធីបុណ្យវិសាខបូជា (Visak Bochea) | Full moon of ខែពិសាខ |
| ព្រះរាជពិធីច្រត់ព្រះនង្គ័ល (Royal Ploughing) | ៤រោច ខែពិសាខ |
| ពិធីបុណ្យភ្ជុំបិណ្ឌ (Pchum Ben) | ១៤រោច ខែភទ្របទ and the two days around it |
| ព្រះរាជពិធីបុណ្យអុំទូក (Water Festival) | Full moon of ខែកត្តិក and the two days around it |

Because these depend on `Chhankitek`, they exist only inside its supported range
(1900–2199). Outside it `forYear()` returns an empty list and the holidays screen says so.

---

## 4. Correcting a year from a sub-decree

The lunar observance and the day off are not the same thing. Cambodia sets its public-holiday
calendar by annual sub-decree, and the gazetted range occasionally differs from the
traditional reckoning by a day — the Water Festival in particular is sometimes shifted.

To track a new sub-decree, add an entry to `GAZETTED_OVERRIDES` in `KhmerHolidays.kt`:

```kotlin
private val GAZETTED_OVERRIDES: Map<Int, Map<String, List<LocalDate>>> = mapOf(
    2027 to mapOf(
        // Sub-decree No. NNN (date), cite it here.
        "water" to listOf(
            LocalDate.of(2027, 11, 12),
            LocalDate.of(2027, 11, 13),
            LocalDate.of(2027, 11, 14),
        ),
    ),
)
```

Keys currently understood: `"water"`, `"pchumben"`.

Rules:

- **Cite the sub-decree** in a comment. An override with no citation is indistinguishable
  from a guess, and a guess is worse than the computed date.
- Add an override **only** where the gazetted dates genuinely differ from the computed ones.
  If they agree, leave it out — the computed value is self-maintaining and an override is one
  more thing to get stale.
- Anything overridden is shown to the user as `GAZETTED` (អនុក្រឹត្យ), so the difference is
  visible rather than silent.

> **The one override currently in the file** is the 2026 Water Festival. Verify it against the
> published sub-decree before relying on it; if it agrees with the computed date, delete it.

## 5. Tests

`KhmerHolidaysTest` in `:calendar-core` covers the fixed dates, the kind/source split, the
range behaviour, and that an override wins over the computed date. Run:

```bash
./gradlew :calendar-core:test
```
