package de.mm20.launcher2.ui.launcher.search.common.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.ui.ktx.withCorners
import de.mm20.launcher2.ui.theme.transparency.transparency
import kotlin.math.ceil

fun <T : SavableSearchable> LazyListScope.GridResults(
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
    items(
        rows,
        key = {
            "$key-$it"
        },
        contentType = { key }
    ) {

        val isFirst = it == 0 && before == null
        val isLast = it == rows - 1 && after == null
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = if (reverse && isLast) 8.dp else 0.dp,
                    bottom = if (!reverse && isLast) 8.dp else 0.dp,
                )
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = MaterialTheme.transparency.surface),
                    MaterialTheme.shapes.medium.withCorners(
                        topStart = isFirst && !reverse || isLast && reverse,
                        topEnd = isFirst && !reverse || isLast && reverse,
                        bottomEnd = isLast && !reverse || isFirst && reverse,
                        bottomStart = isLast && !reverse || isFirst && reverse,
                    )
                )
                .padding(
                    top = if (it == 0) 8.dp else 0.dp,
                    bottom = if (it == rows - 1) 8.dp else 0.dp,
                    start = if (columns == 1) 0.dp else 4.dp,
                    end = if (columns == 1) 0.dp else 4.dp,
                )
        ) {
            Row {
                for (i in 0 until columns) {
                    val item = items.getOrNull(it * columns + i)
                    if (item != null) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                        ) {
                            itemContent(item)
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }

                }
            }
        }
    }

    if (after != null) {
        item(
            key = "$key-after",
            contentType = { "$key-after" },
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