package de.mm20.launcher2.ui.launcher.search.location

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.Modifier
import de.mm20.launcher2.search.Location
import de.mm20.launcher2.ui.launcher.search.common.list.ListItem
import de.mm20.launcher2.ui.launcher.search.common.list.ListResults

fun LazyListScope.LocationResults(
    locations: List<Location>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    highlightedItem: Location?,
    reverse: Boolean,
) {
    ListResults(
        items = locations,
        key = "location",
        reverse = reverse,
        selectedIndex = selectedIndex,
        itemContent = { location, showDetails, index ->
            ListItem(
                modifier = Modifier
                    .fillMaxWidth(),
                item = location,
                showDetails = showDetails,
                onShowDetails = { onSelect(if(it) index else -1) },
                highlight = highlightedItem?.key == location.key
            )
        },
    )
}