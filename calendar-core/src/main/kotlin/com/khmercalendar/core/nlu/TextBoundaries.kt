package com.khmercalendar.core.nlu

/**
 * Where one part of a Khmer sentence stops and the next begins.
 *
 * ## Why this exists
 *
 * Khmer is written without spaces between words. "កិច្ចប្រជុំប្រចាំសប្តាហ៍នៅម៉ោង" is one
 * whitespace token holding a noun, a preposition and the word for "hour", so every
 * "take the next four words" heuristic silently swallows a whole clause. That single wrong
 * assumption produced two visible bugs: a pasted announcement whose title ran on into
 * "...នៅម៉ោង ២ រសៀល", and a place read out of "នៅថ្ងៃស្អែក" - a date, not a room.
 *
 * So a phrase is bounded by character index instead: it runs from where it starts to the
 * first marker of a *different* kind - a time, a date, a place, a person, a purpose.
 *
 * ## Which words count as boundaries
 *
 * Khmer markers are matched as plain substrings, because there are no word boundaries to
 * anchor to. Latin markers are only used when the lexicon already writes them with
 * surrounding spaces (`" at "`, `" with "`); a bare Latin word like "may" or "at" would
 * otherwise cut an English title in half at "Mayfair" or "Batch".
 */
internal object TextBoundaries {

    /** Conjunctions and prepositions that introduce why or how, not what. */
    val PURPOSE: List<String> = listOf(
        "ដើម្បី", "ស្តីពី", "ស្តីអំពី", "អំពី", "ពិភាក្សាអំពី",
        " to discuss ", " in order to ", " regarding ", " about ",
    )

    /** Everything else that ends a phrase without starting a purpose. */
    private val CLAUSE: List<String> = listOf(
        "ដែល", "ដោយសារ", "ព្រោះ", "បន្ទាប់ពី", "ហើយនិង", "រួចហើយ", "។", "៖", "\n",
        " so that ", " because ",
    )

    /**
     * The full set: anything that means "the phrase you were reading has ended".
     *
     * Built from the lexicon rather than restated, so a marker added there for parsing
     * automatically becomes a boundary here.
     */
    val ALL: List<String> = buildList {
        addAll(PURPOSE)
        addAll(CLAUSE)
        addAll(KhmerLexicon.LOCATION_MARKERS)
        addAll(KhmerLexicon.HOUR_MARKERS)
        addAll(KhmerLexicon.PARTICIPANT_MARKERS)
        addAll(KhmerLexicon.RELATIVE_DAYS.keys)
        addAll(KhmerLexicon.WEEKDAYS.keys)
        addAll(KhmerLexicon.SOLAR_MONTHS.keys)
        addAll(KhmerLexicon.TIME_OF_DAY.keys)
        addAll(KhmerLexicon.REMINDER_WORDS)
    }

    /**
     * Words that, right after a location marker, mean it was not introducing a place.
     *
     * "នៅម៉ោង ២" and "នៅថ្ងៃស្អែក" both start with the same preposition as "នៅបន្ទប់ប្រជុំ A".
     * Only the third is a room.
     */
    val NOT_A_PLACE: List<String> = buildList {
        addAll(KhmerLexicon.HOUR_MARKERS)
        addAll(KhmerLexicon.RELATIVE_DAYS.keys)
        addAll(KhmerLexicon.WEEKDAYS.keys)
        addAll(KhmerLexicon.TIME_OF_DAY.keys)
        add("ថ្ងៃទី")
        add("ថ្ងៃ")
        add("ខែ")
        add("ពេល")
    }

    private val patterns = HashMap<List<String>, Regex>()

    /**
     * The index of the earliest of [needles] at or after [from], or -1.
     *
     * Compiled once per needle set and cached: this runs per sentence of a pasted document,
     * and rebuilding a hundred-branch alternation each time would dominate the extraction.
     */
    fun indexOfAny(text: String, needles: List<String>, from: Int = 0): Int {
        if (from >= text.length) return -1
        val match = patternFor(needles).find(text, from) ?: return -1
        return match.range.first
    }

    /**
     * The end of the phrase that starts at [from]: the next boundary, or the end of the text.
     */
    fun endOfPhrase(text: String, from: Int = 0, needles: List<String> = ALL): Int {
        val at = indexOfAny(text, needles, from)
        return if (at < 0) text.length else at
    }

    /** True when [text] begins with any of [needles], ignoring leading space. */
    fun startsWithAny(text: String, needles: List<String>): Boolean {
        val trimmed = text.trimStart()
        return needles.any { it.isNotBlank() && trimmed.startsWith(it.trim(), ignoreCase = true) }
    }

    private fun patternFor(needles: List<String>): Regex = patterns.getOrPut(needles) {
        val branches = needles
            .filter { it.isNotBlank() }
            .filter { usable(it) }
            .distinct()
            .sortedByDescending { it.length }
            .map { Regex.escape(it.trim()) }
        Regex(branches.joinToString("|"), RegexOption.IGNORE_CASE)
    }

    /** See the class comment: Khmer always, Latin only when the lexicon spaces it out. */
    private fun usable(needle: String): Boolean =
        needle.any { it.code > 127 } || (needle.startsWith(' ') && needle.endsWith(' '))
}
