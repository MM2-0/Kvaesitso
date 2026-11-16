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
package com.vitorpamplona.amethyst.commons.nip43RelayMembers.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.relay_join_request
import com.vitorpamplona.amethyst.commons.resources.relay_leave_request
import com.vitorpamplona.amethyst.commons.resources.relay_member_added
import com.vitorpamplona.amethyst.commons.resources.relay_member_removed
import com.vitorpamplona.amethyst.commons.resources.relay_members_added
import com.vitorpamplona.amethyst.commons.resources.relay_members_count
import com.vitorpamplona.amethyst.commons.resources.relay_members_removed
import com.vitorpamplona.amethyst.commons.resources.relay_membership_list
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.quartz.nip43RelayMembers.addMember.RelayAddMemberEvent
import com.vitorpamplona.quartz.nip43RelayMembers.list.RelayMembershipListEvent
import com.vitorpamplona.quartz.nip43RelayMembers.removeMember.RelayRemoveMemberEvent
import com.vitorpamplona.quartz.nip43RelayMembers.roles.RelayRole

/** NIP-43 kind 13534: the relay's current member list, shown as a count. */
@Composable
fun RelayMembershipListCard(event: RelayMembershipListEvent) {
    val memberCount = remember(event) { event.members().size }

    RelayMemberEventCard(
        icon = MaterialSymbols.People,
        title = stringRes(Res.string.relay_membership_list),
        subtitle = stringRes(Res.string.relay_members_count, memberCount),
    )
}

/** NIP-43 kind 8000: members the relay added. */
@Composable
fun RelayAddMemberCard(event: RelayAddMemberEvent) {
    val memberKeys = remember(event) { event.memberPubKeys() }
    val title =
        if (memberKeys.size == 1) {
            stringRes(Res.string.relay_member_added)
        } else {
            stringRes(Res.string.relay_members_added, memberKeys.size)
        }
    val subtitle = remember(memberKeys) { memberKeys.joinToString(", ") { it.take(16) + "..." } }

    RelayMemberEventCard(
        icon = MaterialSymbols.PersonAdd,
        title = title,
        subtitle = subtitle,
    )
}

/** NIP-43 kind 8001: members the relay removed. */
@Composable
fun RelayRemoveMemberCard(event: RelayRemoveMemberEvent) {
    val memberKeys = remember(event) { event.memberPubKeys() }
    val title =
        if (memberKeys.size == 1) {
            stringRes(Res.string.relay_member_removed)
        } else {
            stringRes(Res.string.relay_members_removed, memberKeys.size)
        }
    val subtitle = remember(memberKeys) { memberKeys.joinToString(", ") { it.take(16) + "..." } }

    RelayMemberEventCard(
        icon = MaterialSymbols.PersonRemove,
        title = title,
        subtitle = subtitle,
    )
}

/** NIP-43 kind 28934: a request to join the relay. */
@Composable
fun RelayJoinRequestCard() {
    RelayMemberEventCard(
        icon = MaterialSymbols.PersonAdd,
        title = stringRes(Res.string.relay_join_request),
        subtitle = null,
    )
}

/** NIP-43 kind 28936: a request to leave the relay. */
@Composable
fun RelayLeaveRequestCard() {
    RelayMemberEventCard(
        icon = MaterialSymbols.AutoMirrored.ExitToApp,
        title = stringRes(Res.string.relay_leave_request),
        subtitle = null,
    )
}

/**
 * The NIP-43 roles (kind 33534) a relay assigned to a member, as small labelled
 * chips tinted with each role's hue. Roles without a label show their id; roles
 * without a color use the theme's secondary container. Sorted by the roles'
 * display `order`.
 */
@Composable
fun RelayRoleChips(
    roles: List<RelayRole>,
    modifier: Modifier = Modifier,
) {
    if (roles.isEmpty()) return
    val sorted = remember(roles) { roles.sortedWith(compareBy<RelayRole>({ it.order ?: Int.MAX_VALUE }, { it.label ?: it.id })) }
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        sorted.forEach { RelayRoleChip(it) }
    }
}

@Composable
fun RelayRoleChip(role: RelayRole) {
    val hue = role.color
    val background =
        if (hue != null && RelayRole.isValidHue(hue)) {
            Color.hsv(hue.toFloat(), 0.45f, 0.85f)
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        }
    val content = if (hue != null && RelayRole.isValidHue(hue)) Color.Black else MaterialTheme.colorScheme.onSecondaryContainer

    Text(
        text = role.label ?: role.id,
        style = MaterialTheme.typography.labelSmall,
        color = content,
        maxLines = 1,
        modifier =
            Modifier
                .clip(RoundedCornerShape(50))
                .background(background)
                .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun RelayMemberEventCard(
    icon: MaterialSymbol,
    title: String,
    subtitle: String?,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                symbol = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge,
                )
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun RelayMembershipListCardPreview() {
    ThemeComparisonColumn {
        RelayMemberEventCard(
            icon = MaterialSymbols.People,
            title = "Relay membership list",
            subtitle = "42 members",
        )
    }
}

@Preview
@Composable
private fun RelayRoleChipsPreview() {
    ThemeComparisonColumn {
        RelayRoleChips(
            listOf(
                RelayRole("28b7e50f", label = "king", color = 37, order = 1),
                RelayRole("mod", label = "moderator", color = 200, order = 2),
                RelayRole("plain"),
            ),
        )
    }
}

@Preview
@Composable
private fun RelayAddMemberCardPreview() {
    ThemeComparisonColumn {
        RelayMemberEventCard(
            icon = MaterialSymbols.PersonAdd,
            title = "Member added to relay",
            subtitle = "a1b2c3d4e5f6a7b8...",
        )
    }
}

@Preview
@Composable
private fun RelayRemoveMemberCardPreview() {
    ThemeComparisonColumn {
        RelayMemberEventCard(
            icon = MaterialSymbols.PersonRemove,
            title = "Member removed from relay",
            subtitle = "a1b2c3d4e5f6a7b8...",
        )
    }
}

@Preview
@Composable
private fun RelayJoinRequestCardPreview() {
    ThemeComparisonColumn {
        RelayMemberEventCard(
            icon = MaterialSymbols.PersonAdd,
            title = "Relay join request",
            subtitle = null,
        )
    }
}

@Preview
@Composable
private fun RelayLeaveRequestCardPreview() {
    ThemeComparisonColumn {
        RelayMemberEventCard(
            icon = MaterialSymbols.AutoMirrored.ExitToApp,
            title = "Relay leave request",
            subtitle = null,
        )
    }
}
