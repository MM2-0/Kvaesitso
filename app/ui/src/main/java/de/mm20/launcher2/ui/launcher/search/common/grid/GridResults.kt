package de.mm20.launcher2.ui.launcher.search.common.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.ui.ktx.withCorners
import de.mm20.launcher2.ui.theme.transparency.transparency
import kotlin.math.ceil

fun <T : SavableSearchable> LazyGridScope.GridResults(
    key: String,
    items: List<T>,
    itemContent: @Composable (T) -> Unit,
    before: @Composable (() -> Unit)? = null,
    after: @Composable (() -> Unit)? = null,
    columns: Int,
    reverse: Boolean = false,
) {
    if (before != null) {
        item(
            key = "$key-before",
            contentType = { "$key-before" },
            span = {
                GridItemSpan(maxLineSpan)
            }
        ) {
            val isTop = !reverse || items.isEmpty() && after == null
            val isBottom = reverse || items.isEmpty() && after == null
            Box(
                modifier = Modifier
                    .padding(
                        top = if (reverse && isTop) 8.dp else 0.dp,
                        bottom = if (!reverse && isBottom) 8.dp else 0.dp,
                    )
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = MaterialTheme.transparency.surface),
                        MaterialTheme.shapes.medium.withCorners(
                            topStart = isTop,
                            topEnd = isTop,
                            bottomEnd = isBottom,
                            bottomStart = isBottom,
                        )
                    )
            ) {
                before()
            }
        }
    }

    val rows = ceil(items.size / columns.toFloat()).toInt()
    // cells at the end of the grid that are empty
    val emptySlots = rows * columns - items.size
    items(
        items.size,
        key = { "$key-${items[it].key}" },
        contentType = { key },
        span = {
            if (it == items.lastIndex) {
                GridItemSpan(maxCurrentLineSpan)
            } else {
                GridItemSpan(1)
            }
        }
    ) {
        val item = items[it]

        val isFirstRow = before == null && it < columns
        val isLastRow = after == null && it >= items.lastIndex - (items.lastIndex % columns)
        val isFirstInRow = it % columns == 0
        val isLastInRow = it % columns == columns - 1 || it == items.lastIndex
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = if (reverse && isLastRow) 8.dp else 0.dp,
                    bottom = if (!reverse && isLastRow) 8.dp else 0.dp,
                )
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = MaterialTheme.transparency.surface),
                    MaterialTheme.shapes.medium.withCorners(
                        topStart = isFirstInRow && (isFirstRow && !reverse || isLastRow && reverse),
                        topEnd = isLastInRow && (isFirstRow && !reverse || isLastRow && reverse),
                        bottomStart = isFirstInRow && (isLastRow && !reverse || isFirstRow && reverse),
                        bottomEnd = isLastInRow && (isLastRow && !reverse || isFirstRow && reverse),
                    )
                )
                .padding(
                    top = if (isFirstRow) 8.dp else 0.dp,
                    bottom = if (isLastRow) 8.dp else 0.dp,
                )
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(
                    if (it == items.lastIndex) 1f / (1f + emptySlots)
                    else 1f
                )
            ) {
                itemContent(item)
            }
        }
    }

    if (after != null) {
        item(
            key = "$key-after",
            contentType = { "$key-after" },
            span = {
                GridItemSpan(maxLineSpan)
            }
        ) {
            val isTop = reverse || items.isEmpty() && before == null
            val isBottom = !reverse || items.isEmpty() && before == null
            Box(
                modifier = Modifier
                    .padding(
                        top = if (reverse) 8.dp else 0.dp,
                        bottom = if (!reverse) 8.dp else 0.dp,
                    )
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = MaterialTheme.transparency.surface),
                        MaterialTheme.shapes.medium.withCorners(
                            topStart = isTop,
                            topEnd = isTop,
                            bottomEnd = isBottom,
                            bottomStart = isBottom,
                        )
                    )
            ) {
                after()
            }
        }
    }
}