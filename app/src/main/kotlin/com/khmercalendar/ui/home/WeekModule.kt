package com.khmercalendar.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.khmercalendar.core.khmer.KhmerTerms
import com.khmercalendar.domain.DayLoad
import com.khmercalendar.domain.WeekOverview
import com.khmercalendar.ui.components.AppMotion
import com.khmercalendar.ui.components.SurfaceCard
import com.khmercalendar.ui.components.localeNumber
import com.khmercalendar.ui.theme.CardAccent
import com.khmercalendar.ui.theme.LocalAppSettings
import com.khmercalendar.ui.theme.Motion
import com.khmercalendar.ui.theme.Radius
import com.khmercalendar.ui.theme.Spacing
import com.khmercalendar.ui.theme.Stroke
import com.khmercalendar.ui.theme.color
import java.time.LocalDate

/**
 * The week, as seven bars.
 *
 * The dashboard could already tell you about today and about the next sixty days, and had
 * nothing to say about the shape of the week you are actually in - which is the horizon most
 * people plan against. Seven bars answer "is Thursday going to be bad" in one glance, which no
 * amount of list scrolling does.
 *
 * Events and tasks stack in different tones rather than summing into one bar: eight events is
 * a full day and eight tasks is a full week, and a single bar would say they were the same
 * thing. Height alone never carries meaning here either - every bar keeps its date under it,
 * and today is marked by a filled pill rather than only by being taller.
 */
@Composable
fun WeekModule(
    days: List<DayLoad>,
    totals: WeekOverview.Totals,
    onOpenDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (days.isEmpty()) return

    val calendar = CardAccent.CALENDAR.color()
    val tasksAccent = CardAccent.TASKS.color()
    val holidayAccent = CardAccent.HOLIDAY.color()
    val khmerNumerals = LocalAppSettings.current.useKhmerNumerals
    val peak = WeekOverview.peak(days)

    SurfaceCard(modifier) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ModuleLabel("THIS WEEK", color = calendar)
            Text(
                weekSummary(totals),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(Spacing.md))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            days.forEach { day ->
                WeekBar(
                    day = day,
                    peak = peak,
                    khmerNumerals = khmerNumerals,
                    eventColor = calendar,
                    taskColor = tasksAccent,
                    holidayColor = holidayAccent,
                    onClick = { onOpenDay(day.date) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * The summary line: what the week holds, in words.
 *
 * Above the bars rather than below them, because it is the sentence someone reads when the
 * chart has told them nothing - an empty week draws seven stubs, and "គ្មានកម្មវិធី" is the
 * only thing on the module that then means anything.
 */
@Composable
private fun weekSummary(totals: WeekOverview.Totals): String {
    val events = localeNumber(totals.events)
    val tasks = localeNumber(totals.tasks)
    return when {
        totals.events == 0 && totals.tasks == 0 -> "គ្មានកម្មវិធី"
        totals.tasks == 0 -> "$events ព្រឹត្តិការណ៍"
        totals.events == 0 -> "$tasks កិច្ចការ"
        else -> "$events ព្រឹត្តិការណ៍ · $tasks កិច្ចការ"
    }
}

/** The tallest a bar can be. Everything else is a fraction of it. */
private val BarHeight = 64.dp

@Composable
private fun WeekBar(
    day: DayLoad,
    peak: Int,
    khmerNumerals: Boolean,
    eventColor: Color,
    taskColor: Color,
    holidayColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val fraction by animateFloatAsState(
        targetValue = WeekOverview.fraction(day, peak),
        animationSpec = AppMotion.tweenOf(Motion.MEDIUM),
        label = "weekBar",
    )
    // Days already behind you still count - they are why the week looks the way it does - but
    // they are not what you can still act on, so they sit back rather than disappearing.
    val alpha = if (day.isPast && !day.isToday) 0.45f else 1f
    val shape = RoundedCornerShape(Radius.sm)

    Column(
        modifier
            .clip(shape)
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.xs)
            .clearAndSetSemantics { contentDescription = describe(day, khmerNumerals) },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(BarHeight)
                .clip(shape)
                .background(scheme.surfaceVariant.copy(alpha = 0.45f))
                .then(
                    if (day.isToday) {
                        Modifier.border(Stroke.hairline, eventColor.copy(alpha = 0.55f), shape)
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            if (day.isEmpty) {
                // A day with nothing on it draws a baseline stub rather than nothing at all,
                // so the week reads as seven days one of which is free, not as six days.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(Stroke.accentBar)
                        .background(scheme.outline.copy(alpha = alpha)),
                )
            } else {
                Column(Modifier.fillMaxWidth().fillMaxHeight(fraction.coerceAtLeast(0.06f))) {
                    if (day.tasks > 0) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .weight(day.tasks.toFloat())
                                .background(taskColor.copy(alpha = 0.55f * alpha)),
                        )
                    }
                    if (day.events > 0) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .weight(day.events.toFloat())
                                .background(eventColor.copy(alpha = alpha)),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(
            KhmerTerms.dayOfWeekShort(day.date.dayOfWeek),
            style = MaterialTheme.typography.labelSmall,
            color = if (day.isHoliday) holidayColor else scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.xs))
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (day.isToday) eventColor else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                localeNumber(day.date.dayOfMonth),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
                color = if (day.isToday) scheme.surface else scheme.onSurface.copy(alpha = alpha),
            )
        }
    }
}

/**
 * What a screen reader hears.
 *
 * The bar is a picture of a number, so the semantics have to carry the number itself; the
 * whole column is one node because seven bars announced as twenty-one fragments is worse than
 * no chart at all.
 */
private fun describe(day: DayLoad, khmerNumerals: Boolean): String = buildString {
    fun num(value: Int) = localeNumber(value, khmerNumerals)
    append(KhmerTerms.dayOfWeek(day.date.dayOfWeek))
    append(" ")
    append(num(day.date.dayOfMonth))
    if (day.isToday) append(" ថ្ងៃនេះ")
    when {
        day.isEmpty -> append(", ទំនេរ")
        else -> {
            if (day.events > 0) append(", ${num(day.events)} ព្រឹត្តិការណ៍")
            if (day.tasks > 0) append(", ${num(day.tasks)} កិច្ចការ")
        }
    }
    if (day.isHoliday) append(", បុណ្យជាតិ")
}
