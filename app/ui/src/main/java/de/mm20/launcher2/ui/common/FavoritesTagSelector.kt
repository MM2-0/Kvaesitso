package de.mm20.launcher2.ui.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
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
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.search.Tag
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.launcher.sheets.LocalBottomSheetManager
import de.mm20.launcher2.ui.layout.TopReversed

@Composable
fun FavoritesTagSelector(
    tags: List<Tag>,
    selectedTag: String?,
    editButton: Boolean,
    reverse: Boolean,
    onSelectTag: (String?) -> Unit,
    scrollState: ScrollState,
    compact: Boolean,
    expanded: Boolean,
    onExpand: (Boolean) -> Unit,
    showFavorites: Boolean
) {
    val sheetManager = LocalBottomSheetManager.current

    AnimatedContent(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                top = 4.dp,
                bottom = 4.dp,
                end = if (editButton) 8.dp else 0.dp
            ),
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
                        .horizontalScroll(scrollState)
                        .padding(end = 12.dp),
                ) {
                    if (showFavorites) {
                        FilterChip(
                            modifier = Modifier
                                .padding(start = 16.dp),
                            selected = selectedTag == null,
                            onClick = { onSelectTag(null) },
                            leadingIcon = if (compact) null else {
                                {
                                    Icon(
                                        painter = painterResource(R.drawable.star_20px_filled),
                                        contentDescription = null,
                                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                                    )
                                }
                            },
                            label = {
                                if (compact) {
                                    Icon(
                                        painter = painterResource(R.drawable.star_20px_filled),
                                        contentDescription = null,
                                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                                    )
                                } else {
                                    Text(stringResource(R.string.favorites))
                                }
                            }
                        )
                    }
                    for ((i, tag) in tags.withIndex()) {
                        TagChip(
                            modifier = Modifier
                                .padding(start = if (!showFavorites && i == 0) 16.dp else 8.dp),
                            tag = tag,
                            selected = selectedTag == tag.tag,
                            onClick = {
                                if (selectedTag == tag.tag && showFavorites) {
                                    onSelectTag(null)
                                } else {
                                    onSelectTag(tag.tag)
                                }
                            },
                            compact = compact,
                            onLongClick = {
                                sheetManager.showEditTagSheet(tag.tag)
                            }
                        )
                    }
                    if (canScroll) {
                        val rot by transition.animateFloat {
                            if (it == EnterExitState.Visible) 0f else 180f
                        }
                        IconButton(
                            modifier = Modifier
                                .rotate(rot),
                            onClick = { onExpand(true) }) {
                            Icon(painterResource(R.drawable.arrow_drop_down_24px), null)
                        }
                    }

                }

                if (editButton) {
                    FilledTonalIconButton(
                        onClick = { sheetManager.showEditFavoritesSheet() },
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.edit_24px),
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
                            .padding(end = 8.dp),
                        selected = selectedTag == null,
                        onClick = { onSelectTag(null) },
                        leadingIcon = if (compact) null else {
                            {
                                Icon(
                                    painter = painterResource(R.drawable.star_20px_filled),
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                                )
                            }
                        },
                        label = {
                            if (compact) {
                                Icon(
                                    painter = painterResource(R.drawable.star_20px_filled),
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                                )
                            } else {
                                Text(stringResource(R.string.favorites))
                            }
                        }
                    )
                    for (tag in tags) {
                        TagChip(
                            modifier = Modifier
                                .padding(end = 8.dp),
                            tag = tag,
                            compact = compact,
                            selected = selectedTag == tag.tag,
                            onClick = {
                                if (selectedTag == tag.tag && showFavorites) {
                                    onSelectTag(null)
                                } else {
                                    onSelectTag(tag.tag)
                                }
                            },
                            onLongClick = {
                                sheetManager.showEditTagSheet(tag.tag)
                            }
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
                            .rotate(rot),
                        onClick = { onExpand(false) }
                    ) {
                        Icon(painterResource(R.drawable.arrow_drop_up_24px), null)
                    }

                    if (editButton) {
                        FilledTonalIconButton(
                            onClick = { sheetManager.showEditFavoritesSheet() },
                            shapes = IconButtonDefaults.shapes(),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.edit_24px),
                                contentDescription = null
                            )
                        }
                    }
                }
            }
        }
    }
}

