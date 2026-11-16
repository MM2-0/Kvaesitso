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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.nip51Lists.followSets.PeopleList
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.follow_set_copy_action_btn_label
import com.vitorpamplona.amethyst.commons.resources.follow_set_copy_desc_label
import com.vitorpamplona.amethyst.commons.resources.follow_set_copy_dialog_title
import com.vitorpamplona.amethyst.commons.resources.follow_set_copy_indicator_description
import com.vitorpamplona.amethyst.commons.resources.follow_set_copy_name_label
import com.vitorpamplona.amethyst.commons.resources.follow_set_current_desc_label
import com.vitorpamplona.amethyst.commons.resources.follow_set_desc_modify_btn_label
import com.vitorpamplona.amethyst.commons.resources.follow_set_desc_modify_label
import com.vitorpamplona.amethyst.commons.resources.follow_set_edit_list_metadata
import com.vitorpamplona.amethyst.commons.resources.follow_set_empty_desc_label
import com.vitorpamplona.amethyst.commons.resources.follow_set_empty_label2
import com.vitorpamplona.amethyst.commons.resources.follow_set_icon_description
import com.vitorpamplona.amethyst.commons.resources.follow_set_rename_btn_label
import com.vitorpamplona.amethyst.commons.resources.follow_set_rename_dialog_indicator_first_part
import com.vitorpamplona.amethyst.commons.resources.follow_set_rename_dialog_indicator_second_part
import com.vitorpamplona.amethyst.commons.resources.list_management_dialog_title
import com.vitorpamplona.amethyst.commons.resources.quick_action_delete
import com.vitorpamplona.amethyst.commons.resources.rename
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
import com.vitorpamplona.amethyst.commons.ui.theme.Size50ModifierOffset10
import com.vitorpamplona.amethyst.commons.ui.theme.Size5dp
import com.vitorpamplona.amethyst.commons.ui.theme.SpacedBy2dp
import com.vitorpamplona.amethyst.commons.ui.theme.SpacedBy5dp
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonColumn

private const val PREVIEW_LIST_TITLE = "Sample List Title"
private const val PREVIEW_LIST_DESCRIPTION = "Sample List Description"
private const val PREVIEW_LIST_IMAGE = "https://some.com/image.png"

@Preview()
@Composable
private fun PeopleListItemPreview() {
    val user1: User = LocalCache.getOrCreateUser("460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c")
    val user2: User = LocalCache.getOrCreateUser("ca89cb11f1c75d5b6622268ff43d2288ea8b2cb5b9aa996ff9ff704fc904b78b")
    val user3: User = LocalCache.getOrCreateUser("7eb29c126b3628077e2e3d863b917a56b74293aa9d8a9abc26a40ba3f2866baf")

    val samplePeopleList1 =
        PeopleList(
            identifierTag = "00001-2222",
            title = "Sample List Title, Very long title, very very very long",
            description = PREVIEW_LIST_DESCRIPTION,
            image = PREVIEW_LIST_IMAGE,
            emptySet(),
            emptySet(),
        )

    val samplePeopleList2 =
        PeopleList(
            identifierTag = "00001-2223",
            title = PREVIEW_LIST_TITLE,
            description = PREVIEW_LIST_DESCRIPTION,
            image = PREVIEW_LIST_IMAGE,
            setOf(user1, user3),
            emptySet(),
        )

    val samplePeopleList3 =
        PeopleList(
            identifierTag = "00001-2224",
            title = PREVIEW_LIST_TITLE,
            description = PREVIEW_LIST_DESCRIPTION,
            image = PREVIEW_LIST_IMAGE,
            emptySet(),
            setOf(user1, user3),
        )

    val samplePeopleList4 =
        PeopleList(
            identifierTag = "00001-2225",
            title = PREVIEW_LIST_TITLE,
            description = PREVIEW_LIST_DESCRIPTION,
            image = PREVIEW_LIST_IMAGE,
            setOf(user3),
            setOf(user1, user2, user3),
        )

    ThemeComparisonColumn {
        Column {
            PeopleListItem(
                modifier = Modifier,
                peopleList = samplePeopleList1,
                onClick = {},
                onEditMetadata = {},
                onClone = { _, _ -> },
                onDelete = {},
            )
            PeopleListItem(
                modifier = Modifier,
                peopleList = samplePeopleList2,
                onClick = {},
                onEditMetadata = {},
                onClone = { _, _ -> },
                onDelete = {},
            )
            PeopleListItem(
                modifier = Modifier,
                peopleList = samplePeopleList3,
                onClick = {},
                onEditMetadata = {},
                onClone = { _, _ -> },
                onDelete = {},
            )
            PeopleListItem(
                modifier = Modifier,
                peopleList = samplePeopleList4,
                onClick = {},
                onEditMetadata = {},
                onClone = { _, _ -> },
                onDelete = {},
            )
        }
    }
}

@Composable
fun PeopleListItem(
    modifier: Modifier = Modifier,
    peopleList: PeopleList,
    onClick: () -> Unit,
    onEditMetadata: () -> Unit,
    onClone: (customName: String?, customDescription: String?) -> Unit,
    onDelete: () -> Unit,
) {
    ListItem(
        modifier = modifier.clickable(onClick = onClick),
        headlineContent = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = peopleList.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Column(
                    modifier = NoSoTinyBorders,
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.End,
                ) {
                    PeopleListOptionsButton(
                        onListEditMetadata = onEditMetadata,
                        onListCloneCreate = onClone,
                        onListDelete = onDelete,
                    )
                }
            }
        },
        supportingContent = {
            Text(
                peopleList.description ?: "",
                overflow = TextOverflow.Ellipsis,
                maxLines = 2,
            )
        },
        leadingContent = {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    symbol = MaterialSymbols.Groups,
                    contentDescription = stringRes(Res.string.follow_set_icon_description),
                    modifier = Size50ModifierOffset10,
                )
                DisplayParticipantNumberAndStatus(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    privateMembersSize = peopleList.privateMembersList.size,
                    publicMembersSize = peopleList.publicMembersList.size,
                )
            }
        },
    )
}

@Composable
fun DisplayParticipantNumberAndStatus(
    modifier: Modifier,
    privateMembersSize: Int,
    publicMembersSize: Int,
) {
    Row(
        modifier = modifier.offset(y = (-5).dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = SpacedBy5dp,
    ) {
        if (privateMembersSize <= 0 && publicMembersSize <= 0) {
            Text(
                text = stringRes(Res.string.follow_set_empty_label2),
                fontSize = Font10SP,
            )
        } else {
            if (privateMembersSize > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = SpacedBy2dp,
                ) {
                    Icon(
                        symbol = MaterialSymbols.Lock,
                        modifier = Size10Modifier,
                        contentDescription = null,
                    )
                    Text(
                        text = privateMembersSize.toString(),
                        fontSize = Font10SP,
                    )
                }
            }
            if (publicMembersSize > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = SpacedBy2dp,
                ) {
                    Icon(
                        symbol = MaterialSymbols.Public,
                        modifier = Size10Modifier,
                        contentDescription = null,
                    )
                    Text(
                        text = publicMembersSize.toString(),
                        fontSize = Font10SP,
                    )
                }
            }
        }
    }
}

@Composable
private fun PeopleListOptionsButton(
    modifier: Modifier = Modifier,
    onListEditMetadata: () -> Unit,
    onListCloneCreate: (optionalName: String?, optionalDec: String?) -> Unit,
    onListDelete: () -> Unit,
) {
    val isMenuOpen = remember { mutableStateOf(false) }

    ClickableBox(
        onClick = { isMenuOpen.value = true },
    ) {
        VerticalDotsIcon()
    }

    ListOptionsMenu(
        isExpanded = isMenuOpen.value,
        onListEditMetadata = onListEditMetadata,
        onListClone = onListCloneCreate,
        onDelete = onListDelete,
        onDismiss = { isMenuOpen.value = false },
    )
}

@Composable
private fun ListOptionsMenu(
    isExpanded: Boolean,
    onListEditMetadata: () -> Unit,
    onListClone: (optionalNewName: String?, optionalNewDesc: String?) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isCopyDialogOpen = remember { mutableStateOf(false) }
    val optionalCloneName = remember { mutableStateOf<String?>(null) }
    val optionalCloneDescription = remember { mutableStateOf<String?>(null) }

    if (isExpanded) {
        M3ActionDialog(
            title = stringRes(Res.string.list_management_dialog_title),
            onDismiss = onDismiss,
        ) {
            M3ActionSection {
                M3ActionRow(icon = MaterialSymbols.Edit, text = stringRes(Res.string.follow_set_edit_list_metadata)) {
                    onListEditMetadata()
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
        ListCloneDialog(
            optionalNewName = optionalCloneName.value,
            optionalNewDesc = optionalCloneDescription.value,
            onCloneNameChange = {
                optionalCloneName.value = it
            },
            onCloneDescChange = {
                optionalCloneDescription.value = it
            },
            onCloneCreate = { _, _ ->
                onListClone(optionalCloneName.value, optionalCloneDescription.value)
            },
            onDismiss = { isCopyDialogOpen.value = false },
        )
    }
}

@Composable
private fun ListRenameDialog(
    modifier: Modifier = Modifier,
    currentName: String,
    newName: String,
    onStringRenameChange: (String) -> Unit,
    onDismissDialog: () -> Unit,
    onListRename: (String) -> Unit,
) {
    val renameIndicator =
        buildAnnotatedString {
            append(stringRes(Res.string.follow_set_rename_dialog_indicator_first_part) + " ")
            withStyle(
                SpanStyle(
                    fontWeight = FontWeight.Bold,
                    fontStyle = FontStyle.Normal,
                    fontSize = 15.sp,
                ),
            ) {
                append("\"" + currentName + "\"")
            }
            append(" " + stringRes(Res.string.follow_set_rename_dialog_indicator_second_part))
        }

    AlertDialog(
        onDismissRequest = onDismissDialog,
        title = {
            Text(text = stringRes(Res.string.follow_set_rename_btn_label))
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(Size5dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = renameIndicator,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Light,
                    fontStyle = FontStyle.Italic,
                )
                TextField(
                    value = newName,
                    onValueChange = onStringRenameChange,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onListRename(newName)
                    onDismissDialog()
                },
            ) { Text(text = stringRes(Res.string.rename)) }
        },
        dismissButton = {
            Button(onClick = onDismissDialog) { Text(text = stringRes(Res.string.cancel)) }
        },
    )
}

@Composable
private fun ListModifyDescriptionDialog(
    modifier: Modifier = Modifier,
    currentDescription: String?,
    onDismissDialog: () -> Unit,
    onModifyDescription: (String) -> Unit,
) {
    val updatedDescription = remember { mutableStateOf("") }

    val modifyIndicatorLabel =
        if (currentDescription == null) {
            stringRes(Res.string.follow_set_empty_desc_label)
        } else {
            buildAnnotatedString {
                append(stringRes(Res.string.follow_set_current_desc_label) + " ")
                withStyle(
                    SpanStyle(
                        fontWeight = FontWeight.Bold,
                        fontStyle = FontStyle.Normal,
                        fontSize = 15.sp,
                    ),
                ) {
                    append("\"" + currentDescription + "\"")
                }
            }.text
        }

    AlertDialog(
        onDismissRequest = onDismissDialog,
        title = {
            Text(text = stringRes(Res.string.follow_set_desc_modify_label))
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(Size5dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = modifyIndicatorLabel,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Light,
                    fontStyle = FontStyle.Italic,
                )
                SpellCheckedTextField(
                    value = updatedDescription.value,
                    onValueChange = { updatedDescription.value = it },
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onModifyDescription(updatedDescription.value)
                    onDismissDialog()
                },
            ) { Text(text = stringRes(Res.string.follow_set_desc_modify_btn_label)) }
        },
        dismissButton = {
            Button(onClick = onDismissDialog) { Text(text = stringRes(Res.string.cancel)) }
        },
    )
}

@Composable
private fun ListCloneDialog(
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
                    text = stringRes(Res.string.follow_set_copy_dialog_title),
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
                // For the set clone name
                TextField(
                    value = optionalNewName ?: "",
                    onValueChange = onCloneNameChange,
                    label = {
                        Text(text = stringRes(Res.string.follow_set_copy_name_label))
                    },
                )
                Spacer(modifier = DoubleVertSpacer)
                // For the set clone description
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
