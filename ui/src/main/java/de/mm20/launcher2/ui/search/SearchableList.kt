package de.mm20.launcher2.ui.search

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import de.mm20.launcher2.search.data.Searchable
import de.mm20.launcher2.ui.SectionDivider

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

@Composable
fun ListItem(item: Searchable) {
    SearchableItem(item = item)
}