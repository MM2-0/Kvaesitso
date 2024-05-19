package de.mm20.launcher2.ui.launcher.search.common.list

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.ui.ktx.animateCorners
import de.mm20.launcher2.ui.layout.BottomReversed
import de.mm20.launcher2.ui.locals.LocalCardStyle

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
    ) {
        val item = items[it]
        val showDetails = it == selectedIndex

        ListItemSurface(
            isFirst = it == 0 && before == null,
            isLast = it == rows - 1 && after == null,
            reverse = reverse,
            isExpanded = showDetails,
            isBeforeExpanded = selectedIndex - 1 == it,
            isAfterExpanded = selectedIndex + 1 == it,
        ) {
            itemContent(item, showDetails, it)
        }
    }
    if (after != null) {
        item(
            key = "$key-after",
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
    val elevation by transition.animateDp {
        if (it) 2.dp else 0.dp
    }
    val backgroundAlpha by transition.animateFloat {
        if (it) 1f else LocalCardStyle.current.opacity
    }

    val padding by transition.animateDp {
        if (it) 8.dp else 0.dp
    }

    val modifier = if (reverse) {
        Modifier
            .padding(
                bottom = if (!isFirst) padding else 0.dp,
                top = if (!isLast) padding else 8.dp
            )
            .shadow(
                elevation = elevation,
                MaterialTheme.shapes.medium.animateCorners(
                    bottomStart = isFirst || isExpanded || isAfterExpanded,
                    bottomEnd = isFirst || isExpanded || isAfterExpanded,
                    topEnd = isLast || isExpanded || isBeforeExpanded,
                    topStart = isLast || isExpanded || isBeforeExpanded,
                ),
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
                MaterialTheme.shapes.medium.animateCorners(
                    topStart = isFirst || isExpanded || isAfterExpanded,
                    topEnd = isFirst || isExpanded || isAfterExpanded,
                    bottomEnd = isLast || isExpanded || isBeforeExpanded,
                    bottomStart = isLast || isExpanded || isBeforeExpanded,
                ),
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
        AnimatedVisibility(!isFirst && !isExpanded && !isAfterExpanded) {
            HorizontalDivider()
        }
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            content()
        }
    }
}