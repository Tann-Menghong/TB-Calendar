package com.khmercalendar.domain

import com.khmercalendar.core.khmer.CalendarWeek
import com.khmercalendar.core.khmer.KhmerTerms
import com.khmercalendar.core.work.WorkSchedule
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

/** How wide a window the figures cover. */
enum class StatsPeriod(val labelKm: String) {
    DAY("ថ្ងៃ"),
    WEEK("សប្តាហ៍"),
    MONTH("ខែ"),
    YEAR("ឆ្នាំ"),
}

/**
 * Which figure the bar chart is drawing.
 *
 * The labels are chip-length rather than sentence-length. Four chips of "កិច្ចការរួចរាល់"
 * will not fit across a phone, and they fit even less well once somebody turns their font
 * size up - which is exactly the user least able to cope with a row that has run off the
 * edge.
 */
enum class StatsMetric(val labelKm: String) {
    TASKS("កិច្ចការ"),
    FOCUS("ផ្តោតអារម្មណ៍"),
    HABITS("ទម្លាប់"),
    EVENTS("ព្រឹត្តិការណ៍"),
}

/**
 * A window of days, and how much of it has actually happened.
 *
 * ## Why [elapsedEnd] exists
 *
 * The commonest way a statistics screen lies is by dividing by a period that has not
 * finished. On the third of the month, "tasks this month" against thirty-one days reads as a
 * catastrophe, and beside last month's complete figure it reads as a collapse. Every total
 * and every rate here stops at [elapsedEnd], and the chart draws the remaining days as absent
 * rather than as zero, because a Thursday that has not happened is not a Thursday you failed.
 */
data class StatsRange(
    val period: StatsPeriod,
    /** The day the period is anchored on: the day itself, or a day inside the week or year. */
    val anchor: LocalDate,
    val start: LocalDate,
    /** The last day of the period, whether or not it has arrived. */
    val end: LocalDate,
    val today: LocalDate,
) {
    /** Entirely ahead: nothing to count, and nothing to compare against. */
    val isFuture: Boolean get() = start.isAfter(today)

    /** The last day that has actually happened. Equal to [end] for a finished period. */
    val elapsedEnd: LocalDate get() = if (end.isBefore(today)) end else today

    val totalDays: Int get() = (end.toEpochDay() - start.toEpochDay()).toInt() + 1

    /** Days of the period that have happened. Zero for a period still ahead. */
    val elapsedDays: Int
        get() = if (isFuture) 0 else (elapsedEnd.toEpochDay() - start.toEpochDay()).toInt() + 1

    /** Running: today falls inside it, so the figures are not final. */
    val isPartial: Boolean get() = !isFuture && elapsedEnd.isBefore(end)

    fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)

    /** Inside the period *and* already happened. The test every total applies. */
    fun countsIn(date: LocalDate): Boolean =
        !isFuture && !date.isBefore(start) && !date.isAfter(elapsedEnd)
}

/** One column of the chart. */
data class StatsBucket(
    /** Digits stay ASCII; the screen converts them if the user reads Khmer numerals. */
    val label: String,
    val start: LocalDate,
    val end: LocalDate,
    /** Has not happened. Drawn as absent, not as a zero. */
    val isFuture: Boolean,
    val isToday: Boolean,
)

/** A category, and how much of the period it accounted for. */
data class CategorySlice(
    val categoryId: Long?,
    val name: String,
    val colorArgb: Int,
    /** Timed minutes only. All-day entries are in [count] and deliberately not here. */
    val minutes: Int,
    val count: Int,
)

/** A completed task, reduced to what a total needs. */
data class CompletedTask(
    val eventId: Long,
    /** The day it was *ticked*, not the day it was due. */
    val date: LocalDate,
    val priority: TaskPriority,
    val categoryId: Long?,
)

/** Everything the arithmetic reads. Assembled by the repository; pure from here on. */
data class StatsInput(
    val completedTasks: List<CompletedTask> = emptyList(),
    val focusSessions: List<FocusSession> = emptyList(),
    val habits: List<Habit> = emptyList(),
    val habitTicks: Map<Long, Set<LocalDate>> = emptyMap(),
    val occurrences: List<EventOccurrence> = emptyList(),
    val noteDays: Set<LocalDate> = emptySet(),
    val workSchedule: WorkSchedule = WorkSchedule.DEFAULT,
    val zone: ZoneId = ZoneId.systemDefault(),
)

/**
 * The figures for one range.
 *
 * @property habitExpected how many ticks the habits *promised* over the elapsed days, which
 *   is what [habitRate] divides by. See [Statistics.habitExpected] for why it is not simply
 *   "days times habits".
 * @property plannedWorkMinutes what the work schedule says, not what was worked. The app has
 *   no way to know whether somebody was at their desk, and a figure called "worked" would be
 *   inventing that.
 */
data class StatsSummary(
    val tasksCompleted: Int = 0,
    val focusMinutes: Int = 0,
    val focusSessions: Int = 0,
    val habitTicks: Int = 0,
    val habitExpected: Int = 0,
    val eventCount: Int = 0,
    val timedEventMinutes: Int = 0,
    val allDayEventCount: Int = 0,
    val plannedWorkMinutes: Int = 0,
    val noteDays: Int = 0,
    val categories: List<CategorySlice> = emptyList(),
) {
    /** Percent of promised habit ticks that were kept, or null when nothing was promised. */
    val habitRate: Int?
        get() = if (habitExpected <= 0) null else (habitTicks * 100) / habitExpected

    val isEmpty: Boolean
        get() = tasksCompleted == 0 && focusMinutes == 0 && habitTicks == 0 &&
            eventCount == 0 && noteDays == 0
}

/**
 * Local productivity statistics.
 *
 * ## Where the numbers come from
 *
 * Every one of them is arithmetic over rows that already exist: completed tasks, focus
 * sessions, habit ticks, calendar entries, day notes and the work schedule. Nothing new is
 * recorded to make this screen possible, no table was added for it, and there is no
 * identifier anywhere in it. It is the data the user has been looking at, counted.
 *
 * ## The rules that keep it honest
 *
 * These are the substance of this file, and each is a way the screen would otherwise mislead:
 *
 * - **A period that has not finished is not counted as though it had.** See [StatsRange].
 * - **This period is compared with the same *slice* of the last one.** On a Tuesday, "this
 *   week" is compared with the first two days of last week rather than with all seven -
 *   otherwise every week opens looking like a collapse and closes looking like a triumph,
 *   and the arrow is reporting the day of the week rather than anything the user did.
 * - **A habit is measured only from the day it was created**, and stops being measured when
 *   it is archived. A habit started on Friday is not four weeks of failure.
 * - **A weekly-target habit is measured against its target**, not against seven days.
 * - **A task is counted on the day it was ticked**, which is what "completed this week"
 *   means. The day it was *due* is a different question, and the to-do list already answers
 *   it.
 * - **All-day entries are counted, never timed.** A day marked "leave" is not twenty-four
 *   hours of an activity, and putting it into a time budget would swamp everything real.
 * - **Work hours are the ones planned**, because the app cannot see a desk.
 * - Focus totals exclude breaks and anything still running, and a session left running is
 *   capped at what was planned - see [FocusTimer.elapsedMinutes].
 *
 * ## What it deliberately does not do
 *
 * No score, no grade, no "you were 12% less productive this week". The figures are reported
 * and the reader draws the conclusion. A productivity screen that grades its user is one they
 * stop opening in exactly the weeks they would most benefit from opening it.
 */
object Statistics {

    /** The window [period] covers around [anchor]. */
    fun rangeOf(
        period: StatsPeriod,
        anchor: LocalDate,
        today: LocalDate,
        weekStart: DayOfWeek,
    ): StatsRange {
        val start: LocalDate
        val end: LocalDate
        when (period) {
            StatsPeriod.DAY -> {
                start = anchor
                end = anchor
            }

            StatsPeriod.WEEK -> {
                start = CalendarWeek.startOfWeek(anchor, weekStart)
                end = start.plusDays(6)
            }

            StatsPeriod.MONTH -> {
                val month = YearMonth.from(anchor)
                start = month.atDay(1)
                end = month.atEndOfMonth()
            }

            StatsPeriod.YEAR -> {
                start = anchor.withDayOfYear(1)
                end = anchor.withDayOfYear(anchor.lengthOfYear())
            }
        }
        return StatsRange(period, anchor, start, end, today)
    }

    /** The same period, [by] steps away. Negative goes back. */
    fun shift(range: StatsRange, by: Long, weekStart: DayOfWeek): StatsRange {
        val anchor = when (range.period) {
            StatsPeriod.DAY -> range.anchor.plusDays(by)
            StatsPeriod.WEEK -> range.anchor.plusWeeks(by)
            StatsPeriod.MONTH -> range.anchor.plusMonths(by)
            StatsPeriod.YEAR -> range.anchor.plusYears(by)
        }
        return rangeOf(range.period, anchor, range.today, weekStart)
    }

    /**
     * The window the current one should be compared against: the previous period, cut to the
     * same number of days that have elapsed in this one.
     *
     * This is the difference between a comparison that means something and one that is an
     * artefact of the calendar. Comparing a Tuesday-morning "this week" against a complete
     * previous week says nothing except that it is Tuesday.
     *
     * Null when there is nothing to compare against: a period entirely in the future.
     */
    fun comparisonRange(range: StatsRange, weekStart: DayOfWeek): StatsRange? {
        if (range.isFuture || range.elapsedDays <= 0) return null
        val previous = shift(range, -1, weekStart)
        // Clipped twice: to the elapsed length, and to the previous period's own end - which
        // matters whenever the previous period is shorter, as February always is.
        val cutoff = previous.start.plusDays((range.elapsedDays - 1).toLong())
        val end = if (cutoff.isBefore(previous.end)) cutoff else previous.end
        return previous.copy(end = end)
    }

    /**
     * The columns of the chart.
     *
     * A day has a single column, which is not a chart; the screen hides it below two.
     */
    fun buckets(range: StatsRange): List<StatsBucket> = when (range.period) {
        StatsPeriod.DAY -> listOf(
            StatsBucket(
                label = range.start.dayOfMonth.toString(),
                start = range.start,
                end = range.start,
                isFuture = range.start.isAfter(range.today),
                isToday = range.start == range.today,
            ),
        )

        StatsPeriod.WEEK -> (0..6).map { offset ->
            val day = range.start.plusDays(offset.toLong())
            StatsBucket(
                label = KhmerTerms.dayOfWeekShort(day.dayOfWeek),
                start = day,
                end = day,
                isFuture = day.isAfter(range.today),
                isToday = day == range.today,
            )
        }

        StatsPeriod.MONTH -> (0 until range.totalDays).map { offset ->
            val day = range.start.plusDays(offset.toLong())
            StatsBucket(
                label = day.dayOfMonth.toString(),
                start = day,
                end = day,
                isFuture = day.isAfter(range.today),
                isToday = day == range.today,
            )
        }

        StatsPeriod.YEAR -> (1..12).map { month ->
            val first = range.start.withMonth(month)
            val last = first.withDayOfMonth(first.lengthOfMonth())
            StatsBucket(
                label = KhmerTerms.solarMonth(month),
                start = first,
                end = last,
                isFuture = first.isAfter(range.today),
                isToday = !range.today.isBefore(first) && !range.today.isAfter(last),
            )
        }
    }

    /**
     * What to call [range], in Khmer.
     *
     * Digits stay ASCII so the screen can convert them with the user's numeral preference;
     * `KhmerNumerals` only touches digits, so the Khmer words pass through untouched.
     *
     * A week is written out in full only where it has to be. "15–21 មករា 2026" is the common
     * case; a week that straddles two months or two years spells both out, because "29–4
     * មករា" is not a range anybody can read.
     */
    fun title(range: StatsRange): String = when (range.period) {
        StatsPeriod.DAY ->
            "ថ្ងៃ${KhmerTerms.dayOfWeek(range.start.dayOfWeek)} " +
                "${range.start.dayOfMonth} ${KhmerTerms.solarMonth(range.start.monthValue)} " +
                "${range.start.year}"

        StatsPeriod.WEEK -> {
            val a = range.start
            val b = range.end
            when {
                a.year != b.year ->
                    "${a.dayOfMonth} ${KhmerTerms.solarMonth(a.monthValue)} ${a.year} – " +
                        "${b.dayOfMonth} ${KhmerTerms.solarMonth(b.monthValue)} ${b.year}"

                a.month != b.month ->
                    "${a.dayOfMonth} ${KhmerTerms.solarMonth(a.monthValue)} – " +
                        "${b.dayOfMonth} ${KhmerTerms.solarMonth(b.monthValue)} ${b.year}"

                else ->
                    "${a.dayOfMonth}–${b.dayOfMonth} ${KhmerTerms.solarMonth(a.monthValue)} ${a.year}"
            }
        }

        StatsPeriod.MONTH -> "${KhmerTerms.solarMonth(range.start.monthValue)} ${range.start.year}"

        StatsPeriod.YEAR -> "ឆ្នាំ ${range.start.year}"
    }

    /**
     * A span of minutes as Khmer text: "45 នាទី", "2 ម៉ោង", "2 ម៉ោង 15 នាទី".
     *
     * Hours appear only once there is an hour to show. "0 ម៉ោង 45 នាទី" is longer, harder to
     * scan and says nothing extra, and a row of tiles is read at a glance or not at all.
     * Digits stay ASCII for the same reason as [title].
     */
    fun formatMinutes(minutes: Int): String {
        val safe = minutes.coerceAtLeast(0)
        val hours = safe / 60
        val rest = safe % 60
        return when {
            hours == 0 -> "$rest នាទី"
            rest == 0 -> "$hours ម៉ោង"
            else -> "$hours ម៉ោង $rest នាទី"
        }
    }

    /** Every figure for [range]. */
    fun summarise(input: StatsInput, range: StatsRange): StatsSummary {
        if (range.isFuture) return StatsSummary()

        val from = range.start
        val to = range.elapsedEnd

        val focusMinutes = FocusTimer.minutesBetween(input.focusSessions, from, to, input.zone)
        val focusSessions = input.focusSessions.count {
            !it.kind.isBreak && !it.isRunning && range.countsIn(sessionDate(it, input.zone))
        }

        var ticks = 0
        var expected = 0
        for (habit in input.habits) {
            val done = input.habitTicks[habit.id].orEmpty()
            ticks += done.count { range.countsIn(it) && isMeasured(habit, it) }
            expected += habitExpected(habit, from, to)
        }

        val distinct = distinctOccurrences(input, range)
        val slices = categorySlices(
            occurrences = distinct,
            windowStart = from.atStartOfDay(),
            windowEnd = to.plusDays(1).atStartOfDay(),
        )

        return StatsSummary(
            tasksCompleted = input.completedTasks.count { range.countsIn(it.date) },
            focusMinutes = focusMinutes,
            focusSessions = focusSessions,
            habitTicks = ticks,
            habitExpected = expected,
            eventCount = slices.sumOf { it.count },
            timedEventMinutes = slices.sumOf { it.minutes },
            allDayEventCount = distinct.count { !it.isTask && it.allDay },
            plannedWorkMinutes = plannedWorkMinutes(input.workSchedule, from, to),
            noteDays = input.noteDays.count { range.countsIn(it) },
            categories = slices,
        )
    }

    /**
     * One value per bucket, for the chart.
     *
     * A future bucket is null rather than zero. The screen has to be able to tell "nothing
     * happened" from "this has not happened yet", and at zero height they look identical.
     */
    fun series(input: StatsInput, buckets: List<StatsBucket>, metric: StatsMetric): List<Int?> {
        // Indexed once rather than scanned per bucket: a month has thirty-one buckets and a
        // busy calendar has thousands of rows, and the naive form is that product.
        val tasksByDay: Map<LocalDate, Int> = input.completedTasks
            .groupingBy { it.date }
            .eachCount()

        val habitTicksByDay = HashMap<LocalDate, Int>()
        for (habit in input.habits) {
            for (date in input.habitTicks[habit.id].orEmpty()) {
                if (isMeasured(habit, date)) {
                    habitTicksByDay[date] = (habitTicksByDay[date] ?: 0) + 1
                }
            }
        }

        val eventsByDay = HashMap<LocalDate, Int>()
        for (occurrence in input.occurrences.distinctBy { it.eventId to it.start }) {
            if (occurrence.isTask) continue
            val date = occurrence.occurrenceDate
            eventsByDay[date] = (eventsByDay[date] ?: 0) + 1
        }

        return buckets.map { bucket ->
            if (bucket.isFuture) {
                null
            } else {
                when (metric) {
                    StatsMetric.TASKS -> sumDays(bucket, tasksByDay)
                    StatsMetric.HABITS -> sumDays(bucket, habitTicksByDay)
                    StatsMetric.EVENTS -> sumDays(bucket, eventsByDay)
                    StatsMetric.FOCUS -> FocusTimer.minutesBetween(
                        sessions = input.focusSessions,
                        from = bucket.start,
                        to = bucket.end,
                        zone = input.zone,
                    )
                }
            }
        }
    }

    /**
     * How many ticks [habit] promised between [from] and [to].
     *
     * Three genuinely different promises, measured three different ways:
     *
     * - a daily habit owes one per day;
     * - a weekday habit owes one per matching weekday, so keeping a Monday-Wednesday-Friday
     *   habit perfectly reads as 100% rather than 43%;
     * - a "twice a week" habit owes its target per week, prorated over however many days of
     *   the window it was active - measuring it per day would report somebody who did exactly
     *   what they promised at 29%.
     *
     * The window is clipped to the habit's own life at both ends. A habit created on Friday
     * owes nothing for Monday, and an archived one owes nothing after it was put away.
     */
    fun habitExpected(habit: Habit, from: LocalDate, to: LocalDate): Int {
        val first = if (habit.createdAt.isAfter(from)) habit.createdAt else from
        val archived = habit.archivedAt
        val last = if (archived != null && archived.isBefore(to)) archived else to
        if (last.isBefore(first)) return 0

        val activeDays = (last.toEpochDay() - first.toEpochDay()).toInt() + 1
        return when (habit.schedule.kind) {
            HabitSchedule.Kind.TIMES_PER_WEEK -> {
                val target = habit.schedule.target.coerceIn(1, 7)
                // Rounded rather than truncated: three days of a twice-a-week habit is closer
                // to one owed tick than to none, and truncating would make any window shorter
                // than a week unmeasurable.
                Math.round(target * activeDays / 7.0).toInt()
            }

            else -> {
                var due = 0
                var date = first
                while (!date.isAfter(last)) {
                    if (habit.schedule.isDue(date)) due++
                    date = date.plusDays(1)
                }
                due
            }
        }
    }

    /**
     * Scheduled working minutes over the elapsed days.
     *
     * Zero when the countdown is switched off: a schedule the user has disabled is not a plan
     * they are keeping.
     */
    fun plannedWorkMinutes(schedule: WorkSchedule, from: LocalDate, to: LocalDate): Int {
        if (!schedule.enabled || to.isBefore(from)) return 0
        var total = 0L
        var date = from
        while (!date.isAfter(to)) {
            total += schedule.totalFor(date.dayOfWeek).toMinutes()
            date = date.plusDays(1)
        }
        return total.toInt()
    }

    /** Whether [date] falls inside the habit's own life, for counting a tick. */
    private fun isMeasured(habit: Habit, date: LocalDate): Boolean {
        if (date.isBefore(habit.createdAt)) return false
        val archived = habit.archivedAt ?: return true
        return !date.isAfter(archived)
    }

    /**
     * Where the time went, by category.
     *
     * Timed entries contribute minutes, clipped to the range so a meeting spanning the
     * boundary contributes only the part inside it. All-day entries contribute to the count
     * and to nothing else - see the class comment. Tasks are excluded entirely: they are
     * counted as completions, and a task with a nominal hour on it is not an hour spent.
     */
    private fun categorySlices(
        occurrences: List<EventOccurrence>,
        windowStart: LocalDateTime,
        windowEnd: LocalDateTime,
    ): List<CategorySlice> {
        class Bucket {
            var minutes = 0
            var count = 0
            var color = 0
            var name: String? = null
        }

        val byCategory = LinkedHashMap<Long?, Bucket>()
        for (occurrence in occurrences) {
            if (occurrence.isTask) continue
            val bucket = byCategory.getOrPut(occurrence.categoryId) { Bucket() }
            bucket.count++
            if (bucket.name == null) {
                bucket.name = occurrence.categoryName
                bucket.color = occurrence.colorArgb
            }
            if (!occurrence.allDay) {
                bucket.minutes += clippedMinutes(
                    start = occurrence.start,
                    end = occurrence.end,
                    windowStart = windowStart,
                    windowEnd = windowEnd,
                )
            }
        }

        return byCategory.entries
            .map { (id, bucket) ->
                CategorySlice(
                    categoryId = id,
                    name = bucket.name ?: UNCATEGORISED_KM,
                    colorArgb = bucket.color,
                    minutes = bucket.minutes,
                    count = bucket.count,
                )
            }
            .sortedWith(compareByDescending<CategorySlice> { it.minutes }.thenByDescending { it.count })
    }

    /**
     * The occurrences inside the elapsed part of the range, each counted once.
     *
     * A multi-day event is listed under every day it covers so that it appears in each grid.
     * A total that did not de-duplicate would count a three-day trip three times.
     */
    private fun distinctOccurrences(input: StatsInput, range: StatsRange): List<EventOccurrence> =
        input.occurrences
            .filter { range.countsIn(it.occurrenceDate) }
            .distinctBy { it.eventId to it.start }

    /**
     * The part of an entry that falls inside the window.
     *
     * A meeting that runs from half past eleven at night into the next day contributes half
     * an hour to the day it started on, not two hours - and on the last day of the range,
     * only the part that has actually happened.
     */
    private fun clippedMinutes(
        start: LocalDateTime,
        end: LocalDateTime,
        windowStart: LocalDateTime,
        windowEnd: LocalDateTime,
    ): Int {
        val from = if (start.isBefore(windowStart)) windowStart else start
        val to = if (end.isAfter(windowEnd)) windowEnd else end
        if (!to.isAfter(from)) return 0
        return Duration.between(from, to).toMinutes().toInt()
    }

    private fun sumDays(bucket: StatsBucket, byDay: Map<LocalDate, Int>): Int {
        if (bucket.start == bucket.end) return byDay[bucket.start] ?: 0
        var total = 0
        var date = bucket.start
        while (!date.isAfter(bucket.end)) {
            total += byDay[date] ?: 0
            date = date.plusDays(1)
        }
        return total
    }

    private fun sessionDate(session: FocusSession, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(session.startedAtMillis).atZone(zone).toLocalDate()

    const val UNCATEGORISED_KM = "គ្មានប្រភេទ"
}
