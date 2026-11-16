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
package com.vitorpamplona.amethyst.commons.nipsOnNostr

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.service.http.IRoleBasedHttpClientBuilder
import com.vitorpamplona.quartz.experimental.nipsOnNostr.NipTextEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** An official NIP being loaded from the nips repo. */
@Immutable
sealed interface OfficialNipState {
    data object Loading : OfficialNipState

    data object Failed : OfficialNipState

    @Immutable
    class Loaded(
        val view: NipTextView,
    ) : OfficialNipState
}

/** Loads official NIP [number] for the in-app reader. */
class OfficialNipViewModel(
    val number: String,
    private val http: IRoleBasedHttpClientBuilder,
) : ViewModel() {
    private val _state = MutableStateFlow<OfficialNipState>(OfficialNipState.Loading)
    val state: StateFlow<OfficialNipState> = _state

    init {
        load()
    }

    fun load() {
        _state.value = OfficialNipState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            val text = OfficialNips.text(number, http)
            _state.value = if (text == null) OfficialNipState.Failed else OfficialNipState.Loaded(NipTextView.ofMarkdown(number, text))
        }
    }
}

/** Which NIPs the catalogue lists. */
enum class NipCatalogFilter {
    ALL,
    OFFICIAL,
    ON_NOSTR,
}

/** The nips repo's index as the NIPs screen shows it: filtered by the search and the chips. */
@Immutable
class OfficialIndexState(
    val entries: List<OfficialNipEntry>,
    val loading: Boolean,
    val failed: Boolean,
)

/**
 * Backs the NIPs screen apart from its feed: the nips repo's index (fetched once, from GitHub),
 * the search [query] and the [filter] chips. The NIPs published on Nostr are the account's NIPs
 * feed (AccountFeedContentStates.nipsFeed), kept live by its subscription; [onNostr] filters and
 * ranks that feed's notes for the search.
 */
class NipCatalogViewModel(
    private val http: IRoleBasedHttpClientBuilder,
) : ViewModel() {
    val query = MutableStateFlow("")
    val filter = MutableStateFlow(NipCatalogFilter.ALL)

    private val entries = MutableStateFlow<List<OfficialNipEntry>>(emptyList())
    private val loading = MutableStateFlow(true)
    private val failed = MutableStateFlow(false)

    val official: StateFlow<OfficialIndexState> =
        combine(entries, loading, failed, query, filter) { entries, loading, failed, query, filter ->
            val q = query.trim()
            OfficialIndexState(
                entries = if (filter == NipCatalogFilter.ON_NOSTR) emptyList() else entries.filter { matches(it, q) },
                loading = loading,
                failed = failed,
            )
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OfficialIndexState(emptyList(), true, false))

    init {
        loadOfficial()
    }

    /** Loads the nips repo's index; also the "Try again" after it failed. */
    fun loadOfficial() {
        viewModelScope.launch(Dispatchers.IO) {
            loading.value = true
            val index = OfficialNips.index(http)
            failed.value = index == null
            if (index != null) entries.value = index
            loading.value = false
        }
    }

    /**
     * The NIPs on Nostr of [notes] (the feed, newest first) that [query] matches under [filter],
     * strongest first: a match in the title, identifier or a defined kind before one only in the
     * text. Call it off the main thread: a text match reads every NIP's body.
     */
    fun onNostr(
        notes: List<Note>,
        query: String,
        filter: NipCatalogFilter,
    ): List<Note> {
        if (filter == NipCatalogFilter.OFFICIAL) return emptyList()
        val q = query.trim()
        if (q.isEmpty()) return notes
        val strong = ArrayList<Note>()
        val inText = ArrayList<Note>()
        val kind = q.toIntOrNull()
        for (note in notes) {
            val event = note.event as? NipTextEvent ?: continue
            when {
                event.title()?.contains(q, ignoreCase = true) == true ||
                    event.dTag().contains(q, ignoreCase = true) ||
                    (kind != null && definesKind(event.tags, kind)) -> strong.add(note)

                q.length >= 3 && event.content.contains(q, ignoreCase = true) -> inText.add(note)
            }
        }
        return strong + inText
    }

    private fun matches(
        entry: OfficialNipEntry,
        q: String,
    ): Boolean {
        if (q.isEmpty()) return true
        val number = NipLinks.normalize(q)
        if (number != null && entry.number == number) return true
        if (entry.title.contains(q, ignoreCase = true)) return true
        val kind = q.toIntOrNull() ?: return false
        return officialKinds(entry.number).any { it.kind == kind }
    }
}
