package de.mm20.launcher2.ui.launcher.widgets

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.liveData
import de.mm20.launcher2.widgets.Widget
import de.mm20.launcher2.widgets.WidgetRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class WidgetsVM : ViewModel(), KoinComponent {
    private val widgetRepository: WidgetRepository by inject()

    val isEditMode = MutableLiveData(false)

    val widgets = widgetRepository.getWidgets().asLiveData()

    fun setEditMode(editMode: Boolean) {
        isEditMode.value = editMode
    }

    fun saveWidgets(widgets: List<Widget>) {
        widgetRepository.saveWidgets(widgets)
    }

    fun getInternalWidgets(): List<Widget> {
        return widgetRepository.getInternalWidgets()
    }
}