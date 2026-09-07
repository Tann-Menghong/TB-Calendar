package com.khmercalendar.core.nlu

import com.khmercalendar.core.recurrence.RecurrenceRule
import java.time.LocalDate
import java.time.LocalTime

/**
 * Where a piece of a proposal came from.
 *
 * The distinction is the point of the whole type. A user reviewing a proposal needs to know
 * which parts the text actually said and which the app worked out, because those carry
 * different risk: a misread date that was written down is a typo, a misread date that was
 * inferred is a guess.
 */
enum class FactSource {
    /** Stated in the text: "ម៉ោង ២ រសៀល", "នៅបន្ទប់ប្រជុំ A". */
    EXPLICIT,

    /** Derived from something explicit: a default duration, tomorrow's date from "ស្អែក". */
    INFERRED,
}

/** One thing the extractor believes, and why. */
data class ExtractedFact(
    val field: ProposalField,
    val valueKm: String,
    val source: FactSource,
    /** The words that produced it, for showing the user where it came from. */
    val evidence: String? = null,
)

/** The parts of a proposal, so "what is missing" is a typed list rather than free text. */
enum class ProposalField(val labelKm: String) {
    TITLE("ចំណងជើង"),
    DATE("កាលបរិច្ឆេទ"),
    START_TIME("ម៉ោងចាប់ផ្តើម"),
    END_TIME("ម៉ោងបញ្ចប់"),
    LOCATION("ទីតាំង"),
    DESCRIPTION("ការពិពណ៌នា"),
    PARTICIPANTS("អ្នកចូលរួម"),
    RECURRENCE("ធ្វើម្តងទៀត"),
    REMINDER("ការរំលឹក"),
}

/**
 * A calendar event proposed from text, for the user to confirm.
 *
 * Every field that the text did not supply is null and named in [missing]. Nothing is
 * invented: an event with no stated time has a null [startTime] and "ម៉ោងចាប់ផ្តើម" in
 * [missing], rather than a plausible-looking 9am the user never asked for. A guessed time is
 * worse than an obviously incomplete proposal, because the incomplete one gets corrected and
 * the guess gets accepted.
 *
 * @property confidence 0..1, how much of the proposal rests on explicit statements.
 */
data class EventProposal(
    val title: String,
    val date: LocalDate? = null,
    val startTime: LocalTime? = null,
    val endTime: LocalTime? = null,
    val durationMinutes: Int? = null,
    val location: String? = null,
    val description: String? = null,
    val participants: List<String> = emptyList(),
    val recurrence: RecurrenceRule? = null,
    val reminderMinutes: List<Int> = emptyList(),
    val confidence: Float = 0f,
    val facts: List<ExtractedFact> = emptyList(),
    val missing: List<ProposalField> = emptyList(),
    /** True when the proposal came from a language model rather than the rule parser. */
    val fromModel: Boolean = false,
) {
    /** An event with a date but no clock time is an all-day entry. */
    val allDay: Boolean get() = startTime == null

    /** Enough to save: something to call it and a day to put it on. */
    val isActionable: Boolean get() = title.isNotBlank() && date != null

    fun factFor(field: ProposalField): ExtractedFact? = facts.firstOrNull { it.field == field }

    fun isExplicit(field: ProposalField): Boolean =
        factFor(field)?.source == FactSource.EXPLICIT
}
