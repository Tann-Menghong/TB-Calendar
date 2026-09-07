package com.khmercalendar.ui.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khmercalendar.ai.AiAssistant
import com.khmercalendar.ai.AiEngine
import com.khmercalendar.ai.AiRuntimeConfig
import com.khmercalendar.ai.AnswerSource
import com.khmercalendar.ai.AssistantReply
import com.khmercalendar.ai.model.ModelCatalog
import com.khmercalendar.ai.model.ModelStore
import com.khmercalendar.ai.tools.AiEventView
import com.khmercalendar.core.nlu.EventProposal
import com.khmercalendar.data.prefs.AppSettings
import com.khmercalendar.data.repo.EventRepository
import com.khmercalendar.domain.EventDraftModel
import com.khmercalendar.notify.ReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

/** One entry in the conversation. */
sealed interface ChatItem {
    val id: Long

    data class User(override val id: Long, val text: String) : ChatItem

    data class Reply(
        override val id: Long,
        val text: String,
        val source: AnswerSource,
    ) : ChatItem

    /** A proposed event. Nothing is written until the user accepts it. */
    data class Proposal(
        override val id: Long,
        val proposal: EventProposal,
        val clashes: List<AiEventView>,
        val source: AnswerSource,
        val savedEventId: Long? = null,
        val dismissed: Boolean = false,
    ) : ChatItem

    data class Working(override val id: Long) : ChatItem
}

data class AssistantState(
    val items: List<ChatItem> = emptyList(),
    val input: String = "",
    val busy: Boolean = false,
    /** True when a model is resident and can answer free-form questions. */
    val modelReady: Boolean = false,
    val modelLoading: Boolean = false,
    val modelName: String? = null,
    val aiEnabled: Boolean = false,
    val modelInstalled: Boolean = false,
    val notice: String? = null,
)

/**
 * Backs the assistant screen.
 *
 * The important property here is that everything except [AiAssistant.ask] is answered by
 * arithmetic over the event table, so the screen is fully functional with the AI switched off
 * and no model downloaded. Loading a model only adds the ability to answer questions phrased
 * in ways the deterministic parser declines.
 */
class AssistantViewModel(
    private val assistant: AiAssistant,
    private val engine: AiEngine,
    private val repository: EventRepository,
    private val scheduler: ReminderScheduler,
    private val settings: StateFlow<AppSettings>,
    private val modelStore: ModelStore,
) : ViewModel() {

    private var nextId = 1L

    private val _state = MutableStateFlow(AssistantState())
    val state: StateFlow<AssistantState> = _state.asStateFlow()

    init {
        refreshModelStatus()
        greet()
    }

    fun setInput(text: String) = _state.update { it.copy(input = text) }

    fun dismissNotice() = _state.update { it.copy(notice = null) }

    fun refreshModelStatus() {
        val prefs = settings.value
        val spec = ModelCatalog.byId(prefs.aiModelId)
        _state.update {
            it.copy(
                aiEnabled = prefs.aiEnabled,
                modelReady = engine.isReady,
                modelName = spec?.displayName,
                modelInstalled = spec != null && modelStore.isInstalled(spec),
            )
        }
    }

    /**
     * Loads the selected model into memory.
     *
     * Kept explicit rather than automatic on screen entry: a load costs hundreds of megabytes
     * and several seconds, and a user who only wants to add an event should never pay for it.
     */
    fun loadModel() {
        val prefs = settings.value
        val spec = ModelCatalog.byId(prefs.aiModelId) ?: return
        if (!modelStore.isInstalled(spec)) {
            _state.update { it.copy(notice = "ម៉ូដែលមិនទាន់ត្រូវបានទាញយកទេ") }
            return
        }
        _state.update { it.copy(modelLoading = true) }
        viewModelScope.launch {
            val result = engine.load(
                modelPath = modelStore.fileFor(spec).absolutePath,
                config = AiRuntimeConfig(
                    maxTokens = prefs.aiMaxTokens,
                    temperature = prefs.aiTemperature,
                ),
            )
            _state.update {
                it.copy(
                    modelLoading = false,
                    modelReady = engine.isReady,
                    notice = result.exceptionOrNull()?.let { e -> "ផ្ទុកម៉ូដែលមិនបាន៖ ${e.message}" },
                )
            }
        }
    }

    fun unloadModel() {
        viewModelScope.launch {
            engine.unload()
            _state.update { it.copy(modelReady = false) }
        }
    }

    fun send() {
        val text = _state.value.input.trim()
        if (text.isEmpty() || _state.value.busy) return
        _state.update {
            it.copy(
                items = it.items + ChatItem.User(nextId++, text),
                input = "",
                busy = true,
            )
        }
        respond(text)
    }

    /**
     * Analyses text shared in from another app.
     *
     * Submitted straight away rather than dropped into the input box: the user already chose
     * this text and chose to send it here, so making them tap send again is one step for no
     * decision. Guarded against re-running when the screen is recreated on rotation.
     */
    fun analyseSharedText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed == lastSharedText) return
        lastSharedText = trimmed
        if (_state.value.busy) return
        _state.update {
            it.copy(
                items = it.items + ChatItem.User(nextId++, trimmed),
                input = "",
                busy = true,
            )
        }
        respond(trimmed)
    }

    private var lastSharedText: String? = null

    fun quickAction(action: QuickAction) {
        if (_state.value.busy) return
        _state.update {
            it.copy(items = it.items + ChatItem.User(nextId++, action.label), busy = true)
        }
        viewModelScope.launch {
            val reply = when (action) {
                QuickAction.SUMMARY -> assistant.summarizeUpcoming(days = 7)
                QuickAction.CONFLICTS -> assistant.findConflicts(days = 30)
                QuickAction.FREE_TODAY -> assistant.suggestFreeSlots(LocalDate.now())
                QuickAction.FREE_TOMORROW -> assistant.suggestFreeSlots(LocalDate.now().plusDays(1))
            }
            emit(reply)
        }
    }

    private fun respond(text: String) {
        viewModelScope.launch {
            val now = LocalDateTime.now()
            // Creating an event is tried first: it is the request people actually type, and
            // the deterministic parser answers it without the model.
            val proposal = assistant.proposeEvent(text, now)
            if (proposal is AssistantReply.Draft) {
                emit(proposal)
                return@launch
            }
            if (!engine.isReady) {
                emit(
                    AssistantReply.Text(
                        text = FALLBACK_HELP,
                        source = AnswerSource.RULES,
                    ),
                )
                return@launch
            }
            val builder = StringBuilder()
            val placeholderId = nextId++
            _state.update { it.copy(items = it.items + ChatItem.Working(placeholderId)) }
            runCatching {
                assistant.ask(text, now).collect { chunk ->
                    builder.append(chunk)
                    val snapshot = builder.toString()
                    _state.update { s ->
                        s.copy(
                            items = s.items.map { item ->
                                if (item.id == placeholderId) {
                                    ChatItem.Reply(placeholderId, snapshot, AnswerSource.LOCAL_MODEL)
                                } else {
                                    item
                                }
                            },
                        )
                    }
                }
            }.onFailure { error ->
                _state.update { s ->
                    s.copy(
                        items = s.items.map { item ->
                            if (item.id == placeholderId) {
                                ChatItem.Reply(
                                    placeholderId,
                                    "មិនអាចឆ្លើយបានទេ៖ ${error.message ?: "កំហុសមិនស្គាល់"}",
                                    AnswerSource.RULES,
                                )
                            } else {
                                item
                            }
                        },
                    )
                }
            }
            _state.update { it.copy(busy = false) }
        }
    }

    private fun emit(reply: AssistantReply) {
        val item = when (reply) {
            is AssistantReply.Draft ->
                ChatItem.Proposal(nextId++, reply.proposal, reply.clashes, reply.source)

            is AssistantReply.Text -> ChatItem.Reply(nextId++, reply.text, reply.source)
            is AssistantReply.Failure -> ChatItem.Reply(nextId++, reply.message, reply.source)
        }
        _state.update { it.copy(items = it.items + item, busy = false) }
    }

    /** Writes a proposed event, after the user has confirmed it. */
    /**
     * Saves a proposal the user has accepted.
     *
     * Where the text said nothing, the user's own defaults fill in rather than anything the
     * extractor guessed: an event with no stated time becomes all-day, and one with no stated
     * end runs for the user's default duration. That keeps "the app never invents" true right
     * through to what is written to the database.
     */
    fun accept(item: ChatItem.Proposal) {
        val proposal = item.proposal
        val date = proposal.date ?: return
        viewModelScope.launch {
            val prefs = settings.value
            val start = proposal.startTime
            val end = proposal.endTime
                ?: start?.plusMinutes(prefs.defaultEventDurationMinutes.toLong())
            val model = EventDraftModel(
                title = proposal.title,
                description = proposal.description.orEmpty(),
                location = proposal.location.orEmpty(),
                date = date,
                startTime = start ?: java.time.LocalTime.of(0, 0),
                endTime = end ?: java.time.LocalTime.of(23, 59),
                endDate = date,
                allDay = proposal.allDay,
                recurrence = proposal.recurrence,
                reminderMinutes = proposal.reminderMinutes.ifEmpty {
                    listOf(prefs.defaultReminderMinutes)
                },
            )
            val id = repository.save(model)
            scheduler.rescheduleAll()
            _state.update { s ->
                s.copy(
                    items = s.items.map {
                        if (it.id == item.id && it is ChatItem.Proposal) {
                            it.copy(savedEventId = id)
                        } else {
                            it
                        }
                    },
                )
            }
        }
    }

    fun dismiss(proposal: ChatItem.Proposal) {
        _state.update { s ->
            s.copy(
                items = s.items.map {
                    if (it.id == proposal.id && it is ChatItem.Proposal) it.copy(dismissed = true) else it
                },
            )
        }
    }

    fun clear() {
        _state.update { it.copy(items = emptyList()) }
        greet()
    }

    private fun greet() {
        _state.update {
            it.copy(
                items = it.items + ChatItem.Reply(nextId++, GREETING, AnswerSource.RULES),
            )
        }
    }

    private companion object {
        const val GREETING =
            "សួស្តី! ខ្ញុំជាជំនួយការក្នុងឧបករណ៍។ ទិន្នន័យទាំងអស់ដំណើរការនៅលើទូរស័ព្ទរបស់អ្នក " +
                "ហើយមិនត្រូវបានផ្ញើទៅណាទេ។\n\n" +
                "សាកល្បងសរសេរ៖ «ថ្ងៃស្អែកម៉ោង ២ រសៀល រំលឹកខ្ញុំឱ្យប្រជុំជាមួយក្រុមការងារ»"

        const val FALLBACK_HELP =
            "ខ្ញុំមិនយល់សំណើនេះទេ។ សូមសាកល្បងសរសេរឱ្យច្បាស់ជាងនេះ ដូចជា " +
                "«ថ្ងៃច័ន្ទម៉ោង ៨ ព្រឹក ប្រជុំការងារ» ឬប្រើប៊ូតុងខាងក្រោម។\n\n" +
                "បើចង់សួរសំណួរជាភាសាធម្មតា សូមបើកម៉ូដែល AI ក្នុងការកំណត់។"
    }
}

/** The buttons under the input box. All four are answered without a model. */
enum class QuickAction(val label: String) {
    SUMMARY("សង្ខេបសប្តាហ៍នេះ"),
    CONFLICTS("រកមើលការជាន់គ្នា"),
    FREE_TODAY("ពេលទំនេរថ្ងៃនេះ"),
    FREE_TOMORROW("ពេលទំនេរថ្ងៃស្អែក"),
}
