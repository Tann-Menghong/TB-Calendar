package com.khmercalendar.ui.countdown

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khmercalendar.data.repo.EventRepository
import com.khmercalendar.domain.CountdownItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class CountdownListState(
    val upcoming: List<CountdownItem> = emptyList(),
    /**
     * Pinned dates that have already arrived.
     *
     * Kept and shown rather than dropped. A countdown that has passed is not rubbish - it is
     * the exam you sat, still pinned - and the only honest options are to show it or to unpin
     * it for the user. Unpinning somebody's data on their behalf is not one this app takes.
     */
    val passed: List<CountdownItem> = emptyList(),
)

/**
 * The countdown list.
 *
 * The repository resolves a pinned row to its *next* occurrence, so a yearly birthday is
 * always upcoming and only a one-off can end up in [CountdownListState.passed] - which is
 * exactly the behaviour you want from both.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CountdownViewModel(
    private val repository: EventRepository,
) : ViewModel() {

    /**
     * Today, re-read when the screen is resubscribed.
     *
     * Not `LocalDate.now()` inline: the flow below is built once and would then hold whatever
     * day it was when the screen first opened - the same class of bug as reading the clock in
     * composition. A day boundary crossed with the app open is rare; a countdown that is a
     * day out is exactly the failure this feature cannot have.
     */
    private val today = MutableStateFlow(LocalDate.now())

    val state: StateFlow<CountdownListState> = today
        .flatMapLatest { day ->
            combine(
                repository.observeCountdowns(day),
                repository.observePinnedPast(day),
            ) { upcoming, passed ->
                CountdownListState(upcoming = upcoming, passed = passed)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CountdownListState())

    /** Called when the screen resumes, so a countdown left open overnight is still right. */
    fun refreshToday() {
        today.value = LocalDate.now()
    }

    fun unpin(eventId: Long) {
        if (eventId <= 0L) return
        viewModelScope.launch { repository.setPinned(eventId, false) }
    }
}
