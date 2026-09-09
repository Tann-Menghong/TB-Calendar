package com.khmercalendar.ui.focus

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.khmercalendar.domain.FocusKind
import com.khmercalendar.domain.FocusTimer
import com.khmercalendar.ui.components.localeNumber
import com.khmercalendar.ui.theme.LocalAppSettings
import com.khmercalendar.ui.theme.Spacing
import kotlinx.coroutines.delay

/**
 * ផ្តោតអារម្មណ៍ — the focus timer.
 *
 * ## Where the countdown comes from
 *
 * Not from a timer object. The running session is a start time and a length in the database,
 * and the number on screen is `now` minus that, recomputed by the ticker below. Which means
 * backgrounding the app, killing the process, or rotating the phone changes nothing at all -
 * there is no in-memory countdown to lose - and nothing is ticking when this screen is not
 * being looked at.
 *
 * ## Why the ticker is bound to the lifecycle
 *
 * A one-second loop that keeps running while the screen is off is a battery leak with a
 * pleasant face. `repeatOnLifecycle(STARTED)` stops it the moment the screen stops being
 * visible and restarts it on the way back, where it recomputes from the clock and is
 * immediately correct again.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusScreen(
    viewModel: FocusViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val khmerNumerals = LocalAppSettings.current.useKhmerNumerals
    val now = rememberTickingClock(active = state.running != null)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ផ្តោតអារម្មណ៍") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ត្រឡប់")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val running = state.running
            val remaining = running?.let { FocusTimer.remainingMillis(it, now) } ?: 0L
            val progress = running?.let { FocusTimer.progress(it, now) } ?: 0f

            Spacer(Modifier.height(Spacing.lg))
            TimerRing(
                progress = progress,
                label = if (running != null) {
                    FocusTimer.format(remaining)
                } else {
                    FocusTimer.format(state.settings.minutesFor(state.nextKind) * 60_000L)
                },
                caption = (running?.kind ?: state.nextKind).labelKm,
                khmerNumerals = khmerNumerals,
            )

            Spacer(Modifier.height(Spacing.xl))

            if (running == null) {
                Button(
                    onClick = { viewModel.start(state.nextKind) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("ចាប់ផ្តើម " + state.nextKind.labelKm)
                }
                Spacer(Modifier.height(Spacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    FocusKind.entries.filter { it != state.nextKind }.forEach { kind ->
                        TextButton(onClick = { viewModel.start(kind) }) { Text(kind.labelKm) }
                    }
                }
            } else {
                Button(onClick = viewModel::stop, modifier = Modifier.fillMaxWidth()) {
                    // "Finish", not "pause". A paused focus session is a session you did not
                    // have, and pretending otherwise would put invented minutes into totals.
                    Text(if (remaining == 0L) "រួចរាល់" else "បញ្ចប់ឥឡូវ")
                }
                Spacer(Modifier.height(Spacing.sm))
                OutlinedButton(onClick = viewModel::discard, modifier = Modifier.fillMaxWidth()) {
                    Text("បោះបង់")
                }
            }

            Spacer(Modifier.height(Spacing.xl))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(Spacing.lg))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Figure("ថ្ងៃនេះ", state.minutesToday, khmerNumerals)
                Figure("សប្តាហ៍នេះ", state.minutesThisWeek, khmerNumerals)
                Figure("វគ្គថ្ងៃនេះ", state.focusesToday, khmerNumerals, isMinutes = false)
            }
        }
    }
}

/**
 * A clock that ticks once a second, and only while there is something to count.
 *
 * `produceState` keyed on [active] so the loop does not exist at all when nothing is running,
 * and `repeatOnLifecycle` so it stops when the screen does.
 */
@Composable
private fun rememberTickingClock(active: Boolean): Long {
    val owner = LocalLifecycleOwner.current
    return produceState(initialValue = System.currentTimeMillis(), active, owner) {
        if (!active) {
            value = System.currentTimeMillis()
            return@produceState
        }
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = System.currentTimeMillis()
                delay(1_000L)
            }
        }
    }.value
}

/** The countdown, as a ring. Numbers stay upright and legible; the ring carries the progress. */
@Composable
private fun TimerRing(
    progress: Float,
    label: String,
    caption: String,
    khmerNumerals: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 14.dp.toPx()
            drawArc(
                color = scheme.surfaceVariant,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = scheme.primary,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (khmerNumerals) {
                    com.khmercalendar.core.khmer.KhmerNumerals.toKhmer(label)
                } else {
                    label
                },
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = caption,
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Figure(label: String, value: Int, khmerNumerals: Boolean, isMinutes: Boolean = true) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = localeNumber(value, khmerNumerals) + if (isMinutes) " នាទី" else "",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
