package com.khmercalendar.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A task's checklist.
 *
 * The ordering rules carry most of these. A checklist is a *procedure* - often the steps in
 * the order they have to happen - so anything that quietly reorders it destroys the only
 * information the order carries, and none of that is visible by looking at a list of ticks.
 */
class ChecklistTest {

    private fun item(id: Long, text: String, done: Boolean = false, order: Int = id.toInt()) =
        ChecklistItem(id = id, text = text, isDone = done, sortOrder = order)

    private val steps = listOf(
        item(1, "កក់បន្ទប់", order = 0),
        item(2, "ផ្ញើរបៀបវារៈ", done = true, order = 1),
        item(3, "រៀបចំឯកសារ", order = 2),
    )

    // --- progress ---

    @Test
    fun `progress counts done against total`() {
        val p = Checklist.progress(steps)
        assertEquals(1, p.done)
        assertEquals(3, p.total)
        assertFalse(p.isComplete)
    }

    @Test
    fun `an empty checklist is empty rather than complete`() {
        val p = Checklist.progress(emptyList())
        assertTrue(p.isEmpty)
        assertFalse("nothing to do is not the same as everything done", p.isComplete)
        assertEquals(0f, p.fraction, 0f)
    }

    @Test
    fun `everything ticked is complete`() {
        val done = steps.map { it.copy(isDone = true) }
        assertTrue(Checklist.progress(done).isComplete)
        assertEquals(1f, Checklist.progress(done).fraction, 0f)
    }

    @Test
    fun `fraction does not divide by zero`() {
        assertEquals(0f, Checklist.progress(emptyList()).fraction, 0f)
    }

    // --- ordering ---

    @Test
    fun `order follows position, not completion`() {
        // The done item sits in the middle and must stay there: the order is the procedure.
        assertEquals(listOf(1L, 2L, 3L), Checklist.ordered(steps).map { it.id })
    }

    @Test
    fun `ordering is stable when two lines share a position`() {
        val clashing = listOf(item(7, "b", order = 0), item(3, "a", order = 0))
        assertEquals(listOf(3L, 7L), Checklist.ordered(clashing).map { it.id })
    }

    @Test
    fun `the next undone item skips the ones already ticked`() {
        val partly = listOf(
            item(1, "one", done = true, order = 0),
            item(2, "two", done = true, order = 1),
            item(3, "three", order = 2),
        )
        assertEquals("three", Checklist.nextUndone(partly)?.text)
    }

    @Test
    fun `a finished checklist has no next item`() {
        assertNull(Checklist.nextUndone(steps.map { it.copy(isDone = true) }))
        assertNull(Checklist.nextUndone(emptyList()))
    }

    // --- adding ---

    @Test
    fun `a new line goes after the last position, not at the size`() {
        // Deleting the middle line leaves positions 0 and 2; a new line must not take 2.
        val withGap = listOf(item(1, "a", order = 0), item(3, "c", order = 2))
        assertEquals(3, Checklist.nextSortOrder(withGap))
    }

    @Test
    fun `the first line takes position zero`() {
        assertEquals(0, Checklist.nextSortOrder(emptyList()))
    }

    @Test
    fun `a checklist stops accepting lines at the limit`() {
        val full = (1..Checklist.MAX_ITEMS).map { item(it.toLong(), "x", order = it) }
        assertFalse(Checklist.canAdd(full))
        assertTrue(Checklist.canAdd(full.drop(1)))
    }

    // --- reordering ---

    @Test
    fun `moving a line down renumbers everything from zero`() {
        val moved = Checklist.reorder(steps, from = 0, to = 2)
        assertEquals(listOf(2L to 0, 3L to 1, 1L to 2), moved)
    }

    @Test
    fun `moving a line up renumbers everything from zero`() {
        val moved = Checklist.reorder(steps, from = 2, to = 0)
        assertEquals(listOf(3L to 0, 1L to 1, 2L to 2), moved)
    }

    @Test
    fun `an out-of-range move leaves the order alone but still renumbers`() {
        // Renumbering regardless is what repairs a list whose positions have drifted apart.
        val same = Checklist.reorder(steps, from = 0, to = 9)
        assertEquals(listOf(1L to 0, 2L to 1, 3L to 2), same)
    }

    @Test
    fun `moving a line onto itself changes nothing`() {
        assertEquals(listOf(1L to 0, 2L to 1, 3L to 2), Checklist.reorder(steps, from = 1, to = 1))
    }

    @Test
    fun `reordering an empty checklist is empty rather than an error`() {
        assertTrue(Checklist.reorder(emptyList(), 0, 1).isEmpty())
    }

    // --- cleaning ---

    @Test
    fun `a typed line is trimmed and flattened`() {
        assertEquals("កក់បន្ទប់", Checklist.clean("  កក់បន្ទប់\n "))
        assertEquals("a b", Checklist.clean("a\nb"))
    }

    @Test
    fun `a very long line is capped rather than stored whole`() {
        assertEquals(200, Checklist.clean("x".repeat(500)).length)
    }
}
