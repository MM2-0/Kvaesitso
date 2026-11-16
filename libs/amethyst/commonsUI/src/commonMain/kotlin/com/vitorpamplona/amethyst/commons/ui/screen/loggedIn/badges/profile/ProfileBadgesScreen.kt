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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.badges.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.routeFor
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.event.EventFinderFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.badge_untitled
import com.vitorpamplona.amethyst.commons.resources.profile_badges_description
import com.vitorpamplona.amethyst.commons.resources.profile_badges_empty
import com.vitorpamplona.amethyst.commons.resources.profile_badges_title
import com.vitorpamplona.amethyst.commons.ui.components.RobohashAsyncImage
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.badges.profile.datasource.ProfileBadgesFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip58Badges.accepted.AcceptedBadgeSetEvent
import com.vitorpamplona.quartz.nip58Badges.award.BadgeAwardEvent
import com.vitorpamplona.quartz.nip58Badges.definition.BadgeDefinitionEvent
import com.vitorpamplona.quartz.nip58Badges.profile.ProfileBadgesEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

@Composable
fun ProfileBadgesScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val myPubkey = accountViewModel.userProfile().pubkeyHex

    // Pull every kind 8 award tagging me from the user's notification relays so
    // historic awards land in cache while this screen is open.
    ProfileBadgesFilterAssemblerSubscription(accountViewModel)

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

    val acceptedAwardIds =
        remember(newState, oldState) {
            val newEvent = newState.note.event as? ProfileBadgesEvent
            val oldEvent = oldState.note.event as? AcceptedBadgeSetEvent
            (newEvent?.badgeAwardEvents()?.map { it.eventId } ?: oldEvent?.badgeAwardEvents()?.map { it.eventId } ?: emptyList())
                .toSet()
        }

    // Tick whenever LocalCache emits a bundle that contains an award addressed
    // to me, so the snapshot below recomputes and the new award appears.
    var bundleTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(myPubkey) {
        launch(Dispatchers.IO) {
            LocalCache.live.newEventBundles.collect { bundle ->
                val touchesMe =
                    bundle.any { note ->
                        val ev = note.event
                        ev is BadgeAwardEvent && ev.awardeeIds().contains(myPubkey)
                    }
                if (touchesMe) bundleTick++
            }
        }
    }

    // "loaded" flips once when either profile-badges event arrives in cache, so
    // we re-sort exactly once after the initial load. Toggling the Switch below
    // doesn't change it, so the list stays in place while the user edits.
    val acceptedDataLoaded =
        newState.note.event != null || oldState.note.event != null

    val receivedAwards =
        remember(myPubkey, bundleTick, acceptedDataLoaded) {
            // acceptedAwardIds is captured from the enclosing scope at this
            // recomputation point; it is NOT part of the remember key, so
            // subsequent toggles don't re-run this sort.
            val initialAccepted = acceptedAwardIds
            LocalCache.notes
                .filterIntoSet { _, it ->
                    val event = it.event
                    event is BadgeAwardEvent && event.awardeeIds().contains(myPubkey)
                }.mapNotNull { it.event as? BadgeAwardEvent }
                .sortedWith(
                    // Accepted badges on top (at load time), then most recent first.
                    compareByDescending<BadgeAwardEvent> { initialAccepted.contains(it.id) }
                        .thenByDescending { it.createdAt },
                )
        }

    Scaffold(
        topBar = {
            TopBarWithBackButton(stringRes(id = Res.string.profile_badges_title), nav)
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Text(
                text = stringRes(Res.string.profile_badges_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            HorizontalDivider()

            if (receivedAwards.isEmpty()) {
                Text(
                    text = stringRes(Res.string.profile_badges_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(
                        items = receivedAwards,
                        key = { it.id },
                    ) { award ->
                        AwardRow(
                            award = award,
                            isAccepted = acceptedAwardIds.contains(award.id),
                            accountViewModel = accountViewModel,
                            nav = nav,
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun AwardRow(
    award: BadgeAwardEvent,
    isAccepted: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val defAddr = award.awardDefinition().firstOrNull()

    if (defAddr == null) {
        StaticAwardRow(
            definition = null,
            defNote = null,
            isAccepted = isAccepted,
            accountViewModel = accountViewModel,
            award = award,
            nav = nav,
        )
        return
    }

    LoadAddressableNote(defAddr) { defNote ->
        if (defNote == null) {
            StaticAwardRow(
                definition = null,
                defNote = null,
                isAccepted = isAccepted,
                accountViewModel = accountViewModel,
                award = award,
                nav = nav,
            )
        } else {
            // Ask relays for the definition if we don't have it yet, then watch.
            EventFinderFilterAssemblerSubscription(defNote, accountViewModel)
            val definition by observeNoteEvent<BadgeDefinitionEvent>(defNote, accountViewModel)
            StaticAwardRow(
                definition = definition,
                defNote = defNote,
                isAccepted = isAccepted,
                accountViewModel = accountViewModel,
                award = award,
                nav = nav,
            )
        }
    }
}

@Composable
private fun StaticAwardRow(
    definition: BadgeDefinitionEvent?,
    defNote: com.vitorpamplona.amethyst.commons.model.AddressableNote?,
    isAccepted: Boolean,
    accountViewModel: AccountViewModel,
    award: BadgeAwardEvent,
    nav: INav,
) {
    val rowModifier =
        if (defNote != null) {
            Modifier
                .fillMaxWidth()
                .clickable {
                    routeFor(defNote, accountViewModel.account)?.let { nav.nav(it) }
                }.padding(horizontal = 20.dp, vertical = 12.dp)
        } else {
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        }

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BadgeThumb(definition)

        Spacer(modifier = Modifier.size(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = definition?.name()?.ifBlank { null } ?: stringRes(Res.string.badge_untitled),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            definition?.description()?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(modifier = Modifier.size(12.dp))

        Switch(
            checked = isAccepted,
            enabled = definition != null,
            onCheckedChange = { checked ->
                accountViewModel.launchSigner {
                    if (checked) {
                        val defEvent = definition ?: return@launchSigner
                        accountViewModel.account.addAcceptedBadge(award, defEvent)
                    } else {
                        accountViewModel.account.removeAcceptedBadge(award)
                    }
                }
            },
        )
    }
}

@Composable
private fun BadgeThumb(definition: BadgeDefinitionEvent?) {
    val imageUrl = definition?.thumb()?.ifBlank { null } ?: definition?.image()?.ifBlank { null }
    val thumbModifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))

    if (imageUrl.isNullOrBlank()) {
        RobohashAsyncImage(
            robot = definition?.id ?: "badgenotfound",
            contentDescription = null,
            modifier = thumbModifier,
            loadRobohash = true,
        )
    } else {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            modifier = thumbModifier,
            contentScale = ContentScale.Crop,
        )
    }
}
