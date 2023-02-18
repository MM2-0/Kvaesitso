package de.mm20.launcher2.ui.launcher.search.common.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.ui.layout.BottomReversed

@Composable
fun SearchResultList(
    items: List<SavableSearchable>,
    modifier: Modifier = Modifier,
    reverse: Boolean = false,
    highlightedItem: SavableSearchable? = null
) {
    Column(
        modifier = modifier,
        verticalArrangement = if (reverse) Arrangement.BottomReversed else Arrangement.Top
    ) {
        for (item in items) {
            key(item.key) {
                ListItem(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    item = item,
                    highlight = item.key == highlightedItem?.key
                )
            }
        }
    }
}