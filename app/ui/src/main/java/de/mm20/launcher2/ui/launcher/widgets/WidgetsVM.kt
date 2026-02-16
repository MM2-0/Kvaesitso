package de.mm20.launcher2.ui.launcher.widgets

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.mm20.launcher2.preferences.ui.UiSettings
import de.mm20.launcher2.widgets.Widget
import de.mm20.launcher2.widgets.WidgetRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.UUID

class WidgetsVM(
    private val parentId: UUID?,
) : ViewModel(), KoinComponent {
    private val widgetRepository: WidgetRepository by inject()

    private val uiSettings: UiSettings by inject()

    val editButton = uiSettings.widgetEditButton
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val widgets = widgetRepository.get(parent = parentId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    fun addWidget(widget: Widget, index: Int? = null) {
        val widgets = widgets.value.toMutableList()
        if (index == null) {
            widgets.add(widget)
        } else {
            widgets.add(index.coerceAtMost(widgets.size), widget)
        }
        widgetRepository.set(widgets, parentId)
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
        widgetRepository.set(widgets, parentId)
    }

    fun moveDown(index: Int) {
        val widgets = widgets.value.toMutableList()
        val widget = widgets.removeAt(index)
        widgets.add(index + 1, widget)
        widgetRepository.set(widgets, parentId)
    }

    companion object : KoinComponent {
        fun Factory(parentId: String) = viewModelFactory {
            initializer {
                val id = try {
                    UUID.fromString(parentId)
                } catch (e: IllegalArgumentException) {
                    Log.e("WidgetsVM", "Invalid parentId: $parentId", e)
                    null
                }
                WidgetsVM(id)
            }
        }
    }
}
