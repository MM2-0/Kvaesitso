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

import com.vitorpamplona.crypto.random.RandomInstance
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEntry
import com.vitorpamplona.quartz.concord.cord02Community.PrivateChannelKey
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordChannelKeyring
import com.vitorpamplona.quartz.concord.cord04Roles.AuthorityCitation
import com.vitorpamplona.quartz.concord.cord04Roles.AuthorityResolver
import com.vitorpamplona.quartz.concord.cord04Roles.ChannelEntity
import com.vitorpamplona.quartz.concord.cord04Roles.ConcordLimits
import com.vitorpamplona.quartz.concord.cord04Roles.ConcordPermissions
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEdition
import com.vitorpamplona.quartz.concord.cord04Roles.RoleEntity
import com.vitorpamplona.quartz.concord.cord04Roles.RoleScope
import com.vitorpamplona.quartz.concord.cord05Invites.ConcordInviteVend
import com.vitorpamplona.quartz.concord.cord06Rekey.ChannelRekeyOutcome
import com.vitorpamplona.quartz.concord.cord06Rekey.ChannelRotation
import com.vitorpamplona.quartz.concord.cord06Rekey.ConcordChannelRekey
import com.vitorpamplona.quartz.concord.cord06Rekey.ConcordRefounding
import com.vitorpamplona.quartz.concord.cord06Rekey.ConcordRotationAuthority
import com.vitorpamplona.quartz.concord.crypto.ControlPlaneKeys
import com.vitorpamplona.quartz.concord.crypto.GroupKey
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner

/**
 * A Private Channel just minted or privatised (CORD-03 §2): its [channelIdHex], the independent
 * [key] to store in the Community List **before** [wraps] publish (a lost List write would orphan
 * the only copy), the access Role minted beside it ([roleIdHex]), and the Control editions to
 * publish in order — the Role first (an orphan Role is inert), then the channel edition.
 */
class PrivateChannelBuild(
    val channelIdHex: HexKey,
    val key: PrivateChannelKey,
    val roleIdHex: HexKey,
    val wraps: List<Event>,
)

/**
 * Private Channels end to end (CORD-03 §1-2, CORD-04 §2, CORD-05 §6, CORD-06 §1-3) as pure
 * builders and decisions, shared by the app and `amy`. The network and the Community List write
 * stay with the caller.
 *
 * Who may read a Private Channel is its Roles: a Role scoped `{kind:"channel", channel_id}` IS its
 * access list ([ConcordInviteVend.isEntitled]). Read access is enforced by key possession alone, so
 * this decides who a key is delivered TO (a Direct Invite on grant) and who a rotation keeps (a
 * channel rekey on revoke). Pinned to Armada's `channelAccess.ts`, `useCommunityActions`
 * (`createChannel`, `privatiseChannel`, `publiciseChannel`) and `useRekey` (`useChannelRekey`,
 * `useChannelRekeyWatch`).
 */
object ConcordPrivateChannels {
    /**
     * The position a new access Role takes (Armada `accessRolePosition`): the bottom of the roster,
     * never above what [actor] may mint (the owner mints from 1, anyone else strictly below their
     * own rank), so a grantee is never promoted by being let into a channel. Null when [actor] holds
     * no rank to mint from.
     */
    fun accessRolePosition(
        authority: AuthorityResolver?,
        actor: HexKey,
        owner: HexKey,
    ): Long? {
        val ceiling =
            if (actor.equals(owner, ignoreCase = true)) {
                1L
            } else {
                (authority?.rank(actor) ?: return null) + 1
            }
        val lowest =
            authority
                ?.roles()
                ?.values
                ?.filterNot { it.deleted }
                ?.maxOfOrNull { it.position } ?: 0L
        return maxOf(ceiling, lowest + 1)
    }

    /** [name] cut to the protocol's 64-byte cap on a character boundary (Armada slices the same way). */
    private fun fitName(name: String): String {
        var out = name
        while (!ConcordLimits.nameFits(out)) out = out.dropLast(1)
        return out
    }

    /** The bit-less access Role scoped to [channelIdHex] (CORD-04 §2): read access is the key, never a bit. */
    fun accessRole(
        name: String,
        channelIdHex: HexKey,
        position: Long,
    ): RoleEntity =
        RoleEntity(
            name = fitName(name),
            position = position,
            permissions = "0",
            scope = RoleScope(kind = "channel", channelId = channelIdHex.lowercase()),
        )

    /**
     * A new channel (CORD-03 §2): a random `channel_id`, and for a Private one an independent key
     * at channel epoch 0 plus its access Role (Armada `createChannel`: a born-private channel is
     * epoch 0; the first *privatisation* of a public channel is epoch 1). Returns null when the
     * name is invalid or the actor has no rank to mint the Role from.
     */
    suspend fun create(
        actor: NostrSigner,
        cp: ControlPlaneKeys,
        communityId: ByteArray,
        name: String,
        accessRoleName: String?,
        current: List<ControlEdition>,
        authority: AuthorityResolver?,
        owner: HexKey,
        createdAt: Long,
    ): PrivateChannelBuild? {
        val channel = ChannelEntity(name = name.trim(), private = true)
        if (!channel.hasValidName()) return null
        val position = accessRolePosition(authority, actor.pubKey, owner) ?: return null
        val channelId = RandomInstance.bytes(32)
        val channelIdHex = channelId.toHexKey()
        val roleId = RandomInstance.bytes(32)
        val role = accessRole(accessRoleName?.trim()?.ifBlank { null } ?: channel.name, channelIdHex, position)
        val roleWrap = ConcordModeration.defineRole(actor, cp, communityId, roleId, role, current, createdAt, owner = owner)
        val channelWrap = ConcordModeration.defineChannel(actor, cp, communityId, channelId, channel, current, createdAt, owner = owner)
        return PrivateChannelBuild(
            channelIdHex = channelIdHex,
            key = PrivateChannelKey(channelIdHex, ConcordChannelRekey.mintKey().toHexKey(), 0, channel.name),
            roleIdHex = roleId.toHexKey(),
            wraps = listOf(roleWrap, channelWrap),
        )
    }

    /**
     * Converts the Public channel [channelIdHex] to Private (CORD-03 §2): a fresh independent key at
     * the NEXT channel epoch ([ConcordChannelKeyring.nextChannelEpoch], floored at [observedFloor],
     * the highest channel rotation seen on the wire), a new access Role, and the channel edition
     * flipping `private` on — every other field of [standing] carried through. Protects the future
     * only: the public era stays readable to every member. Null when the actor can't mint the Role.
     */
    suspend fun privatize(
        actor: NostrSigner,
        cp: ControlPlaneKeys,
        entry: ConcordCommunityListEntry,
        channelIdHex: HexKey,
        standing: ChannelEntity,
        accessRoleName: String?,
        current: List<ControlEdition>,
        authority: AuthorityResolver?,
        createdAt: Long,
        observedFloor: Long = 0,
    ): PrivateChannelBuild? {
        if (standing.private || standing.deleted) return null
        val position = accessRolePosition(authority, actor.pubKey, entry.owner) ?: return null
        val communityId = entry.id.hexToByteArray()
        val roleId = RandomInstance.bytes(32)
        val role = accessRole(accessRoleName?.trim()?.ifBlank { null } ?: standing.name, channelIdHex, position)
        val roleWrap = ConcordModeration.defineRole(actor, cp, communityId, roleId, role, current, createdAt, owner = entry.owner)
        val channelWrap = ConcordModeration.defineChannel(actor, cp, communityId, channelIdHex.hexToByteArray(), standing.copy(private = true), current, createdAt, owner = entry.owner)
        val epoch = ConcordChannelKeyring.nextChannelEpoch(entry, channelIdHex, observedFloor)
        return PrivateChannelBuild(
            channelIdHex = channelIdHex.lowercase(),
            key = PrivateChannelKey(channelIdHex.lowercase(), ConcordChannelRekey.mintKey().toHexKey(), epoch, standing.name),
            roleIdHex = roleId.toHexKey(),
            wraps = listOf(roleWrap, channelWrap),
        )
    }

    /**
     * Converts the Private channel [channelIdHex] back to Public (CORD-03 §2): the flag only. The
     * channel derives from the `community_root` from here on; the held key stays in the List so its
     * holders keep reading the private era, which a later joiner never can. Null when it is not
     * private.
     */
    suspend fun publicize(
        actor: NostrSigner,
        cp: ControlPlaneKeys,
        communityId: ByteArray,
        channelIdHex: HexKey,
        standing: ChannelEntity,
        current: List<ControlEdition>,
        owner: HexKey,
        createdAt: Long,
    ): Event? {
        if (!standing.private || standing.deleted) return null
        return ConcordModeration.defineChannel(actor, cp, communityId, channelIdHex.hexToByteArray(), standing.copy(private = false), current, createdAt, owner = owner)
    }

    // ---- channel rotations (CORD-06 §1-2) -------------------------------------

    /**
     * Whether [actor] may launch a single-channel Rekey cutting [removed] (CORD-06 §3 Authority):
     * `MANAGE_CHANNELS` (or `BAN`, a Refounding's) and strictly outranking every removed target.
     * The owner may always; the owner is never a valid target.
     */
    fun canRotate(
        authority: AuthorityResolver,
        actor: HexKey,
        removed: Collection<HexKey>,
        bit: Int = ConcordPermissions.MANAGE_CHANNELS,
    ): Boolean {
        if (authority.isOwner(actor)) return true
        if (!authority.hasPermission(actor, bit)) return false
        return removed.all { it.equals(actor, ignoreCase = true) || authority.canActOn(actor, it, bit) }
    }

    /**
     * The members a rotation of [channelIdHex] keeps (Armada `handleRotateChannelKey`): exactly
     * those entitled today ([ConcordInviteVend.entitledMembers]) plus the [rotator], who must keep
     * every key or nobody could rotate the channel next time.
     */
    fun keepSet(
        authority: AuthorityResolver,
        channelIdHex: HexKey,
        rotator: HexKey,
    ): Set<HexKey> = ConcordInviteVend.entitledMembers(authority, channelIdHex) + rotator.lowercase()

    /**
     * The wraps of one rotation of [held] to [newKey] for [keep], sealed under [sealingRoot] (the
     * current root for a single-channel Rekey, the PRIOR root inside a Refounding — CORD-06 §3), and
     * citing [authority] on every chunk.
     */
    suspend fun buildRotation(
        rotator: NostrSigner,
        sealingRoot: ByteArray,
        held: PrivateChannelKey,
        newKey: ByteArray,
        keep: Collection<HexKey>,
        createdAt: Long,
        authority: AuthorityCitation?,
    ): List<Event> =
        ConcordChannelRekey.build(
            rotatorSigner = rotator,
            sealingRoot = sealingRoot,
            channelId = held.channelId.hexToByteArray(),
            heldKey = held.key.hexToByteArray(),
            heldEpoch = held.epoch,
            newKey = newKey,
            recipients = keep + rotator.pubKey,
            createdAt = createdAt,
            authority = authority,
        )

    /** The roots a member watches channel rekeys under: the current one and the canonical prior one (CORD-06 §3). */
    fun watchRoots(entry: ConcordCommunityListEntry): List<ByteArray> {
        val prior = ConcordRefounding.canonicalHeldRoots(entry.heldRoots).filter { it.epoch == entry.rootEpoch - 1 }
        return (listOf(entry.root) + prior.map { it.key }).distinct().map { it.hexToByteArray() }
    }

    /**
     * Every channel-rekey address this entry should watch (CORD-06 §2): per held Private Channel,
     * the next [ConcordChannelRekey.LOOKAHEAD] channel epochs past the held one, under each of
     * [watchRoots]. Address hex → key.
     */
    fun watchKeys(entry: ConcordCommunityListEntry): Map<HexKey, GroupKey> {
        val out = LinkedHashMap<HexKey, GroupKey>()
        val roots = watchRoots(entry)
        for (held in entry.privateChannels) {
            if (ConcordChannelKeyring.heldKey(entry, held.channelId) == null) continue
            val channelId = held.channelId.hexToByteArray()
            for (root in roots) {
                for (ahead in 1..ConcordChannelRekey.LOOKAHEAD) {
                    val key = ConcordChannelRekey.address(root, channelId, held.epoch + ahead)
                    out[key.publicKeyHex] = key
                }
            }
        }
        return out
    }

    /**
     * Whether a received channel rotation's Rotator may be honored (CORD-06 §3 Authority): the owner,
     * or a non-banned holder of `MANAGE_CHANNELS` (a single-channel Rekey) or `BAN` (a Refounding's
     * channel rekeys), whose `vac` cites a Grant our fold has synced. Key possession is never
     * authority.
     */
    fun isHonoredRotation(
        entry: ConcordCommunityListEntry,
        editions: Collection<ControlEdition>,
        authority: AuthorityResolver,
        rotation: ChannelRotation,
    ): Boolean {
        val rotator = rotation.rotator
        if (!authority.isOwner(rotator)) {
            if (authority.isBanned(rotator)) return false
            if (!authority.hasPermission(rotator, ConcordPermissions.MANAGE_CHANNELS) && !authority.hasPermission(rotator, ConcordPermissions.BAN)) return false
        }
        val heads = ConcordRotationAuthority.headsOf(editions, entry.id, entry.owner)
        return ConcordRotationAuthority.citationSatisfied(entry.id, rotator, entry.owner, rotation.authority, heads)
    }

    /** Whether [rotator] strictly outranks [me] — only such a Rotator's omission is a cut (CORD-06 §3). */
    fun outranks(
        authority: AuthorityResolver,
        rotator: HexKey,
        me: HexKey,
    ): Boolean {
        if (authority.isOwner(me)) return false
        val theirs = authority.rank(rotator) ?: return false
        val mine = authority.rank(me) ?: Long.MAX_VALUE
        return theirs < mine
    }

    /**
     * What the buffered channel-rekey [wraps] mean for each Private Channel [entry] holds a key for
     * (CORD-06 §2), per channel id: an adoption moves that channel's key forward, a cut drops it and
     * records `channel_cuts`. Channels with nothing to do are omitted. The caller applies the result
     * inside its List write ([applyOutcome]).
     */
    suspend fun receive(
        entry: ConcordCommunityListEntry,
        wraps: Collection<Event>,
        editions: Collection<ControlEdition>,
        authority: AuthorityResolver,
        recipient: NostrSigner,
    ): Map<HexKey, ChannelRekeyOutcome> {
        if (wraps.isEmpty()) return emptyMap()
        val keys = watchKeys(entry)
        val me = recipient.pubKey
        val joinedAtSecs = entry.addedAt / 1000
        val out = LinkedHashMap<HexKey, ChannelRekeyOutcome>()
        for (held in entry.privateChannels) {
            if (ConcordChannelKeyring.heldKey(entry, held.channelId) == null) continue
            val id = held.channelId.lowercase()
            val rotations = ConcordChannelRekey.rotations(wraps, keys, id)
            if (rotations.isEmpty()) continue
            val outcome =
                ConcordChannelRekey.walk(
                    rotations = rotations,
                    channelIdHex = id,
                    heldKey = held.key.hexToByteArray(),
                    heldEpoch = held.epoch,
                    recipientSigner = recipient,
                    joinedAtSecs = joinedAtSecs,
                    honored = { isHonoredRotation(entry, editions, authority, it) },
                    outranksMe = { outranks(authority, it, me) },
                )
            if (outcome !is ChannelRekeyOutcome.None) out[id] = outcome
        }
        return out
    }

    /**
     * [current] with [outcomes] applied — only where the channel is still at the epoch the outcome
     * was computed from ([fromEpochs]), so a write racing another adoption never rolls a key back —
     * or null when nothing changed.
     */
    fun applyOutcome(
        current: ConcordCommunityListEntry,
        outcomes: Map<HexKey, ChannelRekeyOutcome>,
        fromEpochs: Map<HexKey, Long>,
    ): ConcordCommunityListEntry? {
        var next = current
        for ((id, outcome) in outcomes) {
            val held = ConcordChannelKeyring.heldKey(next, id) ?: continue
            if (held.epoch != fromEpochs[id]) continue
            next =
                when (outcome) {
                    is ChannelRekeyOutcome.Adopted -> ConcordChannelKeyring.withRotatedKey(next, id, outcome.key.toHexKey(), outcome.epoch, steppedOver = outcome.steppedOver) ?: next
                    is ChannelRekeyOutcome.Removed -> ConcordChannelKeyring.withoutChannel(next, id, outcome.epoch)
                    ChannelRekeyOutcome.None -> next
                }
        }
        return if (next === current) null else next
    }
}
