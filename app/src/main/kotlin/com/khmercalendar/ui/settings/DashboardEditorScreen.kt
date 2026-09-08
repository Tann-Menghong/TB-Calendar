package com.khmercalendar.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.khmercalendar.data.prefs.AppSettings
import com.khmercalendar.data.prefs.DashboardCard
import com.khmercalendar.data.prefs.DashboardDensity
import com.khmercalendar.data.prefs.SettingsStore
import com.khmercalendar.domain.DashboardArrangement
import com.khmercalendar.domain.DashboardPreset
import com.khmercalendar.ui.components.localeNumber
import com.khmercalendar.ui.theme.Radius
import com.khmercalendar.ui.theme.Spacing
import com.khmercalendar.ui.theme.Stroke
import kotlinx.coroutines.launch

/** Every row is this tall, which is what lets a drag be arithmetic rather than hit-testing. */
private val RowHeight = 56.dp

/**
 * The dashboard, arranged.
 *
 * This used to be a block at the bottom of the appearance screen, below the wallpaper opacity
 * slider, where the one control that decides what the app's main screen contains was the last
 * thing on a long page. It is now its own screen, reachable from settings and from a long
 * press on any module.
 *
 * ## Three ways to arrange it, on purpose
 *
 * A **preset** is one tap for someone who does not want to arrange anything. **Dragging** is
 * for someone rearranging several modules at once. The **arrows** are for everyone else, and
 * they are not a fallback: they are reachable one-handed, they work with a screen reader, and
 * they do not require discovering a gesture. Drag was added alongside them rather than in
 * place of them for exactly that reason.
 *
 * The drag does not auto-scroll, so moving a module the length of a long list is still an
 * arrows job. That is a deliberate limit rather than an oversight - auto-scrolling a list
 * being reordered under a finger is where this kind of control usually starts losing items.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardEditorScreen(
    settings: AppSettings,
    settingsStore: SettingsStore,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val order = settings.dashboardCards
    val rows = DashboardArrangement.arrangeable(order)
    val hidden = settings.hiddenDashboardCards
    val visibleCount = rows.count { it.key !in hidden }
    val activePreset = DashboardArrangement.presetOf(order, hidden)

    // Held here rather than inside a row: the row that owns the gesture keeps receiving it
    // while the card underneath it moves, so the index being dragged belongs to the screen.
    var dragIndex by remember { mutableIntStateOf(-1) }
    val rowHeightPx = with(LocalDensity.current) { RowHeight.toPx() }

    /** Moves the module at row [from] to row [to], in terms of the saved order. */
    fun moveRow(from: Int, to: Int) {
        if (from !in rows.indices || to !in rows.indices) return
        val next = DashboardArrangement.move(
            order,
            order.indexOf(rows[from]),
            order.indexOf(rows[to]),
        )
        scope.launch { settingsStore.setDashboardOrder(next) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ផ្ទាំងដើម") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "ត្រឡប់")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            val reset = DashboardArrangement.default()
                            scope.launch {
                                settingsStore.setDashboardArrangement(reset.order, reset.hidden)
                            }
                        },
                    ) { Text("កំណត់ដើមវិញ") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
        ) {
            SettingsGroup("គំរូ") {
                Text(
                    "ជ្រើសរើសគំរូមួយ រួចកែតាមចិត្ត",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                )
                Spacer(Modifier.height(Spacing.md))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    DashboardPreset.entries.forEach { preset ->
                        PresetChip(
                            preset = preset,
                            selected = preset == activePreset,
                            onClick = {
                                val next = DashboardArrangement.apply(preset)
                                scope.launch {
                                    settingsStore.setDashboardArrangement(next.order, next.hidden)
                                }
                            },
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    if (activePreset == null) {
                        "ប្លង់ផ្ទាល់ខ្លួន"
                    } else {
                        activePreset.descriptionKm
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                )
            }

            HorizontalDivider()

            SettingsGroup("ទំហំកាត") {
                DashboardDensity.entries.forEach { density ->
                    ChoiceRow(
                        title = density.labelKm,
                        selected = settings.dashboardDensity == density,
                        onClick = { scope.launch { settingsStore.setDashboardDensity(density) } },
                    )
                }
            }

            HorizontalDivider()

            SettingsGroup("ផ្ទាំង") {
                Text(
                    "បង្ហាញ ${localeNumber(visibleCount)} ក្នុងចំណោម ${localeNumber(rows.size)} · " +
                        "ចុចឱ្យយូរដើម្បីអូស",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                )
                Spacer(Modifier.height(Spacing.sm))

                // Rows are the arrangeable modules; moves are translated back to indices in
                // the saved order, which still carries the retired one.
                rows.forEachIndexed { slot, card ->
                    // The lambdas and the list are read at gesture time, not at the time the
                    // gesture was installed: pointerInput keeps its block across
                    // recompositions, and a captured copy of the order would be one move out
                    // of date from the first step of a drag onwards.
                    val currentRows by rememberUpdatedState(rows)
                    val onMove by rememberUpdatedState<(Int, Int) -> Unit> { from, to ->
                        moveRow(from, to)
                    }
                    val accumulated = remember { mutableFloatStateOf(0f) }

                    ModuleRow(
                        card = card,
                        visible = card.key !in hidden,
                        canMoveUp = slot > 0,
                        canMoveDown = slot < rows.lastIndex,
                        dragging = dragIndex == slot,
                        onToggle = { show ->
                            val next = hidden.toMutableSet()
                            if (show) next.remove(card.key) else next.add(card.key)
                            scope.launch { settingsStore.setDashboardHidden(next) }
                        },
                        onArrow = { delta -> moveRow(slot, slot + delta) },
                        handle = {
                            DragHandle(
                                Modifier.pointerInput(slot) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            dragIndex = slot
                                            accumulated.floatValue = 0f
                                            haptics.performHapticFeedback(
                                                HapticFeedbackType.LongPress,
                                            )
                                        },
                                        onDragEnd = { dragIndex = -1 },
                                        onDragCancel = { dragIndex = -1 },
                                        onDrag = { change, delta ->
                                            change.consume()
                                            accumulated.floatValue += delta.y
                                            // One row at a time, so a fast drag lands where
                                            // the finger is rather than skipping past the
                                            // positions it crossed.
                                            while (
                                                accumulated.floatValue >= rowHeightPx &&
                                                dragIndex < currentRows.lastIndex
                                            ) {
                                                onMove(dragIndex, dragIndex + 1)
                                                dragIndex += 1
                                                accumulated.floatValue -= rowHeightPx
                                            }
                                            while (
                                                accumulated.floatValue <= -rowHeightPx &&
                                                dragIndex > 0
                                            ) {
                                                onMove(dragIndex, dragIndex - 1)
                                                dragIndex -= 1
                                                accumulated.floatValue += rowHeightPx
                                            }
                                        },
                                    )
                                },
                            )
                        },
                    )
                }
            }

            Spacer(Modifier.height(64.dp))
        }
    }
}

/** The grip. Its own target, so a drag can never be mistaken for a tap on the switch. */
@Composable
private fun DragHandle(modifier: Modifier = Modifier) {
    Icon(
        Icons.Outlined.DragIndicator,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .size(24.dp)
            .clearAndSetSemantics { contentDescription = "អូសដើម្បីរៀបលំដាប់" },
    )
}

@Composable
private fun PresetChip(preset: DashboardPreset, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(Radius.sm)
    Column(
        Modifier
            .clip(shape)
            .background(if (selected) scheme.primaryContainer else scheme.surfaceVariant)
            .border(
                Stroke.hairline,
                if (selected) scheme.primary else Color.Transparent,
                shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
    ) {
        Text(
            preset.labelKm,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            localeNumber(preset.cards.size) + " ផ្ទាំង",
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
        )
    }
}

/**
 * One module: where it sits, and whether it shows at all.
 *
 * The drag handle is a separate target from the row. A drag that starts anywhere on the row
 * would fight the switch and the two arrows, and a long press that sometimes reorders and
 * sometimes does nothing is worse than one that only works in a place you can see.
 */
@Composable
private fun ModuleRow(
    card: DashboardCard,
    visible: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    dragging: Boolean,
    onToggle: (Boolean) -> Unit,
    onArrow: (Int) -> Unit,
    handle: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val background by animateColorAsState(
        targetValue = if (dragging) scheme.surfaceVariant else Color.Transparent,
        label = "dragRow",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .height(RowHeight)
            .padding(horizontal = Spacing.sm)
            .clip(RoundedCornerShape(Radius.sm))
            .background(background)
            .padding(start = Spacing.sm, end = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        handle()
        Spacer(Modifier.width(Spacing.md))
        Text(
            card.labelKm,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            color = if (visible) scheme.onSurface else scheme.onSurfaceVariant,
        )
        IconButton(onClick = { onArrow(-1) }, enabled = canMoveUp) {
            Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "ផ្លាស់ឡើងលើ")
        }
        IconButton(onClick = { onArrow(1) }, enabled = canMoveDown) {
            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "ផ្លាស់ចុះក្រោម")
        }
        Switch(checked = visible, onCheckedChange = onToggle)
    }
}
