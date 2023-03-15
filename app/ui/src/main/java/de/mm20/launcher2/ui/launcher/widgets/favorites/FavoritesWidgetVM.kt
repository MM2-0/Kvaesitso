package de.mm20.launcher2.ui.launcher.widgets.favorites

import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.preferences.Settings.ClockWidgetSettings.ClockWidgetLayout
import de.mm20.launcher2.ui.common.FavoritesVM
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

class FavoritesWidgetVM : FavoritesVM() {

    override val tagsExpanded: Flow<Boolean> = dataStore.data.map { it.ui.widgetTagsMultiline }
        .shareIn(viewModelScope, SharingStarted.Lazily)

    private val isTopWidget = widgetRepository.isFavoritesWidgetFirst()
    private val clockWidgetFavSlots = dataStore.data.combine(isTopWidget) { data, isTop ->
        if (!isTop || !data.clockWidget.favoritesPart) 0
        else {
            if (data.clockWidget.layout == ClockWidgetLayout.Horizontal) data.grid.columnCount - 2
            else data.grid.columnCount
        }
    }

    override val favorites = super.favorites.combine(clockWidgetFavSlots) { favs, slots ->
        if (selectedTag.value == null) {
            favs.subList(slots, favs.size)
        } else {
            favs
        }
    }

    override fun setTagsExpanded(expanded: Boolean) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setUi(
                        it.ui.toBuilder()
                            .setWidgetTagsMultiline(expanded)
                            .build()
                    )
                    .build()
            }
        }
    }

}