package de.mm20.launcher2.ui.launcher.search.calendar

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.Modifier
import de.mm20.launcher2.search.CalendarEvent
import de.mm20.launcher2.ui.launcher.search.common.ShowAllButton
import de.mm20.launcher2.ui.launcher.search.common.list.ListItem
import de.mm20.launcher2.ui.launcher.search.common.list.ListResults
import kotlin.math.min

fun LazyListScope.CalendarResults(
    events: List<CalendarEvent>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    highlightedItem: CalendarEvent?,
    reverse: Boolean,
    truncate: Boolean,
    onShowAll: () -> Unit,
) {
    ListResults(
        items = events.subList(0, if (truncate) min(5, events.size) else events.size),
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
        after = if (truncate && events.size > 5) {
            {
                ShowAllButton(onShowAll = onShowAll)
            }
        } else null
    )
}