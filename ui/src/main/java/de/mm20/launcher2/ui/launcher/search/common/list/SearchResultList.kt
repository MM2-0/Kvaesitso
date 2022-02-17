package de.mm20.launcher2.ui.launcher.search.common.list

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.search.data.Searchable

@Composable
fun SearchResultList(
    items: List<Searchable>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
    ) {
        for (item in items) {
            key(item.key) {
                ListItem(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    item = item
                )
            }
        }
    }
}