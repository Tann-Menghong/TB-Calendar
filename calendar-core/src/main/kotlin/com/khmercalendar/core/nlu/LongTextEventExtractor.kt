package com.khmercalendar.core.nlu

import com.khmercalendar.core.khmer.KhmerNumerals
import java.time.Duration
import java.time.LocalDateTime

/**
 * Turns a paragraph into one proposed event.
 *
 * ## Why this exists separately from [NaturalLanguageEventParser]
 *
 * That parser is built for a command - "ថ្ងៃស្អែកម៉ោង ២ រសៀល ប្រជុំជាមួយក្រុមការងារ" - and it
 * builds the title by deleting the date and time from the input and keeping the rest. Given a
 * pasted announcement that is exactly wrong: the title becomes the entire paragraph, greeting
 * and all. A calendar full of hundred-word titles is worse than no extraction.
 *
 * So a paragraph is treated differently. The text is split into sentences, the one actually
 * announcing something is found, the *title* is built from the event noun in that sentence
 * rather than from what is left over, and everything else becomes the description.
 *
 * ## The pipeline
 *
 * ```
 * normalise → segment into sentences → score each for event signal
 *   → pick the anchor sentence → parse date/time/place from it
 *   → build a short title around its event noun
 *   → summarise the remaining sentences into a description
 *   → record what was explicit, what was inferred, what is missing
 * ```
 *
 * It runs entirely on-device in microseconds, needs no model, and cannot hallucinate: every
 * value it returns is either read out of the text or left null.
 */
class LongTextEventExtractor(
    private val parser: NaturalLanguageEventParser = NaturalLanguageEventParser(),
    private val defaultDuration: Duration = Duration.ofHours(1),
) {

    companion object {
        /**
         * Above this, the text is chunked before scoring.
         *
         * Not a memory limit - it is all in one string either way - but a relevance one: in a
         * long document the announcement is usually in one region, and scoring sentence by
         * sentence across thousands of characters lets an unrelated date elsewhere win.
         */
        const val CHUNK_THRESHOLD = 1_200

        /** Hard ceiling. Beyond this the tail is dropped rather than processed. */
        const val MAX_INPUT = 20_000

        /** Words kept in a generated title. Long enough to be specific, short enough to read. */
        private const val TITLE_MAX_WORDS = 8
        private const val TITLE_MAX_CHARS = 60

        /** Sentences kept in a generated description. */
        private const val DESCRIPTION_MAX_SENTENCES = 3
        private const val DESCRIPTION_MAX_CHARS = 400

        private val WS = Regex("\\s+")
        private val SENTENCE_SPLIT = Regex("(?<=[។!?])|\\n+")
    }

    /** A sentence and how strongly it looks like an announcement. */
    private data class Scored(val text: String, val score: Int, val index: Int)

    /**
     * Extracts a proposal, or null when the text announces nothing datable.
     *
     * Returning null is a real answer: text with no date, no time and no event noun is not a
     * calendar entry, and proposing one anyway trains the user to distrust the feature.
     */
    fun extract(rawText: String, now: LocalDateTime): EventProposal? {
        val input = rawText.take(MAX_INPUT)
        val normalised = KhmerNumerals.composeVowels(input).trim()
        if (normalised.isBlank()) return null

        val sentences = segment(normalised)
        if (sentences.isEmpty()) return null

        val scored = sentences
            .mapIndexed { index, text -> Scored(text, scoreOf(text), index) }
            .let { if (normalised.length > CHUNK_THRESHOLD) restrictToBestRegion(it) else it }

        val anchor = scored.maxByOrNull { it.score }
        if (anchor == null || anchor.score == 0) return null

        // The rule parser does the date, time, place and recurrence work, but only on the one
        // sentence that carries the announcement.
        val parsed = parser.parse(anchor.text, now)

        val facts = mutableListOf<ExtractedFact>()
        val missing = mutableListOf<ProposalField>()

        val date = parsed?.start?.toLocalDate()
        val hasClockTime = parsed != null && !parsed.allDay
        val startTime = if (hasClockTime) parsed.start.toLocalTime() else null
        val endTime = if (hasClockTime) parsed.end.toLocalTime() else null

        if (date != null) {
            facts += ExtractedFact(
                field = ProposalField.DATE,
                valueKm = date.toString(),
                // A weekday or "ស្អែក" is stated; the calendar date it lands on is worked out.
                source = if (mentionsAbsoluteDate(anchor.text)) FactSource.EXPLICIT else FactSource.INFERRED,
                evidence = anchor.text.take(80),
            )
        } else {
            missing += ProposalField.DATE
        }

        if (startTime != null) {
            facts += ExtractedFact(ProposalField.START_TIME, startTime.toString(), FactSource.EXPLICIT)
            val stated = parsed!!.end.toLocalTime() != startTime.plusMinutes(defaultDuration.toMinutes())
            facts += ExtractedFact(
                field = ProposalField.END_TIME,
                valueKm = endTime.toString(),
                source = if (stated) FactSource.EXPLICIT else FactSource.INFERRED,
            )
        } else {
            // No time stated. This is the case that matters most: leave it empty.
            missing += ProposalField.START_TIME
        }

        val title = buildTitle(anchor.text, parsed?.title)
        if (title.isBlank()) return null
        facts += ExtractedFact(ProposalField.TITLE, title, FactSource.INFERRED, anchor.text.take(80))

        val location = parsed?.location
        if (location != null) {
            facts += ExtractedFact(ProposalField.LOCATION, location, FactSource.EXPLICIT)
        } else {
            missing += ProposalField.LOCATION
        }

        val participants = extractParticipants(anchor.text)
        if (participants.isNotEmpty()) {
            facts += ExtractedFact(
                ProposalField.PARTICIPANTS,
                participants.joinToString(", "),
                FactSource.EXPLICIT,
            )
        }

        val description = buildDescription(sentences, anchor.index, anchor.text)
        if (description != null) {
            facts += ExtractedFact(ProposalField.DESCRIPTION, description.take(60), FactSource.EXPLICIT)
        }

        parsed?.recurrence?.let {
            facts += ExtractedFact(ProposalField.RECURRENCE, it.toRRule(), FactSource.EXPLICIT)
        }
        if (parsed?.reminderMinutes?.isNotEmpty() == true) {
            facts += ExtractedFact(
                ProposalField.REMINDER,
                parsed.reminderMinutes.joinToString(","),
                FactSource.EXPLICIT,
            )
        } else {
            missing += ProposalField.REMINDER
        }

        return EventProposal(
            title = title,
            date = date,
            startTime = startTime,
            endTime = endTime,
            durationMinutes = if (startTime != null && endTime != null) {
                Duration.between(startTime, endTime).toMinutes().toInt()
            } else {
                null
            },
            location = location,
            description = description,
            participants = participants,
            recurrence = parsed?.recurrence,
            reminderMinutes = parsed?.reminderMinutes.orEmpty(),
            confidence = confidenceOf(date, startTime, location, anchor.score),
            facts = facts,
            missing = missing,
        )
    }

    // --- segmentation -------------------------------------------------------------------

    private fun segment(text: String): List<String> =
        SENTENCE_SPLIT.split(text)
            .map { WS.replace(it, " ").trim() }
            .filter { it.isNotBlank() }

    /**
     * Narrows a long document to the region around its strongest sentence.
     *
     * A meeting announcement buried in a long email should not have its date taken from an
     * unrelated line three paragraphs away, so scoring is confined to the best sentence and
     * its immediate neighbours.
     */
    private fun restrictToBestRegion(scored: List<Scored>): List<Scored> {
        val best = scored.maxByOrNull { it.score } ?: return scored
        val from = (best.index - 1).coerceAtLeast(0)
        val to = (best.index + 2).coerceAtMost(scored.size)
        return scored.subList(from, to)
    }

    /**
     * How strongly a sentence looks like it is announcing something.
     *
     * Weighted so that a sentence with both a date and an event noun always beats one with
     * either alone - which is what separates "នៅថ្ងៃស្អែកយើងនឹងមានកិច្ចប្រជុំ" from a passing
     * mention of a deadline elsewhere in the same message.
     */
    private fun scoreOf(sentence: String): Int {
        val ascii = KhmerNumerals.toAscii(sentence)
        val lower = ascii.lowercase()
        var score = 0

        if (KhmerLexicon.RELATIVE_DAYS.keys.any { lower.contains(it) }) score += 3
        if (KhmerLexicon.WEEKDAYS.keys.any { lower.contains(it) }) score += 3
        if (KhmerLexicon.SOLAR_MONTHS.keys.any { lower.contains(it) }) score += 3
        if (Regex("\\d{1,2}\\s*[/-]\\s*\\d{1,2}").containsMatchIn(ascii)) score += 3

        if (KhmerLexicon.HOUR_MARKERS.any { lower.contains(it) } &&
            Regex("\\d").containsMatchIn(ascii)
        ) {
            score += 3
        }
        if (KhmerLexicon.TIME_OF_DAY.keys.any { lower.contains(it) }) score += 1

        if (KhmerLexicon.EVENT_NOUNS.any { lower.contains(it.lowercase()) }) score += 4
        if (KhmerLexicon.REMINDER_WORDS.any { lower.contains(it) }) score += 2
        if (KhmerLexicon.LOCATION_MARKERS.any { lower.contains(it) }) score += 1

        return score
    }

    /** True when the sentence names a calendar date rather than a relative day. */
    private fun mentionsAbsoluteDate(sentence: String): Boolean {
        val ascii = KhmerNumerals.toAscii(sentence).lowercase()
        return KhmerLexicon.SOLAR_MONTHS.keys.any { ascii.contains(it) } ||
            Regex("\\d{1,2}\\s*[/-]\\s*\\d{1,2}").containsMatchIn(ascii)
    }

    // --- title --------------------------------------------------------------------------

    /**
     * A short name for the event.
     *
     * Preference order: the phrase around an event noun, then the rule parser's own title if
     * it is already short, then a trimmed opening. Whatever the source, it is capped - the
     * failure this class exists to prevent is a title that is really a paragraph.
     */
    private fun buildTitle(sentence: String, parserTitle: String?): String {
        aroundEventNoun(sentence)?.let { return it }

        val fromParser = parserTitle?.let { clean(it) }.orEmpty()
        if (fromParser.isNotBlank() && wordCount(fromParser) <= TITLE_MAX_WORDS) return fromParser

        val trimmed = clean(sentence)
        return capWords(trimmed)
    }

    /**
     * The noun phrase around the first event noun, with its modifiers.
     *
     * "នៅថ្ងៃស្អែកយើងនឹងមានកិច្ចប្រជុំប្រចាំសប្តាហ៍នៅម៉ោង ២ រសៀល" yields
     * "កិច្ចប្រជុំប្រចាំសប្តាហ៍" rather than the whole clause.
     */
    private fun aroundEventNoun(sentence: String): String? {
        val cleaned = clean(sentence)
        val lower = cleaned.lowercase()
        // Longest first, so "កិច្ចប្រជុំប្រចាំសប្តាហ៍" wins over "កិច្ចប្រជុំ".
        val noun = KhmerLexicon.EVENT_NOUNS
            .sortedByDescending { it.length }
            .firstOrNull { lower.contains(it.lowercase()) }
            ?: return null

        val at = lower.indexOf(noun.lowercase())
        val afterNoun = at + noun.length

        // Stop at whatever introduces a different part of the sentence - a place, a time, a
        // purpose - so the title stays the name of the thing. By character index, not by
        // words: "កិច្ចប្រជុំប្រចាំសប្តាហ៍នៅម៉ោង" is a single whitespace token, and taking it
        // whole is how the title used to run on into the meeting time.
        var end = afterNoun + TextBoundaries.endOfPhrase(cleaned.substring(afterNoun))

        // People are part of what the event is called - "ប្រជុំជាមួយក្រុមការងារ" reads better
        // in a calendar than a bare "ប្រជុំ" - so a participant phrase directly after the noun
        // is kept, and the cut is made at whatever follows it instead.
        if (end == afterNoun) {
            val rest = cleaned.substring(afterNoun)
            val marker = KhmerLexicon.PARTICIPANT_MARKERS
                .firstOrNull { rest.startsWith(it.trim(), ignoreCase = true) }
            if (marker != null) {
                val from = afterNoun + marker.trim().length
                end = from + TextBoundaries.endOfPhrase(cleaned.substring(from))
            }
        }

        val title = cleaned.substring(at, end.coerceAtMost(cleaned.length))
            .trim(' ', ',', '.', '។', ':', '-')
        return title.takeIf { it.isNotBlank() }?.let { capWords(it) }
    }

    private fun clean(raw: String): String {
        var s = raw
        for (p in KhmerLexicon.PLEASANTRIES) {
            s = s.replace(p, " ", ignoreCase = true)
        }
        return WS.replace(s, " ").trim(' ', ',', '.', '-', ':', '។')
    }

    private fun capWords(text: String): String {
        val words = WS.split(text).filter { it.isNotBlank() }
        val kept = words.take(TITLE_MAX_WORDS).joinToString(" ")
        return kept.take(TITLE_MAX_CHARS).trim(' ', ',', '.', '។')
    }

    private fun wordCount(text: String): Int = WS.split(text).count { it.isNotBlank() }

    // --- description and participants ----------------------------------------------------

    /**
     * The rest of the message, kept as context.
     *
     * The anchor sentence is left out because it is already represented by the title, date and
     * time; repeating it would make the description restate the event. Everything else is kept
     * verbatim rather than paraphrased - the extractor has no business rewording what the user
     * was sent.
     *
     * The one part of the anchor that is kept is its purpose clause - the "ដើម្បី..." saying
     * what the meeting is for. That is the single most useful line in an announcement and it
     * is deliberately not in the title, which has to stay a name.
     */
    private fun buildDescription(
        sentences: List<String>,
        anchorIndex: Int,
        anchor: String,
    ): String? {
        val rest = sentences
            .filterIndexed { index, _ -> index != anchorIndex }
            .map { it.trim() }
            .filter { it.length > 8 }
            .filterNot { line ->
                val lower = line.lowercase()
                KhmerLexicon.PLEASANTRIES.any { lower.trim().startsWith(it.lowercase()) } &&
                    line.length < 24
            }
        val parts = listOfNotNull(purposeClause(anchor)) + rest.take(DESCRIPTION_MAX_SENTENCES)
        if (parts.isEmpty()) return null
        return parts.joinToString(" ")
            .take(DESCRIPTION_MAX_CHARS)
            .trim()
            .ifBlank { null }
    }

    /** The "ដើម្បី..." tail of the announcing sentence, when it has one. */
    private fun purposeClause(anchor: String): String? {
        val at = TextBoundaries.indexOfAny(anchor, TextBoundaries.PURPOSE)
        if (at < 0) return null
        return anchor.substring(at).trim(' ', ',', '.', '។', ':')
            .takeIf { it.length in 8..DESCRIPTION_MAX_CHARS }
    }

    /**
     * People named after "ជាមួយ" / "with".
     *
     * Only what is explicitly marked. Guessing at names from a paragraph would put the wrong
     * people on an invitation, and there is no way for the user to notice a name that was
     * quietly left off.
     */
    private fun extractParticipants(sentence: String): List<String> {
        for (marker in KhmerLexicon.PARTICIPANT_MARKERS) {
            val idx = sentence.indexOf(marker, ignoreCase = true)
            if (idx < 0) continue
            val after = sentence.substring(idx + marker.length).trimStart()
            val phrase = after.substring(0, TextBoundaries.endOfPhrase(after))
                .trim(' ', ',', '.', '។', ':')
            if (phrase.length in 2..48) return listOf(phrase)
        }
        return emptyList()
    }

    private fun confidenceOf(
        date: java.time.LocalDate?,
        startTime: java.time.LocalTime?,
        location: String?,
        anchorScore: Int,
    ): Float {
        var c = 0f
        if (date != null) c += 0.40f
        if (startTime != null) c += 0.30f
        if (location != null) c += 0.10f
        c += (anchorScore.coerceAtMost(10) / 10f) * 0.20f
        return c.coerceIn(0f, 1f)
    }
}
