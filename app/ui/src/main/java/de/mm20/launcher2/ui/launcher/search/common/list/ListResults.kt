package de.mm20.launcher2.ui.launcher.search.common.list

import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.ui.ktx.animateShapeAsState
import de.mm20.launcher2.ui.layout.BottomReversed
import de.mm20.launcher2.ui.theme.transparency.transparency

fun <T : SavableSearchable> LazyListScope.ListResults(
    key: String,
    items: List<T>,
    itemContent: @Composable ColumnScope.(T, Boolean, Int) -> Unit,
    before: @Composable (ColumnScope.() -> Unit)? = null,
    after: @Composable (ColumnScope.() -> Unit)? = null,
    reverse: Boolean = false,
    selectedIndex: Int = -1,
) {
    if (before != null) {
        item(
            key = "$key-before",
            contentType = { "$key-before" },
        ) {
            ListItemSurface(
                isFirst = true,
                isLast = after == null && items.isEmpty(),
                reverse = reverse,
                isBeforeExpanded = selectedIndex == 0,
            ) {
                before()
            }
        }
    }
    val rows = items.size
    items(
        items.size,
        key = {
            "$key-${items[it].key}"
        },
        contentType = { key },
    ) {
        val item = items[it]
        val showDetails = it == selectedIndex

        ListItemSurface(
            isFirst = it == 0 && before == null,
            isLast = it == rows - 1 && after == null,
            reverse = reverse,
            isExpanded = showDetails,
            isBeforeExpanded = selectedIndex - 1 == it,
            isAfterExpanded = selectedIndex >= 0 && selectedIndex + 1 == it,
        ) {
            itemContent(item, showDetails, it)
        }
    }
    if (after != null) {
        item(
            key = "$key-after",
            contentType = { "$key-after" },
        ) {
            ListItemSurface(
                isFirst = before == null && items.isEmpty(),
                isLast = true,
                reverse = reverse,
                isAfterExpanded = selectedIndex == items.lastIndex,
            ) {
                after()
            }
        }
    }
}

@Composable
fun LazyItemScope.ListItemSurface(
    isFirst: Boolean = false,
    isLast: Boolean = false,
    reverse: Boolean = false,
    isExpanded: Boolean = false,
    isBeforeExpanded: Boolean = false,
    isAfterExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val transition = updateTransition(isExpanded)
    val backgroundAlpha by transition.animateFloat {
        if (it) MaterialTheme.transparency.elevatedSurface else MaterialTheme.transparency.surface
    }
    val elevation by transition.animateDp {
        if (it && backgroundAlpha == 1f) 2.dp else 0.dp
    }


    val padding by transition.animateDp {
        if (it) 8.dp else 1.dp
    }

    val shape by animateShapeAsState(
        if (isExpanded) MaterialTheme.shapes.medium
        else if (!isFirst && !isLast && !isAfterExpanded && !isBeforeExpanded) MaterialTheme.shapes.extraSmall
        else {
            val xs = MaterialTheme.shapes.extraSmall
            val md = MaterialTheme.shapes.medium
            if (reverse) {
                xs.copy(
                    topStart = if (isLast || isBeforeExpanded) md.topStart else xs.topStart,
                    topEnd = if (isLast || isBeforeExpanded) md.topEnd else xs.topEnd,
                    bottomEnd = if (isFirst || isAfterExpanded) md.bottomEnd else xs.bottomEnd,
                    bottomStart = if (isFirst || isAfterExpanded) md.bottomStart else xs.bottomStart,
                )
            } else {
                xs.copy(
                    topStart = if (isFirst || isAfterExpanded) md.topStart else xs.topStart,
                    topEnd = if (isFirst || isAfterExpanded) md.topEnd else xs.topEnd,
                    bottomEnd = if (isLast || isBeforeExpanded) md.bottomEnd else xs.bottomEnd,
                    bottomStart = if (isLast || isBeforeExpanded) md.bottomStart else xs.bottomStart,
                )
            }
        }
    )

    val modifier = if (reverse) {
        Modifier
            .padding(
                bottom = if (!isFirst) padding else 0.dp,
                top = if (!isLast) padding else 8.dp
            )
            .shadow(
                elevation = elevation,
                shape,
                true,
            )
    } else {
        Modifier
            .padding(
                top = if (!isFirst) padding else 0.dp,
                bottom = if (!isLast) padding else 8.dp
            )
            .shadow(
                elevation = elevation,
                shape,
                true,
            )
    }

    Column(
        modifier = modifier
            .animateItem()
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(backgroundAlpha)),
        verticalArrangement = if (reverse) Arrangement.BottomReversed else Arrangement.Top
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            content()
        }
    }
}