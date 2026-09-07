package com.khmercalendar.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.khmercalendar.core.khmer.KhmerNumerals
import com.khmercalendar.core.work.WorkClock
import com.khmercalendar.core.work.WorkSchedule
import com.khmercalendar.core.work.WorkState
import com.khmercalendar.core.work.WorkStatus
import com.khmercalendar.ui.components.CalendarFormats
import com.khmercalendar.ui.components.LocalUses24Hour
import com.khmercalendar.ui.theme.LocalAppSettings
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDateTime

/**
 * The live work-time countdown.
 *
 * ## Why the tick is where it is
 *
 * The countdown shows seconds, so something has to run every second - but only while the card
 * is actually on screen and the app is actually in the foreground. [repeatOnLifecycle] at
 * STARTED gives exactly that: leaving the screen or backgrounding the app stops the loop, and
 * returning restarts it. A timer that keeps ticking behind a locked screen would drain a
 * battery to display a number nobody is looking at.
 *
 * On a day with nothing left to count down to - after work, or a day off - the loop stops
 * entirely rather than recomputing an unchanging value once a second.
 */
@Composable
fun WorkCountdownCard(
    schedule: WorkSchedule,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
) {
    val status = rememberWorkStatus(schedule)
    if (status.state == WorkState.DISABLED) return

    val settings = LocalAppSettings.current
    val uses24Hour = LocalUses24Hour.current
    val palette = paletteFor(status.state)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(palette.gradient)),
    ) {
        Row(
            Modifier.padding(18.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = status.state.labelKm,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.onGradient,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = CalendarFormats.time(
                        status.now.toLocalTime(),
                        uses24Hour,
                        settings.useKhmerNumerals,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.onGradient.copy(alpha = 0.85f),
                )

                Spacer(Modifier.height(10.dp))
                status.remaining?.let { remaining ->
                    Text(
                        text = clock(remaining, settings.useKhmerNumerals),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.onGradient,
                    )
                    Text(
                        text = subtitleFor(status, uses24Hour, settings.useKhmerNumerals),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.onGradient.copy(alpha = 0.85f),
                    )
                } ?: Text(
                    text = closingLine(status),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.onGradient.copy(alpha = 0.9f),
                )

                if (status.remainingToday > Duration.ZERO && status.state != WorkState.FINISHED) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "ការងារនៅសល់ថ្ងៃនេះ៖ " +
                            clock(status.remainingToday, settings.useKhmerNumerals),
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.onGradient.copy(alpha = 0.8f),
                    )
                }
            }

            status.progress?.let { progress ->
                Spacer(Modifier.size(12.dp))
                ProgressRing(
                    progress = progress,
                    track = palette.onGradient.copy(alpha = 0.25f),
                    indicator = palette.onGradient,
                    label = "${KhmerNumerals.toKhmer((progress * 100).toInt())}%",
                    labelColor = palette.onGradient,
                    khmerNumerals = settings.useKhmerNumerals,
                )
            }
        }
    }
}

/**
 * Recomputes the status on a ticking clock.
 *
 * The status is derived from [WorkClock], which is pure - this composable owns nothing but
 * the clock. That split is what lets every state boundary be unit-tested without a device.
 */
@Composable
private fun rememberWorkStatus(schedule: WorkSchedule): WorkStatus {
    val lifecycleOwner = LocalLifecycleOwner.current
    val initial = WorkClock.statusAt(schedule, LocalDateTime.now())

    val status by produceState(initialValue = initial, schedule, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val next = WorkClock.statusAt(schedule, LocalDateTime.now())
                value = next
                // Nothing left to count down to today: stop ticking rather than recomputing
                // an unchanging value every second until midnight.
                if (!next.hasCountdown) break
                delay(1_000)
            }
        }
    }
    return status
}

/** A circular progress indicator drawn directly, so it can carry a label and a gradient. */
@Composable
private fun ProgressRing(
    progress: Float,
    track: Color,
    indicator: Color,
    label: String,
    labelColor: Color,
    khmerNumerals: Boolean,
) {
    // Animated so a resumed screen sweeps to its value instead of snapping.
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        label = "workProgress",
    )
    Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(84.dp)) {
            val stroke = 9.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = indicator,
                startAngle = -90f,
                sweepAngle = 360f * animated,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Text(
            text = if (khmerNumerals) label else label.let { KhmerNumerals.toAscii(it) },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = labelColor,
            fontSize = 16.sp,
        )
    }
}

// -----------------------------------------------------------------------------------------

private data class WorkPalette(val gradient: List<Color>, val onGradient: Color)

/**
 * A colour per state.
 *
 * The gradient is doing work here rather than decoration: at a glance across the room, green
 * means working, amber means a break, blue-grey means the day is done. Two stops only, and
 * always in the same hue family, so the dashboard does not turn into a paint chart.
 */
private fun paletteFor(state: WorkState): WorkPalette = when (state) {
    WorkState.WORKING -> WorkPalette(
        gradient = listOf(Color(0xFF1E9E6A), Color(0xFF12795A)),
        onGradient = Color.White,
    )

    WorkState.BREAK -> WorkPalette(
        gradient = listOf(Color(0xFFE0902B), Color(0xFFC96F1E)),
        onGradient = Color.White,
    )

    WorkState.BEFORE_WORK -> WorkPalette(
        gradient = listOf(Color(0xFF2F6FED), Color(0xFF2453B8)),
        onGradient = Color.White,
    )

    WorkState.FINISHED -> WorkPalette(
        gradient = listOf(Color(0xFF4A5568), Color(0xFF2D3748)),
        onGradient = Color.White,
    )

    WorkState.DAY_OFF -> WorkPalette(
        gradient = listOf(Color(0xFF7A4FE0), Color(0xFF5A34B0)),
        onGradient = Color.White,
    )

    WorkState.DISABLED -> WorkPalette(
        gradient = listOf(Color(0xFF9AA2B2), Color(0xFF7A8294)),
        onGradient = Color.White,
    )
}

/** HH:MM:SS, in the user's digits. */
private fun clock(duration: Duration, khmerNumerals: Boolean): String {
    val total = duration.seconds.coerceAtLeast(0)
    val text = "%02d:%02d:%02d".format(total / 3600, (total % 3600) / 60, total % 60)
    return if (khmerNumerals) KhmerNumerals.toKhmer(text) else text
}

private fun subtitleFor(status: WorkStatus, uses24Hour: Boolean, khmerNumerals: Boolean): String {
    val at = status.nextTransitionAt?.toLocalTime()
        ?: return status.state.labelKm
    val time = CalendarFormats.time(at, uses24Hour, khmerNumerals)
    return when (status.state) {
        WorkState.WORKING -> {
            val isLast = status.nextBlock == null
            if (isLast) "ចប់ការងារ៖ $time" else "ចប់${status.currentBlock?.labelKm.orEmpty()}៖ $time"
        }

        WorkState.BREAK -> "ចូលធ្វើការវិញ៖ $time"
        WorkState.BEFORE_WORK -> "ចូលធ្វើការនៅ $time"
        else -> time
    }
}

private fun closingLine(status: WorkStatus): String = when (status.state) {
    WorkState.FINISHED -> "ថ្ងៃនេះការងារបានបញ្ចប់"
    WorkState.DAY_OFF -> "ថ្ងៃនេះគ្មានកាលវិភាគការងារ"
    else -> ""
}
