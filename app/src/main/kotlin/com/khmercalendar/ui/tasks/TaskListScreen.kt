package com.khmercalendar.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khmercalendar.core.khmer.KhmerTerms
import com.khmercalendar.domain.TaskBucket
import com.khmercalendar.domain.TaskItem
import com.khmercalendar.domain.Checklist
import com.khmercalendar.domain.TaskPriority
import com.khmercalendar.ui.components.ProgressBar
import com.khmercalendar.ui.components.localeNumber
import com.khmercalendar.ui.home.ModuleLabel
import com.khmercalendar.ui.theme.CardAccent
import com.khmercalendar.ui.theme.IconSize
import com.khmercalendar.ui.theme.Radius
import com.khmercalendar.ui.theme.Spacing
import com.khmercalendar.ui.theme.Stroke
import com.khmercalendar.ui.theme.color
import java.time.LocalDate

/**
 * The to-do list.
 *
 * ## What this is, and what it is not
 *
 * It is a view of the calendar, not a separate list beside it. Every line here is an entry in
 * the same table the month grid and the day timeline read, which is why a task added at the
 * top of this screen shows up on its day everywhere else in the app a moment later, and why
 * ticking one off here changes the dashboard's progress ring.
 *
 * The alternative - a tasks table of its own - is easier to write and worse to live with: two
 * stores that have to agree about what a day contains, and a "sync" that only ever gets
 * noticed when it stops working.
 *
 * ## The sections are relative, on purpose
 *
 * ហួសកំណត់ / ថ្ងៃនេះ / ថ្ងៃស្អែក / សប្តាហ៍នេះ / ក្រោយៗ. Not dates, because a to-do list answers "what do I
 * have to do now", and a date only answers that after you have worked out what today is.
 * Overdue leads, because work you have already missed is the most useful thing this screen
 * can tell you and the easiest for a forward-looking list to hide.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    viewModel: TaskListViewModel,
    onOpenTask: (Long, LocalDate) -> Unit,
    onOpenAgenda: () -> Unit,
    onFullEditor: (LocalDate) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("កិច្ចការ") },
                actions = {
                    TextButton(onClick = { viewModel.setShowCompleted(!state.showCompleted) }) {
                        Text(if (state.showCompleted) "លាក់រួចរាល់" else "បង្ហាញរួចរាល់")
                    }
                    TextButton(onClick = onOpenAgenda) { Text("កាលវិភាគ") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TaskSummary(state)
            QuickAdd(
                today = state.today,
                onAdd = viewModel::add,
                onFullEditor = onFullEditor,
            )
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Spacing.lg,
                    end = Spacing.lg,
                    bottom = 96.dp,
                ),
            ) {
                state.groups.forEach { group ->
                    item(key = "h-${group.bucket.name}") {
                        BucketHeader(group.bucket, group.tasks.size)
                    }
                    if (group.tasks.isEmpty()) {
                        item(key = "e-${group.bucket.name}") { NothingToday() }
                    }
                    items(group.tasks, key = { "${group.bucket.name}-${it.eventId}" }) { task ->
                        TaskRow(
                            checklist = state.checklists[task.occurrence.eventId],
                            task = task,
                            today = state.today,
                            onToggle = { done -> viewModel.setCompleted(task.eventId, task.date, done) },
                            onCyclePriority = {
                                viewModel.setPriority(task.eventId, next(task.priority))
                            },
                            onOpen = { onOpenTask(task.eventId, task.date) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Tapping the flag cycles rather than opening a menu.
 *
 * Three values and one target: a menu would be three taps to do what the list exists to make
 * quick, and the flag shows its own state so nothing is hidden behind the gesture.
 */
private fun next(priority: TaskPriority): TaskPriority = when (priority) {
    TaskPriority.NORMAL -> TaskPriority.HIGH
    TaskPriority.HIGH -> TaskPriority.LOW
    TaskPriority.LOW -> TaskPriority.NORMAL
}

@Composable
private fun TaskSummary(state: TaskListState) {
    val scheme = MaterialTheme.colorScheme
    val warning = CardAccent.WARNING.color()
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ModuleLabel("TODAY'S LOAD", modifier = Modifier.weight(1f))
            if (state.progress.overdue > 0) {
                Text(
                    "ហួសកំណត់ ${localeNumber(state.progress.overdue)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = warning,
                    fontWeight = FontWeight.SemiBold,
                )
            } else {
                Text(
                    "${localeNumber(state.progress.done)}/${localeNumber(state.progress.total)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        ProgressBar(fraction = state.progress.fraction)
    }
}

/**
 * The one-line add.
 *
 * A title and a day, and nothing else. Everything a task needs beyond that - a time, a
 * reminder, a category, a repeat - is in the full editor one tap away, and putting any of it
 * here would make the fast path slower than the slow one.
 */
@Composable
private fun QuickAdd(
    today: LocalDate,
    onAdd: (String, LocalDate, TaskPriority) -> Unit,
    onFullEditor: (LocalDate) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var offset by remember { mutableLongStateOf(0L) }
    var priority by remember { mutableStateOf(TaskPriority.NORMAL) }
    val focus = remember { FocusRequester() }
    val date = today.plusDays(offset)

    fun submit() {
        if (text.isBlank()) return
        onAdd(text, date, priority)
        // The day and the priority stay put. Adding five things for Thursday means picking
        // Thursday once, not five times.
        text = ""
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            placeholder = { Text("បន្ថែមកិច្ចការ...") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            trailingIcon = {
                IconButton(onClick = { submit() }, enabled = text.isNotBlank()) {
                    Icon(Icons.Outlined.Add, contentDescription = "បន្ថែម")
                }
            },
        )
        Spacer(Modifier.height(Spacing.sm))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DayChip("ថ្ងៃនេះ", offset == 0L) { offset = 0L }
            DayChip("ស្អែក", offset == 1L) { offset = 1L }
            DayChip("សប្តាហ៍ក្រោយ", offset == 7L) { offset = 7L }
            PriorityChip(priority) { priority = next(priority) }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { onFullEditor(date) }) { Text("លម្អិត") }
        }
    }
}

@Composable
private fun DayChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(Radius.sm)
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .clip(shape)
            .background(if (selected) scheme.primaryContainer else scheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
    )
}

@Composable
private fun PriorityChip(priority: TaskPriority, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Radius.sm)
    val accent = priorityColor(priority)
    Row(
        Modifier
            .clip(shape)
            .border(Stroke.hairline, accent.copy(alpha = 0.5f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
        Spacer(Modifier.width(Spacing.sm))
        Text(
            priority.labelKm,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun priorityColor(priority: TaskPriority): Color = when (priority) {
    TaskPriority.HIGH -> CardAccent.WARNING.color()
    TaskPriority.NORMAL -> CardAccent.TASKS.color()
    TaskPriority.LOW -> MaterialTheme.colorScheme.outline
}

@Composable
private fun BucketHeader(bucket: TaskBucket, count: Int) {
    val accent = when (bucket) {
        TaskBucket.OVERDUE -> CardAccent.WARNING.color()
        TaskBucket.TODAY -> CardAccent.TASKS.color()
        TaskBucket.DONE -> MaterialTheme.colorScheme.outline
        else -> CardAccent.CALENDAR.color()
    }
    Row(
        Modifier.fillMaxWidth().padding(top = Spacing.lg, bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            bucket.labelKm,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = accent,
        )
        Spacer(Modifier.width(Spacing.sm))
        if (count > 0) {
            Text(
                localeNumber(count),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NothingToday() {
    Text(
        "គ្មានកិច្ចការសម្រាប់ថ្ងៃនេះ",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = Spacing.sm),
    )
}

/**
 * One task.
 *
 * The date sits on the right for every row, including today's, because a list whose rows are
 * shaped differently depending on which section they are in is harder to scan than one where
 * the same fact is always in the same place.
 */
@Composable
private fun TaskRow(
    task: TaskItem,
    today: LocalDate,
    checklist: Checklist.Progress?,
    onToggle: (Boolean) -> Unit,
    onCyclePriority: () -> Unit,
    onOpen: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val accent = priorityColor(task.priority)
    val done = task.isCompleted
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.sm))
            .clickable(onClick = onOpen)
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onToggle(!done) }) {
            Icon(
                if (done) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = if (done) "សម្គាល់ថាមិនទាន់រួច" else "សម្គាល់ថារួចរាល់",
                tint = if (done) CardAccent.TASKS.color() else scheme.outline,
                modifier = Modifier.size(IconSize.small),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                task.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (done) scheme.onSurfaceVariant else scheme.onSurface,
                textDecoration = if (done) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    dueText(task.date, today),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (!done && task.date.isBefore(today)) {
                        CardAccent.WARNING.color()
                    } else {
                        scheme.onSurfaceVariant
                    },
                )
                // Steps, when there are any. Beside the due date rather than on its own line:
                // it is the same kind of fact about the task, and a second line for "២/៥"
                // would make every task with a checklist taller than every task without one.
                if (checklist != null && !checklist.isEmpty) {
                    Text(
                        text = "  ·  " + localeNumber(checklist.done) + "/" +
                            localeNumber(checklist.total),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (checklist.isComplete) {
                            CardAccent.TASKS.color()
                        } else {
                            scheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
        // The flag is its own target, so cycling urgency never opens the task by accident.
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(onClick = onCyclePriority),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(if (task.priority == TaskPriority.HIGH) 12.dp else 8.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
        }
    }
}

/** The due date in words, because "១០ កញ្ញា" only helps if you know today's date. */
@Composable
private fun dueText(date: LocalDate, today: LocalDate): String {
    val days = java.time.temporal.ChronoUnit.DAYS.between(today, date)
    return when {
        days == 0L -> "ថ្ងៃនេះ"
        days == 1L -> "ថ្ងៃស្អែក"
        days == -1L -> "ម្សិលមិញ"
        days < 0L -> "យឺត ${localeNumber(-days)} ថ្ងៃ"
        days < 7L -> "ថ្ងៃ" + KhmerTerms.dayOfWeek(date.dayOfWeek)
        else -> "${localeNumber(date.dayOfMonth)} ${KhmerTerms.solarMonth(date.monthValue)}"
    }
}
