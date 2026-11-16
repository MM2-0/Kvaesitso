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
@file:OptIn(ExperimentalUuidApi::class)

package com.vitorpamplona.amethyst.commons.model.drss

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.drss.DrssListenRates
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.anyNotNullEvent
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.cache.filter
import com.vitorpamplona.amethyst.commons.model.events
import com.vitorpamplona.amethyst.commons.model.updateFlow
import com.vitorpamplona.quartz.drss.DrssListPicture
import com.vitorpamplona.quartz.drss.clips.DrssClip
import com.vitorpamplona.quartz.drss.eventList.DrssEventListEvent
import com.vitorpamplona.quartz.drss.eventList.tags.ListItemTag
import com.vitorpamplona.quartz.drss.feedList.DrssFeedListEvent
import com.vitorpamplona.quartz.drss.listFolder.DrssListFolderEvent
import com.vitorpamplona.quartz.drss.listenProgress.DrssListenProgress
import com.vitorpamplona.quartz.drss.listenProgress.DrssListenProgressAppDataEvent
import com.vitorpamplona.quartz.drss.listenProgress.DrssListenProgressEvent
import com.vitorpamplona.quartz.drss.provenance.DrssProvenanceEvent
import com.vitorpamplona.quartz.drss.provenance.DrssProvenanceFolderEvent
import com.vitorpamplona.quartz.drss.readWriteLists.DrssReadWriteListsEvent
import com.vitorpamplona.quartz.drss.show.DrssShowEvent
import com.vitorpamplona.quartz.drss.show.status
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.update
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip09Deletions.DeletionRequestEvent
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip51Lists.articleCurationSet.ArticleCurationSetEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.AddressBookmark
import com.vitorpamplona.quartz.nip51Lists.followSet.FollowSetEvent
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.UserTag
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The logged-in user's drss.io reader state: the read and write lists (a NIP-78 preference), the
 * folders and lists they keep, their listening progress, and every edit Amethyst makes to them.
 *
 * drss keeps a reader's follows in NIP-51 follow sets (30000) and saved articles in curation sets
 * (30004), arranged in list folders (30101). The read list is the folder the river shows; the write
 * list is the folder whose sets take new follows and saves. A user without a preference has neither,
 * and Amethyst then offers their own sets with no folder.
 *
 * Edits go to the user's outbox and to the relays the earlier version was seen on, which is where
 * drss reads them back.
 */
class DrssState(
    val signer: NostrSigner,
    val cache: LocalCache,
    val scope: CoroutineScope,
) {
    private val me: HexKey = signer.pubKey

    // ---- the read and write lists ----

    // A long-term reference, so the GC keeps the preference's note.
    val preferenceNote: AddressableNote = cache.getOrCreateAddressableNote(DrssReadWriteListsEvent.createAddress(me))

    val preference: StateFlow<DrssReadWriteListsEvent?> =
        preferenceNote
            .flow()
            .metadata.stateFlow
            .map { it.note.event as? DrssReadWriteListsEvent }
            .onStart { emit(preferenceNote.event as? DrssReadWriteListsEvent) }
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, preferenceNote.event as? DrssReadWriteListsEvent)

    val readList: StateFlow<Address?> =
        preference
            .map { it?.readList()?.toAddress() }
            .stateIn(scope, SharingStarted.Eagerly, preference.value?.readList()?.toAddress())

    val writeList: StateFlow<Address?> =
        preference
            .map { it?.writeList()?.toAddress() }
            .stateIn(scope, SharingStarted.Eagerly, preference.value?.writeList()?.toAddress())

    // ---- the user's folders and feed lists ----

    private fun existing(kind: Int) =
        cache.addressables
            .filter(kind, me)
            .filter { it.event != null || !cache.hasBeenDeleted(it.address) }

    private val versions = MutableStateFlow(0)

    /** Moves whenever one of the user's folders, feed lists, follow sets or curation sets changes. */
    val version: StateFlow<Int> = versions

    /** The user's folders (30101) and drss feed lists (30100), the lists a river can read. */
    val folderAndFeedListNotes: StateFlow<List<AddressableNote>> =
        versions
            .map { existing(DrssListFolderEvent.KIND) + existing(DrssFeedListEvent.KIND) }
            .onStart { emit(existing(DrssListFolderEvent.KIND) + existing(DrssFeedListEvent.KIND)) }
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val folders: StateFlow<List<DrssListFolderEvent>> =
        folderAndFeedListNotes
            .transformLatest { notes -> emitAll(notes.filter { it.address.kind == DrssListFolderEvent.KIND }.updateFlow<DrssListFolderEvent>()) }
            .onStart { emit(folderAndFeedListNotes.value.events<DrssListFolderEvent>()) }
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** The folders no other folder of the user's holds: the tops of their trees. */
    fun rootFolders(): List<DrssListFolderEvent> {
        val all = folders.value
        val held = all.flatMapTo(mutableSetOf()) { it.childAddressIds() }
        return all.filter { it.address().toValue() !in held }
    }

    /**
     * The user's lists of [kind] under the write folder, each with the folder that holds it, or
     * all of them, in no folder, when there is no write folder.
     */
    fun writableSets(kind: Int): List<DrssWritableSet> {
        val write = writeList.value ?: return existing(kind).map { DrssWritableSet(it, null) }
        return DrssTree
            .walk(write, cache)
            .filter { it.address.kind == kind && it.address.pubKeyHex == me }
            .map { entry -> DrssWritableSet(cache.getOrCreateAddressableNote(entry.address), entry.parent?.let(cache::getOrCreateAddressableNote)) }
    }

    private val trackedKinds =
        setOf(
            DrssListFolderEvent.KIND,
            DrssFeedListEvent.KIND,
            DrssProvenanceFolderEvent.KIND,
            FollowSetEvent.KIND,
            ArticleCurationSetEvent.KIND,
            DrssEventListEvent.KIND,
        )

    private fun hasItemInNoteList(notes: Set<Note>): Boolean =
        notes.anyNotNullEvent { event ->
            event.pubKey == me &&
                (event.kind in trackedKinds || (event is DeletionRequestEvent && trackedKinds.any { event.deleteAddressesWithKind(it) }))
        }

    /**
     * The user's own drss records, held here because the cache keeps a note only while something
     * references it: progress, saves and provenance load with the account but no screen holds them,
     * and a collected note comes back empty until a screen fetches it again.
     */
    private val pinned = MutableStateFlow<Set<AddressableNote>>(PINNED_KINDS.flatMapTo(mutableSetOf()) { kind -> existing(kind).filter { isPinned(it.address) } })

    /** The user's drss records by kind, and of the user's NIP-78 preferences only the listening records. */
    private fun isPinned(address: Address) =
        address.kind in PINNED_KINDS &&
            (address.kind != DrssListenProgressAppDataEvent.KIND || address.dTag.startsWith(DrssListenProgressAppDataEvent.D_TAG_PREFIX))

    private fun pin(notes: Set<Note>) {
        val mine = notes.filterIsInstance<AddressableNote>().filter { it.address.pubKeyHex == me && isPinned(it.address) }
        if (mine.isNotEmpty()) pinned.update { it + mine }
    }

    fun newNotes(newNotes: Set<Note>) {
        pin(newNotes)
        if (hasItemInNoteList(newNotes)) versions.update { it + 1 }
        if (newNotes.anyNotNullEvent { it.pubKey == me && it is DrssListenProgress }) progressVersions.update { it + 1 }
    }

    private val progressVersions = MutableStateFlow(0)

    /** Ticks whenever one of the user's listening records arrives or changes. */
    val progressVersion: StateFlow<Int> = progressVersions

    /**
     * The user's listening records the cache holds, one per episode, last saved first. drss keeps
     * them in kind 30078 and moves the older kind-30054 ones there; an episode with both shows the
     * 30078 one.
     */
    fun myProgress(): List<DrssListenProgress> {
        val records = pinned.value.mapNotNull { it.event as? DrssListenProgress }
        val current = records.filterIsInstance<DrssListenProgressAppDataEvent>()
        val lifted = current.mapNotNullTo(mutableSetOf()) { it.episodeAddressId() }
        val legacy = records.filter { it is DrssListenProgressEvent && it.episodeAddressId() !in lifted }
        return (current + legacy).sortedByDescending { it.createdAt }
    }

    /** The playback speed of each feed, kept on this device as drss.io keeps it in the browser. */
    val rates = DrssListenRates()

    fun deletedNotes(deletedNotes: Set<Note>) {
        if (hasItemInNoteList(deletedNotes)) versions.update { it + 1 }
    }

    // ---- listening progress ----

    // Read-only: drss keeps these records in kind 30078, NIP-78 app data that only drss signs, so
    // Amethyst shows them and resumes from them but never writes one.

    /**
     * The note that holds the user's progress through [episode]: a kind-30078 record whose `d` is
     * `drss-listen-progress:` and the episode's `d`.
     */
    fun progressNote(episode: Address): AddressableNote = cache.getOrCreateAddressableNote(DrssListenProgressAppDataEvent.createAddress(me, episode))

    /**
     * The user's progress through [episode], when the record at its `d` is about this episode: the
     * kind-30078 record, or the kind-30054 one drss has not moved yet.
     */
    fun progressOf(episode: Address): DrssListenProgress? =
        recordAt(DrssListenProgressAppDataEvent.createAddress(me, episode), episode)
            ?: recordAt(Address(DrssListenProgressEvent.KIND, me, episode.dTag), episode)

    private fun recordAt(
        address: Address,
        episode: Address,
    ): DrssListenProgress? =
        (cache.getAddressableNoteIfExists(address)?.event as? DrssListenProgress)
            ?.takeIf { it.episodeAddressId() == episode.toValue() }

    /**
     * Clips [startMillis]..[endMillis] of [episode] as a highlight of it with [note] as the
     * comment, sent where the episode was seen so drss finds it next to the episode.
     */
    suspend fun saveClip(
        episode: AddressableNote,
        startMillis: Long,
        endMillis: Long,
        note: String?,
        account: Account,
    ) {
        val template =
            DrssClip.build(
                episode = ATag(episode.address, episode.relayHintUrl()),
                episodeAuthor = episode.address.pubKeyHex,
                startMillis = startMillis,
                endMillis = endMillis,
                note = note?.ifBlank { null },
            )
        publish(signer.sign(template), episode, account)
    }

    // ---- writing ----

    /** A time that supersedes [earlier], even when it was saved this same second. */
    private fun nextCreatedAt(earlier: Event?) = maxOf(TimeUtils.now(), (earlier?.createdAt ?: 0) + 1)

    /**
     * Sends [event] to the outbox and to the relays [earlier] was seen on: where drss reads it back.
     * A record with no earlier version goes where the user's drss preference was seen instead.
     */
    private fun publish(
        event: Event,
        earlier: Note?,
        account: Account,
    ) {
        account.sendMyPublicAndPrivateOutbox(event)
        val seenOn = (earlier ?: preferenceNote).relays.toSet() - account.outboxRelays.flow.value
        account.broadcaster.republishEventsTo(listOf(event), seenOn)
    }

    private suspend fun <T : Event> signAndPublish(
        template: EventTemplate<T>,
        earlier: Note?,
        account: Account,
    ): T = signer.sign(template).also { publish(it, earlier, account) }

    private fun folderEvent(address: Address): DrssListFolderEvent? = cache.getAddressableNoteIfExists(address)?.event as? DrssListFolderEvent

    /** Makes [read] what the river shows and [write] where follows and saves go. */
    suspend fun setReadWriteLists(
        read: Address,
        write: Address,
        account: Account,
    ) {
        signAndPublish(
            DrssReadWriteListsEvent.build(ATag(read, null), ATag(write, null), nextCreatedAt(preferenceNote.event)),
            preferenceNote,
            account,
        )
    }

    /** Puts [child] in [folder]; false when the folder is not loaded. */
    suspend fun addToFolder(
        folder: Address,
        child: Address,
        account: Account,
    ): Boolean {
        val note = cache.getOrCreateAddressableNote(folder)
        val event = note.event as? DrssListFolderEvent ?: return false
        if (event.childAddressIds().contains(child.toValue())) return true
        signAndPublish(DrssListFolderEvent.addChild(event, ATag(child, null), nextCreatedAt(event)), note, account)
        return true
    }

    /**
     * Takes [child] out of [folder]. A folder cannot lose its last child, so that removal deletes
     * the folder and takes it out of [folderParent] when given.
     */
    suspend fun removeFromFolder(
        folder: Address,
        child: Address,
        account: Account,
        folderParent: Address? = null,
    ) {
        val note = cache.getOrCreateAddressableNote(folder)
        val event = note.event as? DrssListFolderEvent ?: return
        if (!event.childAddressIds().contains(child.toValue())) return
        if (event.children().size == 1) {
            deleteList(folder, folderParent, account)
        } else {
            signAndPublish(DrssListFolderEvent.removeChild(event, child, nextCreatedAt(event)), note, account)
        }
    }

    /** Moves [child] from [from] to [to], adding first so it is never in neither. */
    suspend fun move(
        child: Address,
        from: Address,
        to: Address,
        account: Account,
        fromParent: Address? = null,
    ) {
        if (from == to || child == to) return
        if (addToFolder(to, child, account)) removeFromFolder(from, child, account, fromParent)
    }

    /** Moves everything in [source] into [into], then deletes [source] and takes it out of [sourceParent]. */
    suspend fun merge(
        source: Address,
        into: Address,
        account: Account,
        sourceParent: Address? = null,
    ) {
        if (source == into) return
        val from = folderEvent(source) ?: return
        val target = cache.getOrCreateAddressableNote(into)
        val targetEvent = target.event as? DrssListFolderEvent ?: return
        val moved = from.children().filter { it.toAddress() != into }
        if (moved.isNotEmpty()) {
            val template = targetEvent.update<DrssListFolderEvent>(nextCreatedAt(targetEvent)) { moved.forEach { addUniqueValueIfNew(it.toATagArray()) } }
            signAndPublish(template, target, account)
        }
        deleteList(source, sourceParent, account)
    }

    /** Renames [folder]. */
    suspend fun retitle(
        folder: Address,
        title: String,
        account: Account,
    ) {
        val note = cache.getOrCreateAddressableNote(folder)
        val event = note.event as? DrssListFolderEvent ?: return
        signAndPublish(DrssListFolderEvent.retitle(event, title, nextCreatedAt(event)), note, account)
    }

    /** A new folder titled [title] holding [firstChild], put in [parent] when given. drss has no empty folders. */
    suspend fun createFolder(
        title: String,
        firstChild: Address,
        parent: Address?,
        account: Account,
    ): Address {
        val dTag = Uuid.random().toString()
        val folder = signAndPublish(DrssListFolderEvent.build(dTag, title, listOf(ATag(firstChild, null))), null, account)
        if (parent != null) addToFolder(parent, folder.address(), account)
        return folder.address()
    }

    /** Deletes the user's list or folder at [address] (NIP-09) and takes it out of [parent]. */
    suspend fun deleteList(
        address: Address,
        parent: Address?,
        account: Account,
    ) {
        val note = cache.getAddressableNoteIfExists(address)
        val event = note?.event ?: return
        if (event.pubKey != me) return
        if (parent != null) {
            val parentEvent = folderEvent(parent)
            if (parentEvent != null && parentEvent.childAddressIds().contains(address.toValue())) {
                val parentNote = cache.getOrCreateAddressableNote(parent)
                if (parentEvent.children().size > 1) {
                    signAndPublish(DrssListFolderEvent.removeChild(parentEvent, address, nextCreatedAt(parentEvent)), parentNote, account)
                }
            }
        }
        val deletion = signer.sign(DeletionRequestEvent.build(listOf(event)))
        publish(deletion, note, account)
        account.settings.resetFeedFiltersPointingTo(address)
    }

    // ---- follows ----

    /** Follows [user] in the user's follow set at [set]. */
    suspend fun followInto(
        set: Address,
        user: User,
        account: Account,
    ) {
        val note = cache.getOrCreateAddressableNote(set)
        val event = note.event as? FollowSetEvent ?: return
        if (event.publicMembers().any { it is UserTag && it.pubKey == user.pubkeyHex }) return
        val next = FollowSetEvent.addUser(event, user.pubkeyHex, user.bestRelayHint(), isPrivate = false, signer = signer, createdAt = nextCreatedAt(event))
        publish(next, note, account)
    }

    /** Stops following [user] in the follow set at [set]. */
    suspend fun unfollowFrom(
        set: Address,
        user: User,
        account: Account,
    ) {
        val note = cache.getOrCreateAddressableNote(set)
        val event = note.event as? FollowSetEvent ?: return
        val next = FollowSetEvent.removeUser(event, user.pubkeyHex, isUserPrivate = false, signer = signer, createdAt = nextCreatedAt(event))
        publish(next, note, account)
    }

    /** A new follow set titled [title] holding [members], put in the write folder. */
    suspend fun newFollowSet(
        title: String,
        members: List<UserTag>,
        account: Account,
    ): Address {
        val set = signer.sign(FollowSetEvent.build(title = title, publicMembers = members, signer = signer))
        publish(set, null, account)
        writeList.value?.let { addToFolder(it, set.address(), account) }
        return set.address()
    }

    /** Puts someone else's list or folder at [list] in the write folder, as drss's "follow list" does. */
    suspend fun followList(
        list: Address,
        account: Account,
    ): Boolean {
        val write = writeList.value ?: return false
        return addToFolder(write, list, account)
    }

    // ---- saved articles ----

    /** Whether the curation set at [set] holds [article]. */
    fun holds(
        set: Address,
        article: Address,
    ): Boolean = (cache.getAddressableNoteIfExists(set)?.event as? ArticleCurationSetEvent)?.publicItemAddressIds()?.contains(article.toValue()) == true

    /** Saves or unsaves [article] in the curation set at [set]. */
    suspend fun toggleSaved(
        set: Address,
        article: Address,
        account: Account,
    ) {
        val note = cache.getOrCreateAddressableNote(set)
        val event = note.event as? ArticleCurationSetEvent ?: return
        val item = AddressBookmark(article, null)
        val next =
            if (holds(set, article)) {
                ArticleCurationSetEvent.remove(event, item, isPrivate = false, signer = signer, createdAt = nextCreatedAt(event))
            } else {
                ArticleCurationSetEvent.add(event, item, isPrivate = false, signer = signer, createdAt = nextCreatedAt(event))
            }
        publish(next, note, account)
    }

    /** A new curation set titled [title] holding [article], put in the write folder. */
    suspend fun newCollection(
        title: String,
        article: Address,
        account: Account,
    ): Address {
        val set = ArticleCurationSetEvent.create(title = title, publicItems = listOf(AddressBookmark(article, null)), signer = signer)
        publish(set, null, account)
        writeList.value?.let { addToFolder(it, set.address(), account) }
        return set.address()
    }

    // ---- older collections (kind 30102) ----

    /** The user's collections of the kind drss wrote before curation sets, which it still edits. */
    fun eventLists(): List<AddressableNote> = existing(DrssEventListEvent.KIND).filter { it.event is DrssEventListEvent }

    fun eventListHolds(
        list: AddressableNote,
        eventId: HexKey,
    ): Boolean = (list.event as? DrssEventListEvent)?.itemIds()?.contains(eventId) == true

    /**
     * Adds [item] to the collection [list], or takes it out. A collection keeps its last item:
     * without one it would no longer read as a collection, so it is deleted instead, by the user.
     * Returns false when nothing was sent for that reason.
     */
    suspend fun toggleInEventList(
        list: AddressableNote,
        item: Note,
        account: Account,
    ): Boolean {
        val event = list.event as? DrssEventListEvent ?: return false
        // A collection holds event ids: an addressable note's id is its address, so it waits for its event.
        val eventId = item.event?.id ?: if (item is AddressableNote) return false else item.idHex
        val next =
            if (eventListHolds(list, eventId)) {
                if (event.items().size <= 1) return false
                DrssEventListEvent.removeItem(event, eventId, nextCreatedAt(event))
            } else {
                DrssEventListEvent.addItem(event, ListItemTag(eventId, item.relayHintUrl(), TimeUtils.now()), nextCreatedAt(event))
            }
        signAndPublish(next, list, account)
        return true
    }

    // ---- pictures ----

    /**
     * Gives the user's list at [address] the picture [image], or none when null, as drss's list
     * picture form does, without the small copy drss also makes: readers fall back to the picture.
     * drss's feed lists (30100) and older collections (30102) carry none: their shapes allow no
     * other tags.
     */
    suspend fun setPicture(
        address: Address,
        image: String?,
        account: Account,
    ) {
        val note = cache.getAddressableNoteIfExists(address) ?: return
        val event = note.event ?: return
        if (event.pubKey != me || !canHavePicture(event.kind)) return
        signAndPublish(DrssListPicture.set(event, image, null, nextCreatedAt(event)), note, account)
    }

    fun canHavePicture(kind: Int) = kind != DrssFeedListEvent.KIND && kind != DrssEventListEvent.KIND

    // ---- provenance ----

    /**
     * Records that the user found [feed] at [source] (a feed or site URL) through [foundThrough],
     * with their [note], and files it in their provenance vault, as drss's provenance form does.
     */
    suspend fun recordProvenance(
        feed: User,
        source: String,
        note: String,
        foundThrough: Address?,
        account: Account,
    ) {
        val existing = cache.getAddressableNoteIfExists(Address(DrssProvenanceEvent.KIND, me, feed.pubkeyHex))
        val record =
            signAndPublish(
                DrssProvenanceEvent.build(
                    feed = PTag(feed.pubkeyHex, feed.bestRelayHint()),
                    source = source,
                    sourceType = SOURCE_TYPE_WEB,
                    note = note,
                    foundThrough = foundThrough?.let { ATag(it, null) },
                    createdAt = nextCreatedAt(existing?.event),
                ),
                existing,
                account,
            )

        val rootAddress = Address(DrssProvenanceFolderEvent.KIND, me, VAULT_ROOT)
        val rootNote = cache.getOrCreateAddressableNote(rootAddress)
        val root = rootNote.event as? DrssProvenanceFolderEvent
        if (root != null) {
            if (!root.childAddressIds().contains(record.address().toValue())) {
                signAndPublish(DrssProvenanceFolderEvent.addChild(root, ATag(record.address(), null), nextCreatedAt(root)), rootNote, account)
            }
        } else {
            signAndPublish(DrssProvenanceFolderEvent.build(VAULT_ROOT, VAULT_ROOT_TITLE, listOf(ATag(record.address(), null))), null, account)
            if (cache.getAddressableNoteIfExists(Address(DrssListFolderEvent.KIND, me, VAULT))?.event == null) {
                signAndPublish(DrssListFolderEvent.build(VAULT, VAULT_TITLE, listOf(ATag(rootAddress, null))), null, account)
            }
        }
    }

    // ---- shows ----

    /** Republishes the user's [show] with new [settings] (the JSON content), [name] and [status]. */
    suspend fun updateShow(
        show: DrssShowEvent,
        settings: String,
        name: String,
        status: String,
        account: Account,
    ) {
        if (show.pubKey != me) return
        val note = cache.getOrCreateAddressableNote(show.address())
        val template =
            show.update<DrssShowEvent>(nextCreatedAt(show)) {
                status(status)
                alt(DrssShowEvent.ALT_PREFIX + name)
            }
        signAndPublish(EventTemplate(template.createdAt, template.kind, template.tags, settings), note, account)
    }

    companion object {
        private val PINNED_KINDS =
            setOf(
                DrssListenProgressAppDataEvent.KIND,
                DrssListenProgressEvent.KIND,
                ArticleCurationSetEvent.KIND,
                DrssProvenanceFolderEvent.KIND,
                DrssProvenanceEvent.KIND,
                DrssEventListEvent.KIND,
            )

        /** A show drss no longer runs; the other status is [DrssShowEvent.ACTIVE]. */
        const val SHOW_RETIRED = "retired"

        /** drss's `k` for a provenance record whose source is a web page or feed. */
        const val SOURCE_TYPE_WEB = "web"
        const val VAULT = "vault-provenance"
        const val VAULT_TITLE = "provenance"
        const val VAULT_ROOT = "vault-provenance/_root"
        const val VAULT_ROOT_TITLE = "Root"
    }
}

/** A list the user can add to, and the folder it sits in (null outside any folder). */
@Immutable
data class DrssWritableSet(
    val note: AddressableNote,
    val folder: AddressableNote?,
)

private fun ATag.toAddress() = Address(kind, pubKeyHex, dTag)
