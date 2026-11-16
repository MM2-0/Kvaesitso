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

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.nip51Lists.BookmarkType
import com.vitorpamplona.amethyst.commons.model.nip51Lists.bookmarkSets.BookmarkSet
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.bookmark_list_articles_btn_label
import com.vitorpamplona.amethyst.commons.resources.bookmark_list_clone_btn_label
import com.vitorpamplona.amethyst.commons.resources.bookmark_list_icon_label
import com.vitorpamplona.amethyst.commons.resources.bookmark_list_posts_btn_label
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.follow_set_copy_action_btn_label
import com.vitorpamplona.amethyst.commons.resources.follow_set_copy_desc_label
import com.vitorpamplona.amethyst.commons.resources.follow_set_copy_indicator_description
import com.vitorpamplona.amethyst.commons.resources.follow_set_copy_name_label
import com.vitorpamplona.amethyst.commons.resources.follow_set_desc_modify_label
import com.vitorpamplona.amethyst.commons.resources.follow_set_empty_label2
import com.vitorpamplona.amethyst.commons.resources.follow_set_rename_btn_label
import com.vitorpamplona.amethyst.commons.resources.group_actions_dialog_title
import com.vitorpamplona.amethyst.commons.resources.quick_action_delete
import com.vitorpamplona.amethyst.commons.ui.components.ClickableBox
import com.vitorpamplona.amethyst.commons.ui.components.M3ActionDialog
import com.vitorpamplona.amethyst.commons.ui.components.M3ActionRow
import com.vitorpamplona.amethyst.commons.ui.components.M3ActionSection
import com.vitorpamplona.amethyst.commons.ui.components.SpellCheckedTextField
import com.vitorpamplona.amethyst.commons.ui.note.VerticalDotsIcon
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DoubleVertSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.Font10SP
import com.vitorpamplona.amethyst.commons.ui.theme.NoSoTinyBorders
import com.vitorpamplona.amethyst.commons.ui.theme.Size10Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size40Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size5dp
import com.vitorpamplona.amethyst.commons.ui.theme.SpacedBy2dp
import com.vitorpamplona.amethyst.commons.ui.theme.SpacedBy5dp
import com.vitorpamplona.amethyst.commons.ui.theme.StdVertSpacer

@Composable
fun BookmarkGroupItem(
    modifier: Modifier = Modifier,
    bookmarkList: BookmarkSet,
    onClick: (bookmarkItemType: BookmarkType) -> Unit,
    onRename: () -> Unit,
    onDescriptionChange: () -> Unit,
    onClone: (customName: String?, customDescription: String?) -> Unit,
    onDelete: () -> Unit,
) {
    var isExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = modifier.clickable(onClick = { isExpanded = !isExpanded }),
    ) {
        Column(
            modifier = Modifier.animateContentSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ListItem(
                headlineContent = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(bookmarkList.title, maxLines = 1, overflow = TextOverflow.Ellipsis)

                        Column(
                            modifier = NoSoTinyBorders,
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.End,
                        ) {
                            BookmarkGroupOptionsButton(
                                bookmarkGroupName = bookmarkList.title,
                                bookmarkGroupDescription = bookmarkList.description,
                                onGroupRename = onRename,
                                onGroupDescriptionChange = onDescriptionChange,
                                onGroupCloneCreate = onClone,
                                onGroupDelete = onDelete,
                            )
                        }
                    }
                },
                supportingContent = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            bookmarkList.description ?: "",
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 2,
                        )
                    }
                },
                leadingContent = {
                    Column(
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            symbol = MaterialSymbols.CollectionsBookmark,
                            contentDescription = stringRes(Res.string.bookmark_list_icon_label),
                            modifier = Size40Modifier,
                        )
                        Spacer(StdVertSpacer)
                        BookmarkMembershipStatusAndNumberDisplay(
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                            postBookmarksSize = bookmarkList.publicPostBookmarks.size + bookmarkList.privatePostBookmarks.size,
                            articleBookmarksSize = bookmarkList.publicArticleBookmarks.size + bookmarkList.privateArticleBookmarks.size,
                        )
                    }
                },
            )
            if (isExpanded) {
                BookmarkGroupActions(
                    modifier = Modifier.fillMaxWidth(),
                    openPostBookmarks = { onClick(BookmarkType.PostBookmark) },
                    openArticleBookmarks = { onClick(BookmarkType.ArticleBookmark) },
                )
            }
        }
    }
}

@Composable
private fun BookmarkGroupActions(
    modifier: Modifier = Modifier,
    openPostBookmarks: () -> Unit = {},
    openArticleBookmarks: () -> Unit = {},
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.SpaceAround,
        itemVerticalAlignment = Alignment.CenterVertically,
        maxLines = 2,
        maxItemsInEachRow = 2,
    ) {
        FilledTonalButton(
            onClick = openPostBookmarks,
        ) {
            Icon(
                symbol = MaterialSymbols.News,
                contentDescription = null,
            )
            Text(stringRes(Res.string.bookmark_list_posts_btn_label))
        }
        FilledTonalButton(
            onClick = openArticleBookmarks,
        ) {
            Icon(
                symbol = MaterialSymbols.AutoMirrored.Article,
                contentDescription = null,
            )
            Text(stringRes(Res.string.bookmark_list_articles_btn_label))
        }
    }
}

@Composable
fun BookmarkMembershipStatusAndNumberDisplay(
    modifier: Modifier = Modifier,
    postBookmarksSize: Int,
    articleBookmarksSize: Int,
) {
    Row(
        modifier = modifier.offset(y = (-5).dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = SpacedBy5dp,
    ) {
        if (postBookmarksSize <= 0 && articleBookmarksSize <= 0) {
            Text(
                text = stringRes(Res.string.follow_set_empty_label2),
                fontSize = Font10SP,
            )
        } else {
            if (postBookmarksSize > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = SpacedBy2dp,
                ) {
                    Icon(
                        symbol = MaterialSymbols.News,
                        modifier = Size10Modifier,
                        contentDescription = null,
                    )
                    Text(
                        text = postBookmarksSize.toString(),
                        fontSize = Font10SP,
                    )
                }
            }
            if (articleBookmarksSize > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = SpacedBy2dp,
                ) {
                    Icon(
                        symbol = MaterialSymbols.AutoMirrored.Article,
                        modifier = Size10Modifier,
                        contentDescription = null,
                    )
                    Text(
                        text = articleBookmarksSize.toString(),
                        fontSize = Font10SP,
                    )
                }
            }
        }
    }
}

@Composable
private fun BookmarkGroupOptionsButton(
    modifier: Modifier = Modifier,
    bookmarkGroupName: String,
    bookmarkGroupDescription: String?,
    onGroupRename: () -> Unit,
    onGroupDescriptionChange: () -> Unit,
    onGroupCloneCreate: (optionalName: String?, optionalDec: String?) -> Unit,
    onGroupDelete: () -> Unit,
) {
    val isMenuOpen = remember { mutableStateOf(false) }

    ClickableBox(
        onClick = { isMenuOpen.value = true },
    ) {
        VerticalDotsIcon()
    }

    GroupOptionsMenu(
        groupName = bookmarkGroupName,
        groupDescription = bookmarkGroupDescription,
        isExpanded = isMenuOpen.value,
        onDismiss = { isMenuOpen.value = false },
        onGroupRename = onGroupRename,
        onGroupDescriptionChange = onGroupDescriptionChange,
        onGroupClone = onGroupCloneCreate,
        onDelete = onGroupDelete,
    )
}

@Composable
private fun GroupOptionsMenu(
    modifier: Modifier = Modifier,
    isExpanded: Boolean,
    groupName: String,
    groupDescription: String?,
    onGroupRename: () -> Unit,
    onGroupDescriptionChange: () -> Unit,
    onGroupClone: (optionalNewName: String?, optionalNewDesc: String?) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isCopyDialogOpen = remember { mutableStateOf(false) }
    val optionalCloneName = remember { mutableStateOf<String?>(null) }
    val optionalCloneDescription = remember { mutableStateOf<String?>(null) }

    if (isExpanded) {
        M3ActionDialog(
            title = stringRes(Res.string.group_actions_dialog_title),
            onDismiss = onDismiss,
        ) {
            M3ActionSection {
                M3ActionRow(icon = MaterialSymbols.Edit, text = stringRes(Res.string.follow_set_rename_btn_label)) {
                    onGroupRename()
                    onDismiss()
                }
                M3ActionRow(icon = MaterialSymbols.Description, text = stringRes(Res.string.follow_set_desc_modify_label)) {
                    onGroupDescriptionChange()
                    onDismiss()
                }
                M3ActionRow(icon = MaterialSymbols.ContentCopy, text = stringRes(Res.string.follow_set_copy_action_btn_label)) {
                    isCopyDialogOpen.value = true
                    onDismiss()
                }
            }
            M3ActionSection {
                M3ActionRow(icon = MaterialSymbols.Delete, text = stringRes(Res.string.quick_action_delete), isDestructive = true) {
                    onDelete()
                    onDismiss()
                }
            }
        }
    }

    if (isCopyDialogOpen.value) {
        GroupCloneDialog(
            optionalNewName = optionalCloneName.value,
            optionalNewDesc = optionalCloneDescription.value,
            onCloneNameChange = {
                optionalCloneName.value = it
            },
            onCloneDescChange = {
                optionalCloneDescription.value = it
            },
            onCloneCreate = { name, description ->
                onGroupClone(name, description)
            },
            onDismiss = { isCopyDialogOpen.value = false },
        )
    }
}

@Composable
private fun GroupCloneDialog(
    modifier: Modifier = Modifier,
    optionalNewName: String?,
    optionalNewDesc: String?,
    onCloneNameChange: (String?) -> Unit,
    onCloneDescChange: (String?) -> Unit,
    onCloneCreate: (customName: String?, customDescription: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringRes(Res.string.bookmark_list_clone_btn_label),
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(Size5dp),
            ) {
                Text(
                    text = stringRes(Res.string.follow_set_copy_indicator_description),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Light,
                    fontStyle = FontStyle.Italic,
                )
                // For the group clone name
                TextField(
                    value = optionalNewName ?: "",
                    onValueChange = onCloneNameChange,
                    label = {
                        Text(text = stringRes(Res.string.follow_set_copy_name_label))
                    },
                )
                Spacer(modifier = DoubleVertSpacer)
                // For the group clone description
                SpellCheckedTextField(
                    value = optionalNewDesc ?: "",
                    onValueChange = onCloneDescChange,
                    label = {
                        Text(text = stringRes(Res.string.follow_set_copy_desc_label))
                    },
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onCloneCreate(optionalNewName, optionalNewDesc)
                    onDismiss()
                },
            ) {
                Text(stringRes(Res.string.follow_set_copy_action_btn_label))
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
            ) {
                Text(stringRes(Res.string.cancel))
            }
        },
    )
}
