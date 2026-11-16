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

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.model.nip88Polls.PollResponsesCache
import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.firstFullCharOrEmoji
import com.vitorpamplona.amethyst.commons.util.replace
import com.vitorpamplona.amethyst.commons.util.toShortDisplay
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.experimental.bounties.addedRewardValue
import com.vitorpamplona.quartz.experimental.bounties.hasAdditionalReward
import com.vitorpamplona.quartz.experimental.ephemChat.chat.EphemeralChatEvent
import com.vitorpamplona.quartz.lightning.LnInvoiceUtil
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.anyHashTag
import com.vitorpamplona.quartz.nip01Core.tags.publishedAt.PublishedAtProvider
import com.vitorpamplona.quartz.nip03Timestamp.VerificationState
import com.vitorpamplona.quartz.nip10Notes.BaseThreadedEvent
import com.vitorpamplona.quartz.nip10Notes.threadRootIdOrSelf
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip28PublicChat.base.IsInPublicChatChannel
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.nip36SensitiveContent.isSensitiveOrNSFW
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent
import com.vitorpamplona.quartz.nip47WalletConnect.events.NwcRequestEvent
import com.vitorpamplona.quartz.nip47WalletConnect.events.NwcResponseEvent
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.PayInvoiceMethod
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.PayInvoiceSuccessResponse
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip56Reports.ReportEvent
import com.vitorpamplona.quartz.nip56Reports.ReportType
import com.vitorpamplona.quartz.nip57Zaps.ZapReceiptEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapRequestEvent
import com.vitorpamplona.quartz.nip59Giftwrap.HostStub
import com.vitorpamplona.quartz.nip72ModCommunities.approval.CommunityPostApprovalEvent
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import com.vitorpamplona.quartz.nipCCGeocaching.listing.GeocacheListingEvent
import com.vitorpamplona.quartz.utils.BigDecimal
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.anyAsync
import com.vitorpamplona.quartz.utils.containsAny
import com.vitorpamplona.quartz.utils.internIfPossible
import com.vitorpamplona.quartz.utils.launchAndWaitAll
import com.vitorpamplona.quartz.utils.plus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlin.concurrent.Volatile

interface NotesGatherer {
    fun removeNote(note: Note)
}

@Stable
class AddressableNote(
    val address: Address,
) : Note(address.toValue()) {
    override fun idNote() = toNAddr()

    override fun toNEvent() = toNAddr()

    override fun idDisplayNote() = idNote().toShortDisplay()

    override fun address() = address

    override fun createdAt(): Long? {
        val currentEvent = event ?: return null
        if (currentEvent is PublishedAtProvider) return currentEvent.publishedAt() ?: currentEvent.createdAt
        return currentEvent.createdAt
    }

    fun dTag(): String = address.dTag

    fun toNAddr() = address.toNIP19OrFallback(relayHintUrl(), event)

    override fun toNostrUri() = address.toNostrUriOrFallback(relayHintUrl(), event)
}

@Stable
open class Note(
    val idHex: HexKey,
) : NotesGatherer {
    // Per-instance lock shared by the previously @Synchronized methods (zap /
    // onchain-zap / zap-payment / relay-add / flowSet lifecycle). Replaces the
    // JVM-only @Synchronized annotation so this class compiles on iOS.
    private val syncLock = KmpLock()

    // These fields are only available after the Text Note event is received.
    // They are immutable after that.
    // `@Volatile`: written by the decrypt/index pipeline (IO coroutines) and
    // read by the relay socket thread (OK confirmations drilling into the
    // gift-wrap chain) — a stale read strands a relay on the outer wrap.
    @Volatile
    var event: Event? = null
    var author: User? = null
    var replyTo: List<Note>? = null

    /**
     * The envelope that delivered this note when [event] is an unsealed
     * rumor: normally the kind-1059 gift wrap, a bare kind-13 seal when
     * one arrives unwrapped. Null for public events.
     *
     * Rumors are unsigned and must never be referenced or republished
     * directly on public relays — consumers cite, broadcast, prune, and
     * evict through this stub instead. Living on the Note (not on a
     * global index, not on the quartz event) ties its lifetime to the
     * note: whatever removes or garbage-collects the note frees the stub.
     */
    var rumorHost: HostStub? = null

    /** Records the envelope that delivered this rumor. */
    fun recordRumorHost(envelope: Event) {
        rumorHost = HostStub(envelope.id, envelope.pubKey, envelope.kind, envelope.createdAt)
    }

    var inGatherers: List<NotesGatherer>? = null

    fun inGatherers() = inGatherers ?: listOf<NotesGatherer>().also { inGatherers = it }

    fun addGatherer(gatherer: NotesGatherer) {
        val list = inGatherers()
        if (gatherer !in list) {
            inGatherers = inGatherers() + gatherer
        }
    }

    fun removeGatherer(gatherer: NotesGatherer) {
        val list = inGatherers()
        if (gatherer in list) {
            inGatherers = inGatherers() - gatherer
        }
    }

    override fun removeNote(note: Note) {
        removeReply(note)
        removeBoost(note)
        removeReaction(note)
        removeEdit(note)
        removeTimestamp(note)
        removeZap(note)
        removeZapPayment(note)
        removeReport(note)
        removeLabel(note)
        removeNutzap(note)
        removeOnchainZapBySource(note)
        removeBolt12ZapBySource(note)
    }

    var poll: PollResponsesCache? = null

    fun pollStateOrNull(): PollResponsesCache? = poll

    fun pollState(): PollResponsesCache = poll ?: PollResponsesCache().also { poll = it }

    /**
     * When this note holds a NIP-03 OpenTimestamps event (kind 1040), its memoized blockchain
     * verification verdict. Kept on the note itself — not in a separate id-keyed cache — so the
     * (expensive, network-backed) result shares the note's lifecycle: the attestation is anchored on
     * its target's [timestamps] and both are evicted together. `null` means never verified yet.
     *
     * `@Volatile`: written by the OTS verifier on `applicationIOScope` and read from the Compose
     * read path when folding an earliest-attested time.
     */
    @Volatile
    var otsVerification: VerificationState? = null

    // These fields are updated every time an event related to this note is received.
    @Volatile
    var replies = listOf<Note>()
        private set

    @Volatile
    var reactions = mapOf<String, List<Note>>()
        private set

    @Volatile
    var boosts = listOf<Note>()
        private set

    /**
     * Concord chat edits (kind 3302) targeting this message, held here — like [reactions] and
     * [replies] — so an edit survives exactly as long as its message does. Concord decrypts each
     * wrap's rumor only once per session (the community session dedups re-delivered wraps), so an
     * edit evicted from the soft event cache can never be re-downloaded; anchoring it to the
     * (channel-retained) message keeps it strongly reachable. The chat bubble overlays the latest
     * author-matching edit.
     */
    @Volatile
    var edits = listOf<Note>()
        private set

    /**
     * NIP-03 OpenTimestamps attestations (kind 1040) proving this note existed by a given time,
     * held here — like [reactions] and [edits] — so an attestation survives exactly as long as the
     * note it timestamps and is dropped the moment that note leaves the cache (including on a NIP-09
     * delete). Anchoring them here also replaces the old full-cache scan: the OTS pill folds this
     * list directly. Each attestation memoizes its own blockchain verdict in [Note.otsVerification].
     */
    @Volatile
    var timestamps = listOf<Note>()
        private set

    @Volatile
    var reports = mapOf<User, List<Note>>()
        private set

    /**
     * NIP-32 hashtag labels (kind 1985) targeting this note.
     * Key: hashtag value, lowercased (the `l` tag value under the `#t` namespace).
     * Value: the list of LabelEvent notes that applied that hashtag to this note,
     * so `source.author` identifies who labeled it. Used by the hashtag feed to
     * surface posts a follow has tagged and to attribute the label in the UI.
     */
    @Volatile
    var labels = mapOf<String, List<Note>>()
        private set

    // `@Volatile`: written under [syncLock], read lock-free by addZap/removeZap's fast paths.
    @Volatile
    var zaps = mapOf<Note, Note?>()
        private set

    var zapsAmount: BigDecimal = BigDecimal(0)

    /**
     * NIP-BC onchain zaps targeting this note.
     * Key: Bitcoin txid (lowercase 64-char hex). Value: entry with the source
     * OnchainZapEvent note (so `source.author` identifies the sender), the sender-claimed
     * amount, the on-chain verified amount, and a verification status.
     * Unverified, pending, and confirmed entries live here together; `updateZapTotal`
     * only counts CONFIRMED amounts.
     *
     * `@Volatile` ensures cross-thread visibility: writes happen on `applicationIOScope`
     * (inside the syncLock-guarded inner methods) and reads happen on the Compose Main
     * thread (gallery recomposition + the reverification driver's `any { … }` check).
     */
    @Volatile
    var onchainZaps = mapOf<String, OnchainZapEntry>()
        private set

    /**
     * True when the NIP-BC chain verifier has reached a terminal verdict for THIS
     * note's OnchainZapEvent — i.e. on-chain Confirmed, or hard-rejected for a reason
     * other than `TX_NOT_FOUND`. `LocalCache.consume()` uses this to skip re-launching
     * the verifier on relay echoes once the chain has spoken definitively.
     *
     * Stays `false` for transient states (`UNVERIFIED`, `PENDING`, `TX_NOT_FOUND`) so
     * the gallery's reverify driver can still upgrade them as the chain advances.
     */
    @Volatile
    var onchainZapResolved: Boolean = false

    /**
     * NIP-61 nutzaps (kind 9321) targeting this note.
     * Key: nutzap event id (the kind:9321 event itself). Value: source note +
     * the sender-claimed sat total parsed once from the proof tags at attach
     * time so the UI hot path doesn't re-parse JSON on every recomposition.
     *
     * The amount is the SENDER-claimed sum. Verifying it requires talking to
     * the mint (signature check on every proof + state lookup), which is the
     * recipient's wallet flow (`CashuWalletState.redeemNutzap`) — for the
     * reaction row / notifications we trust the claim, same as we trust the
     * `amount` tag on an OnchainZapEvent before its on-chain verifier runs.
     */
    @Volatile
    var nutzaps = mapOf<HexKey, NutzapEntry>()
        private set

    /**
     * NIP-B1 BOLT12 zaps (kind 9736) targeting this note.
     * Key: the payer proof's `invoice_payment_hash` (hex) — the spec's dedup key,
     * so two zap events proving the same settled payment collapse to one entry.
     * Value: entry with the source Bolt12ZapEvent note (so `source.author` is the
     * payer), the validated amount in millisats, and whether the proof's crypto
     * was fully verified. Every entry here has already passed the synchronous
     * `Bolt12ZapValidator`, so all are counted by `updateZapTotal` (there is no
     * async pending state like onchain zaps have).
     *
     * `@Volatile` for the same cross-thread visibility reason as [onchainZaps].
     */
    @Volatile
    var bolt12Zaps = mapOf<String, Bolt12ZapEntry>()
        private set

    // `@Volatile`: written under [syncLock], read lock-free by removeZapPayment's fast path.
    @Volatile
    var zapPayments = mapOf<Note, Note?>()
        private set

    // `@Volatile`: written under [syncLock] but read lock-free from the relay
    // socket thread and from [LocalCache.copyRelaysFromTo] on IO coroutines.
    @Volatile
    var relays = listOf<NormalizedRelayUrl>()
        private set

    open fun idNote() = toNEvent()

    open fun toNEvent(): String {
        // Rumors are cited by the envelope that delivered them: the rumor id
        // resolves to nothing on public relays and exposing it would leak the
        // private event's identity.
        val host = rumorHost
        return if (host != null) {
            NEvent.create(
                host.id,
                host.pubKey,
                host.kind,
                relayHintUrl(),
            )
        } else {
            NEvent.create(idHex, author?.pubkeyHex, event?.kind, relayHintUrl())
        }
    }

    fun relayUrls(): List<NormalizedRelayUrl> {
        val authorRelay = author?.relayHints() ?: emptyList()

        return authorRelay + relays
    }

    fun relayUrlsForReactions(): List<NormalizedRelayUrl> {
        val authorRelay = author?.inboxRelays() ?: emptyList()

        // A NIP-CC cache names where its finds belong, and that is exactly where Amethyst writes
        // them (LogGeocacheFindViewModel sends to the same list). Reading back from anywhere else
        // makes the write and the read land on disjoint relay sets: a cache whose log relays are
        // neither the owner's inbox nor wherever we happened to see the listing reports "No one
        // has logged this cache yet" while its logs sit precisely where the listing said to look.
        val declaredLogRelays = (event as? GeocacheListingEvent)?.logRelays() ?: emptyList()

        return authorRelay + relays + declaredLogRelays
    }

    fun relayHintUrl(): NormalizedRelayUrl? {
        // checks Community Events first
        when (val noteEvent = event) {
            is CommunityDefinitionEvent -> {
                noteEvent.relayUrls().firstOrNull()?.let { return it }
            }

            is IsInPublicChatChannel -> {
                inGatherers?.forEach {
                    if (it is Channel) {
                        it.relays().firstOrNull()?.let { return it }
                    }
                }
            }

            is LiveActivitiesEvent -> {
                noteEvent.relays().firstOrNull()?.let { return it }
            }

            is LiveActivitiesChatMessageEvent -> {
                inGatherers?.forEach {
                    if (it is Channel) {
                        it.relays().firstOrNull()?.let { return it }
                    }
                }
            }

            is EphemeralChatEvent -> {
                noteEvent.roomId()?.let { return it.relayUrl }
            }
        }

        // A short list (a handful of outbox relays): scanning it beats building a Set per call.
        val currentOutbox = author?.outboxRelays()

        return if (relays.isNotEmpty()) {
            if (!currentOutbox.isNullOrEmpty()) {
                val relayMatchesOutbox = relays.firstOrNull { it in currentOutbox }
                if (relayMatchesOutbox != null) {
                    return relayMatchesOutbox
                }
            }

            relays.firstOrNull()
        } else {
            currentOutbox?.firstOrNull() ?: author?.mostUsedDialableRelay()
        }
    }

    /** `nostr:` + [toNEvent], built in one buffer. */
    open fun toNostrUri(): String {
        val host = rumorHost
        return if (host != null) {
            NEvent.createNostrUri(host.id, host.pubKey, host.kind, relayHintUrl())
        } else {
            NEvent.createNostrUri(idHex, author?.pubkeyHex, event?.kind, relayHintUrl())
        }
    }

    open fun idDisplayNote() = idNote().toShortDisplay()

    open fun address(): Address? = null

    /**
     * This note's kind, known even before its event arrives: an addressable note carries its kind
     * in the address itself. Prefer this over `event?.kind` whenever a kind is being *excluded* --
     * an uncached note has a null event, so `event?.kind != SOME_KIND` silently passes for every
     * note that simply hasn't loaded yet.
     */
    fun kindOrNull(): Int? = address()?.kind ?: event?.kind

    fun isKind(kind: Int) = kindOrNull() == kind

    open fun createdAt() = event?.createdAt

    fun isDraft() = event is DraftWrapEvent

    /**
     * True when this note's event is an unsealed NIP-59 rumor (a private
     * reply, private reaction, or chat message that arrived inside a gift
     * wrap). Rumors are unsigned by design — they are materialized with an
     * empty signature — so they must never be e-tagged, quoted, reposted,
     * or rebroadcast on public relays: any public event referencing this
     * note's id leaks the private rumor id.
     */
    fun isPrivateRumor() = event?.sig?.isEmpty() == true

    fun loadEvent(
        event: Event,
        author: User,
        replyTo: List<Note>,
    ) {
        if (this.event?.id != event.id) {
            // The cache keeps this event, so this is where its strings get shared with the
            // rest of the cache: the id with this note's key (an addressable note's key is its
            // address instead), the pubkey with the author's, tag values through the intern
            // pool. Parsers no longer intern: most events they produce are never kept.
            event.shareStrings(if (this is AddressableNote) event.id else idHex, author.pubkeyHex, String::internIfPossible)
            this.event = event
            this.author = author
            this.replyTo = replyTo

            flowSet?.metadata?.invalidateData()
        }
    }

    fun hasZapsBoostsOrReactions(): Boolean =
        reactions.isNotEmpty() ||
            zaps.isNotEmpty() ||
            boosts.isNotEmpty() ||
            onchainZaps.isNotEmpty() ||
            nutzaps.isNotEmpty() ||
            bolt12Zaps.isNotEmpty()

    fun countReactions(): Int {
        var total = 0
        reactions.forEach { total += it.value.size }
        return total
    }

    fun addReply(note: Note) {
        syncLock.withLock {
            if (note !in replies) {
                replies = replies + note
                flowSet?.replies?.invalidateData()
            }
        }
    }

    fun removeReply(note: Note) {
        syncLock.withLock {
            if (note in replies) {
                replies = replies - note
                flowSet?.replies?.invalidateData()
            }
        }
    }

    fun addEdit(note: Note) {
        syncLock.withLock {
            if (note !in edits) {
                edits = edits + note
                flowSet?.edits?.invalidateData()
            }
        }
    }

    fun removeEdit(note: Note) {
        syncLock.withLock {
            if (note in edits) {
                edits = edits - note
                flowSet?.edits?.invalidateData()
            }
        }
    }

    fun addTimestamp(note: Note) {
        syncLock.withLock {
            if (note !in timestamps) {
                timestamps = timestamps + note
                flowSet?.ots?.invalidateData()
            }
        }
    }

    fun removeTimestamp(note: Note) {
        syncLock.withLock {
            if (note in timestamps) {
                timestamps = timestamps - note
                flowSet?.ots?.invalidateData()
            }
        }
    }

    fun removeBoost(note: Note) {
        syncLock.withLock {
            if (note in boosts) {
                boosts = boosts - note
                flowSet?.boosts?.invalidateData()
            }
        }
    }

    fun clearChildLinks(): List<Note> {
        val repliesChanged = replies.isNotEmpty()
        val reactionsChanged = reactions.isNotEmpty()
        val zapsChanged = zaps.isNotEmpty() || zapPayments.isNotEmpty() || onchainZaps.isNotEmpty() || nutzaps.isNotEmpty() || bolt12Zaps.isNotEmpty()
        val boostsChanged = boosts.isNotEmpty()
        val editsChanged = edits.isNotEmpty()
        val timestampsChanged = timestamps.isNotEmpty()
        val reportsChanged = reports.isNotEmpty()
        val labelsChanged = labels.isNotEmpty()

        val toBeRemoved =
            replies +
                reactions.values.flatten() +
                boosts +
                edits +
                timestamps +
                reports.values.flatten() +
                labels.values.flatten() +
                zaps.keys +
                zaps.values.filterNotNull() +
                zapPayments.keys +
                zapPayments.values.filterNotNull() +
                nutzaps.values.map { it.source } +
                onchainZaps.values.map { it.source } +
                bolt12Zaps.values.map { it.source }

        replies = listOf()
        reactions = mapOf()
        boosts = listOf()
        edits = listOf()
        timestamps = listOf()
        reports = mapOf()
        labels = mapOf()
        zaps = mapOf()
        onchainZaps = mapOf()
        onchainZapResolved = false
        nutzaps = mapOf()
        bolt12Zaps = mapOf()
        zapPayments = mapOf()
        zapsAmount = BigDecimal(0)
        relays = listOf()

        if (repliesChanged) flowSet?.replies?.invalidateData()
        if (reactionsChanged) flowSet?.reactions?.invalidateData()
        if (boostsChanged) flowSet?.boosts?.invalidateData()
        if (editsChanged) flowSet?.edits?.invalidateData()
        if (timestampsChanged) flowSet?.ots?.invalidateData()
        if (reportsChanged) flowSet?.reports?.invalidateData()
        if (labelsChanged) flowSet?.labels?.invalidateData()
        if (zapsChanged) flowSet?.zaps?.invalidateData()

        return toBeRemoved
    }

    /**
     * Fully detach this note from the notes below it in the graph so it can be
     * removed from the cache without leaving a partial deletion behind. It both
     * clears this note's own child collections (via [clearChildLinks]) and
     * drops this note from every child's [replyTo], so once this note leaves the
     * cache map nothing keeps the dead shell alive.
     *
     * This matters for the NIP-09 delete path: without severing the child →
     * parent `replyTo` links, the removed note leaks (held by each child) and a
     * later reply resolved through `computeReplyTo` would `getOrCreateNote` a
     * *second* Note for the same id — breaking the one-Note-per-id invariant.
     *
     * Returns the now-orphaned children (their other parents, if any, are kept).
     */
    fun detachFromChildren(): List<Note> {
        val children = clearChildLinks()
        children.forEach { child ->
            val parents = child.replyTo
            if (parents != null && this in parents) {
                child.replyTo = parents - this
            }
        }
        return children
    }

    fun removeReaction(note: Note) {
        syncLock.withLock {
            val tags = note.event?.tags ?: emptyArray()
            val reaction = note.event?.content?.firstFullCharOrEmoji(ImmutableListOfLists(tags)) ?: "+"

            if (reactions[reaction]?.contains(note) == true) {
                reactions[reaction]?.let {
                    if (note in it) {
                        val newList = it.minus(note)
                        if (newList.isEmpty()) {
                            reactions = reactions.minus(reaction)
                        } else {
                            reactions = reactions + Pair(reaction, newList)
                        }

                        flowSet?.reactions?.invalidateData()
                    }
                }
            }
        }
    }

    fun removeReport(deleteNote: Note) {
        syncLock.withLock {
            val author = deleteNote.author ?: return

            if (reports[author]?.contains(deleteNote) == true) {
                reports[author]?.let {
                    val newList = it.minus(deleteNote)
                    // Drop the author key when their last report goes, otherwise
                    // countReportAuthorsBy() keeps counting a deleted report.
                    reports =
                        if (newList.isEmpty()) {
                            reports.minus(author)
                        } else {
                            reports + Pair(author, newList)
                        }
                    flowSet?.reports?.invalidateData()
                }
            }
        }
    }

    // Pruning calls this for every child it frees, nearly always a reply or a reaction, so the
    // no-op is decided without the lock: only a zap request (key) or receipt (value) can be in
    // [zaps], and the map is an immutable snapshot published through a volatile field. A child that
    // is there is removed under syncLock, like the add paths: an unlocked remove wrote back a map
    // built from a snapshot, dropping any zap a relay thread added in between.
    fun removeZap(note: Note) {
        val event = note.event
        if (event != null && event !is ZapRequestEvent && event !is ZapReceiptEvent) return
        val current = zaps
        if (current.isEmpty() || (current[note] == null && !current.containsValue(note))) return

        syncLock.withLock {
            if (zaps[note] != null) {
                zaps = zaps.minus(note)
                updateZapTotal()
                flowSet?.zaps?.invalidateData()
            } else if (zaps.containsValue(note)) {
                zaps = zaps.filterValues { it != note }
                updateZapTotal()
                flowSet?.zaps?.invalidateData()
            }
        }
    }

    // Same fast path as [removeZap]: only NWC requests (keys) and responses (values) are in here.
    fun removeZapPayment(note: Note) {
        val event = note.event
        if (event != null && event !is NwcRequestEvent && event !is NwcResponseEvent) return
        val current = zapPayments
        if (current.isEmpty() || (!current.containsKey(note) && !current.containsValue(note))) return

        syncLock.withLock {
            if (zapPayments.containsKey(note)) {
                zapPayments = zapPayments.minus(note)
                flowSet?.zaps?.invalidateData()
            } else if (zapPayments.containsValue(note)) {
                zapPayments = zapPayments.filterValues { it != note }
                flowSet?.zaps?.invalidateData()
            }
        }
    }

    fun addBoost(note: Note) {
        syncLock.withLock {
            if (note !in boosts) {
                boosts = boosts + note
                flowSet?.boosts?.invalidateData()
            }
        }
    }

    private fun innerAddZap(
        zapRequest: Note,
        zap: Note?,
    ): Boolean =
        syncLock.withLock {
            if (zaps[zapRequest] == null) {
                zaps = zaps + Pair(zapRequest, zap)
                return@withLock true
            }
            return@withLock false
        }

    fun addZap(
        zapRequest: Note,
        zap: Note?,
    ) {
        if (zaps[zapRequest] == null) {
            val inserted = innerAddZap(zapRequest, zap)
            if (inserted && zap != null) {
                updateZapTotal()
                flowSet?.zaps?.invalidateData()
            }
        }
    }

    private fun innerAddOnchainZap(
        txid: String,
        entry: OnchainZapEntry,
    ): Boolean =
        syncLock.withLock {
            val existing = onchainZaps[txid]
            if (existing != null) {
                // Exact structural duplicate (same source Note + same fields) — typical
                // relay echo of the same event. Skip the rewrite to avoid spurious
                // flowSet invalidation.
                if (entry == existing) return@withLock false
                // Reject downgrades using the explicit OnchainZapStatus.level (not ordinal)
                // so the upgrade contract survives future enum reordering or insertions.
                if (entry.status.level < existing.status.level) return@withLock false
                // Same level: accept only when verifiedSats grows OR the source differs
                // (legitimate alternate signer republishing a split-zap receipt). A strictly
                // smaller verifiedSats is a backend downgrade and we ignore it.
                if (entry.status.level == existing.status.level && entry.verifiedSats < existing.verifiedSats) return@withLock false
            }
            onchainZaps = onchainZaps + Pair(txid, entry)
            return@withLock true
        }

    private fun innerRemoveOnchainZapForSource(
        txid: String,
        sourceAuthorPubKey: HexKey,
    ): Boolean =
        syncLock.withLock {
            val existing = onchainZaps[txid] ?: return@withLock false
            // Anti-spoof: only remove the entry if its source matches the rejecting event.
            // Otherwise a malicious third party could erase a legitimate CONFIRMED entry
            // by publishing a spoofed kind:8333 with the same txid but a bystander recipient.
            // Also refuse to remove a CONFIRMED entry — once chain-verified, only a fresh
            // CONFIRMED replacement should change it; a transient backend hiccup must not
            // wipe a previously-CONFIRMED entry just because some other target on the same
            // event is still UNVERIFIED.
            if (existing.status == OnchainZapStatus.CONFIRMED) return@withLock false
            if (existing.source.author?.pubkeyHex == null) return@withLock false
            if (existing.source.author?.pubkeyHex != sourceAuthorPubKey) return@withLock false
            onchainZaps = onchainZaps - txid
            return@withLock true
        }

    /**
     * Register a NIP-BC onchain zap targeting this note. `source` is the OnchainZapEvent's own
     * note — `source.author` is the sender shown in the reactions gallery.
     *
     * Call with [OnchainZapStatus.UNVERIFIED] (and `verifiedSats = 0`) at consumption time
     * to attach the zap optimistically so the user immediately sees their own outgoing zap
     * is processing. Call again with [OnchainZapStatus.PENDING] or [OnchainZapStatus.CONFIRMED]
     * (and the verified output sum) once the chain backend confirms it.
     *
     * `verifiedSats` MUST come from on-chain verification (sum of outputs paying the
     * recipient's derived Taproot address), not the sender-claimed `amount` tag.
     */
    fun addOnchainZap(
        source: Note,
        txid: String,
        claimedSats: Long,
        verifiedSats: Long,
        status: OnchainZapStatus,
    ) {
        val inserted = innerAddOnchainZap(txid, OnchainZapEntry(source, claimedSats, verifiedSats, status))
        if (inserted) {
            updateZapTotal()
            flowSet?.zaps?.invalidateData()
        }
    }

    /**
     * Remove a previously-attached onchain zap entry whose source matches [sourceAuthorPubKey].
     * Used when verification produced a hard rejection (e.g. the transaction paid zero to the
     * recipient — a spoof attempt). The source-scoped match prevents a malicious third party
     * from erasing a legitimate CONFIRMED entry by publishing a spoofed kind:8333 with the
     * same txid but a different recipient pubkey. Per-target CONFIRMED check also prevents
     * a transient backend reject from erasing an already-confirmed entry.
     */
    fun removeOnchainZapForSource(
        txid: String,
        sourceAuthorPubKey: HexKey,
    ) {
        val removed = innerRemoveOnchainZapForSource(txid, sourceAuthorPubKey)
        if (removed) {
            updateZapTotal()
            flowSet?.zaps?.invalidateData()
        }
    }

    private fun innerRemoveOnchainZapBySource(source: Note): Boolean =
        syncLock.withLock {
            val newMap = onchainZaps.filterValues { it.source != source }
            if (newMap.size == onchainZaps.size) return@withLock false
            onchainZaps = newMap
            return@withLock true
        }

    /**
     * Detach every onchain-zap entry whose source is [source] — used when the
     * source OnchainZapEvent note is being pruned from `LocalCache`. Unlike
     * [removeOnchainZapForSource] (a verification verdict that respects the
     * anti-spoof / no-CONFIRMED-downgrade guards), this is an unconditional
     * cache-removal that must drop the strong reference no matter the status,
     * otherwise the pruned source Note leaks through this map.
     */
    fun removeOnchainZapBySource(source: Note) {
        if (innerRemoveOnchainZapBySource(source)) {
            updateZapTotal()
            flowSet?.zaps?.invalidateData()
        }
    }

    private fun innerAddNutzap(
        eventId: HexKey,
        entry: NutzapEntry,
    ): Boolean =
        syncLock.withLock {
            if (nutzaps[eventId] != null) return@withLock false
            nutzaps = nutzaps + Pair(eventId, entry)
            return@withLock true
        }

    private fun innerRemoveNutzap(eventId: HexKey): Boolean =
        syncLock.withLock {
            if (nutzaps[eventId] == null) return@withLock false
            nutzaps = nutzaps - eventId
            return@withLock true
        }

    /**
     * Register a NIP-61 nutzap targeting this note. [source] is the kind:9321
     * event's own note — `source.author` is the sender shown in the reactions
     * gallery and the notifications card. [claimedSats] is the sum of `amount`
     * fields across the kind:9321's proof tags, computed once at consumption
     * time so this hot path stays JSON-parse-free.
     */
    fun addNutzap(
        source: Note,
        claimedSats: Long,
    ) {
        val eventId = source.event?.id ?: return
        if (innerAddNutzap(eventId, NutzapEntry(source, claimedSats))) {
            updateZapTotal()
            flowSet?.zaps?.invalidateData()
        }
    }

    fun removeNutzap(source: Note) {
        val eventId = source.event?.id ?: return
        if (innerRemoveNutzap(eventId)) {
            updateZapTotal()
            flowSet?.zaps?.invalidateData()
        }
    }

    private fun innerAddBolt12Zap(
        paymentHashHex: String,
        entry: Bolt12ZapEntry,
    ): Boolean =
        syncLock.withLock {
            val existing = bolt12Zaps[paymentHashHex]
            val merged =
                if (existing == null) {
                    entry
                } else if (entry.cryptoVerified != existing.cryptoVerified) {
                    // Cross-verification dedup. Only a crypto-verified proof has an amount
                    // bound by the invoice signature; an unverified one (a compressed proof,
                    // or an offer we can't bind) carries a self-chosen amount and payment
                    // hash. Because a payer proof publishes its `proof_preimage`, once any
                    // BOLT12 zap is public anyone can replay that preimage in a fresh,
                    // unverifiable proof with a 1-msat amount and the same payment hash — so
                    // if we let the lower amount win here and inherited the verified flag, a
                    // griefer could drive the counted total to ~zero and mislabel a fake
                    // amount as verified. Keep the verified entry outright, whichever amount
                    // it carries; never let an unverified duplicate override it.
                    if (entry.cryptoVerified) entry else existing
                } else {
                    // Same verification status: dedup by the settled payment and, if amounts
                    // differ, keep the LOWER — so a re-publish with a bigger amount tag can't
                    // inflate the total (NIP-B1). Both share the same cryptoVerified flag.
                    if (entry.amountMillisats < existing.amountMillisats) entry else existing
                }
            if (merged == existing) return@withLock false
            bolt12Zaps = bolt12Zaps + Pair(paymentHashHex, merged)
            return@withLock true
        }

    private fun innerRemoveBolt12ZapBySource(source: Note): Boolean =
        syncLock.withLock {
            val newMap = bolt12Zaps.filterValues { it.source != source }
            if (newMap.size == bolt12Zaps.size) return@withLock false
            bolt12Zaps = newMap
            return@withLock true
        }

    /**
     * Register a NIP-B1 BOLT12 zap targeting this note. [source] is the kind:9736
     * event's own note — `source.author` is the payer shown in the reactions
     * gallery and notifications. [amountMillisats] and [cryptoVerified] come from
     * the synchronous [com.vitorpamplona.quartz.nipB1Bolt12Zaps.verify.Bolt12ZapValidator]
     * verdict; the caller MUST only call this for a `Valid` result. Deduplicated by
     * [paymentHashHex] (the proof's `invoice_payment_hash`).
     */
    fun addBolt12Zap(
        source: Note,
        paymentHashHex: String,
        amountMillisats: Long,
        cryptoVerified: Boolean,
    ) {
        if (innerAddBolt12Zap(paymentHashHex, Bolt12ZapEntry(source, amountMillisats, cryptoVerified))) {
            updateZapTotal()
            flowSet?.zaps?.invalidateData()
        }
    }

    /** Detach every BOLT12-zap entry contributed by [source] — used when the source note is pruned or deleted. */
    fun removeBolt12ZapBySource(source: Note) {
        if (innerRemoveBolt12ZapBySource(source)) {
            updateZapTotal()
            flowSet?.zaps?.invalidateData()
        }
    }

    private fun innerAddZapPayment(
        zapPaymentRequest: Note,
        zapPayment: Note?,
    ): Boolean =
        syncLock.withLock {
            if (zapPayments[zapPaymentRequest] == null) {
                zapPayments = zapPayments + Pair(zapPaymentRequest, zapPayment)
                return@withLock true
            }
            return@withLock false
        }

    fun addZapPayment(
        zapPaymentRequest: Note,
        zapPayment: Note?,
    ) {
        if (zapPayments[zapPaymentRequest] == null) {
            val inserted = innerAddZapPayment(zapPaymentRequest, zapPayment)
            if (inserted) {
                flowSet?.zaps?.invalidateData()
            }
        }
    }

    fun addReaction(note: Note) {
        syncLock.withLock {
            val tags = note.event?.tags ?: emptyArray()
            val reaction = note.event?.content?.firstFullCharOrEmoji(ImmutableListOfLists(tags)) ?: "+"

            val listOfAuthors = reactions[reaction]
            if (listOfAuthors == null) {
                reactions = reactions + Pair(reaction, listOf(note))
                flowSet?.reactions?.invalidateData()
            } else if (!listOfAuthors.contains(note)) {
                reactions = reactions + Pair(reaction, listOfAuthors + note)
                flowSet?.reactions?.invalidateData()
            }
        }
    }

    fun addReport(note: Note) {
        syncLock.withLock {
            val author = note.author ?: return

            val reportsByAuthor = reports[author]

            if (reportsByAuthor == null) {
                reports = reports + Pair(author, listOf(note))
                flowSet?.reports?.invalidateData()
            } else if (!reportsByAuthor.contains(note)) {
                reports = reports + Pair(author, reportsByAuthor + note)
                flowSet?.reports?.invalidateData()
            }
        }
    }

    /** Attach a NIP-32 LabelEvent note as having tagged this note with [hashtag] (lowercased). */
    fun addLabel(
        hashtag: String,
        note: Note,
    ) {
        syncLock.withLock {
            val listOfLabelers = labels[hashtag]
            if (listOfLabelers == null) {
                labels = labels + Pair(hashtag, listOf(note))
                flowSet?.labels?.invalidateData()
            } else if (!listOfLabelers.contains(note)) {
                labels = labels + Pair(hashtag, listOfLabelers + note)
                flowSet?.labels?.invalidateData()
            }
        }
    }

    /** Detach a LabelEvent note (e.g. deleted) from every hashtag bucket it was in. */
    fun removeLabel(note: Note) {
        syncLock.withLock {
            if (labels.none { it.value.contains(note) }) return

            labels =
                labels
                    .mapValues { it.value - note }
                    .filterValues { it.isNotEmpty() }
            flowSet?.labels?.invalidateData()
        }
    }

    fun addRelaySync(relay: NormalizedRelayUrl) =
        syncLock.withLock {
            if (relay !in relays) {
                relays = relays + relay
            }
        }

    fun hasRelay(relay: NormalizedRelayUrl) = relay in relays

    fun addRelay(relay: NormalizedRelayUrl) {
        if (relay !in relays) {
            addRelaySync(relay)
            flowSet?.relays?.invalidateData()
        }
    }

    private suspend fun isPaidByCalculation(
        zapPayments: List<Pair<Note, Note?>>,
        afterTimeInSeconds: Long,
        account: IAccount,
    ): Boolean {
        if (zapPayments.isEmpty()) {
            return false
        }

        return anyAsync(zapPayments) { next ->
            val zapResponseEvent = next.second?.event as? NwcResponseEvent

            if (zapResponseEvent != null) {
                val response = account.nip47SignerState.decryptResponse(zapResponseEvent)
                if (response != null) {
                    response is PayInvoiceSuccessResponse &&
                        account.nip47SignerState.isNIP47Author(zapResponseEvent.requestAuthor()) &&
                        zapResponseEvent.createdAt > afterTimeInSeconds
                } else {
                    false
                }
            } else {
                false
            }
        }
    }

    private suspend fun isZappedByCalculation(
        option: Int?,
        user: User,
        afterTimeInSeconds: Long,
        account: IAccount,
        zapEvents: Map<Note, Note?>,
    ): Boolean {
        if (zapEvents.isEmpty()) {
            return false
        }

        val parallelDecrypt = mutableListOf<Pair<ZapRequestEvent, ZapReceiptEvent>>()

        zapEvents.forEach { next ->
            val zapRequest = next.key.event as ZapRequestEvent
            val zapEvent = next.value?.event as? ZapReceiptEvent

            if (zapEvent != null) {
                if (!zapRequest.isPrivateZap()) {
                    // public events
                    if (zapRequest.pubKey == user.pubkeyHex &&
                        zapEvent.createdAt > afterTimeInSeconds &&
                        (option == null || option == zapEvent.zappedPollOption())
                    ) {
                        return true
                    }
                } else {
                    // private events

                    // if has already decrypted
                    val privateZap = account.privateZapsDecryptionCache.cachedPrivateZap(zapRequest)
                    if (privateZap != null) {
                        if (privateZap.pubKey == user.pubkeyHex &&
                            zapEvent.createdAt > afterTimeInSeconds &&
                            (option == null || option == zapEvent.zappedPollOption())
                        ) {
                            return true
                        }
                    } else {
                        if (account.isWriteable()) {
                            parallelDecrypt.add(Pair(zapRequest, zapEvent))
                        }
                    }
                }
            }
        }

        if (parallelDecrypt.isEmpty()) {
            return false
        }

        return anyAsync(parallelDecrypt) { pair ->
            val result = account.privateZapsDecryptionCache.decryptPrivateZap(pair.first)
            result?.pubKey == user.pubkeyHex &&
                pair.second.createdAt > afterTimeInSeconds &&
                (option == null || option == pair.second.zappedPollOption())
        }
    }

    suspend fun isZappedBy(
        user: User,
        afterTimeInSeconds: Long,
        account: IAccount,
    ): Boolean {
        // NIP-61 nutzaps and NIP-BC onchain zaps: the sender is the
        // source event's pubkey, so the check is direct — no
        // private-zap decryption needed. Run these first; they're
        // in-memory scans and a hit short-circuits the more expensive
        // lightning path (which may have to decrypt NIP-44-private
        // zap requests).
        if (isNutzappedBy(user, afterTimeInSeconds)) return true
        if (isOnchainZappedBy(user, afterTimeInSeconds)) return true
        if (isBolt12ZappedBy(user, afterTimeInSeconds)) return true

        val first = isZappedByCalculation(null, user, afterTimeInSeconds, account, zaps)
        if (first) return true
        if (account.userProfile() == user) {
            return isPaidByCalculation(zapPayments.toList(), afterTimeInSeconds, account)
        }
        return false
    }

    private fun isNutzappedBy(
        user: User,
        afterTimeInSeconds: Long,
    ): Boolean =
        nutzaps.values.any { entry ->
            val sourceEvent = entry.source.event ?: return@any false
            entry.source.author == user && sourceEvent.createdAt > afterTimeInSeconds
        }

    private fun isOnchainZappedBy(
        user: User,
        afterTimeInSeconds: Long,
    ): Boolean =
        onchainZaps.values.any { entry ->
            val sourceEvent = entry.source.event ?: return@any false
            entry.source.author == user && sourceEvent.createdAt > afterTimeInSeconds
        }

    private fun isBolt12ZappedBy(
        user: User,
        afterTimeInSeconds: Long,
    ): Boolean =
        bolt12Zaps.values.any { entry ->
            val sourceEvent = entry.source.event ?: return@any false
            entry.source.author == user && sourceEvent.createdAt > afterTimeInSeconds
        }

    /**
     * Extra sats to add on top of [zapsAmount] for the reaction-row
     * counter when the signed-in user has outgoing onchain zaps on
     * this note that aren't yet CONFIRMED. [updateZapTotal] only
     * counts CONFIRMED onchain entries (verifiedSats) because incoming
     * sender-claimed amounts are spoofable. The signed-in user's OWN
     * outgoing zap is trusted at face value though — they know what
     * they sent — so the counter should reflect it immediately, the
     * same way the gallery shows their own UNVERIFIED entry with the
     * claimed amount. Other senders' non-confirmed entries still
     * contribute 0 here.
     */
    fun extraOwnPendingOnchainSats(loggedInPubKey: HexKey?): Long {
        if (loggedInPubKey == null) return 0L
        var sum = 0L
        onchainZaps.values.forEach { entry ->
            if (entry.status != OnchainZapStatus.CONFIRMED &&
                entry.source.author?.pubkeyHex == loggedInPubKey
            ) {
                sum += entry.claimedSats
            }
        }
        return sum
    }

    suspend fun isZappedBy(
        option: Int?,
        user: User,
        afterTimeInSeconds: Long,
        account: IAccount,
    ): Boolean = isZappedByCalculation(option, user, afterTimeInSeconds, account, zaps)

    fun getReactionBy(user: User): String? =
        reactions.firstNotNullOfOrNull {
            if (it.value.any { it.author?.pubkeyHex == user.pubkeyHex }) {
                it.key
            } else {
                null
            }
        }

    fun isBoostedBy(user: User): Boolean = boosts.any { it.author?.pubkeyHex == user.pubkeyHex }

    fun hasReportsBy(user: User): Boolean = reports[user]?.isNotEmpty() ?: false

    fun countReportAuthorsBy(users: Set<HexKey>): Int = reports.count { it.key.pubkeyHex in users }

    fun reportsBy(users: Set<HexKey>): List<Note> =
        reports
            .mapNotNull {
                if (it.key.pubkeyHex in users) {
                    it.value
                } else {
                    null
                }
            }.flatten()

    private fun updateZapTotal() {
        var sumOfAmounts = BigDecimal(0)

        // Regular Zap Receipts
        zaps.forEach {
            val noteEvent = it.value?.event
            if (noteEvent is ZapReceiptEvent) {
                sumOfAmounts += noteEvent.amount ?: BigDecimal(0)
            }
        }

        // NIP-BC onchain zaps — verified amounts only, confirmed only.
        // Unverified/pending entries are tracked but excluded from the total per spec.
        onchainZaps.values.forEach { entry ->
            if (entry.status == OnchainZapStatus.CONFIRMED) {
                sumOfAmounts += BigDecimal(entry.verifiedSats)
            }
        }

        // NIP-61 nutzaps — claimed amounts. Cashu proofs are verifiable
        // against the mint, but the verification is the recipient's
        // wallet's job at redeem time, not the cache. Until we have a
        // wallet-side gate (analogous to OnchainZapStatus), trust the
        // sender's claim so the reaction-row total reflects what the
        // sender intended to send.
        nutzaps.values.forEach { entry ->
            sumOfAmounts += BigDecimal(entry.claimedSats)
        }

        // NIP-B1 BOLT12 zaps — count only the crypto-verified ones. An unverified
        // (compressed, or offer-unbindable) proof carries a self-chosen preimage,
        // amount, and payment hash with no settled-payment guarantee, so counting it
        // would let anyone inflate a note's total for free. Unverified entries stay
        // stored and shown (dimmed) but never sum. Divide in BigDecimal so fractional
        // sats survive and match the millisat-native lightning column above.
        bolt12Zaps.values.forEach { entry ->
            if (entry.cryptoVerified) {
                sumOfAmounts += BigDecimal(entry.amountMillisats).divide(BigDecimal(1000))
            }
        }

        zapsAmount = sumOfAmounts
    }

    private suspend fun zappedAmountCalculation(
        startAmount: BigDecimal,
        paidInvoiceSet: LinkedHashSet<String>,
        zapPayments: List<Pair<Note, Note?>>,
        signerState: INwcSignerState,
    ): BigDecimal {
        if (zapPayments.isEmpty()) {
            return startAmount
        }

        var output: BigDecimal = startAmount

        launchAndWaitAll(zapPayments) { next ->
            val result =
                processZapAmountFromResponse(
                    next.first,
                    next.second,
                    signerState,
                )

            if (result != null && !paidInvoiceSet.contains(result.invoice)) {
                paidInvoiceSet.add(result.invoice)
                output = output.add(result.amount)
            }
        }

        return output
    }

    private suspend fun processZapAmountFromResponse(
        paymentRequest: Note,
        paymentResponse: Note?,
        signerState: INwcSignerState,
    ): InvoiceAmount? {
        val nwcRequest = paymentRequest.event as? NwcRequestEvent
        val nwcResponse = paymentResponse?.event as? NwcResponseEvent

        return if (nwcRequest != null && nwcResponse != null) {
            processZapAmountFromResponse(
                nwcRequest,
                nwcResponse,
                signerState,
            )
        } else {
            null
        }
    }

    class InvoiceAmount(
        val invoice: String,
        val amount: BigDecimal,
    )

    private suspend fun processZapAmountFromResponse(
        nwcRequest: NwcRequestEvent,
        nwcResponse: NwcResponseEvent,
        signerState: INwcSignerState,
    ): InvoiceAmount? {
        // if we can decrypt the reply
        return signerState.decryptResponse(nwcResponse)?.let { noteEvent ->
            // if it is a sucess
            if (noteEvent is PayInvoiceSuccessResponse) {
                // if we can decrypt the invoice
                val request = signerState.decryptRequest(nwcRequest)
                val invoice = (request as? PayInvoiceMethod)?.params?.invoice
                if (invoice != null) {
                    // if we can parse the amount
                    val amount =
                        try {
                            LnInvoiceUtil.getAmountInSats(invoice)
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            null
                        }

                    // avoid double counting
                    if (amount != null) {
                        InvoiceAmount(invoice, amount)
                    } else {
                        null
                    }
                } else {
                    null
                }
            } else {
                null
            }
        }
    }

    suspend fun zappedAmountWithNWCPayments(signerState: INwcSignerState): BigDecimal {
        if (zapPayments.isEmpty()) {
            return zapsAmount
        }

        val invoiceSet = LinkedHashSet<String>(zaps.size + zapPayments.size)
        zaps.forEach { (it.value?.event as? ZapReceiptEvent)?.lnInvoice()?.let { invoiceSet.add(it) } }

        return zappedAmountCalculation(
            zapsAmount,
            invoiceSet,
            zapPayments.toList(),
            signerState,
        )
    }

    fun hasReport(
        loggedIn: User,
        type: ReportType,
    ): Boolean =
        reports[loggedIn]?.firstOrNull {
            it.event is ReportEvent &&
                (it.event as ReportEvent).reportedAuthor().any { it.type == type }
        } != null

    fun hasPledgeBy(user: User): Boolean =
        replies
            .filter { it.event?.hasAdditionalReward() ?: false }
            .any {
                val pledgeValue =
                    try {
                        it.event?.content?.let { content -> BigDecimal(content) }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        null
                        // do nothing if it can't convert to bigdecimal
                    }

                pledgeValue != null && it.author == user
            }

    // Manual fold rather than Iterable.sumOf { -> BigDecimal } because that
    // overload is JVM-only; the common stdlib only ships sumOf for the
    // primitive numeric types.
    fun pledgedAmountByOthers(): BigDecimal =
        replies.fold(BigDecimal(0)) { acc, note ->
            acc + (note.event?.addedRewardValue() ?: BigDecimal(0))
        }

    fun hasAnyReports(): Boolean {
        val dayAgo = TimeUtils.oneDayAgo()

        if (reports.isNotEmpty()) return true

        return author?.reportsOrNull()?.hasReportNewerThan(dayAgo) ?: false
    }

    fun isNewThread(): Boolean {
        val event = event
        return (
            event is RepostEvent ||
                event is GenericRepostEvent ||
                replyTo == null ||
                replyTo?.size == 0
        ) &&
            // A comment scoped to an external identifier (`I` tag) is a reply to that
            // scope, even though there is no in-cache parent note to populate replyTo.
            !(event is CommentEvent && event.hasRootScopeIdentifier()) &&
            event !is ChannelMessageEvent &&
            event !is LiveActivitiesChatMessageEvent
    }

    fun hasZapped(loggedIn: User): Boolean =
        zaps.any { it.key.author == loggedIn } ||
            nutzaps.values.any { it.source.author == loggedIn } ||
            bolt12Zaps.values.any { it.source.author == loggedIn }

    fun hasReacted(
        loggedIn: User,
        content: String,
    ): Boolean = allReactionsOfContentByAuthor(loggedIn, content).isNotEmpty()

    fun allReactionsOfContentByAuthor(
        loggedIn: User,
        content: String,
    ): List<Note> = reactions[content]?.filter { it.author == loggedIn } ?: emptyList()

    fun allReactionsByAuthor(loggedIn: User): List<String> = reactions.filter { it.value.any { it.author == loggedIn } }.mapNotNull { it.key }

    fun hasBoostedInTheLast5Minutes(loggedIn: User): Boolean {
        val fiveMinsAgo = TimeUtils.fiveMinutesAgo()
        return boosts.any {
            it.author == loggedIn && (it.createdAt() ?: 0L) > fiveMinsAgo
        }
    }

    fun hasBoostedInTheLast5Minutes(loggedIn: HexKey): Boolean {
        val fiveMinsAgo = TimeUtils.fiveMinutesAgo()
        return boosts.any {
            (it.createdAt() ?: 0L) > fiveMinsAgo && it.author?.pubkeyHex == loggedIn
        }
    }

    fun boostedBy(loggedIn: User): List<Note> = boosts.filter { it.author == loggedIn }

    fun moveAllReferencesTo(note: AddressableNote) {
        // migrates these comments to a new version
        replies.forEach {
            note.addReply(it)
            it.replyTo = it.replyTo?.replace(this, note)
        }
        reactions.forEach {
            it.value.forEach {
                note.addReaction(it)
                it.replyTo = it.replyTo?.replace(this, note)
            }
        }
        boosts.forEach {
            note.addBoost(it)
            it.replyTo = it.replyTo?.replace(this, note)
        }
        reports.forEach {
            it.value.forEach {
                note.addReport(it)
                it.replyTo = it.replyTo?.replace(this, note)
            }
        }
        zaps.forEach {
            note.addZap(it.key, it.value)
            it.key.replyTo = it.key.replyTo?.replace(this, note)
            it.value?.replyTo = it.value?.replyTo?.replace(this, note)
        }
        nutzaps.values.forEach {
            note.addNutzap(it.source, it.claimedSats)
            it.source.replyTo = it.source.replyTo?.replace(this, note)
        }
        onchainZaps.forEach { (txid, entry) ->
            note.addOnchainZap(entry.source, txid, entry.claimedSats, entry.verifiedSats, entry.status)
            entry.source.replyTo = entry.source.replyTo?.replace(this, note)
        }
        bolt12Zaps.forEach { (paymentHash, entry) ->
            note.addBolt12Zap(entry.source, paymentHash, entry.amountMillisats, entry.cryptoVerified)
            entry.source.replyTo = entry.source.replyTo?.replace(this, note)
        }
        zapPayments.forEach {
            note.addZapPayment(it.key, it.value)
            it.key.replyTo = it.key.replyTo?.replace(this, note)
            it.value?.replyTo = it.value?.replyTo?.replace(this, note)
        }
        labels.forEach { (hashtag, labelNotes) ->
            labelNotes.forEach {
                note.addLabel(hashtag, it)
                it.replyTo = it.replyTo?.replace(this, note)
            }
        }

        replyTo = null
        replies = emptyList()
        reactions = emptyMap()
        boosts = emptyList()
        reports = emptyMap()
        zaps = emptyMap()
        nutzaps = emptyMap()
        onchainZaps = emptyMap()
        bolt12Zaps = emptyMap()
        zapPayments = emptyMap()
        labels = emptyMap()
        zapsAmount = BigDecimal(0)
    }

    fun isHiddenFor(accountChoices: LiveHiddenUsers): Boolean {
        val thisEvent = event ?: return false
        val hash = thisEvent.pubKey.hashCode()

        // if the author is hidden by spam or blocked
        if (accountChoices.hiddenUsersHashCodes.contains(hash) ||
            accountChoices.spammersHashCodes.contains(hash)
        ) {
            return true
        }

        if (accountChoices.mutedThreads.isNotEmpty() &&
            accountChoices.mutedThreads.contains(thisEvent.threadRootIdOrSelf())
        ) {
            return true
        }

        // if the post is sensitive and the user doesn't want to see sensitive content
        if (accountChoices.showSensitiveContent == false && thisEvent.isSensitiveOrNSFW()) {
            return true
        }

        // if this is a repost, consider the inner event.
        if (
            thisEvent is GenericRepostEvent ||
            thisEvent is RepostEvent ||
            thisEvent is CommunityPostApprovalEvent
        ) {
            if (replyTo?.lastOrNull()?.isHiddenFor(accountChoices) == true) {
                return true
            }
        }

        if (accountChoices.hiddenHashtags.isNotEmpty()) {
            if (thisEvent.anyHashTag { it.lowercase() in accountChoices.hiddenHashtags }) {
                return true
            }
        }

        if (accountChoices.hiddenWordsCase.isNotEmpty()) {
            if (thisEvent is BaseThreadedEvent && thisEvent.content.containsAny(accountChoices.hiddenWordsCase)) {
                return true
            }

            if (thisEvent is CommentEvent && thisEvent.isScoped { it.containsAny(accountChoices.hiddenWordsCase) }) {
                return true
            }

            if (thisEvent.anyHashTag { it.containsAny(accountChoices.hiddenWordsCase) }) {
                return true
            }

            if (author?.metadataOrNull()?.anyPropertyContains(accountChoices.hiddenWordsCase) == true) return true
        }

        return false
    }

    // `@Volatile`: created/destroyed under [syncLock] but read lock-free by
    // every `flowSet?.x?.invalidateData()` call on writer threads.
    @Volatile
    var flowSet: NoteFlowSet? = null

    fun createOrDestroyFlowSync(create: Boolean) =
        syncLock.withLock {
            if (create) {
                if (flowSet == null) {
                    flowSet = NoteFlowSet(this)
                }
            } else {
                if (flowSet != null && flowSet?.isInUse() == false) {
                    flowSet = null
                }
            }
        }

    fun flow(): NoteFlowSet =
        // Fast path reads the @Volatile field once; the slow path re-checks under
        // the same lock createOrDestroyFlowSync() uses, so a concurrent clearFlow()
        // between the two can't leave us dereferencing a just-nulled set.
        flowSet ?: syncLock.withLock {
            flowSet ?: NoteFlowSet(this).also { flowSet = it }
        }

    fun clearFlow() {
        if (flowSet != null && flowSet?.isInUse() == false) {
            createOrDestroyFlowSync(false)
        }
    }

    inline fun <reified T : Event> toEventHint(): EventHintBundle<T>? {
        val safeEvent = event
        return if (safeEvent is T) {
            EventHintBundle(safeEvent, relayHintUrl(), author?.bestRelayHint())
        } else {
            null
        }
    }
}

@Stable
class NoteFlowSet(
    u: Note,
) {
    // Observers line up here.
    val metadata = NoteBundledRefresherFlow(u)
    val reports = NoteBundledRefresherFlow(u)
    val relays = NoteBundledRefresherFlow(u)
    val reactions = NoteBundledRefresherFlow(u)
    val boosts = NoteBundledRefresherFlow(u)
    val replies = NoteBundledRefresherFlow(u)
    val zaps = NoteBundledRefresherFlow(u)
    val ots = NoteBundledRefresherFlow(u)
    val edits = NoteBundledRefresherFlow(u)
    val labels = NoteBundledRefresherFlow(u)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun author() =
        metadata.stateFlow.flatMapLatest {
            it.note.author
                ?.metadata()
                ?.flow ?: MutableStateFlow(null)
        }

    fun isInUse(): Boolean =
        metadata.hasObservers() ||
            reports.hasObservers() ||
            relays.hasObservers() ||
            reactions.hasObservers() ||
            boosts.hasObservers() ||
            replies.hasObservers() ||
            zaps.hasObservers() ||
            ots.hasObservers() ||
            edits.hasObservers() ||
            labels.hasObservers()
}

@Stable
class NoteBundledRefresherFlow(
    val note: Note,
) {
    // Refreshes observers in batches.
    val stateFlow = MutableStateFlow(NoteState(note))

    fun invalidateData() {
        stateFlow.tryEmit(NoteState(note))
    }

    fun hasObservers() = stateFlow.subscriptionCount.value > 0
}

@Immutable
class NoteState(
    val note: Note,
)

fun List<AddressableNote>.eventIdSet() = mapNotNullTo(mutableSetOf<HexKey>()) { it.event?.id }

inline fun <reified T : Event> Array<NoteState>.events() = mapNotNull { it.note.event as? T }

inline fun <reified T : Event> List<AddressableNote>.events() = mapNotNull { it.event as? T }

inline fun <reified T : Event> List<AddressableNote>.updateFlow(): Flow<List<T>> =
    if (this.isEmpty()) {
        MutableStateFlow(emptyList())
    } else {
        combine(
            flows = this.map { it.flow().metadata.stateFlow },
        ) {
            it.events()
        }
    }

inline fun <reified T : Event> Iterable<Note>.anyEvent(predicate: (T) -> Boolean): Boolean {
    if (this is Collection && isEmpty()) return false
    for (note in this) {
        val noteEvent = note.event as? T
        if (noteEvent != null && predicate(noteEvent)) return true
    }
    return false
}

inline fun <reified T : Event> Iterable<Note>.filterEvents(predicate: (T) -> Boolean): List<T> {
    if (this is Collection && isEmpty()) return emptyList()

    val dest = ArrayList<T>()
    for (note in this) {
        val noteEvent = note.event as? T
        if (noteEvent != null && predicate(noteEvent)) {
            dest.add(noteEvent)
        }
    }
    return dest
}

inline fun <reified T : Event> Iterable<Note>.filterAuthoredEvents(pubkey: HexKey): List<T> {
    if (this is Collection && isEmpty()) return emptyList()

    val dest = ArrayList<T>()
    for (note in this) {
        if (note.author?.pubkeyHex != pubkey) {
            val noteEvent = note.event as? T
            if (noteEvent != null) {
                dest.add(noteEvent)
            }
        }
    }
    return dest
}

inline fun Iterable<Note>.anyNotNullEvent(predicate: (Event) -> Boolean): Boolean {
    if (this is Collection && isEmpty()) return false
    for (note in this) {
        val noteEvent = note.event
        if (noteEvent != null && predicate(noteEvent)) return true
    }
    return false
}

inline fun <reified T : Event> List<Note>.latestByAuthor(): Map<User, T> {
    val oneResponsePerUser = mutableMapOf<User, T>()

    forEach { note ->
        val author = note.author ?: return@forEach
        val event = note.event as? T ?: return@forEach

        val currentResponse = oneResponsePerUser[author]
        if (currentResponse == null) {
            oneResponsePerUser[author] = event
        } else if (event.createdAt > currentResponse.createdAt) {
            oneResponsePerUser[author] = event
        }
    }

    return oneResponsePerUser
}
