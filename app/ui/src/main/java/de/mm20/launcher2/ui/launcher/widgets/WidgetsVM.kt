package de.mm20.launcher2.ui.launcher.widgets

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.widgets.Widget
import de.mm20.launcher2.widgets.WidgetRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class WidgetsVM : ViewModel(), KoinComponent {
    private val widgetRepository: WidgetRepository by inject()

    private val dataStore: LauncherDataStore by inject()

    val editButton = dataStore.data.map { it.widgets.editButton }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val widgets = widgetRepository.get()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    fun addWidget(widget: Widget, index: Int? = null) {
        val widgets = widgets.value.toMutableList()
        if (index == null) {
            widgets.add(widget)
        } else {
            widgets.add(index.coerceAtMost(widgets.size), widget)
        }
        widgetRepository.set(widgets)
    }

    fun removeWidget(widget: Widget) {
        widgetRepository.delete(widget)
    }

    fun updateWidget(widget: Widget) {
        widgetRepository.update(widget)
    }

    fun moveUp(index: Int) {
        val widgets = widgets.value.toMutableList()
        val widget = widgets.removeAt(index)
        widgets.add(index - 1, widget)
        widgetRepository.set(widgets)
    }

    fun moveDown(index: Int) {
        val widgets = widgets.value.toMutableList()
        val widget = widgets.removeAt(index)
        widgets.add(index + 1, widget)
        widgetRepository.set(widgets)
    }
}