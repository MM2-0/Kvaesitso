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
package com.vitorpamplona.amethyst.commons.model.marmotGroups

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.Channel.Companion.DefaultFeedOrder
import com.vitorpamplona.amethyst.commons.model.ListChange
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.nip88Polls.PollTallyPolicy
import com.vitorpamplona.amethyst.commons.model.rooms.MessageRoom
import com.vitorpamplona.amethyst.commons.model.rooms.roomFor
import com.vitorpamplona.amethyst.commons.notices.MarmotNotice
import com.vitorpamplona.amethyst.commons.notices.UserNoticeSink
import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.WeakReference
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.marmot.appComponents.GroupAvatarUrlV1
import com.vitorpamplona.marmot.client.GroupMemberInfo
import com.vitorpamplona.marmot.client.MarmotGroupImage
import com.vitorpamplona.marmot.foundation.appEvents.MarmotCustomEmoji
import com.vitorpamplona.marmot.foundation.appEvents.MarmotPolls
import com.vitorpamplona.marmot.protocolCore.LocalOutboundGate
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip56Reports.ReportEvent
import com.vitorpamplona.quartz.nip56Reports.ReportType
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Represents a Marmot MLS group chat room.
 * Tracks decrypted inner messages for a single group.
 * Follows the same pattern as [com.vitorpamplona.amethyst.commons.model.privateChats.Chatroom].
 *
 * As a [MessageRoom] it is where everything about its messages is written: reactions, deletions,
 * poll votes and admin removals all go into the group as MLS application messages.
 */
@Stable
class MarmotGroupChatroom(
    val nostrGroupId: HexKey,
) : MessageRoom {
    var messages: Set<Note> = setOf()
    var displayName = MutableStateFlow<String?>(null)
    var description = MutableStateFlow<String?>(null)

    /**
     * The group's encrypted avatar image parameters (Blossom hash + decryption key/nonce),
     * or null when the group has no image set. Front ends fetch the blob by hash and decrypt
     * it; when null they fall back to the host relay's NIP-11 icon.
     */
    var image = MutableStateFlow<MarmotGroupImage?>(null)

    /**
     * The group's plain-https avatar (`marmot.group.avatar-url.v1`), or null
     * when it has none. It takes precedence over [image]: a group carrying
     * both shows this one, and only falls back to the encrypted Blossom blob
     * once this is cleared.
     */
    var avatarUrl = MutableStateFlow<GroupAvatarUrlV1?>(null)

    /**
     * False for a legacy MIP-01 group — one that predates the current profile
     * and requires `0xf2f1` instead of the `0x8009` account identity proof.
     *
     * Front ends need this because a legacy group has nowhere to PUT several
     * components: lifecycle (`0x800c`, so no disband), the URL avatar
     * (`0x8007`), and the encrypted-media policy (`0x800b`) are all
     * GroupContext state a legacy group never carried. Offering those actions
     * on one produces a commit that is refused before it is built, so the
     * honest thing is not to offer them.
     *
     * Nor can that be fixed by upgrading the group: the identity proof lives in
     * each member's own LeafNode and covers that leaf's signature key, so it
     * cannot be added to leaves that already exist. See `AccountIdentityProofV2`
     * — "There is no fallback and no in-place migration". A legacy room stays a
     * legacy room; a new group is the only route.
     *
     * Defaults to TRUE so a group that has just been created shows its full
     * feature set immediately: creation does not run a metadata sync, and every
     * group created now is a current-profile one. A restored legacy group is
     * corrected by the startup sync before any screen reads this.
     */
    var isCurrentProfile = MutableStateFlow(true)

    /** This device can no longer read what the other members send (see MarmotDesyncDetector). */
    val isOutOfSync = MutableStateFlow(false)

    /** This device dropped its copy of the group and waits for an admin to add it back. */
    val awaitingReinvite = MutableStateFlow(false)

    /**
     * True once the group carries the `encrypted-media-v2` policy (`0x800b`).
     *
     * Attachments fall back to MIP-04 without it, so a front end needs this to
     * tell an admin the group can be upgraded — and to stop offering the
     * upgrade once it has been.
     */
    var hasEncryptedMediaPolicy = MutableStateFlow(false)

    /**
     * Why this group takes no new outbound work, or null when it does.
     *
     * A durable outbound gate is not a lifecycle state: the member is still in
     * the tree and the group is not terminal, but nothing new may be sent —
     * an unresolved disband request, a SelfRemove already sent, a realized
     * removal. A front end needs it separately from [isCurrentProfile] and the
     * lifecycle so it can DISABLE the composer with a reason rather than let a
     * send throw and surface as an error after the fact.
     */
    var outboundGate = MutableStateFlow<LocalOutboundGate?>(null)

    var adminPubkeys = MutableStateFlow<List<HexKey>>(emptyList())
    var relays = MutableStateFlow<List<String>>(emptyList())
    var memberCount = MutableStateFlow(0)
    var members = MutableStateFlow<List<GroupMemberInfo>>(emptyList())
    var newestMessage: Note? = null

    /**
     * Tracks the most recent createdAt (seconds) of a kind:445 group event
     * observed from each relay, keyed by the relay that delivered it. Used by
     * the Group Info screen to flag which configured relays are actively
     * carrying traffic for this MLS group.
     */
    val relayActivity = MutableStateFlow<Map<NormalizedRelayUrl, Long>>(emptyMap())

    /**
     * True if the local user has ever sent an application message in this
     * group, OR explicitly created/owns it. Used by list UIs to split groups
     * into "Known" vs "New Requests" — invitees that have not yet replied
     * stay in "New Requests" until they participate.
     */
    var ownerSentMessage: Boolean = false

    /**
     * Classifies this group for the Known/New Requests split.
     *
     * Rules:
     * - If the local user has already participated ([ownerSentMessage]), Known.
     * - If the group has no known members and no messages yet, New Requests
     *   (we don't know who invited us, so it's untrusted by default).
     * - Otherwise, Known iff at least one admin is in the follow set.
     */
    fun isKnown(followingKeySet: Set<HexKey>): Boolean {
        if (ownerSentMessage) return true
        if (memberCount.value == 0 && messages.isEmpty()) return false
        return adminPubkeys.value.any { it in followingKeySet }
    }

    // Per-instance lock shared by previously @Synchronized methods.
    private val syncLock = KmpLock()

    // Synthetic note used by list views to represent the group when no
    // messages have been received yet. Lazily created and kept stable so
    // equality-based feed diffing treats it as the same row across refreshes.
    private var cachedPlaceholder: Note? = null

    fun placeholderNote(): Note =
        syncLock.withLock {
            val existing = cachedPlaceholder
            if (existing != null) return@withLock existing
            val created =
                Note(placeholderIdHex(nostrGroupId)).apply {
                    addGatherer(this@MarmotGroupChatroom)
                }
            cachedPlaceholder = created
            created
        }

    companion object {
        fun placeholderIdHex(nostrGroupId: HexKey): HexKey = "marmot-empty-$nostrGroupId"
    }

    private var changesFlow: WeakReference<MutableSharedFlow<ListChange<Note>>>? = null

    fun changesFlow(): MutableSharedFlow<ListChange<Note>> {
        val current = changesFlow?.get()
        if (current != null) return current
        val new = MutableSharedFlow<ListChange<Note>>(0, 100, BufferOverflow.DROP_OLDEST)
        changesFlow = WeakReference(new)
        return new
    }

    override fun removeNote(note: Note) {
        removeMessageSync(note)
    }

    override fun isReadBy(account: Account): Boolean = account.marmotGroupList.rooms.get(nostrGroupId) === this

    override suspend fun react(
        account: Account,
        target: Note,
        reaction: String,
        notices: UserNoticeSink,
    ): Boolean {
        account.marmot.reactToMarmotMessageWithMedia(nostrGroupId, target, reaction) { notices.notify(MarmotNotice.EmojiNotAttached) }
        return true
    }

    override suspend fun delete(
        account: Account,
        notes: List<Note>,
    ): Boolean = account.marmot.deleteMarmotMessages(nostrGroupId, notes)

    // A media message's content is its locator, so editing it would break the attachment. Custom
    // emoji pictures are attachments too, but artwork for the text, which an edit re-sends.
    override fun canEdit(
        account: Account,
        note: Note,
    ): Boolean {
        val event = note.event as? ChatEvent ?: return false
        if (note.author != account.userProfile()) return false
        return event.tags.none { it.isNotEmpty() && it[0] == "imeta" } || MarmotCustomEmoji.hasOnlyEmojiAttachments(event)
    }

    override fun acceptsPoll(poll: PollEvent): Boolean = MarmotPolls.parsePoll(poll) != null

    // MDK lets the author vote in their own poll.
    override fun pollAuthorMayVote(): Boolean = true

    override fun pollTallyPolicy(poll: PollEvent): PollTallyPolicy = PollTallyPolicy.from(poll).copy(rule = MarmotPollResponseRule.of(poll))

    override suspend fun respondToPoll(
        account: Account,
        poll: Note,
        optionCodes: Set<String>,
    ): Boolean {
        val pollEvent = poll.event ?: return false
        return account.marmot.respondToMarmotPoll(nostrGroupId, pollEvent, optionCodes)
    }

    // `features/content-moderation.md`: a report is a kind 1984 every member sees.
    override fun keepsReports(): Boolean = true

    override suspend fun report(
        account: Account,
        note: Note,
        type: ReportType,
        explanation: String,
    ): Boolean = account.marmot.reportMarmotMessage(nostrGroupId, note, type, explanation)

    // Only reports that came through this group: a report is a rumor here, and a dismissed one
    // has already been dropped from the message's reports.
    override fun reportsToDismiss(
        account: Account,
        note: Note,
    ): List<Note> {
        if (account.marmotManager?.isGroupAdmin(nostrGroupId) != true) return emptyList()
        return note.reports.values
            .flatten()
            .filter { it.isPrivateRumor() && it.event is ReportEvent }
    }

    override suspend fun dismissReports(
        account: Account,
        reports: List<Note>,
    ): Boolean = account.marmot.dismissMarmotReports(nostrGroupId, reports.map { it.idHex })

    override fun canRemoveForEveryone(
        account: Account,
        note: Note,
    ): Boolean = account.marmot.canAdminDeleteMarmotMessage(nostrGroupId, note)

    override suspend fun removeForEveryone(
        account: Account,
        note: Note,
    ): Boolean = account.marmot.adminDeleteMarmotMessage(nostrGroupId, note)

    fun addMessageSync(msg: Note): Boolean =
        syncLock.withLock {
            if (msg !in messages) {
                messages = messages + msg
                msg.addGatherer(this)
                // The group counts its polls' votes by its own rules, whatever the cache derived.
                (msg.event as? PollEvent)?.let { msg.pollState().updatePolicy(pollTallyPolicy(it)) }

                val createdAt = msg.createdAt() ?: 0L
                if (createdAt > (newestMessage?.createdAt() ?: 0L)) {
                    newestMessage = msg
                }

                changesFlow?.get()?.tryEmit(ListChange.Addition(msg))
                return@withLock true
            }
            return@withLock false
        }

    fun removeMessageSync(msg: Note): Boolean =
        syncLock.withLock {
            if (msg in messages) {
                messages = messages - msg
                msg.removeGatherer(this)

                if (msg == newestMessage) {
                    newestMessage = messages.maxByOrNull { it.createdAt() ?: 0L }
                }

                changesFlow?.get()?.tryEmit(ListChange.Deletion(msg))
                return@withLock true
            }
            return@withLock false
        }

    fun recordRelayActivity(
        relay: NormalizedRelayUrl,
        createdAt: Long,
    ) {
        relayActivity.update { current ->
            val existing = current[relay] ?: 0L
            if (createdAt > existing) current + (relay to createdAt) else current
        }
    }

    fun pruneMessagesToTheLatestOnly(): Set<Note> {
        val sorted = messages.sortedWith(DefaultFeedOrder)
        val toKeep =
            sorted.take(100).toSet() +
                sorted.filter { it.flowSet?.isInUse() ?: false }

        val toRemove = messages.minus(toKeep)
        messages = toKeep

        changesFlow?.get()?.tryEmit(ListChange.SetDeletion<Note>(toRemove))
        return toRemove
    }

    /**
     * Drop every message from this chatroom. Used when the local user leaves
     * the group: cuts the strong references held in [messages] so the
     * decrypted inner notes become eligible for GC out of LocalCache (which
     * holds them weakly).
     */
    fun clearAllMessagesSync(): Set<Note> =
        syncLock.withLock {
            val toRemove = messages
            if (toRemove.isEmpty()) return@withLock toRemove
            messages = emptySet()
            newestMessage = null
            changesFlow?.get()?.tryEmit(ListChange.SetDeletion<Note>(toRemove))
            toRemove
        }
}

/** The Marmot group whose room this note is in for [account], or null outside every Marmot group. */
fun Note.marmotGroupIdFor(account: Account): HexKey? = (roomFor(account) as? MarmotGroupChatroom)?.nostrGroupId
