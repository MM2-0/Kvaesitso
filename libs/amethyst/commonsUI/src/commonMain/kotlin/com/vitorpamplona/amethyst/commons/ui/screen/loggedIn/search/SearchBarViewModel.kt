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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.search

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.vitorpamplona.amethyst.commons.actions.ConcordActions
import com.vitorpamplona.amethyst.commons.feeds.InvalidatableContent
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.relayClient.search.SearchQueryState
import com.vitorpamplona.amethyst.commons.search.AuthorNameRewrite
import com.vitorpamplona.amethyst.commons.search.EmojiSearch
import com.vitorpamplona.amethyst.commons.search.EmojiSearchHit
import com.vitorpamplona.amethyst.commons.search.RenderableKinds
import com.vitorpamplona.amethyst.commons.search.SearchHistory
import com.vitorpamplona.amethyst.commons.search.SearchHistoryStorage
import com.vitorpamplona.amethyst.commons.search.SearchPipeline
import com.vitorpamplona.amethyst.commons.search.SearchResultKind
import com.vitorpamplona.amethyst.commons.search.SearchScope
import com.vitorpamplona.amethyst.commons.search.SearchSortOrder
import com.vitorpamplona.amethyst.commons.search.SearchSource
import com.vitorpamplona.amethyst.commons.search.SearchState
import com.vitorpamplona.amethyst.commons.search.wholeInputNip19
import com.vitorpamplona.amethyst.commons.ui.components.namecoin.namecoinLookupTerm
import com.vitorpamplona.amethyst.commons.ui.note.creators.userSuggestions.userUriPrefixes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.common.relaySetupInfoBuilder
import com.vitorpamplona.quartz.buzz.invite.BuzzInviteLink
import com.vitorpamplona.quartz.experimental.ephemChat.chat.EphemeralChatEvent
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrlOrNull
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip02FollowList.petnames.PetnamePath
import com.vitorpamplona.quartz.nip02FollowList.petnames.PetnameResolver
import com.vitorpamplona.quartz.nip05DnsIdentifiers.INip05Client
import com.vitorpamplona.quartz.nip05DnsIdentifiers.Nip05Id
import com.vitorpamplona.quartz.nip05DnsIdentifiers.Nip05KeyInfo
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinNameResolver
import com.vitorpamplona.quartz.nip10Notes.content.findHashtags
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.decodeEventIdAsHexOrNull
import com.vitorpamplona.quartz.nip19Bech32.entities.Entity
import com.vitorpamplona.quartz.nip19Bech32.entities.IPubKeyEntity
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip19Bech32.entities.NSec
import com.vitorpamplona.quartz.nip30CustomEmoji.pack.EmojiPackEvent
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.Rfc3986
import com.vitorpamplona.quartz.utils.runCatchingCancellable
import com.vitorpamplona.quartz.utils.startsWithAny
import com.vitorpamplona.quartz.utils.toDoubleValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

@Stable
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchBarViewModel(
    val account: Account,
    val nip05Client: INip05Client,
    /**
     * The `.bit` / `d/` / `id/` resolver behind the Namecoin lookups. Held beside [nip05Client]
     * because the NIP-05 grammar cannot express a `d/<name>` or `id/<name>` identifier, so those
     * two namespaces are resolved here directly.
     */
    val namecoinResolver: NamecoinNameResolver,
    /** Where this device keeps what has been searched for; see [history]. */
    historyStorage: SearchHistoryStorage,
    /**
     * The query the calling screen seeded the box with — `from:<npub>` off a profile,
     * `kind:article` off the articles feed. It is ordinary field text from here on: the reader
     * can edit or delete it like anything they typed themselves.
     */
    initialQuery: String? = null,
) : ViewModel(),
    InvalidatableContent {
    val focusRequester = FocusRequester()
    var searchValue by mutableStateOf(initialQuery.orEmpty())

    /** A refresh the app asked for: the screen was composed, or the reader came back to it. */
    private val manualInvalidations = MutableStateFlow(0)

    /**
     * What is being searched, and everything about it that is not Android's: the text, its parse,
     * the two debounce windows, the scope, the sort orders. Shared with Desktop, which had grown
     * its own copy of all of it.
     *
     * What stays here is what a shared holder cannot own: a `LazyListState`, a `FocusRequester`,
     * invite-link routing, NIP-05 resolution, and the seven result flows — the *acquisition* of
     * results, which on Android is a cache scan and on Desktop is a relay callback.
     */
    val state = SearchState(viewModelScope, initialText = initialQuery.orEmpty())

    /**
     * When the results are worth recomputing.
     *
     * This used to be a counter that only the lifecycle touched, which meant the result lists were
     * computed once per keystroke and then frozen: the REQ went out, relays answered a few hundred
     * milliseconds later, `LocalCache` filled up — and nothing re-ran the scan, so what the reader
     * saw was whatever had already been cached when they stopped typing. Everything that arrived
     * because of their search only appeared if they typed another character or left the screen and
     * came back.
     *
     * The cache already publishes what it takes in, so the fix is to listen: a lifecycle refresh
     * and an arriving bundle are the same event to a result list.
     *
     * Two things keep that affordable. Bundles carrying nothing a result list could show are
     * dropped outright — the cache takes in chat, DMs, reactions and zap receipts all day, and
     * none of them can appear in search — and what survives is sampled rather than debounced, so
     * the cost is capped at one rescan per [RESCAN_INTERVAL_MS] however hard the relays push.
     * A lifecycle refresh skips both and applies at once.
     */
    private val refreshes: StateFlow<Int> =
        merge(
            manualInvalidations,
            merge(
                account.cache.getEventStream().newEventBundles,
                account.cache.getEventStream().deletedEventBundles,
            ).filter { bundle -> bundle.any { it.event?.kind in WATCHED_KINDS } }
                .sample(RESCAN_INTERVAL_MS),
        ).scan(0) { count, _ -> count + 1 }
            .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /**
     * [refreshes] narrowed to emoji packs: the emoji scan reads only kind 30030, so a relay batch of
     * notes or profiles has nothing in it that could change its answer.
     */
    private val packRefreshes: StateFlow<Int> =
        merge(
            manualInvalidations,
            merge(
                account.cache.getEventStream().newEventBundles,
                account.cache.getEventStream().deletedEventBundles,
            ).filter { bundle -> bundle.any { it.event?.kind == EmojiPackEvent.KIND } }
                .sample(RESCAN_INTERVAL_MS),
        ).scan(0) { count, _ -> count + 1 }
            .stateIn(viewModelScope, WhileSubscribed(5000), 0)

    /** True while a token picker is open under the field.
     *
     * The search screen pins its bars on this. A picker is a scrollable inside the *top bar*, and
     * [com.vitorpamplona.amethyst.commons.ui.layouts.DisappearingBarNestedScroll] moves the bars
     * on `consumed + available` — a sum that is conserved as a scroll walks up the nested-scroll
     * chain, so no connection under the picker can hide its scrolling from the scaffold. Scrolling
     * the list of kinds slid the whole chrome away with it. The scaffold has to be told instead.
     */
    val pickerOpen = MutableStateFlow(false)

    /**
     * What has been searched for on this device, and what has been kept.
     *
     * Desktop has had this since the advanced bar was written; Android never did, because the
     * code sat in a desktop object on `java.util.prefs`. Nothing about it was desktop-specific,
     * so the move to commons is the whole of the port.
     */
    val history = SearchHistory(historyStorage, viewModelScope)

    /** Records the query the reader actually ran — the Enter key, not every pause in typing. */
    fun remember() = history.remember(state.query)

    val queryAsksNothing get() = state.asksNothing
    val searchSettled get() = state.settled

    /** Relays this query went to, and the ones still to answer. Drives the waiting popup. */
    val relaysAsked get() = searchDataSourceState.asked
    val relaysAnswered get() = searchDataSourceState.answered
    val scopePinnedToNotes get() = state.scopePinnedToNotes
    val scope get() = state.scope

    val source get() = state.source
    val followsOnly get() = state.followsOnly
    val sortOrder get() = state.eventSortOrder

    // EVERYTHING `updateDataSource` TOUCHES MUST BE DECLARED ABOVE THIS LINE.
    //
    // `searchTerm` and `sourceWatcher` below are both shared `Eagerly`, so they call
    // `updateDataSource` while the constructor is still running. Kotlin initialises properties in
    // declaration order, so anything that function reads from further down the class is still
    // null at that moment and the app dies opening search. It has happened twice now -- once on
    // `listState`, once on `searchDataSourceState` when a refactor moved it below -- so the two
    // fields it needs live here, above the collectors, and should stay here.
    val listState: LazyListState = LazyListState(0, 0)

    val searchDataSourceState =
        SearchQueryState(
            searchQuery = MutableStateFlow(searchValue),
            account = account,
            searchRelays = account.searchRelayList.flow,
            indexerRelays = account.indexerRelayList.flow,
            followPlusAllMineWithSearchRelays = account.followPlusAllMineWithSearch.flow,
        )

    val searchTerm =
        state.debouncedForRelays
            .map { it.text }
            .onEach(::updateDataSource)
            .stateIn(viewModelScope, SharingStarted.Eagerly, searchValue)

    /**
     * The asynchronous half of the author qualifiers, written back into the box.
     *
     * A `from:name.bit` or `to:alice@example.com` names a person the events themselves never
     * spell out — an event holds keys, not names — so neither NIP-50 nor the cache’s tag scan
     * can match the name the reader typed. Once the name resolves, the token is rewritten in
     * place to the `from:npub1…` / `to:npub1…` a people-picker pick writes, and from then on it
     * is ordinary field text: the parser decodes it into `authors`/`mentions`, the field draws
     * the pill, and both the cache scan and the relay REQs read it with no side channel — the
     * text is the single source of truth, and the rewrite re-issues the search the same way a
     * keystroke would.
     *
     * The free text is never resolved: a bare npub, hex key or `.bit` name with no `from:`
     * stays NIP-50 search text, so the posts citing a person remain searchable.
     */
    private suspend fun rewriteResolvedAuthorNames(text: String) {
        val unresolved = AuthorNameRewrite.unresolvedAuthorNames(text)
        if (unresolved.isEmpty()) return

        var rewritten = text
        // Right to left, so a rewrite never moves the spans still to be rewritten.
        unresolved.asReversed().forEach { token ->
            val resolved = resolvePersonNameOrNull(token.name, nip05Client, namecoinResolver) ?: return@forEach
            AuthorNameRewrite.rewrite(rewritten, token, resolved.pubkey)?.let { rewritten = it } ?: return@forEach
            resolved.relays.forEach { relay ->
                relay.normalizeRelayUrlOrNull()?.let { account.cache.relayHints.addKey(resolved.pubkey, it) }
            }
        }
        // Nothing is written over a box that has moved on: a keystroke since the pause makes
        // the resolution stale, and the newer text is resolved when it settles in its turn.
        if (rewritten != text && searchValue == text) {
            updateSearchValue(rewritten)
        }
    }

    @Suppress("unused")
    val sourceWatcher =
        source
            .onEach { updateDataSource(searchValue) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, SearchSource.RELAYS)

    val directNip05Resolver: Flow<User?> =
        searchTerm
            .debounce(400)
            .mapLatest { term ->
                // NIP-02 petname path (`~/erin/charlie`, `~npub1…/erin`, `~name@domain/erin`),
                // walked over the follow lists already in the cache.
                PetnamePath.parse(term)?.let { path ->
                    return@mapLatest resolvePetnamePath(path)
                }

                val nip05 = directNip05Target(term)
                when {
                    nip05 != null -> {
                        runCatchingCancellable {
                            resolvePersonNameOrNull(nip05.toValue(), nip05Client, namecoinResolver)?.let { info ->
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
                        }.onFailure { if (it is CancellationException) throw it }
                            .getOrNull()
                    }

                    term.startsWithAny(userUriPrefixes) -> {
                        runCatching {
                            Nip19Parser.uriToRoute(term)?.entity?.let { parsed ->
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

                    term.length == 64 && Hex.isHex64(term) -> {
                        account.cache.getOrCreateUser(term)
                    }

                    else -> {
                        null
                    }
                }
            }.flowOn(Dispatchers.IO)

    private suspend fun resolvePetnamePath(path: PetnamePath): User? =
        runCatching {
            PetnameResolver
                .resolve(
                    path = path,
                    currentUser = account.userProfile().pubkeyHex,
                    followListOf = { pubKey ->
                        (account.cache.getAddressableNoteIfExists(ContactListEvent.createAddress(pubKey))?.event as? ContactListEvent)?.tags
                    },
                    resolveNip05 = { identifier ->
                        Nip05Id.parse(identifier)?.let { nip05Client.get(it)?.pubkey }
                    },
                )?.let { account.cache.getOrCreateUser(it) }
        }.onFailure { if (it is CancellationException) throw it }
            .getOrNull()

    /**
     * The routes the box opens on its own, which is now **only** an invite link.
     *
     * It used to auto-navigate on any nip19 code found anywhere in the text, which the token
     * language broke: `from:npub1…` contains an npub, so typing an author filter threw the reader
     * out of the search screen and onto that person's profile mid-query. A pasted code now
     * resolves into the results list instead (see [directEntity]) — still one tap away, but the
     * reader decides when to leave.
     *
     * Invite links survive because they cannot be typed by accident: both require a URL carrying
     * `/invite/`, which no token can produce, and both open a redeem flow rather than a profile
     * or a post.
     */
    val directRouteResolver: Flow<Route?> =
        searchTerm
            .mapLatest { term ->
                if (term.isBlank()) return@mapLatest null

                // A Concord invite link (…/invite/<naddr>#<fragment>) embeds a kind-33301 naddr that
                // Nip19Parser would otherwise extract and route to the generic event screen (which
                // can't render 33301). Detect the invite first and open the redeem flow — the whole
                // URL is carried so the fragment token survives.
                if (ConcordActions.parseInviteLink(term) != null) {
                    return@mapLatest Route.ConcordInvite(term)
                }

                // A pasted Buzz workspace invite (`…/invite/<token>`) opens the in-app join flow.
                if (term.contains("/invite/") && BuzzInviteLink.parse(term) != null) {
                    return@mapLatest Route.BuzzInvite(term)
                }

                null
            }.flowOn(Dispatchers.IO)

    /**
     * The nip19 entity the box holds *in its entirety*, or null.
     *
     * Whole-input only, and that is the point: `Nip19Parser` extracts a code from anywhere in a
     * string, so anything looser matches the npub inside `from:npub1…` and treats an author filter
     * as a request to open that person.
     */
    private fun directEntity(term: String): Entity? {
        val code = wholeInputNip19(term) ?: return null
        return runCatching { Nip19Parser.uriToRoute(code)?.entity }
            .onFailure { if (it is CancellationException) throw it }
            .getOrNull()
    }

    val searchResultsUsers =
        combine(
            state.debounced,
            refreshes,
            directNip05Resolver,
            scope,
            combine(followsOnly, account.kind3FollowList.flow) { only, follows ->
                if (only) follows.authorsPlusMe else null
            },
        ) { input, _, nip05Resolver, currentScope, follows ->
            if (!currentScope.shows(SearchResultKind.PEOPLE)) return@combine emptyList<User>()

            if (nip05Resolver != null) {
                return@combine if (follows == null || nip05Resolver.pubkeyHex in follows) {
                    listOf(nip05Resolver)
                } else {
                    emptyList()
                }
            }

            // The leftover terms, not the whole box: a name search handed `#bitcoin` or
            // `from:npub1…` verbatim matches nobody, and since notes started honouring the
            // tokens, leaving people and channels on the raw text made one box mean two things.
            // A pasted npub/nprofile resolves to its owner even when the cache has never seen
            // them — this is what the auto-navigation used to do, minus the navigation.
            val direct =
                (directEntity(input.text) as? IPubKeyEntity)?.let {
                    LocalCache.consume(it)
                    LocalCache.getUserIfExists(it.hex) ?: LocalCache.getOrCreateUser(it.hex)
                }

            val nameTerm = input.nameTerms
            val found =
                if (nameTerm.isBlank()) emptyList() else LocalCache.search.findUsersStartingWith(nameTerm, account)
            val users = (listOfNotNull(direct) + found).distinctBy { it.pubkeyHex }
            if (follows != null) users.filter { it.pubkeyHex in follows } else users
        }.flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, WhileSubscribed(5000), emptyList())

    val searchResultsNotes =
        combine(
            state.debounced,
            refreshes,
            scope,
            sortOrder,
            combine(followsOnly, account.kind3FollowList.flow) { only, follows ->
                if (only) follows.authorsPlusMe else null
            },
        ) { input, _, currentScope, order, follows ->
            if (!currentScope.shows(SearchResultKind.NOTES)) return@combine emptyList()

            // The same filters the REQ carries, run against the cache — so `from:`, `to:`,
            // `since:`, `#t` and the rest narrow local results exactly as they narrow relay
            // results. A bech32 id typed in full is a lookup, not a search, and keeps its own
            // path through findNotesStartingWith.
            val parsed = input.query
            // A pasted note/nevent/naddr resolves even when the cache has never seen it —
            // what the auto-navigation used to do, minus the navigation. An id resolves to the note
            // that shows that event: the addressable note when it is a track's (or any addressable
            // event's) current version, as the cache scan below returns it, not a second copy.
            val direct =
                when (val entity = directEntity(input.text)) {
                    is NNote -> LocalCache.consume(entity).let { LocalCache.getNoteShowingEventIfExists(entity.hex) ?: LocalCache.getOrCreateNote(entity.hex) }
                    is NEvent -> LocalCache.consume(entity).let { LocalCache.getNoteShowingEventIfExists(entity.hex) ?: LocalCache.getOrCreateNote(entity.hex) }
                    is NAddress -> LocalCache.consume(entity).let { LocalCache.getOrCreateAddressableNote(entity.address()) }
                    else -> null
                }

            val raw =
                when {
                    parsed.isEmpty -> emptyList()
                    // An id, whole or half-typed, is a lookup rather than a search: it matches on
                    // `idHex`, which is not content and so nothing a filter's `search` can reach.
                    // Routed to the scan that knows how to resolve it — and only for text that
                    // could actually be one, so an ordinary query never pays for two scans.
                    looksLikeAnEventId(input.text) -> LocalCache.findNotesStartingWith(input.text, account.hiddenUsers.flow.value)
                    else ->
                        // The same filters the REQ carries, over the same kind window. Built by
                        // the pipeline rather than here, so the cache is asked exactly what the
                        // relays are asked — the window was the last thing the two disagreed on,
                        // the local scan having had none at all and so matching kinds no relay was
                        // ever asked for. Asked flat rather than in RenderableKinds.GROUPS: the
                        // groups exist for a relay's per-filter cap, and a cache has none.
                        LocalCache.findNotesMatching(
                            SearchPipeline.filters(parsed, RenderableKinds.ALL, limit = 200),
                            account.hiddenUsers.flow.value,
                        )
                }
            val withDirect = (listOfNotNull(direct) + raw).distinctBy { it.idHex }
            val followed = if (follows != null) withDirect.filter { it.author?.pubkeyHex in follows } else withDirect

            // The post-filters and the ordering, in that order, from the one place that owns
            // both. Doing it by hand here is what let `-term` and Relevance quietly do nothing:
            // there was no sequence to be missing a step from.
            SearchPipeline.narrowAndOrder(
                items = followed,
                query = parsed,
                order = order,
                event = { it.event },
                zapTotal = { it.zapsAmount.toDoubleValue() },
            )
        }.flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, WhileSubscribed(5000), emptyList())

    /** The search as shortcode fragments ([EmojiSearch.terms]); empty without free text. */
    val emojiSearchTerms =
        state.debounced
            .map { input -> EmojiSearch.terms(input.query) }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, WhileSubscribed(5000), EmojiSearch.Terms.EMPTY)

    /**
     * Single emojis whose shortcode holds the search's terms, out of the emoji packs that match it.
     *
     * Its own scan, of emoji packs only, rather than a filter over [searchResultsNotes]: that list
     * is capped across every kind, so on a common word ("cat") ordinary notes would push the packs
     * out of it, and the emojis with them. The rest of the query (`from:`, `since:`, follows-only)
     * still narrows it, and a `kind:` that leaves emoji packs out turns it off.
     */
    val emojiResults =
        combine(
            state.debounced,
            packRefreshes,
            scope,
            combine(followsOnly, account.kind3FollowList.flow) { only, follows ->
                if (only) follows.authorsPlusMe else null
            },
        ) { input, _, currentScope, follows ->
            if (!currentScope.shows(SearchResultKind.EMOJIS)) return@combine emptyList<EmojiSearchHit>()
            val parsed = input.query
            if (parsed.kinds.isNotEmpty() && EmojiPackEvent.KIND !in parsed.kinds) return@combine emptyList<EmojiSearchHit>()
            val terms = EmojiSearch.terms(parsed)
            if (terms.isEmpty) return@combine emptyList<EmojiSearchHit>()

            val packs =
                LocalCache.findNotesMatching(
                    SearchPipeline.filters(parsed, listOf(EmojiPackEvent.KIND), limit = 500),
                    account.hiddenUsers.flow.value,
                )
            val followed = if (follows != null) packs.filter { it.author?.pubkeyHex in follows } else packs
            EmojiSearch.find(followed, terms)
        }.flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, WhileSubscribed(5000), emptyList())

    val searchResultsPublicChatChannels =
        combine(
            state.debounced,
            refreshes,
            scope,
        ) { input, _, currentScope ->
            if (!currentScope.shows(SearchResultKind.PUBLIC_CHATS)) emptyList() else LocalCache.findPublicChatChannelsStartingWith(input.nameTerms)
        }.flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, WhileSubscribed(5000), emptyList())

    val searchResultsEphemeralChannels =
        combine(
            state.debounced,
            refreshes,
            scope,
        ) { input, _, currentScope ->
            if (!currentScope.shows(SearchResultKind.EPHEMERAL_CHATS)) emptyList() else LocalCache.findEphemeralChatChannelsStartingWith(input.nameTerms)
        }.flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, WhileSubscribed(5000), emptyList())

    val searchResultsLiveActivityChannels =
        combine(
            state.debounced,
            refreshes,
            scope,
        ) { input, _, currentScope ->
            if (!currentScope.shows(SearchResultKind.LIVE_ACTIVITIES)) emptyList() else LocalCache.findLiveActivityChannelsStartingWith(input.nameTerms)
        }.flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, WhileSubscribed(5000), emptyList())

    val hashtagResults =
        combine(
            state.debounced,
            refreshes,
            scope,
        ) { input, _, currentScope ->
            if (!currentScope.shows(SearchResultKind.HASHTAGS)) emptyList() else findHashtags(input.text)
        }.flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, WhileSubscribed(5000), emptyList())

    val relayResults =
        combine(
            state.debounced,
            refreshes,
            scope,
        ) { input, _, currentScope ->
            if (!currentScope.shows(SearchResultKind.RELAYS)) return@combine emptyList()
            val term = input.text
            if (term.length > 1) {
                val isTypingRelay = term.length > 7 && (term.startsWith("wss://") || term.startsWith("ws://"))
                val relayUrl =
                    if (isTypingRelay) {
                        runCatching { NormalizedRelayUrl(Rfc3986.normalize(term)) }.getOrNull()
                    } else {
                        null
                    }
                val lower = term.lowercase()

                val relays =
                    (
                        listOfNotNull(relayUrl) +
                            LocalCache.relayHints.relayDB.filter { _, relay -> relay.url.contains(lower) }
                    ).distinctBy { it.url }

                relays
                    .map { relaySetupInfoBuilder(it) }
                    .sortedByDescending { it.relayStat.receivedBytes }
                    .take(20)
            } else {
                emptyList()
            }
        }.flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, WhileSubscribed(5000), emptyList())

    /** True when the box holds something to search for — which is not the same as searching. */
    val hasQuery = derivedStateOf { searchValue.isNotBlank() }

    /**
     * [state]'s `settled`, mirrored into snapshot state.
     *
     * Reading `state.settled.value` inside [derivedStateOf] looked right and could never work: a
     * StateFlow read is invisible to the snapshot system, so the derivation only re-ran when
     * `searchValue` changed. The spinner therefore started on the first keystroke and stayed lit
     * for as long as the box had text, long after the results were on screen. The empty state two
     * hundred lines down reads the same flow through `collectAsStateWithLifecycle` and has always
     * been correct, which is why "nothing found" timed out while the spinner did not.
     */
    private var settled by mutableStateOf(false)
    private var relaysAskedNow by mutableStateOf<Set<NormalizedRelayUrl>>(emptySet())
    private var relaysAnsweredNow by mutableStateOf<Set<NormalizedRelayUrl>>(emptySet())

    /**
     * Stops the spinner claiming to wait forever on a relay that will never answer.
     *
     * Some relays simply never send EOSE. Without a ceiling the spinner would turn for the life
     * of the screen, which is the behaviour this was meant to end.
     */
    private var pastDeadline by mutableStateOf(false)

    init {
        viewModelScope.launch { state.settled.collect { settled = it } }
        viewModelScope.launch { searchDataSourceState.asked.collect { relaysAskedNow = it } }
        viewModelScope.launch { searchDataSourceState.answered.collect { relaysAnsweredNow = it } }
        viewModelScope.launch {
            state.text.collectLatest {
                pastDeadline = false
                delay(RELAY_WAIT_CEILING_MS)
                pastDeadline = true
            }
        }
        // The same pause the cache scan waits for: typing in flight resolves nothing, and a
        // keystroke landing mid-lookup cancels it — the newer text is resolved when it settles
        // in its turn.
        viewModelScope.launch(Dispatchers.IO) {
            state.debounced.collectLatest { input -> rewriteResolvedAuthorNames(input.text) }
        }
    }

    /** The relays this query went to that have not sent EOSE yet. */
    val relaysWaiting = derivedStateOf { relaysAskedNow - relaysAnsweredNow }

    /**
     * True while a search is actually under way.
     *
     * This is [InvalidatableContent]'s contract, and every other implementor uses it to mean "a
     * refresh is running". Search had it returning `searchValue.isNotBlank()` — "the box has
     * text" — so three call sites read a name that said one thing and meant another, and nothing
     * on screen could say whether results were still coming. Both meanings now exist under their
     * own names, and the field shows this one as a spinner.
     *
     * No EOSE from the search subscription reaches this screen, so "under way" is the same
     * heuristic the empty state already trusts: the grace window since the query last changed.
     */
    override val isRefreshing =
        derivedStateOf {
            // The timer is the floor -- something has to show in the moment before any relay can
            // possibly reply. After that the real signal takes over: a relay that has not sent
            // EOSE is one the results are still missing, and the popup on the spinner names it.
            searchValue.isNotBlank() &&
                (!settled || (relaysWaiting.value.isNotEmpty() && !pastDeadline))
        }

    /**
     * Could this text name an event rather than describe one? A bech32 pointer, or a run of hex
     * long enough that it is nobody's search term.
     */
    private fun looksLikeAnEventId(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.contains(' ')) return false
        if (decodeEventIdAsHexOrNull(trimmed) != null) return true
        return trimmed.length >= 8 && trimmed.all { it in "0123456789abcdefABCDEF" }
    }

    override fun invalidateData(ignoreIfDoing: Boolean) {
        manualInvalidations.update { it + 1 }
    }

    fun updateSearchValue(newValue: String) {
        searchValue = newValue
        state.updateText(newValue)
    }

    fun clear() = updateSearchValue("")

    suspend fun updateDataSource(searchTerm: String) {
        if (searchTerm.isBlank() || source.value == SearchSource.LOCAL) {
            searchDataSourceState.searchQuery.tryEmit("")
        } else {
            searchDataSourceState.searchQuery.tryEmit(searchTerm)
            listState.scrollToItem(0, 0)
        }
    }

    fun updateScope(newScope: SearchScope) = state.updateScope(newScope)

    fun updateSource(newSource: SearchSource) = state.updateSource(newSource)

    fun updateFollowsOnly(value: Boolean) = state.updateFollowsOnly(value)

    fun updateSortOrder(order: SearchSortOrder) = state.updateEventSortOrder(order)

    fun isSearchingFun() = searchValue.isNotBlank()

    companion object {
        /** How long the spinner will admit to waiting on a relay that has gone quiet. */
        private const val RELAY_WAIT_CEILING_MS = 12_000L

        /**
         * At most one cache-driven rescan per this long.
         *
         * Sampled rather than debounced, and that distinction is the whole of it: a debounce waits
         * for quiet, and a cache taking in a search's own results — plus whatever the rest of the
         * app is subscribed to — may not go quiet for seconds. The list would have stalled exactly
         * when it had the most to show. Sampling caps the cost instead, and guarantees the scan
         * runs while events are still arriving.
         *
         * Wider than [SearchState.LOCAL_DEBOUNCE_MS] because this one walks the cache without the
         * reader having asked for anything.
         */
        private const val RESCAN_INTERVAL_MS = 400L

        /**
         * The kinds an arriving bundle has to contain before it is worth looking again.
         *
         * Everything the seven result lists are built from: [RenderableKinds.ALL] for notes and
         * for the public-chat and live-activity channels, `kind 0` for people, and the ephemeral
         * chat kind for the rooms that have no definition event of their own. A relay delivering
         * anything else — a chat message, a DM, a reaction, a zap receipt, which is most of what
         * a running app takes in — cannot change what this screen shows, and a rescan for it is a
         * walk of the whole cache for nothing.
         *
         * Add to this when a result list starts reading a new kind. Getting it wrong costs a late
         * update rather than a wrong one: the next keystroke, or the next relevant bundle,
         * recomputes everything anyway.
         */
        private val WATCHED_KINDS = RenderableKinds.ALL.toSet() + MetadataEvent.KIND + EphemeralChatEvent.KIND
    }

    class Factory(
        val account: Account,
        val nip05: INip05Client,
        val namecoinResolver: NamecoinNameResolver,
        val historyStorage: SearchHistoryStorage,
        val initialQuery: String? = null,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(
            modelClass: KClass<T>,
            extras: CreationExtras,
        ): T = SearchBarViewModel(account, nip05, namecoinResolver, historyStorage, initialQuery) as T
    }
}

/**
 * The NIP-05 address the search field asks to open directly: `user@domain`, or a bare `.bit`
 * domain. The `.bit` case goes through [namecoinLookupTerm], like the inline Namecoin row, so a
 * seeded chip such as `kind:git ` is not glued to the name typed after it and looked up as
 * `_@kind:git name.bit`. `user@domain` needs no such gate: [Nip05Id.parse] rejects anything with
 * spaces or a `:` in it.
 */
internal fun directNip05Target(term: String): Nip05Id? =
    if (term.contains('@')) {
        Nip05Id.parse(term)
    } else {
        namecoinLookupTerm(term)
            ?.takeIf { it.endsWith(".bit", ignoreCase = true) }
            // Bare .bit domain → synthesize _@domain.bit
            ?.let { Nip05Id("_", it.lowercase()) }
    }

/**
 * The key and relays a single person *name* resolves to, or null when nothing resolves — in
 * which case the name stays a name.
 *
 * The one routing table the box’s name lookups share — the direct open above and the
 * `from:`/`to:` token rewrite — so a name means the same key whichever way it was asked:
 * a NIP-05 address (`alice@example.com`, and the `user@domain.bit` spellings the client
 * routes through Namecoin itself), a bare `.bit` domain (looked up as its `_@domain.bit`
 * wildcard, the same synthesis [directNip05Target] detects for the whole box), or a
 * Namecoin-only `d/`/`id/` identifier the NIP-05 grammar cannot express.
 */
internal suspend fun resolvePersonNameOrNull(
    name: String,
    nip05Client: INip05Client,
    namecoinResolver: NamecoinNameResolver,
): Nip05KeyInfo? =
    runCatching {
        when {
            name.contains('@') -> Nip05Id.parse(name)?.let { nip05Client.get(it) }
            name.endsWith(".bit", ignoreCase = true) -> nip05Client.get(Nip05Id("_", name.lowercase()))
            // The Namecoin-only shapes the NIP-05 grammar rejects: `d/<name>`, `id/<name>`.
            NamecoinNameResolver.isNamecoinIdentifier(name) ->
                namecoinResolver.resolve(name)?.let { Nip05KeyInfo(it.pubkey, it.relays) }

            else -> null
        }
    }.onFailure { if (it is CancellationException) throw it }
        .getOrNull()
