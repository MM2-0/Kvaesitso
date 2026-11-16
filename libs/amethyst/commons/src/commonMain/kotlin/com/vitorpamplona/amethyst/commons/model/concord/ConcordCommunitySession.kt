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

import com.vitorpamplona.amethyst.commons.actions.ChannelPlane
import com.vitorpamplona.amethyst.commons.actions.ConcordActions
import com.vitorpamplona.amethyst.commons.actions.ConcordChannelPins
import com.vitorpamplona.amethyst.commons.actions.ConcordLocalEdit
import com.vitorpamplona.amethyst.commons.actions.ConcordPinSource
import com.vitorpamplona.amethyst.commons.actions.ConcordPinVerifier
import com.vitorpamplona.amethyst.commons.actions.ConcordPinning
import com.vitorpamplona.amethyst.commons.actions.ConcordPrivateChannels
import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEntry
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityState
import com.vitorpamplona.quartz.concord.cord02Community.ConcordDissolution
import com.vitorpamplona.quartz.concord.cord02Community.Guestbook
import com.vitorpamplona.quartz.concord.cord02Community.GuestbookAction
import com.vitorpamplona.quartz.concord.cord02Community.GuestbookEntry
import com.vitorpamplona.quartz.concord.cord03Channels.ChannelChat
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordDisappearing
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordWebxdc
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEdition
import com.vitorpamplona.quartz.concord.cord04Roles.EditionFold
import com.vitorpamplona.quartz.concord.cord04Roles.EntityFloor
import com.vitorpamplona.quartz.concord.cord04Roles.pins.ConcordPinLists
import com.vitorpamplona.quartz.concord.cord04Roles.pins.ConcordPins
import com.vitorpamplona.quartz.concord.cord06Rekey.ConcordRefounding
import com.vitorpamplona.quartz.concord.crypto.ControlPlaneKeys
import com.vitorpamplona.quartz.concord.crypto.GroupKey
import com.vitorpamplona.quartz.concord.envelope.ConcordStreamEnvelope
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip40Expiration.isExpirationBefore
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlin.concurrent.Volatile

/**
 * A validated inner chat rumor emitted by a session: its parent [communityId] and
 * [channelIdHex], plus the typed [rumor] (kind 9 message, 1111 reply, 7 reaction,
 * 5 delete, …). The sink lands it in a store keyed by rumor id so the normal
 * reaction/reply/delete/OTS/zap machinery wires up automatically.
 */
typealias ConcordRumorSink = (communityId: HexKey, channelIdHex: HexKey, rumor: Event, seenOnRelays: Set<NormalizedRelayUrl>) -> Unit

/**
 * A disappearing Chat rumor (CORD-08) a session tracks: the [rumorId] carried by wrap [wrapId] on
 * [channelIdHex], gone at [expiresAt] (unix seconds). Returned by a sweep once expired, so the
 * store drops both notes.
 */
data class ExpiredConcordRumor(
    val channelIdHex: HexKey,
    val wrapId: HexKey,
    val rumorId: HexKey,
    val expiresAt: Long,
    /** The URLs of the rumor's encrypted attachments, whose decryption keys go with it (CORD-08 §3). */
    val attachmentUrls: List<String> = emptyList(),
)

/**
 * The result of feeding one wrap to a session's [ConcordCommunitySession.ingest]. It separates
 * "was it ours" from "did it change structure", so only structure-changing wraps bump the session
 * revision (and thus re-derive plane subscriptions). Landing every chat message as a revision bump
 * re-REQs every plane per message and gets the client rate-limited off the relays.
 */
enum class ConcordIngestOutcome {
    /** The wrap is not addressed to any plane this session knows. Keep routing it elsewhere. */
    NOT_MINE,

    /** Ours and applied, but nothing the subscription set / folded structure depends on changed —
     *  a chat/reaction/reply/delete message landing, or a duplicate wrap. Must NOT bump the revision. */
    NON_STRUCTURAL,

    /** Ours and re-folded the Control Plane. The fold republishes [ConcordCommunitySession.state],
     *  so the session's own state watcher is what bumps the revision — and, because the folded state
     *  compares by value, only when the fold actually *changed* something. A control wrap that folds
     *  to an identical state (a prior-epoch wrap that doesn't move the anti-rollback floor, a role
     *  edition that touches nothing we subscribe on) therefore costs no bump at all. The manager must
     *  NOT bump on this outcome as well, or every control wrap counts twice. */
    STRUCTURAL_FOLD,

    /** Ours and changed structure *without* touching [ConcordCommunitySession.state]: a guestbook
     *  membership change (which republishes `members`) or a buffered base-rekey. No state watcher
     *  covers these, so the manager bumps the revision directly. */
    STRUCTURAL,
    ;

    /** True when the wrap belonged to this session (whether or not it changed structure). */
    val claimed get() = this != NOT_MINE
}

/**
 * The live read-model of one joined Concord community, driven by inbound stream
 * wraps fed via [ingest].
 *
 * It holds the community's [entry] (with secrets), derives the Control Plane
 * address up front, and — as control wraps arrive — re-folds the Control Plane
 * into [state] (metadata + channels + authority) and re-derives each channel's
 * Chat Plane address so subsequent channel wraps decrypt. **It does not store
 * messages itself:** each validated chat rumor is handed to [onRumor], whose
 * platform-side sink lands it in the shared event store (`LocalCache`) as a real
 * Note attached to the channel — so previews, threading, reactions and zaps reuse
 * the same machinery every other chat does. Re-emitting is safe because the sink
 * dedups by rumor id. This is the stateful counterpart to the pure
 * [ConcordActions]/[ConcordPlaneRegistry] helpers.
 */
class ConcordCommunitySession(
    entry: ConcordCommunityListEntry,
    val myPubKey: HexKey,
    /** How many chat wraps each channel's buffer keeps; see [MAX_CHANNEL_WRAPS]. */
    private val maxChannelWraps: Int = MAX_CHANNEL_WRAPS,
    private val onRumor: ConcordRumorSink = { _, _, _, _ -> },
) {
    /**
     * The joined-list entry this session projects. Replaced in place — only ever by
     * [adoptControlMaterial], and only within one epoch — because the Control Plane
     * write key can arrive long after the session was built (CORD-04 §3). A change
     * that moves the *planes* (a Refounding) rebuilds the session instead.
     *
     * Volatile because the ingest path, the UI and the adopting drain are different
     * threads: the write happens under [lock], but readers take it unsynchronized.
     */
    @Volatile
    var entry: ConcordCommunityListEntry = entry
        private set

    private val root = entry.root.hexToByteArray()
    private val communityIdBytes = entry.id.hexToByteArray()

    /**
     * The Control Plane as this account holds it (CORD-02 §5): split when the entry
     * carries a `control_pk` (plus the write key when this account is staff and
     * holds the `control_root`), legacy single-key otherwise.
     */
    @Volatile
    private var controlKeys: ControlPlaneKeys = ConcordActions.controlPlaneKeysFor(entry)

    /** The Guestbook Plane at this epoch — where member join/leave motions ride (CORD-02 §5). */
    private val guestbookKey: GroupKey = ConcordActions.guestbookPlane(root, communityIdBytes, entry.rootEpoch)

    /**
     * The base-rotation rekey address for the *next* epoch (CORD-06 §2). A member
     * precomputes it from the root they already hold so an inbound Refounding — which
     * delivers the next root here — is received live rather than only on re-open.
     */
    private val nextBaseRekeyKey: GroupKey = ConcordActions.nextBaseRekeyPlane(root, communityIdBytes, entry.rootEpoch)

    /**
     * The rekey address the rotation INTO this epoch rode on (from the prior held root), or null
     * for a joiner who holds no prior root. A racing sibling rotation to this same epoch lands
     * here; the app drains it for the down-only heal (CORD-06 §3).
     */
    private val siblingBaseRekeyKey: GroupKey? = ConcordActions.siblingBaseRekeyPlane(entry)

    /**
     * The dissolution tombstone address (CORD-02 §9): derived from the community id alone, so it
     * is the same for every epoch and every member past or present.
     */
    private val dissolvedKey: GroupKey = ConcordDissolution.planeKey(entry.id)

    /**
     * Set once a valid owner tombstone bound to this community arrives at [dissolvedAddress]. One
     * way: there is no un-dissolve, so a later fold can never clear it.
     */
    @Volatile
    private var dissolved = false

    /** The Control Plane stream address to subscribe to (known from the entry alone). */
    val controlPlaneAddress: HexKey get() = controlKeys.address

    /** The dissolution tombstone stream address to subscribe to (known from the community id alone). */
    val dissolvedAddress: HexKey get() = dissolvedKey.publicKeyHex

    /** The Guestbook Plane stream address to subscribe to (known from the entry alone). */
    val guestbookAddress: HexKey get() = guestbookKey.publicKeyHex

    /** The next-epoch base-rekey stream address to watch for an inbound Refounding. */
    val nextBaseRekeyAddress: HexKey get() = nextBaseRekeyKey.publicKeyHex

    /** The current epoch's own base-rekey address (see [siblingBaseRekeyKey]), or null. */
    val siblingBaseRekeyAddress: HexKey? get() = siblingBaseRekeyKey?.publicKeyHex

    /**
     * The Control Plane of every **prior** epoch we still hold a root for (address ->
     * key + epoch), newest-held first and bounded like the channel backfill.
     *
     * This is the anti-rollback memory. A CORD-06 Refounding re-wraps one edition per
     * entity at the new epoch and the *rotator* chooses which one, so it can serve v1
     * of a chain that had already reached v2 — restoring a revoked role, clearing a
     * banlist — with every signature genuine. Folding the epochs we still hold roots
     * for gives us the [EntityFloor] each entity must connect to, and because
     * `heldRoots` is already persisted in the kind-13302 community list, that memory
     * survives a process restart without any new storage.
     */
    private val historicalControlKeys: Map<HexKey, Pair<ControlPlaneKeys, Long>> =
        // Losing-fork roots of a healed race carry no Control Plane of the community's (CORD-06 §3).
        ConcordRefounding
            .canonicalHeldRoots(entry.heldRoots)
            .filter { it.epoch < entry.rootEpoch }
            .sortedByDescending { it.epoch }
            .take(ConcordActions.MAX_BACKFILL_EPOCHS)
            .mapNotNull { held ->
                // A held split epoch's address is the banked control_pk (held, never derivable);
                // a held legacy epoch derives its address from the root as it always did.
                val keys = runCatching { ConcordActions.controlPlaneKeys(held.key.hexToByteArray(), communityIdBytes, held.epoch, held.controlPk, held.controlRoot) }.getOrNull() ?: return@mapNotNull null
                keys.address to (keys to held.epoch)
            }.toMap()

    /** The prior-epoch Control Plane addresses to subscribe to, so the rollback floor can be rebuilt. */
    fun historicalControlPlaneAddresses(): Set<HexKey> = historicalControlKeys.keys

    private val lock = KmpLock()

    // Deduped inbound wraps.
    private val controlWraps = LinkedHashMap<HexKey, Event>()

    /**
     * Decrypted control editions memoized by wrap id.
     *
     * Both [refold] and [controlFloorsLocked] fold their WHOLE buffer on every inbound control
     * wrap, and turning a wrap into an edition is a NIP-44 open + parse. Re-deriving them each
     * time made a cold-boot backfill quadratic in decryptions — one measured boot did ~8.6k opens
     * to ingest 93 control wraps for a single community. Memoizing makes it one open per wrap.
     *
     * Wrap ids are unique and a wrap only ever belongs to one plane (it is routed by `pubKey`), so
     * a single id-keyed map is safe across the current and prior-epoch Control Planes even though
     * they open under different keys. A wrap that fails to open caches `null` so it is not retried
     * on every subsequent fold. The wrap buffers are only ever added to, so this tracks their
     * lifetime exactly and needs no separate eviction.
     */
    private val editionByWrapId = HashMap<HexKey, ControlEdition?>()

    /**
     * Guestbook wraps opened into entries, memoized by wrap id — the guestbook analogue of
     * [editionByWrapId]. [refoldGuestbook] runs on *every* arriving guestbook wrap and re-projects
     * the whole buffer, so without this the nth arrival re-opens all n wraps and a boot costs
     * O(n^2) envelope opens (each = two NIP-44 decrypts + two signature verifies). Measured on a
     * cold start: 6,229 opens over 448 distinct wraps, ~all of the app's NIP-44 traffic.
     *
     * Safe to key on wrap id alone: [guestbookKey] is derived once at construction from the
     * session's epoch and never rotates in place (a rekey builds a new session).
     */
    private val guestbookEntryByWrapId = HashMap<HexKey, GuestbookEntry?>()

    /**
     * Envelope opens [refoldGuestbook] actually performed (cache misses). Exposed so a test can
     * assert the fold stays linear in arrivals; a regression to re-opening the buffer shows up here
     * as O(n^2) long before it shows up as a slow boot.
     */
    internal var guestbookOpens = 0
        private set

    // Prior-epoch Control Plane address -> (wrapId -> wrap). Kept apart from [controlWraps]: these
    // never join the live fold, they only produce the anti-rollback floor.
    private val historicalControlWraps = HashMap<HexKey, LinkedHashMap<HexKey, Event>>()

    // channelIdHex -> (wrapId -> wrap), in delivery order. Holds each channel's [maxChannelWraps] most
    // recently delivered wraps (see [trimChannelWrapsLocked]): every wrap a busy channel ever
    // delivered used to stay for the session's whole life. An evicted wrap delivered again is opened
    // again.
    private val channelWrapsById = HashMap<HexKey, LinkedHashMap<HexKey, Event>>()

    // Chat rumor id -> the id of the wrap that carried it, filled as each wrap is emitted. A pin
    // proves its message with the ORIGINAL kind-20013 seal (CORD-04 §7), which only the wrap holds,
    // so pinning reopens that wrap rather than re-deriving anything from the stored rumor.
    private val wrapIdByRumorId = HashMap<HexKey, HexKey>()

    // The reverse of [wrapIdByRumorId], so a wrap leaving the buffer takes its index entry along.
    private val rumorIdByWrapId = HashMap<HexKey, HexKey>()

    /** Pin-entry verdicts memoized by entry identity (CORD-04 §7 Weight). */
    private val pinVerifier = ConcordPinVerifier()
    private val guestbookWraps = LinkedHashMap<HexKey, Event>()
    private val baseRekeyWraps = LinkedHashMap<HexKey, Event>()
    private val siblingRekeyWraps = LinkedHashMap<HexKey, Event>()

    // Channel-rekey addresses (CORD-06 §2) for each held Private Channel's next epochs, under the
    // current root and the prior one (a Refounding seals its channel rekeys under the prior root,
    // CORD-06 §3) -> key. Re-derived whenever the held channel keys change, since each adoption
    // moves the window forward.
    @Volatile
    private var channelRekeyKeys: Map<HexKey, GroupKey> = ConcordPrivateChannels.watchKeys(entry)
    private val channelRekeyWraps = LinkedHashMap<HexKey, Event>()

    // Current channel plane pubkey -> plane (channel id, key, bound epoch), refreshed on each control
    // re-fold. A Public Channel's plane derives from the root at the root epoch; a Private one's from
    // its held channel key at the channel epoch (CORD-03 §1). A Private Channel we hold no key for has
    // no entry at all: it is neither subscribed, read, nor written.
    private var channelKeysByAddress = HashMap<HexKey, ChannelPlane>()

    // Older channel plane pubkey -> plane, for history: a Public Channel's plane under each held
    // prior root (a CORD-06 Refounding rotates the root per epoch) plus its private-era plane when a
    // channel key is held. Subscribed, AUTHed and decrypted alongside the current planes.
    private var historicalChannelKeysByAddress = HashMap<HexKey, ChannelPlane>()

    // The held Private Channel keys the current channel planes were derived from, so a later list
    // entry carrying different keys re-derives them (see [adoptPrivateChannels]).
    private var derivedPrivateKeys = privateKeySet(entry)

    private val _state = MutableStateFlow<ConcordCommunityState?>(null)
    val state: StateFlow<ConcordCommunityState?> = _state

    private val _controlDrained = MutableStateFlow(false)

    /**
     * True once this session's current Control Plane has been swept whole at least once — every
     * relay page drained and ingested ([markControlDrained]) — so [state] is a fold of the full plane
     * rather than of whatever the live subscription delivered first.
     *
     * Until then the fold may be partial (a cropped first page, an edition below the live `since`
     * cursor), and a write built on it can erase what it never saw: a Pin List edition replaces the
     * list entire (CORD-04 §7: never write from a list the fold was not served), a metadata edition
     * minted without the head resets the name and relays, a registry edit chains onto a stale head.
     * Writers refuse while this is false; readers may show a partial fold but must not call an
     * absent entity "none". One way: a session never un-drains (a Refounding builds a new session).
     */
    val controlDrained: StateFlow<Boolean> = _controlDrained

    /** Records that the whole current Control Plane has been paged in and ingested (see [controlDrained]). */
    fun markControlDrained() {
        _controlDrained.value = true
    }

    /**
     * The fold a Control Plane write may be built from: [state] once the plane has drained, else
     * null. Every writer that lays its edition over a folded head (metadata, timer, registry, pins)
     * goes through this, the owner included — the owner may act before the fold, but not write over
     * a head they have not been served.
     */
    fun foldForWrite(): ConcordCommunityState? = if (_controlDrained.value) _state.value else null

    // The anti-rollback floors the last fold used, so a writer can chain onto the same floor-aware head.
    @Volatile
    private var lastFloors: Map<String, EntityFloor> = emptyMap()

    /**
     * The per-entity anti-rollback floors (from the prior epochs' Control Planes) the current fold
     * honors — what a writer passes so it chains onto the head readers fold to, not the head of the
     * current epoch's editions alone.
     */
    fun controlFloors(): Map<String, EntityFloor> = lastFloors

    private val _pinHeads = MutableStateFlow<Map<HexKey, ControlEdition>>(emptyMap())

    /**
     * The authorized head of each folded Channel's Pin List (CORD-04 §7), by channel id, re-derived
     * on every control fold. Kept apart from [state] because a pin edition changes no field of the
     * folded community, so [state] would not re-emit for it.
     */
    val pinHeads: StateFlow<Map<HexKey, ControlEdition>> = _pinHeads

    private val _members = MutableStateFlow<Set<HexKey>>(emptySet())

    /** The live Guestbook membership set (self-signed joins minus later leaves and honored Kicks). */
    val members: StateFlow<Set<HexKey>> = _members

    private val _guestbook = MutableStateFlow<Map<HexKey, GuestbookEntry>>(emptyMap())

    /**
     * The coalesced Guestbook (CORD-02 §5): each npub's latest Join, Leave or honored Kick, by
     * lowercase pubkey. Kicks are judged against the current roster, so this re-derives on every
     * control fold as well as on every Guestbook arrival.
     */
    val guestbook: StateFlow<Map<HexKey, GuestbookEntry>> = _guestbook

    // Author (lowercase) -> the newest CORD-02 §4 ms of a message of theirs we decrypted: observation
    // only counts *forward* of their latest Leave or Kick (CORD-02 §5).
    private val observedAtMs = HashMap<HexKey, Long>()

    private val _observedAuthors = MutableStateFlow<Set<HexKey>>(emptySet())

    /**
     * Everyone whose decrypted channel message this session has seen (lowercase hex). CORD-02 §5:
     * "an author seen publishing is observably present, auto-included even if their Join never
     * arrived." Most members never send a Guestbook Join, so this is the bulk of the real roster.
     */
    val observedAuthors: StateFlow<Set<HexKey>> = _observedAuthors

    private var memberHarvestStarted = false

    /**
     * Returns true exactly once — for the caller that should run the one-shot full-history member-roster
     * harvest (page every channel's history back to a bounded window so ingest can fold the older posters
     * into [observedAuthors]). Idempotent, so re-opening the members screen never re-pages.
     */
    fun beginMemberHarvest(): Boolean =
        lock.withLock {
            if (memberHarvestStarted) {
                false
            } else {
                memberHarvestStarted = true
                true
            }
        }

    // channelIdHex -> (other member pubkey -> createdAt secs of their latest typing heartbeat).
    private val typingByChannel = HashMap<HexKey, HashMap<HexKey, Long>>()
    private val _typing = MutableStateFlow<Map<HexKey, Map<HexKey, Long>>>(emptyMap())

    /**
     * The latest typing-heartbeat time (createdAt secs) per channel per *other* member (kind 23311,
     * CORD-03). The UI applies its own freshness window and shows those still typing.
     */
    val typing: StateFlow<Map<HexKey, Map<HexKey, Long>>> = _typing

    /**
     * The community's full membership (lowercase hex): everyone who announced on the Guestbook or
     * was seen publishing a channel message ([observedAuthors]), plus the owner and every
     * role-holder, minus the banned. Best-effort — a member who joined without a Guestbook motion,
     * holds no role, and never posted is invisible (key possession leaves no trace), so this is a
     * floor, not a census.
     */
    fun allMembers(): Set<HexKey> {
        val s = _state.value
        val roster = if (s != null) s.authority.roleHolders() + s.ownerPubKey.lowercase() else emptySet()
        val banned = s?.authority?.bannedMembers().orEmpty()
        return (_members.value + _observedAuthors.value + roster) - banned - departedMembers().keys
    }

    /**
     * Members the Guestbook shows as departed (lowercase hex -> their winning Leave or Kick): their
     * latest motion is a Leave or an honored Kick and we have seen nothing of theirs since
     * (CORD-02 §5: observation only counts forward). The owner never departs by a Kick.
     */
    fun departedMembers(): Map<HexKey, GuestbookEntry> {
        val coalesced = _guestbook.value
        if (coalesced.isEmpty()) return emptyMap()
        val owner = entry.owner.lowercase()
        return lock.withLock {
            coalesced.filter { (pubkey, motion) ->
                motion.action != GuestbookAction.JOIN &&
                    pubkey != owner &&
                    (observedAtMs[pubkey] ?: Long.MIN_VALUE) <= motion.ms
            }
        }
    }

    /**
     * The honored Kick naming this account that postdates this membership (CORD-04 §6), or null.
     * A Kick older than the entry's `added_at` judged an earlier membership — a re-join (a later
     * Join, or a re-invite that re-added the entry) leaves it behind. Never for the owner.
     */
    fun kickedMe(): GuestbookEntry? = ConcordActions.honoredKickAgainst(_guestbook.value, myPubKey, entry.owner, entry.addedAt)

    /** The size of [allMembers] — the community's true (best-effort) member count. */
    fun memberCount(): Int = allMembers().size

    /**
     * Every Chat Plane address to subscribe to: one per folded channel at the current epoch, plus
     * each channel's prior-epoch planes we still hold a root for (pre-Refounding history).
     */
    fun channelAddresses(): Set<HexKey> = lock.withLock { channelKeysByAddress.keys + historicalChannelKeysByAddress.keys }

    /**
     * True when [address] is one of this community's plane addresses — Control (current or a prior
     * epoch we still hold), Guestbook, next-epoch rekey, or any folded Chat Plane.
     *
     * A membership test rather than a set to iterate, because the caller is the NIP-42 auth path
     * asking "whose room is this wrap for?" on every challenge: answering that from
     * [channelAddresses] + [historicalControlPlaneAddresses] allocates a fresh union per community
     * per question, where four map lookups do.
     */
    fun ownsPlane(address: HexKey): Boolean =
        address == controlPlaneAddress ||
            address == guestbookAddress ||
            address == nextBaseRekeyAddress ||
            address == siblingBaseRekeyAddress ||
            address == dissolvedAddress ||
            address in channelRekeyKeys ||
            address in historicalControlKeys ||
            lock.withLock { address in channelKeysByAddress || address in historicalChannelKeysByAddress }

    /** The Chat Plane stream address for [channelIdHex], once this community has folded that channel (else null). */
    fun channelPlaneAddress(channelIdHex: HexKey): HexKey? = lock.withLock { channelKeysByAddress.entries.firstOrNull { it.value.channelIdHex == channelIdHex }?.key }

    /**
     * The plane [channelIdHex] is written on now, or null when it is not folded yet or is a Private
     * Channel this account holds no key for (CORD-03 §1) — the caller must then refuse to post.
     */
    fun currentChannelPlane(channelIdHex: HexKey): ChannelPlane? = lock.withLock { channelKeysByAddress.values.firstOrNull { it.channelIdHex == channelIdHex } }

    /**
     * The plane of [channelIdHex] bound to [epoch] — current or historical — or null when this
     * account holds none. A delete of an older message goes back onto the plane that carried it.
     */
    fun channelPlaneFor(
        channelIdHex: HexKey,
        epoch: Long,
    ): ChannelPlane? =
        lock.withLock {
            channelKeysByAddress.values.firstOrNull { it.channelIdHex == channelIdHex && it.epoch == epoch }
                ?: historicalChannelKeysByAddress.values.firstOrNull { it.channelIdHex == channelIdHex && it.epoch == epoch }
        }

    /**
     * Every Chat Plane stream address for [channelIdHex] across epochs: the current one plus each
     * prior-epoch plane we hold a root for. Used by the history pager as the REQ `authors` set so a
     * single backward `until` sweep walks the channel's whole cross-Refounding timeline (older
     * messages have smaller `created_at` regardless of epoch), and "All caught up" means every epoch
     * is drained — not just the current one. Empty until the Control Plane folds the channel.
     */
    fun channelPlaneAddressesAllEpochs(channelIdHex: HexKey): List<HexKey> =
        lock.withLock {
            val current = channelKeysByAddress.entries.firstOrNull { it.value.channelIdHex == channelIdHex }?.key
            val historical = historicalChannelKeysByAddress.entries.filter { it.value.channelIdHex == channelIdHex }.map { it.key }
            (listOfNotNull(current) + historical)
        }

    /** The base-rotation rekey [GroupKey] a member opens an inbound Refounding under. */
    fun nextBaseRekeyKey(): GroupKey = nextBaseRekeyKey

    /** The buffered kind-3303 base-rotation wraps seen at [nextBaseRekeyAddress], for the account to drain. */
    fun pendingBaseRekeyWraps(): List<Event> = lock.withLock { baseRekeyWraps.values.toList() }

    /** The base-rekey [GroupKey] of the rotation into this epoch (sibling heal), or null. */
    fun siblingBaseRekeyKey(): GroupKey? = siblingBaseRekeyKey

    /** The buffered kind-3303 wraps seen at [siblingBaseRekeyAddress], for the account's heal drain. */
    fun pendingSiblingRekeyWraps(): List<Event> = lock.withLock { siblingRekeyWraps.values.toList() }

    /**
     * Every stream key whose kind-1059 wraps this session reads: the Control Plane plus
     * one per folded channel. These are the identities a NIP-42 relay must see the
     * connection authenticate as (kind 22242) to serve the wraps — a Concord wrap is
     * authored by the stream key and `p`-tagged to a throwaway ephemeral key, so the
     * member is neither author nor recipient and the relay refuses unless we AUTH as the
     * stream key itself.
     *
     * The Guestbook + next-epoch base-rekey planes ([auxStreamKeys]) are intentionally
     * NOT included here: mixing them into the shared control/channel AUTH set starved the
     * subscription on relays that gate a REQ on stream-key AUTH (control stopped folding,
     * channels went empty). They AUTH on their own isolated subscription instead.
     *
     * A **split** Control Plane epoch contributes a key only when this account is staff
     * (CORD-02 §2): a regular member holds the `control_pk` but not the secret behind it,
     * so it cannot answer an AUTH challenge as the plane — which is the write-restriction
     * working as designed, not a gap to paper over. A legacy epoch still contributes, its
     * member-held derivation being address and signer at once (CORD-02 §5).
     */
    fun streamKeys(): List<GroupKey> =
        lock.withLock {
            listOfNotNull(controlKeys.signer) +
                // Prior-epoch Control Planes: the anti-rollback floor is folded from them, so the
                // gated relays must serve their wraps too.
                historicalControlKeys.values.mapNotNull { it.first.signer } +
                channelKeysByAddress.values.map { it.key } +
                // Prior-epoch channel stream keys so the gated relays serve their older wraps too.
                historicalChannelKeysByAddress.values.map { it.key }
        }

    /**
     * The auxiliary plane keys (Guestbook, next base-rekey, and the CORD-02 §9 dissolution address)
     * for their own isolated AUTH.
     */
    fun auxStreamKeys(): List<GroupKey> = listOfNotNull(guestbookKey, nextBaseRekeyKey, dissolvedKey, siblingBaseRekeyKey) + channelRekeyKeys.values

    /** The channel-rekey addresses this session watches (CORD-06 §2), for the auxiliary subscription. */
    fun channelRekeyAddresses(): Set<HexKey> = channelRekeyKeys.keys

    /** The buffered kind-3303 wraps seen at [channelRekeyAddresses], for the account's channel-rekey drain. */
    fun pendingChannelRekeyWraps(): List<Event> = lock.withLock { channelRekeyWraps.values.toList() }

    /** The community's current Control Plane editions — the input a moderation edition chains onto. */
    fun controlEditions(): List<ControlEdition> = lock.withLock { editionsLocked(controlWraps.values.toList(), controlKeys) }

    /** The raw Control Plane wraps buffered so far — the input a Refounding compacts (CORD-06 §3). */
    fun controlPlaneWraps(): List<Event> = lock.withLock { controlWraps.values.toList() }

    /**
     * The Control Plane keys as this account holds them, for authoring moderation
     * editions. [ControlPlaneKeys.canWrite] is false for a regular member on a split
     * epoch (CORD-02 §2) — the caller must not attempt to publish an edition then.
     */
    fun controlPlaneKeys(): ControlPlaneKeys = lock.withLock { controlKeys }

    /**
     * Adopt Control Plane key material that arrived *after* this session was built, at
     * the same epoch: the `control_root` a staff-making Grant delivers (CORD-04 §3), or
     * a `control_pk` filled in by a same-epoch Community List merge (CORD-02 §8).
     *
     * Done in place rather than by rebuilding the session, because a rebuild would drop
     * the buffered Control Plane wraps and leave the community folded empty until every
     * wrap happened to be re-delivered. Nothing about the *plane* moves here: adoption is
     * gated on the secret deriving to exactly the `control_pk` already held (CORD-02 §5),
     * so the address, the read key, the buffered wraps and the subscription set are all
     * invariant — only [ControlPlaneKeys.signer] appears, flipping
     * [ControlPlaneKeys.canWrite] and adding the stream key to [streamKeys].
     *
     * Fails closed and returns false when [newEntry] is not the same community at the
     * same root and epoch, or when the material it carries would move the plane's
     * address — a caller must rebuild the session for that, never mutate it. Returns
     * false too when nothing changed, so the caller can skip a needless revision bump.
     */
    fun adoptControlMaterial(newEntry: ConcordCommunityListEntry): Boolean =
        lock.withLock {
            if (newEntry.id != entry.id || newEntry.root != entry.root || newEntry.rootEpoch != entry.rootEpoch) return@withLock false
            if (newEntry.controlPk == entry.controlPk && newEntry.controlRoot == entry.controlRoot) return@withLock false
            val newKeys = ConcordActions.controlPlaneKeysFor(newEntry)
            // The plane is where the buffered wraps already are. If the new material points
            // somewhere else, this is not an adoption — refuse and let the caller rebuild.
            if (newKeys.address != controlKeys.address) return@withLock false
            entry = newEntry
            controlKeys = newKeys
            true
        }

    /**
     * Adopt a change to the Private Channel keys the Community List carries for this same community,
     * root and epoch (a key delivered on grant, CORD-03 §1): the entry is swapped in place and the
     * channel planes re-derived, so a newly held Private Channel is subscribed, read and written on
     * its own plane without dropping the buffered Control Plane wraps a rebuild would lose.
     *
     * Returns false, changing nothing, when [newEntry] is not the same community at the same root,
     * epoch and Control Plane material (the caller rebuilds, or adopts that first), or when the held
     * channel keys did not change.
     */
    fun adoptPrivateChannels(newEntry: ConcordCommunityListEntry): Boolean {
        val changed =
            lock.withLock {
                val cur = entry
                if (newEntry.id != cur.id || newEntry.root != cur.root || newEntry.rootEpoch != cur.rootEpoch) return false
                if (newEntry.controlPk != cur.controlPk || newEntry.controlRoot != cur.controlRoot) return false
                // Compared with what the planes were derived from, not with [entry]: an adoption of
                // Control material may already have swapped in an entry carrying the new keys.
                if (privateKeySet(newEntry) == derivedPrivateKeys) return false
                entry = newEntry
                channelRekeyKeys = ConcordPrivateChannels.watchKeys(newEntry)
                true
            }
        // Nothing folded yet: the first control wrap derives the planes from the swapped-in entry.
        if (changed && lock.withLock { controlWraps.isNotEmpty() }) refold()
        return changed
    }

    /** This account's standing, from the current fold. */
    fun membership(): ConcordMembership {
        val s = _state.value ?: return ConcordMembership.MEMBER
        return ConcordMembership.of(s.authority, myPubKey)
    }

    /**
     * Ingests a stream [wrap]. If it belongs to this community's Control Plane it
     * re-folds; if it belongs to a known channel plane it re-projects that
     * channel's messages. The [ConcordIngestOutcome] tells the caller both whether
     * the wrap was ours and — crucially — whether it changed *structure* (a fold that
     * moves the subscription set / metadata) versus just landing a chat message. Only
     * a [ConcordIngestOutcome.STRUCTURAL] result should bump the session revision;
     * bumping on every message re-derives every plane's REQ per message and rate-limits
     * the relays (they close the plane subs mid-load, so channels appear empty).
     */
    fun ingest(
        wrap: Event,
        seenOnRelays: Set<NormalizedRelayUrl> = emptySet(),
    ): ConcordIngestOutcome {
        when (wrap.pubKey) {
            controlPlaneAddress -> {
                lock.withLock {
                    if (controlWraps.put(wrap.id, wrap) != null) return ConcordIngestOutcome.NON_STRUCTURAL // dup
                }
                refold()
                return ConcordIngestOutcome.STRUCTURAL_FOLD
            }
            guestbookAddress -> {
                lock.withLock {
                    if (guestbookWraps.put(wrap.id, wrap) != null) return ConcordIngestOutcome.NON_STRUCTURAL // dup
                }
                refoldGuestbook()
                return ConcordIngestOutcome.STRUCTURAL
            }
            dissolvedAddress -> {
                // Anyone holding the (public) community id can sign here, so only an owner-signed,
                // eid-bound tombstone counts (CORD-02 §9); everything else is noise we still claim.
                if (dissolved || !ConcordDissolution.isTombstoneWrap(wrap, entry.id, entry.owner)) return ConcordIngestOutcome.NON_STRUCTURAL
                lock.withLock {
                    dissolved = true
                    _state.value = _state.value?.withDissolved(true)
                }
                // The state watcher bumps the revision off the changed fold, as for a control wrap.
                return ConcordIngestOutcome.STRUCTURAL_FOLD
            }
            nextBaseRekeyAddress -> {
                // Buffer only — decrypting a base-rotation blob needs the account signer, so the
                // app layer drains [pendingBaseRekeyWraps] with it and authorizes the rotator. That
                // drain runs off the revision tick, so a buffered rekey must bump (rare — a rekey,
                // not a message).
                lock.withLock { baseRekeyWraps[wrap.id] = wrap }
                return ConcordIngestOutcome.STRUCTURAL
            }
            siblingBaseRekeyAddress -> {
                // Same as above for a racing rotation into THIS epoch (the down-only heal).
                lock.withLock { siblingRekeyWraps[wrap.id] = wrap }
                return ConcordIngestOutcome.STRUCTURAL
            }
            in channelRekeyKeys -> {
                // Buffer only, like the base rekeys: opening a blob takes the account signer, and the
                // rotator's authority is judged against the fold at drain time.
                lock.withLock {
                    if (channelRekeyWraps.put(wrap.id, wrap) != null) return ConcordIngestOutcome.NON_STRUCTURAL // dup
                }
                return ConcordIngestOutcome.STRUCTURAL
            }
            else -> {
                // A prior-epoch Control Plane wrap: buffer it and re-fold, so the anti-rollback
                // floor rises as the old epochs drain in. Structural — the floor can change the
                // folded state (and therefore the plane set) exactly like a live control wrap.
                if (wrap.pubKey in historicalControlKeys) {
                    lock.withLock {
                        val buffer = historicalControlWraps.getOrPut(wrap.pubKey) { LinkedHashMap() }
                        if (buffer.put(wrap.id, wrap) != null) return ConcordIngestOutcome.NON_STRUCTURAL // dup
                    }
                    refold()
                    return ConcordIngestOutcome.STRUCTURAL_FOLD
                }
                val current = lock.withLock { channelKeysByAddress[wrap.pubKey] }
                if (current != null) {
                    return ingestChannelWrap(wrap, current.channelIdHex, current.key, current.epoch, seenOnRelays)
                }
                // An older plane (pre-Refounding history, or a Public Channel's private era). Decrypt
                // with that plane's key and bind-check against its epoch. Keyed separately from the
                // current buffer so a re-fold never re-projects the historical ones.
                val historical = lock.withLock { historicalChannelKeysByAddress[wrap.pubKey] } ?: return ConcordIngestOutcome.NOT_MINE
                return ingestChannelWrap(wrap, historical.channelIdHex, historical.key, historical.epoch, seenOnRelays)
            }
        }
    }

    /** Shared channel-wrap ingest for any epoch: typing → typing state, else buffer-dedup + emit. */
    private fun ingestChannelWrap(
        wrap: Event,
        channelIdHex: HexKey,
        key: GroupKey,
        epoch: Long,
        seenOnRelays: Set<NormalizedRelayUrl>,
    ): ConcordIngestOutcome {
        // An ephemeral wrap on a channel plane is a transient signal (typing) — fold it into the
        // typing state, never the stored buffer or the Note sink. Typing is a current-epoch live
        // signal, so a prior-epoch ephemeral (there won't be any — old epochs are frozen) is harmless.
        if (wrap.kind == ConcordStreamEnvelope.KIND_WRAP_EPHEMERAL) {
            ingestTyping(wrap, channelIdHex, key, epoch)
            return ConcordIngestOutcome.NON_STRUCTURAL
        }
        // An expired wrap this session already swept, delivered again: ours, but never opened again.
        if (wasSwept(wrap.id)) return ConcordIngestOutcome.NON_STRUCTURAL
        val isNew =
            lock.withLock {
                val held = channelWrapsById.getOrPut(channelIdHex) { LinkedHashMap() }
                // A re-delivery moves the wrap to the newest end, as recently seen as a new one.
                val fresh = held.remove(wrap.id) == null
                held[wrap.id] = wrap
                if (fresh) trimChannelWrapsLocked(held)
                fresh
            }
        // Project only the newly-arrived wrap — the buffer's earlier wraps were already emitted when
        // they landed, so re-decrypting the whole history on every message would be O(history) per
        // message (quadratic over a channel's lifetime). A duplicate re-delivery (isNew == false) is a
        // no-op. A full-history sweep (member-roster harvest) relies on this staying O(1) per wrap.
        if (isNew) emitChannelRumors(channelIdHex, key, epoch, listOf(wrap), seenOnRelays)
        // A chat message lands in the feed via [onRumor] → LocalCache, independent of the revision; it
        // changes no plane address, so it must NOT bump (see the storm note above).
        return ConcordIngestOutcome.NON_STRUCTURAL
    }

    private fun ingestTyping(
        wrap: Event,
        channelIdHex: HexKey,
        key: GroupKey,
        epoch: Long,
    ) {
        val opened = ConcordStreamEnvelope.openOrNull(wrap, key) ?: return
        // The same Chat gate as a stored rumor: encrypted seal, strict binding, well-formed ms.
        val rumor = ChannelChat.acceptOpened(opened, channelIdHex, epoch) ?: return
        if (!ChannelChat.isTyping(rumor)) return
        val who = rumor.pubKey.lowercase()
        if (who == myPubKey.lowercase()) return // never show my own typing back to me
        // A banned member's messages are dropped everywhere, so their typing heartbeat must be too —
        // otherwise they sit in the "… is typing" row forever in a channel they cannot be heard in.
        if (_state.value?.authority?.isBanned(who) == true) return
        val now = TimeUtils.now()
        // Update the map and publish inside the lock so a concurrent heartbeat on another
        // channel can't publish an older snapshot last and drop this channel's typers.
        lock.withLock {
            val perChannel = typingByChannel.getOrPut(channelIdHex) { HashMap() }
            val prev = perChannel[who]
            // Clamp a peer's heartbeat to our clock: a wildly future-dated createdAt would never
            // fall out of the freshness window below and would block later real heartbeats.
            val stamp = minOf(rumor.createdAt, now)
            if (prev == null || stamp > prev) perChannel[who] = stamp
            perChannel.entries.retainAll { now - it.value <= TYPING_STALE_SECS }
            if (perChannel.isEmpty()) typingByChannel.remove(channelIdHex)
            _typing.value = typingByChannel.mapValues { it.value.toMap() }
        }
    }

    private fun refold() {
        // Read the buffer, fold, re-derive channel keys, and publish state atomically under the
        // lock so a concurrent control wrap can't publish a smaller fold last. Control editions
        // are rare (not per-message), so serializing the fold is cheap.
        val newChannels =
            lock.withLock {
                val wraps = controlWraps.values.toList()
                val editions = editionsLocked(wraps, controlKeys)
                val floors = controlFloorsLocked()
                lastFloors = floors
                val folded = ConcordCommunityState.fold(editions, communityIdBytes, entry.owner, floors)

                val prevAddresses = channelKeysByAddress.keys.toHashSet()
                val next = HashMap<HexKey, ChannelPlane>()
                // Re-derive the older planes for the same (epoch-invariant) channel ids, so older
                // history is subscribed/AUTHed/decrypted. Channels are known only after a fold, hence
                // derived here rather than up front.
                val historical = HashMap<HexKey, ChannelPlane>()
                for ((channelIdHex, channel) in folded.channels) {
                    val isPrivate = channel.definition.private
                    // Null for a Private Channel with no held key: never the root-derived plane.
                    ConcordActions.currentChannelPlane(entry, channelIdHex, isPrivate)?.let { next[it.key.publicKeyHex] = it }
                    for (plane in ConcordActions.historicalChannelPlanes(entry, channelIdHex, isPrivate)) {
                        historical[plane.key.publicKeyHex] = plane
                    }
                }
                channelKeysByAddress = next
                historicalChannelKeysByAddress = historical
                derivedPrivateKeys = privateKeySet(entry)

                _state.value = folded.withDissolved(dissolved)
                _pinHeads.value = ConcordPinLists.heads(editions, folded.authority, entry.id, folded.channels.keys, floors)
                next.filterKeys { it !in prevAddresses }.values.map { it.channelIdHex }
            }

        // Kicks are judged against the roster, so a fold can honor (or drop) a held Kick.
        refoldGuestbook()

        // Project only channels whose current plane is new (a first fold, or a plane that moved when a
        // Private Channel's key arrived). Existing planes' wraps were already emitted incrementally as
        // they arrived — re-projecting all channels on every control edition would be
        // O(channels × history) of redundant decryption.
        for (channelIdHex in newChannels) reprojectChannel(channelIdHex)
    }

    /**
     * [wraps] opened into editions through [editionByWrapId], so a wrap is only ever decrypted
     * once no matter how many folds it participates in. Caller must hold [lock].
     */
    private fun editionsLocked(
        wraps: Collection<Event>,
        planeKeys: ControlPlaneKeys,
    ): List<ControlEdition> =
        wraps.mapNotNull { wrap ->
            if (editionByWrapId.containsKey(wrap.id)) {
                editionByWrapId[wrap.id]
            } else {
                val edition = ConcordStreamEnvelope.openOrNull(wrap, planeKeys)?.let { ControlEdition.fromOpened(it) }
                editionByWrapId[wrap.id] = edition
                edition
            }
        }

    /**
     * The per-entity anti-rollback floor: the authority-gated heads of every prior epoch's
     * Control Plane we still hold a root for, folded **oldest epoch first** so each epoch is
     * itself anchored at the one before it and the floor only ever rises.
     *
     * The current epoch must then connect to these heads; an entity whose offered chain cannot
     * reach its floor keeps the state we last folded (see [EditionFold.admissible]) and the
     * refusal is warned. Empty for a fresh joiner (no held roots), which is exactly right — it
     * legitimately has no history and must still accept the compacted head as its baseline.
     *
     * Caller must hold [lock]; a fold reads the wrap buffers.
     */
    private fun controlFloorsLocked(): Map<String, EntityFloor> {
        if (historicalControlKeys.isEmpty()) return emptyMap()
        var floors = emptyMap<String, EntityFloor>()
        for ((address, keyAtEpoch) in historicalControlKeys.entries.sortedBy { it.value.second }) {
            val wraps = historicalControlWraps[address]?.values?.toList() ?: continue
            val editions = editionsLocked(wraps, keyAtEpoch.first)
            if (editions.isEmpty()) continue
            floors = ConcordCommunityState.authorizedHeads(editions, communityIdBytes, entry.owner, floors)
        }
        return floors
    }

    private fun refoldGuestbook() {
        lock.withLock {
            val entries =
                guestbookWraps.values.mapNotNull { wrap ->
                    if (guestbookEntryByWrapId.containsKey(wrap.id)) {
                        guestbookEntryByWrapId[wrap.id]
                    } else {
                        guestbookOpens++
                        ConcordActions.guestbookEntry(wrap, guestbookKey).also { guestbookEntryByWrapId[wrap.id] = it }
                    }
                }
            val coalesced = Guestbook.coalesce(entries, TimeUtils.nowMillis(), _state.value?.authority)
            _guestbook.value = coalesced
            _members.value = ConcordActions.joinedMembers(coalesced)
        }
    }

    /**
     * Keeps [held] at the [maxChannelWraps] most recently delivered wraps, dropping each evicted
     * wrap's rumor index entry with it. By delivery, not by created_at: what the user is looking at
     * is what arrived last, live or paged in by scrolling back, so it stays pinnable; created_at
     * order would drop every scrolled-back page the moment it landed. A pin or jump-to-context for
     * an evicted message finds no wrap here, as for any message this session never held.
     */
    private fun trimChannelWrapsLocked(held: LinkedHashMap<HexKey, Event>) {
        val oldestFirst = held.values.iterator()
        while (held.size > maxChannelWraps && oldestFirst.hasNext()) {
            val wrap = oldestFirst.next()
            oldestFirst.remove()
            val rumorId = rumorIdByWrapId.remove(wrap.id) ?: continue
            if (wrapIdByRumorId[rumorId] == wrap.id) wrapIdByRumorId.remove(rumorId)
        }
    }

    /** Re-decrypts and re-projects a channel's WHOLE wrap buffer at the current epoch. Only for a
     *  re-fold (keys may change). Prior-epoch wraps in the buffer simply won't open under the current
     *  key and are skipped — they were already emitted when they landed (the sink dedups by id). */
    private fun reprojectChannel(channelIdHex: HexKey) {
        val plane = currentChannelPlane(channelIdHex) ?: return
        val wraps = lock.withLock { channelWrapsById[channelIdHex]?.values?.toList() } ?: return
        emitChannelRumors(channelIdHex, plane.key, plane.epoch, wraps)
    }

    /**
     * Decrypts + validates [wraps] on [channelIdHex], hands each bound rumor to the sink (which dedups
     * by rumor id, so re-emitting is idempotent), and folds their authors into [observedAuthors] — every
     * author we decrypt is observably present (CORD-02 §5), a member even without a Guestbook Join.
     */
    private fun emitChannelRumors(
        channelIdHex: HexKey,
        key: GroupKey,
        epoch: Long,
        wraps: List<Event>,
        seenOnRelays: Set<NormalizedRelayUrl> = emptySet(),
    ) {
        val authors = HashSet<HexKey>()
        val now = TimeUtils.now()
        for (wrap in wraps) {
            val rumor = ConcordActions.openChannelRumorAnyExpiry(wrap, key, channelIdHex, epoch, ChannelChat.PLANE_KINDS) ?: continue
            // A WebXDC signal (kind 3310) rides the plane but is never a chat row: held apart for a
            // WebXDC host, never handed to the store, so it can't reach a feed, a preview or an
            // unread count. Its author is still observably present (CORD-02 §5).
            if (ConcordWebxdc.isWebxdc(rumor)) {
                if (!ConcordDisappearing.isExpired(rumor, now) && holdWebxdc(channelIdHex, rumor)) {
                    observe(rumor)
                    authors.add(rumor.pubKey.lowercase())
                }
                continue
            }
            // Pins reopen the carrying wrap to disclose this one message's keys (CORD-04 §7). Indexed
            // only while the buffer holds the wrap: one already trimmed away could never be reopened,
            // and its entry would never be removed.
            lock.withLock {
                if (channelWrapsById[channelIdHex]?.containsKey(wrap.id) == true) {
                    wrapIdByRumorId[rumor.id] = wrap.id
                    rumorIdByWrapId[wrap.id] = rumor.id
                }
            }
            // CORD-08 §3: only the rumor's own tag counts. A rumor carrying one is remembered so the
            // sweep purges it (and its wrap) when it expires; one already expired is refused here —
            // never handed to the store — and queued for the next sweep so its wrap goes too.
            val expiresAt = ConcordDisappearing.expirationOf(rumor)
            if (expiresAt != null) {
                trackExpiring(wrap.id, channelIdHex, rumor.id, expiresAt, ChannelChat.encryptedImagesOf(rumor).map { it.url })
                if (expiresAt <= now) continue
            }
            authors.add(rumor.pubKey.lowercase())
            observe(rumor)
            onRumor(entry.id, channelIdHex, rumor, seenOnRelays)
        }
        // Every author we just decrypted is observably present (CORD-02 §5), so fold them into the
        // roster even if they never posted a Guestbook Join. Atomic so a concurrent add isn't lost.
        if (authors.isNotEmpty()) {
            _observedAuthors.update { if (it.containsAll(authors)) it else it + authors }
        }
    }

    /** Records [rumor]'s author as seen at its CORD-02 §4 time (observation counts forward only). */
    private fun observe(rumor: Event) {
        val author = rumor.pubKey.lowercase()
        val atMs = ChannelChat.orderingMs(rumor) ?: (rumor.createdAt * 1000)
        lock.withLock { if (atMs > (observedAtMs[author] ?: Long.MIN_VALUE)) observedAtMs[author] = atMs }
    }

    // ── WebXDC signals (kind 3310) ───────────────────────────────────────────

    // Channel id -> its WebXDC signals by rumor id, in arrival order, bounded per channel.
    private val webxdcByChannel = HashMap<HexKey, LinkedHashMap<HexKey, Event>>()

    // What the held signals of each channel weigh, so any member's flood is bounded in bytes, not only in count.
    private val webxdcBytesByChannel = HashMap<HexKey, Long>()

    private val _webxdcRevision = MutableStateFlow(0L)

    /** Bumps whenever a new WebXDC signal is held — what a WebXDC host re-reads [webxdcSignals] on. */
    val webxdcRevision: StateFlow<Long> = _webxdcRevision

    /**
     * [channelIdHex]'s held WebXDC signals (kind 3310: app state updates and realtime peer signals,
     * [ConcordWebxdc]) not yet expired (CORD-08: app state disappears with the chat plane), oldest
     * first on the CORD-02 §4 basis. Amethyst has no WebXDC host; this is the plumbing one would read.
     */
    fun webxdcSignals(
        channelIdHex: HexKey,
        now: Long = TimeUtils.now(),
    ): List<Event> =
        lock
            .withLock { webxdcByChannel[channelIdHex]?.values?.toList() }
            .orEmpty()
            .filterNot { ConcordDisappearing.isExpired(it, now) }
            .sortedBy { ChannelChat.orderingMs(it) ?: (it.createdAt * 1000) }

    /**
     * Holds [rumor] for [channelIdHex]; false when already held. Keeps the
     * newest arrivals, at most [MAX_WEBXDC_PER_CHANNEL] of them and [MAX_WEBXDC_BYTES_PER_CHANNEL] in all:
     * any member can send these, and nothing reads them yet, so neither count nor size may grow unbounded.
     */
    private fun holdWebxdc(
        channelIdHex: HexKey,
        rumor: Event,
    ): Boolean {
        // No cap per signal is needed: a wrap carries at most NIP-44's 64 KiB of plaintext.
        val size = webxdcWeight(rumor)
        val added =
            lock.withLock {
                val held = webxdcByChannel.getOrPut(channelIdHex) { LinkedHashMap() }
                if (held.put(rumor.id, rumor) != null) return@withLock false
                var bytes = (webxdcBytesByChannel[channelIdHex] ?: 0L) + size
                while (held.size > MAX_WEBXDC_PER_CHANNEL || bytes > MAX_WEBXDC_BYTES_PER_CHANNEL) {
                    val oldest = held.keys.first()
                    bytes -= held.remove(oldest)?.let(::webxdcWeight) ?: 0L
                }
                webxdcBytesByChannel[channelIdHex] = bytes
                true
            }
        if (added) _webxdcRevision.update { it + 1 }
        return added
    }

    // ── Disappearing messages (CORD-08) ──────────────────────────────────────

    /** Wrap id -> the expiring rumor it carries, for every rumor with an `expiration` we emitted or refused. */
    private val expiringByWrapId = HashMap<HexKey, ExpiredConcordRumor>()

    private val _nextExpiry = MutableStateFlow<Long?>(null)

    /**
     * The earliest `expiration` (unix seconds) among the rumors this session holds, or null when none
     * expires — what the account's sweep schedules itself on, so a community with no timer costs
     * nothing. At or before now when an expired rumor was just refused and its wrap awaits the sweep.
     */
    val nextExpiry: StateFlow<Long?> = _nextExpiry

    /**
     * The disappearing-messages timer (seconds) a compliant sender attaches to its next durable Chat
     * rumor, read from the current fold at send time (CORD-08 §2), or null when off or not folded.
     */
    fun messageExpirationSecs(): Long? = _state.value?.metadata?.messageExpirationSecs()

    /**
     * The same entries as [expiringByWrapId], kept sorted by `expiresAt` (then wrap id), so a sweep
     * pops only what is due instead of scanning every tracked message, and the next deadline is the
     * head. Common code has no priority queue; a binary-searched insert into an array list is the
     * same order of cost here.
     */
    private val expiringByDeadline = ArrayList<ExpiredConcordRumor>()

    /**
     * Wrap ids this session already swept, newest last and bounded: relays keep re-delivering an
     * expired wrap (a relay that ignores NIP-40, a backfill page), and each would otherwise be opened
     * again only to be refused and swept again.
     */
    private val sweptWrapIds = LinkedHashSet<HexKey>()

    private val deadlineOrder = compareBy<ExpiredConcordRumor>({ it.expiresAt }, { it.wrapId })

    private fun trackExpiring(
        wrapId: HexKey,
        channelIdHex: HexKey,
        rumorId: HexKey,
        expiresAt: Long,
        attachmentUrls: List<String> = emptyList(),
    ) {
        lock.withLock { trackLocked(ExpiredConcordRumor(channelIdHex, wrapId, rumorId, expiresAt, attachmentUrls)) }
    }

    private fun trackLocked(entry: ExpiredConcordRumor) {
        val prior = expiringByWrapId[entry.wrapId]
        if (prior != null) {
            // A re-projection re-emits the same wrap: same rumor, same deadline — nothing to move.
            if (prior.expiresAt == entry.expiresAt) return
            val at = expiringByDeadline.binarySearch(prior, deadlineOrder)
            if (at >= 0) expiringByDeadline.removeAt(at)
        }
        expiringByWrapId[entry.wrapId] = entry
        val at = expiringByDeadline.binarySearch(entry, deadlineOrder)
        expiringByDeadline.add(if (at < 0) -at - 1 else at, entry)
        _nextExpiry.value = expiringByDeadline.first().expiresAt
    }

    /** True when [wrapId] was already swept as expired: a re-delivery is dropped before it is opened. */
    private fun wasSwept(wrapId: HexKey): Boolean = lock.withLock { wrapId in sweptWrapIds }

    /**
     * Forgets every rumor whose `expiration` is at or before [now] (CORD-08 §3): its wrap leaves the
     * channel buffer, so no re-projection can resurrect it, and it is returned so the caller purges
     * the rumor's note and the wrap's note from its store. A wrap re-delivered later is dropped at
     * ingest without being opened.
     */
    fun sweepExpired(now: Long = TimeUtils.now()): List<ExpiredConcordRumor> =
        lock.withLock {
            if (expiringByDeadline.isEmpty() || expiringByDeadline.first().expiresAt > now) return@withLock emptyList()
            val out = ArrayList<ExpiredConcordRumor>()
            while (expiringByDeadline.isNotEmpty() && expiringByDeadline.first().expiresAt <= now) {
                val expiring = expiringByDeadline.removeAt(0)
                expiringByWrapId.remove(expiring.wrapId)
                channelWrapsById[expiring.channelIdHex]?.remove(expiring.wrapId)
                wrapIdByRumorId.remove(expiring.rumorId)
                rumorIdByWrapId.remove(expiring.wrapId)
                sweptWrapIds.add(expiring.wrapId)
                out.add(expiring)
            }
            while (sweptWrapIds.size > MAX_SWEPT_WRAP_IDS) sweptWrapIds.remove(sweptWrapIds.first())
            _nextExpiry.value = expiringByDeadline.firstOrNull()?.expiresAt
            out
        }

    /** Every disappearing rumor this session still tracks — handed to the session that replaces it. */
    fun trackedExpiring(): List<ExpiredConcordRumor> = lock.withLock { expiringByDeadline.toList() }

    /**
     * Adopts [entries] tracked by the session this one replaces (a Refounding rebuilds the session):
     * their rumors are already in the store, and without this nothing would ever purge them once
     * their deadline passes, since the new session never sees the old epoch's wraps again.
     */
    fun carryExpiring(entries: Collection<ExpiredConcordRumor>) {
        if (entries.isEmpty()) return
        lock.withLock { entries.forEach { trackLocked(it) } }
    }

    /** True while [channelIdHex]'s buffer holds [wrapId] — for tests of the sweep. */
    internal fun isBuffered(
        channelIdHex: HexKey,
        wrapId: HexKey,
    ): Boolean = lock.withLock { channelWrapsById[channelIdHex]?.containsKey(wrapId) == true }

    // ---- Pins (CORD-04 §7) ------------------------------------------------------------------

    /**
     * The Channel's conversation key at [epoch] for opening a sealed Pin List, or null when this
     * account holds no plane of [channelIdHex] bound to that epoch.
     */
    fun pinUnsealKey(
        channelIdHex: HexKey,
        epoch: Long,
    ): ByteArray? = channelPlaneFor(channelIdHex, epoch)?.key?.conversationKey

    /**
     * [channelIdHex]'s Pin List read from its current head: sealed form opened with the held key of
     * the named epoch, entries verified (memoized), [isKilled] entries hidden, [newestEdit] applied.
     * Null until the Control Plane has folded, so an unfolded community is never mistaken for one
     * with no pins.
     */
    fun readPins(
        channelIdHex: HexKey,
        isKilled: (ConcordPins.VerifiedPin) -> Boolean = { false },
        newestEdit: (ConcordPins.VerifiedPin) -> ConcordLocalEdit? = { null },
        now: Long = TimeUtils.now(),
    ): ConcordChannelPins? {
        val state = _state.value ?: return null
        if (channelIdHex !in state.channels) return null
        // An expired message leaves the pinned list too (CORD-08 §3: never display an expired rumor);
        // its proof is still valid, but the rumor's own tag says it is gone.
        val hidden = { pin: ConcordPins.VerifiedPin -> isKilled(pin) || pin.tags.isExpirationBefore(now) }
        // Not drained yet: the head may simply not have been served, so the result is marked partial
        // (shown, never written from — CORD-04 §7).
        return ConcordPinning.read(_pinHeads.value[channelIdHex], channelIdHex, { pinUnsealKey(channelIdHex, it) }, pinVerifier, hidden, newestEdit, complete = _controlDrained.value)
    }

    /**
     * The proof source for pinning [rumorId] of [channelIdHex] (or for attaching an Edit): the wrap
     * that carried it, reopened on the plane it arrived on so the disclosure derives from the key of
     * the message's own epoch. Null when this session never held that wrap.
     */
    fun pinSource(
        channelIdHex: HexKey,
        rumorId: HexKey,
    ): ConcordPinSource? {
        val (wrap, plane) =
            lock.withLock {
                val wrapId = wrapIdByRumorId[rumorId] ?: return null
                val wrap = channelWrapsById[channelIdHex]?.get(wrapId) ?: return null
                val plane = channelKeysByAddress[wrap.pubKey] ?: historicalChannelKeysByAddress[wrap.pubKey] ?: return null
                wrap to plane
            }
        if (plane.channelIdHex != channelIdHex) return null
        return ConcordPinning.sourceOf(wrap, plane, rumorId)
    }

    /** True when this session holds the wrap that carried [rumorId] (jump-to-context resolves locally). */
    fun holdsRumor(rumorId: HexKey): Boolean = lock.withLock { rumorId in wrapIdByRumorId }

    companion object {
        private fun privateKeySet(e: ConcordCommunityListEntry) = e.privateChannels.mapTo(HashSet()) { Triple(it.channelId.lowercase(), it.key.lowercase(), it.epoch) }

        /** A typing heartbeat is considered current for this many seconds after it's seen. */
        const val TYPING_STALE_SECS = 8L

        /** WebXDC signals held per channel (the reference client scans its newest 2000 for peer signals). */
        const val MAX_WEBXDC_PER_CHANNEL = 2000

        /** All the signals held for one channel. */
        const val MAX_WEBXDC_BYTES_PER_CHANNEL = 4L * 1024 * 1024

        /** What a held signal costs: its content and tags, in characters. */
        internal fun webxdcWeight(rumor: Event): Long = rumor.content.length.toLong() + rumor.tags.sumOf { tag -> tag.sumOf { it.length.toLong() } }

        /** Chat wraps each channel's buffer keeps (most recently delivered) for pins, jump-to-context and re-projection. */
        const val MAX_CHANNEL_WRAPS = 2000

        /** How many swept (expired) wrap ids a session remembers to drop their re-deliveries unopened. */
        const val MAX_SWEPT_WRAP_IDS = 4096
    }
}
