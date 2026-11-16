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
package com.vitorpamplona.amethyst.commons.model

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.filter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.taggedAs
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageRelayListEvent
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchFirst
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip17Dm.settings.DmRelayListEvent
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealEvent
import com.vitorpamplona.quartz.nip60Cashu.wallet.CashuWalletEvent
import com.vitorpamplona.quartz.nip61Nutzaps.info.NutzapInfoEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent

/**
 * The pubkeys an `a`-tagging event addresses, read straight from the coordinates
 * (`kind:pubkey:dTag`) rather than from whatever the local cache happens to hold.
 *
 * This is what lets [EventBroadcaster] route to an addressed author's inbox relays when the
 * addressable itself was never cached on this device - a NIP-52 RSVP being the motivating case,
 * since its `a` tag is the only thing tying it to the appointment's host.
 *
 * Unparseable coordinates are dropped; the result is deduplicated because an event may address
 * several addressables by the same author (a calendar listing its own appointments).
 */
fun addressedAuthors(event: AddressHintProvider): Set<HexKey> = event.linkedAddressIds().mapNotNullTo(mutableSetOf()) { Address.parse(it)?.pubKeyHex }

/**
 * The sign-and-publish choke point for an [Account]: computes the relay set an
 * event should be broadcast to (NIP-65 outbox model, relay hints, channel home
 * relays, broadcast lists, DM inboxes) and owns every publish path - automatic,
 * outbox-only, everywhere, private-relay-list, anonymous, and rebroadcast.
 *
 * Feature orchestration on [Account] (and the Account*Actions classes) should
 * funnel every publish through this class instead of calling the relay client
 * directly.
 */
class EventBroadcaster(
    private val account: Account,
) {
    /** Where each event goes: the routing policy, shared with amy (see [BroadcastRelayPlanner]). */
    val planner =
        BroadcastRelayPlanner(
            account.cache,
            object : BroadcastRelaySource {
                override fun userProfile() = account.userProfile()

                override fun notificationRelays() = account.notificationRelays.flow.value

                override fun broadcastRelays() = account.broadcastRelayList.flow.value

                override fun outboxRelays() = account.outboxRelays.flow.value

                override fun personalOutboxRelays() =
                    account.nip65RelayList.outboxFlow.value +
                        account.privateStorageRelayList.flow.value +
                        account.localRelayList.flow.value

                override fun everywhereRelays() = account.followPlusAllMineWithIndex.flow.value + account.client.availableRelaysFlow().value
            },
        )

    fun computeRelayListToBroadcast(event: Event): Set<NormalizedRelayUrl> = planner.computeRelayListToBroadcast(event)

    fun computeRelayListToBroadcast(note: Note): Set<NormalizedRelayUrl> = planner.computeRelayListToBroadcast(note)

    suspend fun broadcast(note: Note) {
        note.event?.let { noteEvent ->
            val host = note.rumorHost
            if (host != null) {
                // Rumors are rebroadcast as their delivering envelope: the
                // cached copy is content-stripped, so download it and send it.
                // A just-sent note has no relays until its self-wrap echoes
                // back — fall back to our own DM inbox relays. Bare seals
                // (kind 13) carry no p tag, so that filter is wrap-only.
                val relays =
                    note.relays.ifEmpty {
                        account.dmRelays.flow.value
                            .toList()
                    }
                val filter =
                    if (host.kind == SealEvent.KIND) {
                        Filter(
                            kinds = listOf(host.kind),
                            ids = listOf(host.id),
                        )
                    } else {
                        Filter(
                            kinds = listOf(host.kind),
                            tags = mapOf("p" to listOf(account.pubKey)),
                            ids = listOf(host.id),
                        )
                    }
                account.client
                    .taggedAs(SubPurpose.DIRECT_MESSAGES, "Rebroadcast")
                    .fetchFirst(
                        filters = relays.associateWith { _ -> listOf(filter) },
                    )?.let { downloadedEvent ->
                        val toRelays = computeRelayListToBroadcast(downloadedEvent)
                        account.client.publish(downloadedEvent, toRelays)
                    }
            } else if (noteEvent.sig.isEmpty()) {
                // Rumor with no known wrap: publishing it would disclose the
                // private content to relays even though they reject the
                // missing signature.
                return
            } else {
                account.client.publish(noteEvent, computeRelayListToBroadcast(note))
            }
        }
    }

    fun sendAutomatic(events: List<Event>) = events.forEach { sendAutomatic(it) }

    fun sendAutomatic(event: Event?) {
        if (event == null) return
        account.cache.justConsumeMyOwnEvent(event)
        account.client.publish(event, computeRelayListToBroadcast(event))
    }

    fun sendMyPublicAndPrivateOutbox(event: Event?) {
        if (event == null) return
        account.cache.justConsumeMyOwnEvent(event)
        account.client.publish(event, account.outboxRelays.flow.value)
    }

    fun sendMyPublicAndPrivateOutbox(events: List<Event>) {
        events.forEach {
            account.client.publish(it, account.outboxRelays.flow.value)
            account.cache.justConsumeMyOwnEvent(it)
        }
    }

    /**
     * Publishes a re-signed backup that replaces a lossy version from another app. It goes
     * where a normal save of that event goes; lists of the user's own relays (NIP-65, DM,
     * key package) and the wallet go everywhere, and also to every relay the restored list
     * names, since the lossy version may have removed them from the outbox set. Profiles and
     * nutzap info go everywhere too, like their normal saves.
     */
    fun sendRestoredVersion(event: Event) {
        when (event) {
            is AdvertisedRelayListEvent -> sendEverywhereAnd(event, event.relays().mapTo(mutableSetOf()) { it.relayUrl })
            is DmRelayListEvent -> sendEverywhereAnd(event, event.allRelays().toSet())
            is KeyPackageRelayListEvent -> sendEverywhereAnd(event, event.allRelays().toSet())
            is CashuWalletEvent -> sendLiterallyEverywhere(event)
            // Profiles and nutzap info are normally saved everywhere so others can find them;
            // the restore must reach the same relays or they keep serving the lossy version.
            is MetadataEvent -> sendLiterallyEverywhere(event)
            is NutzapInfoEvent -> sendEverywhereAnd(event, event.relays().toSet())
            else -> sendMyPublicAndPrivateOutbox(event)
        }
    }

    private fun sendEverywhereAnd(
        event: Event,
        extraRelays: Set<NormalizedRelayUrl>,
    ) {
        account.client.publish(event, account.followPlusAllMineWithIndex.flow.value + account.client.availableRelaysFlow().value + extraRelays)
        account.cache.justConsumeMyOwnEvent(event)
    }

    fun sendLiterallyEverywhere(event: Event) {
        account.client.publish(event, account.followPlusAllMineWithIndex.flow.value + account.client.availableRelaysFlow().value)
        account.cache.justConsumeMyOwnEvent(event)
    }

    suspend fun <T : Event> signAndSendPrivately(
        template: EventTemplate<T>,
        relayList: Set<NormalizedRelayUrl>,
    ) {
        val event = account.signer.sign(template)
        account.cache.justConsumeMyOwnEvent(event)
        account.client.publish(event, relayList)
    }

    /**
     * Sign [template] with an arbitrary [signer] (e.g. a per-geohash ephemeral
     * identity that is deliberately NOT this account's key) and publish to exactly
     * [relayList]. Used by geohash location chat, where authorship inside a cell
     * must not be linkable to the user's npub.
     */
    suspend fun <T : Event> signWithAndSendPrivately(
        template: EventTemplate<T>,
        signer: NostrSigner,
        relayList: Set<NormalizedRelayUrl>,
    ): T {
        val event = signer.sign(template)
        account.cache.justConsumeMyOwnEvent(event)
        if (relayList.isNotEmpty()) account.client.publish(event, relayList)
        return event
    }

    suspend fun <T : Event> signAndSendPrivatelyOrBroadcast(
        template: EventTemplate<T>,
        relayList: (T) -> List<NormalizedRelayUrl>?,
    ): T {
        val event = account.signer.sign(template)
        account.cache.justConsumeMyOwnEvent(event)
        val relays = relayList(event)
        val targets =
            if (!relays.isNullOrEmpty()) {
                relays.toSet()
            } else {
                computeRelayListToBroadcast(event)
            }
        account.chatDeliveryTracker.trackPublic(event.id, targets)
        account.client.publish(event, targets)
        return event
    }

    suspend fun <T : Event> signAndComputeBroadcast(
        template: EventTemplate<T>,
        broadcast: List<Event> = emptyList(),
    ): T {
        val event = account.signer.sign(template)
        account.cache.justConsumeMyOwnEvent(event)
        val note =
            if (event is AddressableEvent) {
                account.cache.getOrCreateAddressableNote(event.address())
            } else {
                account.cache.getOrCreateNote(event.id)
            }

        val relayList = computeRelayListToBroadcast(note)

        account.client.publish(event, relayList)

        broadcast.forEach { account.client.publish(it, relayList) }

        return event
    }

    suspend fun <T : Event> signAnonymouslyAndBroadcast(
        template: EventTemplate<T>,
        broadcast: List<Event> = emptyList(),
        anonymousSigner: NostrSigner = NostrSignerInternal(KeyPair()),
    ): T {
        val event = anonymousSigner.sign(template)

        account.cache.justConsumeMyOwnEvent(event)
        val note =
            if (event is AddressableEvent) {
                account.cache.getOrCreateAddressableNote(event.address())
            } else {
                account.cache.getOrCreateNote(event.id)
            }

        val relayList = computeRelayListToBroadcast(note)

        account.client.publish(event, relayList)

        broadcast.forEach { account.client.publish(it, relayList) }

        return event
    }

    fun republishEventsTo(
        events: List<Event>,
        relays: Set<NormalizedRelayUrl>,
    ) {
        if (relays.isEmpty() || events.isEmpty()) return
        events.forEach { account.client.publish(it, relays) }
    }
}
