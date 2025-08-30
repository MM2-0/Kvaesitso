package de.mm20.launcher2.ui.launcher.search.calendar

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.Modifier
import de.mm20.launcher2.search.CalendarEvent
import de.mm20.launcher2.ui.launcher.search.common.list.ListItem
import de.mm20.launcher2.ui.launcher.search.common.list.ListResults

fun LazyListScope.CalendarResults(
    events: List<CalendarEvent>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    highlightedItem: CalendarEvent?,
    reverse: Boolean,
) {
    ListResults(
        items = events,
        key = "calendar",
        reverse = reverse,
        selectedIndex = selectedIndex,
        itemContent = { calendar, showDetails, index ->
            ListItem(
                modifier = Modifier
                    .fillMaxWidth(),
                item = calendar,
                showDetails = showDetails,
                onShowDetails = { onSelect(if(it) index else -1) },
                highlight = highlightedItem?.key == calendar.key
            )
        },
    )
}