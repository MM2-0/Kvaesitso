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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.tooling.preview.Preview
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.add_member_dialog_title
import com.vitorpamplona.amethyst.commons.resources.add_user_to_the_list
import com.vitorpamplona.amethyst.commons.resources.follow_set_absence_indicator2
import com.vitorpamplona.amethyst.commons.resources.follow_set_icon_description
import com.vitorpamplona.amethyst.commons.resources.follow_set_private_member_add_label
import com.vitorpamplona.amethyst.commons.resources.follow_set_private_presence_indicator
import com.vitorpamplona.amethyst.commons.resources.follow_set_public_member_add_label
import com.vitorpamplona.amethyst.commons.resources.follow_set_public_presence_indicator
import com.vitorpamplona.amethyst.commons.resources.remove_user_from_the_list
import com.vitorpamplona.amethyst.commons.ui.components.M3ActionDialog
import com.vitorpamplona.amethyst.commons.ui.components.M3ActionRow
import com.vitorpamplona.amethyst.commons.ui.components.M3ActionSection
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.HalfHalfVertPadding
import com.vitorpamplona.amethyst.commons.ui.theme.Size15Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size50ModifierOffset10
import com.vitorpamplona.amethyst.commons.ui.theme.SpacedBy5dp
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonColumn

@Preview
@Composable
fun PeopleListAndUserMemberPreview() {
    ThemeComparisonColumn {
        PeopleListAndUserItem(
            modifier = Modifier.fillMaxWidth(),
            listHeader = "list title",
            userName = "User",
            userIsPrivateMember = true,
            userIsPublicMember = true,
            privateMemberSize = 3,
            publicMemberSize = 2,
            onAddUserToList = {},
            onClick = {},
            onRemoveUser = {},
        )
    }
}

@Preview
@Composable
fun PeopleListAndUserNotMemberPreview() {
    ThemeComparisonColumn {
        PeopleListAndUserItem(
            modifier = Modifier.fillMaxWidth(),
            listHeader = "list title",
            userName = "User",
            userIsPrivateMember = false,
            userIsPublicMember = false,
            privateMemberSize = 3,
            publicMemberSize = 2,
            onAddUserToList = {},
            onClick = {},
            onRemoveUser = {},
        )
    }
}

@Composable
fun PeopleListAndUserItem(
    modifier: Modifier = Modifier,
    listHeader: String,
    userName: String,
    userIsPrivateMember: Boolean,
    userIsPublicMember: Boolean,
    publicMemberSize: Int,
    privateMemberSize: Int,
    onClick: () -> Unit,
    onAddUserToList: (shouldBePrivateMember: Boolean) -> Unit,
    onRemoveUser: () -> Unit,
) {
    ListItem(
        modifier = modifier.clickable(onClick = onClick),
        headlineContent = {
            Text(
                text = listHeader,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            UserStatusInList(userName, userIsPrivateMember, userIsPublicMember)
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
                    privateMembersSize = privateMemberSize,
                    publicMembersSize = publicMemberSize,
                )
            }
        },
        trailingContent = {
            val isUserInList = userIsPrivateMember || userIsPublicMember
            UserAdditionOptions(isUserInList, onAddUserToList, onRemoveUser)
        },
    )
}

@Composable
private fun UserStatusInList(
    userName: String,
    userIsPrivateMember: Boolean,
    userIsPublicMember: Boolean,
) {
    Row(
        modifier = HalfHalfVertPadding,
        horizontalArrangement = SpacedBy5dp,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val text =
            if (userIsPublicMember) {
                stringRes(Res.string.follow_set_public_presence_indicator, userName)
            } else if (userIsPrivateMember) {
                stringRes(Res.string.follow_set_private_presence_indicator, userName)
            } else {
                stringRes(Res.string.follow_set_absence_indicator2, userName)
            }

        val icon =
            if (userIsPublicMember) {
                MaterialSymbols.Public
            } else if (userIsPrivateMember) {
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
private fun UserAdditionOptions(
    isUserInList: Boolean,
    onAddUserToList: (asPrivateMember: Boolean) -> Unit,
    onRemoveUser: () -> Unit,
) {
    val isUserAddTapped = remember { mutableStateOf(false) }

    if (isUserAddTapped.value) {
        M3ActionDialog(
            title = stringRes(Res.string.add_member_dialog_title),
            onDismiss = { isUserAddTapped.value = false },
        ) {
            M3ActionSection {
                M3ActionRow(
                    icon = MaterialSymbols.PersonAdd,
                    text = stringRes(Res.string.follow_set_public_member_add_label),
                ) {
                    onAddUserToList(false)
                    isUserAddTapped.value = false
                }
                M3ActionRow(
                    icon = MaterialSymbols.Lock,
                    text = stringRes(Res.string.follow_set_private_member_add_label),
                ) {
                    onAddUserToList(true)
                    isUserAddTapped.value = false
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
                if (isUserInList) {
                    onRemoveUser()
                } else {
                    isUserAddTapped.value = true
                }
            },
            modifier =
                Modifier
                    .background(
                        color =
                            if (isUserInList) {
                                MaterialTheme.colorScheme.errorContainer
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        shape = RoundedCornerShape(percent = 80),
                    ),
        ) {
            if (isUserInList) {
                Icon(
                    symbol = MaterialSymbols.PersonRemove,
                    contentDescription = stringRes(Res.string.remove_user_from_the_list),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            } else {
                Icon(
                    symbol = MaterialSymbols.PersonAdd,
                    contentDescription = stringRes(Res.string.add_user_to_the_list),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}
