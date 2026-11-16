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
package com.vitorpamplona.amethyst.commons.relayClient.oneshot

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.EventCache
import com.vitorpamplona.amethyst.commons.model.composer.NewMessageTagger
import com.vitorpamplona.amethyst.commons.model.composer.messageTags
import com.vitorpamplona.amethyst.commons.model.composer.pTagsWithHints
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.isValid
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip10Notes.tags.notify
import com.vitorpamplona.quartz.nip17Dm.settings.DmRelayListEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent

/**
 * A short-lived [EventCache] — the class behind the app's `LocalCache` — filled on demand
 * from a [OneShotRelayAccess], so a front end without the app's live subscriptions still
 * runs the app's own `Note`/`User`-based code: `BroadcastRelayPlanner` for routing,
 * [NewMessageTagger] for message tagging, `ThreadAssembler` / `ThreadFeedFilter` for
 * threads. The store stays the source of truth; this cache lives for one operation.
 */
class OneShotNoteCache(
    private val access: OneShotRelayAccess,
    /** Idle timeout for the fetches this cache makes on its own (missing relay lists). */
    private val timeoutMs: Long = 8_000,
) {
    val cache = EventCache()

    /** Users whose kind:10002 this cache already asked relays for: asking again in the same run cannot help. */
    private val relayListsAsked = mutableSetOf<HexKey>()

    /**
     * Consumes [event] as seen on [relays] and returns its note (the addressable slot for an
     * addressable event), or null when a deletion already loaded into the cache covers it.
     *
     * The relay goes in the way the app's relay client hands it over: it feeds the hint
     * index (where to find this author and event again) and creates NIP-29 group channels,
     * whose host relay the planner then routes group content to.
     */
    fun add(
        event: Event,
        relays: Collection<NormalizedRelayUrl> = emptyList(),
    ): Note? {
        val first = relays.firstOrNull()
        if (!cache.checkDeletionAndConsume(event, first, true) && cache.deletionIndex.hasBeenDeleted(event)) return null
        val note = noteOf(event)
        // Consuming hints the first relay for the event and what it links to; every relay that
        // served it is also where its author publishes.
        relays.forEach { relay ->
            note?.addRelay(relay)
            if (relay != first) cache.addIncomingRelayAsHintToAllRelatedEvents(event, relay)
            cache.relayHints.addKey(event.pubKey, relay)
        }
        return note
    }

    /**
     * Adds [events] after the deletions the store holds for them (by id and by address), so
     * an event we (or its author) deleted never comes back from a relay that missed the kind:5.
     */
    suspend fun addAll(events: Collection<Pair<NormalizedRelayUrl?, Event>>) {
        if (events.isEmpty()) return
        access.storedDeletionsFor(events.map { it.second }).forEach { add(it) }
        events.forEach { (relay, event) -> add(event, listOfNotNull(relay)) }
    }

    /** Our own just-signed event, consumed the way the app consumes what it publishes. */
    fun addMine(event: Event): Note? {
        cache.justConsumeMyOwnEvent(event)
        return noteOf(event)
    }

    /**
     * Loads what routing and mentions need to know about [pubKeys] — profile, NIP-65 and
     * DM relay lists — from the store. With [fetchMissing], first fetches the kind:10002
     * of anyone the store has none for (the routing input that matters most), in one
     * request to the bootstrap set plus [alsoAsk] (typically where their events were seen).
     */
    suspend fun addUsers(
        pubKeys: Collection<HexKey>,
        fetchMissing: Boolean = false,
        alsoAsk: Collection<NormalizedRelayUrl> = emptyList(),
    ) {
        val wanted = pubKeys.filterTo(mutableSetOf()) { it.isValid() }.toList()
        if (wanted.isEmpty()) return
        val kinds = listOf(MetadataEvent.KIND, AdvertisedRelayListEvent.KIND, DmRelayListEvent.KIND)
        val known = wanted.chunked(MAX_FILTER_VALUES).flatMap { access.query(Filter(authors = it, kinds = kinds)) }
        known.forEach { add(it) }
        if (fetchMissing) {
            val withList = known.mapNotNullTo(mutableSetOf()) { if (it is AdvertisedRelayListEvent) it.pubKey else null }
            val missing = wanted.filter { it !in withList && relayListsAsked.add(it) }
            if (missing.isNotEmpty()) {
                val relays = access.bootstrapRelays() + alsoAsk
                val listKind = listOf(AdvertisedRelayListEvent.KIND)
                access.fetch(relays.associateWith { missing.chunked(MAX_FILTER_VALUES).map { Filter(authors = it, kinds = listKind) } }, timeoutMs)
                // From the store, not the response: it keeps only the newest list per author.
                missing.chunked(MAX_FILTER_VALUES).forEach { chunk ->
                    access.query(Filter(authors = chunk, kinds = listKind)).forEach { add(it) }
                }
            }
        }
    }

    fun user(pubKey: HexKey): User = cache.getOrCreateUser(pubKey)

    /** The composer's kind:1 for a tagged message: the users it cites as `p`, plus [messageTags]. */
    fun textNote(tagged: Tagged): EventTemplate<TextNoteEvent> =
        TextNoteEvent.build(tagged.message) {
            notify(tagged.mentions)
            messageTags(tagged.message)
        }

    /** A message after the composer's tagging: the rewritten text and the users it notifies. */
    class Tagged(
        val message: String,
        val mentions: List<PTag>,
    )

    /**
     * Runs the app's [NewMessageTagger] over [text]: bare `npub`/`note`/`nevent`/`naddr`
     * words (and `@npub`) become `nostr:` references with relay hints, and every cited
     * user — plus the author of every cited note — is collected for `p` tags. A first
     * pass finds who and what is cited so their relay lists and events can be loaded
     * from the store; the second pass then writes the same hints the app would.
     */
    suspend fun tag(text: String): Tagged {
        val probe = NewMessageTagger(message = text, dao = cache).also { it.run() }
        // Cited users must be reachable: their inboxes are where the app sends the mention.
        addUsers(probe.directMentionsUsers.map { it.pubkeyHex }, fetchMissing = true)
        val (addressable, plain) = probe.directMentionsNotes.partition { it is AddressableNote }
        if (plain.isNotEmpty()) access.query(Filter(ids = plain.map { it.idHex })).forEach { add(it) }
        addressable.filterIsInstance<AddressableNote>().forEach { note ->
            val filter = Filter(kinds = listOf(note.address.kind), authors = listOf(note.address.pubKeyHex), tags = mapOf("d" to listOf(note.address.dTag)))
            access.query(filter).forEach { add(it) }
        }
        val tagger = NewMessageTagger(message = text, dao = cache).also { it.run() }
        return Tagged(tagger.message, tagger.pTagsWithHints(cache.relayHints).orEmpty())
    }

    private fun noteOf(event: Event): Note? = if (event is AddressableEvent) cache.getAddressableNoteIfExists(event.address()) else cache.getNoteIfExists(event.id)
}
