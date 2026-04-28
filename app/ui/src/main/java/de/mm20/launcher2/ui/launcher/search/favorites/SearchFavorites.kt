package de.mm20.launcher2.ui.launcher.search.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.animateContentSize
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.Tag
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.Banner
import de.mm20.launcher2.ui.launcher.search.common.grid.SearchResultGrid
import de.mm20.launcher2.ui.layout.BottomReversed
import de.mm20.launcher2.ui.theme.transparency.transparency
import kotlinx.coroutines.launch

fun LazyListScope.SearchFavorites(
    favorites: List<SavableSearchable>,
    pinnedTags: List<Tag>,
    selectedTag: String?,
    onSelectTag: (String?) -> Unit,
    reverse: Boolean,
) {
    item(
        key = "favorites",
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            Column(
                modifier = Modifier
                    .padding(
                        top = if (reverse) 8.dp else 0.dp,
                        bottom = if (reverse) 0.dp else 8.dp,
                    ),
                verticalArrangement = if (reverse) Arrangement.BottomReversed else Arrangement.Top
            ) {
                Column(
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.surface.copy(
                                MaterialTheme.transparency.surface
                            ),
                            MaterialTheme.shapes.medium
                        )
                        .padding(vertical = 4.dp),
                    verticalArrangement = if (reverse) Arrangement.BottomReversed else Arrangement.Top
                ) {
                    FavoritesTopTagCarousel(
                        tags = pinnedTags,
                        selectedTag = selectedTag,
                        onSelectTag = onSelectTag,
                    )
                    if (favorites.isNotEmpty()) {
                        SearchResultGrid(favorites, transitionKey = selectedTag, reverse = reverse)
                    } else {
                        Banner(
                            modifier = Modifier.padding(16.dp),
                            text = stringResource(
                                if (selectedTag == null) R.string.favorites_empty else R.string.favorites_empty_tag
                            ),
                            icon = if (selectedTag == null) R.drawable.star_24px else R.drawable.tag_24px,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoritesTopTagCarousel(
    tags: List<Tag>,
    selectedTag: String?,
    onSelectTag: (String?) -> Unit,
) {
    data class TopTagItem(
        val tag: String?,
        val label: String,
        val isFavorites: Boolean,
    )

    val topItems = buildList<TopTagItem> {
        add(TopTagItem(tag = null, label = stringResource(R.string.favorites), isFavorites = true))
        tags.forEach { add(TopTagItem(tag = it.tag, label = it.label, isFavorites = false)) }
    }
    val listState = rememberLazyListState()
    val selectedIndex = topItems.indexOfFirst { it.tag == selectedTag }.takeIf { it >= 0 } ?: 0
    val scope = rememberCoroutineScope()

    LaunchedEffect(selectedIndex, topItems.size) {
        listState.animateScrollToItem(selectedIndex)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            state = listState,
            flingBehavior = rememberSnapFlingBehavior(listState),
        ) {
            items(topItems, key = { it.tag ?: "__favorites__" }) { item ->
                val selected = item.tag == selectedTag
                ContextTagPill(
                    label = item.label,
                    iconRes = if (item.isFavorites) R.drawable.star_20px_filled else R.drawable.tag_20px,
                    selected = selected,
                    dimmed = !selected,
                    onClick = {
                        onSelectTag(item.tag)
                        val itemIndex = topItems.indexOfFirst { it.tag == item.tag }.takeIf { it >= 0 } ?: 0
                        scope.launch {
                            listState.animateScrollToItem(itemIndex)
                        }
                    },
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun ContextTagPill(
    label: String,
    iconRes: Int,
    selected: Boolean,
    dimmed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val selectedTextStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
    val unselectedTextStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium)
    val horizontalPadding: Dp = 12.dp
    val iconSize: Dp = 16.dp
    val iconTextGap: Dp = 8.dp

    val maxTextWidthPx = maxOf(
        textMeasurer.measure(label, style = selectedTextStyle).size.width,
        textMeasurer.measure(label, style = unselectedTextStyle).size.width,
    )
    val chipWidth = with(density) {
        (horizontalPadding * 2) + iconSize + iconTextGap + maxTextWidthPx.toDp()
    }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .animateContentSize()
            .width(chipWidth)
            .height(40.dp)
            .alpha(if (dimmed) 0.76f else 1f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = MaterialTheme.transparency.surface * 0.28f),
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
            )
            Text(
                text = label,
                style = if (selected) selectedTextStyle else unselectedTextStyle,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.84f),
                modifier = Modifier
                    .padding(start = 8.dp)
                    .scale(if (selected) 1.12f else 1f)
                    .alpha(if (selected) 1f else 0.72f),
            )
        }
    }
}
