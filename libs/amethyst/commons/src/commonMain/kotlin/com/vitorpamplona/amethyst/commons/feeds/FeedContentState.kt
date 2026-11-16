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
package com.vitorpamplona.amethyst.commons.feeds

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.service.BasicBundledInsert
import com.vitorpamplona.amethyst.commons.service.BasicBundledUpdate
import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.equalImmutableLists
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.nip09Deletions.DeletionRequestEvent
import com.vitorpamplona.quartz.utils.flattenToSet
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Stable
class FeedContentState(
    val localFilter: IFeedFilter<Note>,
    val viewModelScope: CoroutineScope,
    val cacheProvider: ICacheProvider,
) : InvalidatableContent {
    private val _feedContent = MutableStateFlow<FeedState>(FeedState.Loading)
    val feedContent = _feedContent.asStateFlow()

    // Simple counter that changes when it needs to invalidate everything
    private val _scrollToTop = MutableStateFlow<Int>(0)
    val scrollToTop = _scrollToTop.asStateFlow()
    var scrollToTopPending = false

    private var lastFeedKey: Any? = null

    override val isRefreshing: MutableState<Boolean> = mutableStateOf(false)

    val lastNoteCreatedAtWhenFullyLoaded = MutableStateFlow<Long?>(null)

    fun sendToTop() {
        if (scrollToTopPending) return

        scrollToTopPending = true
        viewModelScope.launch(Dispatchers.IO) { _scrollToTop.emit(_scrollToTop.value + 1) }
    }

    suspend fun sentToTop() {
        scrollToTopPending = false
    }

    private fun refresh() {
        viewModelScope.launch(Dispatchers.IO) { refreshSuspended() }
    }

    fun visibleNotes(): List<Note> {
        val currentState = _feedContent.value
        return if (currentState is FeedState.Loaded) {
            currentState.feed.value.list
        } else {
            emptyList()
        }
    }

    fun lastNoteCreatedAtIfFilled() = lastNoteCreatedAtWhenFullyLoaded.value

    // The writers below — the insert bundler, the full refresh, deletions and the memory-pressure
    // trim — run on different IO coroutines, and each reads the list, transforms it and writes it
    // back. Interleaved, the slower one overwrote the other: a deletion landing while an insert
    // batch merged was undone by the insert's stale copy, and the deleted note stayed until the
    // next full rebuild.
    //
    // The slow part (loadTop, the filter merge) runs with no lock held, so a deletion or a trim
    // never queues behind a rebuild. Only the commit is serialized, and it is short: it drops
    // whatever was deleted while that writer computed, which is what the stale copy brought back.
    private val commitLock = KmpLock()

    // Guarded by [commitLock]. Deletions are remembered only while a writer is computing, and
    // forgotten once none is, so the set never outlives the writes that need it.
    private var writersInFlight = 0
    private val deletedWhileWriting = mutableSetOf<Note>()

    /**
     * Runs [compute] with no lock held, then commits the list it returns minus the notes deleted
     * in the meantime. A null result commits nothing.
     */
    private inline fun write(compute: () -> ImmutableList<Note>?) {
        commitLock.withLock { writersInFlight++ }
        var notes: ImmutableList<Note>? = null
        try {
            notes = compute()
        } finally {
            commitLock.withLock {
                try {
                    val computed = notes
                    if (computed != null) {
                        commitLocked(
                            if (deletedWhileWriting.isEmpty()) {
                                computed
                            } else {
                                computed.filterNot { it in deletedWhileWriting }.toImmutableList()
                            },
                        )
                    }
                } finally {
                    writersInFlight--
                    if (writersInFlight == 0) deletedWhileWriting.clear()
                }
            }
        }
    }

    private fun commitLocked(notes: ImmutableList<Note>) {
        val current = _feedContent.value
        if (current is FeedState.Loaded && equalImmutableLists(notes, current.feed.value.list)) return
        updateFeed(notes)
    }

    fun refreshSuspended() {
        isRefreshing.value = true
        try {
            write {
                lastFeedKey = localFilter.feedKey()
                localFilter.loadTop().distinctBy { it.idHex }.toImmutableList()
            }
        } finally {
            isRefreshing.value = false
        }
    }

    private fun updateFeed(notes: ImmutableList<Note>) {
        if (notes.size >= localFilter.limit()) {
            // feeds might not be sorted by created at, so full search
            val lastNoteTime = notes.minOfOrNull { it.createdAt() ?: Long.MAX_VALUE }
            if (lastNoteTime != lastNoteCreatedAtWhenFullyLoaded.value) {
                lastNoteCreatedAtWhenFullyLoaded.tryEmit(lastNoteTime)
            }
        } else {
            lastNoteCreatedAtWhenFullyLoaded.tryEmit(null)
        }

        val currentState = _feedContent.value
        if (notes.isEmpty()) {
            _feedContent.tryEmit(FeedState.Empty)
        } else if (currentState is FeedState.Loaded) {
            currentState.feed.tryEmit(LoadedFeedState(notes, localFilter.showHiddenKey()))
        } else {
            _feedContent.tryEmit(
                FeedState.Loaded(MutableStateFlow(LoadedFeedState(notes, localFilter.showHiddenKey()))),
            )
        }
    }

    fun deleteFromFeed(deletedNotes: Set<Note>) =
        commitLock.withLock {
            if (writersInFlight > 0) deletedWhileWriting.addAll(deletedNotes)
            val feed = _feedContent.value
            if (feed is FeedState.Loaded) {
                updateFeed((feed.feed.value.list - deletedNotes).toImmutableList())
            }
        }

    fun trimToSize(maxItems: Int) =
        commitLock.withLock {
            val current = _feedContent.value
            if (current is FeedState.Loaded) {
                val loaded = current.feed.value
                if (loaded.list.size > maxItems) {
                    current.feed.tryEmit(LoadedFeedState(loaded.list.take(maxItems).toImmutableList(), loaded.showHidden))
                }
            }
        }

    fun refreshFromOldState(newItems: Set<Note>) {
        var refreshEverything = false
        write {
            // Read inside [write], so a deletion from here on is either already in this copy or
            // remembered for the commit.
            val oldNotesState = _feedContent.value
            if (localFilter is AdditiveFeedFilter && lastFeedKey == localFilter.feedKey()) {
                if (oldNotesState is FeedState.Loaded) {
                    val deletionEvents: List<DeletionRequestEvent> = newItems.mapNotNull { it.event as? DeletionRequestEvent }

                    val oldList =
                        if (deletionEvents.isEmpty()) {
                            oldNotesState.feed.value.list
                        } else {
                            oldNotesState.feed.value.list
                                .filter {
                                    val noteEvent = it.event
                                    if (noteEvent != null) {
                                        !cacheProvider.hasBeenDeleted(noteEvent)
                                    } else {
                                        // An event-less row is a placeholder the filter synthesized for a room
                                        // that has no message yet — a just-joined Concord channel, NIP-29 group,
                                        // Marmot group or geohash cell. It carries no event, so it cannot have
                                        // been deleted, and dropping it here deleted every such row from the
                                        // Messages list the moment ANY kind-5 landed in an unrelated batch. The
                                        // row then stayed gone until the next full rebuild, which is why a quiet
                                        // community looked like it had never loaded at all.
                                        true
                                    }
                                }.toImmutableList()
                        }

                    localFilter
                        .updateListWith(oldList, newItems)
                        .distinctBy { it.idHex }
                        .toImmutableList()
                } else if (oldNotesState is FeedState.Empty) {
                    localFilter
                        .updateListWith(emptyList(), newItems)
                        .distinctBy { it.idHex }
                        .toImmutableList()
                        .ifEmpty { null }
                } else {
                    refreshEverything = true
                    null
                }
            } else {
                refreshEverything = true
                null
            }
        }
        if (refreshEverything) refreshSuspended()
    }

    private val bundler = BasicBundledUpdate(250, Dispatchers.IO, viewModelScope)
    private val bundlerInsert = BasicBundledInsert<Set<Note>>(250, Dispatchers.IO, viewModelScope)

    override fun invalidateData(ignoreIfDoing: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            bundler.invalidate(ignoreIfDoing) {
                // adds the time to perform the refresh into this delay
                // holding off new updates in case of heavy refresh routines.
                refreshSuspended()
            }
        }
    }

    fun checkKeysInvalidateDataAndSendToTop() {
        if (lastFeedKey != localFilter.feedKey()) {
            bundler.invalidate(false) {
                // adds the time to perform the refresh into this delay
                // holding off new updates in case of heavy refresh routines.
                refreshSuspended()
                sendToTop()
            }
        }
    }

    fun invalidateInsertData(newItems: Set<Note>) {
        bundlerInsert.invalidateList(newItems) {
            refreshFromOldState(it.flattenToSet())
        }
    }

    fun updateFeedWith(newNotes: Set<Note>) {
        if (
            localFilter is AdditiveFeedFilter &&
            (_feedContent.value is FeedState.Loaded || _feedContent.value is FeedState.Empty)
        ) {
            invalidateInsertData(newNotes)
        } else {
            // Refresh Everything
            invalidateData()
        }
    }
}
