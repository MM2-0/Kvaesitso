package de.mm20.launcher2.ui.launcher.widgets.favorites

import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.ui.common.FavoritesVM
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

class FavoritesWidgetVM: FavoritesVM() {
    override val tagsExpanded: Flow<Boolean> = dataStore.data.map { it.ui.widgetTagsMultiline }
        .shareIn(viewModelScope, SharingStarted.Lazily)

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