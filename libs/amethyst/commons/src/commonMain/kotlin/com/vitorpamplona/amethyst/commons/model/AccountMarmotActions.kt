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

import androidx.collection.LruCache
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.EventCache
import com.vitorpamplona.amethyst.commons.model.marmotGroups.MarmotEmojiMediaPort
import com.vitorpamplona.amethyst.commons.model.marmotGroups.MarmotEmojiUploadCache
import com.vitorpamplona.amethyst.commons.model.marmotGroups.MarmotGroupList
import com.vitorpamplona.amethyst.commons.model.marmotGroups.syncMetadataTo
import com.vitorpamplona.amethyst.commons.model.mediaServers.ServerType
import com.vitorpamplona.amethyst.commons.model.rooms.reactionsOfAuthorLike
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.taggedAs
import com.vitorpamplona.amethyst.commons.relays.dialableRelays
import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.marmot.appComponents.EncryptedMediaPolicyV2
import com.vitorpamplona.marmot.appComponents.EncryptedMediaReferenceV2
import com.vitorpamplona.marmot.appComponents.EncryptedMediaV2
import com.vitorpamplona.marmot.appComponents.GroupAvatarUrlV1
import com.vitorpamplona.marmot.appComponents.GroupProfileV1
import com.vitorpamplona.marmot.appComponents.MarmotMediaType
import com.vitorpamplona.marmot.appComponents.MarmotWebUrl
import com.vitorpamplona.marmot.appComponents.MediaLocatorV2
import com.vitorpamplona.marmot.appComponents.MessageRetentionV1
import com.vitorpamplona.marmot.client.MarmotManager
import com.vitorpamplona.marmot.foundation.appEvents.MarmotAppEvent
import com.vitorpamplona.marmot.foundation.appEvents.MarmotCustomEmoji
import com.vitorpamplona.marmot.foundation.appEvents.MarmotMessageEdit
import com.vitorpamplona.marmot.foundation.appEvents.MarmotPolls
import com.vitorpamplona.marmot.foundation.appEvents.MarmotReport
import com.vitorpamplona.marmot.foundation.appEvents.MarmotReportDismissal
import com.vitorpamplona.marmot.groups.MarmotEpochMovedException
import com.vitorpamplona.marmot.groups.MarmotGroupPermissions
import com.vitorpamplona.marmot.mip00KeyPackages.KeyPackageFetcher
import com.vitorpamplona.marmot.protocolCore.GroupLifecycleState
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageEvent
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.PublishedKeyPackage
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.publishAndConfirm
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrlTag
import com.vitorpamplona.quartz.nip56Reports.ReportEvent
import com.vitorpamplona.quartz.nip56Reports.ReportType
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.RumorAssembler
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * How long a [LatestKeyPackageOwner.NONE] answer may be reused.
 *
 * Much shorter than a definite answer's life, because NONE is ambiguous:
 * `fetchAll` returns an empty list for a device that reached no relay at all
 * rather than throwing, so "nobody has published one" and "we are offline"
 * arrive identically. Long enough to stop repeated entries from re-fanning the
 * query, short enough that the banner is not suppressed for a quarter of an
 * hour after the network comes back.
 */
private const val NONE_MAX_AGE_SECONDS = 60L

/**
 * Which install owns the newest KeyPackage currently on the account's relays.
 *
 * An inviter picks the highest `created_at` kind:30443 and nothing else
 * ([KeyPackageFetcher.fetchKeyPackage]), and only the install holding that
 * bundle's private keys can open the Welcome it produces. With the same
 * account signed in twice, the two installs publish under different random
 * d-tag slots, so both KeyPackages persist and the most recent publisher
 * silently owns every future invite.
 */
enum class LatestKeyPackageOwner {
    /** This install holds the bundle — invites land here. */
    THIS_DEVICE,

    /** A newer KeyPackage we have no private keys for — invites land elsewhere. */
    OTHER_DEVICE,

    /** Nothing published for this account, or nowhere to ask. */
    NONE,
}

/**
 * Marmot (MLS encrypted groups) orchestration for an [Account]: group create/
 * leave/reset, member add/remove via key-package fetch, admin grant/revoke,
 * metadata updates, group messaging, and key-package publishing. MLS state
 * lives in [MarmotManager]; this class wires it to the account's signer, relay
 * client, and relay lists. Functions live here (not a ViewModel) so headless
 * callers - notification receivers, background workers - can drive them.
 */
class AccountMarmotActions(
    private val account: Account,
) {
    /** Last (timestamp, answer) from [latestKeyPackageOwner], for passive callers. */
    private var lastOwnerCheck: Pair<Long, LatestKeyPackageOwner>? = null

    // Welcome rumor ids with a retry already scheduled, so a relay re-delivering the same
    // wrap while one waits does not start a second chain of retries.
    private val welcomeRetries = mutableSetOf<HexKey>()

    // Welcome rumor ids whose retries ran out this session. Relays re-deliver two days of gift
    // wraps on every re-subscription, and each delivery used to start a fresh chain, so a
    // Welcome that can never apply was re-run through MLS for as long as relays kept it.
    // In memory on purpose: the next app start still gives it one more try.
    private val welcomeRetriesExhausted = mutableSetOf<HexKey>()
    private val welcomeRetriesLock = KmpLock()

    /** True when [welcomeId] had no retry pending, has retries left, and now has one pending. */
    fun claimWelcomeRetry(welcomeId: HexKey): Boolean =
        welcomeRetriesLock.withLock {
            welcomeId !in welcomeRetriesExhausted && welcomeRetries.add(welcomeId)
        }

    fun markWelcomeRetriesExhausted(welcomeId: HexKey) {
        welcomeRetriesLock.withLock { welcomeRetriesExhausted.add(welcomeId) }
    }

    fun welcomeRetriesExhausted(welcomeId: HexKey): Boolean = welcomeRetriesLock.withLock { welcomeId in welcomeRetriesExhausted }

    fun releaseWelcomeRetry(welcomeId: HexKey) {
        welcomeRetriesLock.withLock { welcomeRetries.remove(welcomeId) }
    }

    /**
     * Resolve the relay set for a Marmot group. Prefer the relays carried in
     * the MLS GroupContext metadata so every member converges on the same
     * canonical set; fall back to the account's outbox relays if the group
     * has none (e.g. a group joined before MIP-01 metadata existed).
     *
     * Lives on Account (not AccountViewModel) so that headless callers —
     * notifications' BroadcastReceiver, background workers — can resolve
     * relays without spinning up a ViewModel.
     */
    fun marmotGroupRelays(nostrGroupId: HexKey): Set<NormalizedRelayUrl> {
        val groupRelays =
            account.marmotManager
                ?.groupView(nostrGroupId)
                ?.relays
                ?.mapNotNull {
                    com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
                        .normalizeOrNull(it)
                }?.toSet()
        return if (!groupRelays.isNullOrEmpty()) groupRelays else account.outboxRelays.flow.value
    }

    /**
     * Index an own outbound inner event into the cache and the group's
     * chatroom right now, before any MLS work happens.
     *
     * The bubble the user sees is drawn from the inner rumor, and that rumor
     * exists the moment the composer builds it — everything after this point
     * (ratchet step, group-state write, outer wrap, relay hand-off) only
     * decides *when it leaves*, not what is shown. Doing this first is what
     * makes a send feel instant.
     *
     * Idempotent by construction: the self-decrypt of our own kind:445 runs
     * these same two calls a moment later, and `LocalCache.justConsume` and
     * `MarmotGroupChatroom.addMessageSync` both dedupe.
     */
    fun showOwnMessageLocally(
        nostrGroupId: HexKey,
        innerEvent: Event,
    ) {
        // The same indexing as a received message. A bare justConsume returns false for a kind
        // LocalCache does not dispatch (push-token lists 447-449, edits, stream starts), which
        // left our own copy an EVENTLESS note in the conversation: an "Event is loading or can't
        // be found" row for every push-token answer we sent.
        val innerNote = indexMarmotInnerEvent(innerEvent).note
        account.marmotGroupList.addMessage(nostrGroupId, innerNote)
        // Sending a message moves the group out of "New Requests" into
        // "Known" — do this eagerly before the relay round-trip so the UI
        // updates immediately.
        account.marmotGroupList.markAsKnown(nostrGroupId)
    }

    /**
     * Put an outgoing message on screen in its sending state, synchronously.
     *
     * Split out of [sendMarmotGroupMessage] so a UI caller can run it in the
     * user's own interaction context and hand the rest to a scope that
     * outlives the screen: the composer is then free to clear the input the
     * moment this returns, with the bubble already visible and already
     * pulsing. [sendMarmotGroupMessage] calls it too, so a headless caller
     * gets the same behaviour without a second entry point, and calling both
     * is harmless.
     */
    fun beginMarmotGroupMessage(
        nostrGroupId: HexKey,
        innerEvent: Event,
        /** See [sendMarmotGroupMessage]. */
        expectedEpoch: Long? = null,
    ) {
        // The same two guards sendMarmotGroupMessage returns on. Checked here
        // too, because marking a message as sending and then returning early
        // would leave the bubble pulsing forever with no failure glyph and
        // therefore no way to retry it.
        if (account.marmotManager == null || !account.isWriteable()) {
            Log.w("MarmotDbg") {
                "beginMarmotGroupMessage: cannot send in ${nostrGroupId.take(8)}… (no manager, or a read-only account)"
            }
            showOwnMessageLocally(nostrGroupId, innerEvent)
            account.chatDeliveryTracker.markFailed(innerEvent.id)
            return
        }

        // Marked before the note is indexed, so the bubble never renders a
        // frame without its state. Re-resolving the relay set on retry is the
        // point of taking the id rather than the relays: the commonest reason
        // a Marmot send fails is that the group had none, and that is exactly
        // what the user may have just fixed.
        account.chatDeliveryTracker.markSending(innerEvent.id) {
            sendMarmotGroupMessage(nostrGroupId, innerEvent, marmotGroupRelays(nostrGroupId), expectedEpoch)
        }
        showOwnMessageLocally(nostrGroupId, innerEvent)
    }

    /**
     * Send a message to a Marmot MLS group: show it, encrypt it, publish it.
     *
     * The message is on screen from the first line — see [showOwnMessageLocally]
     * — and carries a sending state until the envelope reaches the relay pool.
     * A failure leaves the bubble in place with a retry attached rather than
     * making what the user typed disappear.
     *
     * Suspends until the send is done, so headless callers (the notification
     * reply receiver, the push-token responder) still finish their work before
     * returning. UI callers get their responsiveness from the optimistic insert
     * plus running this on the account scope, not from it returning early.
     */
    suspend fun sendMarmotGroupMessage(
        nostrGroupId: HexKey,
        innerEvent: Event,
        groupRelays: Set<NormalizedRelayUrl>,
        /**
         * The epoch whose media key encrypted what [innerEvent] carries, when it carries any. The
         * send goes out in that epoch or fails with [MarmotEpochMovedException]: in any other, no
         * receiver could open the attachment. A retry keeps the pin, so a dead reference is never
         * sent; the media has to be attached again.
         */
        expectedEpoch: Long? = null,
    ) {
        Log.d("MarmotDbg") {
            "sendMarmotGroupMessage: group=${nostrGroupId.take(8)}… innerKind=${innerEvent.kind} innerId=${innerEvent.id.take(8)}… " +
                "→ ${groupRelays.size} relay(s): ${groupRelays.map { it.url }}"
        }
        val manager = account.marmotManager ?: return
        if (!account.isWriteable()) return

        beginMarmotGroupMessage(nostrGroupId, innerEvent, expectedEpoch)

        if (groupRelays.isEmpty()) {
            // Previously this only logged and the message was silently
            // dropped. It now surfaces on the bubble as a failed send the
            // user can retry once the group has relays.
            Log.w("MarmotDbg") {
                "sendMarmotGroupMessage: NO group relays for group=${nostrGroupId.take(8)}… — nothing to publish to"
            }
            account.chatDeliveryTracker.markFailed(innerEvent.id)
            return
        }

        val outbound =
            try {
                manager.buildGroupMessage(nostrGroupId, innerEvent, expectedEpoch)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w("MarmotDbg", "sendMarmotGroupMessage: could not build the envelope for $nostrGroupId", e)
                account.chatDeliveryTracker.markFailed(innerEvent.id)
                throw e
            }
        Log.d("MarmotDbg") {
            "sendMarmotGroupMessage: built outer kind:${outbound.signedEvent.kind} id=${outbound.signedEvent.id.take(8)}…"
        }
        // Link the envelope to the inner message we just encrypted so relay
        // OK acceptances drill down to the note the chat renders (see
        // LocalCache.addRelayToNoteAndInners).
        outbound.signedEvent.innerEventId = innerEvent.id
        account.cache.justConsumeMyOwnEvent(outbound.signedEvent)
        // Relays OK the outer kind:445, but the feed shows the inner rumor, so
        // the tick has to be keyed by the rumor id through the envelope id.
        account.chatDeliveryTracker.trackWrappedPublic(innerEvent.id, outbound.signedEvent.id, groupRelays)
        account.client.publish(outbound.signedEvent, groupRelays)
        account.chatDeliveryTracker.markSent(innerEvent.id)

        // The decrypt of our own envelope only persists a message it decrypted
        // for the first time, and the optimistic insert above has already put
        // this one in the cache — so that path will skip it and the message
        // would never reach the on-disk log. Write it here instead. Last,
        // because it rewrites the whole log and nothing on screen waits for it.
        manager.persistDecryptedMessage(nostrGroupId, innerEvent.toJson(), outbound.epoch)
        // Our own removal or dismissal takes effect now, not when the relay echoes it back.
        applyMarmotModeration(nostrGroupId, innerEvent)
    }

    /**
     * Index a decrypted Marmot inner event: cache it, hold it on its note, and link an
     * edit to the message it replaces. Shared by live decryption and the startup restore
     * of the stored message log, so both see the same thing.
     *
     * A kind:1009 edit has no typed class, so `LocalCache` has no case for it and
     * `justConsume` drops it ("Event Not Supported"). The note then had no event and
     * `Note.latestMarmotEdit`, which matches on the event's kind, skipped it: an edit
     * from White Noise decrypted fine and never showed. An edit needs nothing from the
     * cache but its note, so it is attached directly.
     */
    fun indexMarmotInnerEvent(innerEvent: Event): IndexedInnerEvent = indexMarmotInnerEvent(account.cache, innerEvent)

    /**
     * Apply an admin's moderation, when its author was an admin of the group in the epoch it was
     * sent in (see [MarmotManager.adminRemovalTargets] and [MarmotManager.dismissedReportIds]):
     * a kind-4891 removal drops the chat message it names, and a kind-1985 dismissal drops the
     * reports it names, so they no longer count against their message. Runs on live delivery,
     * after our own sends and on the restart replay, which re-adds every stored message; the
     * replay reads back the verdict recorded at delivery, so a later demotion undoes neither.
     */
    suspend fun applyMarmotModeration(
        nostrGroupId: HexKey,
        innerEvent: Event,
    ) {
        val manager = account.marmotManager ?: return
        val groups = account.marmotGroupList
        // An admin's authority ends at their group: a target is removed only when it is one of
        // this group's messages, never a message of another group or a public note it names.
        manager.adminRemovalTargets(nostrGroupId, innerEvent).forEach { targetId ->
            val target = account.cache.getNoteIfExists(targetId)
            val removable = groupMessageToRemove(groups, nostrGroupId, target)
            groups.applyAdminRemoval(nostrGroupId, targetId, target)
            removable?.let { account.cache.removeLocally(it) }
        }
        manager.dismissedReportIds(nostrGroupId, innerEvent).forEach { reportId ->
            groups.applyReportDismissal(nostrGroupId, reportId)
            account.cache
                .getNoteIfExists(reportId)
                ?.takeIf { isGroupReport(nostrGroupId, it) }
                ?.let { account.cache.removeLocally(it) }
        }
        // A message or report whose removal arrived first is dropped as it lands.
        val landedRemoved =
            (innerEvent.kind == ChatEvent.KIND && groups.isAdminRemoved(nostrGroupId, innerEvent.id)) ||
                (innerEvent.kind == ReportEvent.KIND && groups.isReportDismissed(nostrGroupId, innerEvent.id))
        if (landedRemoved) {
            account.cache
                .getNoteIfExists(innerEvent.id)
                ?.takeIf { it.isPrivateRumor() }
                ?.let { account.cache.removeLocally(it) }
        }
    }

    /** True when [report] is a report filed inside [nostrGroupId]: a group rumor about one of its messages. */
    private fun isGroupReport(
        nostrGroupId: HexKey,
        report: Note,
    ): Boolean {
        val event = report.event as? ReportEvent ?: return false
        if (!report.isPrivateRumor()) return false
        return event.reportedPost().any { reported ->
            account.cache.getNoteIfExists(reported.eventId)?.let { account.marmotGroupList.isInGroup(nostrGroupId, it) } == true
        }
    }

    /** [note] holds the inner event; [isNew] is true the first time this client indexed it. */
    class IndexedInnerEvent(
        val note: Note,
        val isNew: Boolean,
    )

    /**
     * React to a Marmot message inside its group.
     *
     * A Marmot message is an unsigned rumor, which the generic reaction path
     * handles as a NIP-17 private note: it gift-wrapped the kind:7 to the
     * author as a DM. That never reached the group, so no other client showed
     * it, and it moved group activity out of the group's channel. The reaction
     * is an ordinary inner kind:7 (MIP-03), encrypted to the group like any
     * message.
     */
    suspend fun reactToMarmotMessage(
        nostrGroupId: HexKey,
        target: Note,
        reaction: String,
    ) {
        val manager = account.marmotManager ?: return
        val targetEvent = target.event ?: return
        if (target.reactionsOfAuthorLike(account.userProfile(), reaction).isNotEmpty()) return
        val rumor = manager.buildReactionRumor(targetEvent, reaction)
        sendMarmotGroupMessage(nostrGroupId, rumor, marmotGroupRelays(nostrGroupId))
    }

    /**
     * Retract our own messages or reactions in a Marmot group with an inner
     * kind:5, for the same reason [reactToMarmotMessage] exists: the generic
     * private path sent a gift-wrapped NIP-09 to the target's author, so the
     * other members never saw the deletion.
     */
    suspend fun deleteMarmotMessages(
        nostrGroupId: HexKey,
        notes: List<Note>,
    ): Boolean {
        val manager = account.marmotManager ?: return false
        val mine = notes.filter { it.author == account.userProfile() }.mapNotNull { it.event }
        if (mine.isEmpty()) return false
        val rumor = manager.buildDeletionRumor(mine)
        sendMarmotGroupMessage(nostrGroupId, rumor, marmotGroupRelays(nostrGroupId))
        return true
    }

    /**
     * Whether this account may delete [note] for everyone in [nostrGroupId]: a
     * chat message by someone else, in a group this account is an admin of now.
     * Receivers check the admin set of the epoch the deletion is sent in, which is
     * this one.
     */
    fun canAdminDeleteMarmotMessage(
        nostrGroupId: HexKey,
        note: Note,
    ): Boolean {
        val manager = account.marmotManager ?: return false
        val event = note.event ?: return false
        return manager.canAdminDelete(nostrGroupId, event)
    }

    /**
     * Delete another member's chat message as a group admin: a kind-4891
     * (`features/content-moderation.md`) inside the group. White Noise sends the
     * same event, and hides the message, its edits and its attachments.
     */
    suspend fun adminDeleteMarmotMessage(
        nostrGroupId: HexKey,
        note: Note,
    ): Boolean {
        if (!canAdminDeleteMarmotMessage(nostrGroupId, note)) return false
        val manager = account.marmotManager ?: return false
        val rumor = manager.buildAdminDeletionRumor(note.idHex)
        sendMarmotGroupMessage(nostrGroupId, rumor, marmotGroupRelays(nostrGroupId))
        return true
    }

    /**
     * Fetch a user's KeyPackage from relays and add them to a Marmot group.
     * Returns a status message describing the outcome.
     */
    suspend fun fetchKeyPackageAndAddMember(
        nostrGroupId: HexKey,
        memberPubKey: HexKey,
    ): String {
        Log.d("MarmotDbg") {
            "fetchKeyPackageAndAddMember: group=${nostrGroupId.take(8)}… member=${memberPubKey.take(8)}…"
        }
        val manager = account.marmotManager ?: return "Error: Marmot not initialized"
        if (!account.isWriteable()) return "Error: Account is read-only"

        // Per MIP-00, invitees advertise the relays that host their
        // KeyPackages in a kind:10051 KeyPackageRelayListEvent. Look
        // there first, then fall back to the invitee's NIP-65 outbox
        // (where KeyPackages typically also land), and finally union
        // with our own outbox so we still find packages that ended up
        // on a shared relay.
        val myOutbox = account.outboxRelays.flow.value
        val memberKeyPackageRelays =
            (
                account.cache
                    .getAddressableNoteIfExists(
                        com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageRelayListEvent
                            .createAddress(memberPubKey),
                    )?.event as? com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageRelayListEvent
            )?.dialableRelays()?.toSet().orEmpty()
        val memberOutbox =
            account.cache
                .getOrCreateUser(memberPubKey)
                .outboxRelays()
                ?.toSet()
                .orEmpty()
        // Both kinds: White Noise's MDK still publishes legacy kind:443, some
        // of its users nothing else. The newest valid 30443 wins; a valid 443
        // is the fallback (MIP-00 migration rules). A package that comes back
        // is valid, so it has content.
        val lookup =
            KeyPackageFetcher.fetchForInvite(
                client = account.client.taggedAs(SubPurpose.ENCRYPTED_GROUPS),
                targetPubKey = memberPubKey,
                targetOutbox = memberOutbox,
                myOutbox = myOutbox,
                targetKeyPackageRelays = memberKeyPackageRelays,
            )
        val event = lookup.keyPackage

        if (event == null) {
            Log.w("MarmotDbg") {
                "fetchKeyPackageAndAddMember: NO valid KeyPackage found for ${memberPubKey.take(8)}… on any of ${lookup.relays.size} relay(s) " +
                    "(memberKeyPackageRelays=${memberKeyPackageRelays.size}, memberOutbox=${memberOutbox.size}, myOutbox=${myOutbox.size}): ${lookup.relays.map { it.url }}"
            }
            return "Error: No KeyPackage found for this user. They may not have published one yet."
        }

        Log.d("MarmotDbg") {
            "fetchKeyPackageAndAddMember: got KeyPackage event id=${event.id.take(8)}… kind=${event.kind} authored=${event.pubKey.take(8)}…"
        }

        // The commit goes to the GROUP's relays (its routing component), where
        // every member listens. Our own outbox is only the fallback for a group
        // that names none: a member reading other relays would never see the
        // commit and fall an epoch behind.
        val groupRelays = marmotGroupRelays(nostrGroupId).toList()

        Log.d("MarmotDbg") {
            "fetchKeyPackageAndAddMember: addMarmotGroupMember → groupRelays=${groupRelays.size}: ${groupRelays.map { it.url }}"
        }

        addMarmotGroupMember(
            nostrGroupId = nostrGroupId,
            keyPackageEvent = event,
            groupRelays = groupRelays,
        )

        return "Success: Member added to group"
    }

    /**
     * Add a member to a Marmot MLS group.
     * Publishes the commit GroupEvent, then sends the Welcome gift wrap.
     */
    suspend fun addMarmotGroupMember(
        nostrGroupId: HexKey,
        keyPackageEvent: PublishedKeyPackage,
        groupRelays: List<NormalizedRelayUrl>,
    ) {
        val memberPubKey = keyPackageEvent.pubKey
        Log.d("MarmotDbg") {
            "addMarmotGroupMember: group=${nostrGroupId.take(8)}… member=${memberPubKey.take(8)}… " +
                "groupRelays=${groupRelays.size}"
        }
        val manager = account.marmotManager ?: return
        if (!account.isWriteable()) return

        val (commitEvent, welcomeDelivery) =
            manager.addMember(
                nostrGroupId = nostrGroupId,
                keyPackageEvent = keyPackageEvent,
                relays = groupRelays,
            )

        // The MLS commit has already been applied to the local group state —
        // surface the new member list in the chatroom now so observers (e.g.
        // MarmotGroupInfoScreen) update without waiting for our own commit to
        // loop back through the relay.
        val chatroom = account.marmotGroupList.getOrCreateGroup(nostrGroupId)
        manager.syncMetadataTo(nostrGroupId, chatroom)

        Log.d("MarmotDbg") {
            val commit =
                commitEvent?.let { "kind=${it.signedEvent.kind} id=${it.signedEvent.id.take(8)}…" }
                    ?: "none (founding add, merged locally)"
            "addMarmotGroupMember: built commit $commit " +
                "welcomeDelivery=${if (welcomeDelivery != null) "present(giftWrapId=${welcomeDelivery.giftWrapEvent.id.take(8)}…)" else "null"}"
        }

        // Nothing to publish here either way. A normal commit was already
        // published by the manager, which only advances the group once a relay
        // acknowledged it (publish-before-apply); publishing it again would
        // just duplicate the event. A FOUNDING add has no commit at all — the
        // creator was the group's only member, so it merges locally under the
        // empty publication obligation and the invitee gets epoch 1 from the
        // Welcome.
        Log.d("MarmotDbg") {
            commitEvent?.let { "addMarmotGroupMember: commit kind:${it.signedEvent.kind} published to ${groupRelays.size} relay(s)" }
                ?: "addMarmotGroupMember: founding add merged locally, no commit published"
        }

        // Then send the Welcome gift wrap to the new member.
        //
        // Use the same delivery path that NIP-17 DMs (kind:1059) take —
        // computeRelayListToBroadcast() — which has fallbacks for kind:10050
        // → NIP-65 read → relay hints. Empirically, NIP-17 DMs reach the
        // invitee, so this path is the one we know works. We also union
        // with our own outbox + the recipient's dmInboxRelays() as a
        // belt-and-braces measure in case the cache hasn't been hydrated
        // yet for this contact.
        if (welcomeDelivery != null) {
            val computed = account.broadcaster.computeRelayListToBroadcast(welcomeDelivery.giftWrapEvent)
            val recipientInbox =
                account.cache
                    .getOrCreateUser(memberPubKey)
                    .dmInboxRelays()
                    .orEmpty()
            val relayList = computed + account.outboxRelays.flow.value + recipientInbox
            Log.d("MarmotDbg") {
                "addMarmotGroupMember: welcome gift wrap relay sources " +
                    "computeRelayListToBroadcast=${computed.size} myOutbox=${account.outboxRelays.flow.value.size} " +
                    "recipientInbox=${recipientInbox.size} → union=${relayList.size}"
            }
            if (relayList.isEmpty()) {
                Log.w("MarmotDbg") {
                    "addMarmotGroupMember: NO relays to deliver welcome gift wrap to ${memberPubKey.take(8)}… — welcome will be silently dropped"
                }
            } else {
                Log.d("MarmotDbg") {
                    "addMarmotGroupMember: publishing welcome gift wrap id=${welcomeDelivery.giftWrapEvent.id.take(8)}… " +
                        "kind:${welcomeDelivery.giftWrapEvent.kind} → ${relayList.size} relay(s): ${relayList.map { it.url }}"
                }
            }
            account.client.publish(welcomeDelivery.giftWrapEvent, relayList)
        } else {
            Log.w("MarmotDbg") {
                "addMarmotGroupMember: welcomeDelivery is NULL — invitee ${memberPubKey.take(8)}… will receive nothing!"
            }
        }
    }

    /**
     * Relays where this account publishes kind:30443 KeyPackage events.
     *
     * The NIP-65 write set is the discovery rule now — the spec removed the
     * dedicated kind:10051 KeyPackage relay list. The account's own 10051 is
     * still unioned in so peers that have not migrated keep finding us.
     */
    fun keyPackagePublishRelays(): Set<NormalizedRelayUrl> =
        KeyPackageFetcher.publishRelaysFor(
            myOutbox = account.outboxRelays.flow.value,
            legacyKeyPackageRelayList = account.keyPackageRelayList.flow.value,
        )

    /**
     * Publish or rotate KeyPackage events.
     */
    suspend fun publishMarmotKeyPackages() {
        val manager =
            account.marmotManager ?: run {
                Log.w("MarmotDbg") { "publishMarmotKeyPackages: marmotManager is NULL — no-op" }
                return
            }
        if (!account.isWriteable()) {
            Log.w("MarmotDbg") { "publishMarmotKeyPackages: account is not writeable — no-op" }
            return
        }

        val relays = keyPackagePublishRelays()
        val needsRotation = manager.needsKeyPackageRotation()
        Log.d("MarmotDbg") {
            "publishMarmotKeyPackages: needsRotation=$needsRotation relays=${relays.size}"
        }

        if (needsRotation) {
            val rotatedEvents = manager.rotateConsumedKeyPackages(relays.toList())
            Log.d("MarmotDbg") {
                "publishMarmotKeyPackages: rotateConsumedKeyPackages produced ${rotatedEvents.size} event(s)"
            }
            rotatedEvents.forEach { event ->
                account.cache.justConsumeMyOwnEvent(event)
                Log.d("MarmotDbg") {
                    "publishMarmotKeyPackages: publishing rotated kind:${event.kind} id=${event.id.take(8)}… " +
                        "→ ${relays.size} relay(s): ${relays.map { it.url }}"
                }
                // Confirmed, not just queued: a consumed KeyPackage displaced
                // from this slot keeps its private keys for 24 hours after its
                // replacement is CONFIRMED published, then they are deleted
                // (`foundation/key-packages.md`). The manager can only start
                // that clock if it hears a relay accepted the replacement.
                publishKeyPackage(manager, event, relays)
            }
            // A rotation we just published makes this device the newest owner.
            // Leaving the old answer in place would keep the banner warning for
            // up to its full life about a state that no longer exists.
            lastOwnerCheck = null
        }
    }

    /**
     * Publish [event], a KeyPackage [manager] just made active, and start its replacement
     * clock once a relay confirms it. A first failure is retried in the background with
     * backoff: the new KeyPackage is already the active one, so nothing else would publish
     * it again, and inviters would keep finding only the one it replaced.
     */
    private suspend fun publishKeyPackage(
        manager: MarmotManager,
        event: KeyPackageEvent,
        relays: Set<NormalizedRelayUrl>,
    ) {
        if (account.client.publishAndConfirm(event, relays)) {
            manager.confirmKeyPackagePublished(event)
            return
        }
        Log.w("MarmotDbg") { "no relay confirmed KeyPackage ${event.id.take(8)}…; retrying in the background" }
        account.scope.launch(Dispatchers.IO) {
            var wait = KEY_PACKAGE_RETRY_FIRST_MS
            repeat(KEY_PACKAGE_RETRIES) {
                delay(wait)
                wait *= 4
                if (account.client.publishAndConfirm(event, relays)) {
                    manager.confirmKeyPackagePublished(event)
                    return@launch
                }
            }
            Log.w("MarmotDbg") { "KeyPackage ${event.id.take(8)}… was never confirmed by a relay" }
        }
    }

    /**
     * Generate and publish initial KeyPackage for this account.
     */
    suspend fun publishMarmotKeyPackage() {
        val manager = account.marmotManager ?: return
        if (!account.isWriteable()) return

        val relays = keyPackagePublishRelays()
        Log.d("MarmotDbg") {
            "publishMarmotKeyPackage: generating + publishing KeyPackage event → ${relays.size} relay(s): ${relays.map { it.url }}"
        }
        val event = manager.generateKeyPackageEvent(relays.toList())
        Log.d("MarmotDbg") {
            "publishMarmotKeyPackage: signed kind:${event.kind} id=${event.id.take(8)}… authored=${event.pubKey.take(8)}…"
        }
        account.cache.justConsumeMyOwnEvent(event)
        // A fresh mint can displace an earlier bundle in the same slot, so the
        // confirmation starts that bundle's grace period like a rotation does.
        publishKeyPackage(manager, event, relays)
        // Same as the rotation path: we have just changed who owns the newest
        // KeyPackage, so any cached answer is stale by construction.
        lastOwnerCheck = null
    }

    /**
     * Drop this device's copy of a group it has fallen out of sync with, and make sure a
     * fresh KeyPackage is out there for the admin's re-invite. See
     * [MarmotManager.resetOutOfSyncGroup].
     */
    suspend fun resetOutOfSyncMarmotGroup(nostrGroupId: HexKey) {
        val manager = account.marmotManager ?: return
        manager.resetOutOfSyncGroup(nostrGroupId)
        val chatroom = account.marmotGroupList.getOrCreateGroup(nostrGroupId)
        chatroom.isOutOfSync.value = false
        chatroom.awaitingReinvite.value = true
        account.marmotGroupList.notifyGroupChanged(nostrGroupId)
        ensureMarmotKeyPackagePublished()
    }

    /**
     * Ensure the local user has at least one active KeyPackage bundle and
     * a published KeyPackage event on relays. Called from [init] after
     * Marmot state has been restored from disk.
     *
     * - If [KeyPackageRotationManager] already has an active bundle (from
     *   the persisted snapshot), we trust the previous session and do
     *   nothing. The matching kind:30443 should already be on relays from
     *   when the bundle was first generated.
     * - Otherwise we generate a fresh bundle (which is now persisted to
     *   disk by [KeyPackageRotationManager.generateKeyPackage]) and
     *   publish the corresponding event.
     *
     * Best-effort: failures are logged but never propagated. We don't want
     * a flaky relay or missing outbox config at startup to crash account
     * initialization.
     */
    suspend fun ensureMarmotKeyPackagePublished() {
        val manager = account.marmotManager ?: return
        if (!account.isWriteable()) return
        try {
            val hasBundle = manager.hasActiveKeyPackages()
            Log.d("MarmotDbg") {
                "ensureMarmotKeyPackagePublished: hasActiveKeyPackages=$hasBundle for ${account.signer.pubKey.take(8)}…"
            }
            if (hasBundle) {
                // A KeyPackage minted by an older release advertises fewer app
                // components than this one supports. Replace it, so an inviter
                // can add us to a group that requires a newly supported one
                // (`foundation/key-packages.md`).
                val relays = keyPackagePublishRelays()
                manager.replaceStaleKeyPackages(relays.toList()).forEach { event ->
                    Log.d("MarmotDbg") { "ensureMarmotKeyPackagePublished: replacing stale KeyPackage → ${event.id.take(8)}…" }
                    account.cache.justConsumeMyOwnEvent(event)
                    publishKeyPackage(manager, event, relays)
                }
                return
            }
            Log.d("MarmotDbg") {
                "ensureMarmotKeyPackagePublished: no active bundle — generating + publishing now"
            }
            publishMarmotKeyPackage()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w("MarmotDbg", "ensureMarmotKeyPackagePublished failed: ${e.message}", e)
        }
    }

    /**
     * Keep this device's leaf in every group advertising every app component
     * the client supports (`app-components/README.md`, "Advertising support").
     * The manager delays each refresh by a random amount within a window of
     * hours, so a new release does not make a whole group commit at once; this
     * loop only gives it a clock. Runs until [account]'s scope is cancelled.
     */
    suspend fun runMarmotLeafRefreshLoop() {
        while (currentCoroutineContext().isActive) {
            val manager = account.marmotManager ?: return
            if (account.isWriteable()) {
                try {
                    val refreshed = manager.refreshStaleLeaves()
                    if (refreshed.isNotEmpty()) Log.d("MarmotDbg") { "refreshed our leaf in ${refreshed.size} group(s)" }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.w("MarmotDbg", "leaf refresh pass failed", e)
                }
            }
            delay(LEAF_REFRESH_CHECK_INTERVAL_MS)
        }
    }

    /**
     * Check if a KeyPackage has been published in this session.
     * The d-tag is a randomly-generated value stored in the KeyPackageRotationManager's
     * persisted snapshot, so there is no fixed address to query in the cache.
     */
    suspend fun hasPublishedKeyPackage(): Boolean {
        val manager = account.marmotManager ?: return false
        return manager.hasActiveKeyPackages()
    }

    /**
     * Ask the relays which install currently owns this account's invites.
     *
     * Deliberately a read, never a self-correcting one. Republishing whenever
     * the answer is [LatestKeyPackageOwner.OTHER_DEVICE] would deadlock two
     * installs against each other — each device's correction is the other's
     * trigger, and neither ever settles — so the decision belongs to the user,
     * with this as the evidence.
     *
     * Queries the same set we publish our own KeyPackages to, which is the
     * inviter's view of us minus their own outbox: `fetchRelaysFor` unions our
     * NIP-65 write set and our legacy kind:10051 with the inviter's outbox, and
     * the first two are exactly [keyPackagePublishRelays].
     */
    suspend fun latestKeyPackageOwner(maxAgeSeconds: Long = 0L): LatestKeyPackageOwner {
        val manager = account.marmotManager ?: return LatestKeyPackageOwner.NONE
        val relays = keyPackagePublishRelays()
        if (relays.isEmpty()) return LatestKeyPackageOwner.NONE

        // A passive caller (the groups-screen banner) may reuse a recent answer.
        // Without this, every entry to that screen fanned a REQ out across the
        // whole write set — and the thing it asks about only changes when
        // another device publishes, which is rare enough to cache.
        val cached = lastOwnerCheck
        if (maxAgeSeconds > 0 && cached != null) {
            val maxAge =
                if (cached.second == LatestKeyPackageOwner.NONE) {
                    minOf(maxAgeSeconds, NONE_MAX_AGE_SECONDS)
                } else {
                    maxAgeSeconds
                }
            if (TimeUtils.now() - cached.first <= maxAge) return cached.second
        }

        val latest = KeyPackageFetcher.fetchKeyPackage(account.client.taggedAs(SubPurpose.ENCRYPTED_GROUPS), account.signer.pubKey, relays)

        val owner =
            when {
                latest == null -> LatestKeyPackageOwner.NONE
                manager.ownsKeyPackage(latest) -> LatestKeyPackageOwner.THIS_DEVICE
                else -> LatestKeyPackageOwner.OTHER_DEVICE
            }
        Log.d("MarmotDbg") {
            "latestKeyPackageOwner: newest KeyPackage id=${latest?.id?.take(8)}… " +
                "createdAt=${latest?.createdAt} owner=$owner"
        }
        // Cached even when nothing was found: that answer cost the same fan-out
        // as any other, so leaving it uncached would re-run the whole query on
        // every entry for exactly the accounts with nothing on their relays.
        lastOwnerCheck = TimeUtils.now() to owner
        return owner
    }

    /**
     * The user-initiated republish behind the invite-device banner and the
     * settings row. Returns whether a relay actually accepted the KeyPackage.
     *
     * Deliberately not [publishMarmotKeyPackage]. That one is the best-effort
     * startup path: it early-returns in silence for a read-only account or an
     * empty relay set and then hands the event to a fire-and-forget
     * `client.publish`, so a caller reporting the outcome to someone watching
     * would call every one of those failures a success.
     */
    suspend fun republishKeyPackageConfirmed(): Boolean {
        val manager = account.marmotManager ?: return false
        if (!account.isWriteable()) return false
        val relays = keyPackagePublishRelays()
        if (relays.isEmpty()) return false

        // Minting no longer destroys the displaced bundle — the rotation
        // manager retains it, keyed by the event id it was published as — but
        // every regeneration still costs a keypair, a relay round trip, and a
        // slot in the bounded retention map, where it can evict a bundle
        // someone is about to invite us through. Republishing when we already
        // own the newest KeyPackage buys none of that back, since the answer
        // cannot change, so that case is a no-op reporting success truthfully.
        if (latestKeyPackageOwner() == LatestKeyPackageOwner.THIS_DEVICE) {
            Log.d("MarmotDbg") { "republishKeyPackageConfirmed: already the newest; not minting" }
            return true
        }

        val event = manager.generateKeyPackageEvent(relays.toList())
        account.cache.justConsumeMyOwnEvent(event)
        val accepted = account.client.publishAndConfirm(event, relays)
        Log.d("MarmotDbg") {
            "republishKeyPackageConfirmed: id=${event.id.take(8)}… accepted=$accepted on ${relays.size} relay(s)"
        }
        // The answer we just changed; a stale cache would keep the banner up.
        lastOwnerCheck = if (accepted) TimeUtils.now() to LatestKeyPackageOwner.THIS_DEVICE else null
        return accepted
    }

    /**
     * Create a new Marmot MLS group under the CURRENT profile.
     *
     * Not the legacy `0xF2EE` shape. A current-profile peer refuses a leaf
     * with no account identity proof, and a legacy group cannot be upgraded
     * into one afterwards — its existing leaves have no proofs to add — so the
     * profile is decided here, once, and never migrated. Groups made the old
     * way are joinable only by other legacy clients.
     *
     * The name, description, retention and the routing component (the
     * `nostr_group_id` every kind-445 event is addressed to, and the relays)
     * go in at epoch 0. The avatar and the media policy are later commits:
     * `BaseAccountViewModel.createMarmotGroup` makes them.
     */
    suspend fun createMarmotGroup(
        nostrGroupId: HexKey,
        name: String = "",
        description: String = "",
        /**
         * Disappearing messages (`0x8005`), or null for off. Fixed at creation:
         * promoting a component to required later needs its state installed by
         * a prior commit, which this path does not make.
         */
        disappearingMessageSecs: ULong? = null,
    ) {
        val manager = account.marmotManager ?: return
        if (!account.isWriteable()) return
        manager.createCurrentProfileGroup(
            nostrGroupId = nostrGroupId,
            relays =
                account.outboxRelays.flow.value
                    .map { it.url },
            profile = if (name.isEmpty() && description.isEmpty()) null else GroupProfileV1(name, description),
            retention = disappearingMessageSecs?.let { MessageRetentionV1(it) },
        )
        // Creator owns the group — mark it as "known" immediately so it
        // doesn't appear under "New Requests" before the first message.
        account.marmotGroupList.markAsKnown(nostrGroupId)
        syncAndNotify(nostrGroupId)
    }

    /**
     * Copy the group's current MLS state (name, admins, members, relays) into its
     * chatroom and tell the list to re-render it. For actions that commit outside the
     * paths here which already sync.
     */
    fun syncAndNotify(nostrGroupId: HexKey) {
        val manager = account.marmotManager ?: return
        manager.syncMetadataTo(nostrGroupId, account.marmotGroupList.getOrCreateGroup(nostrGroupId))
        account.marmotGroupList.notifyGroupChanged(nostrGroupId)
    }

    /**
     * Leave a Marmot MLS group: step down if an admin (naming an heir when no
     * other admin is a member, see [MarmotManager.leave]), publish the
     * SelfRemove proposal and drop the chatroom.
     */
    suspend fun leaveMarmotGroup(
        nostrGroupId: HexKey,
        groupRelays: Set<NormalizedRelayUrl>,
    ) {
        val manager = account.marmotManager ?: return
        if (!account.isWriteable()) return

        val departure = manager.leave(nostrGroupId, groupRelays.toList())
        // manager.leave already wiped MLS state, relay subscriptions and
        // the persisted message log. Drop the in-memory chatroom too — that
        // releases the strong refs to the decrypted inner notes so LocalCache
        // (which holds them weakly) can GC them, and the Notification feed
        // (which iterates account.marmotGroupList.rooms) stops surfacing the group.
        account.marmotGroupList.removeGroup(nostrGroupId)
        emojiUploads.forget(nostrGroupId)
        departure.selfRemove?.let { account.client.publish(it.signedEvent, groupRelays) }
    }

    /**
     * User-initiated "nuclear" reset for the Marmot subsystem.
     *
     * Wipes every MLS group, every retained epoch secret, every persisted
     * KeyPackage bundle, every relay subscription and every in-memory
     * chatroom associated with this account. Does NOT broadcast any
     * SelfRemove/leave commits to peers — if the user is in this flow at
     * all, local state may already be unusable and a graceful leave is
     * probably not possible. Peers will see the user as unresponsive until
     * their next commit evicts the stale leaf.
     *
     * A fresh KeyPackage will be republished lazily on the next
     * `ensureMarmotKeyPackagePublished` cycle, so the account remains
     * reachable for future group invites.
     */
    suspend fun resetMarmotState() {
        Log.w("MarmotDbg") { "resetMarmotState(): wiping all Marmot state for ${account.signer.pubKey.take(8)}…" }
        account.marmotManager?.resetAllState()
        for (groupId in account.marmotGroupList.allGroupIds()) {
            account.marmotGroupList.removeGroup(groupId)
            emojiUploads.forget(groupId)
        }
    }

    /**
     * Remove a member from a Marmot MLS group.
     * Publishes the commit GroupEvent to group relays.
     */
    suspend fun removeMarmotGroupMember(
        nostrGroupId: HexKey,
        targetLeafIndex: Int,
        groupRelays: Set<NormalizedRelayUrl>,
    ) {
        Log.d("MarmotDbg") {
            "removeMarmotGroupMember: group=${nostrGroupId.take(8)}… targetLeafIndex=$targetLeafIndex " +
                "groupRelays=${groupRelays.size}"
        }
        val manager =
            account.marmotManager ?: run {
                Log.w("MarmotDbg") { "removeMarmotGroupMember: marmotManager is NULL — no-op" }
                return
            }
        if (!account.isWriteable()) {
            Log.w("MarmotDbg") { "removeMarmotGroupMember: account is not writeable — no-op" }
            return
        }

        val outbound = manager.removeMember(nostrGroupId, targetLeafIndex, groupRelays.toList())
        Log.d("MarmotDbg") {
            "removeMarmotGroupMember: built commit kind=${outbound.signedEvent.kind} id=${outbound.signedEvent.id.take(8)}…"
        }
        val chatroom = account.marmotGroupList.getOrCreateGroup(nostrGroupId)
        manager.syncMetadataTo(nostrGroupId, chatroom)
        Log.d("MarmotDbg") {
            "removeMarmotGroupMember: commit id=${outbound.signedEvent.id.take(8)}… " +
                "published to ${groupRelays.size} relay(s): ${groupRelays.map { it.url }}"
        }
    }

    /**
     * Update a Marmot MLS group's metadata (name, description, etc.).
     * Publishes the commit GroupEvent to group relays.
     */
    suspend fun updateMarmotGroupMetadata(
        nostrGroupId: HexKey,
        metadata: com.vitorpamplona.marmot.mip01Groups.MarmotGroupData,
        groupRelays: Set<NormalizedRelayUrl>,
    ) {
        val manager = account.marmotManager ?: return
        if (!account.isWriteable()) return

        manager.updateGroupMetadata(nostrGroupId, metadata, groupRelays.toList())
        // The commit was published and acknowledged before it became canonical,
        // so the local state is already the one peers will see — surface it now
        // rather than waiting for our own event to loop back.
        val chatroom = account.marmotGroupList.getOrCreateGroup(nostrGroupId)
        manager.syncMetadataTo(nostrGroupId, chatroom)
    }

    /**
     * Disband a Marmot MLS group (`marmot.group.lifecycle.v1`, `0x800c`).
     *
     * Irreversible and absorbing: every member's copy terminalizes when they
     * apply the commit, and there is no commit that walks it back. The caller
     * MUST have confirmed with a human first — this layer only refuses what is
     * structurally impossible (a non-admin, a legacy group, a second disband),
     * which is not the same as asking.
     *
     * Deliberately NOT silent on failure the way the other actions here are: a
     * disband that did not happen must not look like one that did, so the
     * exception propagates to the caller's error path.
     *
     * It is no longer terminal the moment it is published, either: the Commit
     * is admitted as a convergence candidate and only a SELECTED one moves the
     * group to `Disbanded`, so between the two the request sits behind a
     * durable `Disbanding` gate. Reporting that distinction is the whole point
     * of the return value — announcing "group disbanded" for a request that is
     * still pending is the one thing a terminal action must never do.
     *
     * @return true when the group is terminal now; false when the request is
     *   durable and unresolved, which is not a failure.
     */
    suspend fun disbandMarmotGroup(
        nostrGroupId: HexKey,
        groupRelays: Set<NormalizedRelayUrl>,
    ): Boolean {
        val manager = account.marmotManager ?: return false
        if (!account.isWriteable()) return false

        manager.disbandGroup(nostrGroupId, groupRelays.toList())
        val chatroom = account.marmotGroupList.getOrCreateGroup(nostrGroupId)
        manager.syncMetadataTo(nostrGroupId, chatroom)
        return manager.lifecycle(nostrGroupId) == GroupLifecycleState.DISBANDED
    }

    /**
     * Commit the `encrypted-media-v2` policy (`0x800b`) for a group.
     *
     * Creation deliberately leaves this off — `CurrentProfileGroupFactory`
     * explains why: carrying it at epoch 0 would make our GroupContext differ
     * from the reference's for the same inputs, and would force every joiner to
     * advertise `0x800b` before it could be added. The spec's answer is that "a
     * group that wants a media policy commits one", and until now nothing on
     * Android could, so `marmotUsesEncryptedMediaV2` was false for every group
     * this app created and attachments always fell back to MIP-04.
     *
     * Enable-only on purpose. Changing the policy later is the same commit;
     * REMOVING it is a different question the component does not answer, and
     * inventing a removal that strands members mid-upload is not something to
     * guess at.
     *
     * The endpoints come from the account's own Blossom server list, because a
     * policy naming servers the uploader does not use would describe a group
     * nobody can actually post media to. An account without one uploads to the
     * default list (the same one the upload picker offers and falls back
     * through), so that is what its policy names.
     */
    fun marmotMediaPolicyServers(): List<String> =
        account.blossomServers.flow.value
            .ifEmpty {
                account.blossomServers.hostNameFlow.value
                    .filter { it.type == ServerType.Blossom }
                    .map { it.baseUrl }
            }.mapNotNull { url ->
                // A server with no base-URL form (a query, say) is left out rather than
                // failing the whole commit.
                try {
                    EncryptedMediaPolicyV2.normalizeBaseUrl(url)
                } catch (e: IllegalArgumentException) {
                    null
                }
            }.distinct()
            .take(EncryptedMediaPolicyV2.MAX_ENTRIES)

    suspend fun enableMarmotEncryptedMediaV2(nostrGroupId: HexKey) {
        val manager = account.marmotManager ?: return
        if (!account.isWriteable()) return

        val servers = marmotMediaPolicyServers()
        require(servers.isNotEmpty()) {
            "Cannot enable encrypted media without at least one Blossom server configured"
        }
        val policy = EncryptedMediaPolicyV2.forBlossom(servers)
        manager.setEncryptedMediaPolicy(nostrGroupId, policy, marmotGroupRelays(nostrGroupId).toList())
        val chatroom = account.marmotGroupList.getOrCreateGroup(nostrGroupId)
        manager.syncMetadataTo(nostrGroupId, chatroom)
    }

    /**
     * Set or clear the group's plain-`https` avatar link
     * (`marmot.group.avatar-url.v1`, `0x8007`).
     *
     * The lightweight avatar carrier: no Blossom upload, no key material, just
     * a URL every Marmot client can render. A blank [url] clears it, which
     * falls the group back to its encrypted Blossom image if it has one — the
     * two carriers coexist and this one wins while it is set.
     */
    suspend fun setMarmotGroupAvatarUrl(
        nostrGroupId: HexKey,
        url: String,
        groupRelays: Set<NormalizedRelayUrl>,
    ) {
        val manager = account.marmotManager ?: return
        if (!account.isWriteable()) return

        val avatar = url.trim().takeIf { it.isNotEmpty() }?.let { GroupAvatarUrlV1(MarmotWebUrl.normalize(it, label = "avatar URL")) }
        manager.setGroupAvatarUrl(nostrGroupId, avatar, groupRelays.toList())
        val chatroom = account.marmotGroupList.getOrCreateGroup(nostrGroupId)
        manager.syncMetadataTo(nostrGroupId, chatroom)
    }

    /**
     * Grant admin privileges to [targetPubKey] in a Marmot MLS group by
     * appending them to `admin_pubkeys` via a GroupContextExtensions commit.
     *
     * No-op if the group has no prior metadata (shouldn't happen outside the
     * first bootstrap commit) or the target is already an admin. Callers
     * must be an admin themselves — the MLS engine enforces this via the
     * MIP-03 authorization gate in `enforceAuthorizedProposalSet`.
     */
    suspend fun grantMarmotGroupAdmin(
        nostrGroupId: HexKey,
        targetPubKey: HexKey,
        groupRelays: Set<NormalizedRelayUrl>,
    ) {
        val manager = account.marmotManager ?: return
        if (!account.isWriteable()) return

        val view = manager.groupView(nostrGroupId) ?: return
        if (view.adminPubkeys.contains(targetPubKey)) return

        manager.setGroupAdmins(nostrGroupId, view.adminPubkeys + targetPubKey, groupRelays.toList())
        // The commit is canonical once published; without this the roster and the
        // admin badges keep the pre-commit admin list until our own echo or a restart.
        manager.syncMetadataTo(nostrGroupId, account.marmotGroupList.getOrCreateGroup(nostrGroupId))
    }

    /**
     * Revoke admin privileges from [targetPubKey]. Rejects any change that
     * would leave no admin among the members ([MarmotGroupPermissions.canDemote]);
     * peers refuse that commit as admin depletion.
     */
    suspend fun revokeMarmotGroupAdmin(
        nostrGroupId: HexKey,
        targetPubKey: HexKey,
        groupRelays: Set<NormalizedRelayUrl>,
    ) {
        val manager = account.marmotManager ?: return
        if (!account.isWriteable()) return

        val view = manager.groupView(nostrGroupId) ?: return
        if (!view.adminPubkeys.contains(targetPubKey)) return
        val members = manager.memberPubkeys(nostrGroupId).map { it.pubkey }
        check(MarmotGroupPermissions.canDemote(view.adminPubkeys, members, account.signer.pubKey, targetPubKey)) {
            "Cannot revoke the last admin among the members of a Marmot group (MIP-03)"
        }

        manager.setGroupAdmins(nostrGroupId, view.adminPubkeys.filter { it != targetPubKey }, groupRelays.toList())
        // The commit is canonical once published; without this the roster and the
        // admin badges keep the pre-commit admin list until our own echo or a restart.
        manager.syncMetadataTo(nostrGroupId, account.marmotGroupList.getOrCreateGroup(nostrGroupId))
    }

    // ---- Encrypted custom emoji, polls (MDK 0.11/0.12 interop) --------------

    /**
     * Where custom-emoji images are fetched from and their ciphertext uploaded
     * to. Set by the platform; null keeps the old public-URL emoji behaviour.
     */
    var emojiMediaPort: MarmotEmojiMediaPort? = null

    /**
     * Pack pictures already downloaded, by URL: a pack is a handful of small images used over and
     * over, so each is fetched once. Bounded by bytes; the port caps every download.
     */
    private val emojiImages =
        object : LruCache<String, MarmotEmojiMediaPort.Image>(EMOJI_IMAGE_CACHE_BYTES) {
            override fun sizeOf(
                key: String,
                value: MarmotEmojiMediaPort.Image,
            ) = value.bytes.size
        }

    private val emojiUploads = MarmotEmojiUploadCache()

    /**
     * Encrypt the picture of [emoji] as an `encrypted-media-v2` attachment of
     * [nostrGroupId] and upload it, the way MDK sends a custom emoji: the
     * picture travels as ciphertext and the group sees only the reference.
     *
     * Encrypted under the current epoch's media key, which [AttachedEmoji.epoch] names: the message
     * carrying it must go out pinned to that epoch (see [sendMarmotGroupMessage]).
     * Returns null when the platform has no port, the image cannot be fetched,
     * or no blob server takes it.
     */
    suspend fun encryptMarmotCustomEmoji(
        nostrGroupId: HexKey,
        emoji: EmojiUrlTag,
    ): AttachedEmoji? {
        val manager = account.marmotManager ?: return null
        val port = emojiMediaPort ?: return null
        return try {
            val key = manager.mediaUploadKey(nostrGroupId) ?: return null
            emojiUploads.get(nostrGroupId, key.epoch, emoji.url)?.let { return AttachedEmoji(it, key.epoch) }
            val image = emojiImages[emoji.url] ?: port.download(emoji.url)?.also { emojiImages.put(emoji.url, it) } ?: return null
            val mediaType =
                image.contentType
                    ?.substringBefore(';')
                    ?.trim()
                    ?.let { MarmotMediaType.canonicalize(it) }
                    ?.takeIf { it.startsWith("image/") }
                    ?: mediaTypeFromUrl(emoji.url)
                    ?: return null
            val filename = emojiFilename(emoji.code, mediaType)
            val sealed = EncryptedMediaV2.encrypt(image.bytes, key.mediaSecret, mediaType, filename)
            val url = port.uploadCiphertext(sealed.ciphertext) ?: return null
            val reference =
                EncryptedMediaReferenceV2(
                    locators = listOf(MediaLocatorV2(EncryptedMediaPolicyV2.INITIAL_LOCATOR_KIND, url)),
                    ciphertextSha256 = sealed.ciphertextSha256,
                    plaintextSha256 = sealed.plaintextSha256,
                    nonce = sealed.nonce,
                    mediaType = mediaType,
                    filename = filename,
                )
            emojiUploads.remember(nostrGroupId, key.epoch, emoji.url, reference)
            AttachedEmoji(reference, key.epoch)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w("MarmotDbg", "encryptMarmotCustomEmoji: could not attach :${emoji.code}: in ${nostrGroupId.take(8)}", e)
            null
        }
    }

    /**
     * [rumor] (a kind:9 or kind:7 Marmot payload) with every custom emoji in
     * [emojis] attached MDK's way: one `imeta` per picture, and an `emoji` tag
     * naming it by the reference's first locator. An emoji whose picture could
     * not be attached is left out, so its `:shortcode:` shows as text rather
     * than pointing receivers at a URL they must never fetch.
     *
     * Every picture is encrypted under one epoch, returned with the payload: send it pinned to
     * that epoch. A commit that lands while the pictures upload leaves them behind, so they are
     * attached again, once, under the new one.
     */
    suspend fun withEncryptedCustomEmoji(
        nostrGroupId: HexKey,
        rumor: Event,
        emojis: List<EmojiUrlTag>,
        /**
         * A cap on all of the event's tags, for kinds MDK bounds that way (a poll: MarmotPolls.MAX_TAGS).
         * Null applies a message's limits on its extra tags.
         */
        maxTotalTags: Int? = null,
        /** Called when at least one emoji could not be attached and goes out as text. */
        onEmojiNotAttached: () -> Unit = {},
    ): PinnedRumor {
        if (emojis.isEmpty()) return PinnedRumor(rumor, null)
        val distinct = emojis.distinctBy { it.code }
        val base = rumor.tags.filter { !(it.isNotEmpty() && it[0] == MarmotCustomEmoji.TAG_EMOJI) }
        // Only as many as the tag count can take, before any download or upload: a poll spends two
        // tags per emoji (imeta + emoji), a message's limit counts the emoji tags alone.
        val room = if (maxTotalTags != null) (maxTotalTags - base.size) / 2 else MarmotCustomEmoji.MAX_MESSAGE_TAGS - base.size
        val wanted = distinct.take(room.coerceAtLeast(0))

        // In parallel: each one is a download and an upload, and one slow host should not
        // hold the others back.
        suspend fun attachAll() =
            coroutineScope {
                wanted
                    .map { emoji -> async { encryptMarmotCustomEmoji(nostrGroupId, emoji)?.let { emoji.code to it } } }
                    .awaitAll()
                    .filterNotNull()
            }

        var attached = attachAll()
        if (attached.any { (_, it) -> it.epoch != currentMarmotEpoch(nostrGroupId) }) attached = attachAll()
        // Still split after a second try (commits in a row): keep the newest epoch's, the only
        // ones a send can still carry.
        val epoch = attached.maxOfOrNull { (_, it) -> it.epoch }
        val kept = attached.filter { (_, it) -> it.epoch == epoch }

        val imetas = kept.map { (_, it) -> it.reference.toImetaTag() }
        val emojiTags =
            kept.map { (code, it) ->
                arrayOf(
                    MarmotCustomEmoji.TAG_EMOJI,
                    code,
                    it.reference.locators
                        .first()
                        .value,
                )
            }
        // MDK refuses to SEND past its limits on a message's extra tags (the
        // imeta rows are counted apart), and so must we, or a peer that one day
        // enforces them on receive drops the message.
        val fits =
            if (maxTotalTags != null) {
                imetas.size + emojiTags.size + base.size <= maxTotalTags
            } else {
                MarmotCustomEmoji.withinTagLimits(emojiTags + base)
            }
        if (kept.size < distinct.size || (!fits && kept.isNotEmpty())) onEmojiNotAttached()
        val tags = if (fits) imetas + emojiTags + base else base
        val template = EventTemplate<Event>(rumor.createdAt, rumor.kind, tags.toTypedArray(), rumor.content)
        return PinnedRumor(RumorAssembler.assembleRumor(account.signer.pubKey, template), if (fits) epoch else null)
    }

    /**
     * React to a Marmot message, carrying a custom emoji as an encrypted
     * attachment of the reaction (MDK's `reactWithMedia`): `e` names the
     * target, then the `imeta`, then the `emoji` tag. Anything that is not a
     * custom emoji, or a custom emoji that cannot be attached, takes the
     * ordinary [reactToMarmotMessage] path.
     */
    suspend fun reactToMarmotMessageWithMedia(
        nostrGroupId: HexKey,
        target: Note,
        reaction: String,
        /** Called when the custom emoji could not be attached and the reaction goes out as text. */
        onEmojiNotAttached: () -> Unit = {},
    ) {
        val shortcode = MarmotCustomEmoji.reactionShortcode(reaction)
        val targetEvent = target.event
        if (shortcode == null || emojiMediaPort == null || targetEvent == null || account.marmotManager == null) {
            reactToMarmotMessage(nostrGroupId, target, reaction)
            return
        }
        if (target.reactionsOfAuthorLike(account.userProfile(), reaction).isNotEmpty()) return
        // The picture comes from the account's own packs, by shortcode. The URL in [reaction] may be
        // another member's ciphertext (a chip tapped) or any URL a peer wrote, and is never fetched.
        val emoji = account.emoji.findEmojiTags(shortcode).firstOrNull()
        var attached = emoji?.let { encryptMarmotCustomEmoji(nostrGroupId, it) }
        // Left behind by a commit during the upload: attach again under the new epoch, once.
        if (emoji != null && attached != null && attached.epoch != currentMarmotEpoch(nostrGroupId)) attached = encryptMarmotCustomEmoji(nostrGroupId, emoji)
        if (emoji == null || attached == null) {
            // As text: the bare shortcode carries no emoji tag, so no receiver fetches a public URL.
            onEmojiNotAttached()
            reactToMarmotMessage(nostrGroupId, target, shortcode)
            return
        }
        val tags =
            arrayOf(
                arrayOf("e", targetEvent.id),
                attached.reference.toImetaTag(),
                arrayOf(
                    MarmotCustomEmoji.TAG_EMOJI,
                    emoji.code,
                    attached.reference.locators
                        .first()
                        .value,
                ),
            )
        val template = EventTemplate<Event>(TimeUtils.now(), REACTION_KIND, tags, ":${emoji.code}:")
        val rumor = RumorAssembler.assembleRumor<Event>(account.signer.pubKey, template)
        sendMarmotGroupMessage(nostrGroupId, rumor, marmotGroupRelays(nostrGroupId), attached.epoch)
    }

    private fun currentMarmotEpoch(nostrGroupId: HexKey): Long? =
        account.marmotManager
            ?.groupManager
            ?.getGroup(nostrGroupId)
            ?.epoch

    /**
     * Build a Marmot poll (kind 1068) for [nostrGroupId], or null when the input
     * breaks MDK's profile ([MarmotPolls.pollTags]). Returned rather than sent,
     * so the caller shows it through the usual show-then-publish path.
     */
    suspend fun buildMarmotPoll(
        question: String,
        options: List<String>,
        type: MarmotPolls.PollType,
        endsAt: Long?,
        /** The accounts the question and options mention, `p`-tagged as a message tags them. */
        mentions: List<PTag> = emptyList(),
    ): Event? {
        val createdAt = TimeUtils.now()
        val tags = MarmotPolls.pollTags(createdAt, question, options, type, endsAt) ?: return null
        // MDK ignores tags a poll does not define, within MAX_TAGS in all. A mention past that
        // still reads as a link in the text; it just notifies no one.
        val room = (MarmotPolls.MAX_TAGS - tags.size).coerceAtLeast(0)
        val pTags = mentions.distinctBy { it.pubKey }.take(room).map { it.toTagArray() }
        val template = EventTemplate<Event>(createdAt, MarmotPolls.KIND_POLL, tags + pTags, question)
        return RumorAssembler.assembleRumor(account.signer.pubKey, template)
    }

    /**
     * Build a vote (kind 1018) on [poll] choosing [optionIds], or null when the
     * poll is closed, invalid, or the selection does not fit it. A vote is the
     * voter's complete selection, replacing any earlier one.
     */
    suspend fun buildMarmotPollVote(
        poll: Event,
        optionIds: List<String>,
    ): Event? {
        val definition = MarmotPolls.parsePoll(poll) ?: return null
        val createdAt = TimeUtils.now()
        if (!MarmotPolls.isValidResponse(definition, createdAt, optionIds)) return null
        val tags = MarmotPolls.responseTags(poll.id, optionIds) ?: return null
        val template = EventTemplate<Event>(createdAt, MarmotPolls.KIND_POLL_RESPONSE, tags, "")
        return RumorAssembler.assembleRumor(account.signer.pubKey, template)
    }

    /**
     * Report [target], a message in [nostrGroupId], to the group's members for [type] (a kind
     * 1984 inside the group, `features/content-moderation.md`). Every member sees the report
     * and who sent it. NIP-56 reasons MDK does not read go as "other", with the reason kept as
     * the explanation when there is none, so White Noise still counts the report.
     */
    suspend fun reportMarmotMessage(
        nostrGroupId: HexKey,
        target: Note,
        type: ReportType,
        explanation: String,
    ): Boolean {
        val manager = account.marmotManager ?: return false
        val targetEvent = target.event ?: return false
        val reason = MarmotReport.Reason.parse(type.code) ?: MarmotReport.Reason.OTHER
        val text = if (explanation.isBlank() && reason.code != type.code) type.code else explanation
        val rumor = manager.buildReportRumor(targetEvent, reason, text)
        sendMarmotGroupMessage(nostrGroupId, rumor, marmotGroupRelays(nostrGroupId))
        return true
    }

    /**
     * Dismiss [reportIds], reports in [nostrGroupId], as a group admin (a kind-1985 label inside
     * the group). False when this account is not an admin now or there is nothing to dismiss.
     */
    suspend fun dismissMarmotReports(
        nostrGroupId: HexKey,
        reportIds: List<HexKey>,
    ): Boolean {
        val manager = account.marmotManager ?: return false
        if (!manager.isGroupAdmin(nostrGroupId)) return false
        val rumor = manager.buildReportDismissalRumor(reportIds.take(MarmotReportDismissal.MAX_REPORTS), "") ?: return false
        sendMarmotGroupMessage(nostrGroupId, rumor, marmotGroupRelays(nostrGroupId))
        return true
    }

    /**
     * Vote for [optionIds] on [poll] in [nostrGroupId], replacing any earlier vote. The options go
     * out in the poll's own order, so the same choice is always the same tags. False when the
     * poll is closed or the selection does not fit it.
     */
    suspend fun respondToMarmotPoll(
        nostrGroupId: HexKey,
        poll: Event,
        optionIds: Set<String>,
    ): Boolean {
        val ordered =
            MarmotPolls
                .parsePoll(poll)
                ?.options
                ?.map { it.id }
                ?.filter { it in optionIds } ?: return false
        val vote = buildMarmotPollVote(poll, ordered) ?: return false
        sendMarmotGroupMessage(nostrGroupId, vote, marmotGroupRelays(nostrGroupId))
        return true
    }

    private fun mediaTypeFromUrl(url: String): String? =
        when (url.substringBefore('?').substringAfterLast('.', "").lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "avif" -> "image/avif"
            "svg" -> "image/svg+xml"
            else -> null
        }

    private fun emojiFilename(
        code: String,
        mediaType: String,
    ): String {
        val ext =
            when (mediaType) {
                "image/jpeg" -> "jpg"
                "image/svg+xml" -> "svg"
                else -> mediaType.substringAfter('/').substringBefore('+')
            }
        val safe = code.filter { it.isLetterOrDigit() || it == '_' || it == '-' }.ifEmpty { "emoji" }.take(200)
        return "$safe.$ext"
    }

    companion object {
        /**
         * [target], when an authorized admin removal in [nostrGroupId] may drop it from the cache:
         * only a chat message of that same group. Null for anything else, a message of another
         * group or a public note included.
         */
        fun groupMessageToRemove(
            groups: MarmotGroupList,
            nostrGroupId: HexKey,
            target: Note?,
        ): Note? = target?.takeIf { it.event?.kind == ChatEvent.KIND && groups.isInGroup(nostrGroupId, it) }

        private const val REACTION_KIND = 7

        /** How often the leaf-refresh loop asks whether a delayed refresh is due: 30 minutes. */
        private const val LEAF_REFRESH_CHECK_INTERVAL_MS = 30L * 60 * 1000

        // Retries of an unconfirmed KeyPackage publish: after 30 s, 2 min, 8 min and 32 min.
        private const val KEY_PACKAGE_RETRY_FIRST_MS = 30_000L
        private const val KEY_PACKAGE_RETRIES = 4

        fun indexMarmotInnerEvent(
            cache: EventCache,
            innerEvent: Event,
        ): IndexedInnerEvent {
            val isEdit = innerEvent.kind == MarmotAppEvent.KIND_EDIT
            val innerNote = cache.getOrCreateNote(innerEvent.id)
            // A message deleted on an earlier pass stays deleted. Relays replay its kind:445 after
            // a restart, the cache refuses the event, and loading it below anyway brought it back.
            if (innerNote.event == null && cache.deletionIndex.hasBeenDeleted(innerEvent)) {
                return IndexedInnerEvent(innerNote, false)
            }
            // wasVerified=true: MIP-03 inner events are unsigned rumors; MLS authenticated the sender.
            // For an edit, "new" is whether its note was empty — which also decides whether it is
            // persisted, so an edit is kept in the local log and survives a restart.
            val isNew = if (isEdit) innerNote.event == null else cache.justConsume(innerEvent, null, true)
            if (isNew || innerNote.event == null) {
                // loadEvent, not a bare `event =`: the overlay also matches the edit's AUTHOR to the
                // message's, and only loadEvent sets it.
                innerNote.loadEvent(innerEvent, cache.getOrCreateUser(innerEvent.pubKey), emptyList())
            }

            // The overlay's rules (author-only, latest wins) are applied at render time by
            // `Note.latestMarmotEdit`: the target's author may not be known yet.
            if (isEdit) {
                MarmotMessageEdit.fromAppEvent(MarmotAppEvent.fromEvent(innerEvent))?.let { edit ->
                    cache.getOrCreateNote(edit.targetId).addEdit(innerNote)
                }
            }
            return IndexedInnerEvent(innerNote, isNew)
        }
    }
}

/** A custom emoji's picture attached to a group message, and the epoch whose media key encrypted it. */
class AttachedEmoji(
    val reference: EncryptedMediaReferenceV2,
    val epoch: Long,
)

/** A Marmot payload ready to send, and the epoch its attachments were encrypted in; null when it has none. */
class PinnedRumor(
    val rumor: Event,
    val epoch: Long?,
)

/** Pack pictures kept in memory for re-sending, in bytes. */
private const val EMOJI_IMAGE_CACHE_BYTES = 4 * 1024 * 1024
