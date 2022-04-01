package de.mm20.launcher2.ui.launcher.widgets.picker

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.ui.text.toLowerCase
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import de.mm20.launcher2.crashreporter.CrashReporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

typealias AppWidgetGroup = Pair<String, List<AppWidgetProviderInfo>>

inline val AppWidgetGroup.appName: String
    get() = this.first
inline val AppWidgetGroup.widgets: List<AppWidgetProviderInfo>
    get() = this.second

class PickAppWidgetVM : ViewModel() {
    var appWidgetId: MutableLiveData<Int?> = MutableLiveData(null)
    val selectedAppWidget: MutableLiveData<AppWidgetProviderInfo?> = MutableLiveData(null)

    fun selectAppWidget(appWidget: AppWidgetProviderInfo, appWidgetId: Int) {
        this.appWidgetId.value = appWidgetId
        this.selectedAppWidget.value = appWidget
    }

    fun getAvailableWidgets(context: Context): LiveData<List<AppWidgetGroup>?> = liveData {
        emit(null)
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val widgets = withContext(Dispatchers.IO) {
            appWidgetManager.installedProviders
                .sortedBy { it.loadLabel(context.packageManager).lowercase() }
                .groupBy {
                    val pkg = it.provider.packageName
                    val appInfo = try {
                        context.packageManager.getApplicationInfo(pkg, 0)
                    } catch (e: PackageManager.NameNotFoundException) {
                        CrashReporter.logException(e)
                        return@groupBy ""
                    }
                    appInfo.loadLabel(context.packageManager).toString()
                }
                .toList()
                .sortedBy { it.first.lowercase() }
        }
        emit(widgets)
    }
}