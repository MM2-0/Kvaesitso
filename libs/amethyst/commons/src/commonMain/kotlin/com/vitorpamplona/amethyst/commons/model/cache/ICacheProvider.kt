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
package com.vitorpamplona.amethyst.commons.model.cache

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Channel
import com.vitorpamplona.amethyst.commons.model.LiveHiddenUsers
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.emphChat.EphemeralChatChannel
import com.vitorpamplona.amethyst.commons.model.nip28PublicChats.PublicChatChannel
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.commons.model.nip53LiveActivities.LiveActivitiesChannel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.hints.HintIndexer
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip19Bech32.entities.Entity
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEmbed
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip19Bech32.entities.NSec
import com.vitorpamplona.quartz.utils.Log

/**
 * Cache provider interface for accessing cached Notes, Users, and Channels.
 *
 * This abstraction allows Note and User models to access the cache without
 * direct coupling to LocalCache singleton. Platform-specific implementations
 * (Android LocalCache, Desktop DesktopLocalCache) implement this interface.
 *
 * Benefits:
 * - Dependency injection instead of singleton coupling
 * - Testable (can mock for unit tests)
 * - Platform-agnostic model layer
 *
 * ## The search entry points
 *
 * The `find*` methods default to returning nothing rather than being abstract, because that is
 * the honest answer for a cache that has no index for the question: Desktop holds notes but no
 * public-chat or ephemeral-channel store, and a port that forced it to implement those would be
 * asking it to lie. A front end therefore renders the result kinds its cache can actually
 * answer, and gains the rest when its cache does — which is the shape the `LocalCache` move in
 * `commons/plans/2026-08-30-commons-migration-sweep.md` step 3 arrives at anyway.
 */
interface ICacheProvider {
    /**
     * NIP-hints index (event/address/pubkey → relay) accumulated from consumed
     * events. Used by the shared user/event finder assemblers to discover which
     * relays are likely to hold a given user's metadata or a missing event.
     */
    val relayHints: HintIndexer

    /**
     * Gets a channel by Note reference.
     * Used for resolving relay hints for channel messages.
     *
     * @param note The note to look up channel for
     * @return The channel if found, null otherwise
     */
    fun getAnyChannel(note: Note): Channel?

    /**
     * Gets a User by public key hex.
     * Used for updating follower counts and user relationships.
     *
     * @param pubkey The user's public key in hex format
     * @return The User if exists in cache, null otherwise
     */
    fun getUserIfExists(pubkey: HexKey): User?

    /**
     * Counts users matching a predicate.
     * Used for calculating follower counts.
     *
     * @param predicate Filter function for counting users
     * @return Count of users matching the predicate
     */
    fun countUsers(predicate: (String, User) -> Boolean): Int

    /**
     * Gets a Note if it exists in cache.
     * Used by ThreadAssembler for finding existing notes.
     *
     * @param hexKey The note's ID in hex format
     * @return The Note if exists in cache, null otherwise
     */
    fun getNoteIfExists(hexKey: HexKey): Note?

    /**
     * Gets an existing Note or creates a new one if it doesn't exist.
     * Used by ThreadAssembler for building thread structures.
     *
     * @param hexKey The note's ID in hex format
     * @return The Note (existing or newly created)
     */
    fun checkGetOrCreateNote(hexKey: HexKey): Note?

    /**
     * Gets an existing AddressableNote or creates a new one if it doesn't exist.
     * Used by ThreadAssembler for building thread structures.
     *
     * @param address The note's ID in address format
     * @return The AddressableNote (existing or newly created)
     */

    fun getOrCreateAddressableNote(address: Address): AddressableNote

    /**
     * Parses [key] as an address and returns the addressable note for it, or null
     * when the key is not a valid address.
     */
    fun checkGetOrCreateAddressableNote(key: String): AddressableNote? =
        try {
            Address.parse(key)?.let { getOrCreateAddressableNote(it) }
        } catch (e: IllegalArgumentException) {
            Log.e("ICacheProvider", "Invalid address key: $key", e)
            null
        }

    /**
     * Gets the event stream for cache updates.
     * Used by ViewModels to react to new notes and deletions.
     *
     * @return The event stream interface
     */
    fun getEventStream(): ICacheEventStream

    /**
     * Checks if an event has been deleted via NIP-09 deletion events.
     * Used by feed state to filter out deleted notes.
     *
     * @param event The event to check
     * @return true if the event has been deleted, false otherwise
     */
    fun hasBeenDeleted(event: Any): Boolean

    /**
     * Finds users whose name, displayName, nip05, or lud16 starts with the given prefix.
     * Used by search functionality to find users by name.
     *
     * @param prefix The search prefix to match against user names
     * @param limit Maximum number of results to return
     * @return List of Users matching the prefix
     */
    fun findUsersStartingWith(
        prefix: String,
        limit: Int = 50,
    ): List<User> = emptyList()

    /**
     * Every note in the cache matching [filters], as a search asks for them.
     *
     * The same `Filter`s the REQ carries, run against the cache, so `from:`, `to:`, `since:`,
     * `#t` and the rest narrow local results exactly as they narrow relay results — and so the
     * NIP-50 `search` string, which a `Filter` cannot express and `FilterMatcher` does not read,
     * is applied by the implementation.
     *
     * [hidden] is the reader's mute list, passed as the value rather than the state holder it
     * comes from: the holder lives in the Android module and a port cannot name it.
     *
     * @return the matching notes, in no particular order — ranking is
     *   [com.vitorpamplona.amethyst.commons.search.SearchPipeline]'s job, not a cache's.
     */
    fun findNotesMatching(
        filters: List<Filter>,
        hidden: LiveHiddenUsers,
    ): List<Note> = emptyList()

    /**
     * Notes reachable from a literal: an event id, or text appearing in a note's content or tags.
     *
     * Separate from [findNotesMatching] because an id is not a query — it resolves to one note,
     * through the addressable indirection if the id names a replaceable event's current version.
     */
    fun findNotesStartingWith(
        text: String,
        hidden: LiveHiddenUsers,
    ): List<Note> = emptyList()

    /** NIP-28 public chats whose name, about or picture begins with [text]. */
    fun findPublicChatChannelsStartingWith(text: String): List<PublicChatChannel> = emptyList()

    /** NIP-C7 ephemeral relay chats whose name begins with [text]. */
    fun findEphemeralChatChannelsStartingWith(text: String): List<EphemeralChatChannel> = emptyList()

    /** NIP-53 live activities whose title begins with [text], or that [text] names as an naddr. */
    fun findLiveActivityChannelsStartingWith(text: String): List<LiveActivitiesChannel> = emptyList()

    /**
     * Gets or creates a User by public key hex.
     * Used when processing events that reference users.
     *
     * @param pubkey The user's public key in hex format
     * @return The User (existing or newly created)
     */
    fun getOrCreateUser(pubkey: HexKey): User?

    /**
     * Gets or creates a User by public key hex, swallowing any failure.
     * Used by the event-finder relay-hint scan, which touches many potentially
     * malformed pubkeys and must never throw mid-scan.
     *
     * @param key The user's public key in hex format
     * @return The User (existing or newly created), or null on failure
     */
    fun checkGetOrCreateUser(key: HexKey): User? = runCatching { getOrCreateUser(key) }.getOrNull()

    fun justConsumeMyOwnEvent(event: Event): Boolean

    /**
     * Seeds the cache from a parsed NIP-19 entity: records its relay hints and creates the
     * placeholder User / Note / AddressableNote, so a REQ built for it has something to
     * attach to. Used by the search sub-assemblers when the query is an npub/nevent/naddr.
     */
    fun consume(nip19: Entity) {
        when (nip19) {
            is NSec -> getOrCreateUser(nip19.toPubKeyHex())
            is NPub -> getOrCreateUser(nip19.hex)
            is NProfile -> {
                nip19.relay.forEach { relayHints.addKey(nip19.hex, it) }
                getOrCreateUser(nip19.hex)
            }
            is NNote -> checkGetOrCreateNote(nip19.hex)
            is NEvent -> {
                nip19.relay.forEach { relayHints.addEvent(nip19.hex, it) }
                val note = checkGetOrCreateNote(nip19.hex)
                if (note != null && note.author == null) {
                    nip19.author?.let { note.author = checkGetOrCreateUser(it) }
                }
            }
            is NEmbed -> consumeEmbedded(nip19.event)
            is NAddress -> {
                val aTag = nip19.aTag()
                nip19.relay.forEach { relayHints.addAddress(aTag, it) }
                getOrCreateAddressableNote(nip19.address())
            }
            else -> {}
        }
    }

    /**
     * Ingests an event carried inline in an `nembed`. Unlike [justConsumeMyOwnEvent] the event
     * did not come from this user, so implementations verify its signature like a relay event.
     */
    fun consumeEmbedded(event: Event)

    /**
     * Every NIP-29 relay group (kind 39000 metadata + rosters) this cache holds. The discovery
     * filters read it to back-fill metadata for groups whose roster names a follow. Defaults to
     * empty for a cache with no relay-group support (Desktop today, test stubs).
     */
    fun allRelayGroupChannels(): List<RelayGroupChannel> = emptyList()

    /** The subset of [allRelayGroupChannels] hosted on [relay]. */
    fun getRelayGroupChannelsOnRelay(relay: NormalizedRelayUrl): List<RelayGroupChannel> = allRelayGroupChannels().filter { it.groupId.relayUrl == relay }
}
