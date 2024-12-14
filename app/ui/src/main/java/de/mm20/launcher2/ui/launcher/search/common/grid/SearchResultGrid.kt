package de.mm20.launcher2.ui.launcher.search.common.grid

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
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
    showList: Boolean = LocalGridSettings.current.showList,
    showListIcons: Boolean = LocalGridSettings.current.showListIcons,
    columns: Int = LocalGridSettings.current.columnCount,
    reverse: Boolean = false,
    highlightedItem: SavableSearchable? = null,
    transitionKey: Any? = items
) {
    AnimatedContent(
        items to transitionKey,
        modifier = modifier
            .fillMaxWidth()
            .padding(4.dp),
        transitionSpec = {
            fadeIn() togetherWith fadeOut()
        },
        contentKey = { it.second }
    ) { (items, _) ->
        Column(
            verticalArrangement = if (reverse) Arrangement.BottomReversed else Arrangement.Top
        ) {
            for (i in 0 until ceil(items.size / columns.toFloat()).toInt()) {
                Row {
                    for (j in 0 until columns) {
                        val item = items.getOrNull(i * columns + j)
                        if (item != null) {
                            key(item.key) {
                                GridItem(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(4.dp),
                                    item = item,
                                    showList = showList,
                                    showLabels = showLabels,
                                    showListIcons = showListIcons,
                                    highlight = item.key == highlightedItem?.key
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}
