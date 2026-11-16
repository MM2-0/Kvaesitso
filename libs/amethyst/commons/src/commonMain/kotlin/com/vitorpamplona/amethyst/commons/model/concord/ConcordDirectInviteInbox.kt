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

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEntry
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityState
import com.vitorpamplona.quartz.concord.cord02Community.HeldRoot
import com.vitorpamplona.quartz.concord.cord02Community.ImagePointer
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordChannelKeyring
import com.vitorpamplona.quartz.concord.cord05Invites.CommunityInvite
import com.vitorpamplona.quartz.concord.cord05Invites.ConcordDirectInvite
import com.vitorpamplona.quartz.concord.cord05Invites.ConcordInviteVend
import com.vitorpamplona.quartz.concord.cord05Invites.OpenedDirectInvite
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.Rumor
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile

/**
 * One parked Direct Invite as the UI renders it (CORD-05 §6): who sent it (seal-verified), what it
 * opens (name/icon preview from the bundle), whether its `expires_at` has passed, and whether it
 * is a [catchUp] — a Private Channel key for a community this account already holds on the same
 * base, which accepting merges in without moving the base or announcing a new Join.
 */
@Immutable
class ConcordDirectInviteView(
    val opened: OpenedDirectInvite,
    val catchUp: Boolean,
    val expired: Boolean,
    /** For a [catchUp]: the ids of the Private Channels it would newly add (not every key it carries). */
    val newChannelIds: List<HexKey> = emptyList(),
    /** The rumor's `sentAt` clamped to the time it was ranked, so a future date buys no rank. */
    val clampedSentAt: Long = opened.sentAt,
    /** True when this account follows the sender: ranked above strangers. */
    val followedSender: Boolean = false,
) {
    val wrapId: HexKey get() = opened.wrapId
    val sender: HexKey get() = opened.sender
    val invite: CommunityInvite get() = opened.invite
    val communityId: HexKey get() = opened.invite.communityId
    val name: String get() = opened.invite.name
    val icon: ImagePointer? get() = opened.invite.icon

    /**
     * Names of the Private Channels a catch-up would newly add — [newChannelIds] only, named by
     * [foldedName] (the held community's folded channel name) when it knows the channel, else by the
     * bundle's own label.
     */
    fun newChannelNames(foldedName: (HexKey) -> String? = { null }): List<String> {
        val ids = newChannelIds.mapTo(HashSet()) { it.lowercase() }
        return opened.invite.channels
            .filter { it.id.lowercase() in ids }
            .map { foldedName(it.id)?.takeIf { name -> name.isNotBlank() } ?: it.name }
    }
}

/** What accepting a Direct Invite does; see [ConcordDirectInviteInbox.acceptPlan]. */
sealed interface DirectInviteAcceptPlan {
    /** `expires_at` has passed: the preview renders, joining refuses. */
    data object Expired : DirectInviteAcceptPlan

    /** A community we don't hold: run the shared join path. */
    data object Join : DirectInviteAcceptPlan

    /**
     * A held community whose fold still bans us, re-invited at a newer epoch (see [ConcordDirectInviteInbox.isReadmission]):
     * run the shared join path over the held entry, which re-checks the ban at the new epoch.
     */
    data object Readmit : DirectInviteAcceptPlan

    /**
     * A held community: store [entry] — the held one plus the newly granted Private Channel keys
     * ([channelIds]). A writer re-applies [channelIds] to the entry it reads inside the List write
     * ([ConcordInviteVend.adoptCatchUp] with `only`), never [entry] itself, which is a snapshot.
     */
    class CatchUp(
        val entry: ConcordCommunityListEntry,
        val channelIds: List<HexKey>,
    ) : DirectInviteAcceptPlan

    /** A held community the bundle adds nothing to (or can't: a different base, or dissolved). */
    data object NothingNew : DirectInviteAcceptPlan

    /** The held community's roster bans us. */
    data object Banned : DirectInviteAcceptPlan

    /** The held community's roster isn't folded yet, so the ban verdict is unknown: wait. */
    data object RosterNotLoaded : DirectInviteAcceptPlan
}

/**
 * The Direct Invite inbox (CORD-05 §6) — headless, shared by the app and `amy`.
 *
 * Wraps arrive from anywhere — the app's gift-wrap subscription (the NIP-17 inbox, which honours an
 * untagged invite all the same, or its `{"kinds":[1059],"#p":[me],"#k":["3313"]}` invite-only filter
 * ([com.vitorpamplona.amethyst.commons.actions.ConcordActions.directInvitesFilter]) when NIP-17 is
 * off), a periodic sweep of the stock Concord relays with that filter, or `amy`'s one-shot drain — and
 * are [offer]ed here.
 * The inbox opens each wrap once (two NIP-44 decrypts; none more when the DM pipeline already
 * unsealed it, [offerRumor]), dedupes by wrap id, drops a wrap whose NIP-40 `expiration` has passed,
 * validates the bundle exactly like a fetched one, and parks it in [pending]. **Nothing** else
 * happens: no relay connection, no icon fetch, no Join, until the user accepts (the caller's join
 * path) or [decline]s.
 *
 * A wrap is written off ([seen]) only on a definitive outcome — opened, not an invite for us, or
 * expired. A signer that could not answer (timed out, busy, not approved) leaves it to be retried by
 * the next delivery or sweep.
 *
 * Bounded: at most [MAX_PENDING] invites are parked; past it the lowest-ranked one goes (a sender
 * [isFollowed] outranks a stranger, then newer outranks older). Invites from senders [isHidden]
 * (muted or blocked) are never parked.
 *
 * Declined wrap ids are remembered ([declined], restorable via [restoreDeclined], at most
 * [DECLINED_CAP]) so a re-delivered wrap never resurfaces. [newestWrapCreatedAt] is the sweep cursor;
 * query from [since], which rewinds it by NIP-59's two-day backdate window.
 */
class ConcordDirectInviteInbox(
    private val signer: NostrSigner,
    private val isHidden: (HexKey) -> Boolean = { false },
    private val isFollowed: (HexKey) -> Boolean = { false },
) {
    private val mutex = Mutex()

    /** Wrap ids with a definitive outcome this session (opened, refused, or expired), oldest first. */
    private val seen = LinkedHashSet<HexKey>()

    /** Wrap ids being opened right now, so a concurrent delivery of the same wrap is not decrypted twice. */
    private val inFlight = HashSet<HexKey>()

    private val _pending = MutableStateFlow<Map<HexKey, OpenedDirectInvite>>(emptyMap())

    /** Parked invites by wrap id, as opened. See [visible] for what a UI should show. */
    val pending: StateFlow<Map<HexKey, OpenedDirectInvite>> = _pending.asStateFlow()

    private val _declined = MutableStateFlow<Set<HexKey>>(emptySet())

    /** Wrap ids the user declined, oldest first; persisted by the front end so they stay declined across restarts. */
    val declined: StateFlow<Set<HexKey>> = _declined.asStateFlow()

    /** The newest wrap `created_at` offered so far (the sweep cursor), or null on a cold inbox. */
    @Volatile
    var newestWrapCreatedAt: Long? = null
        private set

    /** The `since` for the next sweep: the cursor rewound by the backdate window (null = everything). */
    fun since(): Long? = ConcordDirectInvite.inboxSince(newestWrapCreatedAt)

    /**
     * Replaces the declined set — used to restore it from disk at startup — keeping the newest
     * [DECLINED_CAP]. Drops any pending one. Serialized with [offer] so a restore can't race a park.
     */
    suspend fun restoreDeclined(wrapIds: Set<HexKey>) {
        mutex.withLock {
            _declined.value = bounded(wrapIds)
            _pending.update { current -> current.filterKeys { it !in wrapIds } }
        }
    }

    /**
     * Considers one kind-1059 [wrap] addressed to us. Returns the parked invite (new or already
     * pending), or null when it isn't one: not a direct invite for us, a forgery, an invalid
     * bundle, an expired handoff, a hidden sender, a wrap the user already declined — or a signer
     * that could not answer now (retried on the next offer). Never throws but for cancellation.
     */
    suspend fun offer(
        wrap: Event,
        nowSecs: Long = TimeUtils.now(),
    ): OpenedDirectInvite? = admit(wrap, nowSecs) { ConcordDirectInvite.openOrRetry(wrap, signer) }

    /**
     * [offer] for a pipeline that already peeled [wrap] down to its kind-13 [seal] (the NIP-17
     * giftwrap inbox). [wrap] only lends its id, `created_at` and tags, so a content-stripped copy
     * is fine; the seal is re-opened with the anti-spoofing check the generic unseal skips.
     */
    suspend fun offerSeal(
        wrap: Event,
        seal: Event,
        nowSecs: Long = TimeUtils.now(),
    ): OpenedDirectInvite? = admit(wrap, nowSecs) { ConcordDirectInvite.openSealOrRetry(wrap.id, seal, signer) }

    /**
     * [offerSeal] for a pipeline that already decrypted [seal] into [rumor] (the rumor as the seal
     * carries it, its claimed author intact — [SealEvent.unsealRumorThrowing]): validated without
     * any further decrypt.
     */
    suspend fun offerRumor(
        wrap: Event,
        seal: Event,
        rumor: Rumor,
        nowSecs: Long = TimeUtils.now(),
    ): OpenedDirectInvite? = admit(wrap, nowSecs) { ConcordDirectInvite.openRumor(wrap.id, seal, rumor) }

    /**
     * Records [wrapId] as definitively not an invite for us (it failed to open for a reason no retry
     * changes, or opened to something else), so a sweep that fetches it again skips the decrypt.
     */
    suspend fun markNotInvite(wrapId: HexKey) {
        mutex.withLock { if (wrapId !in _pending.value) remember(wrapId) }
    }

    private suspend fun admit(
        wrap: Event,
        nowSecs: Long,
        open: suspend () -> OpenedDirectInvite?,
    ): OpenedDirectInvite? {
        if (wrap.kind != GiftWrapEvent.KIND) return null
        mutex.withLock {
            // The cursor only advances to a time that has happened (plus the skew allowance): a
            // future-dated wrap would otherwise push `since` past every invite sent until then.
            val stamp = minOf(wrap.createdAt, nowSecs + FUTURE_SKEW_SECS)
            val newest = newestWrapCreatedAt
            if (newest == null || stamp > newest) newestWrapCreatedAt = stamp
            _pending.value[wrap.id]?.let { return it }
            if (wrap.id in _declined.value || wrap.id in seen || wrap.id in inFlight) return null
            inFlight.add(wrap.id)
        }
        try {
            // An expired handoff is never decrypted or surfaced (NIP-40 on the wrap mirrors expires_at).
            if (ConcordDirectInvite.isWrapExpired(wrap, nowSecs)) {
                mutex.withLock { remember(wrap.id) }
                return null
            }
            val opened =
                try {
                    open()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // The signer could not answer now: not written off, so the next offer retries it.
                    return null
                }
            mutex.withLock {
                remember(wrap.id)
                if (opened == null || wrap.id in _declined.value) return null
                // Muted or blocked senders never reach the inbox.
                if (isHidden(opened.sender)) return null
                _pending.update { park(it, opened, nowSecs) }
            }
            return _pending.value[wrap.id]
        } finally {
            mutex.withLock { inFlight.remove(wrap.id) }
        }
    }

    /** [current] plus [opened], shedding the lowest-ranked invite past [MAX_PENDING]. */
    private fun park(
        current: Map<HexKey, OpenedDirectInvite>,
        opened: OpenedDirectInvite,
        nowSecs: Long,
    ): Map<HexKey, OpenedDirectInvite> {
        val next = current + (opened.wrapId to opened)
        if (next.size <= MAX_PENDING) return next
        val rank = compareBy<OpenedDirectInvite>({ isFollowed(it.sender) }, { clampedSentAt(it, nowSecs) }, { it.wrapId })
        val drop = next.values.minWithOrNull(rank) ?: return next
        return next - drop.wrapId
    }

    /** The parked invite behind [wrapId], if any. */
    fun get(wrapId: HexKey): OpenedDirectInvite? = _pending.value[wrapId.lowercase()] ?: _pending.value[wrapId]

    /** Discards [wrapId] for good (CORD-05 §6 "declining means discarding them"). False if not pending. */
    fun decline(wrapId: HexKey): Boolean {
        val id = get(wrapId)?.wrapId ?: return false
        _pending.update { it - id }
        _declined.update { bounded(it + id) }
        return true
    }

    /** Drops [wrapId] after it was accepted; this session will not re-park it. */
    fun resolve(wrapId: HexKey) {
        _pending.update { it - wrapId }
    }

    private fun remember(wrapId: HexKey) {
        if (seen.size >= SEEN_CAP) {
            val drop = seen.take(SEEN_CAP / 2)
            seen.removeAll(drop.toSet())
        }
        seen.add(wrapId)
    }

    companion object {
        /** Cap on remembered wrap ids; the oldest half is shed past it (a sweep re-dedupes deeper). */
        const val SEEN_CAP = 4096

        /** Cap on parked invites (the reference client's bound): a flood can't grow the inbox without end. */
        const val MAX_PENDING = 256

        /** Cap on remembered declines, newest kept: a declined wrap older than that has long expired or been buried. */
        const val DECLINED_CAP = 4096

        /** Clock skew tolerated on a wrap's `created_at` before it stops moving the sweep cursor. */
        const val FUTURE_SKEW_SECS = 15 * 60L

        private fun bounded(ids: Set<HexKey>): Set<HexKey> = if (ids.size <= DECLINED_CAP) ids else ids.toList().takeLast(DECLINED_CAP).toCollection(LinkedHashSet())

        /** [opened]'s `sentAt` (the sender's word) clamped to [nowSecs]: a future date buys no rank. */
        fun clampedSentAt(
            opened: OpenedDirectInvite,
            nowSecs: Long,
        ): Long = minOf(opened.sentAt, nowSecs)

        /**
         * What accepting [opened] should do (CORD-05 §6), given the community entry this account
         * already [held] (if any) and its folded [heldState]:
         *  - past `expires_at` → [DirectInviteAcceptPlan.Expired] ("`expires_at` refuses a late join");
         *  - not held → [DirectInviteAcceptPlan.Join] (the shared join path, which still ban-gates
         *    against the community's own Control Plane);
         *  - held on the SAME base with new Private Channel keys → [DirectInviteAcceptPlan.CatchUp],
         *    the held entry with only those keys ADDED — never moving the base, never replacing a
         *    held key (Armada `catchUpChannelIds`) — and only from a sender who is staff in the held
         *    fold, for channels it knows as live Private Channels
         *    ([ConcordInviteVend.admissibleCatchUpIds]); refused when the held roster bans [me], and
         *    while it isn't folded ([DirectInviteAcceptPlan.RosterNotLoaded]);
         *  - held otherwise (nothing new, a different base, dissolved) → [DirectInviteAcceptPlan.NothingNew].
         */
        fun acceptPlan(
            opened: OpenedDirectInvite,
            held: ConcordCommunityListEntry?,
            heldState: ConcordCommunityState?,
            me: HexKey,
            nowMs: Long = TimeUtils.nowMillis(),
        ): DirectInviteAcceptPlan {
            if (opened.isExpired(nowMs)) return DirectInviteAcceptPlan.Expired
            if (held == null) return DirectInviteAcceptPlan.Join
            if (isReadmission(held, heldState, opened, me)) return DirectInviteAcceptPlan.Readmit
            if (ConcordInviteVend.catchUpChannelIds(held, opened.invite).isEmpty()) return DirectInviteAcceptPlan.NothingNew
            if (heldState == null) return DirectInviteAcceptPlan.RosterNotLoaded
            // Death wins every race (CORD-02 §9): a dissolved community takes no new keys.
            if (heldState.dissolved) return DirectInviteAcceptPlan.NothingNew
            if (heldState.authority.isBanned(me)) return DirectInviteAcceptPlan.Banned
            val ids = ConcordInviteVend.admissibleCatchUpIds(held, opened.invite, heldState.authority, heldState.privateChannelIds, opened.sender)
            if (ids.isEmpty()) return DirectInviteAcceptPlan.NothingNew
            val adopted = ConcordInviteVend.adoptCatchUp(held, opened.invite, ids) ?: return DirectInviteAcceptPlan.NothingNew
            return DirectInviteAcceptPlan.CatchUp(adopted, ids)
        }

        /**
         * True when [invite] readmits a member [heldState] still bans: a ban in a Private community
         * Refounds, so an owner who later unbans and re-invites us can only do it at a newer epoch.
         * Armada drops a banned community from the list, so there its re-invite is a plain one; we
         * keep the banned community (read-only), and without this the re-invite was hidden and
         * could never be accepted. A banned member has no live membership a bundle could hijack
         * (the reason a bundle may never move a held base, CORD-06 §2), and the join re-checks the
         * ban against the NEW epoch's roster, failing closed.
         *
         * A bundle's `community_root` is not bound to its id, so anyone could mint a "newer epoch"
         * under a root of their own and, on one tap, replace the held base (and, with a huge epoch,
         * block every genuine readmission after it). So, as for a catch-up, only a sender who is
         * staff in the held fold (seal-verified) may readmit us, into the same owner's community.
         */
        fun isReadmission(
            held: ConcordCommunityListEntry,
            heldState: ConcordCommunityState?,
            opened: OpenedDirectInvite,
            me: HexKey,
        ): Boolean =
            heldState != null &&
                !heldState.dissolved &&
                heldState.authority.isBanned(me) &&
                opened.invite.communityId.equals(held.id, ignoreCase = true) &&
                opened.invite.owner.equals(held.owner, ignoreCase = true) &&
                opened.invite.rootEpoch > held.rootEpoch &&
                heldState.authority.isStaff(opened.sender) &&
                !heldState.authority.isBanned(opened.sender)

        /**
         * The List entry a readmission writes: [fresh]'s base (the new root, epoch, Control Plane key,
         * relays, name and join time) on top of everything [held] knew. Kept from [held]: every root
         * it held plus the one it is leaving (pre-ban history), its `seed` and entry extras such as
         * `channel_cuts` (so a cut key never comes back), and its private channel keys, each moved to
         * the key [fresh] delivers through [ConcordChannelKeyring.withChannelKey], which keeps the one
         * it replaces in `priors`. Built fresh, a readmission lost all of that.
         *
         * Apply it to the entry the List holds inside the write, never to a snapshot from before
         * the join's suspensions.
         */
        fun readmittedEntry(
            held: ConcordCommunityListEntry,
            fresh: ConcordCommunityListEntry,
        ): ConcordCommunityListEntry {
            val base =
                ConcordCommunityListEntry(
                    id = held.id,
                    owner = held.owner,
                    ownerSalt = held.ownerSalt,
                    root = fresh.root,
                    rootEpoch = fresh.rootEpoch,
                    controlPk = fresh.controlPk,
                    // A staff write key belongs to its epoch; the new one arrives with a Grant.
                    controlRoot = null,
                    heldRoots = (held.heldRoots + HeldRoot(held.rootEpoch, held.root, held.controlPk, held.controlRoot)).distinctBy { it.epoch to it.key.lowercase() },
                    privateChannels = held.privateChannels,
                    relays = fresh.relays.ifEmpty { held.relays },
                    name = fresh.name.ifBlank { held.name },
                    addedAt = fresh.addedAt,
                    inviteRef = fresh.inviteRef ?: held.inviteRef,
                    residue = held.residue,
                )
            return fresh.privateChannels.fold(base) { entry, key -> ConcordChannelKeyring.withChannelKey(entry, key) ?: entry }
        }

        /**
         * What a UI shows out of [pending], given the communities this account already holds
         * ([joined]) and the ones it left ([removedAt], community id → the Community List
         * tombstone's `removed_at` in unix ms): followed senders first, then newest first, with
         *  - an invite for a community already held on the SAME base that carries a Private Channel
         *    key it lacks kept as a [ConcordDirectInviteView.catchUp];
         *  - any other invite for a held community (nothing new, or a different base — which may
         *    never move the held one) hidden;
         *  - an invite sent at or before the user left that community hidden (it would otherwise
         *    resurface right after leaving; a fresh re-invite still shows — Armada `tombstonedAt`);
         *  - invites from [isHidden] (muted/blocked) senders hidden;
         *  - one invite per community and sender (newest clamped `sentAt`, ties by wrap id), so a
         *    future-dated invite can only ever shadow its own sender's; catch-ups keyed by their
         *    channel set too since each may vend a key no other wrap carries (Armada
         *    `dedupeParkedInvites`). `sentAt` is the sender's word, so it is clamped to now for both
         *    ordering and the tombstone check.
         */
        fun visible(
            pending: Collection<OpenedDirectInvite>,
            joined: List<ConcordCommunityListEntry>,
            nowMs: Long = TimeUtils.nowMillis(),
            removedAt: Map<String, Long> = emptyMap(),
            isFollowed: (HexKey) -> Boolean = { false },
            isHidden: (HexKey) -> Boolean = { false },
            heldStateOf: (communityId: HexKey) -> ConcordCommunityState? = { null },
            me: HexKey? = null,
        ): List<ConcordDirectInviteView> {
            val nowSecs = nowMs / 1000
            val heldById = joined.associateBy { it.id.lowercase() }
            val removedById = removedAt.mapKeys { it.key.lowercase() }
            val byKey = LinkedHashMap<String, ConcordDirectInviteView>()
            for (opened in pending) {
                if (isHidden(opened.sender)) continue
                val communityId = opened.invite.communityId.lowercase()
                val sentAt = clampedSentAt(opened, nowSecs)
                val buriedAt = removedById[communityId]
                if (buriedAt != null && sentAt * 1000 <= buriedAt) continue
                val held = heldById[communityId]
                // For a held community whose fold is in, only what accepting would actually adopt:
                // a catch-up from a non-staff sender, for channels the fold doesn't know as Private,
                // or into a dissolved community is refused by [acceptPlan], so it is not offered.
                val heldState = held?.let { heldStateOf(it.id) }
                val readmit = held != null && me != null && isReadmission(held, heldState, opened, me)
                val newChannels =
                    when {
                        held == null || readmit -> emptyList()
                        heldState == null -> ConcordInviteVend.catchUpChannelIds(held, opened.invite)
                        heldState.dissolved -> emptyList()
                        else -> ConcordInviteVend.admissibleCatchUpIds(held, opened.invite, heldState.authority, heldState.privateChannelIds, opened.sender)
                    }
                if (held != null && !readmit && newChannels.isEmpty()) continue
                val catchUp = held != null && !readmit
                val base = communityId + "|" + opened.sender.lowercase()
                val key = if (catchUp) base + "|" + newChannels.sorted().joinToString(",") else base
                val view = ConcordDirectInviteView(opened, catchUp, opened.isExpired(nowMs), newChannels.toList(), sentAt, isFollowed(opened.sender))
                val existing = byKey[key]
                if (existing == null ||
                    sentAt > existing.clampedSentAt ||
                    (sentAt == existing.clampedSentAt && opened.wrapId < existing.opened.wrapId)
                ) {
                    byKey[key] = view
                }
            }
            return byKey.values.sortedWith(
                compareByDescending<ConcordDirectInviteView> { it.followedSender }
                    .thenByDescending { it.clampedSentAt }
                    .thenBy { it.wrapId },
            )
        }
    }
}
