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
package com.vitorpamplona.amethyst.commons.relayClient.event

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.NoteState
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.chats.isMinichatReply
import com.vitorpamplona.amethyst.commons.model.textNoteModifications
import com.vitorpamplona.amethyst.commons.relayClient.user.LocalUserFinderAccount
import com.vitorpamplona.amethyst.commons.relayClient.user.UserFinderAccount
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip72ModCommunities.approval.CommunityPostApprovalEvent
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import com.vitorpamplona.quartz.nip72ModCommunities.isForCommunity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.sample

/*
 * Per-note observers: each subscribes [note] with the relay event finder (batched with every other
 * on-screen note) and exposes a LocalCache flow of the note as Compose state.
 *
 * [account] and [dataSource] default to the front end's [LocalUserFinderAccount] and
 * [LocalEventFinder], so a shared composable needs only the note. Callers outside a composition
 * that provides them (a separate Activity root) pass both explicitly.
 */

@Composable
fun observeNote(
    note: Note,
    account: UserFinderAccount = LocalUserFinderAccount.current,
    dataSource: EventFinderFilterAssembler = LocalEventFinder.current,
): State<NoteState> {
    // Subscribe in the relay for changes in this note.
    EventFinderFilterAssemblerSubscription(note, account, dataSource)

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow = remember(note) { note.flow().metadata.stateFlow }
    return flow.collectAsStateWithLifecycle()
}

/**
 * [observeNote] without the relay half: watches LocalCache and asks no relay for the note.
 *
 * For a NIP-17 rumor, which has no fetchable id — putting one in a REQ would tell relays the
 * private event's identity, the leak [com.vitorpamplona.amethyst.commons.model.Note.isPrivateRumor]
 * guards everywhere else. Nothing is lost by not asking: a rumor only ever reaches the cache by
 * unwrapping the envelope that carried it, and the always-on gift-wrap tail re-fetches a week of
 * those on every cold start, so this flow fires on its own once the envelope lands.
 */
@Composable
fun observeNoteLocally(note: Note): State<NoteState> {
    val flow = remember(note) { note.flow().metadata.stateFlow }
    return flow.collectAsStateWithLifecycle()
}

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
inline fun <reified T : Event> observeNoteEvent(
    note: Note,
    account: UserFinderAccount = LocalUserFinderAccount.current,
    dataSource: EventFinderFilterAssembler = LocalEventFinder.current,
): State<T?> {
    // Subscribe in the relay for changes in this note.
    EventFinderFilterAssemblerSubscription(note, account, dataSource)

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow =
        remember(note) {
            note
                .flow()
                .metadata.stateFlow
                .mapLatest { it.note.event as? T? }
        }

    return flow.collectAsStateWithLifecycle(note.event as? T?)
}

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun <T> observeNoteAndMap(
    note: Note,
    account: UserFinderAccount = LocalUserFinderAccount.current,
    dataSource: EventFinderFilterAssembler = LocalEventFinder.current,
    map: (Note) -> T,
): State<T> {
    // Subscribe in the relay for changes in this note.
    EventFinderFilterAssemblerSubscription(note, account, dataSource)

    val flow =
        remember(note) {
            note
                .flow()
                .metadata.stateFlow
                .mapLatest { map(it.note) }
                .distinctUntilChanged()
                .flowOn(Dispatchers.IO)
        }

    // Subscribe in the LocalCache for changes that arrive in the device
    return flow.collectAsStateWithLifecycle(map(note))
}

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("UNCHECKED_CAST")
@Composable
fun <T, U> observeNoteEventAndMapNotNull(
    note: Note,
    account: UserFinderAccount = LocalUserFinderAccount.current,
    dataSource: EventFinderFilterAssembler = LocalEventFinder.current,
    map: (T) -> U,
): State<U?> {
    // Subscribe in the relay for changes in this note.
    EventFinderFilterAssemblerSubscription(note, account, dataSource)

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow =
        remember(note) {
            note
                .flow()
                .metadata.stateFlow
                .mapLatest { (it.note.event as? T)?.let { map(it) } }
                .distinctUntilChanged()
                .flowOn(Dispatchers.IO)
        }

    // Subscribe in the LocalCache for changes that arrive in the device
    return flow.collectAsStateWithLifecycle((note.event as? T)?.let { map(it) })
}

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("UNCHECKED_CAST")
@Composable
fun <T, U> observeNoteEventAndMap(
    note: Note,
    account: UserFinderAccount = LocalUserFinderAccount.current,
    dataSource: EventFinderFilterAssembler = LocalEventFinder.current,
    map: (T?) -> U,
): State<U> {
    // Subscribe in the relay for changes in this note.
    EventFinderFilterAssemblerSubscription(note, account, dataSource)

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow =
        remember(note) {
            note
                .flow()
                .metadata.stateFlow
                .mapLatest { map(it.note.event as? T) }
                .distinctUntilChanged()
                .flowOn(Dispatchers.IO)
        }

    // Subscribe in the LocalCache for changes that arrive in the device
    return flow.collectAsStateWithLifecycle(map(note.event as? T))
}

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun observeNoteHasEvent(
    note: Note,
    account: UserFinderAccount = LocalUserFinderAccount.current,
    dataSource: EventFinderFilterAssembler = LocalEventFinder.current,
): State<Boolean> {
    // Subscribe in the relay for changes in this note.
    EventFinderFilterAssemblerSubscription(note, account, dataSource)

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow =
        remember(note) {
            note
                .flow()
                .metadata.stateFlow
                .mapLatest { it.note.event != null }
                .distinctUntilChanged()
        }

    return flow.collectAsStateWithLifecycle(note.event != null)
}

@Composable
fun observeNoteReplies(
    note: Note,
    account: UserFinderAccount = LocalUserFinderAccount.current,
    dataSource: EventFinderFilterAssembler = LocalEventFinder.current,
): State<NoteState?> {
    // Subscribe in the relay for changes in this note.
    EventFinderFilterAssemblerSubscription(note, account, dataSource)

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow = remember(note) { note.flow().replies.stateFlow }
    return flow.collectAsStateWithLifecycle()
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Composable
fun observeNoteReplyCount(
    note: Note,
    account: UserFinderAccount = LocalUserFinderAccount.current,
    dataSource: EventFinderFilterAssembler = LocalEventFinder.current,
): State<Int> {
    // Subscribe in the relay for changes in this note.
    EventFinderFilterAssemblerSubscription(note, account, dataSource)

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow =
        remember(note) {
            note
                .flow()
                .replies.stateFlow
                .sample(200)
                .mapLatest { it.note.replies.size }
                .distinctUntilChanged()
        }

    return flow.collectAsStateWithLifecycle(note.replies.size)
}

/**
 * Count of a chat message's **minichat** replies — its kind-1111 [CommentEvent]
 * children only (inline quote-replies are ordinary kind-9/42 messages and are not
 * counted here). Drives the "N replies" chip that opens the minichat.
 *
 * Mounting this registers the message with [EventFinderFilterAssemblerSubscription], which
 * batches the visible messages' ids into shared REQs for their replies (kind-1111 among
 * them) — so for public chats (NIP-28/NIP-29) the thread replies load, and the chip appears,
 * just by rendering the rows. Concord's kind-1111 replies instead arrive over the channel
 * plane, so that REQ finds nothing there and is a harmless no-op.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Composable
fun observeNoteMinichatReplyCount(
    note: Note,
    account: UserFinderAccount = LocalUserFinderAccount.current,
    dataSource: EventFinderFilterAssembler = LocalEventFinder.current,
): State<Int> {
    EventFinderFilterAssemblerSubscription(note, account, dataSource)

    val flow =
        remember(note) {
            note
                .flow()
                .replies.stateFlow
                .sample(200)
                .mapLatest { it.note.replies.count { reply -> isMinichatReply(reply.event) } }
                .distinctUntilChanged()
        }

    return flow.collectAsStateWithLifecycle(note.replies.count { isMinichatReply(it.event) })
}

@Composable
fun observeNoteReactions(
    note: Note,
    account: UserFinderAccount = LocalUserFinderAccount.current,
    dataSource: EventFinderFilterAssembler = LocalEventFinder.current,
): State<NoteState?> {
    // Subscribe in the relay for changes in this note.
    EventFinderFilterAssemblerSubscription(note, account, dataSource)

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow = remember(note) { note.flow().reactions.stateFlow }
    return flow.collectAsStateWithLifecycle()
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Composable
fun observeNoteReactionCount(
    note: Note,
    account: UserFinderAccount = LocalUserFinderAccount.current,
    dataSource: EventFinderFilterAssembler = LocalEventFinder.current,
): State<Int> {
    // Subscribe in the relay for changes in this note.
    EventFinderFilterAssemblerSubscription(note, account, dataSource)

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow =
        remember(note) {
            note
                .flow()
                .reactions.stateFlow
                .sample(200)
                .mapLatest { it.note.countReactions() }
                .distinctUntilChanged()
                .flowOn(Dispatchers.IO)
        }

    // Subscribe in the LocalCache for changes that arrive in the device
    return flow.collectAsStateWithLifecycle(note.countReactions())
}

@Composable
fun observeNoteZaps(
    note: Note,
    account: UserFinderAccount = LocalUserFinderAccount.current,
    dataSource: EventFinderFilterAssembler = LocalEventFinder.current,
): State<NoteState?> {
    // Subscribe in the relay for changes in this note.
    EventFinderFilterAssemblerSubscription(note, account, dataSource)

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow = remember(note) { note.flow().zaps.stateFlow }
    return flow.collectAsStateWithLifecycle()
}

@Composable
fun observeNoteReposts(
    note: Note,
    account: UserFinderAccount = LocalUserFinderAccount.current,
    dataSource: EventFinderFilterAssembler = LocalEventFinder.current,
): State<NoteState?> {
    // Subscribe in the relay for changes in this note.
    EventFinderFilterAssemblerSubscription(note, account, dataSource)

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow = remember(note) { note.flow().boosts.stateFlow }
    return flow.collectAsStateWithLifecycle()
}

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun observeNoteRepostsBy(
    note: Note,
    user: User,
    account: UserFinderAccount = LocalUserFinderAccount.current,
    dataSource: EventFinderFilterAssembler = LocalEventFinder.current,
): State<Boolean> {
    // Subscribe in the relay for changes in this note.
    EventFinderFilterAssemblerSubscription(note, account, dataSource)

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow =
        remember(note) {
            note
                .flow()
                .boosts.stateFlow
                .mapLatest { it.note.isBoostedBy(user) }
                .distinctUntilChanged()
                .flowOn(Dispatchers.IO)
        }

    return flow.collectAsStateWithLifecycle(note.isBoostedBy(user))
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Composable
fun observeNoteRepostCount(
    note: Note,
    account: UserFinderAccount = LocalUserFinderAccount.current,
    dataSource: EventFinderFilterAssembler = LocalEventFinder.current,
): State<Int> {
    // Subscribe in the relay for changes in this note.
    EventFinderFilterAssemblerSubscription(note, account, dataSource)

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow =
        remember(note) {
            note
                .flow()
                .boosts.stateFlow
                .sample(200)
                .mapLatest { note.boosts.size }
                .distinctUntilChanged()
        }

    return flow.collectAsStateWithLifecycle(note.boosts.size)
}

@Composable
fun observeNoteReferences(
    note: Note,
    account: UserFinderAccount = LocalUserFinderAccount.current,
    dataSource: EventFinderFilterAssembler = LocalEventFinder.current,
): State<Boolean> {
    // Subscribe in the relay for changes in this note.
    EventFinderFilterAssemblerSubscription(note, account, dataSource)

    // Subscribe in the LocalCache for changes that arrive in the device.
    // On-chain zaps piggyback `note.flow().zaps.stateFlow` because Note.addOnchainZap
    // invalidates the same `flowSet.zaps`. If that ever moves to a dedicated flow,
    // add it here too — otherwise on-chain-only notes will stop pinging the chevron.
    val flow =
        remember(note) {
            combine(
                note.flow().zaps.stateFlow,
                note.flow().boosts.stateFlow,
                note.flow().reactions.stateFlow,
            ) { zapState, _, _ ->
                zapState.note.hasZapsBoostsOrReactions()
            }.distinctUntilChanged()
        }

    return flow.collectAsStateWithLifecycle(note.hasZapsBoostsOrReactions())
}

@Composable
fun observeNoteOts(
    note: Note,
    account: UserFinderAccount = LocalUserFinderAccount.current,
    dataSource: EventFinderFilterAssembler = LocalEventFinder.current,
): State<NoteState?> {
    // Subscribe in the relay for changes in this note.
    EventFinderFilterAssemblerSubscription(note, account, dataSource)

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow = remember(note) { note.flow().ots.stateFlow }
    return flow.collectAsStateWithLifecycle()
}

// Resolves the actual modification list off the main thread and filters identical results,
// so the caller's LaunchedEffect only fires when the list of edits truly changes.
// `sample(500)` collapses bursts — a heavily-edited note can emit hundreds of times during
// initial relay sync, and we only need the last state per ~half second.
// Returns `null` until the first IO resolution completes — callers should treat that as
// "still loading" and not flip their UI to "no edits".
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Composable
fun observeNoteModifications(
    note: Note,
    account: UserFinderAccount = LocalUserFinderAccount.current,
    dataSource: EventFinderFilterAssembler = LocalEventFinder.current,
): State<List<Note>?> {
    // Subscribe in the relay for changes in this note.
    EventFinderFilterAssemblerSubscription(note, account, dataSource)

    return produceState<List<Note>?>(initialValue = null, note) {
        note
            .flow()
            .edits
            .stateFlow
            .sample(500)
            .mapLatest { note.textNoteModifications() }
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)
            .collect { value = it }
    }
}

@Composable
fun observeCommunityApprovalNeedStatus(
    note: Note,
    community: Note,
    account: UserFinderAccount = LocalUserFinderAccount.current,
    dataSource: EventFinderFilterAssembler = LocalEventFinder.current,
): State<Boolean?> {
    // Subscribe in the relay for changes in this note.
    EventFinderFilterAssemblerSubscription(note, account, dataSource)

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow =
        remember(note, community) {
            combine(
                community.flow().metadata.stateFlow,
                note.flow().boosts.stateFlow,
            ) { communityMetadata, boosts ->
                (communityMetadata.note.event as? CommunityDefinitionEvent)?.let { communityDefEvent ->
                    val moderators = communityDefEvent.moderatorKeys().toSet()

                    if (note.author?.pubkeyHex in moderators) {
                        false
                    } else {
                        val isModerator = account.userFinderPubkeyHex in moderators

                        if (isModerator) {
                            val wasAlreadyApproved =
                                note.boosts.any {
                                    val approvalEvent = it.event
                                    (approvalEvent is CommunityPostApprovalEvent || approvalEvent is RepostEvent || approvalEvent is GenericRepostEvent) &&
                                        approvalEvent.pubKey in moderators &&
                                        approvalEvent.isForCommunity(community.idHex)
                                }
                            !wasAlreadyApproved
                        } else {
                            false
                        }
                    }
                }
            }.distinctUntilChanged()
                .flowOn(Dispatchers.IO)
        }

    // Subscribe in the LocalCache for changes that arrive in the device
    return flow.collectAsStateWithLifecycle(false)
}
