package com.khmercalendar.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import com.khmercalendar.ui.components.AnimatedNumberText
import com.khmercalendar.ui.components.AppMotion
import com.khmercalendar.ui.components.StatusBadge
import com.khmercalendar.ui.theme.Motion
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.khmercalendar.core.khmer.KhmerTerms
import com.khmercalendar.domain.EventOccurrence
import com.khmercalendar.domain.TimelineEntry
import com.khmercalendar.ui.components.CalendarFormats
import com.khmercalendar.ui.components.LocalUses24Hour
import com.khmercalendar.ui.components.ProgressBar
import com.khmercalendar.ui.components.SurfaceCard
import com.khmercalendar.ui.components.localeNumber
import com.khmercalendar.ui.theme.AppType
import com.khmercalendar.ui.theme.CardAccent
import com.khmercalendar.ui.theme.IconSize
import com.khmercalendar.ui.theme.LocalAccentTrio
import com.khmercalendar.ui.theme.LocalAppSettings
import com.khmercalendar.ui.theme.Radius
import com.khmercalendar.ui.theme.Spacing
import com.khmercalendar.ui.theme.Stroke
import com.khmercalendar.ui.theme.color
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

/**
 * The dashboard's modules.
 *
 * ## Why these are not all cards
 *
 * The previous dashboard was nine cards of the same shape and weight stacked down the screen,
 * and a stack of equals has no hierarchy: everything is equally important, so nothing is. The
 * modules here are deliberately different kinds of object - a two-up row of small figures, a
 * timeline with no card around it at all, a numbered list, a horizontal strip - so that the
 * eye can tell them apart before it reads a word.
 *
 * The rule they still share is the one that matters: spacing, radius and colour all come from
 * the tokens, so "different" never becomes "unrelated".
 */

/**
 * A small module heading.
 *
 * Latin, letter-spaced, and quiet - it labels the module without competing with the number
 * underneath, which is what the user is actually here to read.
 */
@Composable
fun ModuleLabel(text: String, modifier: Modifier = Modifier, color: Color? = null) {
    Text(
        text = text,
        style = AppType.techLabel(),
        color = color ?: MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/**
 * The two-up row: what is next, and how today is going.
 *
 * Side by side because they answer the two halves of the same question - what is coming, and
 * how much is behind me - and because a dashboard that is only ever one column deep reads as
 * a list.
 */
@Composable
fun NextAndProgress(
    next: EventOccurrence?,
    now: LocalTime,
    tasksDone: Int,
    tasksTotal: Int,
    eventsToday: Int,
    onOpenEvent: (Long, LocalDate) -> Unit,
    onOpenTasks: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        NextEventModule(
            next = next,
            now = now,
            onOpen = onOpenEvent,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        ProgressModule(
            tasksDone = tasksDone,
            tasksTotal = tasksTotal,
            eventsToday = eventsToday,
            onClick = onOpenTasks,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }
}

@Composable
private fun NextEventModule(
    next: EventOccurrence?,
    now: LocalTime,
    onOpen: (Long, LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = LocalAppSettings.current
    val uses24Hour = LocalUses24Hour.current
    val accent = CardAccent.CALENDAR.color()

    SurfaceCard(
        modifier = modifier,
        onClick = next?.let { { onOpen(it.eventId, it.occurrenceDate) } },
        padding = settings.dashboardDensity.cardPaddingDp.dp,
    ) {
        ModuleLabel("NEXT", color = accent)
        Spacer(Modifier.height(Spacing.sm))
        if (next == null) {
            Text(
                "ទំនេរ",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "គ្មានអ្វីទៀតថ្ងៃនេះ",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                next.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                CalendarFormats.time(
                    next.start.toLocalTime(),
                    uses24Hour,
                    settings.useKhmerNumerals,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = accent,
            )
            val minutes = Duration.between(now, next.start.toLocalTime()).toMinutes()
            when {
                // Inside a quarter of an hour the countdown stops being information and
                // starts being a prompt, so it says so in words rather than by turning a
                // colour - which would tell a colour-blind user nothing at all.
                minutes in 0..15 -> {
                    Spacer(Modifier.height(Spacing.xs))
                    StatusBadge(
                        text = "\u1785\u17b6\u1794\u17cb\u1795\u17d2\u178f\u17be\u1798\u1786\u17b6\u1794\u17cb\u17d7",
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                minutes > 15 -> Text(
                    relativeMinutes(minutes, settings.useKhmerNumerals),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun relativeMinutes(minutes: Long, khmerNumerals: Boolean): String = when {
    minutes < 60L -> "ក្នុងរយៈពេល ${localeNumber(minutes, khmerNumerals)} នាទី"
    minutes % 60L == 0L -> "ក្នុងរយៈពេល ${localeNumber(minutes / 60, khmerNumerals)} ម៉ោង"
    else -> "ក្នុងរយៈពេល ${localeNumber(minutes / 60, khmerNumerals)} ម៉ោង " +
        "${localeNumber(minutes % 60, khmerNumerals)} នាទី"
}

@Composable
private fun ProgressModule(
    tasksDone: Int,
    tasksTotal: Int,
    eventsToday: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = LocalAppSettings.current
    val accent = CardAccent.TASKS.color()
    val percent = if (tasksTotal > 0) tasksDone * 100 / tasksTotal else 0

    SurfaceCard(
        modifier = modifier,
        onClick = onClick,
        padding = settings.dashboardDensity.cardPaddingDp.dp,
    ) {
        ModuleLabel("TODAY", color = accent)
        Spacer(Modifier.height(Spacing.sm))
        if (tasksTotal > 0) {
            // Counts to its new value when a task is ticked, rather than jumping.
            AnimatedNumberText(
                value = percent,
                style = AppType.metric(),
                color = MaterialTheme.colorScheme.onSurface,
                suffix = "%",
            )
            Spacer(Modifier.height(Spacing.xs))
            // The figure and the bar say the same thing twice on purpose, and the words say
            // it a third time - a percentage alone is not an accessible progress indicator.
            ProgressBar(
                fraction = tasksDone.toFloat() / tasksTotal.toFloat(),
                indicator = accent,
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                "កិច្ចការ ${localeNumber(tasksDone)}/${localeNumber(tasksTotal)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            AnimatedNumberText(
                value = eventsToday,
                style = AppType.metric(),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "ព្រឹត្តិការណ៍ថ្ងៃនេះ",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The day, as a line.
 *
 * This replaces the old "today's events" list, and it is the biggest single change to the
 * dashboard. A list of events tells you what is on; a timeline tells you *where you are* -
 * the rule runs from clocking on to going home, the passed entries are dimmed, and a marker
 * sits at the current time. That is the question the dashboard exists to answer.
 *
 * Drawn without a card around it deliberately. The vertical rule is the structure, and a card
 * border around a line that is already a line is one border too many.
 */
@Composable
fun TimelineModule(
    entries: List<TimelineEntry>,
    now: LocalTime,
    isToday: Boolean,
    onOpenEvent: (Long, LocalDate) -> Unit,
    onOpenAgenda: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = LocalAppSettings.current
    val uses24Hour = LocalUses24Hour.current
    val scheme = MaterialTheme.colorScheme

    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ModuleLabel("TIMELINE", modifier = Modifier.weight(1f))
            Text(
                "កាលវិភាគ",
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.clickable(onClick = onOpenAgenda),
            )
        }
        Spacer(Modifier.height(Spacing.md))

        if (entries.isEmpty()) {
            Text(
                "ថ្ងៃនេះទំនេរទាំងស្រុង",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            return@Column
        }

        entries.forEachIndexed { index, entry ->
            val passed = isToday && entry.at <= now
            TimelineEnter(index = index) {
            TimelineRow(
                entry = entry,
                passed = passed,
                first = index == 0,
                last = index == entries.lastIndex,
                showNowMarker = isToday &&
                    entry.at > now &&
                    (index == 0 || entries[index - 1].at <= now),
                now = now,
                uses24Hour = uses24Hour,
                khmerNumerals = settings.useKhmerNumerals,
                onClick = entry.eventId?.let { id ->
                    entry.date?.let { d -> { onOpenEvent(id, d) } }
                },
            )
            }
        }
    }
}

/**
 * A timeline row arriving.
 *
 * Fades and lifts a few pixels, staggered by position so the day assembles downwards rather
 * than appearing all at once. The stagger is capped - a long day would otherwise take a
 * second and a half to finish arriving, which is an animation the user is waiting on rather
 * than one they enjoy.
 *
 * Runs once. Recomposing a row - because a minute passed, or a task was ticked - must not
 * replay it.
 */
@Composable
private fun TimelineEnter(index: Int, content: @Composable () -> Unit) {
    val delay = (index * Motion.STAGGER).coerceAtMost(Motion.STAGGER_CAP)
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = AppMotion.enterOf(Motion.MEDIUM, delayMillis = delay),
        label = "timelineEnter",
    )
    Box(
        Modifier
            .graphicsLayer {
                alpha = progress
                translationY = (1f - progress) * 12.dp.toPx()
            },
    ) {
        content()
    }
}

@Composable
private fun TimelineRow(
    entry: TimelineEntry,
    passed: Boolean,
    first: Boolean,
    last: Boolean,
    showNowMarker: Boolean,
    now: LocalTime,
    uses24Hour: Boolean,
    khmerNumerals: Boolean,
    onClick: (() -> Unit)?,
) {
    val scheme = MaterialTheme.colorScheme
    val accent = entry.accentColor()
    // Passed entries are dimmed rather than hidden: the shape of the day so far is part of
    // knowing where you are in it.
    val alpha = if (passed) 0.45f else 1f

    Column(Modifier.fillMaxWidth()) {
        if (showNowMarker) {
            NowMarker(now, uses24Hour, khmerNumerals)
        }
        Row(
            Modifier
                .fillMaxWidth()
                // The row is measured to its tallest child first, so the connector column
                // below can fill that height. Without it the rule's weighted segment gets
                // zero height in an unbounded column and the line breaks into stubs.
                .height(IntrinsicSize.Min)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        ) {
            Text(
                text = CalendarFormats.time(entry.at, uses24Hour, khmerNumerals),
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant.copy(alpha = alpha),
                modifier = Modifier.width(64.dp).padding(top = 10.dp),
                maxLines = 1,
            )

            // The rule and its node. The rule is continuous down the whole timeline; the
            // first and last rows only draw their half of it.
            Column(
                Modifier.width(20.dp).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .width(Stroke.hairline)
                        .height(12.dp)
                        .background(
                            if (first) Color.Transparent else scheme.outlineVariant,
                        ),
                )
                TimelineNode(entry = entry, accent = accent, dimmed = passed)
                Box(
                    Modifier
                        .width(Stroke.hairline)
                        .weight(1f)
                        .background(if (last) Color.Transparent else scheme.outlineVariant),
                )
            }

            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f).padding(top = 6.dp, bottom = Spacing.md)) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (entry.isSchedule()) FontWeight.Normal else FontWeight.Medium,
                    color = scheme.onSurface.copy(alpha = alpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (entry.done) {
                        androidx.compose.ui.text.style.TextDecoration.LineThrough
                    } else {
                        null
                    },
                )
                val subtitle = entry.subtitle ?: entry.subtitleAt?.let { at ->
                    "រហូតដល់ " + CalendarFormats.time(at, uses24Hour, khmerNumerals)
                }
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant.copy(alpha = alpha),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** A filled node for an event, a hollow one for a moment in the working day. */
@Composable
private fun TimelineNode(entry: TimelineEntry, accent: Color, dimmed: Boolean) {
    val alpha = if (dimmed) 0.45f else 1f
    if (entry.isSchedule()) {
        Box(
            Modifier
                .size(11.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.5.dp, accent.copy(alpha = alpha), CircleShape),
        )
    } else {
        Box(
            Modifier
                .size(11.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = alpha)),
        )
    }
}

/** The line that says where you are. */
@Composable
private fun NowMarker(now: LocalTime, uses24Hour: Boolean, khmerNumerals: Boolean) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        Modifier.fillMaxWidth().padding(bottom = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = CalendarFormats.time(now, uses24Hour, khmerNumerals),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = accent,
            modifier = Modifier.width(64.dp),
            maxLines = 1,
        )
        Box(
            Modifier.width(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(accent))
        }
        Spacer(Modifier.width(Spacing.md))
        Box(
            Modifier
                .weight(1f)
                .height(Stroke.hairline)
                .background(accent.copy(alpha = 0.6f))
                // The line is decoration; the time beside it already says what it means.
                .clearAndSetSemantics { contentDescription = "ពេលបច្ចុប្បន្ន" },
        )
    }
}

private fun TimelineEntry.isSchedule(): Boolean = kind != TimelineEntry.Kind.EVENT &&
    kind != TimelineEntry.Kind.TASK

@Composable
private fun TimelineEntry.accentColor(): Color = when (kind) {
    TimelineEntry.Kind.WORK_START -> CardAccent.WORK.color()
    TimelineEntry.Kind.BREAK -> CardAccent.WARNING.color()
    TimelineEntry.Kind.WORK_END -> MaterialTheme.colorScheme.onSurfaceVariant
    TimelineEntry.Kind.TASK -> CardAccent.TASKS.color()
    TimelineEntry.Kind.EVENT -> colorArgb?.let { Color(it) } ?: CardAccent.CALENDAR.color()
}

/**
 * The few things that actually need doing.
 *
 * Numbered, because a numbered list is a ranking and a bulleted one is a pile. Three at most:
 * a dashboard that lists everything is a task manager, and the full list is one tap away.
 */
@Composable
fun FocusModule(
    tasks: List<EventOccurrence>,
    onToggle: (Long, Boolean) -> Unit,
    onOpen: (Long, LocalDate) -> Unit,
    onViewAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = CardAccent.TASKS.color()
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ModuleLabel("FOCUS", modifier = Modifier.weight(1f), color = accent)
            Text(
                "ទាំងអស់",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable(onClick = onViewAll),
            )
        }
        Spacer(Modifier.height(Spacing.md))

        if (tasks.isEmpty()) {
            Text(
                "គ្មានអ្វីត្រូវធ្វើទេ",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        tasks.take(3).forEachIndexed { index, task ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.sm))
                    .clickable { onOpen(task.eventId, task.occurrenceDate) }
                    .padding(vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "%02d".format(index + 1),
                    style = AppType.techLabel(),
                    color = accent,
                    modifier = Modifier.width(32.dp),
                )
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(Spacing.sm))
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, accent.copy(alpha = 0.6f), CircleShape)
                        .clickable { onToggle(task.eventId, true) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.CheckCircle,
                        contentDescription = "សម្គាល់ថារួចរាល់",
                        tint = accent,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

/**
 * What is coming after today, as a strip.
 *
 * Horizontal because these are peers competing for a glance rather than a ranked list, and
 * because a vertical list of three future things pushes everything below it off the screen.
 */
@Composable
fun UpNextStrip(
    events: List<EventOccurrence>,
    holidays: List<com.khmercalendar.core.holiday.Holiday>,
    today: LocalDate,
    onOpenEvent: (Long, LocalDate) -> Unit,
    onOpenHolidays: () -> Unit,
    modifier: Modifier = Modifier,
) {
    data class Item(
        val icon: ImageVector,
        val title: String,
        val when_: String,
        val accent: Color,
        val onClick: () -> Unit,
    )

    val calendarAccent = CardAccent.CALENDAR.color()
    val holidayAccent = CardAccent.HOLIDAY.color()
    val khmerNumerals = LocalAppSettings.current.useKhmerNumerals

    val items = buildList {
        events.filter { it.occurrenceDate.isAfter(today) }.take(3).forEach { event ->
            add(
                Item(
                    icon = Icons.Outlined.Event,
                    title = event.title,
                    when_ = relativeDay(today, event.occurrenceDate, khmerNumerals),
                    accent = event.colorArgb.let { Color(it) },
                    onClick = { onOpenEvent(event.eventId, event.occurrenceDate) },
                ),
            )
        }
        holidays.filter { it.date.isAfter(today) }.take(2).forEach { holiday ->
            add(
                Item(
                    icon = Icons.Outlined.Celebration,
                    title = holiday.nameKm,
                    when_ = relativeDay(today, holiday.date, khmerNumerals),
                    accent = holidayAccent,
                    onClick = onOpenHolidays,
                ),
            )
        }
    }
    if (items.isEmpty()) return

    Column(modifier.fillMaxWidth()) {
        ModuleLabel("UP NEXT", color = calendarAccent)
        Spacer(Modifier.height(Spacing.md))
        // A snapping rail rather than free scrolling. The cards are all one width and are
        // read one at a time, so a flick that stops halfway through a card leaves the user
        // looking at two halves; snapping means a flick always lands on something readable.
        val railState = rememberLazyListState()
        LazyRow(
            state = railState,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            flingBehavior = rememberSnapFlingBehavior(railState),
        ) {
            itemsIndexed(items, key = { index, item -> "$index:${item.title}" }) { _, item ->
                Column(
                    Modifier
                        .width(152.dp)
                        .clip(RoundedCornerShape(Radius.md))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable(onClick = item.onClick)
                        .padding(Spacing.md),
                ) {
                    Icon(
                        item.icon,
                        contentDescription = null,
                        tint = item.accent,
                        modifier = Modifier.size(IconSize.small),
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        item.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        item.when_,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun relativeDay(today: LocalDate, date: LocalDate, khmerNumerals: Boolean): String {
    val days = java.time.temporal.ChronoUnit.DAYS.between(today, date)
    return when {
        days == 1L -> "ថ្ងៃស្អែក"
        days < 7L -> "ថ្ងៃ" + KhmerTerms.dayOfWeek(date.dayOfWeek)
        else -> "នៅ ${localeNumber(days, khmerNumerals)} ថ្ងៃ"
    }
}

/**
 * The assistant, kept small.
 *
 * The brief that produced the previous version put AI on the dashboard as a full card; this
 * one asks for it to stop dominating, which is right - the assistant is a tool you reach for,
 * not a thing you check. It gets the cyan-to-magenta sweep so it is unmistakably not calendar
 * content, and four verbs, and no more room than that.
 */
@Composable
fun AiAssistStrip(
    onPaste: () -> Unit,
    onSummarise: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val trio = LocalAccentTrio.current
    val shape = RoundedCornerShape(Radius.md)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        trio.alt.copy(alpha = 0.16f),
                        trio.far.copy(alpha = 0.10f),
                        MaterialTheme.colorScheme.surface,
                    ),
                ),
            )
            .border(Stroke.hairline, trio.far.copy(alpha = 0.30f), shape)
            .padding(Spacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = trio.far,
                modifier = Modifier.size(IconSize.small),
            )
            Spacer(Modifier.width(Spacing.sm))
            ModuleLabel("AI ASSIST", color = trio.far)
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(
            "តើអ្នកចង់រៀបចំអ្វី?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Spacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            AiChip("បិទភ្ជាប់អត្ថបទ", trio.alt, Modifier.weight(1f), onPaste)
            AiChip("សង្ខេបថ្ងៃនេះ", trio.far, Modifier.weight(1f), onSummarise)
        }
    }
}

@Composable
private fun AiChip(label: String, accent: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(Radius.sm))
            .background(accent.copy(alpha = 0.14f))
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The control dock.
 *
 * Replaces the floating action button, which had been covering whichever card happened to sit
 * under it - a card's value one release, a quick action the next. A dock at the end of the
 * dashboard blocks nothing, holds four actions instead of one, and reads as part of the
 * dashboard rather than as something dropped on top of it.
 */
@Composable
fun QuickDock(
    onAddEvent: () -> Unit,
    onAddTask: () -> Unit,
    onAddNote: () -> Unit,
    onCountdown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(MaterialTheme.colorScheme.surface)
            .padding(Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        DockAction(Icons.Outlined.Event, "ព្រឹត្តិការណ៍", CardAccent.CALENDAR.color(), Modifier.weight(1f), onAddEvent)
        DockAction(Icons.Outlined.CheckCircle, "កិច្ចការ", CardAccent.TASKS.color(), Modifier.weight(1f), onAddTask)
        DockAction(Icons.Outlined.EditNote, "កំណត់ចំណាំ", CardAccent.NEUTRAL.color(), Modifier.weight(1f), onAddNote)
        DockAction(Icons.Outlined.HourglassEmpty, "រាប់ថយក្រោយ", CardAccent.HOLIDAY.color(), Modifier.weight(1f), onCountdown)
    }
}

@Composable
private fun DockAction(
    icon: ImageVector,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(Radius.sm))
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.md, horizontal = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(IconSize.md))
        Spacer(Modifier.height(Spacing.xs))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * One upcoming holiday: a dot, its name and date, and how far off it is.
 *
 * The day count is the useful part - "24 កញ្ញា" only means something if you already know
 * today's date, which is a strange thing to ask of a calendar.
 */
@Composable
fun HolidayRow(
    holiday: com.khmercalendar.core.holiday.Holiday,
    today: LocalDate,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val public = holiday.kind == com.khmercalendar.core.holiday.HolidayKind.PUBLIC
    val away = java.time.temporal.ChronoUnit.DAYS.between(today, holiday.date)
    Row(
        modifier.fillMaxWidth().padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (public) accent else MaterialTheme.colorScheme.onSurfaceVariant),
        )
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                holiday.nameKm,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${localeNumber(holiday.date.dayOfMonth)} ${KhmerTerms.solarMonth(holiday.date.monthValue)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(Spacing.sm))
        com.khmercalendar.ui.components.StatusBadge(
            text = when {
                away <= 0L -> "ថ្ងៃនេះ"
                away == 1L -> "ស្អែក"
                else -> "នៅ ${localeNumber(away)} ថ្ងៃ"
            },
            color = accent,
        )
    }
}
