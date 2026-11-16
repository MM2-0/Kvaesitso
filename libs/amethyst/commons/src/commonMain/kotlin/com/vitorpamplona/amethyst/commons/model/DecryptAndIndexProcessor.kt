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
package com.vitorpamplona.amethyst.commons.model

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.chatMessageMarksRoomAsRead
import com.vitorpamplona.amethyst.commons.model.marmotGroups.syncMetadataTo
import com.vitorpamplona.amethyst.commons.model.privateChatLastReadRoute
import com.vitorpamplona.amethyst.commons.model.privateChats.ChatroomList
import com.vitorpamplona.amethyst.commons.nipACWebRtcCalls.CallManager
import com.vitorpamplona.marmot.GroupEventResult
import com.vitorpamplona.marmot.MarmotInboundProcessor
import com.vitorpamplona.marmot.client.MarmotIngestResult
import com.vitorpamplona.marmot.client.MarmotManager
import com.vitorpamplona.marmot.client.MarmotReceiver
import com.vitorpamplona.marmot.client.ingestWelcome
import com.vitorpamplona.quartz.concord.cord05Invites.ConcordDirectInvite
import com.vitorpamplona.quartz.experimental.ephemChat.chat.EphemeralChatEvent
import com.vitorpamplona.quartz.marmot.mip02Welcome.WelcomeEvent
import com.vitorpamplona.quartz.marmot.mip03GroupMessages.GroupEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.IEvent
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKeyable
import com.vitorpamplona.quartz.nip21UriScheme.toNostrUri
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nip57Zaps.PrivateZapCache
import com.vitorpamplona.quartz.nip57Zaps.ZapReceiptEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapRequestEvent
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.EphemeralGiftWrapEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallAnswerEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallHangupEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallIceCandidateEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallOfferEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallRejectEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallRenegotiateEvent
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

class EventProcessor(
    private val account: Account,
    private val cache: LocalCache,
) {
    private val chatHandler = ChatHandler(account.chatroomList)
    private val giftWrapPrefetch =
        GiftWrapPrefetch(
            scope = account.scope,
            accountSigner = { account.signer },
            enabled = { account.settings.usesRemoteSigner() },
            skipDraft = { LocalCache.deletionIndex.hasBeenDeleted(it) },
            warmDraft = { draft -> account.draftsDecryptionCache.cachedDraft(draft) },
        )

    private val draftHandler = DraftEventHandler(account, cache, giftWrapPrefetch::awaitDraft)

    private val giftWrapHandler = GiftWrapEventHandler(account, cache, this)
    private val sealHandler = SealEventHandler(account, cache, this)

    private val zapRequest = ZapRequestEventHandler(account.privateZapsDecryptionCache)
    private val zapEvent = ZapReceiptEventHandler(account.privateZapsDecryptionCache)

    private val groupEventHandler = GroupEventHandler(account, cache)

    /** What the gift wrap and seal handlers decrypt with: the account's signer, with answers fetched ahead. */
    internal val decryptSigner get() = giftWrapPrefetch.signer

    /** The gift wrap handler is done with [wrap]: the prefetch may fetch a later copy again. */
    internal fun openedGiftWrap(wrap: GiftWrapEvent) = giftWrapPrefetch.opened(wrap)

    /** Starts opening the gift wraps in a batch as it arrives, before [runNew] gets to it. */
    fun prefetch(newNotes: Set<Note>) = giftWrapPrefetch.offer(newNotes.mapNotNull { it.event })

    var callManager: CallManager? = null

    suspend fun consume(note: Note) {
        note.event?.let { event ->
            try {
                consumeEvent(event, note, note)
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                Log.e("EventProcessor", "Error processing note", e)
            }
        }
    }

    internal suspend fun consumeEvent(
        event: Event,
        eventNote: Note,
        publicNote: Note,
    ) {
        when (event) {
            is CallOfferEvent,
            is CallAnswerEvent,
            is CallIceCandidateEvent,
            is CallHangupEvent,
            is CallRejectEvent,
            is CallRenegotiateEvent,
            -> callManager?.onSignalingEvent(event)

            is ChatroomKeyable -> {
                // A DM protocol turned off in Settings › Messages never reaches the rooms; admit checks
                // and adds atomically with an unload, so nothing slips in as it turns off.
                if (account.chatFeedToggles.admit(event) { account.chatroomList.add(event, eventNote) }) {
                    markOwnChatMessageAsRead(event)
                }
            }

            is DraftWrapEvent -> draftHandler.add(event, eventNote, publicNote)

            is GroupEvent -> groupEventHandler.add(event, eventNote, publicNote)

            is GiftWrapEvent -> giftWrapHandler.add(event, eventNote, publicNote)

            is SealEvent -> sealHandler.add(event, eventNote, publicNote)

            is ZapRequestEvent -> zapRequest.add(event, eventNote, publicNote)
        }
    }

    /**
     * A chat message authored by this account — sent from this device through any code
     * path, or arriving via the self-addressed gift wrap from another device — means the
     * user was caught up with the room when they sent it, so advance the room's read
     * marker here at the single ingestion choke point rather than at each send site
     * (#1286, #1287). markAsRead is monotonic, so out-of-order history sync cannot move
     * the marker backwards. Unsent drafts never reach this branch (DraftEventHandler
     * indexes their rumors directly into the chatroom).
     */
    private fun <T> markOwnChatMessageAsRead(event: T) where T : Event, T : ChatroomKeyable {
        val me = account.signer.pubKey
        val room = event.chatroomKey(me)
        if (chatMessageMarksRoomAsRead(event, room, me)) {
            account.markAsRead(privateChatLastReadRoute(room), event.createdAt)
        }
    }

    suspend fun delete(note: Note) {
        note.event?.let { event ->
            try {
                deleteEvent(event, note)
            } catch (e: Exception) {
                Log.e("EventProcessor", "Error deleting note", e)
            }
        }
    }

    internal suspend fun deleteEvent(
        event: Event,
        note: Note,
    ) {
        when (event) {
            is ChatroomKeyable -> chatHandler.delete(event, note)
            is DraftWrapEvent -> draftHandler.delete(event, note)
            is GiftWrapEvent -> giftWrapHandler.delete(event, note)
            is SealEvent -> sealHandler.delete(event, note)
            is ZapRequestEvent -> zapRequest.delete(event, note)
            is ZapReceiptEvent -> zapEvent.delete(event, note)
        }
    }

    suspend fun runNew(newNotes: Set<Note>) {
        try {
            // Serial, the handlers share state; their signer round trips run ahead in parallel. Also
            // offered here in case this batch beat the early offer: a wrap is never fetched twice.
            prefetch(newNotes)
            newNotes.forEach { consume(it) }
            handleDeletedDrafts(newNotes)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("EventProcessor", "Error processing batch", e)
        }
    }

    suspend fun runDeleted(notes: Set<Note>) {
        try {
            notes.forEach { delete(it) }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("EventProcessor", "Error deleting batch", e)
        }
    }

    private suspend fun handleDeletedDrafts(newNotes: Set<Note>) {
        val deletedDrafts =
            newNotes.mapNotNull { note ->
                val event = note.event
                if (event is DraftWrapEvent &&
                    event.isDeleted() &&
                    !cache.deletionIndex.hasBeenDeleted(event)
                ) {
                    note
                } else {
                    null
                }
            }

        if (deletedDrafts.isNotEmpty()) {
            Log.w("EventProcessor") { "Deleting ${deletedDrafts.size} draft notes" }
            account.delete(deletedDrafts)
        }
    }
}

interface EventHandler<T : IEvent> {
    // Default no-ops; implementations override only the operations they handle
    suspend fun add(
        event: T,
        eventNote: Note,
        publicNote: Note,
    ) {
        // no-op default: handlers that only care about deletes (or only adds) skip overriding this.
    }

    suspend fun delete(
        event: T,
        eventNote: Note,
    ) {
        // no-op default: handlers that only care about adds (or only deletes) skip overriding this.
    }
}

class ChatHandler(
    private val chatroomList: ChatroomList,
) : EventHandler<ChatroomKeyable> {
    override suspend fun add(
        event: ChatroomKeyable,
        eventNote: Note,
        publicNote: Note,
    ) {
        chatroomList.add(event, eventNote)
    }

    override suspend fun delete(
        event: ChatroomKeyable,
        eventNote: Note,
    ) {
        chatroomList.delete(event, eventNote)
    }
}

class DraftEventHandler(
    private val account: Account,
    private val cache: LocalCache,
    /** Waits for a draft's decrypt that [GiftWrapPrefetch] already started. */
    private val awaitPrefetch: suspend (DraftWrapEvent) -> Unit = {},
) : EventHandler<DraftWrapEvent> {
    override suspend fun add(
        event: DraftWrapEvent,
        eventNote: Note,
        publicNote: Note,
    ) {
        if (event.pubKey == account.signer.pubKey && !event.isDeleted() && !LocalCache.deletionIndex.hasBeenDeleted(event)) {
            awaitPrefetch(event)
            val rumor = account.draftsDecryptionCache.preCachedDraft(event) ?: account.draftsDecryptionCache.cachedDraft(event)
            rumor?.let { indexDraftAsRealEvent(eventNote, it) }
        }
    }

    fun indexDraftAsRealEvent(
        draftEventWrap: Note,
        rumor: Event,
    ) {
        draftEventWrap.replyTo = cache.computeReplyTo(rumor)
        draftEventWrap.replyTo?.forEach { it.addReply(draftEventWrap) }

        when (rumor) {
            is ChatroomKeyable -> {
                account.chatFeedToggles.admit(rumor) { account.chatroomList.add(rumor, draftEventWrap) }
            }

            is EphemeralChatEvent -> {
                rumor.roomId()?.let { roomId ->
                    val channel = cache.getOrCreateEphemeralChannel(roomId)
                    channel.addNote(draftEventWrap, null)
                }
            }

            is ChannelMessageEvent -> {
                rumor.channelId()?.let { channelId ->
                    val channel = cache.checkGetOrCreatePublicChatChannel(channelId)
                    channel?.addNote(draftEventWrap, null)
                }
            }

            is LiveActivitiesChatMessageEvent -> {
                rumor.activityAddress()?.let { channelId ->
                    val channel = cache.getOrCreateLiveChannel(channelId)
                    channel.addNote(draftEventWrap, null)
                }
            }
        }
    }
}

class GiftWrapEventHandler(
    private val account: Account,
    private val cache: LocalCache,
    private val eventProcessor: EventProcessor,
) : EventHandler<GiftWrapEvent> {
    override suspend fun add(
        event: GiftWrapEvent,
        eventNote: Note,
        publicNote: Note,
    ) {
        // Concord plane wraps are kind-1059 too, but their `p` tag is ephemeral and
        // the payload opens with a derived plane key, not our identity — so route
        // them to the Concord read-path first. A recognized wrap is fully handled
        // there (folded / re-projected) and must not fall through to the DM path.
        // Pass the wrap note's seen-on relays so the Concord read-path can stamp them onto the
        // decrypted inner rumor (the rumor otherwise arrives with no per-relay attribution).
        if (account.concordSessions.ingest(event, eventNote.relays.toSet())) {
            // Concord typing heartbeats ride kind-21059 ephemeral wraps that arrive
            // continuously while anyone in any joined community is composing. NIP-01
            // ephemeral events (20000–29999) must never be persisted; the session has
            // already folded the state they carried, so drop the durable wrap note now
            // to keep LocalCache from growing without bound.
            if (event is EphemeralGiftWrapEvent) {
                cache.pruner.unlinkAndRemove(listOf(eventNote))
            }
            return
        }

        if (event.recipientPubKey() != account.signer.pubKey) return

        val innerGiftId = event.innerEventId
        if (innerGiftId == null) {
            processNewGiftWrap(event, eventNote, publicNote)
        } else {
            processExistingGiftWrap(innerGiftId, publicNote)
        }
    }

    override suspend fun delete(
        event: GiftWrapEvent,
        eventNote: Note,
    ) {
        if (event.recipientPubKey() != account.signer.pubKey) return

        event.innerEventId?.let { innerGiftId ->
            val innerGiftNote = cache.getNoteIfExists(innerGiftId)
            innerGiftNote?.event?.let { innerGift ->
                eventProcessor.deleteEvent(innerGift, innerGiftNote)
            }
        }
    }

    private suspend fun processNewGiftWrap(
        event: GiftWrapEvent,
        eventNote: Note,
        publicNote: Note,
    ) {
        val innerGift =
            try {
                event.unwrapThrowing(eventProcessor.decryptSigner)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                eventProcessor.openedGiftWrap(event)
                // A Direct Invite-tagged wrap (CORD-05 §6) that will never open is written off in the
                // invite inbox too, so its sweep does not decrypt it again; a signer that merely
                // could not answer now leaves it to be retried.
                if (ConcordDirectInvite.isInviteTagged(event) && !ConcordDirectInvite.isTransientSignerFailure(e)) {
                    account.concord.directInviteInbox.markNotInvite(event.id)
                }
                Log.d("GiftWrapEvent") { "Couldn't Decrypt the content " + event.toNostrUri() }
                return
            }

        eventNote.event = event.copyNoContent()
        eventProcessor.openedGiftWrap(event)

        if (cache.justConsume(innerGift, null, false)) {
            cache.copyRelaysFromTo(publicNote, innerGift)
            val innerGiftNote = cache.getOrCreateNote(innerGift.id)

            // Marmot Welcomes need MLS processing in addition to being cached,
            // but they do not route through the normal eventProcessor consumer.
            if (MarmotInboundProcessor.isWelcomeEvent(innerGift)) {
                processMarmotWelcomeFlow(innerGift, account)
            } else {
                eventProcessor.consumeEvent(innerGift, innerGiftNote, publicNote)
            }
        }

        // The wrap note already emitted once — from `consumeRegularEvent`, before any of this
        // ran, when `innerEventId` was still null and the note said nothing about where it
        // leads. Everything above is plain assignment, so without this the wrap's last word on
        // itself is that first, useless emission: anything waiting on it (a `nostr:nevent…` for
        // a wrap, a notification from a build that addressed DMs that way) waits forever while
        // the message it wanted sits decrypted in the cache. Emitted last so an observer that
        // wakes here can walk wrap → seal → rumor and find the whole chain linked.
        //
        // `flowSet?`, not `flow()`: a wrap nobody is watching — which is nearly all of them,
        // a week's worth on every cold start — must not be given a flow set to hold.
        eventNote.flowSet?.metadata?.invalidateData()
    }

    private suspend fun processExistingGiftWrap(
        innerGiftId: String,
        publicNote: Note,
    ) {
        cache.copyRelaysFromTo(publicNote, innerGiftId)
        val innerGiftNote = cache.getOrCreateNote(innerGiftId)
        innerGiftNote.event?.let { innerGift ->
            eventProcessor.consumeEvent(innerGift, innerGiftNote, publicNote)
        }
    }
}

/**
 * Shared Marmot Welcome handler used by both [GiftWrapEventHandler]
 * (in case a Welcome arrives directly inside a kind:1059 with no Seal
 * layer) and [SealEventHandler] (the actual production path —
 * Welcomes are wrapped GiftWrap → Seal → Welcome per
 * [com.vitorpamplona.marmot.mip02Welcome.WelcomeGiftWrap]).
 */
private suspend fun processMarmotWelcomeFlow(
    innerEvent: Event,
    account: Account,
    attempt: Int = 0,
) {
    val manager = account.marmotManager ?: return
    if (innerEvent !is WelcomeEvent) {
        return
    }
    // A Welcome that already joined (or never can) is done. Replays of its wrap are routine:
    // every re-subscription re-delivers the last two days of gift wraps.
    if (manager.isTerminallyIngested(innerEvent.id)) return
    // Out of retries this session: a re-delivered wrap waits for the next app start.
    if (attempt == 0 && account.marmot.welcomeRetriesExhausted(innerEvent.id)) return

    // The same join amy's catch-up runs: it also records the group's system-row baseline, and
    // marks the Welcome terminally ingested once nothing more can come of it (joined, already
    // in, or naming a KeyPackage this device never held). "h" is optional per MIP-02 — some
    // senders (e.g. whitenoise-rs) omit it — so the group id comes from the MLS GroupContext.
    when (val result = manager.ingestWelcome(innerEvent)) {
        is MarmotIngestResult.JoinedGroup -> {
            Log.d("MarmotDbg") {
                "processMarmotWelcomeFlow: Joined ${result.nostrGroupId.take(8)}… needsKeyPackageRotation=${result.needsKeyPackageRotation}"
            }

            // Sync MIP-01 metadata from group extensions to chatroom
            val chatroom = account.marmotGroupList.getOrCreateGroup(result.nostrGroupId)
            chatroom.awaitingReinvite.value = false
            manager.syncMetadataTo(result.nostrGroupId, chatroom)
            Log.d("MarmotDbg") {
                "processMarmotWelcomeFlow: synced metadata name=${chatroom.displayName.value} " +
                    "members=${chatroom.memberCount.value} relays=${chatroom.relays.value}"
            }

            // Notify any open MarmotGroupListScreen that a new invited
            // group has appeared so it can re-render (the screen
            // re-runs `loadGroupList` on every emission). Without this,
            // newly-joined groups only show up after a manual refresh.
            account.marmotGroupList.notifyGroupChanged(result.nostrGroupId)

            // Rotate KeyPackages if needed
            if (result.needsKeyPackageRotation) {
                account.marmot.publishMarmotKeyPackages()
            }

            // Fire the "You've been added to <group>" notification. Welcomes
            // have no `p` tag, so the cache-observer path can't route them;
            // this is the single point where we know the recipient account.
            account.marmotNotifier().notifyWelcome(innerEvent, account)
        }

        is MarmotIngestResult.AlreadyInGroup -> {
            // Benign replay of a gift-wrapped Welcome (kind:1059) we already
            // processed in a prior session — the relay is just re-delivering
            // it after app restart. Log at DEBUG, not WARN.
            Log.d("MarmotDbg") {
                "processMarmotWelcomeFlow: already joined group=${result.nostrGroupId.take(8)}… — treating Welcome as replay"
            }
        }

        is MarmotIngestResult.Failure -> {
            Log.w("MarmotDbg") { "processMarmotWelcomeFlow: ERROR (attempt ${attempt + 1}) ${result.message}" }
            // One that can never succeed is already marked terminal; anything else can pass.
            if (!manager.isTerminallyIngested(innerEvent.id)) scheduleWelcomeRetry(innerEvent, account, attempt)
        }

        else -> {
            Log.d("MarmotDbg") { "processMarmotWelcomeFlow: ${innerEvent.id.take(8)}… → ${result::class.simpleName}" }
        }
    }
}

/**
 * Backoff for a Welcome that failed for a reason that can pass (the signer was busy, the
 * KeyPackage store was still loading, a relay round trip failed). Before this a failed
 * Welcome was only retried on the next app start: a replayed wrap skipped the flow.
 */
private val WELCOME_RETRY_DELAYS_MS = longArrayOf(30_000L, 120_000L, 600_000L)

private fun scheduleWelcomeRetry(
    welcome: WelcomeEvent,
    account: Account,
    attempt: Int,
) {
    if (attempt >= WELCOME_RETRY_DELAYS_MS.size) {
        account.marmot.markWelcomeRetriesExhausted(welcome.id)
        return
    }
    if (!account.marmot.claimWelcomeRetry(welcome.id)) return
    account.scope.launch(Dispatchers.IO) {
        delay(WELCOME_RETRY_DELAYS_MS[attempt])
        account.marmot.releaseWelcomeRetry(welcome.id)
        processMarmotWelcomeFlow(welcome, account, attempt + 1)
    }
}

class SealEventHandler(
    private val account: Account,
    private val cache: LocalCache,
    private val eventProcessor: EventProcessor,
) : EventHandler<SealEvent> {
    override suspend fun add(
        event: SealEvent,
        eventNote: Note,
        publicNote: Note,
    ) {
        val rumorId = event.innerEventId
        if (rumorId == null) {
            processNewSeal(event, eventNote, publicNote)
        } else {
            // Replayed seal: re-link the rumor to its delivering envelope so
            // broadcast can republish the wrap after a cache rebuild.
            // publicNote is the outermost event of this unwrap chain — the
            // kind-1059 wrap normally, the seal itself when it arrived bare.
            publicNote.event?.let { envelope -> cache.getOrCreateNote(rumorId).recordRumorHost(envelope) }
            processExistingSeal(rumorId, publicNote)
        }
    }

    override suspend fun delete(
        event: SealEvent,
        eventNote: Note,
    ) {
        event.innerEventId?.let { rumorId ->
            val innerRumorNote = cache.getNoteIfExists(rumorId)
            innerRumorNote?.event?.let { innerRumor ->
                eventProcessor.deleteEvent(innerRumor, innerRumorNote)
            }
        }
    }

    private suspend fun processNewSeal(
        event: SealEvent,
        eventNote: Note,
        publicNote: Note,
    ) {
        // Decrypted once, kept as the seal carries it (claimed author intact) so a Direct Invite can be
        // checked against NIP-59 anti-spoofing without a second decrypt.
        val rumor =
            try {
                event.unsealRumorThrowing(eventProcessor.decryptSigner)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("RumorEvent", "Fail to decrypt or parse Rumor", e)
                return
            }
        val innerRumor = event.unsealed(rumor)

        // A Concord Direct Invite (CORD-05 §6) is a standard NIP-59 giftwrap, so the DM inbox sees
        // it too — tagged `k=3313` or not. It is not a DM: its rumor carries a community's keys. Hand
        // the seal and its rumor to the Concord invite inbox, which runs the NIP-59 anti-spoofing
        // check the generic unseal skips and parks it for the user, and keep the rumor out of the
        // cache and every chat feed.
        if (innerRumor.kind == ConcordDirectInvite.KIND) {
            account.concord.directInviteInbox.offerRumor(publicNote.event ?: event, event, rumor)
            eventNote.event = event.copyNoContent()
            return
        }
        // Tagged as an invite but carrying something else: never one, so the inbox sweep skips it.
        (publicNote.event as? GiftWrapEvent)?.let { wrap ->
            if (ConcordDirectInvite.isInviteTagged(wrap)) account.concord.directInviteInbox.markNotInvite(wrap.id)
        }

        eventNote.event = event.copyNoContent()

        cache.justConsume(innerRumor, null, true)
        cache.copyRelaysFromTo(publicNote, innerRumor)

        val innerRumorNote = cache.getOrCreateNote(innerRumor.id)

        // Remember which envelope delivered this rumor (publicNote is the
        // kind-1059 wrap normally, the seal itself when it arrived bare).
        // Consumers cite/broadcast/prune/evict through this stub — the
        // unsigned rumor itself must never be referenced publicly.
        publicNote.event?.let { envelope -> innerRumorNote.recordRumorHost(envelope) }

        // Marmot Welcome: GiftWrap → Seal → WelcomeEvent. The Seal handler
        // is the actual point at which we see the kind:444 inner. Route it
        // to the MLS flow for group joining in addition to caching — there's
        // no normal eventProcessor consumer for kind:444.
        if (MarmotInboundProcessor.isWelcomeEvent(innerRumor)) {
            processMarmotWelcomeFlow(innerRumor, account)
        } else {
            eventProcessor.consumeEvent(innerRumor, innerRumorNote, publicNote)
        }

        // Same as the wrap above: the seal's only emission came before it was opened, so
        // re-emit now that it points at its rumor. This runs inside the wrap's own unwrap, so
        // the wrap's emission lands after it and sees a fully linked chain.
        eventNote.flowSet?.metadata?.invalidateData()
    }

    private suspend fun processExistingSeal(
        rumorId: String,
        publicNote: Note,
    ) {
        cache.copyRelaysFromTo(publicNote, rumorId)
        val innerRumorNote = cache.getOrCreateNote(rumorId)
        innerRumorNote.event?.let { innerRumor ->
            // A re-delivered Welcome goes back through the MLS flow, which returns at once
            // when it already joined; one that failed the first time gets another chance.
            if (MarmotInboundProcessor.isWelcomeEvent(innerRumor)) {
                processMarmotWelcomeFlow(innerRumor, account)
            } else {
                eventProcessor.consumeEvent(innerRumor, innerRumorNote, publicNote)
            }
        }
    }
}

class ZapRequestEventHandler(
    val decryptionCache: PrivateZapCache,
) : EventHandler<ZapRequestEvent> {
    override suspend fun add(
        event: ZapRequestEvent,
        eventNote: Note,
        publicNote: Note,
    ) {
        if (decryptionCache.cachedPrivateZap(event) == null && event.isPrivateZap()) {
            decryptionCache.decryptPrivateZap(event)
        }
    }

    override suspend fun delete(
        event: ZapRequestEvent,
        eventNote: Note,
    ) {
        if (event.isPrivateZap()) {
            decryptionCache.delete(event)
        }
    }
}

class ZapReceiptEventHandler(
    val decryptionCache: PrivateZapCache,
) : EventHandler<ZapReceiptEvent> {
    override suspend fun delete(
        event: ZapReceiptEvent,
        eventNote: Note,
    ) {
        event.zapRequest?.let { req ->
            if (req.isPrivateZap()) {
                decryptionCache.delete(req)
            }
        }
    }
}

/**
 * Handles inbound GroupEvent (kind:445) for Marmot MLS group messaging.
 *
 * The protocol half is marmot-kmp's [MarmotReceiver], the same one amy's catch-up runs: it
 * decrypts, persists, derives system rows, holds an event that arrived ahead of the commit it
 * needs and replays it once that commit lands, and collects staged departures for an admin to
 * commit. What this class adds is Amethyst's: indexing the inner event in LocalCache, the chat
 * room, push token gossip and notifications.
 */
class GroupEventHandler(
    private val account: Account,
    private val cache: LocalCache,
) : EventHandler<GroupEvent> {
    /**
     * One per account, because it holds state across events: relays do not deliver kind:445 in
     * order, so a run like
     *
     *   1) B sends msg-1..5 at epoch 1
     *   2) B adds C (commit advances B → epoch 2)
     *   3) B sends msg-6..8 at epoch 2
     *   4) B renames (commit advances B → epoch 3)
     *
     * can reach a receiver as {msg-6, msg-7, msg-8, rename, add-C, msg-1..5}. Everything from
     * step 3 on is held until the add-C commit lands, then replayed, bounded per group.
     */
    private val receiver: MarmotReceiver? by lazy {
        account.marmotManager?.let { manager ->
            MarmotReceiver(manager, MAX_PENDING_PER_GROUP) { event, result -> onResult(manager, event, result) }
        }
    }

    override suspend fun add(
        event: GroupEvent,
        eventNote: Note,
        publicNote: Note,
    ) {
        Log.d("MarmotDbg") {
            "GroupEventHandler.add: kind:445 id=${event.id.take(8)}… groupId=${event.groupId()?.take(8)}…"
        }
        val manager = account.marmotManager
        val receiver = receiver
        if (manager == null || receiver == null) {
            Log.w("MarmotDbg") { "GroupEventHandler.add: marmotManager is null" }
            return
        }

        val groupId = event.groupId()
        if (groupId == null) {
            Log.w("MarmotDbg") { "GroupEventHandler.add: kind:445 missing 'h' tag" }
            return
        }
        if (!manager.isMember(groupId)) {
            // Debug, not warn: relays serve kind:445 for every group they carry, so traffic for
            // groups we are not in is the expected steady state, not an anomaly — unlike the null
            // manager and missing 'h' tag above, which stay warnings because they mean we cannot
            // process a group we *should* be handling. Fires ~22 times a boot (the same handful of
            // events, re-offered on each pass), which drowns the two real warnings next to it.
            Log.d("MarmotDbg") {
                "GroupEventHandler.add: not a member of group=${groupId.take(8)}… — dropping kind:445 ${event.id.take(8)}…"
            }
            return
        }

        val chatroom = account.marmotGroupList.getOrCreateGroup(groupId)
        eventNote.relays.forEach { chatroom.recordRelayActivity(it, event.createdAt) }

        try {
            receiver.receive(event)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("MarmotDbg", "GroupEventHandler.add: exception processing kind:445", e)
        }
    }

    /** Amethyst's side of one result, for [event] or for a held event the receiver replayed. */
    private suspend fun onResult(
        manager: MarmotManager,
        event: Event,
        result: MarmotIngestResult,
    ) {
        Log.d("MarmotDbg") {
            "GroupEventHandler: ${event.id.take(8)}… → ${result::class.simpleName}"
        }
        when (result) {
            is MarmotIngestResult.Message -> {
                if (event is GroupEvent) indexApplicationMessage(manager, event, result.inner)
            }

            is MarmotIngestResult.Commit -> {
                Log.d("MarmotDbg") {
                    "GroupEventHandler: CommitProcessed group=${result.inner.groupId.take(8)}… newEpoch=${result.inner.newEpoch}"
                }
                // Sync MIP-01 metadata after epoch advance (extensions may have changed). The
                // kind:1210 rows this commit implies were derived by the ingest and reach the
                // feed through `onSystemRowDerived`.
                manager.syncMetadataTo(result.inner.groupId, account.marmotGroupList.getOrCreateGroup(result.inner.groupId))
            }

            is MarmotIngestResult.ProposalStaged -> {
                Log.d("MarmotDbg") {
                    "GroupEventHandler: ProposalStaged group=${result.groupId.take(8)}… senderLeaf=${result.senderLeafIndex}"
                }
                // A departure only takes effect once an admin commits it; if that is us, do it.
                // Off this coroutine, and for everything staged by the time it runs, so the
                // departures of one catch-up go out in one commit.
                account.scope.launch(Dispatchers.IO) {
                    for (groupId in receiver?.commitStagedProposals()?.keys.orEmpty()) {
                        manager.syncMetadataTo(groupId, account.marmotGroupList.getOrCreateGroup(groupId))
                        account.marmotGroupList.notifyGroupChanged(groupId)
                    }
                }
            }

            is MarmotIngestResult.UndecryptableOuter -> {
                // Either the epoch predates our join, or the commit that advances us to it has
                // not arrived yet; the receiver holds it for a retry after the next commit.
                Log.d("MarmotDbg") {
                    "GroupEventHandler: undecryptable outer layer for group=${result.groupId.take(8)}… " +
                        "(current + ${result.retainedEpochCount} retained epoch key(s) tried) — held for post-commit retry"
                }
            }

            is MarmotIngestResult.Ignored -> {
                Log.d("MarmotDbg") { "GroupEventHandler: ${event.id.take(8)}… ignored (${result.reason})" }
            }

            is MarmotIngestResult.Failure -> {
                val groupId = (event as? GroupEvent)?.groupId()
                Log.w("MarmotDbg") { "GroupEventHandler: ERROR group=${groupId?.take(8)} ${result.message}" }
                groupId?.let {
                    val outOfSync = manager.isOutOfSync(it)
                    val chatroom = account.marmotGroupList.getOrCreateGroup(it)
                    if (chatroom.isOutOfSync.value != outOfSync) chatroom.isOutOfSync.value = outOfSync
                }
            }

            is MarmotIngestResult.JoinedGroup, is MarmotIngestResult.AlreadyInGroup -> {}
        }
    }

    /**
     * Index a decrypted application message. The ingest already persisted it, so it survives a
     * restart even though the ratchet has advanced past it.
     */
    private suspend fun indexApplicationMessage(
        manager: MarmotManager,
        event: GroupEvent,
        result: GroupEventResult.ApplicationMessage,
    ) {
        // A message that decrypts means this device is back in step.
        account.marmotGroupList
            .getOrCreateGroup(result.groupId)
            .isOutOfSync.value = false
        // Parse the inner event JSON and index it
        val innerEvent = Event.fromJson(result.innerEventJson)
        Log.d("MarmotDbg") {
            "GroupEventHandler: ApplicationMessage decrypted innerKind=${innerEvent.kind} " +
                "innerId=${innerEvent.id.take(8)}… author=${innerEvent.pubKey.take(8)}…"
        }
        // wasVerified=true: MIP-03 inner events are unsigned
        // rumors (empty sig); LocalCache.justVerify would reject
        // every one on Schnorr verification. Authenticity is
        // already guaranteed by MLS — MarmotInboundProcessor
        // enforces innerEvent.pubKey == MLS sender's credential
        // identity before returning ApplicationMessage. Without
        // this, wn-signed kind:9 chat messages, kind:7 reactions,
        // and kind:5 deletions would all silently drop here.
        //
        // Always call marmotGroupList.addMessage regardless of
        // justConsume's return value. `addMessageSync` dedupes
        // by Note identity, so a duplicate is a cheap no-op —
        // but a previous-session persist that hydrated the
        // cache at startup without populating the chatroom
        // needs this path to surface the note (otherwise the
        // operator saw nothing but a misleading "inner event
        // already in cache" log and the message never rendered).
        val indexed = account.marmot.indexMarmotInnerEvent(innerEvent)
        val innerNote = indexed.note
        val isNew = indexed.isNew

        // Link the envelope to its inner note and copy over the
        // relays that delivered/accepted the kind-445 so far, so
        // the chat row shows relay icons. Later acceptances drill
        // down on their own via LocalCache.addRelayToNoteAndInners
        // once innerEventId is set. The outer note is looked up by
        // event.id — NOT eventNote/publicNote, which belong to the
        // *triggering* event when this runs from retryPendingFor.
        event.innerEventId = innerEvent.id
        cache.getNoteIfExists(event.id)?.let { outerNote ->
            cache.copyRelaysFromTo(outerNote, innerEvent.id)
        }

        // A kind:1009 edit was linked to the message it replaces by
        // indexMarmotInnerEvent, which also holds it on its note.

        // Push token gossip (kinds 447/448/449) is routing data for
        // a notification server, addressed to the other members'
        // clients rather than to the people in the room. It still
        // reaches the feed's dedupe and cache paths above like any
        // inner event — `MarmotGroupList` is what keeps it off the
        // screen — but its meaning is applied here.
        //
        // Everything this call does is advisory: a malformed entry,
        // a signature that does not verify, a list that lost its
        // ordering race are all dropped on their own and none of
        // them may reach the validity of the kind:445 that carried
        // them. That is why it neither throws nor is checked.
        account.marmotPushCoordinator?.let { push ->
            push.apply(result.groupId, innerEvent)
            // A peer asking for records gets our view, once. We
            // answer with the records we hold — including other
            // members' — with their owner signatures untouched, so
            // a member who has been offline can be caught up by
            // whoever happens to be around.
            if (push.isTokenRequest(innerEvent) && innerEvent.pubKey != account.signer.pubKey) {
                push.buildTokenList(result.groupId)?.let { response ->
                    account.marmot.sendMarmotGroupMessage(
                        result.groupId,
                        response,
                        account.marmot.marmotGroupRelays(result.groupId),
                    )
                }
            }
        }

        // Track the message in the Marmot group chatroom. A
        // peer-sent kind:1210 is dropped inside addMessage — see
        // `MarmotGroupList.isDisplayableFeedMessage`.
        account.marmotGroupList.addMessage(result.groupId, innerNote)
        account.marmot.applyMarmotModeration(result.groupId, innerEvent)

        // Traffic is the natural clock for disappearing messages: a
        // group being read is a group whose expired messages should
        // already be gone.
        manager.pruneExpiredMessages(result.groupId)

        // Notify only on first-time indexing, so a relay re-broadcast or a
        // persist-replay doesn't double-notify. Our OWN messages are never
        // new here: `AccountMarmotActions.sendMarmotGroupMessage` puts the
        // inner rumor in the cache before it encrypts anything, so the bubble
        // can be drawn immediately.
        if (isNew) {
            // GroupEvents have no `p` tag, so the cache-observer
            // notification path can't route them. Fire the popup
            // directly here — only on first-time decryption, so
            // a relay re-broadcast or persist-replay doesn't
            // double-notify. Restrict to ChatEvent (kind:9) so
            // reactions, deletions, and control messages stay
            // silent. A message an admin removed before it
            // landed is never shown, so it never notifies either.
            if (innerEvent is ChatEvent && !account.marmotGroupList.isAdminRemoved(result.groupId, innerEvent.id)) {
                account.marmotNotifier().notifyGroupMessage(
                    innerEvent,
                    result.groupId,
                    account,
                )
            }
        }
    }

    companion object {
        private const val MAX_PENDING_PER_GROUP = 64
    }
}
