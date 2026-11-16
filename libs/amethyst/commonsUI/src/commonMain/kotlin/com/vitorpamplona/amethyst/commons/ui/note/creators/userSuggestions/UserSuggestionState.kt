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
package com.vitorpamplona.amethyst.commons.ui.note.creators.userSuggestions

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Stable
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.relayClient.search.SearchQueryState
import com.vitorpamplona.amethyst.commons.util.logTime
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrlOrNull
import com.vitorpamplona.quartz.nip05DnsIdentifiers.INip05Client
import com.vitorpamplona.quartz.nip05DnsIdentifiers.Nip05Id
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip19Bech32.entities.NSec
import com.vitorpamplona.quartz.utils.DualCase
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.runCatchingCancellable
import com.vitorpamplona.quartz.utils.startsWithAny
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

val userUriPrefixes =
    listOf(
        DualCase("npub"),
        DualCase("nprofile"),
        DualCase("nostr:npub"),
        DualCase("nostr:nprofile"),
    )

/**
 * Moves users whose pubkey is in [priority] to the top of [found],
 * preserving the relative order everywhere else (stable sort). Reorders
 * only — it never adds or removes entries, so priority keys whose users
 * didn't match the search have no effect.
 */
fun rankPriorityFirst(
    found: List<User>,
    priority: Set<HexKey>,
): List<User> =
    if (priority.isEmpty()) {
        found
    } else {
        found.sortedByDescending { it.pubkeyHex in priority }
    }

/**
 * Drives the @-mention autocomplete dropdown: searches the local cache,
 * relays, and NIP-05 identifiers for the word currently being typed.
 *
 * [priorityPubkeys] is a live supplier of pubkeys to rank first in the
 * results — pass the current conversation's participants (NIP-17 room
 * users, public-chat authors, MLS group members, …) so they beat
 * network-wide matches. Ranking only; it never filters anyone out.
 */
@Stable
class UserSuggestionState(
    val account: Account,
    val nip05Client: INip05Client,
    val priorityPubkeys: () -> Set<HexKey> = { emptySet() },
) {
    val invalidations = MutableStateFlow(0)
    val currentWord = MutableStateFlow("")
    val searchDataSourceState =
        SearchQueryState(
            searchQuery = MutableStateFlow(""),
            account = account,
            searchRelays = account.searchRelayList.flow,
            indexerRelays = account.indexerRelayList.flow,
            followPlusAllMineWithSearchRelays = account.followPlusAllMineWithSearch.flow,
        )

    @OptIn(FlowPreview::class)
    val searchTerm =
        currentWord
            .debounce(300)
            .distinctUntilChanged()
            .map(::userSearchTermOrNull)
            .onEach(::updateDataSource)

    @OptIn(FlowPreview::class)
    val nip05ResolutionFlow =
        currentWord
            .debounce(300)
            .distinctUntilChanged()
            .map(::userSearchTermOrNull)
            .map { prefix ->
                if (prefix != null) {
                    // NIP-05 resolution: full `name@domain` form, or bare
                    // `.bit` domain synthesised as the wildcard `_@domain`.
                    // Bare DNS domains aren't accepted here on purpose: a
                    // `.com`/`.io`/etc. that happens to host nostr.json is
                    // ambiguous with a regular URL the user might be typing.
                    val nip05 =
                        if (prefix.endsWith(".bit", ignoreCase = true) && !prefix.contains('@')) {
                            Nip05Id.parseLenient(prefix)
                        } else if (prefix.contains('@')) {
                            Nip05Id.parse(prefix)
                        } else {
                            null
                        }
                    when {
                        nip05 != null -> {
                            runCatchingCancellable {
                                nip05Client.get(nip05)?.let { info ->
                                    val user = account.cache.checkGetOrCreateUser(info.pubkey)
                                    if (user != null) {
                                        info.relays.forEach {
                                            it.normalizeRelayUrlOrNull()?.let { relay ->
                                                account.cache.relayHints.addKey(user.pubkey(), relay)
                                            }
                                        }
                                    }
                                    user
                                }
                            }.getOrNull()
                        }

                        prefix.startsWithAny(userUriPrefixes) -> {
                            runCatching {
                                Nip19Parser.uriToRoute(prefix)?.entity?.let { parsed ->
                                    when (parsed) {
                                        is NSec -> {
                                            account.cache.getOrCreateUser(parsed.toPubKey().toHexKey())
                                        }

                                        is NPub -> {
                                            account.cache.getOrCreateUser(parsed.hex)
                                        }

                                        is NProfile -> {
                                            val user = account.cache.getOrCreateUser(parsed.hex)
                                            parsed.relay.forEach { relay ->
                                                account.cache.relayHints.addKey(user.pubkey(), relay)
                                            }
                                            user
                                        }

                                        else -> {
                                            null
                                        }
                                    }
                                }
                            }.getOrNull()
                        }

                        prefix.length == 64 && Hex.isHex64(prefix) -> {
                            account.cache.getOrCreateUser(prefix)
                        }

                        else -> {
                            null
                        }
                    }
                } else {
                    null
                }
            }.flowOn(Dispatchers.IO)

    @OptIn(FlowPreview::class)
    val results =
        combine(searchTerm, nip05ResolutionFlow, invalidations.debounce(100)) { prefix, nip05, version ->
            if (nip05 != null) {
                return@combine listOf(nip05)
            }
            if (prefix != null) {
                logTime("UserSuggestionState Search $prefix version $version") {
                    rankPriorityFirst(
                        account.cache.search.findUsersStartingWith(prefix, account),
                        priorityPubkeys(),
                    )
                }
            } else {
                emptyList()
            }
        }.flowOn(Dispatchers.IO)

    fun reset() {
        if (!currentWord.value.isEmpty()) {
            currentWord.tryEmit("")
        }
    }

    fun processCurrentWord(word: String) {
        currentWord.tryEmit(word)
    }

    fun invalidateData() {
        // force new query
        invalidations.update { it + 1 }
    }

    fun userSearchTermOrNull(currentWord: String): String? =
        if (currentWord.length > 2) {
            currentWord.removePrefix("@")
        } else {
            null
        }

    fun updateDataSource(searchTerm: String?) {
        if (searchTerm != null) {
            searchDataSourceState.searchQuery.tryEmit(searchTerm)
        } else {
            searchDataSourceState.searchQuery.tryEmit("")
        }
    }

    fun replaceCurrentWord(
        message: TextFieldValue,
        word: String,
        item: User,
    ): TextFieldValue {
        val lastWordStart = message.selection.end - word.length
        val wordToInsert = mentionInsertion(word, item)

        return TextFieldValue(
            message.text.replaceRange(lastWordStart, message.selection.end, wordToInsert),
            TextRange(lastWordStart + wordToInsert.length, lastWordStart + wordToInsert.length),
        )
    }

    fun replaceCurrentWord(
        state: TextFieldState,
        word: String,
        item: User,
        /**
         * Always insert the resolved `nostr:` URI, never the `@npub1…` short form.
         *
         * The short form is only half a mention: it relies on a send-time tagger to
         * rewrite it and emit the `p` tag. Surfaces that have one (every nostr-event
         * composer) keep it. cordn does not — its content goes into a sealed envelope
         * verbatim, and its reader parses `nostr:` URIs out of that content — so an
         * `@npub1…` there would ship as literal text and mention nobody.
         */
        forceNostrUri: Boolean = false,
    ) {
        val wordToInsert = mentionInsertion(word, item, forceNostrUri)
        state.edit {
            val lastWordStart = selection.end - word.length
            replace(lastWordStart, selection.end, wordToInsert)
            selection = TextRange(lastWordStart + wordToInsert.length, lastWordStart + wordToInsert.length)
        }
    }

    /**
     * The token to insert into the message text when the author picks [item]
     * from the suggestion popover. When the author was typing a NIP-05
     * mention (full `m@testls.bit` or bare-domain `.bit` form), we insert
     * `nostr:nprofile1…` directly so the send-time tagger doesn't need to
     * re-resolve anything — it parses the bech32 inline via its existing
     * `nprofile1` branch, with no main-thread I/O. For every other path
     * (search by name, typed npub/nprofile, hex) we keep the existing
     * `@npub1…` form to preserve current behaviour.
     *
     * Pre-resolved NIP-05 hits already have their relay hints pushed into
     * the account cache by [nip05ResolutionFlow] before this runs, so
     * [User.toNProfile] picks them up automatically.
     */
    private fun mentionInsertion(
        word: String,
        item: User,
        forceNostrUri: Boolean = false,
    ): String {
        if (forceNostrUri) return "nostr:${item.toNProfile()} "

        val typed = userSearchTermOrNull(word)
        val wasNip05Mention =
            typed != null &&
                (
                    (typed.endsWith(".bit", ignoreCase = true) && !typed.contains('@')) ||
                        (typed.contains('@') && Nip05Id.parse(typed) != null)
                )
        return if (wasNip05Mention) {
            "nostr:${item.toNProfile()} "
        } else {
            "@${item.pubkeyNpub()} "
        }
    }
}
