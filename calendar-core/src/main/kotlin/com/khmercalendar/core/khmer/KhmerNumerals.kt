package com.khmercalendar.core.khmer

/**
 * Conversion between ASCII and Khmer digits, plus the invisible-character cleanup that any
 * Khmer text arriving from a keyboard, a paste buffer or OCR needs before it can be parsed.
 */
object KhmerNumerals {

    private const val KHMER_ZERO = '០'

    /** Zero-width space, ZWNJ, ZWJ and BOM all appear routinely in typed Khmer. */
    private val INVISIBLES = Regex("[​‌‍﻿]")

    fun isKhmerDigit(c: Char): Boolean = c in '០'..'៩'

    /** Rewrites Khmer digits to ASCII and strips zero-width characters. */
    fun toAscii(input: String?): String {
        if (input.isNullOrEmpty()) return ""
        val cleaned = INVISIBLES.replace(input, "")
        if (cleaned.none { isKhmerDigit(it) }) return cleaned
        val sb = StringBuilder(cleaned.length)
        for (c in cleaned) {
            sb.append(if (isKhmerDigit(c)) '0' + (c - KHMER_ZERO) else c)
        }
        return sb.toString()
    }

    /** Rewrites ASCII digits to Khmer digits, leaving everything else untouched. */
    fun toKhmer(input: String): String {
        if (input.none { it in '0'..'9' }) return input
        val sb = StringBuilder(input.length)
        for (c in input) {
            sb.append(if (c in '0'..'9') KHMER_ZERO + (c - '0') else c)
        }
        return sb.toString()
    }

    fun toKhmer(value: Int): String = toKhmer(value.toString())

    fun containsKhmerDigits(input: String?): Boolean = input != null && input.any { isKhmerDigit(it) }

    /**
     * Rewrites decomposed Khmer vowels to their composed forms.
     *
     * U+17BE (ើ) can be typed as one character or as U+17C1 followed by U+17B8, and U+17C4
     * (ោ) as one character or U+17C1 followed by U+17B6. Khmer keyboards produce both and
     * both turn up in real documents, so matching only the composed form silently fails on
     * perfectly ordinary text.
     */
    fun composeVowels(text: String): String = text
        .replace("េី", "ើ")
        .replace("េា", "ោ")

    /** Everything a Khmer string needs before it is matched against a lexicon. */
    fun normalizeForMatching(text: String): String =
        composeVowels(INVISIBLES.replace(text, "")).trim()
}
