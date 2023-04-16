package de.mm20.launcher2.ui.launcher.widgets

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.widgets.Widget
import de.mm20.launcher2.widgets.WidgetRepository
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class WidgetsVM : ViewModel(), KoinComponent {
    private val widgetRepository: WidgetRepository by inject()

    private val dataStore: LauncherDataStore by inject()

    val editButton = dataStore.data.map { it.widgets.editButton }.asLiveData()

    val widgets = widgetRepository.get().asLiveData()

    fun addWidget(widget: Widget) {
        val widgets = widgets.value?.toMutableList() ?: return
        widgets.add(widget)
        widgetRepository.set(widgets)
    }

    fun removeWidget(widget: Widget) {
        widgetRepository.delete(widget)
    }

    fun updateWidget(widget: Widget) {
        widgetRepository.update(widget)
    }

    fun moveUp(index: Int) {
        val widgets = widgets.value?.toMutableList() ?: return
        val widget = widgets.removeAt(index)
        widgets.add(index - 1, widget)
        widgetRepository.set(widgets)
    }

    fun moveDown(index: Int) {
        val widgets = widgets.value?.toMutableList() ?: return
        val widget = widgets.removeAt(index)
        widgets.add(index + 1, widget)
        widgetRepository.set(widgets)
    }
}