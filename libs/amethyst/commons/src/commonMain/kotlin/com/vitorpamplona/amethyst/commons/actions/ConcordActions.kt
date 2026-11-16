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
package com.vitorpamplona.amethyst.commons.actions

import com.vitorpamplona.amethyst.commons.model.ConcordDirectInviteDraft
import com.vitorpamplona.amethyst.commons.model.ConcordDirectInviteSendResult
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.marmot.RecipientRelayFetcher
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityFactory
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEntry
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityState
import com.vitorpamplona.quartz.concord.cord02Community.Guestbook
import com.vitorpamplona.quartz.concord.cord02Community.GuestbookAction
import com.vitorpamplona.quartz.concord.cord02Community.GuestbookEntry
import com.vitorpamplona.quartz.concord.cord02Community.HeldRoot
import com.vitorpamplona.quartz.concord.cord02Community.ImagePointer
import com.vitorpamplona.quartz.concord.cord02Community.NewConcordCommunity
import com.vitorpamplona.quartz.concord.cord02Community.PrivateChannelKey
import com.vitorpamplona.quartz.concord.cord03Channels.ChannelChat
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordChannelKeyring
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordChannelKeys
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordDisappearing
import com.vitorpamplona.quartz.concord.cord04Roles.AuthorityCitation
import com.vitorpamplona.quartz.concord.cord04Roles.AuthorityResolver
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEdition
import com.vitorpamplona.quartz.concord.cord05Invites.CommunityInvite
import com.vitorpamplona.quartz.concord.cord05Invites.ConcordDirectInvite
import com.vitorpamplona.quartz.concord.cord05Invites.ConcordInviteBundle
import com.vitorpamplona.quartz.concord.cord05Invites.ConcordInviteLink
import com.vitorpamplona.quartz.concord.cord05Invites.ConcordInviteVend
import com.vitorpamplona.quartz.concord.cord05Invites.ConcordStrandedRecovery
import com.vitorpamplona.quartz.concord.cord05Invites.InviteBundleStatus
import com.vitorpamplona.quartz.concord.cord05Invites.InviteRelayDictionary
import com.vitorpamplona.quartz.concord.cord05Invites.MintedInviteLink
import com.vitorpamplona.quartz.concord.cord05Invites.OpenedDirectInvite
import com.vitorpamplona.quartz.concord.cord05Invites.ParsedInviteLink
import com.vitorpamplona.quartz.concord.cord05Invites.bundle.ConcordInviteBundleEvent
import com.vitorpamplona.quartz.concord.cord06Rekey.ConcordRefounding
import com.vitorpamplona.quartz.concord.cord06Rekey.ReceivedRefounding
import com.vitorpamplona.quartz.concord.cord06Rekey.RefoundingBuild
import com.vitorpamplona.quartz.concord.crypto.ConcordKeyDerivation
import com.vitorpamplona.quartz.concord.crypto.ControlPlaneKeys
import com.vitorpamplona.quartz.concord.crypto.GroupKey
import com.vitorpamplona.quartz.concord.envelope.ConcordStreamEnvelope
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.nip92IMeta.IMetaTag
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent
import com.vitorpamplona.quartz.utils.TimeUtils

/** One decrypted, verified Concord channel message projected for display. */
data class ConcordChatMessage(
    val id: HexKey,
    val author: HexKey,
    val content: String,
    val createdAt: Long,
    val channelId: HexKey,
    val epoch: Long,
)

/**
 * One channel's Chat Plane: the epoch-invariant [channelIdHex], the [epoch] its rumors are bound to
 * (for `isBoundTo` validation — the root epoch for a Public Channel, the channel's own epoch for a
 * Private one, CORD-03 §1), and the derived [key] its wraps are addressed by and decrypt under.
 */
data class ChannelPlane(
    val channelIdHex: HexKey,
    val epoch: Long,
    val key: GroupKey,
)

/** A [ChannelPlane] at a prior epoch (pre-Refounding history). */
typealias HistoricalChannelPlane = ChannelPlane

/**
 * Concord community verbs — pure builders, plane-key derivation, relay-filter
 * assembly, and event folding usable from amy CLI, the Android app, and any other
 * non-UI consumer.
 *
 * Like [DmActions], this object never touches the network: create/send builders
 * return events to publish, the read side takes already-fetched wraps and folds
 * them. The caller (amy `Context`, an Android ViewModel) owns publish/drain and
 * persistence of the community's secrets.
 */
object ConcordActions {
    // ---- plane key derivation -------------------------------------------------

    /**
     * The **legacy** Control Plane group key (pre-split epochs, CORD-06 §3), which
     * doubles as the split epochs' *read* key derivation. For anything that opens
     * or writes the Control Plane, prefer [controlPlaneKeys].
     */
    fun controlPlane(
        communityRoot: ByteArray,
        communityId: ByteArray,
        rootEpoch: Long,
    ): GroupKey = ConcordKeyDerivation.controlPlaneKey(communityRoot, communityId, rootEpoch)

    /**
     * The Control Plane keys as this account holds them (CORD-02 §5): staff when
     * [controlRoot] is held, read-only member when only [controlPk] is, and the
     * legacy single-key plane when neither (a pre-split epoch).
     */
    fun controlPlaneKeys(
        communityRoot: ByteArray,
        communityId: ByteArray,
        rootEpoch: Long,
        controlPk: HexKey? = null,
        controlRoot: HexKey? = null,
    ): ControlPlaneKeys = ControlPlaneKeys.of(communityRoot, communityId, rootEpoch, controlPk, controlRoot)

    /** The Control Plane keys described by a joined-list [entry]. */
    fun controlPlaneKeysFor(entry: ConcordCommunityListEntry): ControlPlaneKeys =
        controlPlaneKeys(
            entry.root.hexToByteArray(),
            entry.id.hexToByteArray(),
            entry.rootEpoch,
            entry.controlPk,
            entry.controlRoot,
        )

    /**
     * The Control Plane's stream address for a subscription: the held `control_pk`
     * on a split epoch, else the legacy derived address. Cheaper than
     * [controlPlaneKeys] when only the address is needed.
     */
    fun controlPlaneAddress(
        communityRoot: ByteArray,
        communityId: ByteArray,
        rootEpoch: Long,
        controlPk: HexKey?,
    ): HexKey = controlPk?.lowercase() ?: controlPlane(communityRoot, communityId, rootEpoch).publicKeyHex

    fun publicChannel(
        communityRoot: ByteArray,
        channelId: ByteArray,
        rootEpoch: Long,
    ): GroupKey = ConcordChannelKeys.publicChannel(communityRoot, channelId, rootEpoch)

    private val HEX64 = Regex("^[0-9a-fA-F]{64}$")

    /**
     * The independent key this account holds for Private Channel [channelIdHex] (delivered on grant
     * and carried in the Community List's `privateChannels`, CORD-03 §1 / CORD-02 §8), or null when
     * it holds none. A keyless entry (a writer listing a public channel as `{id, epoch}`) is not a key.
     */
    fun heldPrivateChannelKey(
        entry: ConcordCommunityListEntry,
        channelIdHex: HexKey,
    ): PrivateChannelKey? = entry.privateChannels.firstOrNull { it.channelId.equals(channelIdHex, ignoreCase = true) && HEX64.matches(it.key) }

    /**
     * The Chat Plane a channel is **written** on, or null when this account cannot write it
     * (CORD-03 §1):
     *  - Public: `group_key("concord/channel", community_root, channel_id, root_epoch)`, bound to
     *    the root epoch;
     *  - Private: `group_key("concord/channel", channel_key, channel_id, channel_epoch)` from the
     *    held key, bound to the **channel** epoch — and null when no key is held. A Private Channel
     *    must never fall back to the root-derived plane: every member decrypts that one, so a post
     *    there would be public to the whole community under a Lock icon.
     */
    fun currentChannelPlane(
        entry: ConcordCommunityListEntry,
        channelIdHex: HexKey,
        isPrivate: Boolean,
    ): ChannelPlane? {
        val channelId = channelIdHex.hexToByteArray()
        if (isPrivate) {
            val held = heldPrivateChannelKey(entry, channelIdHex) ?: return null
            return ChannelPlane(channelIdHex, held.epoch, ConcordChannelKeys.privateChannel(held.key.hexToByteArray(), channelId, held.epoch))
        }
        return ChannelPlane(channelIdHex, entry.rootEpoch, publicChannel(entry.root.hexToByteArray(), channelId, entry.rootEpoch))
    }

    /**
     * [currentChannelPlane] for a channel of the folded [state], or null when the channel is not in
     * the fold (unknown or deleted) or is Private with no held key.
     */
    fun currentChannelPlane(
        entry: ConcordCommunityListEntry,
        state: ConcordCommunityState,
        channelIdHex: HexKey,
    ): ChannelPlane? {
        val def = state.channels[channelIdHex]?.definition ?: return null
        return currentChannelPlane(entry, channelIdHex, def.private)
    }

    /**
     * The older Chat Planes of a channel this account can still read, beside [currentChannelPlane]:
     *  - Public: its plane under every held prior root ([historicalChannelPlanes]), plus every
     *    private-era plane a channel key is held for (a channel that was Private before);
     *  - Private: the planes of the older channel keys the entry still carries (its `seed` and a
     *    peer's `priors`, [ConcordChannelKeyring.historicalKeys]) — history across a channel rekey.
     *    Never the root-derived plane: every member reads that one, so showing it would present
     *    public content as private (Armada `channelsView`).
     */
    fun historicalChannelPlanes(
        entry: ConcordCommunityListEntry,
        channelIdHex: HexKey,
        isPrivate: Boolean,
    ): List<ChannelPlane> {
        val channelId = channelIdHex.hexToByteArray()
        val olderKeys =
            ConcordChannelKeyring.historicalKeys(entry, channelIdHex).map { old ->
                ChannelPlane(channelIdHex, old.epoch, ConcordChannelKeys.privateChannel(old.key.hexToByteArray(), channelId, old.epoch))
            }
        if (isPrivate) return olderKeys
        val rootEras = historicalChannelPlanes(entry.heldRoots, listOf(channelIdHex))
        val privateEra =
            heldPrivateChannelKey(entry, channelIdHex)?.let { held ->
                ChannelPlane(channelIdHex, held.epoch, ConcordChannelKeys.privateChannel(held.key.hexToByteArray(), channelId, held.epoch))
            }
        return rootEras + listOfNotNull(privateEra) + olderKeys
    }

    /**
     * The Private Channel keys an [invite] delivers (CORD-05 §1), as Community List entries. A
     * keyless listing (a public channel written as `{id, epoch}`) delivers nothing.
     */
    fun privateChannelKeysOf(invite: CommunityInvite): List<PrivateChannelKey> =
        invite.channels
            .filter { HEX64.matches(it.id) && HEX64.matches(it.key) }
            .map { PrivateChannelKey(it.id.lowercase(), it.key.lowercase(), it.epoch, it.name) }

    /** True when this account can read and write [channelIdHex] as folded in [state]. */
    fun canAccessChannel(
        entry: ConcordCommunityListEntry,
        state: ConcordCommunityState,
        channelIdHex: HexKey,
    ): Boolean = currentChannelPlane(entry, state, channelIdHex) != null

    /**
     * How many prior epochs of channel history to backfill. A CORD-06 Refounding rotates the
     * `community_root` and bumps the epoch, so pre-refounding messages live under a *different*
     * derived Chat Plane per epoch; the client keeps each rotated-out root in
     * [ConcordCommunityListEntry.heldRoots]. We re-derive those planes to read the older history
     * instead of stopping at the current epoch. Bounded because each covered epoch multiplies the
     * subscription + NIP-42 AUTH footprint by (channels); refoundings are rare, so a handful covers
     * every real community. Set to 0 to disable historical backfill entirely.
     */
    const val MAX_BACKFILL_EPOCHS = 8

    /**
     * The historical Chat Plane keys for [channelIdsHex] across the prior epochs in [heldRoots]
     * (newest-held first, bounded to [MAX_BACKFILL_EPOCHS]). The channel id is epoch-invariant, so a
     * message decrypted under a held root lands in the same channel as the current-epoch ones.
     */
    fun historicalChannelPlanes(
        heldRoots: List<HeldRoot>,
        channelIdsHex: Collection<HexKey>,
    ): List<HistoricalChannelPlane> =
        heldRoots
            .sortedByDescending { it.epoch }
            .take(MAX_BACKFILL_EPOCHS)
            .flatMap { held ->
                val rootBytes = held.key.hexToByteArray()
                channelIdsHex.map { channelIdHex ->
                    HistoricalChannelPlane(channelIdHex, held.epoch, publicChannel(rootBytes, channelIdHex.hexToByteArray(), held.epoch))
                }
            }

    /** The Guestbook Plane address for a community at [rootEpoch] — where join/leave motions ride. */
    fun guestbookPlane(
        communityRoot: ByteArray,
        communityId: ByteArray,
        rootEpoch: Long,
    ): GroupKey = ConcordKeyDerivation.guestbookPlaneKey(communityRoot, communityId, rootEpoch)

    /**
     * The base-rotation rekey address a member watches to receive the *next* epoch's
     * Refounding (CORD-06 §2): `base-rekey-pseudonym(current_root, community_id,
     * rootEpoch + 1)`. Precomputed from the root the member already holds.
     */
    fun nextBaseRekeyPlane(
        communityRoot: ByteArray,
        communityId: ByteArray,
        rootEpoch: Long,
    ): GroupKey = ConcordKeyDerivation.baseRekeyAddress(communityRoot, communityId, rootEpoch + 1)

    /**
     * The base-rekey address the rotation INTO [entry]'s current epoch rode on, derived from the
     * prior epoch's (canonical) held root — or null when we hold none (a fresh joiner at this
     * epoch). Watching it after adopting is what lets the same-epoch race heal (CORD-06 §3): a
     * racing sibling rotation sealed under the same prior root arrives here, and a strictly lower
     * one replaces the root we adopted.
     */
    fun siblingBaseRekeyPlane(entry: ConcordCommunityListEntry): GroupKey? {
        if (entry.rootEpoch <= 0) return null
        val prior = ConcordRefounding.canonicalHeldRoots(entry.heldRoots).firstOrNull { it.epoch == entry.rootEpoch - 1 } ?: return null
        return ConcordKeyDerivation.baseRekeyAddress(prior.key.hexToByteArray(), entry.id.hexToByteArray(), entry.rootEpoch)
    }

    // ---- relay filters (what to REQ) -----------------------------------------
    //
    // Tagged here rather than at each call site: they reach the relays through one-shot helpers
    // (fetchAll, fetchAllPages) from a dozen places, several of them running in the background.

    /** Wraps at a plane/channel address: kind-1059 events authored by the stream key. */
    fun planeFilter(planePubKeyHex: HexKey): Filter = ExplainedFilter(kinds = listOf(ConcordStreamEnvelope.KIND_WRAP), authors = listOf(planePubKeyHex), purpose = SubPurpose.COMMUNITY_CHATS)

    /** Wraps across several plane addresses on one relay: kind-1059 authored by any of them. */
    fun planeFilterFor(planePubKeysHex: List<HexKey>): Filter = ExplainedFilter(kinds = listOf(ConcordStreamEnvelope.KIND_WRAP), authors = planePubKeysHex, purpose = SubPurpose.COMMUNITY_CHATS)

    /** The public invite bundle for a link signer. */
    fun bundleFilter(linkSignerPubKeyHex: HexKey): Filter = ExplainedFilter(kinds = listOf(ConcordInviteBundleEvent.KIND), authors = listOf(linkSignerPubKeyHex), purpose = SubPurpose.COMMUNITY_CHATS)

    /**
     * The bundles of several links at once — one REQ over every link signer instead of a round trip
     * per link, which is what a Refounding needs when it re-mints a creator's whole set.
     *
     * Partition the result by `pubKey` before classifying: [ConcordInviteBundle.classify] resolves a
     * single coordinate, so handing it a pooled set would let one link's revocation tombstone decide
     * another link's status purely by being newer.
     */
    fun bundlesFilter(linkSignerPubKeyHexes: List<HexKey>): Filter = ExplainedFilter(kinds = listOf(ConcordInviteBundleEvent.KIND), authors = linkSignerPubKeyHexes, purpose = SubPurpose.COMMUNITY_CHATS)

    /**
     * Pending direct invites addressed to the given member (indexed by k=3313, CORD-05 §6). [since]
     * should come from [ConcordDirectInvite.inboxSince]: wraps are backdated up to two days, so a
     * cursor at the newest wrap seen would miss invites published after it.
     */
    fun directInvitesFilter(
        memberPubKeyHex: HexKey,
        since: Long? = null,
    ): Filter =
        ExplainedFilter(
            kinds = listOf(ConcordStreamEnvelope.KIND_WRAP),
            tags = mapOf("p" to listOf(memberPubKeyHex), "k" to listOf(ConcordDirectInvite.KIND.toString())),
            since = since,
            purpose = SubPurpose.COMMUNITY_CHATS,
            purposeDetail = "Concord direct invites",
        )

    // ---- community lifecycle --------------------------------------------------

    /** Creates a community and its genesis editions (see [ConcordCommunityFactory]). */
    suspend fun createCommunity(
        ownerSigner: NostrSigner,
        name: String,
        createdAt: Long,
        description: String? = null,
        relays: List<String> = emptyList(),
        icon: ImagePointer? = null,
    ): NewConcordCommunity = ConcordCommunityFactory.create(ownerSigner, name, createdAt, description, relays, icon)

    /**
     * Opens the control-plane [wraps] into their [ControlEdition]s, dropping any that don't open or
     * parse — including an edition under an encrypted seal, which the Control Plane never carries
     * (CORD-02 §5: its seals MUST be plaintext kind 20014).
     */
    fun controlEditions(
        wraps: List<Event>,
        controlPlane: ControlPlaneKeys,
    ): List<ControlEdition> =
        wraps.mapNotNull { wrap ->
            ConcordStreamEnvelope.openOrNull(wrap, controlPlane)?.let { ControlEdition.fromOpened(it) }
        }

    /** Opens the control-plane [wraps] and folds them into the live community state. */
    fun foldCommunity(
        wraps: List<Event>,
        controlPlane: ControlPlaneKeys,
        communityId: ByteArray,
        ownerPubKey: HexKey,
    ): ConcordCommunityState = ConcordCommunityState.fold(controlEditions(wraps, controlPlane), communityId, ownerPubKey)

    // ---- channel chat ---------------------------------------------------------

    /**
     * [extraTags] plus the CORD-08 §2 `expiration` a rumor of [kind] created at [createdAt] must carry
     * while the community's timer is [timerSecs] — none when the timer is off or the kind is exempt
     * (deletes, timer notices, ephemeral kinds). Inside the signed rumor, so it is authoritative.
     */
    private fun withTimer(
        extraTags: Array<Array<String>>,
        kind: Int,
        createdAt: Long,
        timerSecs: Long?,
    ): Array<Array<String>> = ConcordDisappearing.withExpiration(extraTags, ConcordDisappearing.expirationFor(kind, createdAt, timerSecs))

    /**
     * Seals [rumor] (encrypted 20013) and wraps it on the [channel] plane. The wrap repeats the
     * rumor's own `expiration`, if any, so NIP-40 relays delete the ciphertext (CORD-08 §2).
     */
    private suspend fun wrapChat(
        rumor: Event,
        channel: GroupKey,
        authorSigner: NostrSigner,
    ): Event = ConcordStreamEnvelope.wrap(rumor, channel, authorSigner, encrypted = true, outerTags = ConcordDisappearing.wrapTagsFor(rumor))

    /**
     * Builds a CORD-08 §4 timer-notice wrap (kind 1740) announcing [timerSecs] (`0` = off) on the
     * [channel] plane. A notice never expires, whatever the timer.
     */
    suspend fun buildChannelTimerNotice(
        authorSigner: NostrSigner,
        channel: GroupKey,
        channelId: HexKey,
        epoch: Long,
        timerSecs: Long,
        createdAt: Long,
    ): Event = wrapChat(ConcordDisappearing.timerNotice(authorSigner.pubKey, channelId, epoch, timerSecs, createdAt), channel, authorSigner)

    /** Builds an encrypted-seal channel message wrap to publish on the [channel] plane. */
    suspend fun buildChannelMessage(
        authorSigner: NostrSigner,
        channel: GroupKey,
        channelId: HexKey,
        epoch: Long,
        text: String,
        createdAt: Long,
        extraTags: Array<Array<String>> = emptyArray(),
        timerSecs: Long? = null,
    ): Event {
        val rumor = ChannelChat.message(authorSigner.pubKey, channelId, epoch, text, createdAt, withTimer(extraTags, ChatEvent.KIND, createdAt, timerSecs))
        return wrapChat(rumor, channel, authorSigner)
    }

    /**
     * Builds an encrypted-seal channel message wrap carrying one or more encrypted image [imetas]
     * (Armada `encryptAttachments` shape) to publish on the [channel] plane.
     */
    suspend fun buildChannelImageMessage(
        authorSigner: NostrSigner,
        channel: GroupKey,
        channelId: HexKey,
        epoch: Long,
        text: String,
        imetas: List<IMetaTag>,
        createdAt: Long,
        extraTags: Array<Array<String>> = emptyArray(),
        timerSecs: Long? = null,
    ): Event {
        val rumor = ChannelChat.imageMessage(authorSigner.pubKey, channelId, epoch, text, imetas, createdAt, withTimer(extraTags, ChatEvent.KIND, createdAt, timerSecs))
        return wrapChat(rumor, channel, authorSigner)
    }

    /** Builds an encrypted-seal inline quote-reply wrap (kind-9 message quoting [parent] via `q`) on the [channel] plane. */
    suspend fun buildChannelInlineReply(
        authorSigner: NostrSigner,
        channel: GroupKey,
        channelId: HexKey,
        epoch: Long,
        parent: Event,
        text: String,
        createdAt: Long,
        extraTags: Array<Array<String>> = emptyArray(),
        timerSecs: Long? = null,
    ): Event {
        val rumor = ChannelChat.inlineReply(authorSigner.pubKey, channelId, epoch, text, parent.id, parent.pubKey, createdAt, withTimer(extraTags, ChatEvent.KIND, createdAt, timerSecs))
        return wrapChat(rumor, channel, authorSigner)
    }

    /** Builds an encrypted-seal thread-reply wrap (kind-1111 NIP-22 comment on [parent]) on the [channel] plane. */
    suspend fun buildChannelReply(
        authorSigner: NostrSigner,
        channel: GroupKey,
        channelId: HexKey,
        epoch: Long,
        parent: Event,
        text: String,
        createdAt: Long,
        extraTags: Array<Array<String>> = emptyArray(),
        timerSecs: Long? = null,
    ): Event {
        val rumor = ChannelChat.reply(authorSigner.pubKey, channelId, epoch, text, parent, createdAt, withTimer(extraTags, CommentEvent.KIND, createdAt, timerSecs))
        return wrapChat(rumor, channel, authorSigner)
    }

    /**
     * Builds an encrypted-seal thread-reply wrap carrying one or more encrypted image [imetas]
     * (kind-1111 NIP-22 comment on [parent]) on the [channel] plane — the minichat image path.
     */
    suspend fun buildChannelImageReply(
        authorSigner: NostrSigner,
        channel: GroupKey,
        channelId: HexKey,
        epoch: Long,
        parent: Event,
        text: String,
        imetas: List<IMetaTag>,
        createdAt: Long,
        extraTags: Array<Array<String>> = emptyArray(),
        timerSecs: Long? = null,
    ): Event {
        val rumor = ChannelChat.imageReply(authorSigner.pubKey, channelId, epoch, text, imetas, parent, createdAt, withTimer(extraTags, CommentEvent.KIND, createdAt, timerSecs))
        return wrapChat(rumor, channel, authorSigner)
    }

    /**
     * Builds an encrypted-seal **edit** wrap (kind-3302 [ChannelChat.edit] of [target]) on the
     * [channel] plane. [newText] replaces [target]'s content on receivers that apply the edit overlay;
     * only the original author's edits take effect, so restrict callers to their own messages.
     *
     * The Edit carries [expiration] verbatim — by default [target]'s own NIP-40 deadline, and none
     * when [target] has none — never `now + timer`: an Edit stamped with a fresh deadline would
     * outlive (or cut short) the message it revises, so the revised words could survive the message
     * the timer already erased (CORD-08 §2; the reference client keeps `expirationOf(original)`).
     */
    suspend fun buildChannelEdit(
        authorSigner: NostrSigner,
        channel: GroupKey,
        channelId: HexKey,
        epoch: Long,
        target: Event,
        newText: String,
        createdAt: Long,
        extraTags: Array<Array<String>> = emptyArray(),
        expiration: Long? = ConcordDisappearing.expirationOf(target),
    ): Event {
        val tags = ConcordDisappearing.withExpiration(extraTags.filterNot { it.isNotEmpty() && it[0] == "expiration" }.toTypedArray(), expiration)
        val rumor = ChannelChat.edit(authorSigner.pubKey, channelId, epoch, target.id, newText, createdAt, tags)
        return wrapChat(rumor, channel, authorSigner)
    }

    /**
     * Builds an encrypted-seal **delete** wrap (kind-5 [ChannelChat.delete] of the author's own
     * [targets]) on the [channel] plane — the in-stream delete of CORD-01. Never publish a Concord
     * delete any other way: a signed kind 5 or a NIP-17 delete would carry the rumor ids outside
     * the community.
     */
    suspend fun buildChannelDelete(
        authorSigner: NostrSigner,
        channel: GroupKey,
        channelId: HexKey,
        epoch: Long,
        targets: List<Event>,
        createdAt: Long,
    ): Event {
        val rumor = ChannelChat.delete(authorSigner.pubKey, channelId, epoch, targets, createdAt)
        return wrapChat(rumor, channel, authorSigner)
    }

    /** Builds an encrypted-seal reaction wrap (kind 7 against [target]) on the [channel] plane. */
    suspend fun buildChannelReaction(
        authorSigner: NostrSigner,
        channel: GroupKey,
        channelId: HexKey,
        epoch: Long,
        target: Event,
        reaction: String,
        createdAt: Long,
        extraTags: Array<Array<String>> = emptyArray(),
        timerSecs: Long? = null,
    ): Event {
        val rumor = ChannelChat.reaction(authorSigner.pubKey, channelId, epoch, target.id, target.pubKey, target.kind, reaction, createdAt, withTimer(extraTags, ReactionEvent.KIND, createdAt, timerSecs))
        return wrapChat(rumor, channel, authorSigner)
    }

    /**
     * Builds an **ephemeral** typing heartbeat wrap (kind-23311 rumor, kind-21059 wrap)
     * on the [channel] plane. Relays broadcast but never store it; publish every few
     * seconds while the user is composing.
     */
    suspend fun buildChannelTyping(
        authorSigner: NostrSigner,
        channel: GroupKey,
        channelId: HexKey,
        epoch: Long,
        createdAt: Long,
    ): Event {
        val rumor = ChannelChat.typing(authorSigner.pubKey, channelId, epoch, createdAt)
        return ConcordStreamEnvelope.wrap(rumor, channel, authorSigner, encrypted = true, ephemeral = true, createdAt = createdAt)
    }

    /**
     * Opens the channel [wraps], keeps the kind-9 messages that pass the Chat ingest gate
     * ([channelRumors]), and returns them oldest-first by their CORD-02 §4 send time
     * (`created_at * 1000 + ms`), then id.
     */
    fun channelMessages(
        wraps: List<Event>,
        channel: GroupKey,
        channelId: HexKey,
        epoch: Long,
    ): List<ConcordChatMessage> =
        channelRumors(wraps, channel, channelId, epoch)
            .filter { it.kind == ChatEvent.KIND }
            .distinctBy { it.id }
            .sortedWith(compareBy({ ChannelChat.orderingMs(it) }, { it.id }))
            .map { ConcordChatMessage(it.id, it.pubKey, it.content, it.createdAt, channelId, epoch) }

    /**
     * Opens the channel [wraps] and returns every validated inner rumor bound to
     * [channelId]/[epoch] — messages (kind 9), replies (1111), reactions (7),
     * deletes (5), edits, etc. — as typed [Event]s. The caller lands these in a
     * store keyed by rumor id so the normal reaction/reply/delete/OTS machinery
     * wires up automatically. Deduping is left to that store (rumor ids are stable
     * content hashes), so this may return duplicates across mirrored wraps.
     */
    fun channelRumors(
        wraps: List<Event>,
        channel: GroupKey,
        channelId: HexKey,
        epoch: Long,
    ): List<Event> = wraps.mapNotNull { wrap -> openChannelRumor(wrap, channel, channelId, epoch) }

    /**
     * Opens one channel [wrap] and returns its rumor only when it passes the Chat ingest gate
     * ([ChannelChat.acceptOpened]): an encrypted 20013 seal, a Chat kind (never another plane's
     * kind), a strict `channel`/`epoch` binding, and a well-formed `ms`. A rumor whose own
     * `expiration` is at or before [now] is refused too (CORD-08 §3: never stored). Anything else is
     * dropped here, before it can reach the store.
     */
    fun openChannelRumor(
        wrap: Event,
        channel: GroupKey,
        channelId: HexKey,
        epoch: Long,
        now: Long = TimeUtils.now(),
    ): Event? = openChannelRumorAnyExpiry(wrap, channel, channelId, epoch)?.takeUnless { ConcordDisappearing.isExpired(it, now) }

    /**
     * [openChannelRumor] without the CORD-08 expiry refusal, for a caller that must tell an expired
     * rumor apart from garbage — the session, which purges an expired rumor's wrap instead of merely
     * skipping it. Such a caller owns the refusal. [kinds] widens the gate to
     * [ChannelChat.PLANE_KINDS] for a caller that routes the WebXDC signal apart from chat rows.
     */
    fun openChannelRumorAnyExpiry(
        wrap: Event,
        channel: GroupKey,
        channelId: HexKey,
        epoch: Long,
        kinds: Set<Int> = ChannelChat.CHAT_KINDS,
    ): Event? = ConcordStreamEnvelope.openOrNull(wrap, channel)?.let { ChannelChat.acceptOpened(it, channelId, epoch, kinds) }

    // ---- invites --------------------------------------------------------------

    /**
     * Builds a [CommunityInvite] from a freshly created (or joined) community's
     * public info. [controlPk] is the Control Plane's signer pubkey at [rootEpoch]
     * (CORD-05 §1) — read access for the joiner, never write; null only for a
     * legacy, pre-split community.
     */
    fun inviteFor(
        communityIdHex: HexKey,
        ownerPubKey: HexKey,
        ownerSaltHex: HexKey,
        communityRootHex: HexKey,
        rootEpoch: Long,
        name: String,
        relays: List<String>,
        controlPk: HexKey? = null,
        creator: HexKey? = null,
        label: String? = null,
    ): CommunityInvite =
        CommunityInvite(
            communityId = communityIdHex,
            owner = ownerPubKey,
            ownerSalt = ownerSaltHex,
            communityRoot = communityRootHex,
            rootEpoch = rootEpoch,
            controlPk = controlPk,
            relays = relays,
            name = name,
            // Optional attribution (CORD-05 §1): echoed in the joiner's Guestbook Join, so link
            // holders can count per-link usage. Inside the token-encrypted bundle only.
            creatorNpub = creator,
            label = label,
        )

    /**
     * The §1 bundle a Direct Invite hands [recipient] for the community [entry] holds (CORD-05 §6):
     * the current base, epoch and `control_pk`, the relays, a name/icon preview, the optional
     * [expiresAtMs] (unix ms) and [creator] attribution — and exactly the Private Channel keys the
     * recipient's Roles entitle them to in [authority] ([ConcordInviteVend.vendableChannels], Armada's
     * `VendAudience` "member" rule). A key the recipient isn't entitled to is never whispered, even
     * though nothing on the wire could stop it.
     */
    fun directInviteFor(
        entry: ConcordCommunityListEntry,
        authority: AuthorityResolver,
        recipient: HexKey,
        creator: HexKey,
        expiresAtMs: Long? = null,
        name: String = entry.name,
        icon: ImagePointer? = null,
        onlyChannelIds: Set<HexKey>? = null,
    ): CommunityInvite =
        CommunityInvite(
            communityId = entry.id,
            owner = entry.owner,
            ownerSalt = entry.ownerSalt,
            communityRoot = entry.root,
            rootEpoch = entry.rootEpoch,
            controlPk = entry.controlPk,
            channels =
                ConcordInviteVend.toInviteChannels(
                    ConcordInviteVend
                        .vendableChannels(entry.privateChannels, authority, recipient)
                        .filter { onlyChannelIds == null || it.channelId.lowercase() in onlyChannelIds },
                ),
            relays = entry.relays.take(ConcordInviteBundle.MAX_COMMUNITY_RELAYS),
            name = name.ifBlank { entry.name },
            icon = icon,
            expiresAt = expiresAtMs,
            creatorNpub = creator,
        )

    /**
     * The Direct Invite [sender] may hand [recipient] for the held [entry] whose Control Plane folds
     * to [state] (CORD-05 §6), or why not. No community permission gates a Direct Invite — none
     * could — but a dissolved community, a [sender] its roster bans (like minting a link), and a
     * banned [recipient] (whose join would be refused anyway) are refused; the bundle's name/icon
     * preview comes from the folded metadata.
     */
    fun draftDirectInvite(
        entry: ConcordCommunityListEntry,
        state: ConcordCommunityState,
        sender: HexKey,
        recipient: HexKey,
        expiresAtMs: Long? = null,
        onlyChannelIds: Set<HexKey>? = null,
    ): ConcordDirectInviteDraft {
        val to = recipient.lowercase()
        if (!HEX64.matches(to)) return ConcordDirectInviteDraft.Refused(ConcordDirectInviteSendResult.INVALID_RECIPIENT)
        if (state.dissolved || state.authority.isBanned(sender)) return ConcordDirectInviteDraft.Refused(ConcordDirectInviteSendResult.NOT_MEMBER)
        if (state.authority.isBanned(to)) return ConcordDirectInviteDraft.Refused(ConcordDirectInviteSendResult.RECIPIENT_BANNED)
        return ConcordDirectInviteDraft.Ready(
            directInviteFor(
                entry = entry,
                authority = state.authority,
                recipient = to,
                creator = sender.lowercase(),
                expiresAtMs = expiresAtMs,
                name = state.metadata?.name ?: entry.name,
                icon = state.metadata?.icon,
                onlyChannelIds = onlyChannelIds?.mapTo(HashSet()) { it.lowercase() },
            ),
        )
    }

    /** Giftwraps [invite] to [recipient] as a Direct Invite (see [ConcordDirectInvite.build]). */
    suspend fun buildDirectInvite(
        senderSigner: NostrSigner,
        recipient: HexKey,
        invite: CommunityInvite,
        createdAt: Long = TimeUtils.now(),
    ): GiftWrapEvent = ConcordDirectInvite.build(senderSigner, recipient, invite, createdAt)

    /** Opens + validates a Direct Invite wrap addressed to [recipientSigner] (see [ConcordDirectInvite.open]). */
    suspend fun openDirectInvite(
        wrap: Event,
        recipientSigner: NostrSigner,
    ): OpenedDirectInvite? = ConcordDirectInvite.open(wrap, recipientSigner)

    /**
     * Where a Direct Invite reaches a member, and where that member scans for one (CORD-05 §6):
     * their kind-10050 DM relays, else their NIP-65 read relays, else the stock Concord set every
     * client ships (Armada `inviteDeliveryRelays`). Send and scan share this so both sides meet. The
     * stock set is fallback-only: a curated private inbox is never also fanned out to public relays.
     */
    fun directInviteDeliveryRelays(lists: RecipientRelayFetcher.Lists?): Set<NormalizedRelayUrl> {
        val inbox = lists?.dmInboxOrFallback().orEmpty()
        if (inbox.isNotEmpty()) return inbox.toSet()
        return InviteRelayDictionary.STOCK.mapNotNullTo(LinkedHashSet()) { RelayUrlNormalizer.normalizeOrNull(it) }
    }

    /** Mints a shareable public invite link + bundle event (see [ConcordInviteBundle.mintLink]). */
    fun mintInviteLink(
        base: String,
        invite: CommunityInvite,
        createdAt: Long,
        relays: List<String>? = null,
    ): MintedInviteLink = ConcordInviteBundle.mintLink(base, invite, createdAt, relays)

    /**
     * Re-publishes a bundle at an **existing** link's coordinate, carrying [invite] refreshed for the
     * current epoch (CORD-05 §1). The kind-33301 bundle is addressable and authored by the link
     * signer, so re-signing with the same [linkSignerPrivKey] and re-encrypting under the same
     * [token] replaces what is there — every holder of that link keeps working, now pointing at the
     * new root.
     *
     * This is what makes stranded recovery live: a member a Refounding left out has no rekey blob and
     * no message to miss, and re-resolving their link is the only way back — which requires the
     * community to re-mint at the *same* coordinate rather than issuing a fresh link. Minting a new
     * link leaves the old one pointing at a dead epoch forever.
     *
     * Safe to call for every live link because recovery is ban-gated at the epoch being left
     * (CORD-06, A2): a member the Refounding removed was banned on the way out, so their own
     * `recover` is refused even though their link now resolves.
     */
    fun remintBundleAt(
        linkSignerPrivKey: ByteArray,
        token: ByteArray,
        invite: CommunityInvite,
        createdAt: Long,
    ): Event = ConcordInviteBundle.build(linkSignerPrivKey, token, invite, createdAt)

    /**
     * Retires an existing link by publishing a `vsk=9` revocation tombstone at its coordinate
     * (CORD-05 §2). Once this lands, every client resolving that URL gets
     * [com.vitorpamplona.quartz.concord.cord05Invites.InviteBundleStatus.Revoked] instead of keys.
     *
     * Publish this *before* recording the tombstone in the kind-13303 Invite List — the list entry
     * carries the only copy of the `signer_sk` this call needs, and the list merge drops a
     * tombstoned token's entry for good. Recording first and failing to publish would leave the link
     * live on the wire with no way left to retire it.
     */
    fun revokeBundleAt(
        linkSignerPrivKey: ByteArray,
        createdAt: Long,
    ): Event = ConcordInviteBundle.buildRevocation(linkSignerPrivKey, createdAt)

    /** Parses a shareable invite URL into its pointer + private fragment. */
    fun parseInviteLink(url: String): ParsedInviteLink? = ConcordInviteLink.parseUrl(url)

    /**
     * Reduces an invite URL to the domain-agnostic bare `<naddr>#<fragment>` form
     * stored as an entry's `invite_ref` (the stranded-recovery anchor). Null if the
     * link is unparseable.
     */
    fun bareInviteRef(url: String): String? = ConcordInviteLink.bareForm(url)

    /**
     * True when a live [bundle] resolved at [entry]'s own stored invite link says a Refounding
     * left us behind (a higher epoch, and we are not banned). Detection only: a bundle may never
     * move a held community's base on its own (CORD-06 §2) — see [ConcordStrandedRecovery].
     */
    fun isStranded(
        entry: ConcordCommunityListEntry,
        bundle: CommunityInvite,
        bannedAtCurrentEpoch: Boolean,
    ): Boolean = ConcordStrandedRecovery.isStranded(entry, bundle, bannedAtCurrentEpoch)

    /**
     * The entry after the user **explicitly** re-accepts the invite link a stranded [entry] was
     * joined through, or null when not stranded. Only ever from a user action — never a sweep.
     */
    fun rejoinStranded(
        entry: ConcordCommunityListEntry,
        bundle: CommunityInvite,
        bannedAtCurrentEpoch: Boolean,
    ): ConcordCommunityListEntry? = ConcordStrandedRecovery.rejoinForward(entry, bundle, bannedAtCurrentEpoch)

    /** Decrypts + validates a fetched bundle event with the link token; null if invalid. */
    fun openBundle(
        bundleEvent: Event,
        token: ByteArray,
    ): CommunityInvite? = ConcordInviteBundle.parse(bundleEvent, token)?.takeIf { ConcordInviteBundle.validate(it) }

    /**
     * Resolves every event fetched at an invite's addressable coordinate into one
     * [InviteBundleStatus] (live / expired / revoked / unreadable / absent) per CORD-05
     * §2, so a redeeming client honours a `vsk=9` revocation tombstone and an
     * `expires_at` in the past, and reports why a link can't be opened instead of
     * retrying blindly. [nowMs] is unix milliseconds. Only events genuinely at the link's
     * coordinate count — signed by [linkSignerPubKey], `d == ""` — never what a relay claims is.
     */
    fun classifyInvite(
        wraps: List<Event>,
        linkSignerPubKey: HexKey,
        token: ByteArray,
        nowMs: Long = TimeUtils.nowMillis(),
    ): InviteBundleStatus = ConcordInviteBundle.classify(wraps, linkSignerPubKey, token, nowMs)

    /**
     * The Control Plane keys described by a redeemed [invite] so the joiner can
     * read it: the bundle's `control_pk` on a split community, the legacy plane
     * when absent (CORD-05 §1). Never a writer — an invite delivers no secret.
     */
    fun controlPlaneFor(invite: CommunityInvite): ControlPlaneKeys =
        controlPlaneKeys(
            invite.communityRoot.hexToByteArray(),
            invite.communityId.hexToByteArray(),
            invite.rootEpoch,
            controlPk = invite.controlPk,
        )

    // ---- guestbook (CORD-02 §5) ----------------------------------------------

    /**
     * Builds a self-signed Guestbook JOIN (kind 3306) wrap on the community's
     * Guestbook Plane. Membership is off-consensus best-effort presence, but it is
     * the member-visible roster a Refounding rotates keys to (CORD-06), so a client
     * announces one on create/join to be re-keyed on future removals.
     */
    suspend fun buildGuestbookJoin(
        memberSigner: NostrSigner,
        guestbook: GroupKey,
        createdAt: Long,
        inviteCreator: HexKey? = null,
        inviteLabel: String? = null,
    ): Event {
        val rumor = Guestbook.join(memberSigner.pubKey, createdAt, inviteCreator = inviteCreator, inviteLabel = inviteLabel)
        return ConcordStreamEnvelope.wrap(rumor, guestbook, memberSigner, encrypted = true, createdAt = createdAt)
    }

    /**
     * Builds a self-signed Guestbook LEAVE (kind 3306) wrap on the community's Guestbook Plane
     * (CORD-02 §5): the member's own word that they left, so everyone else's coalesced roster drops
     * them and a later Refounding stops re-keying them.
     */
    suspend fun buildGuestbookLeave(
        memberSigner: NostrSigner,
        guestbook: GroupKey,
        createdAt: Long,
    ): Event = ConcordStreamEnvelope.wrap(Guestbook.leave(memberSigner.pubKey, createdAt), guestbook, memberSigner, encrypted = true, createdAt = createdAt)

    /**
     * Builds an authorized Guestbook KICK (kind 3309) wrap naming [target], citing [citation] — the
     * actor's own Grant head (`vac`, CORD-04 §5), null only for the owner. A Kick is the *second*
     * layer of a removal: the caller strips the target's roles first (CORD-04 §6).
     */
    suspend fun buildGuestbookKick(
        actorSigner: NostrSigner,
        guestbook: GroupKey,
        target: HexKey,
        citation: AuthorityCitation?,
        createdAt: Long,
    ): Event {
        val rumor = Guestbook.kick(actorSigner.pubKey, target.lowercase(), createdAt, citation = citation)
        return ConcordStreamEnvelope.wrap(rumor, guestbook, actorSigner, encrypted = true, createdAt = createdAt)
    }

    /**
     * Opens the guestbook [wraps] into their live membership set: joins minus later leaves and later
     * Kicks honored against [authority] (none is honored without it).
     */
    fun guestbookMembers(
        wraps: List<Event>,
        guestbook: GroupKey,
        authority: AuthorityResolver? = null,
    ): Set<HexKey> = projectGuestbook(wraps.mapNotNull { guestbookEntry(it, guestbook) }, authority)

    /**
     * Opens a single guestbook [wrap] into its entry, or null when it doesn't belong to
     * [guestbook] or isn't a guestbook rumor.
     *
     * Split out of [guestbookMembers] so a caller holding a growing wrap buffer can memoize the
     * open per wrap id: opening is the expensive half (two NIP-44 decrypts plus the wrap and seal
     * signature verifies), while [projectGuestbook] over the already-opened entries is trivial.
     * Re-projecting a buffer of n wraps on every arrival without that memo is quadratic in
     * decryptions — see [ConcordCommunitySession]'s guestbook cache.
     */
    fun guestbookEntry(
        wrap: Event,
        guestbook: GroupKey,
    ): GuestbookEntry? =
        ConcordStreamEnvelope
            .openOrNull(wrap, guestbook)
            // The Guestbook's seals MUST be encrypted (CORD-02 §5); a plaintext one is Control-only.
            ?.takeIf { it.sealKind == ConcordStreamEnvelope.KIND_SEAL_ENCRYPTED }
            ?.rumor
            ?.let { Guestbook.parse(it) }

    /**
     * The honored Kick naming [me] in a [coalesced] Guestbook (CORD-04 §6), or null. Only a Kick
     * newer than [addedAtMs] — when this membership began — counts: an older one judged an earlier
     * membership that a re-join has already put behind us. The owner is never kicked.
     */
    fun honoredKickAgainst(
        coalesced: Map<HexKey, GuestbookEntry>,
        me: HexKey,
        owner: HexKey,
        addedAtMs: Long,
    ): GuestbookEntry? {
        val self = me.lowercase()
        if (self == owner.lowercase()) return null
        return coalesced[self]?.takeIf { it.action == GuestbookAction.KICK && it.ms > addedAtMs }
    }

    /**
     * The CORD-02 §5 coalesce of already-opened [entries] (latest motion per npub, Kicks honored
     * against [authority]) down to the JOINed member set.
     */
    fun projectGuestbook(
        entries: Collection<GuestbookEntry>,
        authority: AuthorityResolver? = null,
        nowMs: Long = TimeUtils.nowMillis(),
    ): Set<HexKey> = joinedMembers(Guestbook.coalesce(entries, nowMs, authority))

    /** The npubs whose coalesced Guestbook state ([Guestbook.coalesce]) is a Join. */
    fun joinedMembers(coalesced: Map<HexKey, GuestbookEntry>): Set<HexKey> = coalesced.filterValues { it.action == GuestbookAction.JOIN }.keys

    // ---- refounding / rekey (CORD-06) ----------------------------------------

    /**
     * Builds a whole-community Refounding (CORD-06 §3): the compacted Control Plane
     * re-sealed at the new epoch's split Control address plus the base-rotation
     * rekey blobs delivering [newRoot] + the new `control_pk` to [recipientsXOnly]
     * — the [staffXOnly] subset also receiving [newControlRoot] (CORD-06 §1). Pure
     * — the caller sources the recipient and staff sets (the folded Roster's
     * `staffMembers()`, CORD-04 §3) and owns publish + persistence.
     */
    suspend fun buildRefounding(
        rotatorSigner: NostrSigner,
        communityId: HexKey,
        priorRoot: ByteArray,
        newRoot: ByteArray,
        newControlRoot: ByteArray,
        rootEpoch: Long,
        priorControlWraps: List<Event>,
        priorControlKeys: ControlPlaneKeys,
        recipientsXOnly: List<HexKey>,
        staffXOnly: Set<HexKey>,
        createdAt: Long,
        ownerPubKey: HexKey,
        authority: AuthorityCitation? = null,
        mustCarry: Map<String, Long> = emptyMap(),
    ): RefoundingBuild =
        ConcordRefounding.build(
            rotatorSigner = rotatorSigner,
            communityId = communityId.hexToByteArray(),
            priorRoot = priorRoot,
            newRoot = newRoot,
            newControlRoot = newControlRoot,
            rootEpoch = rootEpoch,
            priorControlWraps = priorControlWraps,
            priorControlKeys = priorControlKeys,
            recipientsXOnly = recipientsXOnly,
            staffXOnly = staffXOnly,
            createdAt = createdAt,
            ownerPubKey = ownerPubKey,
            authority = authority,
            mustCarry = mustCarry,
        )

    /**
     * Receives an inbound base rotation for the member behind [recipientSigner]:
     * finds the delivered new root across the buffered kind-3303 [wraps], verifying
     * scope, epoch and continuity against the [priorRoot] the member holds — and,
     * on a staff blob, that the delivered `control_root` derives to the delivered
     * `control_pk` (CORD-06 §1). Returns the new root + Control keys + rotator
     * or null if not re-keyed. [accept] is the caller's authority check (see
     * [ConcordReceive.isHonoredRotation]); racing rotations it admits converge on
     * the lowest new root (CORD-06 §3).
     */
    suspend fun openBaseRekey(
        wraps: List<Event>,
        baseRekey: GroupKey,
        recipientSigner: NostrSigner,
        communityId: HexKey,
        priorRoot: ByteArray,
        rootEpoch: Long,
        accept: (ReceivedRefounding) -> Boolean = { true },
    ): ReceivedRefounding? = ConcordRefounding.findNewRoot(wraps, baseRekey, recipientSigner, communityId.hexToByteArray(), priorRoot, rootEpoch, accept)
}
