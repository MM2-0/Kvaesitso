package de.mm20.launcher2.ui.launcher.search.common.grid

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.ui.layout.BottomReversed
import de.mm20.launcher2.ui.locals.LocalGridSettings
import kotlin.math.ceil

@Composable
fun SearchResultGrid(
    items: List<SavableSearchable>,
    modifier: Modifier = Modifier,
    showLabels: Boolean = LocalGridSettings.current.showLabels,
    columns: Int = LocalGridSettings.current.columnCount,
    reverse: Boolean = false,
    highlightedItem: SavableSearchable? = null
) {
    Column(
        modifier = modifier
            .animateContentSize()
            .fillMaxWidth()
            .padding(4.dp),
        verticalArrangement = if (reverse) Arrangement.BottomReversed else Arrangement.Top
    ) {
        for (i in 0 until ceil(items.size / columns.toFloat()).toInt()) {
            Row {
                for (j in 0 until columns) {
                    val item = items.getOrNull(i * columns + j)
                    if (item != null) {
                        GridItem(
                            modifier = Modifier
                                .weight(1f)
                                .padding(4.dp, 8.dp),
                            item = item,
                            showLabels = showLabels,
                            highlight = item.key == highlightedItem?.key
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
