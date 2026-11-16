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
package com.vitorpamplona.amethyst.commons.notifications

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import com.vitorpamplona.amethyst.commons.feeds.AdditiveFeedFilter
import com.vitorpamplona.amethyst.commons.feeds.Card
import com.vitorpamplona.amethyst.commons.feeds.CardFeedState
import com.vitorpamplona.amethyst.commons.feeds.FeedFilter
import com.vitorpamplona.amethyst.commons.feeds.InvalidatableContent
import com.vitorpamplona.amethyst.commons.feeds.LoadedFeedState
import com.vitorpamplona.amethyst.commons.feeds.NotificationFeedOrderCard
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.CombinedZap
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzChannelInvite
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache.getNoteIfExists
import com.vitorpamplona.amethyst.commons.model.chats.ConcordDirectInviteNote
import com.vitorpamplona.amethyst.commons.model.marmotGroups.MarmotGroupChatroom
import com.vitorpamplona.amethyst.commons.notifications.dal.NotificationFeedFilter
import com.vitorpamplona.amethyst.commons.search.calendar.LocalClock
import com.vitorpamplona.amethyst.commons.service.BundledInsert
import com.vitorpamplona.amethyst.commons.service.BundledUpdate
import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.equalImmutableLists
import com.vitorpamplona.amethyst.commons.util.logTime
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.buzz.notifications.MemberAddedNotificationEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip04Dm.messages.EncryptedDmEvent
import com.vitorpamplona.quartz.nip17Dm.base.NIP17Group
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelMetadataEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapReceiptEvent
import com.vitorpamplona.quartz.nip58Badges.award.BadgeAwardEvent
import com.vitorpamplona.quartz.nip61Nutzaps.nutzap.NutzapEvent
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.flattenToSet
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@Stable
class CardFeedContentState(
    val localFilter: FeedFilter<Note>,
    val viewModelScope: CoroutineScope,
) : InvalidatableContent {
    private val _feedContent = MutableStateFlow<CardFeedState>(CardFeedState.Loading)
    val feedContent = _feedContent.asStateFlow()

    // Simple counter that changes when it needs to invalidate everything
    private val _scrollToTop = MutableStateFlow(0)
    val scrollToTop = _scrollToTop.asStateFlow()
    var scrolltoTopPending = false

    private var lastFeedKey: Any? = null

    override val isRefreshing: MutableState<Boolean> = mutableStateOf(false)

    val lastNoteCreatedAtWhenFullyLoaded = MutableStateFlow<Long?>(null)

    private var lastAccount: Account? = null
    private var lastNotes: Set<Note>? = null

    // [clear] says "rebuild from scratch", but a refresh already computing from the old answers
    // (verdicts, mutes) would otherwise write its notes back after the clear, and the next refresh
    // would only add to them. Each clear starts a generation; [lastNotes] counts as the base for
    // an additive refresh only when it was built in the current one.
    @OptIn(ExperimentalAtomicApi::class)
    private val generation = AtomicLong(0L)
    private var builtGeneration = -1L

    fun sendToTop() {
        if (scrolltoTopPending) return

        scrolltoTopPending = true
        viewModelScope.launch(Dispatchers.IO) { _scrollToTop.emit(_scrollToTop.value + 1) }
    }

    fun sentToTop() {
        scrolltoTopPending = false
    }

    fun visibleNotes(): List<Card> {
        val currentState = _feedContent.value
        return if (currentState is CardFeedState.Loaded) {
            currentState.feed.value.list
        } else {
            emptyList()
        }
    }

    fun lastNoteCreatedAtIfFilled() = lastNoteCreatedAtWhenFullyLoaded.value

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) { refreshSuspended() }
    }

    // Refreshes can arrive from several IO coroutines at once; they must not interleave.
    private val refreshLock = KmpLock()

    private fun refreshSuspended() = refreshLock.withLock { refreshLocked() }

    @OptIn(ExperimentalAtomicApi::class)
    private fun refreshLocked() {
        try {
            isRefreshing.value = true

            // Read before the filter runs: a clear after this point marks the result stale.
            val gen = generation.load()
            val notes = localFilter.feed()
            lastFeedKey = localFilter.feedKey()

            val thisAccount = (localFilter as? NotificationFeedFilter)?.account
            val lastNotesCopy = if (thisAccount == lastAccount && builtGeneration == gen) lastNotes else null

            val oldNotesState = _feedContent.value
            if (lastNotesCopy != null && oldNotesState is CardFeedState.Loaded) {
                val newCards = convertToCard(notes.minus(lastNotesCopy))
                if (newCards.isNotEmpty()) {
                    lastNotes = notes.toSet()
                    builtGeneration = gen
                    lastAccount = (localFilter as? NotificationFeedFilter)?.account

                    val updatedCards =
                        (oldNotesState.feed.value.list + newCards)
                            .distinctBy { it.id() }
                            .sortedWith(NotificationFeedOrderCard)
                            .take(localFilter.limit())
                            .toImmutableList()

                    if (!equalImmutableLists(oldNotesState.feed.value.list, updatedCards)) {
                        updateFeed(updatedCards)
                    }
                }
            } else {
                lastNotes = notes.toSet()
                builtGeneration = gen
                lastAccount = (localFilter as? NotificationFeedFilter)?.account

                val cards =
                    convertToCard(notes)
                        .sortedWith(NotificationFeedOrderCard)
                        .take(localFilter.limit())
                        .toImmutableList()

                updateFeed(cards)
            }
        } finally {
            isRefreshing.value = false
        }
    }

    private fun convertToCard(notes: Collection<Note>): List<Card> {
        val reactionsPerEvent = mutableMapOf<Note, MutableList<Note>>()
        notes
            .filter { it.event is ReactionEvent }
            .forEach { note ->
                val reactedPost =
                    note.replyTo?.lastOrNull {
                        it.event !is ChannelMetadataEvent && it.event !is ChannelCreateEvent
                    }
                if (reactedPost != null) {
                    reactionsPerEvent.getOrPut(reactedPost) { mutableListOf() }.add(note)
                }
            }

        // val reactionCards = reactionsPerEvent.map { LikeSetCard(it.key, it.value) }
        val zapsPerUser = mutableMapOf<User, MutableList<CombinedZap>>()
        val zapsPerEvent = mutableMapOf<Note, MutableList<CombinedZap>>()
        notes
            .filter { it.event is ZapReceiptEvent }
            .forEach { zapEvent ->
                val zappedPost = zapEvent.replyTo?.lastOrNull()
                if (zappedPost != null) {
                    val zapRequest =
                        zappedPost.zaps
                            .filter { it.value == zapEvent }
                            .keys
                            .firstOrNull()
                    if (zapRequest != null) {
                        // var newZapRequestEvent = LocalCache.checkPrivateZap(zapRequest.event as Event)
                        // zapRequest.event = newZapRequestEvent
                        zapsPerEvent
                            .getOrPut(zappedPost) { mutableListOf() }
                            .add(CombinedZap(zapRequest, zapEvent))
                    }
                } else {
                    val event = (zapEvent.event as ZapReceiptEvent)
                    val author =
                        event.zappedAuthor().firstNotNullOfOrNull {
                            LocalCache.getUserIfExists(it) // don't create user if it doesn't exist
                        }
                    if (author != null) {
                        val existingZapRequest = event.zapRequest?.id?.let { getNoteIfExists(it) }
                        if (existingZapRequest == null || existingZapRequest.event == null) {
                            // tries to add it
                            event.zapRequest?.let {
                                LocalCache.checkDeletionAndConsume(it, null, false)
                            }
                        }

                        val zapRequest = event.zapRequest
                        if (zapRequest != null) {
                            val zapRequestNote = getNoteIfExists(zapRequest.id)
                            if (zapRequestNote != null) {
                                zapsPerUser
                                    .getOrPut(author) { mutableListOf() }
                                    .add(CombinedZap(zapRequestNote, zapEvent))
                            }
                        }
                    }
                }
            }

        val boostsPerEvent = mutableMapOf<Note, MutableList<Note>>()
        notes
            .filter { it.event is RepostEvent || it.event is GenericRepostEvent }
            .forEach { note ->
                val boostedPost =
                    note.replyTo?.lastOrNull {
                        it.event !is ChannelMetadataEvent && it.event !is ChannelCreateEvent
                    }
                if (boostedPost != null) {
                    boostsPerEvent.getOrPut(boostedPost) { mutableListOf() }.add(note)
                }
            }

        // NIP-61 nutzaps — parallel to the lightning-zap grouping above.
        // For nutzaps targeting a specific note, accumulate into
        // [nutzapsPerEvent] so the MultiSetCard can show a cashu rail
        // alongside the lightning rail. For nutzaps with no e-tagged
        // target (just the recipient's p-tag), accumulate into
        // [nutzapsPerUser] so they surface as a NutzapUserSetCard,
        // mirroring how user-targeted lightning zaps become a
        // ZapUserSetCard.
        val nutzapsPerUser = mutableMapOf<User, MutableList<Note>>()
        val nutzapsPerEvent = mutableMapOf<Note, MutableList<Note>>()
        notes
            .filter { it.event is NutzapEvent }
            .forEach { nutzapNote ->
                val zappedPost = nutzapNote.replyTo?.lastOrNull()
                if (zappedPost != null) {
                    nutzapsPerEvent
                        .getOrPut(zappedPost) { mutableListOf() }
                        .add(nutzapNote)
                } else {
                    val sender = nutzapNote.author
                    if (sender != null) {
                        nutzapsPerUser
                            .getOrPut(sender) { mutableListOf() }
                            .add(nutzapNote)
                    }
                }
            }

        // Notifications are bucketed per calendar day. Computing the day key as a
        // raw epoch-day Long (instead of formatting a "yyyy-MM-dd" String per event)
        // gives identical buckets and chronological ordering while avoiding a
        // DateTimeFormatter + ZonedDateTime + String allocation for every single
        // reaction/zap/repost — a meaningful GC saving on full feed conversions.
        val dayCounter = LocalClock.epochDayCounter()

        fun epochDay(createdAt: Long?): Long = dayCounter.epochDay(createdAt ?: 0L)

        val allBaseNotes = zapsPerEvent.keys + boostsPerEvent.keys + reactionsPerEvent.keys + nutzapsPerEvent.keys
        val multiCards =
            allBaseNotes.flatMap { baseNote ->
                val boostsInCard = boostsPerEvent[baseNote] ?: emptyList()
                val reactionsInCard = reactionsPerEvent[baseNote] ?: emptyList()
                val zapsInCard = zapsPerEvent[baseNote] ?: emptyList()
                val nutzapsInCard = nutzapsPerEvent[baseNote] ?: emptyList()

                val singleList =
                    (boostsInCard + zapsInCard.map { it.response } + reactionsInCard + nutzapsInCard).groupBy {
                        epochDay(it.createdAt())
                    }

                val days = singleList.keys.sortedBy { it }

                days
                    .mapNotNull { day ->
                        val sortedList =
                            singleList
                                .get(day)
                                ?.sortedWith(compareByDescending<Note> { it.createdAt() }.thenBy { it.idHex })

                        sortedList?.chunked(30)?.map { chunk ->
                            MultiSetCard(
                                baseNote,
                                boostsInCard.filter { it in chunk }.toImmutableList(),
                                reactionsInCard.filter { it in chunk }.toImmutableList(),
                                zapsInCard.filter { it.response in chunk }.toImmutableList(),
                                nutzapsInCard.filter { it in chunk }.toImmutableList(),
                            )
                        }
                    }.flatten()
            }

        val userZaps =
            zapsPerUser
                .map { user ->
                    val byDay =
                        user.value.groupBy {
                            epochDay(it.createdAt())
                        }

                    byDay.values.map { zaps ->
                        ZapUserSetCard(
                            user.key,
                            zaps
                                .sortedWith(compareByDescending<CombinedZap> { it.createdAt() }.thenBy { it.idHex() })
                                .toImmutableList(),
                        )
                    }
                }.flatten()

        // Per-sender nutzap aggregate cards — mirror userZaps but render
        // with a cashu icon so the user sees the rail at a glance. Cards
        // are bucketed by yyyy-MM-dd so a sender's daily run rolls up.
        val userNutzaps =
            nutzapsPerUser
                .map { user ->
                    val byDay =
                        user.value.groupBy {
                            epochDay(it.createdAt())
                        }

                    byDay.values.map { nutzaps ->
                        NutzapUserSetCard(
                            user.key,
                            nutzaps
                                .sortedWith(compareByDescending<Note> { it.createdAt() }.thenBy { it.idHex })
                                .toImmutableList(),
                        )
                    }
                }.flatten()

        val textNoteCards =
            notes
                .filter {
                    it.event !is ReactionEvent &&
                        it.event !is RepostEvent &&
                        it.event !is GenericRepostEvent &&
                        it.event !is ZapReceiptEvent &&
                        // Nutzaps are already grouped into nutzapsPerEvent (MultiSetCard)
                        // or nutzapsPerUser (NutzapUserSetCard) above, exactly like lightning
                        // zaps. Without this exclusion a nutzap would ALSO fall through to a
                        // standalone NoteCard and render a second time as a big RenderNutzap
                        // card — a duplicate of the grouped one.
                        it.event !is NutzapEvent
                }.map {
                    val pendingInvite = if (it.event is MemberAddedNotificationEvent) pendingInvites[it.idHex] else null
                    if (it is ConcordDirectInviteNote) {
                        ConcordDirectInviteNotificationCard(it)
                    } else if (pendingInvite != null) {
                        ChannelInviteCard(it, pendingInvite)
                    } else if (it.event is EncryptedDmEvent || it.event is NIP17Group || it.isInMarmotGroup()) {
                        MessageSetCard(it)
                    } else if (it.event is BadgeAwardEvent) {
                        BadgeCard(it)
                    } else {
                        NoteCard(it)
                    }
                }

        return (multiCards + textNoteCards + userZaps + userNutzaps)
            .sortedWith(NotificationFeedOrderCard)
    }

    /**
     * The unanswered channel invites, keyed by their kind-44100.
     *
     * A StateFlow read, so each access is a volatile load of an already-built map — cheap enough to
     * touch per note. `acceptableEvent` has already narrowed the feed to pending ones, so this only has
     * to attach the resolved invite to its row; a 44100 that is no longer pending falls through to an
     * ordinary card, which the filter should have excluded anyway.
     */
    private val pendingInvites: Map<HexKey, BuzzChannelInvite>
        get() =
            (localFilter as? NotificationFeedFilter)
                ?.account
                ?.channelInvites
                ?.pendingByEventId
                ?.value ?: emptyMap()

    private fun updateFeed(notes: ImmutableList<Card>) {
        if (notes.size >= localFilter.limit()) {
            val lastNoteTime =
                notes.minOfOrNull {
                    val createdAt = it.createdAt()
                    if (createdAt > 0L) {
                        createdAt
                    } else {
                        Long.MAX_VALUE
                    }
                }
            if (lastNoteTime != lastNoteCreatedAtWhenFullyLoaded.value) {
                lastNoteCreatedAtWhenFullyLoaded.tryEmit(notes.lastOrNull()?.createdAt())
            }
        }

        val currentState = _feedContent.value
        if (notes.isEmpty()) {
            _feedContent.tryEmit(CardFeedState.Empty)
        } else if (currentState is CardFeedState.Loaded) {
            currentState.feed.tryEmit(LoadedFeedState(notes, localFilter.showHiddenKey()))
        } else {
            _feedContent.tryEmit(
                CardFeedState.Loaded(MutableStateFlow(LoadedFeedState(notes, localFilter.showHiddenKey()))),
            )
        }
    }

    /**
     * Drops cards built from [deletedNotes] — e.g. a reaction its author took back, which used to
     * linger on the notification card after it vanished from the message. A card can bundle many
     * notes (all reactions to one post), so this rebuilds from the filter rather than editing cards.
     */
    fun deleteFromFeed(deletedNotes: Set<Note>) {
        val shown = lastNotes ?: return
        if (deletedNotes.none { it in shown }) return
        clear()
        invalidateData()
    }

    fun trimToSize(maxItems: Int) {
        val current = _feedContent.value
        if (current is CardFeedState.Loaded) {
            val loaded = current.feed.value
            if (loaded.list.size > maxItems) {
                current.feed.tryEmit(LoadedFeedState(loaded.list.take(maxItems).toImmutableList(), loaded.showHidden))
                // lastNotes is intentionally kept intact. Clearing it would cause the next
                // additive update to fall through to refreshSuspended(), reloading up to
                // limit() notes from LocalCache and undoing the trim immediately. With
                // lastNotes intact, the fast additive path stays active: only genuinely
                // new notifications are appended, so the card list stays near maxItems.
                // The Note refs in lastNotes are the same objects already held by LocalCache;
                // they are freed when pruneRepliesAndReactions() prunes LocalCache and the
                // next full refreshSuspended() replaces lastNotes with the pruned set.
            }
        }
    }

    // Under the refresh lock: it writes the same feed and notes a full refresh does.
    private fun refreshFromOldState(newItems: Set<Note>) = refreshLock.withLock { refreshFromOldStateLocked(newItems) }

    @OptIn(ExperimentalAtomicApi::class)
    private fun refreshFromOldStateLocked(newItems: Set<Note>) {
        val oldNotesState = _feedContent.value

        val thisAccount = (localFilter as? NotificationFeedFilter)?.account
        val lastNotesCopy = if (thisAccount == lastAccount && builtGeneration == generation.load()) lastNotes else null

        if (
            lastNotesCopy != null &&
            localFilter is AdditiveFeedFilter &&
            oldNotesState is CardFeedState.Loaded &&
            lastFeedKey == localFilter.feedKey()
        ) {
            val filteredNewList = localFilter.applyFilter(newItems)

            if (filteredNewList.isEmpty()) return

            val actuallyNew = filteredNewList.minus(lastNotesCopy)

            if (actuallyNew.isEmpty()) return

            val newCards = convertToCard(actuallyNew)

            if (newCards.isNotEmpty()) {
                lastNotes = lastNotesCopy + actuallyNew
                lastAccount = (localFilter as? NotificationFeedFilter)?.account

                val updatedCards =
                    (oldNotesState.feed.value.list + newCards)
                        .distinctBy { it.id() }
                        .sortedWith(NotificationFeedOrderCard)
                        .take(localFilter.limit())
                        .toImmutableList()

                if (!equalImmutableLists(oldNotesState.feed.value.list, updatedCards)) {
                    updateFeed(updatedCards)
                }
            }
        } else {
            // Refresh Everything
            refreshLocked()
        }
    }

    private val bundler = BundledUpdate(1000, Dispatchers.IO)
    private val bundlerInsert = BundledInsert<Set<Note>>(1000, Dispatchers.IO)

    override fun invalidateData(ignoreIfDoing: Boolean) {
        bundler.invalidate(ignoreIfDoing) {
            // adds the time to perform the refresh into this delay
            // holding off new updates in case of heavy refresh routines.
            logTime("${this::class.simpleName} Card update") { refreshSuspended() }
        }
    }

    fun invalidateDataAndSendToTop(ignoreIfDoing: Boolean) {
        clear()
        bundler.invalidate(ignoreIfDoing) {
            // adds the time to perform the refresh into this delay
            // holding off new updates in case of heavy refresh routines.
            logTime("${this::class.simpleName} Card update") {
                refreshSuspended()
                sendToTop()
            }
        }
    }

    fun checkKeysInvalidateDataAndSendToTop() {
        if (lastFeedKey != localFilter.feedKey()) {
            clear()
            bundler.invalidate(false) {
                // adds the time to perform the refresh into this delay
                // holding off new updates in case of heavy refresh routines.
                logTime("${this::class.simpleName} Card update: checkKeysInvalidateDataAndSendToTop") {
                    refreshSuspended()
                    sendToTop()
                }
            }
        }
    }

    fun invalidateInsertData(newItems: Set<Note>) {
        bundlerInsert.invalidateList(newItems) {
            val newObjects = it.flattenToSet()
            logTime("${this::class.simpleName} Card additive receiving ${newObjects.size} items into ${it.size} items") {
                if (newObjects.isNotEmpty()) {
                    refreshFromOldState(newObjects)
                }
            }
        }
    }

    fun updateFeedWith(newNotes: Set<Note>) {
        if (localFilter is AdditiveFeedFilter && _feedContent.value is CardFeedState.Loaded) {
            invalidateInsertData(newNotes)
        } else {
            // Refresh Everything
            invalidateData()
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    fun clear() {
        generation.addAndFetch(1L)
        lastAccount = null
        lastNotes = null
    }

    fun destroy() {
        Log.d("Init") { "OnCleared: ${this::class.simpleName}" }
        clear()
        bundlerInsert.cancel()
        bundler.cancel()
    }
}

private fun Note.isInMarmotGroup(): Boolean = inGatherers?.any { it is MarmotGroupChatroom } == true
