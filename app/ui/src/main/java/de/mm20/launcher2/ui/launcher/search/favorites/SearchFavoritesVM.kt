package de.mm20.launcher2.ui.launcher.search.favorites

import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.ui.common.FavoritesVM
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

class SearchFavoritesVM : FavoritesVM() {
    override val tagsExpanded: Flow<Boolean> = dataStore.data.map { it.ui.searchTagsMultiline }
        .shareIn(viewModelScope, SharingStarted.Lazily)

    override fun setTagsExpanded(expanded: Boolean) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setUi(
                        it.ui.toBuilder()
                            .setSearchTagsMultiline(expanded)
                            .build()
                    )
                    .build()
            }
        }
    }

}