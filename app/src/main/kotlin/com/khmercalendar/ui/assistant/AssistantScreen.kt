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
import androidx.compose.ui.graphics.Brush
import com.khmercalendar.ui.theme.IconSize
import com.khmercalendar.ui.theme.LocalAccentTrio
import com.khmercalendar.ui.theme.Spacing
import com.khmercalendar.ui.theme.TechShape
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khmercalendar.ai.AnswerSource
import com.khmercalendar.core.khmer.KhmerTerms
import com.khmercalendar.core.nlu.EventProposal
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
    sharedText: String? = null,
    onOpenEvent: (Long, LocalDate) -> Unit,
    onManageModels: () -> Unit,
) {
    // Text shared in from another app is analysed once, keyed so a rotation does not resend it.
    LaunchedEffect(sharedText) {
        sharedText?.let { viewModel.analyseSharedText(it) }
    }

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
                            onOpen = { id -> item.proposal.date?.let { d -> onOpenEvent(id, d) } },
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
    val trio = LocalAccentTrio.current
    Box(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(
                    listOf(
                        trio.alt.copy(alpha = 0.18f),
                        trio.far.copy(alpha = 0.10f),
                        Color.Transparent,
                    ),
                ),
            )
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // The lock is the point of this strip, so it gets a tile rather than a 16dp
            // glyph lost against the text. "It runs here, nothing is sent" is the single
            // most reassuring thing this screen can say, and it was set in caption type.
            Box(
                Modifier
                    .size(36.dp)
                    .clip(TechShape)
                    .background(trio.alt.copy(alpha = 0.20f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(IconSize.small),
                    tint = trio.alt,
                )
            }
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    "\u178a\u17c6\u178e\u17be\u179a\u1780\u17b6\u179a\u1780\u17d2\u1793\u17bb\u1784\u17a7\u1794\u1780\u179a\u178e\u17cd\u1791\u17b6\u17c6\u1784\u179f\u17d2\u179a\u17bb\u1784",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    when {
                        loading -> "\u1780\u17c6\u1796\u17bb\u1784\u1795\u17d2\u1791\u17bb\u1780\u1798\u17c9\u17bc\u178a\u17c2\u179b..."
                        modelReady -> "\u1798\u17c9\u17bc\u178a\u17c2\u179b\u17d6 ${modelName ?: "\u1798\u17b7\u1793\u179f\u17d2\u1782\u17b6\u179b\u17cb"} \u2014 \u1780\u17c6\u1796\u17bb\u1784\u178a\u17c6\u178e\u17be\u179a\u1780\u17b6\u179a"
                        !enabled -> "AI \u1794\u17b7\u1791 \u2014 \u1798\u17bb\u1781\u1784\u17b6\u179a\u1782\u178e\u1793\u17b6\u1791\u17b6\u17c6\u1784\u17a2\u179f\u17cb\u1793\u17c5\u178f\u17c2\u178a\u17c6\u178e\u17be\u179a\u1780\u17b6\u179a"
                        installed -> "\u1798\u17c9\u17bc\u178a\u17c2\u179b\u179a\u17bd\u1785\u179a\u17b6\u179b\u17cb \u1794\u17c9\u17bb\u1793\u17d2\u178f\u17c2\u1798\u17b7\u1793\u1791\u17b6\u1793\u17cb\u1795\u17d2\u1791\u17bb\u1780"
                        else -> "\u1782\u17d2\u1798\u17b6\u1793\u1798\u17c9\u17bc\u178a\u17c2\u179b \u2014 \u1798\u17bb\u1781\u1784\u17b6\u179a\u1782\u178e\u1793\u17b6\u1793\u17c5\u178f\u17c2\u178a\u17c6\u178e\u17be\u179a\u1780\u17b6\u179a"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when {
                loading -> CircularProgressIndicator(Modifier.size(IconSize.small), strokeWidth = 2.dp)
                modelReady -> TextButton(onClick = onUnload) { Text("\u1794\u17b7\u1791") }
                enabled && installed -> TextButton(onClick = onLoad) { Text("\u1795\u17d2\u1791\u17bb\u1780") }
                else -> TextButton(onClick = onManage) { Text("\u1798\u17c9\u17bc\u178a\u17c2\u179b") }
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
            val proposal = item.proposal
            val khmerDigits = LocalAppSettings.current.useKhmerNumerals

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("ព្រឹត្តិការណ៍ដែលស្នើ", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.weight(1f))
                Text(
                    if (proposal.fromModel) "ពីម៉ូដែល" else "ពីការវិភាគអត្ថបទ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                proposal.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(proposalWhen(proposal), style = MaterialTheme.typography.bodyMedium)

            proposal.location?.takeIf { it.isNotBlank() }?.let {
                Text("ទីតាំង៖ $it", style = MaterialTheme.typography.bodySmall)
            }
            if (proposal.participants.isNotEmpty()) {
                Text(
                    "អ្នកចូលរួម៖ ${proposal.participants.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            proposal.description?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (proposal.reminderMinutes.isNotEmpty()) {
                Text(
                    "រំលឹក៖ " + proposal.reminderMinutes.joinToString(", ") { minutes ->
                        if (minutes == 0) {
                            "ពេលចាប់ផ្តើម"
                        } else {
                            "${localeNumber(minutes, khmerDigits)} នាទីមុន"
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            // What the text did not say. Shown rather than quietly filled in, because a gap
            // the user can see gets corrected and a plausible guess gets accepted.
            if (proposal.missing.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "មិនបានបញ្ជាក់៖ " + proposal.missing.joinToString(", ") { it.labelKm },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "អ្នកអាចបំពេញបន្ថែមក្រោយពេលរក្សាទុក",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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

/**
 * When the proposal is for, saying so plainly when the text did not give a time.
 *
 * "ម៉ោងមិនបានបញ្ជាក់" rather than a default hour: the point of the whole extraction path is
 * that a value nobody wrote is never presented as though somebody did.
 */
@Composable
private fun proposalWhen(proposal: EventProposal): String {
    val date = proposal.date ?: return "កាលបរិច្ឆេទមិនបានបញ្ជាក់"
    val day = "${KhmerTerms.dayOfWeek(date.dayOfWeek)} ${localeNumber(date.dayOfMonth)} " +
        "${KhmerTerms.solarMonth(date.monthValue)} ${localeNumber(date.year)}"
    val start = proposal.startTime ?: return "$day · ម៉ោងមិនបានបញ្ជាក់"
    val end = proposal.endTime
    return if (end == null) {
        "$day · ${localeNumber(start.format(TIME))}"
    } else {
        "$day · ${localeNumber(start.format(TIME))} - ${localeNumber(end.format(TIME))}"
    }
}
