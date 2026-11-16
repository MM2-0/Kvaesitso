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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.backups

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.backups.BackupEventType
import com.vitorpamplona.amethyst.commons.model.backups.ReplaceableBackupConflict
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.navigation.routeFor
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNote
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.channel.observeChannel
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.backup_review_allowed_tag
import com.vitorpamplona.amethyst.commons.resources.backup_review_concord_body
import com.vitorpamplona.amethyst.commons.resources.backup_review_demoted_tag
import com.vitorpamplona.amethyst.commons.resources.backup_review_dropped_tag
import com.vitorpamplona.amethyst.commons.resources.backup_review_encrypted_added
import com.vitorpamplona.amethyst.commons.resources.backup_review_encrypted_changed
import com.vitorpamplona.amethyst.commons.resources.backup_review_encrypted_cleared
import com.vitorpamplona.amethyst.commons.resources.backup_review_feed
import com.vitorpamplona.amethyst.commons.resources.backup_review_hashtags_you_follow
import com.vitorpamplona.amethyst.commons.resources.backup_review_joined
import com.vitorpamplona.amethyst.commons.resources.backup_review_kept
import com.vitorpamplona.amethyst.commons.resources.backup_review_left
import com.vitorpamplona.amethyst.commons.resources.backup_review_left_tag
import com.vitorpamplona.amethyst.commons.resources.backup_review_listed_tag
import com.vitorpamplona.amethyst.commons.resources.backup_review_moved_tag
import com.vitorpamplona.amethyst.commons.resources.backup_review_new
import com.vitorpamplona.amethyst.commons.resources.backup_review_new_count_caption
import com.vitorpamplona.amethyst.commons.resources.backup_review_new_tag
import com.vitorpamplona.amethyst.commons.resources.backup_review_newly_blocked
import com.vitorpamplona.amethyst.commons.resources.backup_review_no_longer_blocked
import com.vitorpamplona.amethyst.commons.resources.backup_review_offer
import com.vitorpamplona.amethyst.commons.resources.backup_review_offers_gone
import com.vitorpamplona.amethyst.commons.resources.backup_review_payment_targets_gone
import com.vitorpamplona.amethyst.commons.resources.backup_review_payments_explainer
import com.vitorpamplona.amethyst.commons.resources.backup_review_places_gone
import com.vitorpamplona.amethyst.commons.resources.backup_review_places_you_follow
import com.vitorpamplona.amethyst.commons.resources.backup_review_receiving_tag
import com.vitorpamplona.amethyst.commons.resources.backup_review_saved
import com.vitorpamplona.amethyst.commons.resources.backup_review_searching_tag
import com.vitorpamplona.amethyst.commons.resources.backup_review_service
import com.vitorpamplona.amethyst.commons.resources.backup_review_service_followers
import com.vitorpamplona.amethyst.commons.resources.backup_review_service_rank
import com.vitorpamplona.amethyst.commons.resources.backup_review_service_topics
import com.vitorpamplona.amethyst.commons.resources.backup_review_services_changed
import com.vitorpamplona.amethyst.commons.resources.backup_review_services_changed_explainer
import com.vitorpamplona.amethyst.commons.resources.backup_review_still_blocked
import com.vitorpamplona.amethyst.commons.resources.backup_review_storing_tag
import com.vitorpamplona.amethyst.commons.resources.backup_review_topics_gone
import com.vitorpamplona.amethyst.commons.resources.backup_review_trust_warning
import com.vitorpamplona.amethyst.commons.resources.backup_review_trusted_tag
import com.vitorpamplona.amethyst.commons.resources.backup_review_unblocked
import com.vitorpamplona.amethyst.commons.resources.backup_review_unblocked_explainer
import com.vitorpamplona.amethyst.commons.resources.backup_review_untrusted_tag
import com.vitorpamplona.amethyst.commons.resources.backup_review_wallet_body
import com.vitorpamplona.amethyst.commons.resources.backup_type_communities
import com.vitorpamplona.amethyst.commons.resources.backup_type_public_chats
import com.vitorpamplona.amethyst.commons.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.commons.ui.note.LoadPublicChatChannel
import com.vitorpamplona.amethyst.commons.ui.note.LoadUser
import com.vitorpamplona.amethyst.commons.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.note.platform.LoadCityName
import com.vitorpamplona.amethyst.commons.ui.screen.LocalDisplaySettings
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.util.toShortDisplay
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListDiff
import com.vitorpamplona.quartz.experimental.ephemChat.chat.RoomId
import com.vitorpamplona.quartz.experimental.ephemChat.list.EphemeralChatListDiff
import com.vitorpamplona.quartz.experimental.ephemChat.list.EphemeralChatListEvent
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageRelayListEvent
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.diff.ContentChange
import com.vitorpamplona.quartz.nip01Core.diff.ListDiff
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip17Dm.settings.DmRelayListEvent
import com.vitorpamplona.quartz.nip28PublicChat.list.PublicChatListDiff
import com.vitorpamplona.quartz.nip28PublicChat.list.PublicChatListEvent
import com.vitorpamplona.quartz.nip28PublicChat.list.channels
import com.vitorpamplona.quartz.nip37Drafts.privateOutbox.PrivateOutboxRelayListEvent
import com.vitorpamplona.quartz.nip50Search.SearchRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.favoriteAlgoFeedsList.FavoriteAlgoFeedsListDiff
import com.vitorpamplona.quartz.nip51Lists.favoriteAlgoFeedsList.FavoriteAlgoFeedsListEvent
import com.vitorpamplona.quartz.nip51Lists.geohashList.GeohashListDiff
import com.vitorpamplona.quartz.nip51Lists.geohashList.GeohashListEvent
import com.vitorpamplona.quartz.nip51Lists.interestList.InterestListDiff
import com.vitorpamplona.quartz.nip51Lists.interestList.InterestListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.BlockedRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.FavoriteRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.IndexerRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.RelayListDiff
import com.vitorpamplona.quartz.nip51Lists.relayLists.TrustedRelayListEvent
import com.vitorpamplona.quartz.nip60Cashu.wallet.CashuWalletDiff
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import com.vitorpamplona.quartz.nip72ModCommunities.follow.CommunityListDiff
import com.vitorpamplona.quartz.nip72ModCommunities.follow.CommunityListEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.TrustProviderListDiff
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.TrustProviderListEvent
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent
import com.vitorpamplona.quartz.nipA3PaymentTargets.PaymentTarget
import com.vitorpamplona.quartz.nipA3PaymentTargets.PaymentTargetsDiff
import com.vitorpamplona.quartz.nipA3PaymentTargets.PaymentTargetsEvent
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.offer.Bolt12OfferListDiff
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.offer.Bolt12OfferListEvent
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource

/**
 * Per-event layouts for the account's other lists: each relay list framed by what it is
 * for (who can reach you, what gets unblocked…), joined spaces as tiles, followed topics
 * as a pill cloud, trust providers as a per-service table, payment targets and offers as
 * cards, and the encrypted-only events as a single explained panel. Returns false when
 * [conflict] has no layout here, so the caller falls back to the generic one.
 */
internal fun LazyListScope.listDiffItems(
    conflict: ReplaceableBackupConflict,
    accountViewModel: AccountViewModel,
    nav: INav,
): Boolean {
    when (val diff = conflict.diff) {
        is RelayListDiff -> relayListItems(diff, conflict, nav)
        is PublicChatListDiff -> publicChatItems(diff, conflict, accountViewModel, nav)
        is CommunityListDiff -> communityItems(diff, conflict, accountViewModel, nav)
        is EphemeralChatListDiff -> ephemeralRoomItems(diff, conflict, nav)
        is FavoriteAlgoFeedsListDiff -> favoriteFeedItems(diff, conflict, accountViewModel, nav)
        is InterestListDiff -> topicItems(diff.hashtags, (conflict.saved as? InterestListEvent)?.publicHashtags().orEmpty(), diff.privateItems, isPlace = false, nav)
        is GeohashListDiff -> topicItems(diff.geohashes, (conflict.saved as? GeohashListEvent)?.publicGeohashes().orEmpty(), diff.privateItems, isPlace = true, nav)
        is TrustProviderListDiff -> trustProviderItems(diff, conflict, accountViewModel, nav)
        is PaymentTargetsDiff -> paymentTargetItems(diff, conflict)
        is Bolt12OfferListDiff -> offerItems(diff, conflict)
        is CashuWalletDiff -> encryptedOnlyItems(MaterialSymbols.AccountBalanceWallet, diff.wallet, Res.string.backup_review_wallet_body)
        is ConcordCommunityListDiff -> encryptedOnlyItems(MaterialSymbols.Groups, diff.communities, Res.string.backup_review_concord_body)
        else -> return false
    }
    return true
}

private val Pad = Modifier.padding(horizontal = 16.dp)

/** Fate of an item between the saved version and the new one. */
internal enum class ItemFate { KEPT, DROPPED, ADDED, CHANGED }

/**
 * The saved items in their order, each marked kept or dropped (or changed), then the new
 * ones: the full picture of a list, not just its differences.
 */
private fun <T, K> fates(
    saved: List<T>,
    diff: ListDiff<T>,
    key: (T) -> K,
): List<Pair<T, ItemFate>> {
    val removed = diff.removed.mapTo(HashSet(), key)
    val changed = diff.changed.associateBy { key(it.after) }
    val seen = HashSet<K>()
    val result = mutableListOf<Pair<T, ItemFate>>()
    saved.forEach { item ->
        val k = key(item)
        if (!seen.add(k)) return@forEach
        when (k) {
            in removed -> result.add(item to ItemFate.DROPPED)
            in changed -> result.add(changed.getValue(k).after to ItemFate.CHANGED)
            else -> result.add(item to ItemFate.KEPT)
        }
    }
    diff.added.forEach { result.add(it to ItemFate.ADDED) }
    return result
}

@Composable
private fun fateColor(fate: ItemFate): Color {
    val tones = conflictTones()
    return when (fate) {
        ItemFate.KEPT -> MaterialTheme.colorScheme.onSurface
        ItemFate.DROPPED -> tones.removed
        ItemFate.ADDED -> tones.added
        ItemFate.CHANGED -> tones.changed
    }
}

private fun <T> LazyListScope.countTiles(
    items: List<Pair<T, ItemFate>>,
    removedCaption: StringResource,
    addedCaption: StringResource,
) {
    item(key = "count-tiles", contentType = "tiles") {
        val tones = conflictTones()
        Row(Pad.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(items.count { it.second == ItemFate.DROPPED }.toString(), stringRes(removedCaption), tones.removed, Modifier.weight(1f))
            StatTile(items.count { it.second == ItemFate.ADDED }.toString(), stringRes(addedCaption), tones.added, Modifier.weight(1f))
            StatTile(items.count { it.second == ItemFate.KEPT || it.second == ItemFate.CHANGED }.toString(), stringRes(Res.string.backup_review_kept), tones.kept, Modifier.weight(1f))
        }
    }
}

private fun LazyListScope.privateItemsCard(change: ContentChange) {
    if (change == ContentChange.NONE) return
    item(key = "private-items", contentType = "private") { PrivateItemsCard(change, Pad.padding(top = 18.dp)) }
}

// ---------------------------------------------------------------------------------------
// Relay lists, framed by what each one is for.
// ---------------------------------------------------------------------------------------

/** How a relay list's rows are presented: its icon and what a kept or dropped relay is called. */
private class RelayFraming(
    val icon: MaterialSymbol,
    val keptTag: StringResource,
    val droppedTag: StringResource = Res.string.backup_review_dropped_tag,
)

private fun framingOf(type: BackupEventType): RelayFraming =
    when (type) {
        BackupEventType.DM_RELAYS -> RelayFraming(MaterialSymbols.Mail, Res.string.backup_review_receiving_tag)
        BackupEventType.KEY_PACKAGE_RELAYS -> RelayFraming(MaterialSymbols.Key, Res.string.backup_review_listed_tag)
        BackupEventType.SEARCH_RELAYS -> RelayFraming(MaterialSymbols.Search, Res.string.backup_review_searching_tag)
        BackupEventType.INDEXER_RELAYS -> RelayFraming(MaterialSymbols.Dns, Res.string.backup_review_listed_tag)
        BackupEventType.RELAY_FEEDS -> RelayFraming(MaterialSymbols.CellTower, Res.string.backup_review_listed_tag)
        BackupEventType.PRIVATE_OUTBOX_RELAYS -> RelayFraming(MaterialSymbols.Lock, Res.string.backup_review_storing_tag)
        BackupEventType.TRUSTED_RELAYS -> RelayFraming(MaterialSymbols.Shield, Res.string.backup_review_trusted_tag, Res.string.backup_review_untrusted_tag)
        else -> RelayFraming(MaterialSymbols.Dns, Res.string.backup_review_listed_tag)
    }

private fun savedRelaysOf(event: Event): List<NormalizedRelayUrl> =
    when (event) {
        is DmRelayListEvent -> event.allRelays()
        is KeyPackageRelayListEvent -> event.allRelays()
        is SearchRelayListEvent -> event.publicRelays()
        is IndexerRelayListEvent -> event.publicRelays()
        is FavoriteRelayListEvent -> event.publicRelays()
        is BlockedRelayListEvent -> event.publicRelays()
        is TrustedRelayListEvent -> event.publicRelays()
        is PrivateOutboxRelayListEvent -> event.publicRelays()
        else -> emptyList()
    }

private fun LazyListScope.relayListItems(
    diff: RelayListDiff,
    conflict: ReplaceableBackupConflict,
    nav: INav,
) {
    val rows = fates(savedRelaysOf(conflict.saved), diff.relays) { it }
    if (conflict.eventType == BackupEventType.BLOCKED_RELAYS) {
        blockedRelayItems(rows, nav)
    } else {
        // No summary card: the rows themselves, tagged kept / dropped / new, are the change.
        val framing = framingOf(conflict.eventType)
        items(rows, key = { "relay-" + it.first.url }, contentType = { "relay-row" }) { (url, fate) ->
            RelayFateRow(url, fate, framing, nav)
        }
    }
    privateItemsCard(diff.privateRelays)
}

@Composable
private fun RelayFateRow(
    url: NormalizedRelayUrl,
    fate: ItemFate,
    framing: RelayFraming,
    nav: INav,
) {
    val color = fateColor(fate)
    val tag =
        when (fate) {
            ItemFate.KEPT -> framing.keptTag
            ItemFate.DROPPED -> framing.droppedTag
            ItemFate.ADDED -> Res.string.backup_review_new_tag
            ItemFate.CHANGED -> Res.string.backup_review_demoted_tag
        }
    Row(
        Pad
            .padding(top = 6.dp)
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable { nav.nav(Route.RelayInfo(url.url)) }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(symbol = framing.icon, contentDescription = null, tint = MaterialTheme.colorScheme.placeholderText, modifier = Modifier.size(20.dp))
        Text(
            url.url.removePrefix("wss://").removeSuffix("/"),
            modifier = Modifier.weight(1f),
            color = color,
            textDecoration = if (fate == ItemFate.DROPPED) TextDecoration.LineThrough else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        StatusTag(stringRes(tag), if (fate == ItemFate.KEPT) MaterialTheme.colorScheme.placeholderText else color)
    }
}

@OptIn(ExperimentalLayoutApi::class)
private fun LazyListScope.blockedRelayItems(
    rows: List<Pair<NormalizedRelayUrl, ItemFate>>,
    nav: INav,
) {
    val unblocked = rows.filter { it.second == ItemFate.DROPPED }.map { it.first }
    val stillBlocked = rows.filter { it.second == ItemFate.KEPT || it.second == ItemFate.CHANGED }.map { it.first }
    val newlyBlocked = rows.filter { it.second == ItemFate.ADDED }.map { it.first }

    item(key = "blocked-hero", contentType = "hero") {
        val tones = conflictTones()
        TintedPanel(tones.changedContainer(), Pad.padding(top = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(symbol = MaterialSymbols.LockOpen, contentDescription = null, tint = tones.changed, modifier = Modifier.size(44.dp))
                Column {
                    Text(stringRes(Res.string.backup_review_unblocked, unblocked.size.toString()), fontSize = 30.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, color = tones.changed)
                    Text(stringRes(Res.string.backup_review_unblocked_explainer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.placeholderText)
                }
            }
        }
    }
    if (unblocked.isNotEmpty()) {
        item(key = "unblocked-caption", contentType = "caption") { SectionCaption(stringRes(Res.string.backup_review_no_longer_blocked), Pad.padding(top = 18.dp)) }
        items(unblocked, key = { "unblocked-" + it.url }, contentType = { "relay-row" }) { url ->
            val tones = conflictTones()
            Row(
                Pad
                    .padding(top = 6.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, tones.changed.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .clickable { nav.nav(Route.RelayInfo(url.url)) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(36.dp).clip(CircleShape).border(2.dp, tones.changed, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(symbol = MaterialSymbols.LockOpen, contentDescription = null, tint = tones.changed, modifier = Modifier.size(18.dp))
                }
                Text(url.url.removePrefix("wss://").removeSuffix("/"), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                StatusTag(stringRes(Res.string.backup_review_allowed_tag), tones.changed)
            }
        }
    }
    if (newlyBlocked.isNotEmpty()) {
        item(key = "newly-blocked-caption", contentType = "caption") { SectionCaption(stringRes(Res.string.backup_review_newly_blocked), Pad.padding(top = 18.dp)) }
        items(newlyBlocked, key = { "newly-blocked-" + it.url }, contentType = { "relay-row" }) { url ->
            RelayFateRow(url, ItemFate.ADDED, framingOf(BackupEventType.BLOCKED_RELAYS), nav)
        }
    }
    if (stillBlocked.isNotEmpty()) {
        item(key = "still-blocked", contentType = "chips") {
            Column(Pad.padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionCaption(stringRes(Res.string.backup_review_still_blocked))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    stillBlocked.forEach { url ->
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .clickable { nav.nav(Route.RelayInfo(url.url)) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(symbol = MaterialSymbols.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.placeholderText, modifier = Modifier.size(16.dp))
                            Text(url.url.removePrefix("wss://").removeSuffix("/"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.placeholderText)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------
// Joined spaces: public chats, communities, ephemeral rooms and favorite feeds as tiles.
// ---------------------------------------------------------------------------------------

/** One space tile: its picture, name and subtitle are loaded by [content]. */
private fun <T> LazyListScope.spaceGrid(
    keyPrefix: String,
    items: List<Pair<T, ItemFate>>,
    key: (T) -> String,
    content: @Composable (T, ItemFate, Modifier) -> Unit,
) {
    countTiles(items, Res.string.backup_review_left, Res.string.backup_review_joined)
    items(items.chunked(2), key = { keyPrefix + key(it.first().first) }, contentType = { "space-row" }) { row ->
        Row(Pad.padding(top = 10.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { (item, fate) -> content(item, fate, Modifier.weight(1f)) }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun SpaceTile(
    name: String,
    subtitle: String,
    robot: String,
    picture: String?,
    fate: ItemFate,
    accountViewModel: AccountViewModel,
    modifier: Modifier,
    onClick: (() -> Unit)?,
) {
    val tones = conflictTones()
    Box(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(14.dp),
    ) {
        Column(Modifier.alpha(if (fate == ItemFate.DROPPED) 0.45f else 1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            RobohashFallbackAsyncImage(
                robot = robot,
                model = picture,
                contentDescription = null,
                modifier = Modifier.size(48.dp).clip(CircleShape),
                loadProfilePicture = LocalDisplaySettings.current.showProfilePictures,
                loadRobohash = LocalDisplaySettings.current.loadRobohash,
            )
            Column {
                Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.placeholderText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        val tag =
            when (fate) {
                ItemFate.DROPPED -> Res.string.backup_review_left_tag to tones.removed
                ItemFate.ADDED -> Res.string.backup_review_new_tag to tones.added
                ItemFate.CHANGED -> Res.string.backup_review_moved_tag to tones.changed
                ItemFate.KEPT -> null
            }
        tag?.let { (res, color) -> StatusTag(stringRes(res), color, Modifier.align(Alignment.TopEnd)) }
    }
}

private fun LazyListScope.publicChatItems(
    diff: PublicChatListDiff,
    conflict: ReplaceableBackupConflict,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val saved = (conflict.saved as? PublicChatListEvent)?.tags?.channels().orEmpty()
    spaceGrid("chat-", fates(saved, diff.channels) { it.eventId }, { it.eventId }) { tag, fate, modifier ->
        LoadPublicChatChannel(tag.eventId) { channel ->
            // Subscribes to the channel's metadata on relays and recomposes when it arrives.
            val state by observeChannel(channel, accountViewModel)
            val current = state?.channel ?: channel
            SpaceTile(
                name = current.toBestDisplayName(),
                subtitle = stringRes(Res.string.backup_type_public_chats),
                robot = tag.eventId,
                picture = channel.profilePicture(),
                fate = fate,
                accountViewModel = accountViewModel,
                modifier = modifier,
                onClick = { nav.nav(routeFor(channel)) },
            )
        }
    }
    privateItemsCard(diff.privateItems)
}

private fun LazyListScope.communityItems(
    diff: CommunityListDiff,
    conflict: ReplaceableBackupConflict,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val saved = (conflict.saved as? CommunityListEvent)?.publicCommunities().orEmpty()
    spaceGrid("community-", fates(saved, diff.communities) { it.address }, { it.address.toValue() }) { tag, fate, modifier ->
        AddressableSpaceTile(tag.address, fate, accountViewModel, modifier) { nav.nav(Route.Community(tag.address)) }
    }
    privateItemsCard(diff.privateItems)
}

private fun LazyListScope.favoriteFeedItems(
    diff: FavoriteAlgoFeedsListDiff,
    conflict: ReplaceableBackupConflict,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val saved = (conflict.saved as? FavoriteAlgoFeedsListEvent)?.publicFavoriteAlgoFeeds().orEmpty()
    spaceGrid("feed-", fates(saved, diff.feeds) { it.address }, { it.address.toValue() }) { bookmark, fate, modifier ->
        AddressableSpaceTile(bookmark.address, fate, accountViewModel, modifier) {
            nav.nav { LocalCache.getAddressableNoteIfExists(bookmark.address)?.let { routeFor(it, accountViewModel.account) } }
        }
    }
    privateItemsCard(diff.privateItems)
}

/** A community or feed tile: loads the definition event from relays for its name and picture. */
@Composable
private fun AddressableSpaceTile(
    address: Address,
    fate: ItemFate,
    accountViewModel: AccountViewModel,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    LoadAddressableNote(address) { note ->
        val event =
            if (note != null) {
                val state by observeNote(note, accountViewModel)
                state.note.event
            } else {
                null
            }
        val (name, picture) =
            when (event) {
                is CommunityDefinitionEvent -> event.name() to event.image()?.imageUrl
                is AppDefinitionEvent -> event.appMetaData().let { (it?.name ?: it?.displayName) to (it?.picture ?: it?.image) }
                else -> null to null
            }
        SpaceTile(
            name = name ?: address.dTag.ifBlank { address.toValue().toShortDisplay() },
            subtitle = stringRes(if (address.kind == AppDefinitionEvent.KIND) Res.string.backup_review_feed else Res.string.backup_type_communities),
            robot = address.toValue(),
            picture = picture,
            fate = fate,
            accountViewModel = accountViewModel,
            modifier = modifier,
            onClick = onClick,
        )
    }
}

private fun LazyListScope.ephemeralRoomItems(
    diff: EphemeralChatListDiff,
    conflict: ReplaceableBackupConflict,
    nav: INav,
) {
    val saved = (conflict.saved as? EphemeralChatListEvent)?.publicRooms().orEmpty()
    val rooms = fates(saved, diff.rooms) { it }
    countTiles(rooms, Res.string.backup_review_left, Res.string.backup_review_joined)
    items(rooms, key = { "room-" + it.first.toKey() }, contentType = { "room-row" }) { (room, fate) -> RoomRow(room, fate, nav) }
    privateItemsCard(diff.privateItems)
}

@Composable
private fun RoomRow(
    room: RoomId,
    fate: ItemFate,
    nav: INav,
) {
    val color = fateColor(fate)
    Row(
        Pad
            .padding(top = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable { nav.nav(routeFor(room)) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Text("#", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = color)
        }
        Column(Modifier.weight(1f).alpha(if (fate == ItemFate.DROPPED) 0.55f else 1f)) {
            Text(room.id, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                room.relayUrl.url
                    .removePrefix("wss://")
                    .removeSuffix("/"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.placeholderText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        when (fate) {
            ItemFate.DROPPED -> StatusTag(stringRes(Res.string.backup_review_left_tag), color)
            ItemFate.ADDED -> StatusTag(stringRes(Res.string.backup_review_new_tag), color)
            else -> {}
        }
    }
}

// ---------------------------------------------------------------------------------------
// Followed hashtags and places: a pill cloud of what stays, goes and arrives.
// ---------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
private fun LazyListScope.topicItems(
    diff: ListDiff<String>,
    saved: List<String>,
    privateItems: ContentChange,
    isPlace: Boolean,
    nav: INav,
) {
    val topics = fates(saved, diff) { it.lowercase() }
    countTiles(topics, if (isPlace) Res.string.backup_review_places_gone else Res.string.backup_review_topics_gone, Res.string.backup_review_new_count_caption)
    item(key = "topic-cloud", contentType = "cloud") {
        val tones = conflictTones()
        Column(Pad.padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionCaption(stringRes(if (isPlace) Res.string.backup_review_places_you_follow else Res.string.backup_review_hashtags_you_follow))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                topics.forEach { (topic, fate) ->
                    val color = fateColor(fate)
                    val shape = RoundedCornerShape(16.dp)
                    Row(
                        Modifier
                            .clip(shape)
                            .background(
                                when (fate) {
                                    ItemFate.KEPT -> MaterialTheme.colorScheme.surfaceContainer
                                    else -> color.copy(alpha = 0.12f)
                                },
                            ).then(if (fate == ItemFate.DROPPED) Modifier.border(1.dp, tones.removed.copy(alpha = 0.5f), shape) else Modifier)
                            .clickable { nav.nav(if (isPlace) Route.Geohash(topic) else Route.Hashtag(topic)) }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (isPlace) Icon(symbol = MaterialSymbols.LocationOn, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                        val decoration = if (fate == ItemFate.DROPPED) TextDecoration.LineThrough else null
                        if (isPlace) {
                            // Like the rest of the app: the city the geohash resolves to next to
                            // its code, and just the code until (or unless) the name resolves.
                            LoadCityName(topic, onLoading = { TopicText("#$topic", color, decoration) }) { city ->
                                TopicText(if (city == topic) "#$topic" else "$city · #$topic", color, decoration)
                            }
                        } else {
                            TopicText("#$topic", color, decoration)
                        }
                    }
                }
            }
        }
    }
    privateItemsCard(privateItems)
}

@Composable
private fun TopicText(
    text: String,
    color: Color,
    decoration: TextDecoration?,
) {
    Text(
        text,
        color = color,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        textDecoration = decoration,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

// ---------------------------------------------------------------------------------------
// Trust providers: one row per service, who provided it before and who does now.
// ---------------------------------------------------------------------------------------

private class ServiceRow(
    val service: String,
    val before: HexKey?,
    val after: HexKey?,
)

private fun LazyListScope.trustProviderItems(
    diff: TrustProviderListDiff,
    conflict: ReplaceableBackupConflict,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // A service can have several providers, so each side is a set per service: providers on
    // both sides are kept rows, and the ones only before or only after are paired up as swaps.
    val before = (conflict.saved as? TrustProviderListEvent)?.serviceProviders().orEmpty().groupBy({ it.service.type }, { it.pubkey })
    val after = (conflict.incoming as? TrustProviderListEvent)?.serviceProviders().orEmpty().groupBy({ it.service.type }, { it.pubkey })
    val services = (before.keys + after.keys).distinct()
    val rows =
        services.flatMap { service ->
            val had = before[service].orEmpty().distinct()
            val has = after[service].orEmpty().distinct()
            val gone = had - has.toSet()
            val new = has - had.toSet()
            had.filter { it in has }.map { ServiceRow(service, it, it) } +
                List(maxOf(gone.size, new.size)) { ServiceRow(service, gone.getOrNull(it), new.getOrNull(it)) }
        }
    val affected =
        rows
            .filter { it.before != it.after }
            .map { it.service }
            .distinct()
            .size

    item(key = "trust-hero", contentType = "hero") {
        val tones = conflictTones()
        TintedPanel(MaterialTheme.colorScheme.surfaceContainer, Pad.padding(top = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(tones.changedContainer()), contentAlignment = Alignment.Center) {
                    Icon(symbol = MaterialSymbols.Group, contentDescription = null, tint = tones.changed)
                }
                Column {
                    Text(pluralStringResource(Res.plurals.backup_review_services_changed, services.size, affected.toString(), services.size.toString()), fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)
                    Text(stringRes(Res.string.backup_review_services_changed_explainer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.placeholderText)
                }
            }
        }
    }
    item(key = "trust-header", contentType = "table-header") {
        Row(Pad.padding(top = 16.dp, bottom = 4.dp).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Res.string.backup_review_service, Res.string.backup_review_saved, Res.string.backup_review_new).forEachIndexed { index, res ->
                Text(stringRes(res).uppercase(), Modifier.weight(if (index == 0) 1.1f else 1f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.placeholderText)
            }
        }
    }
    items(rows, key = { "service-" + it.service + "-" + it.before + "-" + it.after }, contentType = { "service-row" }) { row -> ServiceTableRow(row, accountViewModel, nav) }
    item(key = "trust-note", contentType = "note") {
        val tones = conflictTones()
        Row(
            Pad
                .padding(top = 14.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(tones.changedContainer())
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(symbol = MaterialSymbols.Warning, contentDescription = null, tint = tones.changed, modifier = Modifier.size(18.dp))
            Text(stringRes(Res.string.backup_review_trust_warning), style = MaterialTheme.typography.bodySmall, color = tones.changed)
        }
    }
    privateItemsCard(diff.privateItems)
}

@Composable
private fun ServiceTableRow(
    row: ServiceRow,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val tones = conflictTones()
    val tint =
        when {
            row.before == row.after -> MaterialTheme.colorScheme.surfaceContainer
            row.after == null -> tones.removedContainer()
            row.before == null -> tones.addedContainer()
            else -> tones.changedContainer()
        }
    Row(
        Pad
            .padding(top = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(tint)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(serviceLabel(row.service), Modifier.weight(1.1f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 2)
        ProviderCell(row.before, struck = row.before != row.after, color = tones.removed, accountViewModel = accountViewModel, nav = nav, modifier = Modifier.weight(1f))
        ProviderCell(row.after, struck = false, color = if (row.before == row.after) MaterialTheme.colorScheme.onSurface else tones.added, accountViewModel = accountViewModel, nav = nav, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun serviceLabel(type: String): String =
    when (type) {
        "rank" -> stringRes(Res.string.backup_review_service_rank)
        "followers" -> stringRes(Res.string.backup_review_service_followers)
        "t" -> stringRes(Res.string.backup_review_service_topics)
        else -> type.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

@Composable
private fun ProviderCell(
    pubKey: HexKey?,
    struck: Boolean,
    color: Color,
    accountViewModel: AccountViewModel,
    nav: INav,
    modifier: Modifier,
) {
    if (pubKey == null) {
        Text("—", modifier, color = MaterialTheme.colorScheme.placeholderText)
        return
    }
    LoadUser(pubKey) { user ->
        Row(
            modifier.clip(RoundedCornerShape(8.dp)).clickable { nav.nav(Route.Profile(pubKey)) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            RobohashFallbackAsyncImage(
                robot = pubKey,
                // Observed, so the picture appears when the provider's metadata arrives.
                model = observedPicture(pubKey, accountViewModel),
                contentDescription = null,
                modifier = Modifier.size(22.dp).clip(CircleShape),
                loadProfilePicture = LocalDisplaySettings.current.showProfilePictures,
                loadRobohash = LocalDisplaySettings.current.loadRobohash,
            )
            ProvideTextStyle(MaterialTheme.typography.labelMedium.copy(color = if (struck) color else MaterialTheme.colorScheme.onSurface, textDecoration = if (struck) TextDecoration.LineThrough else null)) {
                if (user != null) {
                    UsernameDisplay(user, fontWeight = FontWeight.Medium, textColor = if (struck) color else Color.Unspecified, accountViewModel = accountViewModel)
                } else {
                    Text(pubKey.toShortDisplay(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------
// Payment targets and BOLT12 offers: the ways people pay you, as cards.
// ---------------------------------------------------------------------------------------

private fun LazyListScope.paymentTargetItems(
    diff: PaymentTargetsDiff,
    conflict: ReplaceableBackupConflict,
) {
    val saved = (conflict.saved as? PaymentTargetsEvent)?.paymentTargets().orEmpty()
    val targets = fates(saved, diff.targets) { it }
    moneyHero(targets.count { it.second == ItemFate.DROPPED }, Res.plurals.backup_review_payment_targets_gone)
    items(targets, key = { "target-" + it.first.type + ":" + it.first.authority }, contentType = { "money-row" }) { (target, fate) -> PaymentTargetRow(target, fate) }
}

private fun LazyListScope.offerItems(
    diff: Bolt12OfferListDiff,
    conflict: ReplaceableBackupConflict,
) {
    val saved = (conflict.saved as? Bolt12OfferListEvent)?.offers().orEmpty()
    val offers = fates(saved, diff.offers) { it }
    moneyHero(offers.count { it.second == ItemFate.DROPPED }, Res.plurals.backup_review_offers_gone)
    items(offers, key = { "offer-" + it.first }, contentType = { "money-row" }) { (offer, fate) ->
        MoneyRow(stringRes(Res.string.backup_review_offer), offer.toShortDisplay(prefixSize = 4), fate)
    }
}

private fun LazyListScope.moneyHero(
    dropped: Int,
    headline: PluralStringResource,
) {
    item(key = "money-hero", contentType = "hero") {
        val tones = conflictTones()
        val color = if (dropped > 0) tones.removed else tones.added
        TintedPanel(color.copy(alpha = 0.12f), Pad.padding(top = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    Icon(symbol = MaterialSymbols.Bolt, contentDescription = null, tint = color)
                }
                Column {
                    Text(pluralStringResource(headline, dropped, dropped), fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, color = color)
                    Text(stringRes(Res.string.backup_review_payments_explainer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.placeholderText)
                }
            }
        }
    }
}

@Composable
private fun PaymentTargetRow(
    target: PaymentTarget,
    fate: ItemFate,
) = MoneyRow(target.type.replaceFirstChar { it.uppercase() }, target.authority, fate)

@Composable
private fun MoneyRow(
    kind: String,
    value: String,
    fate: ItemFate,
) {
    val color = fateColor(fate)
    Row(
        Pad
            .padding(top = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
            Text(kind.take(1), fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.placeholderText)
        }
        Column(Modifier.weight(1f)) {
            Text(kind, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.placeholderText)
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = color,
                textDecoration = if (fate == ItemFate.DROPPED) TextDecoration.LineThrough else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        when (fate) {
            ItemFate.DROPPED -> StatusTag(stringRes(Res.string.backup_review_dropped_tag), color)
            ItemFate.ADDED -> StatusTag(stringRes(Res.string.backup_review_new_tag), color)
            else -> {}
        }
    }
}

// ---------------------------------------------------------------------------------------
// Encrypted-only events (Cashu wallet, Concord list): one explained panel.
// ---------------------------------------------------------------------------------------

private fun LazyListScope.encryptedOnlyItems(
    icon: MaterialSymbol,
    change: ContentChange,
    body: StringResource,
) {
    item(key = "encrypted-hero", contentType = "hero") {
        val tones = conflictTones()
        val (color, headline) =
            when (change) {
                ContentChange.CLEARED -> tones.removed to Res.string.backup_review_encrypted_cleared
                ContentChange.ADDED -> tones.added to Res.string.backup_review_encrypted_added
                else -> tones.changed to Res.string.backup_review_encrypted_changed
            }
        Column(Pad.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            TintedPanel(color.copy(alpha = 0.12f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(symbol = icon, contentDescription = null, tint = color, modifier = Modifier.size(44.dp))
                    Text(stringRes(headline), fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, color = color)
                }
            }
            PrivateItemsCard(change)
            Text(stringRes(body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.placeholderText)
        }
    }
}
