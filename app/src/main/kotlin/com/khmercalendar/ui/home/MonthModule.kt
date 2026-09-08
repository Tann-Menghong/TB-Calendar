package com.khmercalendar.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.khmercalendar.core.khmer.CalendarWeek
import com.khmercalendar.core.khmer.KhmerTerms
import com.khmercalendar.domain.MonthCell
import com.khmercalendar.domain.MonthOverview
import com.khmercalendar.domain.MonthWeek
import com.khmercalendar.ui.components.SurfaceCard
import com.khmercalendar.ui.components.localeNumber
import com.khmercalendar.ui.theme.CardAccent
import com.khmercalendar.ui.theme.LocalAppSettings
import com.khmercalendar.ui.theme.Radius
import com.khmercalendar.ui.theme.Spacing
import com.khmercalendar.ui.theme.Stroke
import com.khmercalendar.ui.theme.color
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The month, as a heatmap.
 *
 * The week module answers "is Thursday going to be bad". This answers the question one step
 * out: where the heavy stretches of the month are, and how much of it is still free. Seven
 * columns and five or six rows fit in the height of two ordinary cards, which is the whole
 * reason a heatmap earns its place on a dashboard where a month grid would not.
 *
 * Shade carries the load and the number carries the date, so the cell is still readable when
 * the shade is not - at low contrast, in bright sun, or for someone who cannot separate the
 * steps. Today keeps a ring rather than only a darker fill for the same reason.
 */
@Composable
fun MonthModule(
    weeks: List<MonthWeek>,
    totals: MonthOverview.Totals,
    weekStart: DayOfWeek,
    onOpenDay: (LocalDate) -> Unit,
    onOpenMonth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (weeks.isEmpty()) return
    val accent = CardAccent.CALENDAR.color()
    val holiday = CardAccent.HOLIDAY.color()
    val khmerNumerals = LocalAppSettings.current.useKhmerNumerals

    SurfaceCard(modifier, onClick = onOpenMonth) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ModuleLabel("THIS MONTH", color = accent)
            Text(
                monthSummary(totals),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(Spacing.md))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            CalendarWeek.daysOfWeek(weekStart).forEach { day ->
                Text(
                    KhmerTerms.dayOfWeekShort(day),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(Spacing.xs))

        weeks.forEach { week ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                week.days.forEach { cell ->
                    if (cell == null) {
                        // A blank rather than a day from the neighbouring month. The grid is
                        // a picture of *this* month's load, and a shaded 31st of last month
                        // would be read as part of it.
                        Spacer(Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        MonthDay(
                            cell = cell,
                            accent = accent,
                            holidayColor = holiday,
                            khmerNumerals = khmerNumerals,
                            onClick = { onOpenDay(cell.date) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            Spacer(Modifier.height(Spacing.xs))
        }
    }
}

@Composable
private fun monthSummary(totals: MonthOverview.Totals): String {
    val events = localeNumber(totals.events + totals.tasks)
    val free = localeNumber(totals.freeDays)
    return if (totals.events + totals.tasks == 0) {
        "គ្មានកម្មវិធី"
    } else {
        "$events ធាតុ · ទំនេរ $free ថ្ងៃ"
    }
}

@Composable
private fun MonthDay(
    cell: MonthCell,
    accent: Color,
    holidayColor: Color,
    khmerNumerals: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(Radius.sm)
    // Four steps, and none of them opaque. The date has to stay readable on top of the
    // darkest cell, and a saturated accent at full strength under body text is the fastest
    // way to make a grid that can only be read by people who already knew what it said - so
    // the range stops at just over half, where the four steps are still distinguishable.
    val fill = if (cell.level == 0) {
        scheme.surfaceVariant.copy(alpha = 0.35f)
    } else {
        accent.copy(alpha = 0.12f + 0.11f * cell.level)
    }
    val faded = if (cell.isPast && !cell.isToday) 0.55f else 1f

    Box(
        modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(fill.copy(alpha = fill.alpha * faded))
            .then(
                if (cell.isToday) {
                    // Not the accent: on a busy day the fill *is* the accent, and a ring the
                    // same colour as what it sits on marks nothing at all. Found by looking
                    // at the grid on a device rather than by reading this line.
                    Modifier.border(Stroke.hairline, scheme.onSurface, shape)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .clearAndSetSemantics { contentDescription = describe(cell, khmerNumerals) },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                localeNumber(cell.date.dayOfMonth, khmerNumerals),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (cell.isToday) FontWeight.Bold else FontWeight.Normal,
                color = scheme.onSurface.copy(alpha = faded),
            )
            if (cell.isHoliday) {
                Spacer(Modifier.height(1.dp))
                Box(Modifier.size(3.dp).clip(CircleShape).background(holidayColor))
            }
        }
    }
}

private fun describe(cell: MonthCell, khmerNumerals: Boolean): String = buildString {
    fun num(value: Int) = if (khmerNumerals) {
        com.khmercalendar.core.khmer.KhmerNumerals.toKhmer(value.toString())
    } else {
        value.toString()
    }
    append(num(cell.date.dayOfMonth))
    if (cell.isToday) append(" ថ្ងៃនេះ")
    when {
        cell.total == 0 -> append(", ទំនេរ")
        else -> {
            if (cell.events > 0) append(", ${num(cell.events)} ព្រឹត្តិការណ៍")
            if (cell.tasks > 0) append(", ${num(cell.tasks)} កិច្ចការ")
        }
    }
    if (cell.isHoliday) append(", បុណ្យជាតិ")
}
