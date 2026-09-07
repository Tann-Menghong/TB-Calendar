package com.khmercalendar.ui.calendar

import com.khmercalendar.core.holiday.Holiday
import com.khmercalendar.core.khmer.KhmerLunarDate
import com.khmercalendar.domain.EventOccurrence
import java.time.LocalDate

/** Everything one cell of the month grid needs, precomputed off the composition. */
data class DayCellState(
    val date: LocalDate,
    val inCurrentMonth: Boolean,
    val isToday: Boolean,
    val lunar: KhmerLunarDate?,
    val holidays: List<Holiday>,
    val events: List<EventOccurrence>,
    val hasNote: Boolean,
) {
    val isPublicHoliday: Boolean
        get() = holidays.any { it.kind == com.khmercalendar.core.holiday.HolidayKind.PUBLIC }
}

data class MonthState(
    val yearMonth: java.time.YearMonth = java.time.YearMonth.now(),
    val weeks: List<List<DayCellState>> = emptyList(),
    val selected: LocalDate = LocalDate.now(),
    val isLoading: Boolean = true,
    /** Set when the month falls outside the range the lunar engine can convert. */
    val outOfRangeMessage: String? = null,
)
