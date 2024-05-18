package de.mm20.launcher2.ui.launcher.search.common.list

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    var selectedIndex by remember { mutableIntStateOf(-1) }
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small),
        verticalArrangement = if (reverse) Arrangement.BottomReversed else Arrangement.Top
    ) {
        for ((i, item) in items.withIndex()) {
            key(item.key) {
                if (i != 0) {
                    HorizontalDivider()
                }
                ListItem(
                    modifier = Modifier
                        .fillMaxWidth(),
                    item = item,
                    highlight = item.key == highlightedItem?.key,
                    showDetails = selectedIndex == i,
                    onShowDetails = { selectedIndex = if (it) i else -1}
                )
            }
        }
    }
}