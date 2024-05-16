package de.mm20.launcher2.ui.launcher.search.common.grid

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SharedTransitionScope
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
    SharedTransitionScope {
        AnimatedContent(
            items,
            modifier = it then modifier
                .fillMaxWidth()
                .padding(4.dp),
            transitionSpec = {
                fadeIn() togetherWith fadeOut()
            }
        ) { items ->
            Column(
                verticalArrangement = if (reverse) Arrangement.BottomReversed else Arrangement.Top
            ) {
                for (i in 0 until ceil(items.size / columns.toFloat()).toInt()) {
                    Row {
                        for (j in 0 until columns) {
                            val item = items.getOrNull(i * columns + j)
                            if (item != null) {
                                GridItem(
                                    modifier = Modifier
                                        .sharedBounds(
                                            rememberSharedContentState(item.key),
                                            this@AnimatedContent,
                                        )
                                        .weight(1f)
                                        .padding(4.dp),
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
    }
}
