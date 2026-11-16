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
package com.vitorpamplona.amethyst.commons.model.concord

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityList
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityList.withAddedAt
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListDocument
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEntry
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEvent
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListFragmentEvent
import com.vitorpamplona.quartz.concord.cord02Community.ConcordListFragmentSet
import com.vitorpamplona.quartz.concord.cord02Community.ConcordListIncompleteException
import com.vitorpamplona.quartz.concord.cord02Community.ConcordListResidue
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile

/**
 * Persistence hook for the account's Community List (offline backup): the kind-33302 fragments
 * (CORD-02 §8) plus the retired kind-13302 single event, which is still read as a rescue source.
 */
interface ConcordListRepository {
    fun concordList(): ConcordCommunityListEvent?

    fun updateConcordListTo(newConcordList: ConcordCommunityListEvent?)

    fun concordListFragments(): List<ConcordCommunityListFragmentEvent> = emptyList()

    fun updateConcordListFragmentTo(fragment: ConcordCommunityListFragmentEvent) {}
}

/**
 * The account's home base for Concord Channels: the member's Community List (CORD-02 §8) — kind
 * 33302 fragments, NIP-44-encrypted to self, at `d` = the fragment index. The entries carry the
 * community secrets (root/salt/epoch/private-channel keys), so decryption yields everything needed
 * to re-derive each plane on any device.
 *
 * The List reads as the union of every fragment below the declared count, merged with the retired
 * kind-13302 event if one exists — so a membership only the old event carries is still joined, and
 * the next write migrates it into the fragments. Nothing ever writes 13302 again.
 *
 * Exposes [liveCommunities] (the joined [ConcordCommunityListEntry] set) and [liveServers] (the
 * distinct community ids — the "server" rail). [follow]/[unfollow] read-modify-write the List and
 * return the fragment events to publish: a full repack when every fragment is held, or a write
 * scoped to the fragment holding the change when some are still missing (so a fragment stranded on
 * an unreachable relay never blocks a join or a leave).
 */
class ConcordChannelListState(
    val signer: NostrSigner,
    val cache: ICacheProvider,
    val scope: CoroutineScope,
    val settings: ConcordListRepository,
) {
    // Long-term references so the GC doesn't collect the notes themselves.
    val concordListNote = cache.getOrCreateAddressableNote(getConcordListAddress())
    private val fragmentNotes = HashMap<Int, AddressableNote>()
    private val fragmentNotesLock = KmpLock()

    /** How many fragment coordinates we watch: at least index 0, and every index the List declares. */
    private val watchedFragments = MutableStateFlow(1)

    /** Serializes read-modify-writes so two quick edits can't both build on the same base. */
    private val writeLock = Mutex()

    /**
     * Plaintext per fragment/legacy event id. Every List change re-reads the whole List, and each
     * decrypt is a signer round trip (an IPC hop to Amber, a relay hop to a bunker); an event id's
     * plaintext never changes, so each is decrypted once. Failures are not cached, so a transient
     * signer error is retried on the next read.
     */
    private val plaintextById = LinkedHashMap<String, String>()
    private val plaintextLock = KmpLock()

    private suspend fun plaintextOf(
        id: String,
        decrypt: suspend () -> String?,
    ): String? {
        plaintextLock.withLock { plaintextById[id] }?.let { return it }
        val plaintext = decrypt() ?: return null
        plaintextLock.withLock {
            plaintextById[id] = plaintext
            // Bounded: only the newest copy per index is ever read, so a few dozen ids is plenty.
            while (plaintextById.size > MAX_CACHED_PLAINTEXTS) plaintextById.remove(plaintextById.keys.first())
        }
        return plaintext
    }

    /**
     * Whether the relays have been asked for this account's fragments since start-up
     * ([markRelaysConfirmed], after the import fetch). Until then an empty fragment set means
     * "not loaded yet", not "no List", and a write would replace fragments another device or
     * client published — so [follow]/[unfollow] refuse rather than guess.
     */
    @Volatile
    var relaysConfirmed = false
        private set

    fun markRelaysConfirmed() {
        relaysConfirmed = true
    }

    /** The retired single-event list's coordinate, still read for migration. */
    fun getConcordListAddress() = ConcordCommunityListEvent.createAddress(signer.pubKey)

    fun getConcordList(): ConcordCommunityListEvent? = concordListNote.event as? ConcordCommunityListEvent

    private fun fragmentNote(index: Int): AddressableNote =
        fragmentNotesLock.withLock {
            fragmentNotes.getOrPut(index) { cache.getOrCreateAddressableNote(ConcordCommunityListFragmentEvent.createAddress(signer.pubKey, index)) }
        }

    /** Every fragment event we hold, from the cache and the offline backup (newest per index wins later). */
    private fun heldFragments(): List<ConcordCommunityListFragmentEvent> {
        val fromCache = (0 until watchedFragments.value).mapNotNull { fragmentNote(it).event as? ConcordCommunityListFragmentEvent }
        return fromCache + settings.concordListFragments()
    }

    /** Resolves the fragments we hold, widening the watch when the List declares more of them. */
    suspend fun fragmentSet(): ConcordListFragmentSet {
        val set = ConcordListFragmentSet.resolve(heldFragments(), signer.pubKey) { e -> plaintextOf(e.id) { e.decryptPlaintext(signer) } }
        if (set.declared > watchedFragments.value) watchedFragments.value = set.declared
        return set
    }

    /** The fragments plus the merged, decoded List a read-modify-write starts from. */
    private suspend fun snapshot(): Pair<ConcordListFragmentSet, ConcordCommunityListDocument> {
        val set = fragmentSet()
        val legacyEvent = getConcordList() ?: settings.concordList()
        val legacy = legacyEvent?.let { e -> plaintextOf(e.id) { e.decryptPlaintext(signer) } }
        return set to ConcordCommunityList.decodeDocument(ConcordCommunityList.readWithLegacy(set, legacy))
    }

    /** The whole decoded List — entries plus the residue a read-modify-write must hand back. */
    suspend fun document(): ConcordCommunityListDocument = snapshot().second

    /** The joined entries. */
    suspend fun entries(): List<ConcordCommunityListEntry> = document().entries

    @OptIn(ExperimentalCoroutinesApi::class)
    private val listChanges: Flow<Any> =
        watchedFragments.flatMapLatest { n ->
            combine((0 until n).map { fragmentNote(it).flow().metadata.stateFlow } + concordListNote.flow().metadata.stateFlow) { it }
        }

    /**
     * The List as it reads after each change. Every read resolves the fragments, merges the
     * retired 13302 in and decodes the whole document, so it runs once here and feeds both
     * [liveCommunities] and [removedAt] instead of once per view.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val liveDocument: SharedFlow<ConcordCommunityListDocument> =
        listChanges
            .transformLatest { emit(document()) }
            .onStart { emit(document()) }
            .flowOn(Dispatchers.IO)
            .shareIn(scope, SharingStarted.Eagerly, replay = 1)

    val liveCommunities: StateFlow<List<ConcordCommunityListEntry>> =
        liveDocument
            .map { it.entries }
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptyList(),
            )

    /**
     * When this account left each community it no longer holds (community id → the List
     * tombstone's `removed_at`, unix ms, CORD-02 §8). A Direct Invite sent at or before that moment
     * stays buried instead of resurfacing right after the leave.
     */
    val removedAt: StateFlow<Map<String, Long>> =
        liveDocument
            .map { it.residue.removals() }
            .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    /** The distinct community ids across the joined list — the "servers" rail. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val liveServers: StateFlow<Set<String>> =
        liveCommunities
            .transformLatest { entries -> emit(entries.mapTo(mutableSetOf()) { it.id }) }
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, emptySet())

    /**
     * Encrypts and signs the fragments that make the wire hold [entries] + [residue]. The new
     * document replaces the current memberships wholesale (never a merge), so a same-epoch
     * update — a delivered `control_root`, a rename — can't lose the snapshot tie-break to the
     * state it is replacing.
     */
    private suspend fun write(
        set: ConcordListFragmentSet,
        entries: List<ConcordCommunityListEntry>,
        residue: ConcordListResidue,
    ): List<ConcordCommunityListFragmentEvent> {
        if (set.isEmpty && !relaysConfirmed) {
            throw ConcordListIncompleteException("the Community List has not been fetched from relays yet; refusing to overwrite it")
        }
        val newDoc = ConcordCommunityList.encodeInternal(entries, residue)
        return set.planWrites(newDoc, TimeUtils.now()).map { w ->
            ConcordCommunityListFragmentEvent.create(signer, w.index, w.plaintext, w.createdAt).also { settings.updateConcordListFragmentTo(it) }
        }
    }

    /**
     * Add or replace [entry] (by community id) and return the fragment events to publish.
     *
     * Adding a community we once left is a re-join, which must outrank the tombstone
     * (`added_at > removed_at`, CORD-02 §8): an entry that doesn't — a join in the same second as
     * the leave, or a stale snapshot — gets its `added_at` lifted just past the removal.
     *
     * Throws [ConcordListIncompleteException] when the List isn't loaded well enough to write
     * without destroying a fragment, and [ConcordListTooLargeException] when a fragment would
     * pass the event ceiling.
     */
    suspend fun follow(entry: ConcordCommunityListEntry): List<Event> =
        writeLock.withLock {
            val (set, doc) = snapshot()
            val removedAt = doc.residue.removedAt(entry.id)
            val live = if (removedAt != null && entry.addedAt <= removedAt) entry.withAddedAt(maxOf(TimeUtils.nowMillis(), removedAt + 1)) else entry
            write(set, doc.entries.filterNot { it.id == entry.id } + live, doc.residue)
        }

    /**
     * Read-modify-write one membership: [transform] receives the entry for [communityId] **as the
     * List holds it inside the write lock** and returns its replacement, or null to write nothing.
     * Returns the fragment events to publish (empty when the community isn't held or nothing
     * changed).
     *
     * Use this, never [follow] with a snapshot, for any change that touches one field of a live
     * entry (a channel key, a cut): the List can move between reading a snapshot and writing it —
     * a rekey adopted, a new root imported — and following the stale copy would write the old
     * root back over it.
     */
    suspend fun update(
        communityId: String,
        transform: (ConcordCommunityListEntry) -> ConcordCommunityListEntry?,
    ): List<Event> =
        writeLock.withLock {
            val (set, doc) = snapshot()
            val current = doc.entries.firstOrNull { it.id == communityId } ?: return@withLock emptyList()
            val next = transform(current) ?: return@withLock emptyList()
            write(set, doc.entries.map { if (it.id == communityId) next else it }, doc.residue)
        }

    /**
     * Leave [communityId]: drop its membership and tombstone it (CORD-02 §8 — only a tombstone
     * subtracts a membership; a missing entry is just unseen news another fragment may still
     * carry). Returns the fragment events to publish, or empty when we were not a member.
     */
    suspend fun unfollow(communityId: String): List<Event> =
        writeLock.withLock {
            val (set, doc) = snapshot()
            if (doc.entries.none { it.id == communityId }) return@withLock emptyList()
            write(set, doc.entries.filterNot { it.id == communityId }, doc.residue.withTombstone(communityId, TimeUtils.nowMillis()))
        }

    /**
     * Writes the List as it currently reads — used to seed the fragments from the retired 13302
     * event once relays confirmed none exist yet. Empty when there is nothing to write.
     */
    suspend fun republish(): List<Event> =
        writeLock.withLock {
            val (set, doc) = snapshot()
            if (doc.entries.isEmpty() && doc.residue.tombstones.isEmpty()) return@withLock emptyList()
            write(set, doc.entries, doc.residue)
        }

    companion object {
        private const val MAX_CACHED_PLAINTEXTS = 64
    }

    init {
        val savedLegacy = settings.concordList()
        val savedFragments = settings.concordListFragments()
        if (savedLegacy != null || savedFragments.isNotEmpty()) {
            Log.d("AccountRegisterObservers", "Loading saved concord list")
            @OptIn(DelicateCoroutinesApi::class)
            scope.launch(Dispatchers.IO) {
                savedLegacy?.let { cache.justConsumeMyOwnEvent(it) }
                savedFragments.forEach { cache.justConsumeMyOwnEvent(it) }
                if (savedFragments.isNotEmpty()) watchedFragments.value = maxOf(watchedFragments.value, savedFragments.mapNotNull { it.index() }.max() + 1)
            }
        }

        scope.launch(Dispatchers.IO) {
            Log.d("AccountRegisterObservers", "ConcordList Collector Start")
            listChanges.collect {
                getConcordList()?.let { settings.updateConcordListTo(it) }
                for (i in 0 until watchedFragments.value) {
                    (fragmentNote(i).event as? ConcordCommunityListFragmentEvent)?.let { settings.updateConcordListFragmentTo(it) }
                }
            }
        }
    }
}
