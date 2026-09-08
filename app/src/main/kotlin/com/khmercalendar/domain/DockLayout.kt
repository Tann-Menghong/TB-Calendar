package com.khmercalendar.domain

/**
 * One button on the dock at the foot of the dashboard.
 *
 * The dock was four fixed actions chosen by whoever wrote it. These are the things people
 * actually start from, and which four appear is now the user's decision - which is the point
 * of a dock, and was the one part of the dashboard that had no setting at all.
 */
enum class DockSlot(val key: String, val labelKm: String) {
    ADD_EVENT("add_event", "ព្រឹត្តិការណ៍"),
    ADD_TASK("add_task", "កិច្ចការ"),
    NOTE("note", "កំណត់ចំណាំ"),
    COUNTDOWN("countdown", "រាប់ថយក្រោយ"),
    TASKS("tasks", "បញ្ជីកិច្ចការ"),
    CALENDAR("calendar", "ប្រតិទិន"),
    SEARCH("search", "ស្វែងរក"),
    ASSISTANT("assistant", "ជំនួយការ"),
}

/**
 * Which buttons the dock carries.
 *
 * ## Why there is a maximum
 *
 * Four. Five labels across a phone at 1.3x font scale is either two lines of text or an
 * ellipsis, and a button whose label reads "រាប់ថយ..." is a button nobody presses. The limit
 * is enforced here rather than by the editor screen refusing to draw a fifth switch, so it
 * holds for a preference file edited by hand or written by an older version too.
 */
object DockLayout {

    const val MAX = 4

    val DEFAULT: List<DockSlot> = listOf(
        DockSlot.ADD_EVENT,
        DockSlot.ADD_TASK,
        DockSlot.NOTE,
        DockSlot.COUNTDOWN,
    )

    /**
     * Cleans up a stored list.
     *
     * Unknown keys are dropped, duplicates collapse, and anything past [MAX] is trimmed. An
     * empty result falls back to [DEFAULT] rather than an empty dock: a dock with nothing in
     * it is indistinguishable from a bug, and the user can hide the whole thing instead.
     */
    fun decode(keys: List<String>): List<DockSlot> {
        val slots = keys
            .mapNotNull { key -> DockSlot.entries.firstOrNull { it.key == key } }
            .distinct()
            .take(MAX)
        return slots.ifEmpty { DEFAULT }
    }

    /**
     * Turns [slot] on or off, keeping the result within [MAX].
     *
     * Adding a fifth replaces the oldest rather than being refused. A toggle that silently
     * does nothing is the worst of the three options - worse than replacing, and worse than
     * saying no - because the user is left tapping a switch that will not move.
     */
    fun toggle(current: List<DockSlot>, slot: DockSlot): List<DockSlot> = when {
        slot in current -> (current - slot).ifEmpty { DEFAULT }
        current.size < MAX -> current + slot
        else -> current.drop(1) + slot
    }

    /** True when turning [slot] on would push another one off the dock. */
    fun wouldReplace(current: List<DockSlot>, slot: DockSlot): Boolean =
        slot !in current && current.size >= MAX
}
