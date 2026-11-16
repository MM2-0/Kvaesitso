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
package com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account.metadata

import com.vitorpamplona.amethyst.commons.model.nip78AppSpecific.AppSpecificState.Companion.APP_SPECIFIC_DATA_D_TAG
import com.vitorpamplona.amethyst.commons.relayClient.assemblers.filterUserAssertionsByAuthorInTheRelay
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEvent
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListFragmentEvent
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageRelayListEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip17Dm.settings.DmRelayListEvent
import com.vitorpamplona.quartz.nip37Drafts.privateOutbox.PrivateOutboxRelayListEvent
import com.vitorpamplona.quartz.nip38UserStatus.UserStatusEvent
import com.vitorpamplona.quartz.nip50Search.SearchRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.favoriteFollowSetsList.FavoriteFollowSetsListEvent
import com.vitorpamplona.quartz.nip51Lists.geohashList.GeohashListEvent
import com.vitorpamplona.quartz.nip51Lists.interestList.InterestListEvent
import com.vitorpamplona.quartz.nip51Lists.interestSet.InterestSetEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.BlockedRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.BroadcastRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.FavoriteRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.IndexerRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.ProxyRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.TrustedRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.simpleGroupList.SimpleGroupListEvent
import com.vitorpamplona.quartz.nip53LiveActivities.nestsServers.NestsServersEvent
import com.vitorpamplona.quartz.nip60Cashu.wallet.CashuWalletEvent
import com.vitorpamplona.quartz.nip61Nutzaps.info.NutzapInfoEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nip78AppData.AppSpecificDataEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.TrustProviderListEvent
import com.vitorpamplona.quartz.nip96FileStorage.config.FileServersEvent
import com.vitorpamplona.quartz.nipA3PaymentTargets.PaymentTargetsEvent
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.offer.Bolt12OfferListEvent
import com.vitorpamplona.quartz.nipB7Blossom.BlossomServersEvent
import com.vitorpamplona.quartz.nipFAAgentKeys.AgentKeyEvent
import com.vitorpamplona.quartz.nipFBScheduledTasks.ScheduledTaskEvent

val AccountInfoAndListsFromKeyKinds =
    listOf(
        MetadataEvent.KIND,
        ContactListEvent.KIND,
        UserStatusEvent.KIND,
        AdvertisedRelayListEvent.KIND,
        DmRelayListEvent.KIND,
        KeyPackageRelayListEvent.KIND,
        SearchRelayListEvent.KIND,
        FileServersEvent.KIND,
        BlossomServersEvent.KIND,
        NestsServersEvent.KIND,
        PrivateOutboxRelayListEvent.KIND,
    )

val AccountInfoAndListsFromKeyKinds2 =
    listOf(
        BlockedRelayListEvent.KIND,
        TrustedRelayListEvent.KIND,
        BroadcastRelayListEvent.KIND,
        IndexerRelayListEvent.KIND,
        ProxyRelayListEvent.KIND,
        InterestListEvent.KIND,
        GeohashListEvent.KIND,
        TrustProviderListEvent.KIND,
        PaymentTargetsEvent.KIND,
        Bolt12OfferListEvent.KIND,
        FavoriteRelayListEvent.KIND,
        InterestSetEvent.KIND,
        // NIP-51 kind 10021: follow sets (anyone's) pinned as feeds in the top-nav picker.
        FavoriteFollowSetsListEvent.KIND,
        // NIP-51 "simple groups" list (kind 10009): the user's joined NIP-29 groups + servers.
        // Loaded up-front so "My Groups" and group memberships resolve immediately at login,
        // without waiting for the groups screen to mount its own subscription.
        SimpleGroupListEvent.KIND,
        // Concord private joined-communities list (kind 13302, CORD-05): the self-encrypted
        // entries carrying each community's secrets. Loaded up-front for the same reason as
        // the NIP-29 list above — so communities joined on another device or client (e.g. the
        // Armada reference client, sharing this key) surface in the Concord hub at login,
        // instead of only appearing after creating/redeeming an invite in Amethyst itself.
        ConcordCommunityListEvent.KIND,
        ConcordCommunityListFragmentEvent.KIND,
        // NIP-60 Cashu wallet + NIP-61 nutzap info. Replaceables, always
        // useful to have available — wallet event holds the user's P2PK key
        // + mint list, nutzap info tells other clients which mints to lock
        // proofs to when sending us nutzaps. Cross-loaded here so a fresh
        // sign-in resolves the wallet before the wallet screen is opened.
        CashuWalletEvent.KIND,
        NutzapInfoEvent.KIND,
        // The user's AI agent keys (kind 12000), so a fresh sign-in can run AI tasks with the
        // key set up in another app.
        AgentKeyEvent.KIND,
        // The user's scheduled tasks (NIP-FB, kind 34817), such as the morning front page: which
        // device runs it is decided there, so every device must see the newest one.
        ScheduledTaskEvent.KIND,
    )

val AmethystMetadataKinds = listOf(AppSpecificDataEvent.KIND)
val AmethystMetadataTagMapFilter = mapOf("d" to listOf(APP_SPECIFIC_DATA_D_TAG))

fun filterAccountInfoAndListsFromKey(
    relay: NormalizedRelayUrl,
    pubkeys: List<HexKey>,
    since: Long?,
): List<RelayBasedFilter> {
    if (pubkeys.isEmpty()) return emptyList()

    return listOf(
        RelayBasedFilter(
            relay = relay,
            filter =
                ExplainedFilter(
                    purpose = SubPurpose.ACCOUNT_DATA,
                    accountPubKeys = pubkeys,
                    kinds = AccountInfoAndListsFromKeyKinds,
                    authors = pubkeys,
                    limit = 20 * pubkeys.size,
                    since = since,
                ),
        ),
        RelayBasedFilter(
            relay = relay,
            filter =
                ExplainedFilter(
                    purpose = SubPurpose.ACCOUNT_DATA,
                    accountPubKeys = pubkeys,
                    kinds = AccountInfoAndListsFromKeyKinds2,
                    authors = pubkeys,
                    limit = 80 * pubkeys.size,
                    since = since,
                ),
        ),
        // The account's own kind:30382 contact cards (nicknames, NIP-44 encrypted).
        // Addressable — one card per target user — hence its own larger-limit filter.
        filterUserAssertionsByAuthorInTheRelay(
            relay = relay,
            authors = pubkeys,
            since = since,
        ),
        RelayBasedFilter(
            relay = relay,
            filter =
                ExplainedFilter(
                    purpose = SubPurpose.ACCOUNT_DATA,
                    accountPubKeys = pubkeys,
                    kinds = AmethystMetadataKinds,
                    authors = pubkeys,
                    tags = AmethystMetadataTagMapFilter,
                    limit = pubkeys.size,
                    since = since,
                ),
        ),
    )
}
