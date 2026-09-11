package com.khmercalendar.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.khmercalendar.AppContainer
import com.khmercalendar.ui.agenda.AgendaViewModel
import com.khmercalendar.ui.assistant.AssistantViewModel
import com.khmercalendar.ui.calendar.CalendarViewModel
import com.khmercalendar.ui.event.EventEditViewModel
import com.khmercalendar.ui.home.HomeViewModel
import com.khmercalendar.ui.settings.AiModelViewModel
import com.khmercalendar.ui.settings.CategoriesViewModel
import com.khmercalendar.ui.settings.UpdateViewModel
import com.khmercalendar.ui.countdown.CountdownViewModel
import com.khmercalendar.ui.focus.FocusViewModel
import com.khmercalendar.ui.habit.HabitViewModel
import com.khmercalendar.ui.tasks.TaskListViewModel

/**
 * One factory for every ViewModel in the app.
 *
 * `viewModelFactory { initializer { ... } }` keeps the wiring explicit and compile-checked
 * without a second annotation processor - see [AppContainer] for why this app wires its own
 * dependencies.
 */
fun appViewModelFactory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
    initializer { CalendarViewModel(container.eventRepository, container.settings) }
    initializer {
        EventEditViewModel(
            container.eventRepository,
            container.reminderScheduler,
            container.settings,
            container.templateRepository,
        )
    }
    initializer { HomeViewModel(container.eventRepository, container.settings) }
    initializer { AgendaViewModel(container.eventRepository, container.settings) }
    initializer { TaskListViewModel(container.eventRepository, container.settings) }
    initializer { CountdownViewModel(container.eventRepository) }
    initializer { HabitViewModel(container.habitRepository, container.settings) }
    initializer { FocusViewModel(container.focusRepository, container.settings) }
    initializer {
        com.khmercalendar.ui.stats.StatsViewModel(
            events = container.eventRepository,
            focus = container.focusRepository,
            habits = container.habitRepository,
            settings = container.settings,
        )
    }
    initializer {
        AssistantViewModel(
            assistant = container.aiAssistant,
            engine = container.aiEngine,
            repository = container.eventRepository,
            scheduler = container.reminderScheduler,
            settings = container.settings,
            modelStore = container.modelStore,
        )
    }
    initializer {
        AiModelViewModel(
            context = container.appContext,
            modelStore = container.modelStore,
            engine = container.aiEngine,
            settingsStore = container.settingsStore,
            settings = container.settings,
        )
    }
    initializer { CategoriesViewModel(container.eventRepository) }
    initializer { UpdateViewModel(container.updateRepository) }
}
