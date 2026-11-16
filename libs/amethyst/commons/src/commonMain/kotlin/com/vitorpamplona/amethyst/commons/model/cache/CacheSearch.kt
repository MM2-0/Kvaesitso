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

import com.vitorpamplona.amethyst.commons.model.IAccount
import com.vitorpamplona.amethyst.commons.model.LiveHiddenUsers
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.emphChat.EphemeralChatChannel
import com.vitorpamplona.amethyst.commons.model.nip28PublicChats.PublicChatChannel
import com.vitorpamplona.amethyst.commons.model.nip53LiveActivities.LiveActivitiesChannel
import com.vitorpamplona.amethyst.commons.search.RenderableKinds
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.tagValueContains
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.decodeEventIdAsHexOrNull
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip31Alts.AltTag
import com.vitorpamplona.quartz.nip50Search.EventSearchMatcher
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip89AppHandlers.clientTag.ClientTag
import com.vitorpamplona.quartz.utils.DualCase
import kotlinx.coroutines.CancellationException

/**
 * Prefix/content search over the [LocalCache] stores: users, notes, and the
 * public-chat / ephemeral / live-activity channel maps. Pure read-side policy —
 * no state beyond the cache reference — so ranking and filtering rules can be
 * tested against a populated cache.
 */
class CacheSearch(
    private val cache: EventCache,
) {
    /**
     * Users whose name, display name, NIP-05 or lightning address matches [username], best first.
     *
     * [forAccount] is the reader: when given, their muted users and muted words are filtered out,
     * the people they follow sort first, and the people they don't follow sort by their Web of
     * Trust score, highest first, ahead of the unscored. `null` searches everything and ranks on the name
     * match alone — what a caller with no account in hand (a mention autocomplete, a spotlight)
     * wants. [limit] caps the result *after* ranking, so the cap never costs the best matches.
     */
    fun findUsersStartingWith(
        username: String,
        forAccount: IAccount?,
        limit: Int? = null,
    ): List<User> {
        if (username.isBlank()) return emptyList()

        val key = decodePublicKeyAsHexOrNull(username)

        if (key != null) {
            val user = cache.getUserIfExists(key)
            if (user != null) {
                return listOfNotNull(user)
            }
        }

        val dualCase =
            listOf(
                DualCase(username.lowercase(), username.uppercase()),
            )

        val finds =
            cache.users.filter { _, user: User ->
                val metadata = user.metadataOrNull()
                if (metadata == null) {
                    user.pubkeyHex.startsWith(username, true) ||
                        user.pubkeyNpub().startsWith(username, true)
                } else {
                    (
                        metadata.anyNameOrAddressContains(dualCase) ||
                            user.pubkeyHex.startsWith(username, true) ||
                            user.pubkeyNpub().startsWith(username, true)
                    ) &&
                        (forAccount == null || (!forAccount.isHidden(user) && !metadata.anyPropertyContains(forAccount.hiddenWordsCase)))
                }
            }

        val following = forAccount?.followingKeySet() ?: emptySet()
        val findsFollowing = finds.associateWith { it.pubkeyHex in following }
        // Follows keep the name order among themselves: a score shouldn't push a weaker name
        // match above the friend being typed.
        val scores = finds.associateWith { if (findsFollowing[it] == true) 0 else forAccount?.trustRankOf(it.pubkeyHex) ?: 0 }
        val anyNameStartsWith = finds.associateWith { it.metadataOrNull()?.anyNameStartsWith(dualCase) == true }
        val anyAddressStartsWith = finds.associateWith { it.metadataOrNull()?.anyAddressStartsWith(dualCase) == true }
        val displayNames = finds.associateWith { it.toBestDisplayName().lowercase() }

        val ranked =
            finds.sortedWith(
                compareBy(
                    { findsFollowing[it] == false },
                    { -(scores[it] ?: 0) },
                    { anyNameStartsWith[it] == false },
                    { anyAddressStartsWith[it] == false },
                    { displayNames[it] },
                    { it.pubkeyHex },
                ),
            )

        return if (limit != null) ranked.take(limit) else ranked
    }

    /**
     * True when the note is one of the kinds a search never returns.
     *
     * The list is [RenderableKinds.NEVER_IN_RESULTS], not a chain of `is` checks here: it is the
     * reader's policy rather than the cache's, and the `kind:` vocabulary has to be able to read
     * it — offering `kind:repost` while dropping every repost from results is a filter that draws
     * a chip and can never return anything, which is exactly what it did.
     */
    private fun excludeNoteEventFromSearchResults(note: Note): Boolean = note.event?.kind in RenderableKinds.NEVER_IN_RESULTS

    /**
     * Tag names whose values should not match text searches: the `client` tag
     * names the app that published the event (searching for "Amethyst" would
     * otherwise return every event posted through Amethyst), and `p`/`e`/`a`/`alt`
     * values are ids or descriptions of other events, not content of this one.
     */
    private val excludedTagNamesFromSearch =
        setOf(
            ClientTag.TAG_NAME,
            PTag.TAG_NAME,
            ETag.TAG_NAME,
            ATag.TAG_NAME,
            AltTag.TAG_NAME,
        )

    /**
     * Every note in the cache matching [filters], as the search screen asks for them.
     *
     * This is the generic path: the same `Filter`s the REQ carries are run against the cache, so
     * `from:`, `to:`, `since:`, `#t` and the rest narrow local results exactly as they narrow
     * relay results. Before this, local search could only ever match one substring, and every
     * token the search box drew as a chip was ignored on the way in.
     *
     * The two things a `Filter` cannot say are supplied here instead:
     * - **the NIP-50 `search`**, which [FilterMatcher] does not read. One [EventSearchMatcher] is
     *   built per filter and reused across the whole scan; it allocates nothing per note.
     * - **viewer policy** — the mute list, the kinds no one means to search, encrypted content —
     *   which is the reader's business and not a relay's.
     */
    fun findNotesMatching(
        filters: List<Filter>,
        hidden: LiveHiddenUsers,
    ): List<Note> {
        if (filters.isEmpty()) return emptyList()

        // Distinct across filters: a union of arms (a hashtag asks #t, #l and the comment tags)
        // routinely returns the same note down more than one of them.
        val found = LinkedHashSet<Note>()
        filters.forEach { filter ->
            val search = EventSearchMatcher(filter.search)
            cache.filter(filter) { note -> isSearchable(note, hidden) && (search.isEmpty || matchesSearch(note, search)) }.forEach(found::add)
        }
        return found.toList()
    }

    private fun matchesSearch(
        note: Note,
        search: EventSearchMatcher,
    ): Boolean {
        val event = note.event ?: return false
        return search.match(event)
    }

    /** The kinds and authors a reader means to see in results, independent of any query. */
    private fun isSearchable(
        note: Note,
        hidden: LiveHiddenUsers,
    ): Boolean {
        if (excludeNoteEventFromSearchResults(note)) return false
        val event = note.event ?: return false
        // Encrypted content cannot be matched and must not be offered. A SearchableEvent is the
        // exception: the matcher reads only the fields it indexes, never `content`, and those are
        // public by design. Without it every NIP-51 set and emoji pack (their content holds the
        // private items) was missing from local results while relays returned it by title.
        if (event.isContentEncoded() && event !is SearchableEvent) return false
        return !note.isHiddenFor(hidden)
    }

    fun findNotesStartingWith(
        text: String,
        hidden: LiveHiddenUsers,
    ): List<Note> {
        if (text.isBlank()) return emptyList()

        val key = decodeEventIdAsHexOrNull(text)

        if (key != null) {
            val newNote = cache.getNoteShowingEventIfExists(key)

            if ((newNote != null) && !excludeNoteEventFromSearchResults(newNote)) {
                return listOfNotNull(newNote)
            }
        }

        return cache.notes.filter { _, note ->
            if (note.event is AddressableEvent) {
                return@filter false
            }

            if (excludeNoteEventFromSearchResults(note)) {
                return@filter false
            }

            if (note.event?.tags?.tagValueContains(text, true, excludedTagNamesFromSearch) == true ||
                note.idHex.startsWith(text, true)
            ) {
                return@filter !note.isHiddenFor(hidden)
            }

            if (note.event?.isContentEncoded() == false) {
                return@filter if (!note.isHiddenFor(hidden)) {
                    note.event?.content?.contains(text, true) ?: false
                } else {
                    false
                }
            }

            return@filter false
        } +
            cache.addressables.filter { _, addressable ->
                if (excludeNoteEventFromSearchResults(addressable)) {
                    return@filter false
                }

                if (addressable.event?.tags?.tagValueContains(text, true, excludedTagNamesFromSearch) == true ||
                    addressable.idHex.startsWith(text, true)
                ) {
                    return@filter !addressable.isHiddenFor(hidden)
                }

                if (addressable.event?.isContentEncoded() == false) {
                    return@filter if (!addressable.isHiddenFor(hidden)) {
                        addressable.event?.content?.contains(text, true) ?: false
                    } else {
                        false
                    }
                }

                return@filter false
            }
    }

    fun findPublicChatChannelsStartingWith(text: String): List<PublicChatChannel> {
        if (text.isBlank()) return emptyList()

        val key = decodeEventIdAsHexOrNull(text)
        if (key != null) {
            cache.getPublicChatChannelIfExists(key)?.let {
                return listOf(it)
            }
        }

        return cache.publicChatChannels.filter { _, channel ->
            channel.anyNameStartsWith(text)
        }
    }

    fun findEphemeralChatChannelsStartingWith(text: String): List<EphemeralChatChannel> {
        if (text.isBlank()) return emptyList()

        return cache.ephemeralChannels.filter { _, channel ->
            channel.anyNameStartsWith(text)
        }
    }

    fun findLiveActivityChannelsStartingWith(text: String): List<LiveActivitiesChannel> {
        if (text.isBlank()) return emptyList()

        try {
            val parsed = Nip19Parser.uriToRoute(text)?.entity
            if (parsed is NAddress && parsed.kind == LiveActivitiesEvent.KIND) {
                return listOf(cache.getOrCreateLiveChannel(parsed.address()))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
        }

        return cache.liveChatChannels.filter { _, channel ->
            channel.anyNameStartsWith(text)
        }
    }
}
