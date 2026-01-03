package de.mm20.launcher2.ui.launcher.widgets.favorites

import de.mm20.launcher2.preferences.ui.GridSettings
import de.mm20.launcher2.preferences.ui.UiSettings
import de.mm20.launcher2.services.widgets.WidgetsService
import de.mm20.launcher2.ui.common.FavoritesVM
import de.mm20.launcher2.widgets.AppsWidget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.koin.core.component.inject

class AppsWidgetVM : FavoritesVM() {

    private val widgetsService: WidgetsService by inject()

    private val uiSettings: UiSettings by inject()

    private val widget = MutableStateFlow<AppsWidget?>(null)
    override val tagsExpanded = widget.map { it?.config?.tagsMultiline == true }
    override val compactTags: Flow<Boolean> = widget.map { it?.config?.compactTags == true }

    private val isTopWidget = widgetsService.isFavoritesWidgetFirst()
    private val clockWidgetFavSlots =
        combine(uiSettings.dock, uiSettings.dockRows, isTopWidget, uiSettings.gridSettings) { (dock, dockRows, isTop, grid) ->
            dock as Boolean
            dockRows as Int
            isTop as Boolean
            grid as GridSettings
            if (!isTop || !dock) 0
            else {
                grid.columnCount * dockRows
            }
        }

    override val favorites = super.favorites.combine(clockWidgetFavSlots) { favs, slots ->
        if (selectedTag.value == null) {
            if (favs.lastIndex < slots) emptyList()
            else favs.subList(slots, favs.size)
        } else {
            favs
        }
    }

    override fun setTagsExpanded(expanded: Boolean) {
        val widget = this.widget.value ?: return
        widgetsService.updateWidget(
            widget.copy(
                config = widget.config.copy(tagsMultiline = expanded)
            )
        )
    }

    fun updateWidget(widget: AppsWidget) {
        selectTag(null)
        if (widget.config.customTags) {
            selectTag(widget.config.tagList.firstOrNull())
        }

        this.widget.value = widget
    }
}