package de.mm20.launcher2.ui.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.search.data.Tag
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.launcher.sheets.LocalBottomSheetManager
import de.mm20.launcher2.ui.layout.TopReversed
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
    val sheetManager = LocalBottomSheetManager.current

    SharedTransitionLayout(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                top = if (reverse) 8.dp else 4.dp,
                bottom = if (reverse) 4.dp else 8.dp,
                end = if (editButton) 8.dp else 0.dp
            ),
    ) {
        AnimatedContent(
            targetState = expanded,
        ) {
            if (!it) {
                val canScroll by remember {
                    derivedStateOf { scrollState.canScrollForward || scrollState.canScrollBackward }
                }
                Row {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .consumeAllScrolling()
                            .horizontalScroll(scrollState)
                            .padding(end = 12.dp),
                    ) {
                        FilterChip(
                            modifier = Modifier
                                .padding(start = 16.dp)
                                .sharedBounds(
                                    rememberSharedContentState("favorites"),
                                    this@AnimatedContent
                                ),
                            selected = selectedTag == null,
                            onClick = { onSelectTag(null) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Star,
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                                )
                            },
                            label = { Text(stringResource(R.string.favorites)) }
                        )
                        for (tag in tags) {
                            TagChip(
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .sharedBounds(
                                        rememberSharedContentState("tag-${tag.tag}"),
                                        this@AnimatedContent
                                    ),
                                tag = tag,
                                selected = selectedTag == tag.tag,
                                onClick = { onSelectTag(tag.tag) },
                            )
                        }
                        if (canScroll) {
                            val rot by transition.animateFloat {
                                if (it == EnterExitState.Visible) 0f else 180f
                            }
                            IconButton(
                                modifier = Modifier
                                    .sharedElement(
                                        rememberSharedContentState("expandButton"),
                                        this@AnimatedContent
                                    )
                                    .rotate(rot),
                                onClick = { onExpand(true) }) {
                                Icon(Icons.Rounded.ExpandMore, null)
                            }
                        }

                    }

                    if (editButton) {
                        SmallFloatingActionButton(
                            modifier = Modifier.sharedBounds(
                                rememberSharedContentState("editButton"),
                                this@AnimatedContent
                            ),
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
            } else {
                Row(
                    verticalAlignment = if (reverse) Alignment.Top else Alignment.Bottom,
                ) {
                    FlowRow(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp, start = 16.dp),
                    ) {
                        FilterChip(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .sharedBounds(
                                    rememberSharedContentState("favorites"),
                                    this@AnimatedContent
                                ),
                            selected = selectedTag == null,
                            onClick = { onSelectTag(null) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Star,
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                                )
                            },
                            label = { Text(stringResource(R.string.favorites)) }
                        )
                        for (tag in tags) {
                            TagChip(
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .sharedBounds(
                                        rememberSharedContentState("tag-${tag.tag}"),
                                        this@AnimatedContent
                                    ),
                                tag = tag,
                                selected = selectedTag == tag.tag,
                                onClick = { onSelectTag(tag.tag) },
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxHeight(),
                        verticalArrangement = if (reverse) Arrangement.TopReversed else Arrangement.Bottom,
                    ) {
                        val rot by transition.animateFloat {
                            if (it == EnterExitState.Visible) 0f else 180f
                        }
                        IconButton(
                            modifier = Modifier
                                .sharedElement(
                                    rememberSharedContentState("expandButton"),
                                    this@AnimatedContent
                                )
                                .rotate(rot),
                            onClick = { onExpand(false) }
                        ) {
                            Icon(Icons.Rounded.ExpandLess, null)
                        }

                        if (editButton) {
                            SmallFloatingActionButton(
                                modifier = Modifier.sharedBounds(
                                    rememberSharedContentState("editButton"),
                                    this@AnimatedContent
                                ),
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
    }
}
