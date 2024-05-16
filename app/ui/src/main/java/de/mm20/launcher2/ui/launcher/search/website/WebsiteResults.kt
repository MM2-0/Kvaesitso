package de.mm20.launcher2.ui.launcher.search.website

import androidx.compose.foundation.lazy.LazyListScope
import de.mm20.launcher2.search.Website
import de.mm20.launcher2.ui.launcher.search.common.list.ListItem
import de.mm20.launcher2.ui.launcher.search.common.list.ListResults

fun LazyListScope.WebsiteResults(
    websites: List<Website>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    highlightedItem: Website?,
    reverse: Boolean,
) {
    ListResults(
        key = "website",
        items = websites,
        itemContent = { website, showDetails, index ->
            ListItem(
                item = website,
                showDetails = showDetails,
                onShowDetails = { onSelect(if(it) index else -1) },
                highlight = website.key == highlightedItem?.key,
            )
        },
        selectedIndex = selectedIndex,
        reverse = reverse,
    )
}