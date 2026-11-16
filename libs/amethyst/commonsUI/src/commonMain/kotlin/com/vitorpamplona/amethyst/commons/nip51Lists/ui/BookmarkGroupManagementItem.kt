/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.commons.nip51Lists.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.add_bookmark_dialog_title
import com.vitorpamplona.amethyst.commons.resources.bookmark_absence_indicator
import com.vitorpamplona.amethyst.commons.resources.bookmark_add_action_desc
import com.vitorpamplona.amethyst.commons.resources.bookmark_list_icon_label
import com.vitorpamplona.amethyst.commons.resources.bookmark_remove_action_desc
import com.vitorpamplona.amethyst.commons.resources.private_bookmark_add_action_label
import com.vitorpamplona.amethyst.commons.resources.private_bookmark_presence_indicator
import com.vitorpamplona.amethyst.commons.resources.public_bookmark_add_action_label
import com.vitorpamplona.amethyst.commons.resources.public_bookmark_presence_indicator
import com.vitorpamplona.amethyst.commons.ui.components.M3ActionDialog
import com.vitorpamplona.amethyst.commons.ui.components.M3ActionRow
import com.vitorpamplona.amethyst.commons.ui.components.M3ActionSection
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.HalfHalfVertPadding
import com.vitorpamplona.amethyst.commons.ui.theme.Size15Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size50Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.SpacedBy5dp
import com.vitorpamplona.amethyst.commons.ui.theme.StdVertSpacer

@Composable
fun BookmarkGroupManagementItem(
    modifier: Modifier = Modifier,
    listTitle: String,
    isPrivateMemberBookmark: Boolean,
    isPublicMemberBookmark: Boolean,
    totalPostBookmarkSize: Int,
    totalArticleBookmarkSize: Int,
    onClick: () -> Unit,
    onAddBookmarkToGroup: (shouldBookmarkBePrivate: Boolean) -> Unit,
    onRemoveBookmarkFromGroup: () -> Unit,
) {
    ListItem(
        modifier = modifier.clickable(onClick = onClick),
        headlineContent = {
            Text(
                text = listTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            BookmarkStatusInList(isPublicMemberBookmark, isPrivateMemberBookmark)
        },
        leadingContent = {
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    symbol = MaterialSymbols.CollectionsBookmark,
                    contentDescription = stringRes(Res.string.bookmark_list_icon_label),
                    modifier = Size50Modifier,
                )
                Spacer(StdVertSpacer)
                BookmarkMembershipStatusAndNumberDisplay(
                    postBookmarksSize = totalPostBookmarkSize,
                    articleBookmarksSize = totalArticleBookmarkSize,
                )
            }
        },
        trailingContent = {
            val isBookmarkInGroup = isPrivateMemberBookmark || isPublicMemberBookmark
            BookmarkManagementOptions(
                isBookmarkInList = isBookmarkInGroup,
                onAddBookmark = onAddBookmarkToGroup,
                onRemoveBookmark = onRemoveBookmarkFromGroup,
            )
        },
    )
}

@Composable
fun BookmarkStatusInList(
    isPublicMemberBookmark: Boolean,
    isPrivateMemberBookmark: Boolean,
) {
    Row(
        modifier = HalfHalfVertPadding,
        horizontalArrangement = SpacedBy5dp,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val text =
            if (isPublicMemberBookmark) {
                stringRes(Res.string.public_bookmark_presence_indicator)
            } else if (isPrivateMemberBookmark) {
                stringRes(Res.string.private_bookmark_presence_indicator)
            } else {
                stringRes(Res.string.bookmark_absence_indicator)
            }

        val icon =
            if (isPublicMemberBookmark) {
                MaterialSymbols.Public
            } else if (isPrivateMemberBookmark) {
                MaterialSymbols.Lock
            } else {
                MaterialSymbols.RemoveCircleOutline
            }

        Icon(
            symbol = icon,
            contentDescription = text,
            modifier = Size15Modifier,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = text,
            overflow = TextOverflow.MiddleEllipsis,
            maxLines = 1,
        )
    }
}

@Composable
fun BookmarkManagementOptions(
    isBookmarkInList: Boolean,
    onAddBookmark: (shouldBePrivate: Boolean) -> Unit,
    onRemoveBookmark: () -> Unit,
) {
    val isBookmarkAddTapped = remember { mutableStateOf(false) }

    if (isBookmarkAddTapped.value) {
        M3ActionDialog(
            title = stringRes(Res.string.add_bookmark_dialog_title),
            onDismiss = { isBookmarkAddTapped.value = false },
        ) {
            M3ActionSection {
                M3ActionRow(
                    icon = MaterialSymbols.BookmarkAdd,
                    text = stringRes(Res.string.public_bookmark_add_action_label),
                ) {
                    onAddBookmark(false)
                    isBookmarkAddTapped.value = false
                }
                M3ActionRow(
                    icon = MaterialSymbols.Lock,
                    text = stringRes(Res.string.private_bookmark_add_action_label),
                ) {
                    onAddBookmark(true)
                    isBookmarkAddTapped.value = false
                }
            }
        }
    }

    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconButton(
            onClick = {
                if (isBookmarkInList) {
                    onRemoveBookmark()
                } else {
                    isBookmarkAddTapped.value = true
                }
            },
            modifier =
                Modifier
                    .background(
                        color =
                            if (isBookmarkInList) {
                                MaterialTheme.colorScheme.errorContainer
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        shape = RoundedCornerShape(percent = 80),
                    ),
        ) {
            if (isBookmarkInList) {
                Icon(
                    symbol = MaterialSymbols.BookmarkRemove,
                    contentDescription = stringRes(Res.string.bookmark_remove_action_desc),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            } else {
                Icon(
                    symbol = MaterialSymbols.BookmarkAdd,
                    contentDescription = stringRes(Res.string.bookmark_add_action_desc),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}
