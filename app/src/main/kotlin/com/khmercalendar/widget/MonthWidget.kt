package com.khmercalendar.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.khmercalendar.core.holiday.KhmerHolidays
import com.khmercalendar.core.khmer.CalendarWeek
import com.khmercalendar.core.khmer.Chhankitek
import com.khmercalendar.core.khmer.KhmerTerms
import com.khmercalendar.data.prefs.AppSettings
import com.khmercalendar.domain.EventOccurrence
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * The month widget.
 *
 * A month grid is a lot of views for a widget, so it draws only what is readable at this
 * size: the day number, a weekend or holiday tint, and one dot when the day has anything on
 * it. Tapping a day opens that day rather than only the app.
 */
object MonthWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = context.container
        val repository = container.eventRepository
        // Reading one value before composing stops the launcher from showing an empty grid
        // while DataStore opens.
        val initialSettings = container.settings.first()

        provideContent {
            val settings by container.settings.collectAsState(initialSettings)
            val today = LocalDate.now()
            val month = YearMonth.from(today)
            val gridStart = CalendarWeek.startOfMonthGrid(month, settings.weekStart)
            val gridEnd = gridStart.plusDays(41)

            val byDay by produceState(
                initialValue = emptyMap<LocalDate, List<EventOccurrence>>(),
                key1 = gridStart,
            ) {
                value = runCatching { repository.occurrences(gridStart, gridEnd) }
                    .getOrDefault(emptyMap())
            }

            val palette = paletteFor(context, settings)
            val holidays = remember(gridStart) {
                runCatching { KhmerHolidays.inRange(gridStart, gridEnd).map { it.date }.toSet() }
                    .getOrDefault(emptySet())
            }

            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(palette.background)
                    .padding(10.dp),
            ) {
                Header(context, today, settings, palette)
                Spacer(GlanceModifier.height(6.dp))
                WeekdayRow(settings.weekStart, palette)
                Spacer(GlanceModifier.height(2.dp))
                // Always six rows: a fixed shape keeps the widget from reflowing between
                // months, which launchers animate badly.
                repeat(6) { week ->
                    Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                        repeat(7) { index ->
                            val date = gridStart.plusDays((week * 7 + index).toLong())
                            DayCell(
                                context = context,
                                date = date,
                                inMonth = date.month == month.month,
                                isToday = date == today,
                                hasEvents = byDay[date]?.isNotEmpty() == true,
                                isHoliday = settings.showHolidays && date in holidays,
                                settings = settings,
                                palette = palette,
                                modifier = GlanceModifier.defaultWeight(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(
    context: Context,
    today: LocalDate,
    settings: AppSettings,
    palette: WidgetPalette,
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Column(
            modifier = GlanceModifier
                .defaultWeight()
                .clickable(actionStartActivity(dayIntent(context, today))),
        ) {
            Text(
                text = KhmerTerms.solarMonth(today.monthValue) + " " +
                    widgetNumber(today.year, settings.useKhmerNumerals),
                style = TextStyle(
                    color = palette.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
            if (settings.widgetShowLunar) {
                val lunar = Chhankitek.toLunarOrNull(today)
                if (lunar != null) {
                    Text(
                        text = lunar.format(),
                        style = TextStyle(color = palette.muted, fontSize = 11.sp),
                        maxLines = 1,
                    )
                }
            }
        }
        Box(
            modifier = GlanceModifier
                .background(palette.accent)
                .padding(horizontal = 10.dp, vertical = 4.dp)
                .clickable(actionStartActivity(newEventIntent(context, today))),
            contentAlignment = Alignment.Center,
        ) {
            Text("+", style = TextStyle(color = palette.onAccent, fontSize = 15.sp))
        }
    }
}

@Composable
private fun WeekdayRow(weekStart: DayOfWeek, palette: WidgetPalette) {
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        repeat(7) { index ->
            val day = CalendarWeek.daysOfWeek(weekStart)[index]
            Text(
                text = KhmerTerms.dayOfWeekShort(day),
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(
                    color = palette.muted,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun DayCell(
    context: Context,
    date: LocalDate,
    inMonth: Boolean,
    isToday: Boolean,
    hasEvents: Boolean,
    isHoliday: Boolean,
    settings: AppSettings,
    palette: WidgetPalette,
    modifier: GlanceModifier,
) {
    val weekend = settings.highlightWeekends && CalendarWeek.isWeekend(date)
    val textColor = when {
        isToday -> palette.onAccent
        !inMonth -> palette.muted
        isHoliday || weekend -> palette.accent
        else -> palette.onSurface
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (isToday) GlanceModifier.background(palette.accent) else GlanceModifier)
            .clickable(actionStartActivity(dayIntent(context, date))),
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Text(
            text = widgetNumber(date.dayOfMonth, settings.useKhmerNumerals),
            style = TextStyle(
                color = textColor,
                fontSize = 12.sp,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            ),
            maxLines = 1,
        )
        if (settings.showEventDots) {
            Box(
                modifier = GlanceModifier
                    .size(4.dp)
                    .background(
                        if (hasEvents && inMonth) {
                            if (isToday) palette.onAccent else palette.accent
                        } else {
                            ColorProvider(Color.Transparent)
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {}
        }
    }
}

/** Backs the manifest declaration. */
class MonthWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MonthWidget
}

