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
package com.vitorpamplona.amethyst.commons.search

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest

/**
 * What a search *is*, minus where its results come from.
 *
 * Both front ends had grown their own copy of this — the text, the parse, the debounce, the
 * scope, the sort orders, "is there anything to say yet" — and the copies disagreed on every one
 * of them. Android parsed the same string about nine times per keystroke across four different
 * debounce windows; desktop parsed once but had no scope at all. Neither difference was a
 * decision.
 *
 * So it lives here once, and each front end keeps only what is genuinely its own: how it asks for
 * results (a cache scan on Android, relay callbacks on desktop), what result kinds it can render,
 * and its own screen.
 *
 * ## The text is the query
 *
 * There is exactly one authority for what is being searched, and it is [text] — what is in the
 * box. [edit] exists for controls that build a filter without typing (a kind picker, a form
 * panel), and all it does is write the token into the box: the reader sees the same text they
 * could have typed, can edit it by hand, and the chips they see are that text rendered. Desktop
 * carried a `ChangeSource` flag to decide whether to show the raw text or the serialized query;
 * with the box authoritative there is nothing to decide.
 *
 * CLI-safe: flows and data, no Compose.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchState(
    private val coroutineScope: CoroutineScope,
    initialText: String = "",
    /** How still the box must be before the cache is scanned. See [debounced]. */
    private val localDebounceMs: Long = LOCAL_DEBOUNCE_MS,
    /** How still the box must be before relays are asked. See [debouncedForRelays]. */
    private val relayDebounceMs: Long = RELAY_DEBOUNCE_MS,
    private val settleMs: Long = DEFAULT_SETTLE_MS,
) {
    private val _text = MutableStateFlow(initialText)

    /** What is in the box, which is the only authority for what is being searched. */
    val text: StateFlow<String> = _text.asStateFlow()

    /**
     * [text] and its parse, together. Parsed once — not once per collector.
     *
     * The pair travels as one value rather than as two flows because a collector needs both and
     * they must agree: the relay finder and the id lookup read the raw text, everything else reads
     * the query, and two separately debounced flows would let a collector combine last
     * keystroke's text with this one's parse. Every reader of a search's structure comes through
     * here, including the ones that only want the leftover words
     * (`current.value.query.nameSearchTerms()`) — which is what the front ends were re-parsing
     * the whole box to get, about nine times per keystroke on Android.
     *
     * The debounced views below are debounces *of this flow* rather than of the text, so a
     * keystroke is parsed once and the same [SearchInput] instance reaches all three. Debouncing
     * the text and parsing again per window was three parses for one answer.
     */
    val current: StateFlow<SearchInput> =
        _text
            .map { SearchInput(it) }
            .stateIn(coroutineScope, SharingStarted.Eagerly, SearchInput(initialText))

    /**
     * [current] once the reader has paused, for answers that cost a cache scan.
     *
     * There are two windows and only two, each named for what it protects. This one was written
     * eight times over on Android — every result flow declared its own `.debounce(100)` — which
     * is not eight policies but one policy said eight times, and so one policy that could drift.
     */
    val debounced: StateFlow<SearchInput> =
        current
            .debounce(localDebounceMs)
            .stateIn(coroutineScope, SharingStarted.Eagerly, current.value)

    /**
     * [current] once the reader has paused longer, for answers that cost a round trip.
     *
     * Wider than [debounced] because a REQ opens a subscription on every search relay, and
     * withdrawing it a keystroke later is traffic nobody wanted — not because a relay is slower
     * to read.
     */
    val debouncedForRelays: StateFlow<SearchInput> =
        current
            .debounce(relayDebounceMs)
            .stateIn(coroutineScope, SharingStarted.Eagerly, current.value)

    /** Shorthand for `current.value.query`, which is what most callers mean. */
    val query: SearchQuery get() = current.value.query

    /**
     * True when the box holds filters but none a relay can be asked for.
     *
     * A bare `kind:` window is the case that matters: [SearchFilterBuilder] refuses it, because
     * "every recent article" is an unbounded REQ rather than a search. A screen that seeds its
     * kind therefore opens holding a chip and showing nothing, which looks broken unless the box
     * says what it is waiting for.
     */
    val asksNothing: StateFlow<Boolean> =
        current
            .map { !it.query.isEmpty && SearchFilterBuilder.build(it.query).isEmpty() }
            .distinctUntilChanged()
            .stateIn(coroutineScope, SharingStarted.Eagerly, false)

    /**
     * True once enough time has passed since the query last changed that "nothing found" is a
     * fair thing to say.
     *
     * A heuristic, and deliberately so: no EOSE from the search subscription reaches the screen,
     * so nothing actually knows the relays have finished. Without the delay an empty list would
     * announce failure in the gap before the first event arrives — which is every search, for a
     * moment.
     */
    val settled: StateFlow<Boolean> =
        _text
            .transformLatest {
                emit(false)
                delay(settleMs)
                emit(true)
            }.stateIn(coroutineScope, SharingStarted.Eagerly, false)

    /** The scope the reader picked, which is not always the one that applies — see [scope]. */
    private val _pickedScope = MutableStateFlow(SearchScope.ALL)
    val pickedScope: StateFlow<SearchScope> = _pickedScope.asStateFlow()

    /**
     * True while the box holds any filter beyond its free text — see [SearchQuery.pinsToNotes].
     *
     * The People half of the toggle answers one NIP-50 string against kind 0 and nothing else, so
     * a scope that includes People drops every token the reader typed without saying so. Both All
     * and People are therefore taken away while a token is in the box, and given back the moment
     * the last chip goes.
     */
    val scopePinnedToNotes: StateFlow<Boolean> =
        current
            .map { it.query.pinsToNotes }
            .distinctUntilChanged()
            .stateIn(coroutineScope, SharingStarted.Eagerly, false)

    /**
     * The scope that actually applies: the reader's pick, unless the query names a kind.
     *
     * Derived rather than written back over [pickedScope] on purpose — dropping the `kind:` chip
     * has to give the reader the scope they chose before, not leave them pinned to Notes by a
     * filter that is no longer there.
     */
    val scope: StateFlow<SearchScope> =
        combine(_pickedScope, scopePinnedToNotes) { picked, pinned ->
            if (pinned) SearchScope.NOTES else picked
        }.stateIn(coroutineScope, SharingStarted.Eagerly, SearchScope.ALL)

    private val _eventSortOrder = MutableStateFlow(SearchSortOrder.EVENT_DEFAULT)
    val eventSortOrder: StateFlow<SearchSortOrder> = _eventSortOrder.asStateFlow()

    private val _peopleSortOrder = MutableStateFlow(SearchSortOrder.PEOPLE_DEFAULT)
    val peopleSortOrder: StateFlow<SearchSortOrder> = _peopleSortOrder.asStateFlow()

    /** Whether results are narrowed to the reader's follows. */
    private val _followsOnly = MutableStateFlow(false)
    val followsOnly: StateFlow<Boolean> = _followsOnly.asStateFlow()

    /** Whether relays are asked at all, or only what is already in the cache. */
    private val _source = MutableStateFlow(SearchSource.RELAYS)
    val source: StateFlow<SearchSource> = _source.asStateFlow()

    /** Typing, pasting, or a screen seeding the box: all the same thing. */
    fun updateText(raw: String) {
        _text.value = raw
    }

    /**
     * A control that adds or removes a filter without the reader typing it.
     *
     * The new query is written back into the box as text, so a button press and a typed token
     * produce the same state and the same chips — and the reader can undo either one the same
     * way, by editing the words.
     */
    fun edit(transform: (SearchQuery) -> SearchQuery) {
        _text.value = QuerySerializer.serialize(transform(query))
    }

    fun updateScope(newScope: SearchScope) {
        _pickedScope.value = newScope
    }

    fun updateEventSortOrder(order: SearchSortOrder) {
        _eventSortOrder.value = order
    }

    fun updatePeopleSortOrder(order: SearchSortOrder) {
        _peopleSortOrder.value = order
    }

    fun updateFollowsOnly(value: Boolean) {
        _followsOnly.value = value
    }

    fun updateSource(newSource: SearchSource) {
        _source.value = newSource
    }

    /** Empties the box and puts every control back where it started. */
    fun clear() {
        _text.value = ""
        _pickedScope.value = SearchScope.ALL
        _eventSortOrder.value = SearchSortOrder.EVENT_DEFAULT
        _peopleSortOrder.value = SearchSortOrder.PEOPLE_DEFAULT
        _followsOnly.value = false
    }

    companion object {
        /** See [debounced]. */
        const val LOCAL_DEBOUNCE_MS = 100L

        /** See [debouncedForRelays]. */
        const val RELAY_DEBOUNCE_MS = 300L

        /** How long after the last keystroke an empty result list is allowed to say so. */
        const val DEFAULT_SETTLE_MS = 1200L
    }
}
