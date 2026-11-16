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

import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEntry
import com.vitorpamplona.quartz.concord.cord02Community.HeldRoot
import com.vitorpamplona.quartz.concord.cord04Roles.AuthorityCitation
import com.vitorpamplona.quartz.concord.cord04Roles.AuthorityResolver
import com.vitorpamplona.quartz.concord.cord04Roles.ConcordJson
import com.vitorpamplona.quartz.concord.cord04Roles.ConcordPermissions
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEdition
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEntityKind
import com.vitorpamplona.quartz.concord.cord04Roles.ControlRootWrap
import com.vitorpamplona.quartz.concord.cord04Roles.GrantEntity
import com.vitorpamplona.quartz.concord.cord06Rekey.ConcordRefounding
import com.vitorpamplona.quartz.concord.cord06Rekey.ConcordRotationAuthority
import com.vitorpamplona.quartz.concord.cord06Rekey.ReceivedRefounding
import com.vitorpamplona.quartz.concord.crypto.ConcordKeyDerivation
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner

/**
 * The **receive** half of Concord's key lifecycle, as pure functions: what a client must adopt
 * when a Grant hands it the Control Plane write key (CORD-04 §3), and how an entry is rewritten
 * when a base rotation moves the community to a new epoch (CORD-06).
 *
 * These lived only in Amethyst's `AccountConcordActions`, which meant a headless client (`amy`)
 * could hold a rank it could never write under, and could not follow a Refounding at all. The
 * logic is platform-agnostic — the only Android-shaped parts were the persistence and publish,
 * which stay with the caller. Every function here decides *what* to adopt and returns it; the
 * caller owns storing it and republishing the kind-13302 list.
 *
 * Everything fails closed: an undecryptable, mis-epoched or non-deriving delivery yields null,
 * never a partially-adopted entry.
 */
object ConcordReceive {
    /**
     * The `control_root` a staff-making Grant delivered to the account behind [recipientSigner],
     * or null when there is nothing to adopt (CORD-04 §3).
     *
     * Gated three ways, each of which fails closed:
     *  - only a Grant **our own fold honors** can deliver, so [authority] must already seat us as
     *    staff — a rogue cannot feed us a key by minting an edition nobody accepts;
     *  - the wrap must open under the granter↔member pairwise key, and name [entry]'s epoch,
     *    because compaction re-wraps a Grant head verbatim across Refoundings and a folded head
     *    can legitimately carry a wrap minted for a prior epoch;
     *  - the secret must derive to exactly the `control_pk` we already hold, or adopting it would
     *    split us off from the plane's readers.
     *
     * Returns null (not an error) when the entry already holds the secret, holds no `control_pk`
     * to check against (a legacy pre-split community), or when we are not staff.
     */
    suspend fun deliveredControlRoot(
        entry: ConcordCommunityListEntry,
        editions: List<ControlEdition>,
        authority: AuthorityResolver,
        recipientSigner: NostrSigner,
    ): HexKey? {
        val heldControlPk = entry.controlPk
        if (entry.controlRoot != null || heldControlPk == null) return null
        val me = recipientSigner.pubKey.lowercase()
        if (!authority.isStaff(me)) return null

        val myGrantCoordinate =
            ConcordKeyDerivation
                .grantCoordinate(entry.id.hexToByteArray(), me.hexToByteArray())
                .toHexKey()

        return editions
            .filter { it.entityKind == ControlEntityKind.GRANT && it.entityIdHex == myGrantCoordinate }
            // Newest first: a re-issued Grant (a lost key, a head superseded before we fetched it)
            // carries the fresher wrap.
            .sortedByDescending { it.version }
            .firstNotNullOfOrNull { edition ->
                val wrap = ConcordJson.decodeOrNull<GrantEntity>(edition.content)?.controlWrap ?: return@firstNotNullOfOrNull null
                val opened = ControlRootWrap.openOrNull(wrap, recipientSigner, edition.author) ?: return@firstNotNullOfOrNull null
                if (opened.epoch != entry.rootEpoch) return@firstNotNullOfOrNull null
                if (!ControlRootWrap.derivesTo(opened.controlRoot, entry.id.hexToByteArray(), entry.rootEpoch, heldControlPk)) return@firstNotNullOfOrNull null
                opened.controlRoot.toHexKey()
            }
    }

    /**
     * Whether [rotator] was allowed to launch the base rotation that [entry] is being moved by
     * (CORD-06). `hasPermission`, never `effectivePermissions`: the latter ignores the banlist, so
     * a banned BAN-holder could rotate the whole community out from under it.
     */
    fun isAuthorizedRotator(
        authority: AuthorityResolver,
        rotator: HexKey,
    ): Boolean = authority.isOwner(rotator) || authority.hasPermission(rotator, ConcordPermissions.BAN)

    /**
     * Whether a received base rotation may be adopted (CORD-06 §3 "Authority"): its rotator holds
     * BAN (or is the owner) in our fold, AND it cites the Grant it acts under (`vac`) at a version
     * our fold has synced — so a just-demoted admin's rotation is never honored by a client that
     * lags the demotion, and a rotation citing a Grant we have not seen yet waits for it. The owner
     * cites nothing. [editions] are the current epoch's Control editions the citation is checked
     * against.
     */
    fun isHonoredRotation(
        entry: ConcordCommunityListEntry,
        editions: Collection<ControlEdition>,
        authority: AuthorityResolver,
        received: ReceivedRefounding,
    ): Boolean {
        if (!isAuthorizedRotator(authority, received.rotator)) return false
        val heads = ConcordRotationAuthority.headsOf(editions, entry.id, entry.owner)
        return ConcordRotationAuthority.citationSatisfied(entry.id, received.rotator, entry.owner, received.authority, heads)
    }

    /**
     * The `vac` citation [actor] stamps on a rotation it launches (CORD-06 §3): their own Grant's
     * head in our fold, or null for the owner (who cites nothing).
     */
    fun rotationCitation(
        entry: ConcordCommunityListEntry,
        editions: Collection<ControlEdition>,
        actor: HexKey,
    ): AuthorityCitation? = ConcordRotationAuthority.citationFor(entry.id, actor, entry.owner, ConcordRotationAuthority.headsOf(editions, entry.id, entry.owner))

    /**
     * The down-only same-epoch heal (CORD-06 §3): [entry] holds a root at its current epoch, and
     * [sibling] is a rotation to that same epoch (from the same prior root) that we did not adopt.
     * Returns the entry moved onto the sibling when its root is **strictly lower** — the losing
     * (higher) root we held is kept as a held root of the same epoch, so the messages sent into
     * that fork stay readable — or null when the sibling does not win.
     *
     * The losing root keeps no control material: its Control Plane is the losing fork's
     * compaction, not the community's ([ConcordRefounding.canonicalHeldRoots] never folds it).
     */
    fun withHealedRoot(
        entry: ConcordCommunityListEntry,
        sibling: ReceivedRefounding,
    ): ConcordCommunityListEntry? {
        if (sibling.newEpoch != entry.rootEpoch) return null
        if (!ConcordRefounding.healsTo(entry.root.hexToByteArray(), sibling.newRoot)) return null
        return ConcordCommunityListEntry(
            id = entry.id,
            owner = entry.owner,
            ownerSalt = entry.ownerSalt,
            root = sibling.newRoot.toHexKey(),
            rootEpoch = entry.rootEpoch,
            // The control pair is the winner's blob's, never inherited from the losing fork.
            controlPk = sibling.newControlPk?.toHexKey(),
            controlRoot = sibling.newControlRoot?.toHexKey(),
            heldRoots = (entry.heldRoots + HeldRoot(entry.rootEpoch, entry.root)).distinctBy { it.epoch to it.key.lowercase() },
            privateChannels = entry.privateChannels,
            relays = entry.relays,
            name = entry.name,
            addedAt = entry.addedAt,
            inviteRef = entry.inviteRef,
            excludedAtEpoch = entry.excludedAtEpoch,
            residue = entry.residue,
        )
    }

    /**
     * The entry that results from adopting a base rotation to [newEpoch] — a pure rewrite, so the
     * caller can diff, persist and publish it however its platform does.
     *
     * The epoch being left is banked in `heldRoots` **with the address it was folded at**, because
     * a split epoch's Control address can never be re-derived, only remembered (CORD-02 §2) — that
     * banked address is what keeps the anti-rollback floor rebuildable. A rotation that delivered
     * no control material is a legacy pre-split one (CORD-06 §3): the new epoch folds at the legacy
     * address, and the stale prior-epoch values must NOT be carried into it. `inviteRef` survives,
     * or the *next* Refounding we are left out of becomes unrecoverable; `residue` survives, or we
     * delete another client's unknown keys on every rekey.
     */
    fun withAdoptedRoot(
        entry: ConcordCommunityListEntry,
        newRoot: ByteArray,
        newEpoch: Long,
        newControlPk: ByteArray? = null,
        newControlRoot: ByteArray? = null,
    ): ConcordCommunityListEntry =
        ConcordCommunityListEntry(
            id = entry.id,
            owner = entry.owner,
            ownerSalt = entry.ownerSalt,
            root = newRoot.toHexKey(),
            rootEpoch = newEpoch,
            controlPk = newControlPk?.toHexKey(),
            controlRoot = newControlRoot?.toHexKey(),
            // Keyed by (epoch, key), not epoch alone: a healed race leaves a losing fork's root at the
            // same epoch, kept so its messages stay readable (CORD-06 §3).
            heldRoots = (entry.heldRoots + HeldRoot(entry.rootEpoch, entry.root, entry.controlPk, entry.controlRoot)).distinctBy { it.epoch to it.key.lowercase() },
            privateChannels = entry.privateChannels,
            relays = entry.relays,
            name = entry.name,
            addedAt = entry.addedAt,
            inviteRef = entry.inviteRef,
            excludedAtEpoch = entry.excludedAtEpoch,
            residue = entry.residue,
        )
}
