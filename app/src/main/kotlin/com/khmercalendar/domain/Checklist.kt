package com.khmercalendar.domain

/** One line of a task's checklist. */
data class ChecklistItem(
    val id: Long,
    val text: String,
    val isDone: Boolean,
    val sortOrder: Int,
)

/**
 * A task's checklist: the steps inside one piece of work.
 *
 * ## Why this is not a `parentId` on events
 *
 * The rule set when habits got their own tables is "is this a date?", and a checklist line is
 * not one. "Book the room" under "Prepare the review" has no time, no reminder and no place
 * on a calendar - and making it an event row would put eight extra lines on the day the task
 * falls on, burying the day it was meant to describe. A checklist belongs to its task and is
 * seen only there.
 *
 * That is also the difference from a subtask in the calendar sense: something that genuinely
 * needs its own date, reminder or recurrence is not a checklist line, it is another task, and
 * this app already has those.
 *
 * ## Why progress is derived
 *
 * The parent task's completion is the user's to set, not something the checklist decides. A
 * task with four of five boxes ticked is *not* complete, and one the user has ticked off is
 * complete whatever the boxes say - people finish work in ways their own checklist did not
 * predict. So [progress] reports and never writes, and nothing here changes the task.
 */
object Checklist {

    /** Beyond this a checklist is a task list wearing a disguise. */
    const val MAX_ITEMS = 50

    /** How many are done out of how many, for the "៣/៥" beside a task. */
    data class Progress(val done: Int, val total: Int) {
        val isEmpty: Boolean get() = total == 0
        val isComplete: Boolean get() = total > 0 && done == total

        /** 0f..1f, and 0f rather than a division by zero when there is nothing to do. */
        val fraction: Float get() = if (total == 0) 0f else done.toFloat() / total
    }

    fun progress(items: List<ChecklistItem>): Progress =
        Progress(done = items.count { it.isDone }, total = items.size)

    /**
     * The order the list is shown in.
     *
     * By explicit position, never by done-ness. A checklist is a sequence - the steps of one
     * piece of work, often in the order they have to happen - so reordering it under the
     * user as they tick would destroy the only information the order carries. This is the
     * opposite decision from the habit list, and for the opposite reason: habits are a set to
     * be cleared, a checklist is a procedure to be followed.
     */
    fun ordered(items: List<ChecklistItem>): List<ChecklistItem> =
        items.sortedWith(compareBy({ it.sortOrder }, { it.id }))

    /** The next thing to do, which is what a collapsed task can show as a hint. */
    fun nextUndone(items: List<ChecklistItem>): ChecklistItem? =
        ordered(items).firstOrNull { !it.isDone }

    /**
     * The position a newly added line should take.
     *
     * One past the current maximum rather than `size`, so that adding after a deletion cannot
     * collide with an existing position and silently reorder two lines.
     */
    fun nextSortOrder(items: List<ChecklistItem>): Int =
        (items.maxOfOrNull { it.sortOrder } ?: -1) + 1

    /**
     * Moves the item at [from] to [to], renumbering the result.
     *
     * Returns the ids in their new order paired with the position each should be given, so
     * the caller writes exactly what changed. An out-of-range move leaves the list alone.
     */
    fun reorder(items: List<ChecklistItem>, from: Int, to: Int): List<Pair<Long, Int>> {
        val list = ordered(items).toMutableList()
        if (from !in list.indices || to !in list.indices || from == to) {
            return list.mapIndexed { index, item -> item.id to index }
        }
        list.add(to, list.removeAt(from))
        return list.mapIndexed { index, item -> item.id to index }
    }

    /** Whether another line can be added; the screen hides the field rather than failing. */
    fun canAdd(items: List<ChecklistItem>): Boolean = items.size < MAX_ITEMS

    /**
     * Cleans a typed line.
     *
     * Blank lines are rejected by the caller rather than stored: an empty checklist row is
     * invisible, unremovable by tapping, and looks like the list has lost an item.
     */
    fun clean(raw: String): String = raw.trim().replace('\n', ' ').take(200)
}
