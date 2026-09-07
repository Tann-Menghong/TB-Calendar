package com.khmercalendar.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
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
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.compose.ui.graphics.Color
import com.khmercalendar.core.khmer.Chhankitek
import com.khmercalendar.core.khmer.KhmerTerms
import com.khmercalendar.data.prefs.AppSettings
import com.khmercalendar.domain.EventOccurrence
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The agenda widget.
 *
 * Shows what is actually next rather than a fixed day: today's remaining events first, then
 * the following days, so a widget checked in the evening is not a list of things already
 * finished.
 */
object AgendaWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = context.container
        val repository = container.eventRepository
        val initialSettings = container.settings.first()

        provideContent {
            val settings by container.settings.collectAsState(initialSettings)
            val now = LocalDateTime.now()
            val today = now.toLocalDate()
            val palette = paletteFor(context, settings)

            val upcoming by produceState(initialValue = emptyList<EventOccurrence>(), key1 = today) {
                value = runCatching { repository.upcoming(now, UPCOMING_DAYS) }
                    .getOrDefault(emptyList())
                    .take(MAX_ROWS)
            }

            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(palette.background)
                    .padding(10.dp),
            ) {
                Header(context, today, settings, palette)
                Spacer(GlanceModifier.height(6.dp))
                if (upcoming.isEmpty()) {
                    Box(
                        modifier = GlanceModifier
                            .fillMaxSize()
                            .clickable(actionStartActivity(newEventIntent(context, today))),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "គ្មានព្រឹត្តិការណ៍ខាងមុខ",
                            style = TextStyle(color = palette.muted, fontSize = 12.sp),
                        )
                    }
                } else {
                    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                        items(upcoming, itemId = { it.eventId * 100 + it.occurrenceDate.dayOfYear }) {
                            EventRow(context, it, today, settings, palette)
                        }
                    }
                }
            }
        }
    }

    private const val UPCOMING_DAYS = 30
    /** Enough to fill the tallest resize; more would only cost the launcher memory. */
    private const val MAX_ROWS = 25
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
                text = KhmerTerms.dayOfWeek(today.dayOfWeek) + " " +
                    widgetNumber(today.dayOfMonth, settings.useKhmerNumerals) + " " +
                    KhmerTerms.solarMonth(today.monthValue),
                style = TextStyle(
                    color = palette.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
            if (settings.widgetShowLunar) {
                Chhankitek.toLunarOrNull(today)?.let { lunar ->
                    Text(
                        text = lunar.format(),
                        style = TextStyle(color = palette.muted, fontSize = 10.sp),
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
private fun EventRow(
    context: Context,
    occurrence: EventOccurrence,
    today: LocalDate,
    settings: AppSettings,
    palette: WidgetPalette,
) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(
                actionStartActivity(
                    eventIntent(context, occurrence.eventId, occurrence.occurrenceDate),
                ),
            ),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Box(
            modifier = GlanceModifier
                .size(width = 3.dp, height = 22.dp)
                .background(ColorProvider(Color(occurrence.colorArgb))),
            contentAlignment = Alignment.Center,
        ) {}
        Spacer(GlanceModifier.width(8.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = occurrence.title,
                style = TextStyle(color = palette.onSurface, fontSize = 12.sp),
                maxLines = 1,
            )
            Text(
                text = subtitle(occurrence, today, settings),
                style = TextStyle(color = palette.muted, fontSize = 10.sp),
                maxLines = 1,
            )
        }
    }
}

/** "ថ្ងៃនេះ 14:00", or the day plus the time when it is further out. */
private fun subtitle(
    occurrence: EventOccurrence,
    today: LocalDate,
    settings: AppSettings,
): String {
    val dayPart = when (occurrence.occurrenceDate) {
        today -> "ថ្ងៃនេះ"
        today.plusDays(1) -> "ថ្ងៃស្អែក"
        else -> widgetNumber(occurrence.occurrenceDate.dayOfMonth, settings.useKhmerNumerals) +
            " " + KhmerTerms.solarMonth(occurrence.occurrenceDate.monthValue)
    }
    if (occurrence.allDay) return "$dayPart · ពេញមួយថ្ងៃ"
    val time = widgetTime(
        occurrence.start.hour,
        occurrence.start.minute,
        settings.useKhmerNumerals,
    )
    return "$dayPart · $time"
}

/** Backs the manifest declaration. */
class AgendaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AgendaWidget
}
