package de.mm20.launcher2.ui.launcher.widgets.favorites

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.ui.launcher.search.common.grid.SearchResultGrid

@Composable
fun FavoritesWidget() {
    val viewModel: FavoritesWidgetVM = viewModel()
    val favorites by remember { viewModel.favorites }.collectAsState(emptyList())

    SearchResultGrid(favorites)
}