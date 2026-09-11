package com.khmercalendar.domain

/** Kinds of thing search can be narrowed to. Dates are not one: a typed date is a place to go. */
enum class SearchKind(val labelKm: String) {
    EVENTS("ព្រឹត្តិការណ៍"),
    TASKS("កិច្ចការ"),
    COUNTDOWNS("រាប់ថយក្រោយ"),
    NOTES("កំណត់ចំណាំ"),
    HOLIDAYS("បុណ្យជាតិ"),
}

enum class SearchStatus(val labelKm: String) {
    OPEN("មិនទាន់រួច"),
    DONE("រួចរាល់"),
}

/**
 * What search results are narrowed to.
 *
 * ## The rules
 *
 * - **Kinds are a union.** Choosing tasks and notes shows both. Nothing chosen shows everything.
 * - **Status, priority and category belong to calendar entries.** Choosing any of them hides
 *   notes, holidays and typed dates, which have none of those properties - showing them anyway
 *   would present a holiday as though it matched "important".
 * - **Status and priority belong to tasks.** An ordinary event is neither done nor urgent, so it
 *   is hidden by either, rather than silently counted as "not done".
 * - A countdown is anything pinned, task or not, so it can match both [SearchKind.COUNTDOWNS] and
 *   its own kind.
 *
 * Search hits are rows, not occurrences. A repeating task's status is the series' own flag,
 * which since per-occurrence completion is only set for series that older versions marked done
 * as a whole - so "done" finds finished one-off tasks, not individual days of a routine.
 */
data class SearchFilter(
    val kinds: Set<SearchKind> = emptySet(),
    val status: SearchStatus? = null,
    val priority: TaskPriority? = null,
    val categoryId: Long? = null,
) {
    val isActive: Boolean
        get() = kinds.isNotEmpty() || status != null || priority != null || categoryId != null

    private val entriesOnly: Boolean
        get() = status != null || priority != null || categoryId != null

    fun apply(results: SearchResults): SearchResults = SearchResults(
        dates = if (kinds.isEmpty() && !entriesOnly) results.dates else emptyList(),
        events = results.events.filter { keeps(it) },
        notes = if (shows(SearchKind.NOTES) && !entriesOnly) results.notes else emptyList(),
        holidays = if (shows(SearchKind.HOLIDAYS) && !entriesOnly) results.holidays else emptyList(),
    )

    fun toggled(kind: SearchKind): SearchFilter =
        copy(kinds = if (kind in kinds) kinds - kind else kinds + kind)

    private fun shows(kind: SearchKind): Boolean = kinds.isEmpty() || kind in kinds

    private fun keeps(hit: SearchResult.Event): Boolean {
        val kindMatches = kinds.isEmpty() ||
            (SearchKind.EVENTS in kinds && !hit.isTask) ||
            (SearchKind.TASKS in kinds && hit.isTask) ||
            (SearchKind.COUNTDOWNS in kinds && hit.isPinned)
        if (!kindMatches) return false

        if (status != null) {
            if (!hit.isTask) return false
            if ((status == SearchStatus.DONE) != hit.isCompleted) return false
        }
        if (priority != null) {
            if (!hit.isTask || TaskPriority.of(hit.priority) != priority) return false
        }
        if (categoryId != null && hit.categoryId != categoryId) return false
        return true
    }
}
