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

import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.crypto.sha2.sha256
import com.vitorpamplona.quartz.concord.cord03Channels.ChannelChat
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordChatEditEvent
import com.vitorpamplona.quartz.concord.cord04Roles.AuthorityResolver
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEdition
import com.vitorpamplona.quartz.concord.cord04Roles.EntityFloor
import com.vitorpamplona.quartz.concord.cord04Roles.pins.ConcordPinLists
import com.vitorpamplona.quartz.concord.cord04Roles.pins.ConcordPins
import com.vitorpamplona.quartz.concord.cord04Roles.pins.ConcordPins.VerifiedPin
import com.vitorpamplona.quartz.concord.crypto.ControlPlaneKeys
import com.vitorpamplona.quartz.concord.envelope.ConcordStreamEnvelope
import com.vitorpamplona.quartz.concord.envelope.OpenedStreamEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip09Deletions.DeletionRequestEvent
import com.vitorpamplona.quartz.nip40Expiration.ExpirationTag
import kotlinx.serialization.json.JsonObject
import kotlin.random.Random

/** An Edit (kind 3302) this client holds for a pinned message — the author's newest, by send time. */
class ConcordLocalEdit(
    val rumorId: HexKey,
    val author: HexKey,
    val content: String,
    /** `created_at * 1000 + ms`, comparable with [VerifiedPin.editOrderMs]. */
    val orderMs: Long,
)

/** One verified pin as a reader shows it (CORD-04 §7). */
class ConcordPinnedMessage(
    val pin: VerifiedPin,
    /** The newest words this client can show: a held newer Edit's, else the proof's. */
    val content: String,
    /**
     * True when the message was revised: the entry carries a proven Edit, or this client holds a
     * newer one (§7: a client holding a newer Edit MUST mark the pin edited, never render the
     * superseded words as current).
     */
    val edited: Boolean,
    /** A held Edit newer than the entry's proof — what a curator's refresh would attach. */
    val newerEdit: ConcordLocalEdit?,
) {
    val rumorId: HexKey get() = pin.rumorId
    val author: HexKey get() = pin.author
}

/**
 * A Channel's Pin List as this client reads it (CORD-04 §7).
 *
 * [sealedUnavailable] is deliberately distinct from an empty list: the list exists but is sealed
 * under an epoch key this client never held. It renders as "unavailable", and no edition may be
 * built from it — publishing would silently drop every entry this client cannot see.
 */
class ConcordChannelPins(
    val channelIdHex: HexKey,
    /** The authorized head edition the list was read from, or null when the Channel has none. */
    val head: ControlEdition?,
    /** Verified, un-deleted entries in wire order — the base a write replaces entire. */
    val alive: List<VerifiedPin>,
    /** Verified entries their author erased (a held kind 5): hidden now, owed an omitting edition. */
    val killed: List<VerifiedPin>,
    /** [alive] for display, newest message first, with held Edits applied. */
    val pins: List<ConcordPinnedMessage>,
    val sealedUnavailable: Boolean,
    /** True when the head's content broke a cap or the format, so it reads as empty. */
    val violating: Boolean,
    /** True when the head is the sealed form (`{"epoch","sealed"}`). */
    val sealedForm: Boolean,
    /** Entries that failed verification and were dropped alone. */
    val invalidEntries: Int,
    /**
     * False while the Control Plane has not been swept whole yet: [head] is whatever the partial fold
     * holds, so an empty list may only mean "not served yet". No edition may be built from it (§7).
     */
    val complete: Boolean = true,
) {
    val count: Int get() = pins.size

    fun isPinned(rumorId: HexKey): Boolean = alive.any { it.rumorId == rumorId }

    /** True when the head owes keyless readers a republish: an erased entry, or a newer Edit to attach. */
    val owesRepublish: Boolean get() = killed.isNotEmpty() || pins.any { it.newerEdit != null }

    /** Every rumor id the list carries, shown or erased — what a delete or an Edit must name to change this read. */
    val rumorIds: Set<HexKey> by lazy { (alive + killed).mapTo(HashSet()) { it.rumorId } }

    /**
     * The soonest NIP-40 deadline (unix seconds) after [now] among the shown pins, or null: when this
     * read goes stale, since an expired message leaves the list (CORD-08 §3).
     */
    fun nextExpiry(now: Long): Long? = alive.mapNotNull { pin -> pin.tags.firstNotNullOfOrNull(ExpirationTag::parse) }.filter { it > now }.minOrNull()

    companion object {
        fun none(
            channelIdHex: HexKey,
            complete: Boolean = true,
        ) = ConcordChannelPins(channelIdHex, null, emptyList(), emptyList(), emptyList(), sealedUnavailable = false, violating = false, sealedForm = false, invalidEntries = 0, complete = complete)
    }
}

/**
 * Pin-entry verification memoized by entry identity (§7 Weight: a re-folding client "SHOULD cache
 * entry verification by entry identity" — otherwise every fold redoes each signature, MAC and
 * decryption). The key is a hash of the channel and the entry's exact bytes, so a cached verdict can
 * never be served for a different entry that merely names the same seal. Bounded; failures are
 * cached too.
 */
class ConcordPinVerifier(
    private val maxEntries: Int = 512,
) {
    private val lock = KmpLock()
    private val verdicts = LinkedHashMap<HexKey, VerifiedPin?>()

    /** Verification runs actually performed (cache misses) — for tests. */
    var misses: Int = 0
        private set

    fun verify(
        entry: JsonObject,
        channelIdHex: HexKey,
    ): VerifiedPin? {
        val key = sha256((channelIdHex + entry.toString()).encodeToByteArray()).toHexKey()
        lock.withLock { if (verdicts.containsKey(key)) return verdicts[key] }
        val verdict = ConcordPins.verify(entry, channelIdHex)
        lock.withLock {
            misses++
            verdicts[key] = verdict
            while (verdicts.size > maxEntries) verdicts.remove(verdicts.keys.first())
        }
        return verdict
    }

    private val lists = LinkedHashMap<HexKey, ConcordPins.PinListRead>()

    /** List parses (and sealed-form decrypts) actually performed (cache misses) — for tests. */
    var listReads: Int = 0
        private set

    /**
     * [head]'s content read as a Pin List, memoized by the head's rumor id: an edition's bytes never
     * change, so re-reading the pins on every trigger (a fold, a delete landing, an expiry) parses and
     * decrypts the list once. A read that found the list sealed under a key not held is not cached,
     * so the key arriving later (a Private Channel key delivered on grant) opens it.
     */
    fun readList(
        head: ControlEdition,
        unsealKey: (epoch: Long) -> ByteArray?,
    ): ConcordPins.PinListRead {
        lock.withLock { lists[head.rumorId] }?.let { return it }
        val read = ConcordPins.read(head.content, unsealKey)
        lock.withLock {
            listReads++
            if (!read.sealedUnavailable) {
                lists[head.rumorId] = read
                while (lists.size > MAX_LISTS) lists.remove(lists.keys.first())
            }
        }
        return read
    }

    companion object {
        private const val MAX_LISTS = 64
    }
}

/** The proof material for pinning one opened message: its original seal and the plane key of its epoch. */
class ConcordPinSource(
    val opened: OpenedStreamEvent,
    /** The carrying wrap, as the entry's (unverifiable) locator hint. */
    val wrapId: HexKey?,
    /** The Channel's conversation key at the message's epoch — what the disclosure derives from. */
    val conversationKey: ByteArray,
) {
    val rumorId: HexKey get() = opened.rumor.id
}

/**
 * What the reader holds about pinned messages from its own Chat Plane view: the kind-5 deletes and
 * kind-3302 Edits among [rumors]. The app builds the same lookups off its event store; `amy` and the
 * tests build them from the rumors they drained.
 */
class ConcordPinEvidence(
    rumors: Collection<Event>,
) {
    private val deletesByTarget = HashMap<HexKey, MutableList<Event>>()
    private val editsByTarget = HashMap<HexKey, MutableList<Event>>()

    init {
        for (rumor in rumors) {
            // Opened rumors come out of EventFactory typed, so the kind check is the type check.
            when (rumor) {
                is DeletionRequestEvent -> rumor.deleteEventIds().forEach { deletesByTarget.getOrPut(it) { ArrayList() }.add(rumor) }
                is ConcordChatEditEvent -> rumor.editedMessageId()?.let { editsByTarget.getOrPut(it) { ArrayList() }.add(rumor) }
            }
        }
    }

    /** True when a held delete by the pin's proven author names it (§7 Interaction with deletion). */
    fun isKilled(pin: VerifiedPin): Boolean = deletesByTarget[pin.rumorId]?.any { ConcordPins.killedBy(pin, it.pubKey, it.tags) } == true

    /** The author's newest held Edit of the pinned message, or null. */
    fun newestEdit(pin: VerifiedPin): ConcordLocalEdit? =
        editsByTarget[pin.rumorId]
            ?.filter { it.pubKey == pin.author }
            ?.maxWithOrNull(compareBy({ orderMsOf(it) }, { it.id }))
            ?.let { ConcordLocalEdit(it.id, it.pubKey, it.content, orderMsOf(it)) }

    private fun orderMsOf(rumor: Event): Long = ChannelChat.orderingMs(rumor) ?: (rumor.createdAt * 1000)
}

/** Why a pin write did or did not publish. Everything but [PUBLISHED] publishes nothing. */
enum class ConcordPinOutcome {
    PUBLISHED,
    ALREADY_PINNED,
    NOT_PINNED,

    /** The head already says what the write would say (a duty already done by someone else). */
    NOTHING_TO_DO,
    NOT_WRITEABLE,

    /** Neither the owner nor a PIN_MESSAGES holder (a banned holder included). */
    NOT_AUTHORIZED,

    /** No Control Plane write key (`control_root`) at this epoch (CORD-02 §2). */
    NO_WRITE_KEY,

    /** The community or channel has not folded yet: there is no list to build on. */
    NOT_FOLDED,

    /** A Private Channel whose current key this account does not hold: the list cannot be sealed. */
    NO_CHANNEL_KEY,

    /** The current list is sealed under a key this client never held — MUST withhold the write. */
    LIST_UNAVAILABLE,

    /** The message's original wrap (and so its seal) is not held, so it cannot be proven. */
    MESSAGE_UNAVAILABLE,

    /** The entry would not verify (not a message or reply, or not openable at its epoch). */
    UNVERIFIABLE,
    TOO_MANY_PINS,
    TOO_LARGE,

    /** The edition was built but no relay acknowledged it, so it may not have landed. */
    NOT_CONFIRMED,
}

class ConcordPinWrite(
    val outcome: ConcordPinOutcome,
    /** The Control Plane wrap to publish when [outcome] is [ConcordPinOutcome.PUBLISHED]. */
    val wrap: Event? = null,
    /** The entries the new edition carries. */
    val entries: List<JsonObject> = emptyList(),
) {
    val published: Boolean get() = outcome == ConcordPinOutcome.PUBLISHED
}

/** Everything a Pin List write needs, resolved by the caller (the app's session, or `amy`'s drain). */
class ConcordPinContext(
    val actor: NostrSigner,
    val controlPlane: ControlPlaneKeys,
    val communityId: ByteArray,
    val owner: HexKey,
    /** The community's current Control Plane editions (for the `vac` citation). */
    val current: List<ControlEdition>,
    val channelIdHex: HexKey,
    /** The Channel's folded `private` flag: it alone picks the form a writer uses (§7). */
    val channelIsPrivate: Boolean,
    /** The Channel's current plane — a private list is sealed under its key at its epoch. */
    val currentPlane: ChannelPlane?,
    /** The list as read from its head: the base every write replaces entire. */
    val pins: ConcordChannelPins,
    /** The owner or a PIN_MESSAGES holder, per the fold (hasPermission, so a banned holder is not). */
    val authorized: Boolean,
)

/**
 * CORD-04 §7 Pins at the commons layer: read a Channel's Pin List into verified, deletion-aware,
 * edit-aware pins, and write the next edition for pin, unpin, the deletion omission and the Edit
 * refresh. Pure — the caller publishes the returned wrap (and, in the app, echoes it into the
 * session so the next write chains onto it).
 */
object ConcordPinning {
    /** The window a non-pinner witness waits before a duty republish, so simultaneous curators collapse to one. */
    const val DUTY_MIN_DELAY_MS = 3_000L
    const val DUTY_MAX_DELAY_MS = 15_000L

    fun dutyDelayMs(random: Random = Random.Default): Long = random.nextLong(DUTY_MIN_DELAY_MS, DUTY_MAX_DELAY_MS + 1)

    /** The authorized head of [channelIdHex]'s Pin List among [editions], or null (the `amy` / one-shot path). */
    fun headFor(
        editions: Collection<ControlEdition>,
        communityIdHex: HexKey,
        owner: HexKey,
        channelIdHex: HexKey,
        floors: Map<String, EntityFloor> = emptyMap(),
    ): ControlEdition? {
        val authority = AuthorityResolver.resolve(editions, communityIdHex.hexToByteArray(), owner)
        return ConcordPinLists.heads(editions, authority, communityIdHex, listOf(channelIdHex), floors)[channelIdHex]
    }

    /**
     * Reads [head] as [channelIdHex]'s Pin List: the sealed form opens with [unsealKey] (the
     * Channel's conversation key at the named epoch), each entry is verified through [verifier]
     * (dropped alone on failure), an entry its author erased ([isKilled]) is hidden at once, and an
     * entry behind a held newer Edit ([newestEdit]) is marked edited and shows the newer words.
     */
    fun read(
        head: ControlEdition?,
        channelIdHex: HexKey,
        unsealKey: (epoch: Long) -> ByteArray?,
        verifier: ConcordPinVerifier = ConcordPinVerifier(),
        isKilled: (VerifiedPin) -> Boolean = { false },
        newestEdit: (VerifiedPin) -> ConcordLocalEdit? = { null },
        complete: Boolean = true,
    ): ConcordChannelPins {
        if (head == null) return ConcordChannelPins.none(channelIdHex, complete)
        val read = verifier.readList(head, unsealKey)
        val alive = ArrayList<VerifiedPin>()
        val killed = ArrayList<VerifiedPin>()
        var invalid = 0
        val seen = HashSet<HexKey>()
        for (entry in read.entries) {
            val pin = verifier.verify(entry, channelIdHex)
            if (pin == null) {
                invalid++
                continue
            }
            // The recomputed rumor id is the entry's identity, for deduplication too.
            if (!seen.add(pin.rumorId)) continue
            if (isKilled(pin)) killed.add(pin) else alive.add(pin)
        }
        val shown =
            alive
                .map { pin ->
                    val local = newestEdit(pin)?.takeIf { it.author == pin.author && isNewer(it, pin) }
                    ConcordPinnedMessage(pin, local?.content ?: pin.content, edited = pin.edited || local != null, newerEdit = local)
                }.sortedWith(compareByDescending<ConcordPinnedMessage> { it.pin.orderMs }.thenBy { it.rumorId })
        return ConcordChannelPins(
            channelIdHex = channelIdHex,
            head = head,
            alive = alive,
            killed = killed,
            pins = shown,
            sealedUnavailable = read.sealedUnavailable,
            violating = read.violating,
            sealedForm = read.sealedForm,
            invalidEntries = invalid,
            complete = complete,
        )
    }

    /**
     * [pins]' shown entries a reader displays: an entry by a [isBanned] author (the community declines
     * to show a banned member's posts, CORD-04 §4) or by someone the reader [isHidden]s (mutes or
     * blocks) is left out. Display only — the list itself, and every write built from it, is untouched.
     */
    fun visible(
        pins: ConcordChannelPins,
        isBanned: (HexKey) -> Boolean,
        isHidden: (HexKey) -> Boolean,
    ): List<ConcordPinnedMessage> = pins.pins.filterNot { isBanned(it.author) || isHidden(it.author) }

    /** True when [edit] is newer than whatever Edit [pin]'s proof already carries. */
    private fun isNewer(
        edit: ConcordLocalEdit,
        pin: VerifiedPin,
    ): Boolean {
        if (edit.rumorId == pin.editRumorId) return false
        val proven = pin.editOrderMs ?: return true
        return edit.orderMs > proven || (edit.orderMs == proven && edit.rumorId > (pin.editRumorId ?: ""))
    }

    /**
     * Reopens [wrap] on [plane] as the proof source for [rumorId], or null when it does not carry
     * exactly that message under the Chat ingest gate.
     */
    fun sourceOf(
        wrap: Event,
        plane: ChannelPlane,
        rumorId: HexKey,
    ): ConcordPinSource? {
        val opened = ConcordStreamEnvelope.openOrNull(wrap, plane.key) ?: return null
        val rumor = ChannelChat.acceptOpened(opened, plane.channelIdHex, plane.epoch) ?: return null
        if (rumor.id != rumorId) return null
        return ConcordPinSource(opened, wrap.id, plane.key.conversationKey)
    }

    /** Finds [rumorId] among [wraps] on any of [planes] (the one-shot path, e.g. `amy`). */
    fun sourceFrom(
        wraps: Collection<Event>,
        planes: Collection<ChannelPlane>,
        rumorId: HexKey,
    ): ConcordPinSource? {
        val byAddress = planes.associateBy { it.key.publicKeyHex }
        for (wrap in wraps) {
            val plane = byAddress[wrap.pubKey] ?: continue
            sourceOf(wrap, plane, rumorId)?.let { return it }
        }
        return null
    }

    /** The first reason [ctx] may not write at all, or null. */
    fun refusal(ctx: ConcordPinContext): ConcordPinOutcome? =
        when {
            !ctx.authorized -> ConcordPinOutcome.NOT_AUTHORIZED
            !ctx.controlPlane.canWrite -> ConcordPinOutcome.NO_WRITE_KEY
            // §7: never write from a list the fold was not served — a partial fold's head (or its
            // absence) is not the list a replace-entire edition would overwrite.
            !ctx.pins.complete -> ConcordPinOutcome.NOT_FOLDED
            // §7: a writer MUST NOT build an edition from a list it could not read.
            ctx.pins.sealedUnavailable -> ConcordPinOutcome.LIST_UNAVAILABLE
            ctx.channelIsPrivate && ctx.currentPlane == null -> ConcordPinOutcome.NO_CHANNEL_KEY
            else -> null
        }

    /**
     * The entries a write starts from. A list sealed in a Channel's private era is never
     * mechanically re-formed into the now-public form (§7): its entries are not carried, so a
     * post-switch edition can only disclose what a curator pins deliberately.
     */
    private fun base(ctx: ConcordPinContext): List<VerifiedPin> = if (!ctx.channelIsPrivate && ctx.pins.sealedForm) emptyList() else ctx.pins.alive

    private suspend fun publish(
        ctx: ConcordPinContext,
        entries: List<JsonObject>,
        createdAt: Long,
    ): ConcordPinWrite {
        if (entries.size > ConcordPins.MAX_ENTRIES) return ConcordPinWrite(ConcordPinOutcome.TOO_MANY_PINS)
        val content =
            try {
                val plane = ctx.currentPlane
                if (ctx.channelIsPrivate && plane != null) {
                    ConcordPins.serializeSealed(entries, plane.key.conversationKey, plane.epoch)
                } else {
                    ConcordPins.serializePublic(entries)
                }
            } catch (_: ConcordPins.PinListTooLargeException) {
                // Every reader would treat an over-cap edition as an empty list: refuse, never publish it.
                return ConcordPinWrite(ConcordPinOutcome.TOO_LARGE)
            }
        val wrap =
            ConcordModeration.setPinList(
                ctx.actor,
                ctx.controlPlane,
                ctx.communityId,
                ctx.channelIdHex.hexToByteArray(),
                ctx.pins.head,
                content,
                ctx.current,
                createdAt,
                owner = ctx.owner,
            )
        return ConcordPinWrite(ConcordPinOutcome.PUBLISHED, wrap, entries)
    }

    /** Pins [source]'s message: its proof entry prepended to the current list, as the next edition. */
    suspend fun pin(
        ctx: ConcordPinContext,
        source: ConcordPinSource,
        createdAt: Long,
    ): ConcordPinWrite {
        refusal(ctx)?.let { return ConcordPinWrite(it) }
        val base = base(ctx)
        if (base.any { it.rumorId == source.rumorId }) return ConcordPinWrite(ConcordPinOutcome.ALREADY_PINNED)
        if (base.size >= ConcordPins.MAX_ENTRIES) return ConcordPinWrite(ConcordPinOutcome.TOO_MANY_PINS)
        val entry =
            ConcordPins.buildEntry(source.opened, source.conversationKey, ctx.channelIdHex, source.wrapId)
                ?: return ConcordPinWrite(ConcordPinOutcome.UNVERIFIABLE)
        return publish(ctx, listOf(entry) + base.map { it.entry }, createdAt)
    }

    /** Unpins [rumorId]: the next edition without it (there is no deletion event, §7). */
    suspend fun unpin(
        ctx: ConcordPinContext,
        rumorId: HexKey,
        createdAt: Long,
    ): ConcordPinWrite {
        refusal(ctx)?.let { return ConcordPinWrite(it) }
        val base = base(ctx)
        val carried = base.any { it.rumorId == rumorId } || ctx.pins.killed.any { it.rumorId == rumorId }
        if (!carried) return ConcordPinWrite(ConcordPinOutcome.NOT_PINNED)
        return publish(ctx, base.filter { it.rumorId != rumorId }.map { it.entry }, createdAt)
    }

    /**
     * The deletion omission (§7): the list without [rumorIds] and without every entry already known
     * erased. The pinner publishes it at once when deleting their own pinned message; the result is
     * [ConcordPinOutcome.NOTHING_TO_DO] when the head no longer carries any of them.
     */
    suspend fun omit(
        ctx: ConcordPinContext,
        rumorIds: Set<HexKey>,
        createdAt: Long,
    ): ConcordPinWrite {
        refusal(ctx)?.let { return ConcordPinWrite(it) }
        val base = base(ctx)
        val keep = base.filter { it.rumorId !in rumorIds }
        if (keep.size == base.size && ctx.pins.killed.isEmpty()) return ConcordPinWrite(ConcordPinOutcome.NOTHING_TO_DO)
        return publish(ctx, keep.map { it.entry }, createdAt)
    }

    /**
     * Settles what the head owes keyless readers, in one replace-entire write (§7 Edits + deletion):
     * drops every erased entry and attaches the newest provable Edit to each entry behind one.
     * [editSource] reopens a held Edit's wrap for its proof; an Edit it cannot prove is skipped, and
     * only an Edit newer than the entry's is ever attached, so a refresh never reverts one.
     * [ConcordPinOutcome.NOTHING_TO_DO] when nothing is owed — the check a delayed witness re-runs
     * after its random wait, so simultaneous curators collapse to one publisher.
     */
    suspend fun settle(
        ctx: ConcordPinContext,
        editSource: (ConcordPinnedMessage) -> ConcordPinSource?,
        createdAt: Long,
    ): ConcordPinWrite {
        refusal(ctx)?.let { return ConcordPinWrite(it) }
        val base = base(ctx)
        var changed = ctx.pins.killed.isNotEmpty()
        val shownById = ctx.pins.pins.associateBy { it.rumorId }
        val next =
            base.map { pin ->
                val shown = shownById[pin.rumorId]
                val source = if (shown?.newerEdit != null) editSource(shown) else null
                if (source == null) {
                    pin.entry
                } else {
                    val withEdit = ConcordPins.withEdit(pin.entry, source.opened, source.conversationKey, ctx.channelIdHex)
                    if (withEdit != pin.entry) changed = true
                    withEdit
                }
            }
        if (!changed) return ConcordPinWrite(ConcordPinOutcome.NOTHING_TO_DO)
        return publish(ctx, next, createdAt)
    }
}
