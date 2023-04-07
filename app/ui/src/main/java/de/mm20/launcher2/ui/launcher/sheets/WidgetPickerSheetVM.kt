package de.mm20.launcher2.ui.launcher.sheets

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.mm20.launcher2.ktx.normalize
import de.mm20.launcher2.widgets.Widget
import de.mm20.launcher2.widgets.WidgetRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

class WidgetPickerSheetVM(
    private val widgetRepository: WidgetRepository,
    private val context: Context,
) : ViewModel() {

    private val packageManager = context.packageManager

    val searchQuery = MutableStateFlow("")

    private val enabledWidgets = widgetRepository.getWidgets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(100), emptyList())

    private val allBuiltInWidgets = enabledWidgets.map { w ->
        widgetRepository.getInternalWidgets().filter { !w.contains(it) }
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(100))

    val builtInWidgets = allBuiltInWidgets
        .combine(searchQuery) { widgets, query ->
            if (query.isBlank()) return@combine widgets
            withContext(Dispatchers.IO) {
                val normalizedQuery = query.normalize()
                widgets.filter {
                    it.loadLabel(context).normalize().contains(normalizedQuery)
                }
            }
        }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(100))

    private val allAppWidgets = flow {
        val widgets = widgetRepository.getAppWidgets()
        emit(widgets)
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(100))

    private val filteredAppWidgets = allAppWidgets
        .combine(searchQuery) { widgets, query ->
            if (query.isBlank()) return@combine widgets
            withContext(Dispatchers.IO) {
                val normalizedQuery = query.normalize()
                widgets.filter {
                    if (it.loadLabel(packageManager).normalize().contains(normalizedQuery)) {
                        return@filter true
                    }
                    val pkg = it.provider.packageName
                    val appInfo = try {
                        packageManager.getApplicationInfo(pkg, 0)
                    } catch (e: PackageManager.NameNotFoundException) {
                        return@filter false
                    }
                    appInfo.loadLabel(packageManager).toString().normalize()
                        .contains(normalizedQuery)
                }
            }
        }
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(100))

    val expandAllGroups = filteredAppWidgets.map {
        it.size < 10
    }

    val appWidgetGroups = filteredAppWidgets.map { widgets ->
        withContext(Dispatchers.Default) {
            widgets
                .sortedBy { it.loadLabel(packageManager).normalize() }
                .groupBy {
                    it.provider.packageName
                }
                .map {
                    val pkg = it.key
                    val appInfo = try {
                        packageManager.getApplicationInfo(pkg, 0)
                    } catch (e: PackageManager.NameNotFoundException) {
                        return@map AppWidgetGroup("", pkg, emptyList())
                    }
                    AppWidgetGroup(appInfo.loadLabel(packageManager).toString(), pkg, it.value)
                }
                .sortedBy { it.appName.normalize() }
        }
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(100))

    val expandedGroup = mutableStateOf<String?>(null)

    fun pickWidget(widget: Widget) {
        val position = enabledWidgets.value.size
        widgetRepository.addWidget(widget, position)
    }

    fun toggleGroup(group: String) {
        expandedGroup.value = if (expandedGroup.value == group) null else group
    }

    fun search(query: String) {
        searchQuery.value = query
    }

    companion object : KoinComponent {
        val Factory = viewModelFactory {
            initializer {
                WidgetPickerSheetVM(get(), get())
            }
        }
    }
}

data class AppWidgetGroup(
    val appName: String,
    val packageName: String,
    val widgets: List<AppWidgetProviderInfo>
)