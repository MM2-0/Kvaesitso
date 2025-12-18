package de.mm20.launcher2.ui.launcher.widgets.favorites

import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.preferences.ui.GridSettings
import de.mm20.launcher2.preferences.ui.UiSettings
import de.mm20.launcher2.search.Tag
import de.mm20.launcher2.services.widgets.WidgetsService
import de.mm20.launcher2.ui.common.FavoritesVM
import de.mm20.launcher2.widgets.FavoritesWidget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import org.koin.core.component.inject

class FavoritesWidgetVM : FavoritesVM() {

    private val widgetsService: WidgetsService by inject()

    private val uiSettings: UiSettings by inject()

    private val widget = MutableStateFlow<FavoritesWidget?>(null)
    override val tagsExpanded = widget.map { it?.config?.tagsMultiline == true }
    override val compactTags: Flow<Boolean> = widget.map { it?.config?.compactTags == true }
    val showFavorites: Flow<Boolean> = widget.map { it?.config?.showFavorites == true }
    val showTags: Flow<Boolean> = widget.map { it?.config?.showTags == true }
    private val selectedTags: Flow<List<Tag>> = widget.map { tagStr ->
        val tags = mutableListOf<Tag>()
        val tagList = tagStr?.config?.tagList
        if (!tagList.isNullOrEmpty()) {
            tagList.map {
                tags.add(Tag(it))
            }
        }
        tags
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    val combinedTags = selectedTags
        .combine(pinnedTags) { selectedTags, pinnedTags ->
            selectedTags.ifEmpty { pinnedTags }
        }.shareIn(viewModelScope, SharingStarted.WhileSubscribed())

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

    fun updateWidget(widget: FavoritesWidget, combined: List<Tag>) {
        selectTag(null)
        if (!widget.config.showFavorites
            && widget.config.showTags
        ) {
            if (combined.isNotEmpty()) {
                val firstTag = combined[0];
                selectTag(firstTag.tag)
            }
        }

        this.widget.value = widget
    }
}