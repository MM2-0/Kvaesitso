package de.mm20.launcher2.ui.launcher.sheets

import de.mm20.launcher2.services.widgets.WidgetsService
import android.appwidget.AppWidgetProviderInfo
import android.content.pm.PackageManager
import androidx.annotation.StringRes
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.mm20.launcher2.search.ResultScore
import de.mm20.launcher2.search.StringNormalizer
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
import java.text.Collator

class WidgetPickerSheetVM(
    private val widgetsService: WidgetsService,
    private val packageManager: PackageManager,
    private val stringNormalizer: StringNormalizer,
) : ViewModel() {

    val searchQuery = MutableStateFlow("")

    private val enabledWidgets = widgetsService.getWidgets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(100), emptyList())

    private val allBuiltInWidgets =
        widgetsService.getAvailableBuiltInWidgets()
            .shareIn(viewModelScope, SharingStarted.WhileSubscribed(100))

    val builtInWidgets = allBuiltInWidgets
        .combine(searchQuery) { widgets, query ->
            if (query.isBlank()) return@combine widgets
            withContext(Dispatchers.IO) {
                val normalizedQuery = stringNormalizer.normalize(query)
                widgets.filter {
                    ResultScore.from(
                        query = normalizedQuery,
                        primaryFields = listOf(
                            stringNormalizer.normalize(it.label),
                            it.type
                        )
                    ).score >= 0.8f
                }
            }
        }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(100))

    private val allAppWidgets = flow {
        val widgets = widgetsService.getAppWidgetProviders()
        emit(widgets)
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(100))

    private val filteredAppWidgets = allAppWidgets
        .combine(searchQuery) { widgets, query ->
            if (query.isBlank()) return@combine widgets
            withContext(Dispatchers.IO) {
                val normalizedQuery = stringNormalizer.normalize(query)
                widgets.filter {
                    val widgetNormalizedLabel = stringNormalizer.normalize(it.loadLabel(packageManager))
                    if (widgetNormalizedLabel.contains(normalizedQuery)) {
                        return@filter true
                    }
                    val pkg = it.provider.packageName
                    val appInfo = try {
                        packageManager.getApplicationInfo(pkg, 0)
                    } catch (e: PackageManager.NameNotFoundException) {
                        return@filter false
                    }
                    val normalizedAppLabel = stringNormalizer.normalize(appInfo.loadLabel(packageManager).toString())

                    ResultScore.from(
                        query = normalizedQuery,
                        primaryFields = listOf(
                            widgetNormalizedLabel,
                            normalizedAppLabel,
                        )
                    ).score >= 0.8f
                }
            }
        }
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(100))

    val expandAllGroups = filteredAppWidgets.map {
        it.size < 10
    }

    val appWidgetGroups = filteredAppWidgets.map { widgets ->
        val collator = Collator.getInstance().apply { strength = Collator.SECONDARY }
        withContext(Dispatchers.Default) {
            widgets
                .sortedWith { el1, el2 ->
                    collator.compare(el1.loadLabel(packageManager), el2.loadLabel(packageManager))
                }
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
                .sortedWith { el1, el2 ->
                    collator.compare(el1.appName, el2.appName)
                }
        }
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(100))

    val expandedGroup = mutableStateOf<String?>(null)

    fun toggleGroup(group: String) {
        expandedGroup.value = if (expandedGroup.value == group) null else group
    }

    fun search(query: String) {
        searchQuery.value = query
    }

    companion object : KoinComponent {
        val Factory = viewModelFactory {
            initializer {
                WidgetPickerSheetVM(get(), get(), get())
            }
        }
    }

}

data class AppWidgetGroup(
    val appName: String,
    val packageName: String,
    val widgets: List<AppWidgetProviderInfo>
)

data class BuiltInWidgetInfo(
    val type: String,
    @StringRes val label: Int,
    val icon: ImageVector
)