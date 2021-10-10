package de.mm20.launcher2.ui.search

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.favorites.FavoritesViewModel
import org.koin.androidx.compose.getViewModel

@Composable
fun favoriteResults(): LazyListScope.(listState: LazyListState) -> Unit {
    val viewModel: FavoritesViewModel = getViewModel()

    val favorites by viewModel.getFavorites(5).observeAsState(emptyList())
    return {
        SearchableGrid(items = favorites, listState = it)
    }
}