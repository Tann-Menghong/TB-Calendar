package com.khmercalendar.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The dock's four buttons.
 *
 * The interesting cases are all about the limit: what a fifth toggle does, and what a stored
 * list written by a hand-edited preference file or an older version turns into.
 */
class DockLayoutTest {

    @Test
    fun `the default is four buttons`() {
        assertEquals(DockLayout.MAX, DockLayout.DEFAULT.size)
        assertEquals(DockLayout.DEFAULT.size, DockLayout.DEFAULT.distinct().size)
    }

    @Test
    fun `turning one off leaves the rest alone`() {
        val next = DockLayout.toggle(DockLayout.DEFAULT, DockSlot.NOTE)

        assertEquals(listOf(DockSlot.ADD_EVENT, DockSlot.ADD_TASK, DockSlot.COUNTDOWN), next)
    }

    @Test
    fun `turning one on when there is room appends it`() {
        val three = listOf(DockSlot.ADD_EVENT, DockSlot.ADD_TASK, DockSlot.NOTE)

        assertEquals(three + DockSlot.SEARCH, DockLayout.toggle(three, DockSlot.SEARCH))
    }

    @Test
    fun `a fifth replaces the oldest rather than doing nothing`() {
        // A toggle that silently refuses is the worst option: the user is left tapping a
        // switch that will not move, with nothing saying why.
        val next = DockLayout.toggle(DockLayout.DEFAULT, DockSlot.SEARCH)

        assertEquals(DockLayout.MAX, next.size)
        assertTrue(DockSlot.SEARCH in next)
        assertFalse(DockLayout.DEFAULT.first() in next)
        assertEquals(DockLayout.DEFAULT.drop(1) + DockSlot.SEARCH, next)
    }

    @Test
    fun `the editor can say in advance that something will be replaced`() {
        assertTrue(DockLayout.wouldReplace(DockLayout.DEFAULT, DockSlot.SEARCH))
        assertFalse(DockLayout.wouldReplace(DockLayout.DEFAULT, DockSlot.NOTE))
        assertFalse(DockLayout.wouldReplace(DockLayout.DEFAULT.take(2), DockSlot.SEARCH))
    }

    @Test
    fun `emptying the dock falls back to the default rather than leaving a blank strip`() {
        val one = listOf(DockSlot.NOTE)

        assertEquals(DockLayout.DEFAULT, DockLayout.toggle(one, DockSlot.NOTE))
    }

    // --- decoding ------------------------------------------------------------------------

    @Test
    fun `a stored list decodes in its own order`() {
        assertEquals(
            listOf(DockSlot.SEARCH, DockSlot.NOTE),
            DockLayout.decode(listOf("search", "note")),
        )
    }

    @Test
    fun `unknown keys are dropped rather than crashing`() {
        assertEquals(
            listOf(DockSlot.NOTE),
            DockLayout.decode(listOf("note", "a_slot_from_a_newer_version")),
        )
    }

    @Test
    fun `duplicates collapse and anything past the limit is trimmed`() {
        val decoded = DockLayout.decode(
            listOf("note", "note", "search", "calendar", "tasks", "assistant"),
        )

        assertEquals(DockLayout.MAX, decoded.size)
        assertEquals(decoded.distinct(), decoded)
    }

    @Test
    fun `nothing stored means the default, not an empty dock`() {
        assertEquals(DockLayout.DEFAULT, DockLayout.decode(emptyList()))
        assertEquals(DockLayout.DEFAULT, DockLayout.decode(listOf("")))
        assertEquals(DockLayout.DEFAULT, DockLayout.decode(listOf("nonsense")))
    }

    @Test
    fun `every slot has a key of its own`() {
        val keys = DockSlot.entries.map { it.key }

        assertEquals(keys.size, keys.distinct().size)
        assertTrue(keys.none { it.isBlank() })
    }
}
