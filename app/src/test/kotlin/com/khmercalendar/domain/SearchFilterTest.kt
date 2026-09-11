package com.khmercalendar.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Narrowing search results. */
class SearchFilterTest {

    private val day = LocalDate.of(2026, 9, 11)

    private fun hit(
        id: Long,
        isTask: Boolean = false,
        done: Boolean = false,
        priority: TaskPriority = TaskPriority.NORMAL,
        category: Long? = null,
        pinned: Boolean = false,
    ) = SearchResult.Event(
        eventId = id,
        title = "hit $id",
        subtitle = "",
        date = day,
        colorArgb = 0,
        isTask = isTask,
        isCompleted = done,
        categoryId = category,
        priority = priority.stored,
        isPinned = pinned,
    )

    private val meeting = hit(1, category = 10)
    private val openTask = hit(2, isTask = true, priority = TaskPriority.HIGH, category = 10)
    private val doneTask = hit(3, isTask = true, done = true, category = 20)
    private val pinnedBirthday = hit(4, pinned = true, category = 20)

    private val everything = SearchResults(
        dates = listOf(SearchResult.DateJump(day, yearAssumed = false)),
        events = listOf(meeting, openTask, doneTask, pinnedBirthday),
        notes = listOf(SearchResult.Note(day, "note")),
        holidays = listOf(SearchResult.Holiday(day, "ភ្ជុំបិណ្ឌ")),
    )

    private fun ids(results: SearchResults) = results.events.map { it.eventId }

    @Test
    fun `no filter changes nothing`() {
        val filter = SearchFilter()
        assertFalse(filter.isActive)
        assertEquals(everything, filter.apply(everything))
    }

    @Test
    fun `choosing tasks shows only tasks`() {
        val shown = SearchFilter(kinds = setOf(SearchKind.TASKS)).apply(everything)

        assertEquals(listOf(2L, 3L), ids(shown))
        assertTrue(shown.notes.isEmpty())
        assertTrue(shown.holidays.isEmpty())
        assertTrue("a typed date is not a kind to narrow to", shown.dates.isEmpty())
    }

    @Test
    fun `kinds combine rather than intersect`() {
        val shown = SearchFilter(kinds = setOf(SearchKind.EVENTS, SearchKind.NOTES)).apply(everything)

        assertEquals(listOf(1L, 4L), ids(shown))
        assertEquals(1, shown.notes.size)
        assertTrue(shown.holidays.isEmpty())
    }

    @Test
    fun `a countdown is anything pinned`() {
        val shown = SearchFilter(kinds = setOf(SearchKind.COUNTDOWNS)).apply(everything)

        assertEquals(listOf(4L), ids(shown))
    }

    @Test
    fun `status belongs to tasks, so it hides events, notes and holidays`() {
        val done = SearchFilter(status = SearchStatus.DONE).apply(everything)
        val open = SearchFilter(status = SearchStatus.OPEN).apply(everything)

        assertEquals(listOf(3L), ids(done))
        assertEquals(listOf(2L), ids(open))
        assertTrue(done.notes.isEmpty() && done.holidays.isEmpty() && done.dates.isEmpty())
    }

    @Test
    fun `priority matches tasks of that priority only`() {
        assertEquals(listOf(2L), ids(SearchFilter(priority = TaskPriority.HIGH).apply(everything)))
        assertEquals(listOf(3L), ids(SearchFilter(priority = TaskPriority.NORMAL).apply(everything)))
    }

    @Test
    fun `a category keeps its entries and hides what has no category`() {
        val shown = SearchFilter(categoryId = 20).apply(everything)

        assertEquals(listOf(3L, 4L), ids(shown))
        assertTrue(shown.notes.isEmpty())
        assertTrue(shown.holidays.isEmpty())
    }

    @Test
    fun `filters stack`() {
        val shown = SearchFilter(
            kinds = setOf(SearchKind.TASKS),
            status = SearchStatus.OPEN,
            categoryId = 10,
        ).apply(everything)

        assertEquals(listOf(2L), ids(shown))
    }

    @Test
    fun `toggling a kind twice returns to where it started`() {
        val start = SearchFilter()
        assertEquals(start, start.toggled(SearchKind.NOTES).toggled(SearchKind.NOTES))
    }
}
