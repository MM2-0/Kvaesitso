package de.mm20.launcher2.ui.common

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.search.data.Tag
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.launcher.sheets.LocalBottomSheetManager
import de.mm20.launcher2.ui.modifier.consumeAllScrolling

@Composable
fun FavoritesTagSelector(
    tags: List<Tag>,
    selectedTag: String?,
    editButton: Boolean,
    reverse: Boolean,
    onSelectTag: (String?) -> Unit,
    scrollState: ScrollState,
    expanded: Boolean,
    onExpand: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .padding(
                top = if (reverse) 8.dp else 4.dp,
                bottom = if (reverse) 4.dp else 8.dp,
                end = if (editButton) 8.dp else 0.dp
            )
                then
                if (editButton && expanded) Modifier.height(IntrinsicSize.Min) else Modifier,
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!expanded) {
            val canScroll by remember {
                derivedStateOf { scrollState.canScrollForward || scrollState.canScrollBackward }
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .consumeAllScrolling()
                    .horizontalScroll(scrollState)
                    .padding(end = 12.dp),
            ) {
                FilterChip(
                    modifier = Modifier.padding(start = 16.dp),
                    selected = selectedTag == null,
                    onClick = { onSelectTag(null) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Star,
                            contentDescription = null
                        )
                    },
                    label = { Text(stringResource(R.string.favorites)) }
                )
                for (tag in tags) {
                    TagChip(
                        modifier = Modifier.padding(start = 8.dp),
                        tag = tag,
                        selected = selectedTag == tag.tag,
                        onClick = { onSelectTag(tag.tag) },
                    )
                }
                if (canScroll) {
                    IconButton(
                        onClick = { onExpand(true) }) {
                        Icon(Icons.Rounded.ExpandMore, null)
                    }
                }
            }
        } else {
            FlowRow(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp, start = 16.dp),
            ) {
                FilterChip(
                    modifier = Modifier.padding(end = 8.dp),
                    selected = selectedTag == null,
                    onClick = { onSelectTag(null) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Star,
                            contentDescription = null
                        )
                    },
                    label = { Text(stringResource(R.string.favorites)) }
                )
                for (tag in tags) {
                    TagChip(
                        modifier = Modifier.padding(end = 8.dp),
                        tag = tag,
                        selected = selectedTag == tag.tag,
                        onClick = { onSelectTag(tag.tag) },
                    )
                }
            }
        }
        if (editButton || expanded) {
            Column(
                modifier = if (expanded && editButton) Modifier.fillMaxHeight() else Modifier,
                verticalArrangement = if (expanded && editButton) Arrangement.SpaceBetween else Arrangement.Center,
            ) {
                if (expanded) {
                    IconButton(onClick = { onExpand(false) }) {
                        Icon(Icons.Rounded.ExpandLess, null)
                    }
                }
                if (editButton) {
                    val sheetManager = LocalBottomSheetManager.current
                    SmallFloatingActionButton(
                        elevation = FloatingActionButtonDefaults.bottomAppBarFabElevation(),
                        onClick = { sheetManager.showEditFavoritesSheet() }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = null
                        )
                    }
                }
            }
        }
    }
}