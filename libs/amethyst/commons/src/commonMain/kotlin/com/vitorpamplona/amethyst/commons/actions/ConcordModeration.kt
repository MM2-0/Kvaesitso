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

import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityState
import com.vitorpamplona.quartz.concord.cord04Roles.AuthorityCitation
import com.vitorpamplona.quartz.concord.cord04Roles.AuthorityCitations
import com.vitorpamplona.quartz.concord.cord04Roles.AuthorityResolver
import com.vitorpamplona.quartz.concord.cord04Roles.ChannelEntity
import com.vitorpamplona.quartz.concord.cord04Roles.ConcordJson
import com.vitorpamplona.quartz.concord.cord04Roles.ConcordLimits
import com.vitorpamplona.quartz.concord.cord04Roles.ConcordPermissions
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEdition
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEditionBuilder
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEntityKind
import com.vitorpamplona.quartz.concord.cord04Roles.ControlRootWrap
import com.vitorpamplona.quartz.concord.cord04Roles.EntityFloor
import com.vitorpamplona.quartz.concord.cord04Roles.GrantEntity
import com.vitorpamplona.quartz.concord.cord04Roles.MetadataEntity
import com.vitorpamplona.quartz.concord.cord04Roles.RoleEntity
import com.vitorpamplona.quartz.concord.cord04Roles.pins.ConcordPins
import com.vitorpamplona.quartz.concord.cord05Invites.ConcordInviteRegistry
import com.vitorpamplona.quartz.concord.crypto.ConcordKeyDerivation
import com.vitorpamplona.quartz.concord.crypto.ControlPlaneKeys
import com.vitorpamplona.quartz.concord.envelope.ConcordStreamEnvelope
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

/**
 * Builds the Control Plane editions (CORD-04) that drive roles and moderation:
 * defining a role, granting roles to a member, and banning/unbanning members.
 *
 * Each is a kind-3308 edition, plaintext-sealed (so the author signature survives
 * re-encryption across epochs) and wrapped on the community's Control Plane. The
 * caller passes the community's **current** editions so this can chain the next
 * version onto the entity's head (`version = head.version + 1`, `prevHash =
 * head.hash`, a new entity starting at version 1) and read the banlist. Authority is
 * enforced at *fold* time by the `AuthorityResolver`, not here — an edition whose
 * author doesn't outrank its target is simply dropped by every client.
 *
 * Every non-owner edition carries the `vac` authority citation (CORD-04 §1/§5): the
 * actor's own Grant head as the same [current] editions fold it
 * ([AuthorityCitations.forActor]), which every reader, the reference client included,
 * requires before honoring the action. The owner cites nothing. A caller may still pass
 * an explicit [AuthorityCitation] to override it.
 *
 * The CORD-04 §2 / CORD-02 §6 caps ([ConcordLimits]) are enforced here too: an edition
 * every reader would drop is refused with an [IllegalArgumentException] rather than
 * published.
 */
object ConcordModeration {
    /**
     * The current head of [entityId] within [current], or null if the entity has no
     * editions yet — **the authority-gated head**, i.e. the same edition a reader would
     * fold to, resolved against the community's [owner].
     *
     * Two traps live here, and both need the fold:
     *
     * 1. [current] arrives in **wrap-arrival order**, which is not chain order — so the
     *    first matching edition is whichever one a relay happened to deliver first, not
     *    the newest. Chaining off that stale edition forks the chain at an already-used
     *    version, silently dropping the change.
     * 2. The *ungated* structural tip may be an edition every reader **rejects**. Building
     *    on it does two kinds of damage: the rogue's version number is inflated into every
     *    honest edition that follows, and — for a replaced document like the banlist —
     *    the rogue's *content* is read as the current state and re-published under an
     *    authorized signature. That launders the attack: an unauthorized empty banlist
     *    becomes an owner-signed one the moment the owner bans anybody else.
     *
     * Armada's writers chain off `folded.heads` (its `pickHead` gated pick) for exactly
     * this reason; [ConcordCommunityState.authorizedHeads] is the same notion here.
     */
    private fun headOf(
        current: List<ControlEdition>,
        communityId: ByteArray,
        entityId: ByteArray,
        owner: HexKey,
        floors: Map<String, EntityFloor> = emptyMap(),
    ): ControlEdition? = ConcordCommunityState.authorizedHeads(current, communityId, owner, floors)[entityId.toHexKey()]?.known

    /** version/prevHash to chain onto the current head of [entityId], or a genesis at version 1 (CORD-04 §1). */
    private fun versioning(head: ControlEdition?): Pair<Long, ByteArray?> = if (head != null) (head.version + 1) to head.hash else 1L to null

    private suspend fun wrap(
        actor: NostrSigner,
        controlPlane: ControlPlaneKeys,
        communityId: ByteArray,
        kind: ControlEntityKind,
        entityId: ByteArray,
        head: ControlEdition?,
        content: String,
        current: List<ControlEdition>,
        createdAt: Long,
        citation: AuthorityCitation?,
        owner: HexKey,
    ): Event {
        val (version, prevHash) = versioning(head)
        val vac = citation ?: AuthorityCitations.forActor(current, communityId, owner, actor.pubKey)
        val rumor = ControlEditionBuilder.rumor(actor.pubKey, kind, entityId, version, prevHash, content, createdAt, vac)
        return ConcordStreamEnvelope.wrap(rumor, controlPlane, actor, encrypted = false, createdAt = createdAt)
    }

    /**
     * Writes [value] as the next edition of [entityId]: laid over the current authorized head's
     * content, so every field the head carries that [serializer] does not model survives the edit
     * (CORD-02 §6 — renaming never wipes another client's `custom` or a newer protocol field),
     * chained onto that head and cited.
     */
    private suspend fun <T> edit(
        actor: NostrSigner,
        controlPlane: ControlPlaneKeys,
        communityId: ByteArray,
        kind: ControlEntityKind,
        entityId: ByteArray,
        serializer: KSerializer<T>,
        value: T,
        current: List<ControlEdition>,
        createdAt: Long,
        citation: AuthorityCitation?,
        owner: HexKey,
        floors: Map<String, EntityFloor> = emptyMap(),
    ): Event {
        val head = headOf(current, communityId, entityId, owner, floors)
        val content = ConcordJson.encodePreserving(serializer, value, head?.content)
        return wrap(actor, controlPlane, communityId, kind, entityId, head, content, current, createdAt, citation, owner)
    }

    /**
     * Defines (or updates) a role. [roleId] is the role's stable 32-byte entity id
     * — generate one for a new role and reuse it to edit or [RoleEntity.deleted] it. The
     * content always carries it as `role_id` (CORD-04 §2), which the reference client requires.
     */
    suspend fun defineRole(
        actor: NostrSigner,
        controlPlane: ControlPlaneKeys,
        communityId: ByteArray,
        roleId: ByteArray,
        role: RoleEntity,
        current: List<ControlEdition>,
        createdAt: Long,
        citation: AuthorityCitation? = null,
        owner: HexKey,
    ): Event {
        require(ConcordLimits.nameFits(role.name)) { "role name exceeds ${ConcordLimits.NAME_MAX_BYTES} bytes" }
        // CORD-04 §3: position 0 is the owner's alone — refuse here rather than have every reader drop it.
        require(role.deleted || role.position >= 1) { "role position must be 1 or greater (position 0 is the owner's)" }
        val stamped = role.copy(roleId = roleId.toHexKey())
        return edit(actor, controlPlane, communityId, ControlEntityKind.ROLE, roleId, RoleEntity.serializer(), stamped, current, createdAt, citation, owner)
    }

    /**
     * Defines (or updates) a channel (CORD-03/04, `vsk=2`). [channelId] is the channel's stable
     * 32-byte entity id — generate one for a new channel and reuse it to rename, flip its
     * private/voice flags, or [ChannelEntity.deleted] it (terminal; the id is never reused).
     * Honored at fold only when [actor] holds MANAGE_CHANNELS (or is the owner).
     */
    suspend fun defineChannel(
        actor: NostrSigner,
        controlPlane: ControlPlaneKeys,
        communityId: ByteArray,
        channelId: ByteArray,
        channel: ChannelEntity,
        current: List<ControlEdition>,
        createdAt: Long,
        citation: AuthorityCitation? = null,
        owner: HexKey,
    ): Event {
        // Every reader drops an edition naming an empty or over-cap Channel (CORD-03 §2), so
        // refuse to mint one rather than publish an edition nobody will honor.
        require(channel.hasValidName()) { "Channel name must be 1..${ChannelEntity.NAME_MAX_BYTES} UTF-8 bytes" }
        return edit(actor, controlPlane, communityId, ControlEntityKind.CHANNEL, channelId, ChannelEntity.serializer(), channel, current, createdAt, citation, owner)
    }

    /**
     * Sets the community's disappearing-messages timer (CORD-08 §1) to [secs] seconds, or turns it
     * off when null. It is a metadata edition like any other — same chain, same MANAGE_METADATA
     * gate — laid over the folded [standing] metadata so nothing else changes.
     */
    suspend fun setMessageExpiration(
        actor: NostrSigner,
        controlPlane: ControlPlaneKeys,
        communityId: ByteArray,
        standing: MetadataEntity,
        secs: Long?,
        current: List<ControlEdition>,
        createdAt: Long,
        citation: AuthorityCitation? = null,
        owner: HexKey,
        floors: Map<String, EntityFloor> = emptyMap(),
    ): Event = editMetadata(actor, controlPlane, communityId, standing.withMessageExpiration(secs), current, createdAt, citation, owner, floors)

    /**
     * Replaces the community metadata (name / icon / description / relays). The
     * metadata entity id is the community id itself (as in genesis), so this chains
     * the next version onto the metadata head. Honored at fold only when [actor]
     * holds MANAGE_METADATA (or is the owner), and only within the CORD-02 §6 caps.
     */
    suspend fun editMetadata(
        actor: NostrSigner,
        controlPlane: ControlPlaneKeys,
        communityId: ByteArray,
        metadata: MetadataEntity,
        current: List<ControlEdition>,
        createdAt: Long,
        citation: AuthorityCitation? = null,
        owner: HexKey,
        floors: Map<String, EntityFloor> = emptyMap(),
    ): Event {
        require(ConcordLimits.nameFits(metadata.name)) { "community name exceeds ${ConcordLimits.NAME_MAX_BYTES} bytes" }
        require(ConcordLimits.descriptionFits(metadata.description)) { "description exceeds ${ConcordLimits.DESCRIPTION_MAX_BYTES} bytes" }
        return edit(actor, controlPlane, communityId, ControlEntityKind.METADATA, communityId, MetadataEntity.serializer(), metadata, current, createdAt, citation, owner, floors)
    }

    /**
     * Grants [member] exactly [roleIds] (replaces their prior grant). Empty list revokes all roles.
     *
     * A Grant that first makes its member **staff** must deliver the current
     * `control_root` in the same edition (CORD-04 §3): pass [controlWrap] built with
     * [ControlRootWrap.build] for the current epoch. A current staffer may also
     * re-issue a Grant with a fresh wrap to re-deliver (a lost key, a superseded
     * head). Leave null for a non-staff grant, a revoke, or a legacy community.
     */
    suspend fun grant(
        actor: NostrSigner,
        controlPlane: ControlPlaneKeys,
        communityId: ByteArray,
        member: HexKey,
        roleIds: List<String>,
        current: List<ControlEdition>,
        createdAt: Long,
        citation: AuthorityCitation? = null,
        owner: HexKey,
        controlWrap: String? = null,
    ): Event {
        require(roleIds.size <= ConcordLimits.MAX_ROLES_PER_MEMBER) { "a member holds at most ${ConcordLimits.MAX_ROLES_PER_MEMBER} roles" }
        val entityId = ConcordKeyDerivation.grantCoordinate(communityId, member.hexToByteArray())
        val grant = GrantEntity(member = member, roleIds = roleIds, controlWrap = controlWrap)
        return edit(actor, controlPlane, communityId, ControlEntityKind.GRANT, entityId, GrantEntity.serializer(), grant, current, createdAt, citation, owner)
    }

    /**
     * [grant], deciding the staff delivery for the caller: when [roleIds] hands the
     * member any Control-writing bit ([ConcordPermissions.STAFF_BITS]) and we hold the
     * [controlRoot] to deliver, the edition carries a `control_wrap` fresh for [epoch]
     * (CORD-04 §3). A non-staff grant, a revoke, a legacy community, or a granter who
     * does not hold the secret all produce a plain Grant.
     *
     * The role bits are read off the same authority-gated fold the readers use, so a
     * role a reader would drop never triggers a delivery — and a role we cannot resolve
     * yet (its edition unseen) conservatively doesn't either, which the spec's re-issue
     * path covers: any current staffer MAY re-issue a Grant with a fresh wrap.
     */
    suspend fun grantWithStaffDelivery(
        actor: NostrSigner,
        controlPlane: ControlPlaneKeys,
        communityId: ByteArray,
        member: HexKey,
        roleIds: List<String>,
        current: List<ControlEdition>,
        createdAt: Long,
        citation: AuthorityCitation? = null,
        owner: HexKey,
        controlRoot: ByteArray?,
        epoch: Long,
    ): Event {
        val wrap =
            if (controlRoot != null && makesStaff(roleIds, current, communityId, owner)) {
                ControlRootWrap.build(actor, member, epoch, controlRoot)
            } else {
                null
            }
        return grant(actor, controlPlane, communityId, member, roleIds, current, createdAt, citation, owner, wrap)
    }

    /** True when any of [roleIds] resolves to a role carrying a Control-writing bit (CORD-04 §3). */
    fun makesStaff(
        roleIds: List<String>,
        current: List<ControlEdition>,
        communityId: ByteArray,
        owner: HexKey,
    ): Boolean {
        if (roleIds.isEmpty()) return false
        val roles = AuthorityResolver.resolve(current, communityId, owner).roles()
        return roleIds.any { roles[it]?.permissionBits()?.hasAny(ConcordPermissions.STAFF_BITS) == true }
    }

    /**
     * Writes [content] — an already-serialized Pin List ([ConcordPins.serializePublic] /
     * [ConcordPins.serializeSealed]) — as the next edition of [channelId]'s Pin List (CORD-04 §7,
     * vsk 11, at `pins_locator(community_id, channel_id)`), chained onto [head].
     *
     * Unlike the other editors this takes the head explicitly rather than re-folding [current]: a
     * Pin List is replaced entire, so the edition MUST chain onto exactly the list the caller read
     * its entries from (§7 — never build from a list you could not read). [ConcordPinning] is the
     * caller that enforces that, the PIN_MESSAGES gate and the caps; this only mints the wrap.
     */
    suspend fun setPinList(
        actor: NostrSigner,
        controlPlane: ControlPlaneKeys,
        communityId: ByteArray,
        channelId: ByteArray,
        head: ControlEdition?,
        content: String,
        current: List<ControlEdition>,
        createdAt: Long,
        citation: AuthorityCitation? = null,
        owner: HexKey,
    ): Event {
        require(content.encodeToByteArray().size <= ConcordPins.MAX_CONTENT_BYTES) { "pin list exceeds ${ConcordPins.MAX_CONTENT_BYTES} bytes" }
        val entityId = ConcordKeyDerivation.pinsCoordinate(communityId, channelId)
        require(head == null || head.entityIdHex == entityId.toHexKey()) { "head is not this channel's Pin List" }
        return wrap(actor, controlPlane, communityId, ControlEntityKind.PIN_LIST, entityId, head, content, current, createdAt, citation, owner)
    }

    /**
     * Adds [member] to the banlist, written over the current folded head. Another admin's
     * concurrent edition at the same version may win the fold (CORD-04 §4); calling this again
     * after the refold re-applies the ban atop the winner — the spec's re-heal.
     */
    suspend fun ban(
        actor: NostrSigner,
        controlPlane: ControlPlaneKeys,
        communityId: ByteArray,
        member: HexKey,
        current: List<ControlEdition>,
        createdAt: Long,
        citation: AuthorityCitation? = null,
        owner: HexKey,
    ): Event = setBanlist(actor, controlPlane, communityId, currentBanned(current, communityId, owner) + member.lowercase(), current, createdAt, citation, owner)

    /** Removes [member] from the banlist. */
    suspend fun unban(
        actor: NostrSigner,
        controlPlane: ControlPlaneKeys,
        communityId: ByteArray,
        member: HexKey,
        current: List<ControlEdition>,
        createdAt: Long,
        citation: AuthorityCitation? = null,
        owner: HexKey,
    ): Event = setBanlist(actor, controlPlane, communityId, currentBanned(current, communityId, owner) - member.lowercase(), current, createdAt, citation, owner)

    /**
     * The current banlist (lowercase hex): the folded head's list.
     *
     * Read through the [AuthorityResolver] rather than by decoding the head's content directly, so
     * this is the *honored* banlist: the resolver drops entries whose signer did not outrank them
     * (§3's rank rule, enforced as a delta rule). Decoding the raw head instead would make every
     * ban/unban we author re-publish entries our own fold refuses — laundering an unauthorized ban
     * into a list signed by us.
     */
    fun currentBanned(
        current: List<ControlEdition>,
        communityId: ByteArray,
        owner: HexKey,
    ): Set<HexKey> = AuthorityResolver.resolve(current, communityId, owner).bannedMembers()

    /**
     * Publishes [actor]'s Invite Registry (CORD-05 §5, `vsk 8`) listing [linkSigners] — the
     * link-signer pubkeys of their live public links, locators only. The entity sits at
     * `invite_links_locator(community_id, actor)`, so it chains onto [actor]'s own registry head and
     * can never touch another creator's; it is honored at fold only while [actor] holds
     * CREATE_INVITE (or is the owner). Compute [linkSigners] with [ConcordInviteRegistry.nextLinks].
     */
    suspend fun setInviteRegistry(
        actor: NostrSigner,
        controlPlane: ControlPlaneKeys,
        communityId: ByteArray,
        linkSigners: Collection<HexKey>,
        current: List<ControlEdition>,
        createdAt: Long,
        citation: AuthorityCitation? = null,
        owner: HexKey,
        floors: Map<String, EntityFloor> = emptyMap(),
    ): Event {
        val entityId = ConcordInviteRegistry.coordinate(communityId, actor.pubKey)
        // Floor-aware: after a Refounding the honored head may be the prior epoch's (the anti-rollback
        // floor), and chaining onto the current epoch's editions alone would fork below it.
        val head = headOf(current, communityId, entityId, owner, floors)
        return wrap(actor, controlPlane, communityId, ControlEntityKind.INVITE_REGISTRY, entityId, head, ConcordInviteRegistry.encode(linkSigners), current, createdAt, citation, owner)
    }

    private suspend fun setBanlist(
        actor: NostrSigner,
        controlPlane: ControlPlaneKeys,
        communityId: ByteArray,
        banned: Set<HexKey>,
        current: List<ControlEdition>,
        createdAt: Long,
        citation: AuthorityCitation?,
        owner: HexKey,
    ): Event {
        val entityId = ConcordKeyDerivation.banlistCoordinate(communityId)
        val head = headOf(current, communityId, entityId, owner)
        val content = ConcordJson.instance.encodeToString(ListSerializer(String.serializer()), banned.sorted())
        return wrap(actor, controlPlane, communityId, ControlEntityKind.BANLIST, entityId, head, content, current, createdAt, citation, owner)
    }
}
