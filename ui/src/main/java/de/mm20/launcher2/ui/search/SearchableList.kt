package de.mm20.launcher2.ui.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import de.mm20.launcher2.search.data.Searchable
import de.mm20.launcher2.ui.component.SectionDivider

fun LazyListScope.SearchableList(
    items: List<Searchable>
) {
    items(items) {
        ListItem(it)
    }
    if (items.isNotEmpty()) {
        SectionDivider()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LazyItemScope.ListItem(item: Searchable) {
    SearchableItem(item = item, modifier = Modifier/*.animateItemPlacement()*/)
}