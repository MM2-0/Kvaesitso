package de.mm20.launcher2.ui.launcher.widgets.favorites

import de.mm20.launcher2.preferences.ui.GridSettings
import de.mm20.launcher2.preferences.ui.UiSettings
import de.mm20.launcher2.services.widgets.WidgetsService
import de.mm20.launcher2.ui.common.FavoritesVM
import de.mm20.launcher2.widgets.FavoritesWidget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.koin.core.component.inject

class FavoritesWidgetVM : FavoritesVM() {

    private val widgetsService: WidgetsService by inject()

    private val widget = MutableStateFlow<FavoritesWidget?>(null)
    override val tagsExpanded = widget.map { it?.config?.tagsMultiline == true }
    override val compactTags: Flow<Boolean> = widget.map { it?.config?.compactTags == true }

    override fun setTagsExpanded(expanded: Boolean) {
        val widget = this.widget.value ?: return
        widgetsService.updateWidget(
            widget.copy(
                config = widget.config.copy(tagsMultiline = expanded)
            )
        )
    }

    fun updateWidget(widget: FavoritesWidget) {
        selectTag(null)
        if (widget.config.customTags) {
            selectTag(widget.config.tagList.firstOrNull())
        }

        this.widget.value = widget
    }
}