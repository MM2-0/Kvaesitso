package de.mm20.launcher2.ui.settings.searchactions

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.ktx.romanize
import de.mm20.launcher2.searchactions.SearchActionService
import de.mm20.launcher2.searchactions.actions.SearchActionIcon
import de.mm20.launcher2.searchactions.builders.AppSearchActionBuilder
import de.mm20.launcher2.searchactions.builders.CustomIntentActionBuilder
import de.mm20.launcher2.searchactions.builders.CustomizableSearchActionBuilder
import de.mm20.launcher2.searchactions.builders.WebsearchActionBuilder
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import kotlin.math.roundToInt

class EditSearchActionSheetVM : ViewModel(), KoinComponent {

    private val searchActionService: SearchActionService by inject()

    private var initialCustomIcon: String? = null

    val currentPage = mutableStateOf(EditSearchActionPage.SelectType)
    val createNew = mutableStateOf(false)

    val searchAction = mutableStateOf<CustomizableSearchActionBuilder?>(null)

    fun init(searchAction: CustomizableSearchActionBuilder?) {
        initialCustomIcon = searchAction?.customIcon
        currentPage.value = when (searchAction) {
            is AppSearchActionBuilder -> EditSearchActionPage.CustomizeAppSearch
            is WebsearchActionBuilder -> EditSearchActionPage.CustomizeWebSearch
            else -> EditSearchActionPage.SelectType
        }
        createNew.value = searchAction == null
        this.searchAction.value = searchAction
    }

    fun getSearchableApps(context: Context) = flow {
        val items = withContext(Dispatchers.Default) {
            searchActionService.getSearchActivities().map {
                SearchableApp(
                    label = context.packageManager.getActivityInfo(it, 0)
                        .loadLabel(context.packageManager).toString(),
                    componentName = it
                )
            }.sortedBy { it.label.romanize().lowercase() }
        }
        emit(items)
    }

    fun initAppSearch() {
        currentPage.value = EditSearchActionPage.InitAppSearch
    }

    fun initWebSearch() {
        currentPage.value = EditSearchActionPage.InitWebSearch
    }

    fun selectSearchableApp(app: SearchableApp) {
        searchAction.value = AppSearchActionBuilder(
            label = app.label,
            componentName = app.componentName,
            icon = SearchActionIcon.Custom,
            customIcon = null,
            iconColor = 1,
        )
        currentPage.value = EditSearchActionPage.CustomizeAppSearch
    }


    fun initCustomIntent() {
        searchAction.value = CustomIntentActionBuilder(
            label = "",
            queryKey = "",
            baseIntent = Intent(),
            icon = SearchActionIcon.Search,
            customIcon = null,
            iconColor = 0,
        )
        currentPage.value = EditSearchActionPage.CustomizeCustomIntent
    }

    fun setLabel(label: String) {
        val action = searchAction.value ?: return

        val newAction = when (action) {
            is CustomIntentActionBuilder -> action.copy(label = label)
            is AppSearchActionBuilder -> action.copy(label = label)
            is WebsearchActionBuilder -> action.copy(label = label)
        }

        searchAction.value = newAction
    }

    fun setComponentName(componentName: ComponentName) {
        val action = searchAction.value ?: return

        val newAction = when (action) {
            is CustomIntentActionBuilder -> action.copy(
                baseIntent = action.baseIntent.setComponent(
                    componentName
                )
            )

            is AppSearchActionBuilder -> action.copy(componentName = componentName)
            is WebsearchActionBuilder -> action
        }

        searchAction.value = newAction
    }


    val initWebsearchUrl = mutableStateOf("")
    /**
     * Last imported URL that failed (if the current URL is equal to this, show an error banner)
     */
    private val websearchImportErrorUrl = mutableStateOf<String?>(null)
    val websearchImportError =
        derivedStateOf { websearchImportErrorUrl.value == initWebsearchUrl.value }
    val loadingWebsearch = mutableStateOf(false)

    val skipWebsearchImport = derivedStateOf { websearchImportError.value || initWebsearchUrl.value.isEmpty() }

    fun importWebsearch(density: Density) {
        if (loadingWebsearch.value) return
        viewModelScope.launch {
            val url = initWebsearchUrl.value
            loadingWebsearch.value = true
            val action = searchActionService.importWebsearch(
                url,
                with(density) { 20.dp.toPx().roundToInt() })
            if (action == null) {
                websearchImportErrorUrl.value = url
            } else {
                websearchImportErrorUrl.value = null
                searchAction.value = action
                currentPage.value = EditSearchActionPage.CustomizeWebSearch
            }
            loadingWebsearch.value = false
        }
    }

    fun skipWebsearchImport() {
        searchAction.value = WebsearchActionBuilder(
            urlTemplate = "",
            iconColor = 0,
            icon = SearchActionIcon.Search,
            label = "",
        )
        currentPage.value = EditSearchActionPage.CustomizeWebSearch
    }

    fun setUrlTemplate(template: String) {
        val action = searchAction.value ?: return
        if (action is WebsearchActionBuilder) {
            searchAction.value = action.copy(
                urlTemplate = template
            )
        }
    }


    private val invalidWebsearchUrl = mutableStateOf<String?>(null)
    val websearchInvalidUrlError = derivedStateOf { invalidWebsearchUrl.value == (searchAction.value as? WebsearchActionBuilder)?.urlTemplate }
    fun validate() : Boolean {
        val action = searchAction.value ?: return false

        if (action is WebsearchActionBuilder) {
            val valid = action.urlTemplate.contains("\${1}")
            invalidWebsearchUrl.value = if(valid) null else action.urlTemplate
            return valid
        }
        return true
    }

    fun onSave() {
        val action = searchAction.value ?: return
        if (initialCustomIcon != action.customIcon) deleteCustomIcon(initialCustomIcon)
    }

    fun onDismiss() {
        val action = searchAction.value ?: return
        val newIcon = action.customIcon
        if (newIcon != initialCustomIcon) {
            deleteCustomIcon(newIcon)
        }
    }

    fun setIcon(icon: SearchActionIcon) {
        val action = searchAction.value ?: return
        if (action.customIcon != initialCustomIcon) {
            deleteCustomIcon(action.customIcon)
        }
        searchAction.value = when(action) {
            is WebsearchActionBuilder -> action.copy(icon = icon, customIcon = null, iconColor = 0)
            is CustomIntentActionBuilder -> action.copy(icon = icon, customIcon = null, iconColor = 0)
            is AppSearchActionBuilder -> action.copy(icon = icon, customIcon = null, iconColor = 0)
        }
    }

    fun setCustomIcon(iconPath: String?) {
        val action = searchAction.value ?: return
        if (action.customIcon != initialCustomIcon) {
            deleteCustomIcon(action.customIcon)
        }
        searchAction.value = when(action) {
            is WebsearchActionBuilder -> action.copy(customIcon = iconPath, iconColor = 1, icon = SearchActionIcon.Custom)
            is CustomIntentActionBuilder -> action.copy(customIcon = iconPath, iconColor = 1, icon = SearchActionIcon.Custom)
            is AppSearchActionBuilder -> action.copy(customIcon = iconPath, iconColor = 1, icon = SearchActionIcon.Custom)
        }
    }

    private fun deleteCustomIcon(path: String?) {
        path ?: return
        viewModelScope.launch(Dispatchers.IO) {
            File(path).delete()
        }
    }

    fun openIconPicker() {
        currentPage.value = EditSearchActionPage.PickIcon
    }

    fun applyIcon() {
        currentPage.value = when(searchAction.value) {
            is AppSearchActionBuilder -> EditSearchActionPage.CustomizeAppSearch
            is WebsearchActionBuilder -> EditSearchActionPage.CustomizeWebSearch
            is CustomIntentActionBuilder -> EditSearchActionPage.CustomizeCustomIntent
            null -> EditSearchActionPage.SelectType
        }
    }

    fun setIconColor(color: Int) {
        val action = searchAction.value ?: return
        searchAction.value = when(action) {
            is WebsearchActionBuilder -> action.copy(iconColor = color)
            is CustomIntentActionBuilder -> action.copy(iconColor = color)
            is AppSearchActionBuilder -> action.copy(iconColor = color)
        }
    }

    fun importIcon(uri: Uri, size: Int) {
        viewModelScope.launch {
            val path = searchActionService.createIcon(uri, size)
            setCustomIcon(path)
        }
    }
}

enum class EditSearchActionPage {
    SelectType,
    InitAppSearch,
    InitWebSearch,
    CustomizeAppSearch,
    CustomizeWebSearch,
    CustomizeCustomIntent,
    PickIcon,
}

data class SearchableApp(
    val label: String,
    val componentName: ComponentName,
)