package de.mm20.launcher2.ui.searchable

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.search.data.CalendarEvent
import de.mm20.launcher2.search.data.Searchable
import de.mm20.launcher2.ui.search.Representation

@Deprecated("Use [SearchableList] instead")
@Composable
fun DeprecatedSearchableList(items: List<Searchable>, modifier: Modifier = Modifier) {

    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {

        for (item in items) {
            Box(
                modifier = Modifier.padding(bottom = 8.dp)
            )
            key(item.key) {
                if (item is CalendarEvent) {
                    CalendarEventItem(
                        event = item,
                        initialRepresentation = Representation.List,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
