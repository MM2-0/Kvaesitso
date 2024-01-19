package de.mm20.launcher2.ui.launcher.widgets.favorites

import de.mm20.launcher2.preferences.ui.GridSettings
import de.mm20.launcher2.preferences.ui.UiSettings
import de.mm20.launcher2.services.widgets.WidgetsService
import de.mm20.launcher2.ui.common.FavoritesVM
import de.mm20.launcher2.widgets.FavoritesWidget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import org.koin.core.component.inject

class FavoritesWidgetVM : FavoritesVM() {

    private val widgetsService: WidgetsService by inject()

    private val uiSettings: UiSettings by inject()

    override val tagsExpanded: MutableStateFlow<Boolean> = MutableStateFlow(false)
    private val widget = MutableStateFlow<FavoritesWidget?>(null)

    private val isTopWidget = widgetsService.isFavoritesWidgetFirst()
    private val clockWidgetFavSlots =
        combine(uiSettings.dock, isTopWidget, uiSettings.gridSettings) { (dock, isTop, grid) ->
            dock as Boolean
            isTop as Boolean
            grid as GridSettings
            if (!isTop || !dock) 0
            else {
                grid.columnCount
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

    fun updateWidget(widget: FavoritesWidget) {
        tagsExpanded.value = widget.config.tagsMultiline
    }

}