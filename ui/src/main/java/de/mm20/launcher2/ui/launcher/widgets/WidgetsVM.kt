package de.mm20.launcher2.ui.launcher.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.liveData
import de.mm20.launcher2.widgets.ExternalWidget
import de.mm20.launcher2.widgets.Widget
import de.mm20.launcher2.widgets.WidgetRepository
import de.mm20.launcher2.widgets.WidgetType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class WidgetsVM : ViewModel(), KoinComponent {
    private val widgetRepository: WidgetRepository by inject()

    val isEditMode = MutableLiveData(false)

    val widgets = widgetRepository.getWidgets().asLiveData()

    fun setEditMode(editMode: Boolean) {
        isEditMode.value = editMode
    }

    fun addWidget(widget: Widget) {
        widgetRepository.addWidget(widget, widgets.value?.size ?: 0)
    }

    fun addAppWidget(context: Context, widgetId: Int) {
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val appWidget = AppWidgetManager.getInstance(context)
            .getAppWidgetInfo(widgetId) ?: return
        val widget = ExternalWidget(
            widgetProviderInfo = appWidget,
            height = appWidget.minHeight,
            widgetId =  widgetId,
        )
        addWidget(widget)
    }

    fun removeWidget(widget: Widget) {
        widgetRepository.removeWidget(widget)
    }

    fun setWidgetHeight(widget: Widget, newHeight: Int) {
        widgetRepository.setWidgetHeight(widget, newHeight)
    }

    fun getAvailableBuiltInWidgets(): List<Widget> {
        return widgetRepository.getInternalWidgets().filter {
            widgets.value?.contains(it)?.not() ?: false
        }
    }

    fun moveUp(index: Int) {
        val widgets = widgets.value?.toMutableList() ?: return
        val widget = widgets.removeAt(index)
        widgets.add(index - 1, widget)
        widgetRepository.saveWidgets(widgets)
    }

    fun moveDown(index: Int) {
        val widgets = widgets.value?.toMutableList() ?: return
        val widget = widgets.removeAt(index)
        widgets.add(index + 1, widget)
        widgetRepository.saveWidgets(widgets)
    }
}