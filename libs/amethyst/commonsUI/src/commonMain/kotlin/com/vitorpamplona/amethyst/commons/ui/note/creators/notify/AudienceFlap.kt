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
package com.vitorpamplona.amethyst.commons.ui.note.creators.notify

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.composer.AudienceGroupChip
import com.vitorpamplona.amethyst.commons.model.composer.AudienceSelection
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserName
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.audience_empty_private
import com.vitorpamplona.amethyst.commons.resources.audience_empty_public
import com.vitorpamplona.amethyst.commons.resources.audience_group_chip
import com.vitorpamplona.amethyst.commons.resources.audience_group_remove
import com.vitorpamplona.amethyst.commons.resources.audience_manage
import com.vitorpamplona.amethyst.commons.resources.audience_summary_others
import com.vitorpamplona.amethyst.commons.resources.audience_summary_two
import com.vitorpamplona.amethyst.commons.resources.notify_mute_user
import com.vitorpamplona.amethyst.commons.resources.notify_unmute_user
import com.vitorpamplona.amethyst.commons.resources.private_note_visible_to
import com.vitorpamplona.amethyst.commons.resources.reply_notify
import com.vitorpamplona.amethyst.commons.ui.note.BaseUserPicture
import com.vitorpamplona.amethyst.commons.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size24dp
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

/**
 * The composer's audience control: who is p-tagged, and — when the note is
 * private — who can decrypt it at all.
 *
 * Replaces the old flat `Notifying` row in every composer that had one. Two
 * things differ:
 *
 * 1. It is a **container**, tinted when the note is sealed, so the audience
 *    reads as the flap of the envelope the message sits in rather than as loose
 *    text floating above the composer.
 * 2. At rest it shows a **facepile plus a summary line**, which is the same
 *    height at three people or ninety. The per-person chips only appear when
 *    the row is expanded, so a bulk add from a people list can no longer push
 *    the message field off screen.
 *
 * Used by the short-note composer (new post, reply, quote, fork, draft, group
 * thread, poll) and by the NIP-22 comment composer behind the url, geohash,
 * hashtag and generic-comment screens.
 */
@Composable
fun AudienceFlap(
    audience: ImmutableList<User>,
    isPrivate: Boolean,
    accountViewModel: AccountViewModel,
    mutedNotifies: ImmutableSet<HexKey> = persistentSetOf(),
    groupChips: ImmutableList<AudienceGroupChip> = persistentListOf(),
    onManage: () -> Unit,
    onRemoveGroup: (String) -> Unit = {},
    onToggleNotify: (User) -> Unit,
) {
    // A public post with nobody tagged has no audience to show: staying invisible
    // keeps the ordinary composer exactly as quiet as it is today.
    if (!isPrivate && audience.isEmpty()) return

    var expanded by remember { mutableStateOf(false) }

    val background by animateColorAsState(
        targetValue = if (isPrivate) MaterialTheme.colorScheme.primary.copy(alpha = 0.09f) else Color.Transparent,
        animationSpec = tween(FLAP_TINT_MS),
        label = "audienceFlapBackground",
    )
    val border by animateColorAsState(
        targetValue = if (isPrivate) MaterialTheme.colorScheme.primary.copy(alpha = 0.28f) else Color.Transparent,
        animationSpec = tween(FLAP_TINT_MS),
        label = "audienceFlapBorder",
    )
    val accent by animateColorAsState(
        targetValue = if (isPrivate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.placeholderText,
        animationSpec = tween(FLAP_TINT_MS),
        label = "audienceFlapAccent",
    )

    // The facepile separates overlapping portraits with a ring punched in the
    // colour behind them. That is the flap's own tinted surface, not the page:
    // using colorScheme.background would leave untinted discs floating on the
    // tint in exactly the mode this design exists for.
    val flapSurface = background.compositeOver(MaterialTheme.colorScheme.background)

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(FlapShape)
                .background(background)
                .border(1.dp, border, FlapShape),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = audience.isNotEmpty()) { expanded = !expanded }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FlapLock(isPrivate, accent)

            Text(
                text = stringRes(if (isPrivate) Res.string.private_note_visible_to else Res.string.reply_notify),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = accent,
                maxLines = 1,
            )

            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                AudienceRestState(audience, mutedNotifies, isPrivate, flapSurface, accountViewModel, onManage)
            }

            ManageButton(onManage)
        }

        AnimatedVisibility(
            visible = expanded && audience.isNotEmpty(),
            enter = fadeIn(tween(FLAP_TINT_MS)) + expandVertically(tween(FLAP_TINT_MS)),
            exit = fadeOut(tween(FLAP_TINT_MS)) + shrinkVertically(tween(FLAP_TINT_MS)),
        ) {
            AudienceDetail(
                audience = audience,
                mutedNotifies = mutedNotifies,
                groupChips = groupChips,
                accountViewModel = accountViewModel,
                onRemoveGroup = onRemoveGroup,
                onToggleNotify = onToggleNotify,
            )
        }
    }
}

/**
 * Marks which of the two modes the flap is in: a bell for an ordinary post's
 * notify list, a lock once the note is sealed. The lock arrives slightly larger
 * as well as tinted, so the mode change reads even at a glance.
 *
 * Deliberately no rotation: the two states are different glyphs, not one glyph
 * opening and closing, so rotating would just leave the bell permanently
 * crooked in public mode.
 */
@Composable
private fun FlapLock(
    isPrivate: Boolean,
    accent: Color,
) {
    val size by animateDpAsState(
        targetValue = if (isPrivate) 18.dp else 16.dp,
        animationSpec = tween(FLAP_TINT_MS),
        label = "audienceFlapLockSize",
    )

    Icon(
        symbol = if (isPrivate) MaterialSymbols.Lock else MaterialSymbols.Notifications,
        contentDescription = null,
        modifier = Modifier.size(size),
        tint = accent,
    )
}

@Composable
private fun ManageButton(onManage: () -> Unit) {
    Box(
        modifier =
            Modifier
                .minimumInteractiveComponentSize()
                .size(30.dp)
                .clip(CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.placeholderText.copy(alpha = 0.4f), CircleShape)
                .clickable(onClick = onManage),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            symbol = MaterialSymbols.Add,
            contentDescription = stringRes(Res.string.audience_manage),
            modifier = Modifier.size(17.dp),
            tint = MaterialTheme.colorScheme.onBackground,
        )
    }
}

/**
 * Where two lines of grey warning copy used to sit. The words that explained
 * the situation are now the thing you tap to change it — and they still carry
 * the fact the old copy carried: with nobody picked, a sealed note reaches
 * only its author.
 */
@Composable
private fun AudienceInvitation(
    isPrivate: Boolean,
    onManage: () -> Unit,
) {
    Row(
        modifier = Modifier.clickable(onClick = onManage),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            symbol = MaterialSymbols.PersonAdd,
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringRes(if (isPrivate) Res.string.audience_empty_private else Res.string.audience_empty_public),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AudienceRestState(
    audience: ImmutableList<User>,
    mutedNotifies: ImmutableSet<HexKey>,
    isPrivate: Boolean,
    flapSurface: Color,
    accountViewModel: AccountViewModel,
    onManage: () -> Unit,
) {
    // Muted people are not part of the audience, so they are not part of its
    // portrait either — they stay one tap away in the expanded chips.
    val active = remember(audience, mutedNotifies) { audience.filter { it.pubkeyHex !in mutedNotifies } }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        // Everyone muted is the same situation as nobody added: the note has no
        // audience, and the way out is the same tap.
        if (active.isEmpty()) {
            AudienceInvitation(isPrivate, onManage)
        } else {
            AudienceFacepile(active, flapSurface, accountViewModel)
            AudienceSummary(active, accountViewModel)
        }
    }
}

/** Overlapping portraits, capped at [AudienceSelection.PILE_FACES] plus a "+N" bubble. */
@Composable
private fun AudienceFacepile(
    users: List<User>,
    ringColor: Color,
    accountViewModel: AccountViewModel,
) {
    Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
        users.take(AudienceSelection.PILE_FACES).forEach { user ->
            Box(
                modifier =
                    Modifier
                        .size(Size24dp + 4.dp)
                        .clip(CircleShape)
                        .background(ringColor),
                contentAlignment = Alignment.Center,
            ) {
                BaseUserPicture(user, Size24dp, accountViewModel)
            }
        }

        val overflow = users.size - AudienceSelection.PILE_FACES
        if (overflow > 0) {
            Box(
                modifier =
                    Modifier
                        .size(Size24dp + 4.dp)
                        .clip(CircleShape)
                        .background(ringColor),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(Size24dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "+$overflow",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
    }
}

/** "Alice", "Alice & Bruno", "Alice, Bruno & 7 others". */
@Composable
private fun AudienceSummary(
    users: List<User>,
    accountViewModel: AccountViewModel,
) {
    val first by observeUserName(users[0], accountViewModel)
    val second = users.getOrNull(1)?.let { observeUserName(it, accountViewModel).value }

    val text =
        when {
            second == null -> first
            users.size == 2 -> stringRes(Res.string.audience_summary_two, first, second)
            else -> {
                val others = users.size - AudienceSelection.SUMMARY_NAMES
                pluralStringRes(Res.plurals.audience_summary_others, others, first, second, others)
            }
        }

    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.placeholderText,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AudienceDetail(
    audience: ImmutableList<User>,
    mutedNotifies: ImmutableSet<HexKey>,
    groupChips: ImmutableList<AudienceGroupChip>,
    accountViewModel: AccountViewModel,
    onRemoveGroup: (String) -> Unit,
    onToggleNotify: (User) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // The chips render through a selectable Surface that enforces a 48dp
        // minimum touch target, which would otherwise dominate the gap between
        // wrapped rows and swamp verticalArrangement.
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
            groupChips.forEach { group ->
                key(group.listId) {
                    AudienceGroupChipView(group) { onRemoveGroup(group.listId) }
                }
            }

            // Deduped before keying: Compose throws on a duplicate key, and pTags
            // can carry the same pubkey twice (a draft round-trips whatever p tags
            // the event had). The flat row this replaces deduped via toSet().
            audience.distinctBy { it.pubkeyHex }.forEach { user ->
                key(user.pubkeyHex) {
                    AudienceMemberChip(
                        user = user,
                        isMuted = user.pubkeyHex in mutedNotifies,
                        accountViewModel = accountViewModel,
                    ) { onToggleNotify(user) }
                }
            }
        }
    }
}

@Composable
private fun AudienceGroupChipView(
    group: AudienceGroupChip,
    onRemove: () -> Unit,
) {
    AssistChip(
        onClick = onRemove,
        label = { Text(text = stringRes(Res.string.audience_group_chip, group.title, group.count.toString())) },
        leadingIcon = {
            Icon(
                symbol = MaterialSymbols.Groups,
                contentDescription = null,
                modifier = Modifier.size(AssistChipDefaults.IconSize),
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        trailingIcon = {
            Icon(
                symbol = MaterialSymbols.Close,
                contentDescription = stringRes(Res.string.audience_group_remove, group.title),
                modifier = Modifier.size(AssistChipDefaults.IconSize),
            )
        },
        colors =
            AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
            ),
    )
}

/**
 * A muted member is drawn as switched off, not as broken: the old
 * `Modifier.alpha(0.4f)` is Android's universal *disabled* signal, which made a
 * working, reversible choice look like a rendering bug. Instead the fill drops
 * away and the struck bell carries the state at full contrast.
 */
@Composable
private fun AudienceMemberChip(
    user: User,
    isMuted: Boolean,
    accountViewModel: AccountViewModel,
    onToggleNotify: () -> Unit,
) {
    InputChip(
        selected = !isMuted,
        onClick = onToggleNotify,
        label = {
            UsernameDisplay(
                user,
                weight = Modifier.widthIn(max = 180.dp),
                fontWeight = if (isMuted) FontWeight.Normal else FontWeight.SemiBold,
                accountViewModel = accountViewModel,
            )
        },
        // leadingIcon rather than avatar: InputChip clips the avatar slot to a
        // circle, cutting off the following badge BaseUserPicture draws outside it.
        leadingIcon = {
            BaseUserPicture(user, Size24dp, accountViewModel)
        },
        trailingIcon = {
            Icon(
                symbol = if (isMuted) MaterialSymbols.NotificationsOff else MaterialSymbols.Notifications,
                contentDescription = stringRes(if (isMuted) Res.string.notify_unmute_user else Res.string.notify_mute_user),
                modifier = Modifier.size(InputChipDefaults.IconSize),
            )
        },
    )
}

private const val FLAP_TINT_MS = 280

private val FlapShape = RoundedCornerShape(16.dp)
