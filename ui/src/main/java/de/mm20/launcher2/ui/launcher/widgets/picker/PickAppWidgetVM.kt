package de.mm20.launcher2.ui.launcher.widgets.picker

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PickAppWidgetVM: ViewModel() {
    var appWidgetId: MutableLiveData<Int?> = MutableLiveData(null)
    val selectedAppWidget: MutableLiveData<AppWidgetProviderInfo?> = MutableLiveData(null)

    fun selectAppWidget(appWidget: AppWidgetProviderInfo, appWidgetId: Int) {
        this.appWidgetId.value = appWidgetId
        this.selectedAppWidget.value = appWidget
    }

    fun getAvailableWidgets(context: Context): LiveData<List<AppWidgetProviderInfo>?> = liveData {
        emit(null)
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val widgets = withContext(Dispatchers.IO) {
            appWidgetManager.installedProviders.sortedBy { it.loadLabel(context.packageManager) }
        }
        emit(widgets)
    }
}