package com.khmercalendar.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khmercalendar.core.khmer.CalendarWeek
import com.khmercalendar.core.khmer.KhmerTerms
import com.khmercalendar.domain.YearDay
import com.khmercalendar.domain.YearMonthGrid
import com.khmercalendar.domain.YearSummary
import com.khmercalendar.ui.components.localeNumber
import com.khmercalendar.ui.theme.LocalAppSettings
import com.khmercalendar.ui.theme.Spacing
import java.time.LocalDate
import java.time.YearMonth

/**
 * The year, as twelve miniature months.
 *
 * ## Why the dates are drawn at all
 *
 * The cheap version of a year view is twelve shaded rectangles, and it is a chart rather than
 * a calendar: you cannot point at a date in it. Twelve real grids let somebody find the 15th
 * of March and tap it, which is what a year view is actually used for - "which month is the
 * exam in", "when is the quiet stretch", "how far away is that".
 *
 * ## Why the density is a dot and not a fill
 *
 * At this size a shaded cell and a selected cell are indistinguishable, and shading twelve
 * grids turns the page into noise. A dot under the number says "something is on" without
 * competing with the date, and today keeps a filled ring so it is still findable in 365 cells.
 *
 * ## Reading it with a screen reader
 *
 * Each cell would otherwise announce a bare number 365 times. Every day carries its own
 * description - the date, and what is on it - and the empty padding cells are skipped
 * entirely rather than announced as blanks.
 */
@Composable
fun YearScreen(
    viewModel: CalendarViewModel,
    onOpenMonth: (YearMonth) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.yearState.collectAsStateWithLifecycle()
    val settings = LocalAppSettings.current

    Column(modifier.fillMaxSize()) {
        state.outOfRangeMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = 4.dp),
                textAlign = TextAlign.Center,
            )
        }

        YearHeadline(state.summary, state.year)

        LazyVerticalGrid(
            columns = GridCells.Fixed(COLUMNS),
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = Spacing.sm, end = Spacing.sm, bottom = 96.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            items(state.months, key = { it.month.toString() }) { grid ->
                MiniMonth(
                    grid = grid,
                    weekStart = settings.weekStart,
                    khmerNumerals = settings.useKhmerNumerals,
                    onOpenMonth = { onOpenMonth(grid.month) },
                    onOpenDay = onOpenDay,
                )
            }
        }
    }
}

/**
 * What the year adds up to.
 *
 * Names the busiest month, which is the one thing twelve grids of dots are worst at: two
 * nearly equal months look identical, and "which month is worst" is exactly what somebody
 * planning a year wants to know.
 */
@Composable
private fun YearHeadline(summary: YearSummary, year: Int) {
    val khmerNumerals = LocalAppSettings.current.useKhmerNumerals
    if (summary.total == 0 && summary.holidays == 0) return

    val parts = buildList {
        if (summary.total > 0) add("${localeNumber(summary.total, khmerNumerals)} ព្រឹត្តិការណ៍")
        summary.busiest?.let { add("ច្រើនបំផុត ${KhmerTerms.solarMonth(it.monthValue)}") }
        if (summary.holidays > 0) {
            add("${localeNumber(summary.holidays, khmerNumerals)} បុណ្យជាតិ")
        }
    }
    Text(
        text = parts.joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
    )
}

/** One month of the twelve. Tapping its name opens that month; tapping a day opens the day. */
@Composable
private fun MiniMonth(
    grid: YearMonthGrid,
    weekStart: java.time.DayOfWeek,
    khmerNumerals: Boolean,
    onOpenMonth: () -> Unit,
    onOpenDay: (LocalDate) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .padding(horizontal = 2.dp, vertical = 4.dp),
    ) {
        Text(
            text = KhmerTerms.solarMonth(grid.month.monthValue),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (grid.total > 0) scheme.onSurface else scheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onOpenMonth)
                .padding(vertical = 2.dp),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(2.dp))

        Row(Modifier.fillMaxWidth()) {
            CalendarWeek.daysOfWeek(weekStart).forEach { day ->
                Text(
                    text = KhmerTerms.dayOfWeekShort(day).take(1),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = CAPTION_SP.sp,
                    color = scheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }

        grid.weeks.forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    if (day == null) {
                        // Padding, not a day. Given no semantics at all so a screen reader
                        // does not read six blanks before every January.
                        Spacer(Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        MiniDay(
                            day = day,
                            khmerNumerals = khmerNumerals,
                            modifier = Modifier.weight(1f),
                            onClick = { onOpenDay(day.date) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniDay(
    day: YearDay,
    khmerNumerals: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val description = describe(day, khmerNumerals)

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .then(
                if (day.isToday) Modifier.background(scheme.primary, CircleShape) else Modifier,
            )
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = localeNumber(day.date.dayOfMonth, khmerNumerals),
                fontSize = DAY_SP.sp,
                lineHeight = (DAY_SP + 1).sp,
                fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    day.isToday -> scheme.onPrimary
                    day.isHoliday -> scheme.error
                    else -> scheme.onSurface
                },
            )
            // The density mark. A dot rather than a fill: at this size a shaded cell and a
            // selected one are the same thing to the eye, and shading twelve grids at once
            // turns the page into noise. The row keeps its height whether or not there is a
            // dot, so the numbers above stay on one baseline across the month.
            Box(Modifier.height(DOT_DP + 1.dp), contentAlignment = Alignment.Center) {
                if (day.count > 0) {
                    Box(
                        Modifier
                            .size(DOT_DP)
                            .clip(CircleShape)
                            .background(if (day.isToday) scheme.onPrimary else scheme.primary),
                    )
                }
            }
        }
    }
}

/** Spoken instead of a bare number, which is all 365 of these would otherwise say. */
private fun describe(day: YearDay, khmerNumerals: Boolean): String = buildString {
    append(localeNumber(day.date.dayOfMonth, khmerNumerals))
    append(" ")
    append(KhmerTerms.solarMonth(day.date.monthValue))
    if (day.isToday) append(" · ថ្ងៃនេះ")
    if (day.isHoliday) append(" · បុណ្យជាតិ")
    if (day.count > 0) {
        append(" · ")
        append(localeNumber(day.count, khmerNumerals))
        append(" ព្រឹត្តិការណ៍")
    }
}

/** Three across on a phone: four makes the dates too small to tap or read. */
private const val COLUMNS = 3

private const val DAY_SP = 9
private const val CAPTION_SP = 7
private val DOT_DP = 3.dp
