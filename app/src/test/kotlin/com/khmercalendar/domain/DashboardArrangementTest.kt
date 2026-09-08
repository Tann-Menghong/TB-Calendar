package com.khmercalendar.domain

import com.khmercalendar.data.prefs.DashboardCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reordering the dashboard, without a device.
 *
 * The drag gesture reports indices against a list being rewritten underneath it, so the cases
 * that matter are the ones that are awkward to produce by hand: a move to the same place, a
 * move past the end, and a long move that has to leave everything it passes in order.
 */
class DashboardArrangementTest {

    private val order = DashboardCard.entries.toList()

    @Test
    fun `a move inserts rather than swaps`() {
        // Dragging the first card to third place must leave the two it passed in their order.
        val moved = DashboardArrangement.move(order, 0, 2)

        assertEquals(listOf(order[1], order[2], order[0]), moved.take(3))
        assertEquals(order.size, moved.size)
        assertEquals(order.toSet(), moved.toSet())
    }

    @Test
    fun `a move backwards works the same way`() {
        val moved = DashboardArrangement.move(order, 3, 1)

        assertEquals(listOf(order[0], order[3], order[1], order[2]), moved.take(4))
    }

    @Test
    fun `a move to the same index changes nothing`() {
        assertEquals(order, DashboardArrangement.move(order, 2, 2))
    }

    @Test
    fun `an out-of-range index leaves the list alone`() {
        // A drag past the end of the list is a normal event, not a bug: the finger keeps
        // travelling after the last row. It must not throw and must not drop a card.
        assertEquals(order, DashboardArrangement.move(order, 0, order.size))
        assertEquals(order, DashboardArrangement.move(order, -1, 0))
        assertEquals(emptyList<DashboardCard>(), DashboardArrangement.move(emptyList(), 0, 0))
    }

    @Test
    fun `stepping one at a time down the list arrives where a single long move would`() {
        var stepped = order
        for (i in 0 until 4) stepped = DashboardArrangement.move(stepped, i, i + 1)

        assertEquals(DashboardArrangement.move(order, 0, 4), stepped)
    }

    // --- presets ------------------------------------------------------------------------

    @Test
    fun `a preset hides what it does not name rather than dropping it`() {
        val arrangement = DashboardArrangement.apply(DashboardPreset.ESSENTIAL)

        assertEquals(DashboardPreset.ESSENTIAL.cards, arrangement.order.take(4))
        // Every card still exists somewhere in the order; nothing becomes unrecoverable.
        assertEquals(DashboardCard.entries.toSet(), arrangement.order.toSet())
        assertTrue(DashboardCard.NOTE.key in arrangement.hidden)
    }

    @Test
    fun `every preset names only cards that exist and shows at least four`() {
        DashboardPreset.entries.forEach { preset ->
            assertEquals(preset.cards.size, preset.cards.distinct().size)
            assertTrue(preset.key, preset.cards.size >= 4)
            assertTrue(preset.key, DashboardCard.QUICK_ACTIONS !in preset.cards)
        }
    }

    @Test
    fun `applying a preset makes it the recognised one`() {
        DashboardPreset.entries.forEach { preset ->
            val arrangement = DashboardArrangement.apply(preset)
            assertEquals(
                preset,
                DashboardArrangement.presetOf(arrangement.order, arrangement.hidden),
            )
        }
    }

    @Test
    fun `a layout the user has since altered is no longer a preset`() {
        val applied = DashboardArrangement.apply(DashboardPreset.ESSENTIAL)
        val altered = DashboardArrangement.move(applied.order, 0, 1)

        assertNull(DashboardArrangement.presetOf(altered, applied.hidden))
    }

    @Test
    fun `a reset shows every arrangeable module in the declared order`() {
        val default = DashboardArrangement.default()

        assertEquals(
            DashboardCard.entries.filterNot { it == DashboardCard.QUICK_ACTIONS },
            default.order.dropLast(1),
        )
        // The retired module is hidden rather than absent: a saved order that names it must
        // still decode, and it draws nothing either way.
        assertEquals(setOf(DashboardCard.QUICK_ACTIONS.key), default.hidden)
    }

    @Test
    fun `a reset is recognised as the everything preset, not as a custom layout`() {
        val default = DashboardArrangement.default()

        assertEquals(DashboardArrangement.apply(DashboardPreset.EVERYTHING), default)
        assertEquals(
            DashboardPreset.EVERYTHING,
            DashboardArrangement.presetOf(default.order, default.hidden),
        )
        assertNotNull(DashboardArrangement.presetOf(default.order, default.hidden))
    }

    @Test
    fun `a factory layout still reads as the everything preset`() {
        // A fresh install has nothing hidden at all, including the retired module. The
        // preset row must not tell that user their dashboard is already customised.
        assertEquals(
            DashboardPreset.EVERYTHING,
            DashboardArrangement.presetOf(DashboardCard.entries.toList(), emptySet()),
        )
    }

    @Test
    fun `the retired module is never offered as something to arrange`() {
        assertTrue(
            DashboardCard.QUICK_ACTIONS !in
                DashboardArrangement.arrangeable(DashboardCard.entries.toList()),
        )
        assertEquals(
            DashboardCard.entries.size - 1,
            DashboardArrangement.arrangeable(DashboardCard.entries.toList()).size,
        )
    }
}
