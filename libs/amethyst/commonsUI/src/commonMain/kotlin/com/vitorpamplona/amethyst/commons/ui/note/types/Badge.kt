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
package com.vitorpamplona.amethyst.commons.ui.note.types

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.navigation.routeFor
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.accept_badge
import com.vitorpamplona.amethyst.commons.resources.award_badge
import com.vitorpamplona.amethyst.commons.resources.award_granted_to
import com.vitorpamplona.amethyst.commons.resources.badge_and_n_others
import com.vitorpamplona.amethyst.commons.resources.badge_award_image
import com.vitorpamplona.amethyst.commons.resources.badge_award_image_for
import com.vitorpamplona.amethyst.commons.resources.badge_awardees_label
import com.vitorpamplona.amethyst.commons.resources.badge_untitled
import com.vitorpamplona.amethyst.commons.resources.reject_badge
import com.vitorpamplona.amethyst.commons.resources.unaccept_badge
import com.vitorpamplona.amethyst.commons.ui.components.RobohashAsyncImage
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size30dp
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonRow
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip58Badges.accepted.AcceptedBadgeSetEvent
import com.vitorpamplona.quartz.nip58Badges.award.BadgeAwardEvent
import com.vitorpamplona.quartz.nip58Badges.definition.BadgeDefinitionEvent
import com.vitorpamplona.quartz.nip58Badges.profile.ProfileBadgesEvent

private val BadgeCardShape = RoundedCornerShape(12.dp)
private val BadgeThumbSize = 72.dp

@Composable
fun BadgeDisplay(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav? = null,
) {
    val badgeData by observeNoteEvent<BadgeDefinitionEvent>(baseNote, accountViewModel)
    val definition = badgeData ?: return

    val isMine = definition.pubKey == accountViewModel.userProfile().pubkeyHex

    BadgeCard(
        imageUrl = definition.thumb()?.ifBlank { null } ?: definition.image(),
        name = definition.name(),
        description = definition.description(),
        onClick =
            nav?.let {
                {
                    routeFor(baseNote, accountViewModel.account)?.let { route -> nav.nav(route) }
                }
            },
    ) {
        if (isMine && nav != null) {
            BadgeActionRow {
                FilledTonalButton(
                    onClick = {
                        nav.nav(
                            Route.AwardBadge(
                                kind = definition.kind,
                                pubKeyHex = definition.pubKey,
                                dTag = definition.dTag(),
                            ),
                        )
                    },
                ) {
                    Icon(
                        symbol = MaterialSymbols.MilitaryTech,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(stringRes(Res.string.award_badge))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RenderBadgeAward(
    note: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (note.replyTo.isNullOrEmpty()) return

    val noteEvent = note.event as? BadgeAwardEvent ?: return

    val definitionNote = note.replyTo?.firstOrNull()
    val definition by
        if (definitionNote != null) {
            observeNoteEvent<BadgeDefinitionEvent>(definitionNote, accountViewModel)
        } else {
            remember { mutableStateOf<BadgeDefinitionEvent?>(null) }
        }

    var awardees by remember { mutableStateOf<List<User>>(listOf()) }

    LaunchedEffect(note) { accountViewModel.loadUsers(noteEvent.awardeeIds()) { awardees = it } }

    BadgeCard(
        imageUrl = definition?.thumb()?.ifBlank { null } ?: definition?.image(),
        name = definition?.name() ?: stringRes(Res.string.award_granted_to),
        description = definition?.description(),
        onClick = {
            routeFor(note, accountViewModel.account)?.let { nav.nav(it) }
        },
    ) {
        if (awardees.isNotEmpty()) {
            BadgeAwardeesRow(awardees, accountViewModel, nav)
        }
        AcceptBadgeControls(noteEvent, accountViewModel)
    }
}

@Composable
private fun BadgeCard(
    imageUrl: String?,
    name: String?,
    description: String?,
    onClick: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    val baseModifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
    OutlinedCard(
        modifier = if (onClick != null) baseModifier.clickable(onClick = onClick) else baseModifier,
        shape = BadgeCardShape,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BadgeThumbnail(imageUrl, name)

                Spacer(modifier = Modifier.size(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name?.ifBlank { null } ?: stringRes(Res.string.badge_untitled),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (!description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            actions()
        }
    }
}

@Composable
private fun BadgeThumbnail(
    imageUrl: String?,
    name: String?,
) {
    val description =
        if (name != null) {
            stringRes(Res.string.badge_award_image_for, name)
        } else {
            stringRes(Res.string.badge_award_image)
        }

    Box(
        modifier = Modifier.size(BadgeThumbSize).clip(RoundedCornerShape(10.dp)),
    ) {
        if (imageUrl.isNullOrBlank()) {
            RobohashAsyncImage(
                robot = "badgenotfound",
                contentDescription = description,
                modifier = Modifier.size(BadgeThumbSize),
                loadRobohash = true,
            )
        } else {
            AsyncImage(
                model = imageUrl,
                contentDescription = description,
                modifier = Modifier.size(BadgeThumbSize),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun BadgeActionRow(content: @Composable () -> Unit) {
    Spacer(modifier = Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BadgeAwardeesRow(
    awardees: List<User>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Spacer(modifier = Modifier.height(14.dp))
    Text(
        text = stringRes(Res.string.badge_awardees_label, awardees.size),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(6.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        awardees.take(24).forEach { user ->
            UserPicture(
                user = user,
                size = Size30dp,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
        if (awardees.size > 24) {
            Text(
                text = stringRes(Res.string.badge_and_n_others, awardees.size - 24),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 2.dp),
            )
        }
    }
}

@Composable
fun AcceptBadgeControls(
    award: BadgeAwardEvent,
    accountViewModel: AccountViewModel,
) {
    val myPubkey = accountViewModel.userProfile().pubkeyHex
    val amAwardee = remember(award, myPubkey) { award.awardeeIds().contains(myPubkey) }
    if (!amAwardee) return

    val newNote = LocalCache.getOrCreateAddressableNote(ProfileBadgesEvent.createAddress(myPubkey))
    val oldNote = LocalCache.getOrCreateAddressableNote(AcceptedBadgeSetEvent.createAddress(myPubkey))

    val newState by newNote
        .flow()
        .metadata.stateFlow
        .collectAsStateWithLifecycle()
    val oldState by oldNote
        .flow()
        .metadata.stateFlow
        .collectAsStateWithLifecycle()

    val isAccepted =
        remember(newState, oldState, award.id) {
            val newEvent = newState.note.event as? ProfileBadgesEvent
            val oldEvent = oldState.note.event as? AcceptedBadgeSetEvent
            val awardIds =
                newEvent?.badgeAwardEvents()?.map { it.eventId }
                    ?: oldEvent?.badgeAwardEvents()?.map { it.eventId }
                    ?: emptyList()
            awardIds.contains(award.id)
        }

    BadgeActionRow {
        if (isAccepted) {
            OutlinedButton(
                onClick = {
                    accountViewModel.launchSigner {
                        accountViewModel.account.removeAcceptedBadge(award)
                    }
                },
            ) {
                Text(stringRes(Res.string.unaccept_badge))
            }
        } else {
            TextButton(
                onClick = {
                    accountViewModel.launchSigner {
                        accountViewModel.account.removeAcceptedBadge(award)
                    }
                },
            ) {
                Text(stringRes(Res.string.reject_badge))
            }
            Spacer(modifier = Modifier.size(8.dp))
            FilledTonalButton(
                onClick = {
                    accountViewModel.launchSigner {
                        val defAddr = award.awardDefinition().firstOrNull() ?: return@launchSigner
                        val defNote = LocalCache.getAddressableNoteIfExists(defAddr)
                        val defEvent = defNote?.event as? BadgeDefinitionEvent ?: return@launchSigner
                        accountViewModel.account.addAcceptedBadge(award, defEvent)
                    }
                },
            ) {
                Text(stringRes(Res.string.accept_badge))
            }
        }
    }
}

@Preview
@Composable
private fun RenderBadgePreview() {
    ThemeComparisonRow {
        BadgeCard(
            imageUrl = null,
            name = "Relay Beta Tester",
            description = "Awarded to the dedicated individuals who actively contributed by writing events to the relay during the beta phase.",
        )
    }
}
