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

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.Channel
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.rooms.MessageRoom
import com.vitorpamplona.amethyst.commons.notices.UserNoticeSink
import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityState
import com.vitorpamplona.quartz.concord.cord02Community.ImagePointer
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordChannelId
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.paging.RelayLoadingCursors
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

/**
 * A Concord channel — one encrypted chat room inside a community — as a
 * [Channel] the shared chat UI can render, mirroring NIP-29's
 * [com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel].
 *
 * The key difference: a Concord channel has no single relay-signed metadata
 * event. Its name/flags come from the community's **folded Control Plane**, its
 * decrypted messages are fed in by a subscription that holds the channel key, and
 * it is addressed by the derived plane pubkey rather than a host relay — so
 * [relays] is the *community's* relay set (a channel may be mirrored on several),
 * not a single host. Membership derives from the owner-rooted authority resolver.
 */
@Stable
class ConcordChannel(
    val channelId: ConcordChannelId,
) : Channel(),
    MessageRoom {
    /** Reactions and deletions go on the channel's own plane (CORD-01), sealed to the community. */
    override fun isReadBy(account: Account): Boolean = account.concordSessions.sessionFor(channelId.communityId) != null

    override suspend fun react(
        account: Account,
        target: Note,
        reaction: String,
        notices: UserNoticeSink,
    ): Boolean = account.concord.reactToConcordMessage(target, reaction)

    override suspend fun delete(
        account: Account,
        notes: List<Note>,
    ): Boolean = account.concord.deleteConcordRumors(this, notes)

    override fun canEdit(
        account: Account,
        note: Note,
    ): Boolean = note.event?.let { account.concord.isConcordEditable(it) } == true

    /** Channel display name from the folded ChannelMetadata, when known. */
    var channelName: String? = null
        private set

    var isPrivate: Boolean = false
        private set

    /**
     * False for a Private Channel whose independent key this account does not hold (CORD-03 §1):
     * its plane can be neither read nor written, and it must never fall back to the root-derived
     * plane every member can decrypt. Always true for a Public Channel.
     */
    var keyHeld: Boolean = true
        private set

    /** The parent community's display name, from its folded metadata. */
    var communityName: String? = null
        private set

    /** The parent community's encrypted-media icon pointer, from its folded metadata (null if unset). */
    var communityIcon: ImagePointer? = null
        private set

    /** The parent community's encrypted-media banner pointer, from its folded metadata (null if unset). */
    var communityBanner: ImagePointer? = null
        private set

    /** The community's bootstrap relays — a channel plane may be mirrored on all of them. */
    var communityRelays: Set<NormalizedRelayUrl> = emptySet()
        private set

    /** This account's standing in the community (from the authority resolver + banlist). */
    var membership: ConcordMembership = ConcordMembership.MEMBER
        private set

    /**
     * True once the community has been dissolved by an owner-signed tombstone (CORD-02 §9). On sight
     * the client seals the Community read-only: held keys still open history, but nothing new is
     * honored, so no member — not even the owner — may post. Terminal and one-way (there is no
     * un-dissolve). The one carve-out (a member's delete of their own past message stays honored even
     * post-seal) runs through the note context menu, not the composer, so this gate never blocks it.
     */
    var dissolved: Boolean = false
        private set

    /**
     * Per-relay backward-pagination cursors for this channel's history (CORD-03). The live
     * subscription only holds the recent tail the relay serves for the channel plane; older
     * messages are paged in on demand by `until`+`limit` as the user scrolls, exactly like the
     * NIP-04 per-conversation history. Held here so the cursors share the channel's cache lifetime.
     */
    val history = RelayLoadingCursors()

    /**
     * Refresh this channel's metadata from a freshly-folded community [state] plus
     * the community's [relays] and this account's [myPubKey]. Cheap and idempotent
     * — called whenever the Control Plane re-folds.
     *
     * Returns true when a displayed field (channel name, community name/icon,
     * membership) actually changed, so the caller can invalidate the channel's
     * metadata flow ([updateChannelInfo]) — and thus recompose the Messages-row
     * name + community chip — only on a real change, not on every fold tick.
     */
    fun updateFrom(
        state: ConcordCommunityState,
        relays: Set<NormalizedRelayUrl>,
        myPubKey: HexKey,
        keyHeld: Boolean = true,
    ): Boolean {
        val def = state.channels[channelId.channelId]?.definition
        // Channel fields keep their prior value until the channel edition folds.
        val newChannelName = def?.name ?: channelName
        val newPrivate = def?.private ?: isPrivate
        val newCommunityName = state.metadata?.name
        val newCommunityIcon = state.metadata?.icon
        val newCommunityBanner = state.metadata?.banner
        val newMembership = ConcordMembership.of(state.authority, myPubKey)
        val newDissolved = state.dissolved
        val newKeyHeld = !newPrivate || keyHeld

        val changed =
            channelName != newChannelName ||
                isPrivate != newPrivate ||
                communityName != newCommunityName ||
                communityIcon != newCommunityIcon ||
                communityBanner != newCommunityBanner ||
                membership != newMembership ||
                dissolved != newDissolved ||
                this.keyHeld != newKeyHeld

        channelName = newChannelName
        isPrivate = newPrivate
        communityName = newCommunityName
        communityIcon = newCommunityIcon
        communityBanner = newCommunityBanner
        communityRelays = relays
        membership = newMembership
        dissolved = newDissolved
        this.keyHeld = newKeyHeld
        return changed
    }

    /** A Concord channel is reachable on any of its community's relays. */
    override fun relays(): Set<NormalizedRelayUrl> = communityRelays

    override fun toBestDisplayName(): String = channelName ?: channelId.channelId

    /**
     * Whether this account may post to the channel: it must hold a live standing in the community
     * ([ConcordMembership.isMember]) **and** the community must not have been dissolved (CORD-02 §9 —
     * a tombstone seals it read-only for everyone). Deleting one's own past message stays allowed even
     * after dissolution and does not go through this gate.
     *
     * A Private Channel whose key this account does not hold is never postable ([keyHeld]): there
     * is no plane to write to that only its members can read.
     */
    fun canPost(): Boolean = membership.isMember() && !dissolved && keyHeld

    // Synthetic note representing this channel in the Messages list before any
    // message has loaded (so a just-joined channel appears immediately). Mirrors
    // RelayGroupChannel.placeholderNote().
    private val placeholderLock = KmpLock()
    private var cachedPlaceholder: Note? = null

    fun placeholderNote(): Note =
        placeholderLock.withLock {
            cachedPlaceholder ?: Note(placeholderIdHex(channelId)).apply {
                addGatherer(this@ConcordChannel)
                cachedPlaceholder = this
            }
        }

    companion object {
        fun placeholderIdHex(channelId: ConcordChannelId): HexKey = "concord-empty-${channelId.toKey()}"
    }
}
