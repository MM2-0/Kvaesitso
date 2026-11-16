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
package com.vitorpamplona.amethyst.commons.relayClient.account.metadata

import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageRelayListEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip17Dm.settings.DmRelayListEvent
import com.vitorpamplona.quartz.nip50Search.SearchRelayListEvent
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
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.TrustProviderListEvent
import com.vitorpamplona.quartz.nip96FileStorage.config.FileServersEvent
import com.vitorpamplona.quartz.nipB7Blossom.BlossomServersEvent

val BasicAccountInfoKinds =
    listOf(
        MetadataEvent.KIND,
        ContactListEvent.KIND,
        AdvertisedRelayListEvent.KIND,
        DmRelayListEvent.KIND,
        KeyPackageRelayListEvent.KIND,
        SearchRelayListEvent.KIND,
        FileServersEvent.KIND,
        BlossomServersEvent.KIND,
        NestsServersEvent.KIND,
    )

val BasicAccountInfoKinds2 =
    listOf(
        BlockedRelayListEvent.KIND,
        TrustedRelayListEvent.KIND,
        BroadcastRelayListEvent.KIND,
        IndexerRelayListEvent.KIND,
        ProxyRelayListEvent.KIND,
        InterestListEvent.KIND,
        GeohashListEvent.KIND,
        TrustProviderListEvent.KIND,
        FavoriteRelayListEvent.KIND,
        InterestSetEvent.KIND,
        // NIP-51 "simple groups" list (kind 10009), matching the sibling NIP-51 lists above.
        SimpleGroupListEvent.KIND,
    )

fun filterBasicAccountInfoFromKeys(
    relay: NormalizedRelayUrl,
    otherAccounts: List<HexKey>?,
    since: Long?,
    /**
     * Who wants these profiles. The `authors` here are OTHER people — the account-switcher's
     * avatars — so unlike every other builder in this package the authors are NOT the accounts
     * asking, and attribution has to be passed in or the row reads as unattributed.
     */
    requestedBy: List<HexKey>,
): List<RelayBasedFilter> {
    if (otherAccounts.isNullOrEmpty()) return emptyList()

    return listOf(
        RelayBasedFilter(
            relay = relay,
            filter =
                ExplainedFilter(
                    purpose = SubPurpose.ACCOUNT_DATA,
                    accountPubKeys = requestedBy,
                    kinds = BasicAccountInfoKinds,
                    authors = otherAccounts.toList(),
                    limit = otherAccounts.size * BasicAccountInfoKinds.size,
                    since = since,
                ),
        ),
        RelayBasedFilter(
            relay = relay,
            filter =
                ExplainedFilter(
                    purpose = SubPurpose.ACCOUNT_DATA,
                    accountPubKeys = requestedBy,
                    kinds = BasicAccountInfoKinds2,
                    authors = otherAccounts.toList(),
                    limit = otherAccounts.size * BasicAccountInfoKinds2.size,
                    since = since,
                ),
        ),
    )
}
