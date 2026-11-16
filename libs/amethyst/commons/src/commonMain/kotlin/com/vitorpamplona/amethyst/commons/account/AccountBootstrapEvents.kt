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
package com.vitorpamplona.amethyst.commons.account

import com.vitorpamplona.amethyst.commons.defaults.DefaultChannels
import com.vitorpamplona.amethyst.commons.defaults.DefaultDMRelayList
import com.vitorpamplona.amethyst.commons.defaults.DefaultGlobalRelays
import com.vitorpamplona.amethyst.commons.defaults.DefaultIndexerRelayList
import com.vitorpamplona.amethyst.commons.defaults.DefaultNIP65List
import com.vitorpamplona.amethyst.commons.defaults.DefaultNIP65RelaySet
import com.vitorpamplona.amethyst.commons.defaults.DefaultSearchRelayList
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageRelayListEvent
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip02FollowList.tags.ContactTag
import com.vitorpamplona.quartz.nip17Dm.settings.DmRelayListEvent
import com.vitorpamplona.quartz.nip28PublicChat.list.PublicChatListEvent
import com.vitorpamplona.quartz.nip50Search.SearchRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.FavoriteRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.IndexerRelayListEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent

/**
 * Full set of "new Amethyst account" bootstrap events: the nine signed
 * events that [com.vitorpamplona.amethyst.commons.account.AccountSessionManager.createNewAccount]
 * produces for a brand-new user, in the exact same order and with the same
 * relay defaults. Used by:
 *
 *   - Amethyst's in-app "Create Account" flow (builds an `AccountSettings`
 *     from these).
 *   - `amy create` (writes the keypair to disk, publishes these to the
 *     default NIP-65 relay set).
 *
 * Each event is optional in the sense that the builder lets you null out
 * individual ones via the args — but the sensible default is "all of them".
 */
data class AccountBootstrapEvents(
    val userMetadata: MetadataEvent,
    val contactList: ContactListEvent,
    val nip65RelayList: AdvertisedRelayListEvent,
    val dmRelayList: DmRelayListEvent,
    val keyPackageRelayList: KeyPackageRelayListEvent,
    val searchRelayList: SearchRelayListEvent,
    val indexerRelayList: IndexerRelayListEvent,
    val publicChatList: PublicChatListEvent,
    val favoriteRelayList: FavoriteRelayListEvent,
) {
    /** All nine signed events in publication order. */
    fun all(): List<com.vitorpamplona.quartz.nip01Core.core.Event> =
        listOf(
            userMetadata,
            contactList,
            nip65RelayList,
            dmRelayList,
            keyPackageRelayList,
            searchRelayList,
            indexerRelayList,
            publicChatList,
            favoriteRelayList,
        )
}

/**
 * Build and sign the nine events that comprise a freshly-created Amethyst
 * account. The caller is responsible for publishing them — typically to
 * [DefaultNIP65RelaySet] — after at least a short delay so relay
 * connections are established.
 *
 * [name] is the optional display name; null or blank produces a kind:0
 * event with no `name` field.
 */
fun bootstrapAccountEvents(
    signer: NostrSignerSync,
    name: String? = null,
): AccountBootstrapEvents =
    AccountBootstrapEvents(
        userMetadata = signer.sign(MetadataEvent.newUser(name)),
        contactList =
            ContactListEvent.createFromScratch(
                followUsers = listOf(ContactTag(signer.keyPair.pubKey.toHexKey(), null, null)),
                relayUse = emptyMap(),
                signer = signer,
            ),
        nip65RelayList = AdvertisedRelayListEvent.create(DefaultNIP65List, signer),
        dmRelayList = DmRelayListEvent.create(DefaultDMRelayList, signer),
        // MIP-00: advertise the default outbox relays as KeyPackage hosts
        // so other users can discover and fetch this account's KeyPackage
        // events without having to guess where they were published.
        keyPackageRelayList = KeyPackageRelayListEvent.create(DefaultNIP65RelaySet.toList(), signer),
        searchRelayList = SearchRelayListEvent.create(DefaultSearchRelayList.toList(), signer),
        indexerRelayList = IndexerRelayListEvent.create(DefaultIndexerRelayList.toList(), signer),
        publicChatList = PublicChatListEvent.create(emptyList(), DefaultChannels, signer),
        favoriteRelayList = FavoriteRelayListEvent.create(DefaultGlobalRelays, signer),
    )
