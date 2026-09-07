package com.khmercalendar.ui.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khmercalendar.ai.AnswerSource
import com.khmercalendar.core.khmer.KhmerTerms
import com.khmercalendar.core.nlu.EventDraft
import com.khmercalendar.ui.components.localeNumber
import com.khmercalendar.ui.theme.LocalAppSettings
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * The assistant.
 *
 * Every answer is labelled with how it was produced - exact arithmetic or the local model -
 * because a calendar assistant that quietly guesses is worse than one that says it cannot
 * help. Nothing is written to the calendar without the user pressing "រក្សាទុក".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(
    viewModel: AssistantViewModel,
    onOpenEvent: (Long, LocalDate) -> Unit,
    onManageModels: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    LaunchedEffect(Unit) { viewModel.refreshModelStatus() }
    LaunchedEffect(state.items.size) {
        if (state.items.isNotEmpty()) listState.animateScrollToItem(state.items.lastIndex)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ជំនួយការ") },
                actions = {
                    IconButton(onClick = viewModel::clear) {
                        Icon(Icons.Outlined.DeleteSweep, contentDescription = "សម្អាត")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrivacyBanner(
                modelReady = state.modelReady,
                modelName = state.modelName,
                loading = state.modelLoading,
                installed = state.modelInstalled,
                enabled = state.aiEnabled,
                onLoad = viewModel::loadModel,
                onUnload = viewModel::unloadModel,
                onManage = onManageModels,
            )

            state.notice?.let { notice ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            notice,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        TextButton(onClick = viewModel::dismissNotice) { Text("បិទ") }
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.items.size) { index ->
                    when (val item = state.items[index]) {
                        is ChatItem.User -> UserBubble(item.text)
                        is ChatItem.Reply -> ReplyBubble(item.text, item.source)
                        is ChatItem.Working -> WorkingBubble()
                        is ChatItem.Proposal -> ProposalCard(
                            item = item,
                            onAccept = { viewModel.accept(item) },
                            onDismiss = { viewModel.dismiss(item) },
                            onOpen = { id -> onOpenEvent(id, item.draft.start.toLocalDate()) },
                        )
                    }
                }
                item { Spacer(Modifier.height(4.dp)) }
            }

            QuickActionRow(enabled = !state.busy, onAction = viewModel::quickAction)

            InputRow(
                value = state.input,
                busy = state.busy,
                onChange = viewModel::setInput,
                onSend = viewModel::send,
            )
        }
    }
}

@Composable
private fun PrivacyBanner(
    modelReady: Boolean,
    modelName: String?,
    loading: Boolean,
    installed: Boolean,
    enabled: Boolean,
    onLoad: () -> Unit,
    onUnload: () -> Unit,
    onManage: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Lock,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "ដំណើរការក្នុងឧបករណ៍ទាំងស្រុង",
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    when {
                        loading -> "កំពុងផ្ទុកម៉ូដែល..."
                        modelReady -> "ម៉ូដែល៖ ${modelName ?: "មិនស្គាល់"} — កំពុងដំណើរការ"
                        !enabled -> "AI បិទ — មុខងារគណនាទាំងអស់នៅតែដំណើរការ"
                        installed -> "ម៉ូដែលរួចរាល់ ប៉ុន្តែមិនទាន់ផ្ទុក"
                        else -> "គ្មានម៉ូដែល — មុខងារគណនានៅតែដំណើរការ"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when {
                loading -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                modelReady -> TextButton(onClick = onUnload) { Text("បិទ") }
                enabled && installed -> TextButton(onClick = onLoad) { Text("ផ្ទុក") }
                else -> TextButton(onClick = onManage) { Text("ម៉ូដែល") }
            }
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp),
            modifier = Modifier.fillMaxWidth(0.85f),
        ) {
            Text(text, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ReplyBubble(text: String, source: AnswerSource) {
    Column {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
            modifier = Modifier.fillMaxWidth(0.92f),
        ) {
            Text(text, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            text = when (source) {
                AnswerSource.RULES -> "គណនាពិតប្រាកដពីប្រតិទិន"
                AnswerSource.LOCAL_MODEL -> "បង្កើតដោយម៉ូដែលក្នុងឧបករណ៍"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp, top = 2.dp),
        )
    }
}

@Composable
private fun WorkingBubble() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            "កំពុងគិត...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProposalCard(
    item: ChatItem.Proposal,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
    onOpen: (Long) -> Unit,
) {
    if (item.dismissed) return
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("ព្រឹត្តិការណ៍ដែលស្នើ", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                item.draft.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(draftWhen(item.draft), style = MaterialTheme.typography.bodyMedium)
            item.draft.location?.takeIf { it.isNotBlank() }?.let {
                Text("ទីតាំង៖ $it", style = MaterialTheme.typography.bodySmall)
            }
            if (item.draft.reminderMinutes.isNotEmpty()) {
                val khmerDigits = LocalAppSettings.current.useKhmerNumerals
                Text(
                    "រំលឹក៖ " + item.draft.reminderMinutes.joinToString(", ") { minutes ->
                        if (minutes == 0) {
                            "ពេលចាប់ផ្តើម"
                        } else {
                            "${localeNumber(minutes, khmerDigits)} នាទីមុន"
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (item.draft.explanation.isNotBlank()) {
                Text(
                    item.draft.explanation,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (item.clashes.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text("ជាន់គ្នាជាមួយ៖", style = MaterialTheme.typography.labelMedium)
                        item.clashes.forEach { clash ->
                            Text("· ${clash.title}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            if (item.savedEventId != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("បានរក្សាទុក", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { onOpen(item.savedEventId) }) { Text("បើកមើល") }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onAccept) { Text("រក្សាទុក") }
                    OutlinedButton(onClick = onDismiss) { Text("បោះបង់") }
                }
            }
        }
    }
}

@Composable
private fun QuickActionRow(enabled: Boolean, onAction: (QuickAction) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        QuickAction.entries.forEach { action ->
            AssistChip(
                onClick = { if (enabled) onAction(action) },
                enabled = enabled,
                label = { Text(action.label) },
                leadingIcon = {
                    Icon(Icons.Outlined.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                },
            )
        }
    }
}

@Composable
private fun InputRow(
    value: String,
    busy: Boolean,
    onChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("សរសេរសំណើរបស់អ្នក...") },
            maxLines = 4,
        )
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(onClick = onSend, enabled = !busy && value.isNotBlank()) {
                Icon(
                    Icons.AutoMirrored.Outlined.Send,
                    contentDescription = "ផ្ញើ",
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

private val TIME = DateTimeFormatter.ofPattern("HH:mm")

@Composable
private fun draftWhen(draft: EventDraft): String {
    val date = draft.start.toLocalDate()
    val day = "${KhmerTerms.dayOfWeek(date.dayOfWeek)} ${localeNumber(date.dayOfMonth)} " +
        "${KhmerTerms.solarMonth(date.monthValue)} ${localeNumber(date.year)}"
    return if (draft.allDay) {
        "$day · ពេញមួយថ្ងៃ"
    } else {
        "$day · ${localeNumber(draft.start.format(TIME))} - ${localeNumber(draft.end.format(TIME))}"
    }
}
