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
import androidx.compose.foundation.layout.width
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
import com.khmercalendar.ui.components.GradientCard
import com.khmercalendar.ui.components.LocalUses24Hour
import com.khmercalendar.ui.theme.AppType
import com.khmercalendar.ui.theme.GradientTone
import com.khmercalendar.ui.theme.Gradients
import com.khmercalendar.ui.theme.LocalAppSettings
import com.khmercalendar.ui.components.ProgressBar
import com.khmercalendar.ui.components.rememberReducedMotion
import com.khmercalendar.ui.theme.Motion
import com.khmercalendar.ui.theme.Radius
import com.khmercalendar.ui.theme.Spacing
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
    val tone = toneFor(status.state)

    GradientCard(
        tone = tone,
        modifier = modifier,
        shape = RoundedCornerShape(Radius.lg),
        onClick = onOpenSettings,
    ) {
        Column(Modifier.fillMaxWidth()) {
            // The state as a badge, and the clock beside it. The state used to be plain text
            // the same size as the line under it, which meant the single most important word
            // on the dashboard - whether you are working - had no more weight than the
            // timestamp next to it.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = status.state.labelKm,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = tone.onTone,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = CalendarFormats.time(
                        status.now.toLocalTime(),
                        uses24Hour,
                        settings.useKhmerNumerals,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = tone.onTone.copy(alpha = 0.85f),
                )
            }

            Spacer(Modifier.height(Spacing.lg))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    status.remaining?.let { remaining ->
                        Text(
                            text = clock(remaining, settings.useKhmerNumerals),
                            // Tabular figures: without them the string changes width as the
                            // digits tick and the whole card jitters once a second.
                            style = AppType.countdown(),
                            color = tone.onTone,
                            maxLines = 1,
                        )
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            text = subtitleFor(status, uses24Hour, settings.useKhmerNumerals),
                            style = MaterialTheme.typography.bodySmall,
                            color = tone.onTone.copy(alpha = 0.85f),
                        )
                    } ?: Text(
                        text = closingLine(status),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = tone.onTone.copy(alpha = 0.95f),
                    )
                }

                status.progress?.let { progress ->
                    Spacer(Modifier.width(Spacing.lg))
                    ProgressRing(
                        progress = progress,
                        track = tone.onTone.copy(alpha = 0.22f),
                        indicator = tone.onTone,
                        label = "${KhmerNumerals.toKhmer((progress * 100).toInt())}%",
                        labelColor = tone.onTone,
                        khmerNumerals = settings.useKhmerNumerals,
                    )
                }
            }

            if (status.remainingToday > Duration.ZERO && status.state != WorkState.FINISHED) {
                Spacer(Modifier.height(Spacing.lg))
                // How much of the working day is left in total, which is a different question
                // from how long until the next break and the one people actually ask at 3pm.
                // Label and value stacked on the left, not spread to both edges. The
                // dashboard's floating action button sits over the bottom-right corner of
                // whichever card is there, and a number pushed to that corner disappears
                // underneath it.
                Text(
                    text = "ការងារនៅសល់ថ្ងៃនេះ",
                    style = MaterialTheme.typography.labelMedium,
                    color = tone.onTone.copy(alpha = 0.8f),
                )
                Text(
                    text = clock(status.remainingToday, settings.useKhmerNumerals),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = tone.onTone,
                )
                Spacer(Modifier.height(Spacing.sm))
                ProgressBar(
                    fraction = status.dayFraction(),
                    track = tone.onTone.copy(alpha = 0.22f),
                    indicator = tone.onTone,
                )
            }
        }
    }
}

/**
 * How much of today's scheduled work is behind you.
 *
 * Separate from [WorkStatus.progress], which is progress through the *current block*. Both
 * are useful and they are not the same number: at 1:35pm you are two minutes into the
 * afternoon and most of the way through the day.
 */
private fun WorkStatus.dayFraction(): Float {
    val total = totalToday.seconds
    if (total <= 0L) return 0f
    val worked = (total - remainingToday.seconds).coerceAtLeast(0L)
    return (worked.toFloat() / total.toFloat()).coerceIn(0f, 1f)
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
    // Sweeps to its value on resume rather than snapping - unless the user has asked the
    // system for less movement, in which case it simply arrives.
    val reduced = rememberReducedMotion()
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = if (reduced) 0 else Motion.PROGRESS,
        ),
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

/**
 * A gradient per state.
 *
 * The mapping lives here; the colours live in [Gradients], with every other gradient in the
 * app, so a restyle is one edit in the theme rather than a hunt through the screens.
 */
private fun toneFor(state: WorkState): GradientTone = when (state) {
    WorkState.WORKING -> Gradients.working
    WorkState.BREAK -> Gradients.resting
    WorkState.BEFORE_WORK -> Gradients.upcoming
    WorkState.FINISHED -> Gradients.done
    WorkState.DAY_OFF -> Gradients.away
    WorkState.DISABLED -> Gradients.muted
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
