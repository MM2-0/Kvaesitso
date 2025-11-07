package de.mm20.launcher2.ui.launcher.widgets.favorites

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.common.FavoritesTagSelector
import de.mm20.launcher2.ui.component.Banner
import de.mm20.launcher2.ui.launcher.search.common.grid.SearchResultGrid
import de.mm20.launcher2.widgets.FavoritesWidget

@Composable
fun FavoritesWidget(widget: FavoritesWidget) {
    val viewModel: FavoritesWidgetVM = viewModel(key = "favorites-widget-${widget.id}")
    val favorites by remember { viewModel.favorites }.collectAsState(emptyList())
    val pinnedTags by viewModel.pinnedTags.collectAsState(emptyList())
    val selectedTag by viewModel.selectedTag.collectAsState(null)
    val compactTags by viewModel.compactTags.collectAsState(false)
    val favoritesEditButton = widget.config.editButton

    val tagsExpanded by viewModel.tagsExpanded.collectAsState(false)

    LaunchedEffect(widget) {
        viewModel.updateWidget(widget)
    }

    Column(
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        if (favorites.isNotEmpty()) {
            SearchResultGrid(favorites, transitionKey = selectedTag)
        } else {
            Banner(
                modifier = Modifier.padding(16.dp),
                text = stringResource(
                    if (selectedTag == null) R.string.favorites_empty else R.string.favorites_empty_tag
                ),
                icon = if (selectedTag == null) R.drawable.star_24px else R.drawable.tag_24px,
            )
        }
        if (pinnedTags.isNotEmpty() || favoritesEditButton) {
            FavoritesTagSelector(
                tags = pinnedTags,
                selectedTag = selectedTag,
                editButton = favoritesEditButton,
                reverse = false,
                onSelectTag = { viewModel.selectTag(it) },
                scrollState = rememberScrollState(),
                expanded = tagsExpanded,
                compact = compactTags,
                onExpand = { viewModel.setTagsExpanded(it) }
            )
        }
    }
}