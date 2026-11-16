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

import com.vitorpamplona.amethyst.commons.actions.ConcordActions
import com.vitorpamplona.amethyst.commons.actions.ConcordChannelPins
import com.vitorpamplona.amethyst.commons.actions.ConcordLocalEdit
import com.vitorpamplona.amethyst.commons.actions.ConcordModeration
import com.vitorpamplona.amethyst.commons.actions.ConcordPinContext
import com.vitorpamplona.amethyst.commons.actions.ConcordPinOutcome
import com.vitorpamplona.amethyst.commons.actions.ConcordPinWrite
import com.vitorpamplona.amethyst.commons.actions.ConcordPinnedMessage
import com.vitorpamplona.amethyst.commons.actions.ConcordPinning
import com.vitorpamplona.amethyst.commons.actions.ConcordPrivateChannels
import com.vitorpamplona.amethyst.commons.actions.ConcordReceive
import com.vitorpamplona.amethyst.commons.actions.ConcordSubscriptionPlanner
import com.vitorpamplona.amethyst.commons.actions.toRumor
import com.vitorpamplona.amethyst.commons.defaults.DefaultDmIndexerRelays
import com.vitorpamplona.amethyst.commons.model.ConcordInviteResult
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.filter
import com.vitorpamplona.amethyst.commons.model.chats.ChatFeedType
import com.vitorpamplona.amethyst.commons.model.chats.ConcordDirectInviteNote
import com.vitorpamplona.amethyst.commons.model.concord.ConcordChannel
import com.vitorpamplona.amethyst.commons.model.concord.ConcordChannelListState
import com.vitorpamplona.amethyst.commons.model.concord.ConcordCommunitySession
import com.vitorpamplona.amethyst.commons.model.concord.ConcordDirectInviteInbox
import com.vitorpamplona.amethyst.commons.model.concord.ConcordDirectInviteView
import com.vitorpamplona.amethyst.commons.model.concord.ConcordKickNotice
import com.vitorpamplona.amethyst.commons.model.concord.ConcordPinDutyScheduler
import com.vitorpamplona.amethyst.commons.model.concord.DirectInviteAcceptPlan
import com.vitorpamplona.amethyst.commons.model.concordChannelLastReadRoute
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.taggedAs
import com.vitorpamplona.amethyst.commons.relays.dialableRelays
import com.vitorpamplona.amethyst.commons.util.ConcurrentSet
import com.vitorpamplona.amethyst.commons.viewmodels.ReplyMode
import com.vitorpamplona.crypto.random.RandomInstance
import com.vitorpamplona.marmot.RecipientRelayFetcher
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityList.withControlRoot
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEntry
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEvent
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListFragmentEvent
import com.vitorpamplona.quartz.concord.cord02Community.ConcordDissolution
import com.vitorpamplona.quartz.concord.cord02Community.ConcordListIncompleteException
import com.vitorpamplona.quartz.concord.cord02Community.ConcordListTooLargeException
import com.vitorpamplona.quartz.concord.cord02Community.HeldRoot
import com.vitorpamplona.quartz.concord.cord02Community.ImagePointer
import com.vitorpamplona.quartz.concord.cord03Channels.ChannelChat
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordChannelKeyring
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordChatEditEvent
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordDisappearing
import com.vitorpamplona.quartz.concord.cord03Channels.concordEpoch
import com.vitorpamplona.quartz.concord.cord04Roles.AuthorityCitation
import com.vitorpamplona.quartz.concord.cord04Roles.AuthorityCitations
import com.vitorpamplona.quartz.concord.cord04Roles.AuthorityResolver
import com.vitorpamplona.quartz.concord.cord04Roles.ChannelEntity
import com.vitorpamplona.quartz.concord.cord04Roles.ConcordLimits
import com.vitorpamplona.quartz.concord.cord04Roles.ConcordPermissions
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEdition
import com.vitorpamplona.quartz.concord.cord04Roles.MetadataEntity
import com.vitorpamplona.quartz.concord.cord04Roles.RoleEntity
import com.vitorpamplona.quartz.concord.cord05Invites.CommunityInvite
import com.vitorpamplona.quartz.concord.cord05Invites.ConcordInviteList
import com.vitorpamplona.quartz.concord.cord05Invites.ConcordInviteListDocument
import com.vitorpamplona.quartz.concord.cord05Invites.ConcordInviteListEntry
import com.vitorpamplona.quartz.concord.cord05Invites.ConcordInviteListEvent
import com.vitorpamplona.quartz.concord.cord05Invites.ConcordInviteListTombstone
import com.vitorpamplona.quartz.concord.cord05Invites.ConcordInviteRegistry
import com.vitorpamplona.quartz.concord.cord05Invites.ConcordInviteVend
import com.vitorpamplona.quartz.concord.cord05Invites.InviteBundleStatus
import com.vitorpamplona.quartz.concord.cord05Invites.InviteRelayDictionary
import com.vitorpamplona.quartz.concord.cord06Rekey.ChannelRekeyOutcome
import com.vitorpamplona.quartz.concord.cord06Rekey.ConcordChannelRekey
import com.vitorpamplona.quartz.concord.cord06Rekey.ConcordRefounding
import com.vitorpamplona.quartz.concord.cord06Rekey.IncompleteControlPlaneException
import com.vitorpamplona.quartz.concord.cord06Rekey.PendingRefounding
import com.vitorpamplona.quartz.concord.cord06Rekey.ReceivedRefounding
import com.vitorpamplona.quartz.concord.crypto.ControlPlaneKeys
import com.vitorpamplona.quartz.concord.crypto.GroupKey
import com.vitorpamplona.quartz.concord.envelope.ConcordStreamEnvelope
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.PagedFetchResult
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAll
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAllPages
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAllPagesFromPool
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAllWithHooks
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.publishAndConfirm
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip92IMeta.IMetaTag
import com.vitorpamplona.quartz.nip92IMeta.imetas
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.concurrent.ConcurrentMap
import com.vitorpamplona.quartz.utils.runCatchingCancellable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

/** Name of the default Concord community Admin role minted by "Make admin". */
private const val CONCORD_ADMIN_ROLE = "Admin"

/** How long a join waits for the new community's session before handing it the wraps it fetched. */
private const val SESSION_WAIT_MS = 10_000L

/**
 * How often a joined Concord community's stored invite link is re-resolved to check whether
 * we were left out of a Refounding (see `recoverStrandedConcordCommunities`). Stranding is
 * rare and silent, so this trades detection latency for not turning the revision tick into a
 * relay-fetch loop.
 */
private const val RECOVERY_CHECK_INTERVAL_MS = 15 * 60 * 1000L

/** How many times a Pin List write re-applies itself on top of a concurrent edition that won the fold. */
private const val PIN_REHEAL_RETRIES = 2

/**
 * How many recipients one Refounding will re-key. See `AccountConcordActions.boundRecipients`.
 *
 * 120 blobs ride in each kind-3303 chunk, so this is ~42 published events and ~5k NIP-44
 * encryptions at the ceiling — heavy but survivable on a phone, and far above any real community.
 * Raising it raises the cost of the attack it exists to bound, not the safety.
 */
private const val MAX_REFOUNDING_RECIPIENTS = 5_000

// How many channel epochs a privatisation probes for earlier rotations (Armada MAX_PROBED_CHANNEL_EPOCH).
private const val MAX_PROBED_CHANNEL_EPOCH = 32L

/** How long leaving waits for a relay to take the Guestbook LEAVE before dropping the community anyway. */
private const val LEAVE_CONFIRM_SECS = 10L

/** A lowercase 32-byte hex key (the Guestbook `invite` tag's creator). */
private val HEX64 = Regex("^[0-9a-f]{64}$")

/**
 * Concord (encrypted communities) orchestration for an [Account]: join/create/
 * invite flows, channel messages/reactions/edits/typing, roles and moderation,
 * community refound/recovery, metadata and channel management, and control-plane
 * sync. Event building lives in the commons `ConcordActions`/`ConcordModeration`
 * objects; this class wires them to the account's signer, session manager,
 * channel list, and relay client. Rumor ingestion stays on [Account]
 * (`consumeConcordRumorGated`), which is wired into `ConcordSessionManager`.
 */
class AccountConcordActions(
    private val account: Account,
) {
    /**
     * For this class's reads. Most go out as plain filters through the one-shot helpers — some on the
     * 15-minute invite sweep and the control-plane sync, which run with the app backgrounded — and
     * tagging them here is what lets those relays say why they are connected. Publishes stay on
     * [Account.client]: tagging only touches REQ/COUNT, and a publish is attributed by the outbox.
     */
    private val concordClient = account.client.taggedAs(SubPurpose.COMMUNITY_CHATS)

    /**
     * Add a joined Concord community (secret-bearing entry) to the private Community List (kind 33302)
     * list, and announce a self-signed Guestbook JOIN so this member is visible to
     * whoever later refounds the community (CORD-06 re-keys the Guestbook membership).
     */
    suspend fun joinConcordCommunity(
        entry: ConcordCommunityListEntry,
        inviteCreator: HexKey? = null,
        inviteLabel: String? = null,
        fetchedWraps: List<Event> = emptyList(),
        readmit: Boolean = false,
    ): Boolean {
        // False when the List could not take the membership (not loaded, or every held fragment is
        // full while others are missing, CORD-02 §8). Callers must say so: the community would
        // otherwise look joined now and be gone after a restart.
        // A readmission merges into the entry the List holds at write time, keeping what it knew.
        val persisted =
            if (readmit) {
                updateConcordEntry(entry.id) { cur -> ConcordDirectInviteInbox.readmittedEntry(cur, entry) } || persistConcordEntry(entry)
            } else {
                persistConcordEntry(entry)
            }
        if (!persisted) return false
        // The session is built asynchronously from the Community List flow. Every wrap that reaches
        // the cache before it exists is kept as an unclaimed note, and the live subscription's copy
        // of the same wrap is then deduplicated away, so the community showed "No channels yet"
        // until a restart emptied the cache. Wait for the session, then hand it what we fetched.
        awaitConcordSession(entry.id)
        fetchedWraps.forEach { account.concordSessions.ingest(it) }
        announceConcordGuestbookJoin(entry, inviteCreator, inviteLabel)
        return true
    }

    /** The community's current name: its folded metadata, else the name it was joined under. */
    private fun currentConcordName(
        session: ConcordCommunitySession?,
        entry: ConcordCommunityListEntry,
    ): String =
        session
            ?.state
            ?.value
            ?.metadata
            ?.name
            ?.takeIf { it.isNotBlank() } ?: entry.name

    private suspend fun awaitConcordSession(communityId: HexKey) {
        withTimeoutOrNull(SESSION_WAIT_MS) {
            account.concordSessions.revision.first { account.concordSessions.sessionFor(communityId) != null }
        }
    }

    /**
     * Makes the Community List fetched before any write can depend on it: an empty fragment set
     * only means "no List" once the relays have been asked (CORD-02 §8 — a write built on an
     * unloaded List replaces fragments another device published).
     */
    suspend fun preloadConcordList() {
        if (account.concordChannelList.relaysConfirmed) return
        // Single-flight: the import is a ~30s drain of every stock/outbox relay. A join starts it
        // as soon as it begins, and the join's own List write then waits for that same fetch
        // instead of starting a second one.
        concordListImport.withLock {
            if (!account.concordChannelList.relaysConfirmed) importConcordCommunities()
        }
    }

    private val concordListImport = Mutex()

    /**
     * Read-modify-writes the Community List through [change] and publishes the fragments it
     * produced. Returns false — logged, never thrown into a UI coroutine — when the List can't be
     * written safely yet (fragments unreadable or not loaded) or a fragment would pass the ceiling.
     */
    private suspend fun writeConcordList(change: suspend (ConcordChannelListState) -> List<Event>): Boolean {
        preloadConcordList()
        return try {
            account.sendMyPublicAndPrivateOutbox(change(account.concordChannelList))
            true
        } catch (e: ConcordListIncompleteException) {
            Log.w("Concord") { "Community List not written: ${e.message}" }
            false
        } catch (e: ConcordListTooLargeException) {
            Log.w("Concord") { "Community List not written: ${e.message}" }
            false
        }
    }

    /** Adds or replaces [entry] in the Community List; false when it could not be written. */
    private suspend fun persistConcordEntry(entry: ConcordCommunityListEntry): Boolean = writeConcordList { it.follow(entry) }

    /**
     * Rewrites the held entry for [communityId] through [transform], applied to the entry **as the
     * List holds it inside the write** ([ConcordChannelListState.update]) — never to a snapshot read
     * before a suspension, which a concurrent rekey or import could have moved on. True only when
     * [transform] produced a change and it was written.
     */
    private suspend fun updateConcordEntry(
        communityId: String,
        transform: (ConcordCommunityListEntry) -> ConcordCommunityListEntry?,
    ): Boolean {
        var changed = false
        val ok = writeConcordList { list -> list.update(communityId) { cur -> transform(cur)?.also { changed = true } } }
        return ok && changed
    }

    /** Publishes a Guestbook JOIN (kind 3306) for [entry] to its community relays. */
    private suspend fun announceConcordGuestbookJoin(
        entry: ConcordCommunityListEntry,
        inviteCreator: HexKey?,
        inviteLabel: String?,
    ) {
        if (!account.isWriteable()) return
        val guestbook = ConcordActions.guestbookPlane(entry.root.hexToByteArray(), entry.id.hexToByteArray(), entry.rootEpoch)
        val wrap = ConcordActions.buildGuestbookJoin(account.signer, guestbook, TimeUtils.now(), inviteCreator, inviteLabel)
        account.concordSessions.ingest(wrap)
        val relays = entry.relays.mapNotNullTo(mutableSetOf()) { RelayUrlNormalizer.normalizeOrNull(it) }
        if (relays.isNotEmpty()) account.client.publish(wrap, relays)
    }

    /**
     * Create a new Concord community: mint its genesis (metadata + #general),
     * publish the owner-signed genesis wraps to [relays] (or our outbox), and add
     * the secret-bearing entry to the Community List (kind 33302). Returns the new
     * community id, or null if not writeable or no relay accepted the genesis.
     */
    suspend fun createConcordCommunity(
        name: String,
        description: String? = null,
        relays: List<String> = emptyList(),
        icon: ImagePointer? = null,
    ): String? {
        if (!account.isWriteable()) return null
        // CORD-02 §6 caps: every reader drops metadata past them, so never mint a genesis they'd refuse.
        if (!ConcordLimits.nameFits(name) || !ConcordLimits.descriptionFits(description)) return null
        val relayUrls =
            relays.ifEmpty {
                account.outboxRelays.flow.value
                    .map { it.url }
            }
        val community = ConcordActions.createCommunity(account.signer, name, TimeUtils.now(), description, relayUrls, icon)

        val publishTo = relayUrls.mapNotNullTo(mutableSetOf()) { RelayUrlNormalizer.normalizeOrNull(it) }.ifEmpty { account.outboxRelays.flow.value }
        // The genesis is the community: with no relay holding it, nobody (us included, after a
        // restart) can ever fold it. It used to be fired and forgotten, then the community saved
        // anyway, so a genesis a relay refused or an app killed mid-send left a community in the
        // List that could never load again. Confirm every genesis wrap before saving it.
        val landed =
            coroutineScope {
                community.genesisWraps.map { async { account.client.publishAndConfirm(it, publishTo) } }.awaitAll()
            }
        if (!landed.all { it }) {
            Log.w("Concord") { "createConcordCommunity: no relay in $publishTo accepted the genesis; not creating ${community.communityIdHex}" }
            return null
        }

        val saved =
            joinConcordCommunity(
                ConcordCommunityListEntry(
                    id = community.communityIdHex,
                    owner = community.ownerPubKey,
                    ownerSalt = community.ownerSalt.toHexKey(),
                    root = community.communityRoot.toHexKey(),
                    rootEpoch = community.rootEpoch,
                    // The creator is the founding staff member (CORD-02 §2): it keeps the write
                    // secret and publishes only the derived pubkey to everyone else.
                    controlPk = community.controlPkHex,
                    controlRoot = community.controlRoot.toHexKey(),
                    relays = relayUrls,
                    name = name,
                    addedAt = TimeUtils.nowMillis(),
                ),
                fetchedWraps = community.genesisWraps,
            )
        return community.communityIdHex.takeIf { saved }
    }

    // ---- CORD-05 Invite List (kind 13303) -------------------------------------

    /**
     * This account's Invite List (kind 13303): the creator's private, self-encrypted record of every
     * link they minted (`token` + `signer_sk` per entry).
     *
     * Returns **null** when the list could not be read — no relay answered, or the signer refused
     * the decrypt — and an empty document only when the account genuinely has no list yet. Callers
     * must not conflate the two: republishing an "empty" list over this replaceable coordinate
     * destroys every `signer_sk` it failed to read, and those secrets cannot be regenerated.
     *
     * Read on the account's OUTBOX relays, never a community's: the coordinate is
     * (13303, me, "") — one list for the whole account — so scoping it per community would fork it
     * into divergent versions that the newest-wins rule then silently collapses.
     *
     * Fetched rather than read from [LocalCache] because nothing subscribes to 13303: it is
     * bookkeeping the user never sees, needed only at mint and at rotation.
     */
    private suspend fun readConcordInviteList(): ConcordInviteListDocument? {
        val relays = account.outboxRelays.flow.value
        if (relays.isEmpty()) return null
        val filter = Filter(kinds = listOf(ConcordInviteListEvent.KIND), authors = listOf(account.signer.pubKey))
        // Terminal reasons, not just events: `fetchAll` returns an empty list both when a relay
        // served us and had nothing AND when nothing answered at all (cannot-connect, CLOSED, idle
        // timeout). Treating the second as "no list yet" is precisely how a read-merge-write wipes
        // the signer_sk of every link it failed to read, so the two must be told apart.
        val result =
            concordClient.fetchAllWithHooks(
                filters = relays.associateWith { listOf(filter) },
            ) { _, _ -> true }

        val newest =
            result
                .events
                .mapNotNull { it.second as? ConcordInviteListEvent }
                // Filter by kind BEFORE picking the newest: taking the newest of anything and then
                // casting means one stray event at this coordinate reads as "unreadable" forever.
                .maxByOrNull { it.createdAt }
                ?: return if (result.anyRelayServed) {
                    ConcordInviteListDocument.EMPTY // a relay answered and had nothing — safe to start one
                } else {
                    null // nobody answered; we know nothing about what is published
                }
        return newest.decrypt(account.signer)
    }

    /**
     * Merges [patch] into the published Invite List and republishes it, returning the merged document
     * when it landed and null when it did not.
     *
     * Read-merge-write, and **aborts rather than overwriting** when the read fails: the list is
     * replaceable, so publishing a patch-only document over an unread list deletes every other
     * link's `signer_sk` — unrecoverable, and it strands every holder of those links at the next
     * rotation. A momentarily unreachable relay or a bunker signer that declines one decrypt is
     * enough to trigger that, which is exactly how the kind-13302 community list was once emptied.
     */
    private suspend fun publishConcordInviteList(patch: ConcordInviteListDocument): ConcordInviteListDocument? {
        val publishTo = account.outboxRelays.flow.value
        if (publishTo.isEmpty()) return null
        val base =
            readConcordInviteList() ?: run {
                Log.w("Concord") { "Refusing to write the invite list: could not read the current one (would drop other links' signer_sk)" }
                return null
            }
        val merged = ConcordInviteList.merge(base, patch)
        // publishAndConfirm, never publish: `INostrClient.publish` returns Unit — it queues the event
        // and never reports acceptance — so a `runCatching { publish(); true }` is true whenever
        // local signing worked, and every caller's "did the record land?" gate becomes decorative.
        val landed =
            runCatchingCancellable {
                account.client.publishAndConfirm(ConcordInviteListEvent.create(account.signer, merged, TimeUtils.now()), publishTo)
            }.onFailure { Log.w("Concord", "invite list publish failed", it) }.getOrDefault(false)
        return if (landed) merged else null
    }

    /**
     * Publishes this account's Invite Registry for [entry]'s community (CORD-05 §5, `vsk 8`): "a
     * Registry edit accompanies every mint and every retire". The list is this account's honored
     * registry plus the live links its Invite List [list] holds plus [minted], minus [retired] and
     * minus every tombstoned or expired link ([ConcordInviteRegistry.nextLinks]), so an elapsed link
     * stops keeping the community Public. Returns whether an edition was published.
     *
     * Best-effort, like the reference client's: the registry never gates a link working. It is
     * skipped when there is no session to chain onto, when this account no longer holds
     * CREATE_INVITE (every reader would drop the edition), when the `control_root` is not held
     * (CORD-02 §2), and when the next list equals the one already honored.
     */
    private suspend fun publishConcordInviteRegistry(
        entry: ConcordCommunityListEntry,
        list: ConcordInviteListDocument?,
        minted: List<HexKey> = emptyList(),
        retired: List<HexKey> = emptyList(),
    ): Boolean {
        val session = account.concordSessions.sessionFor(entry.id) ?: return false
        // Only from a drained fold: `published` is read off our honored head, and a partial fold
        // would read "no registry" and chain a fresh v1 over the real one (dropped by every reader).
        val state = session.foldForWrite() ?: return false
        // A dissolved community has no future (CORD-02 §9): no registry edit can change anything.
        if (state.dissolved) return false
        if (!isAuthorizedFor(session, ConcordPermissions.CREATE_INVITE)) return false
        val cp = controlKeysForWrite(session) ?: return false
        val me = account.signer.pubKey
        val published = state.registryOf(me)
        val next = ConcordInviteRegistry.nextLinks(published, list, entry.id, TimeUtils.now(), minted, retired)
        val hasHead = state.inviteRegistries.containsKey(me.lowercase())
        if (next == published.sorted() && (hasHead || next.isEmpty())) return false
        // The writer chains off the same authorized, floor-aware head the fold honors.
        val wrap =
            try {
                ConcordModeration.setInviteRegistry(account.signer, cp, entry.id.hexToByteArray(), next, session.controlEditions(), TimeUtils.now(), owner = entry.owner, floors = session.controlFloors())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Concord", "invite registry build failed for ${entry.id}", e)
                return false
            }
        // Confirmed: the registry is the community's Public/Private source of truth (CORD-05 §5).
        return publishConcordWrapConfirmed(entry, wrap)
    }

    /**
     * Re-posts every live link this account minted for [entry]'s community at its own coordinate,
     * carrying [entry]'s epoch (CORD-05). The kind-33301 bundle is addressable and authored by the
     * link signer, so this moves the link behind the same URL instead of orphaning it at a dead
     * epoch — which is the whole premise stranded recovery rests on.
     *
     * [entry] MUST be the post-rotation entry, passed in rather than re-read: the joined-list flow
     * decrypts asynchronously, so reading it straight after adopting a new root yields the OLD
     * epoch and would re-mint every link onto the epoch we just left.
     *
     * Each link is refreshed from its own CURRENT bundle, not rebuilt from scratch, so per-link
     * fields the bundle carries — expiry, channel grants, icon, label — survive the rotation. A
     * coordinate whose newest event is a revocation tombstone is left alone: re-posting a live
     * bundle over it would silently un-revoke the link.
     */
    private suspend fun refreshConcordInviteLinks(entry: ConcordCommunityListEntry): Int {
        val relays = entry.relays.mapNotNullTo(mutableSetOf()) { RelayUrlNormalizer.normalizeOrNull(it) }.ifEmpty { account.outboxRelays.flow.value }
        if (relays.isEmpty()) return 0
        val list = readConcordInviteList() ?: return 0
        val tombstoned = list.tombstones.mapTo(HashSet()) { it.token }
        val now = TimeUtils.now()

        // An elapsed or retired link can no longer be joined; re-posting it would only resurrect a
        // dead URL at a live epoch.
        val links = list.entries.filter { it.communityId == entry.id && !it.isExpired(now) && it.token !in tombstoned }
        if (links.isEmpty()) return 0

        // One REQ for every link's bundle rather than a round trip each. This runs inside the
        // user-visible Refounding, and a serial fetch per link makes a removal take time linear in
        // how many links the creator ever minted, each able to wait out its own idle timeout.
        val byAuthor = links.associateBy { it.signerPubKeyHex().lowercase() }
        val wraps = concordClient.fetchAll(filters = relays.associateWith { listOf(ConcordActions.bundlesFilter(byAuthor.keys.toList())) })
        val wrapsByAuthor = wraps.groupBy { it.pubKey.lowercase() }

        return coroutineScope {
            byAuthor
                .map { (author, link) ->
                    async {
                        runCatchingCancellable {
                            val token = link.token.hexToByteArray()
                            // Classify per coordinate, never over the pooled set: one link's newer
                            // revocation tombstone must not decide another link's status.
                            val current = ConcordActions.classifyInvite(wrapsByAuthor[author].orEmpty(), author, token) as? InviteBundleStatus.Live ?: return@runCatchingCancellable false
                            val moved =
                                current.invite.copy(
                                    communityRoot = entry.root,
                                    rootEpoch = entry.rootEpoch,
                                    controlPk = entry.controlPk,
                                    relays = entry.relays,
                                    name = currentConcordName(account.concordSessions.sessionFor(entry.id), entry),
                                )
                            // Confirmed: a link counted as moved but never stored is a link its
                            // holders can no longer redeem, reported as a success.
                            account.client.publishAndConfirm(ConcordActions.remintBundleAt(link.signerSk.hexToByteArray(), token, moved, now), relays)
                        }.onFailure { Log.w("Concord", "invite refresh failed for ${entry.id}", it) }.getOrDefault(false)
                    }
                }.awaitAll()
                .count { it }
        }
    }

    /**
     * Mint a shareable invite link for a joined community and publish its
     * kind-33301 public bundle to the community relays. Returns the `…/invite/…`
     * URL, or null if the community isn't joined or isn't writeable.
     */
    suspend fun mintConcordInvite(
        communityId: String,
        base: String = "https://amethyst.social",
    ): String? {
        if (!account.isWriteable()) return null
        val entry =
            account.concordChannelList.liveCommunities.value
                .firstOrNull { it.id == communityId } ?: return null
        // CREATE_INVITE, and not while banned. This used to check only that we held the community,
        // which made minting the one moderation-free action in the app: a member the owner had just
        // banned could tap the invite button and hand out a working link to the community they were
        // removed from, and every account they invited arrived as a fresh un-banned npub.
        //
        // Note the bit is not otherwise enforced anywhere. The fold gates the INVITE_* Control
        // entities on CREATE_INVITE, but a link's bundle is a standalone kind-33301 published
        // OUTSIDE the Control Plane, so no fold ever sees it. This check is the only one there is.
        // The owner is proven by the community id (CORD-02), so they are read off the entry and can
        // mint before the session exists — the session is built asynchronously off the joined list,
        // and requiring it here would have made the owner's own invite button fail on a cold start.
        // Everyone else needs the folded roster, so no session means no invite.
        val session = account.concordSessions.sessionFor(communityId)
        val amOwner = entry.owner.equals(account.signer.pubKey, ignoreCase = true)
        if (!amOwner && (session == null || !isAuthorizedFor(session, ConcordPermissions.CREATE_INVITE))) return null
        val invite =
            ConcordActions.inviteFor(
                communityIdHex = entry.id,
                ownerPubKey = entry.owner,
                ownerSaltHex = entry.ownerSalt,
                communityRootHex = entry.root,
                rootEpoch = entry.rootEpoch,
                // The folded metadata, not the List entry: the entry keeps the name it was joined
                // under, so a renamed community's invites previewed its old name.
                name = currentConcordName(session, entry),
                relays = entry.relays,
                // The joiner can never derive the Control Plane address, so the bundle carries
                // it (CORD-05 §1). Null on a legacy community, which has none to carry.
                controlPk = entry.controlPk,
                // Attribution the joiner echoes in their Guestbook Join (CORD-05 §1).
                creator = account.signer.pubKey,
            )
        // The fragment carries at most 3 bootstrap relays (CORD-05 §3); the codec truncates a longer
        // list (the stock set stays a single flag), and the bundle keeps the full relay set.
        val minted = ConcordActions.mintInviteLink(base, invite, TimeUtils.now(), entry.relays)

        val publishTo = entry.relays.mapNotNullTo(mutableSetOf()) { RelayUrlNormalizer.normalizeOrNull(it) }.ifEmpty { account.outboxRelays.flow.value }
        // Record the link BEFORE handing the URL out (CORD-05, kind 13303). A link whose `signer_sk`
        // was never stored can never be refreshed, so the next Refounding orphans it and everyone
        // holding it is stranded — with nothing to have warned them. Failing the mint is the honest
        // outcome; a stored entry for a link nobody received is harmless by comparison.
        val recorded =
            publishConcordInviteList(
                ConcordInviteListDocument(
                    entries =
                        listOf(
                            ConcordInviteListEntry(
                                token = minted.token.toHexKey(),
                                signerSk = minted.linkSignerPrivKey.toHexKey(),
                                communityId = entry.id,
                                url = minted.url,
                                createdAt = TimeUtils.now(),
                            ),
                        ),
                ),
            )
        if (recorded == null) {
            Log.w("Concord") { "Invite not minted for ${entry.id}: its link signer could not be recorded, so the link could never be refreshed" }
            return null
        }

        if (publishTo.isNotEmpty()) account.client.publish(minted.bundleEvent, publishTo)
        // The member-facing shadow of the list we just wrote (CORD-05 §5): the link now makes the
        // community Public. Best-effort — the link works without it.
        publishConcordInviteRegistry(entry, recorded, minted = listOf(minted.linkSignerPubKey))
        return minted.url
    }

    /**
     * Every link this account minted for [communityId] that is still live, newest first — the
     * backing list for the invite-links screen.
     *
     * Null means the list could not be read (no relay answered, or the signer refused the decrypt),
     * which the UI must show as an error rather than as "you have no links": telling a creator their
     * leaked link doesn't exist is worse than telling them we couldn't check.
     *
     * Retired tokens are filtered out here rather than rendered as dead rows — [ConcordInviteList]
     * already drops a tombstoned entry on merge, so a tombstoned entry only appears in the window
     * between our revoke and the next merge.
     */
    suspend fun listConcordInviteLinks(communityId: String): List<ConcordInviteListEntry>? {
        val list = readConcordInviteList() ?: return null
        val tombstoned = list.tombstones.mapTo(HashSet()) { it.token }
        return list.entries
            .filter { it.communityId == communityId && it.token !in tombstoned }
            .sortedByDescending { it.createdAt }
    }

    /**
     * Retires the link [token] (CORD-05 §2): publishes a `vsk=9` tombstone at its coordinate, then
     * records the retirement in the kind-13303 list. Returns false if the link could not be retired.
     *
     * No community permission is checked, deliberately. The coordinate is authored by the link
     * signer, whose secret only the creator holds, so revoking is an act on your own key rather than
     * on the community — and gating it on CREATE_INVITE would mean a demoted admin could no longer
     * retire the links they had already handed out, which is precisely when they most need to.
     *
     * The wire tombstone goes first and the list second. That is the inverse of minting and it is
     * deliberate: the entry holds the only copy of the `signer_sk` this needs, and a merge drops a
     * tombstoned token's entry terminally, so recording first and then failing to publish would
     * leave the link live with its signer gone and no way left to retire it. A failed list write is
     * recoverable — the link is already dead on the wire, and the refresh path re-mints only a
     * coordinate that still resolves Live.
     *
     * Every retire also edits this account's Invite Registry (CORD-05 §5). When the link was the
     * community's last live one, retiring it flips the community Private — "a Refounding (CORD-06)"
     * (CORD-05 §2) — so this then Refounds with nobody removed, provided this account may (it takes
     * BAN). The result says which of those happened.
     */
    suspend fun revokeConcordInvite(
        communityId: String,
        token: String,
    ): ConcordRevokeResult {
        if (!account.isWriteable()) return ConcordRevokeResult.FAILED
        val entry =
            account.concordChannelList.liveCommunities.value
                .firstOrNull { it.id == communityId } ?: return ConcordRevokeResult.FAILED
        val link =
            readConcordInviteList()?.entries?.firstOrNull { it.token == token && it.communityId == communityId }
                ?: run {
                    Log.w("Concord") { "Cannot revoke $token: it is not in this account's invite list, so its link signer is unknown" }
                    return ConcordRevokeResult.FAILED
                }

        val relays = entry.relays.mapNotNullTo(mutableSetOf()) { RelayUrlNormalizer.normalizeOrNull(it) }.ifEmpty { account.outboxRelays.flow.value }
        if (relays.isEmpty()) return ConcordRevokeResult.FAILED
        // Confirmed, not fire-and-forget. A `publish` that returns Unit would report success for a
        // tombstone no relay stored — and the list write below would then drop this entry on merge,
        // destroying the only `signer_sk` that could ever retire the link while the link stays live.
        val published =
            runCatching {
                account.client.publishAndConfirm(ConcordActions.revokeBundleAt(link.signerSk.hexToByteArray(), TimeUtils.now()), relays)
            }.onFailure { Log.w("Concord", "invite revocation failed for $communityId", it) }.getOrDefault(false)
        if (!published) return ConcordRevokeResult.FAILED

        val signer = link.signerPubKeyHex().lowercase()
        // Judged on the fold BEFORE our registry edit lands: afterwards the link is gone from it.
        val privatizes =
            account.concordSessions
                .sessionFor(communityId)
                ?.state
                ?.value
                ?.retiringWouldPrivatize(listOf(signer)) == true

        val recorded = publishConcordInviteList(ConcordInviteListDocument(tombstones = listOf(ConcordInviteListTombstone(token = token, communityId = communityId))))
        if (recorded == null) {
            // The link is already dead on the wire, so this is bookkeeping we can retry rather than a
            // failed revocation. Reported as success for exactly that reason.
            Log.w("Concord") { "Revoked $token on the wire but could not tombstone it in the invite list; a later revoke will record it" }
        }
        publishConcordInviteRegistry(entry, recorded, retired = listOf(signer))

        if (!privatizes) return ConcordRevokeResult.REVOKED
        // The last live link is gone: the community is Private now, and whoever already fetched a
        // link holds the current root. CORD-05 §2/§5: this is a Refounding (CORD-06 §3, "converting a
        // Public Community to Private"), which re-keys the members and leaves the lurkers behind.
        Log.i("Concord") { "Retired the last live invite link of $communityId: the community is Private, Refounding" }
        return if (privatizeConcordCommunity(communityId)) ConcordRevokeResult.PRIVATIZED else ConcordRevokeResult.PRIVATIZED_REFOUND_PENDING
    }

    /**
     * Leave a joined Concord community: tell it with a self-signed Guestbook LEAVE (CORD-02 §5), then
     * drop it from the Community List and tombstone it (CORD-02 §8). Without the LEAVE we stayed on
     * everyone's roster, and every later Refounding kept re-keying us.
     *
     * The LEAVE goes first, confirmed: gated relays only take a plane wrap after AUTH as the plane
     * key, and those secrets come from the live session, which the List write tears down. The wait
     * is bounded so a community whose relays are dead still gets left; callers run this off the UI.
     */
    suspend fun leaveConcordCommunity(communityId: String): Boolean {
        val entry = account.concordSessions.sessionFor(communityId)?.entry
        if (entry != null && account.isWriteable()) {
            val relays = entry.relays.mapNotNullTo(mutableSetOf()) { RelayUrlNormalizer.normalizeOrNull(it) }
            if (relays.isNotEmpty()) {
                // Best-effort from signing on: a remote signer that refuses or times out must not
                // keep the user in a community they asked to leave.
                val announced =
                    try {
                        val guestbook = ConcordActions.guestbookPlane(entry.root.hexToByteArray(), entry.id.hexToByteArray(), entry.rootEpoch)
                        val leave = ConcordActions.buildGuestbookLeave(account.signer, guestbook, TimeUtils.now())
                        account.client.publishAndConfirm(leave, relays, LEAVE_CONFIRM_SECS)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w("Concord", "Leaving $communityId: could not sign or publish the Guestbook LEAVE", e)
                        false
                    }
                if (!announced) {
                    Log.w("Concord") { "Leaving $communityId: no relay accepted the Guestbook LEAVE; members keep listing us" }
                }
            }
        }
        return writeConcordList { it.unfollow(communityId) }
    }

    /**
     * Redeem a Concord invite link (`…/invite/<naddr>#<fragment>`): parse it, fetch
     * the kind-33301 public bundle from the link's relays (+ our outbox), unlock it
     * with the fragment token, and add the resulting secret-bearing entry to the
     * Community List (kind 33302).
     *
     * Returns a [ConcordInviteResult] that separates the failure modes so the UI can
     * both explain what went wrong and decide whether a retry could ever help — a
     * bundle we can't open (e.g. minted by a newer client) must not strand the user
     * on a spinner that retries forever.
     *
     * A bundle whose `expires_at` has passed is rejected with
     * [ConcordInviteResult.Expired]. Expiry is resolved inside
     * [ConcordActions.classifyInvite], so it is enforced on every redeem path rather
     * than being a field nobody reads.
     *
     * **This must only ever be called from an explicit user action.** It contacts
     * relay URLs carried in the link (chosen by whoever minted it) and publishes a
     * Guestbook JOIN signed by this account, so calling it on deep-link arrival would
     * leak the user's IP and enroll them without consent — see `ConcordInviteScreen`.
     *
     * If the resolved community is already in the joined list, this returns
     * [ConcordInviteResult.Joined] without re-following or re-announcing a Guestbook
     * JOIN, so reopening an old invite for a community you're already in simply takes
     * you to it.
     */
    suspend fun joinConcordViaInvite(url: String): ConcordInviteResult {
        if (!account.isWriteable()) return ConcordInviteResult.InvalidLink
        val parsed = ConcordActions.parseInviteLink(url) ?: return ConcordInviteResult.InvalidLink

        // Joining ends in a Community List write, which first needs the List loaded (a slow drain of
        // every stock and outbox relay). Start it now so it overlaps the bundle and plane fetches
        // below instead of running after them.
        account.scope.launch { preloadConcordList() }

        val relays =
            (parsed.fragment.relays.mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) } + account.outboxRelays.flow.value).toSet()
        if (relays.isEmpty()) return ConcordInviteResult.NotReachable

        val filters = relays.associateWith { listOf(ConcordActions.bundleFilter(parsed.linkSignerPubKey)) }
        val wraps = concordClient.fetchAll(filters = filters)

        // Resolve the coordinate per CORD-05 §2 (newest wins; a vsk=9 tombstone revokes even over a
        // stale openable copy) so we honour revocation and can tell the user *why* a link won't open
        // instead of stranding them on a spinner that retries a link we can never redeem.
        val bundle =
            when (val status = ConcordActions.classifyInvite(wraps, parsed.linkSignerPubKey, parsed.fragment.token)) {
                is InviteBundleStatus.Live -> status.invite
                is InviteBundleStatus.Expired -> return ConcordInviteResult.Expired
                InviteBundleStatus.Revoked -> return ConcordInviteResult.Revoked
                InviteBundleStatus.Unreadable -> return ConcordInviteResult.Incompatible
                InviteBundleStatus.Absent -> return ConcordInviteResult.NotReachable
            }

        return joinValidatedConcordInvite(
            bundle = bundle,
            servedBy = relays,
            // Anchor for stranded recovery: keep the link we joined through, domain-agnostic, so a
            // Refounding that leaves us out of the recipient set is recoverable later. See
            // recoverStrandedConcordCommunities().
            inviteRef = ConcordActions.bareInviteRef(url),
            // Invite attribution (CORD-05 §1): the joiner echoes the link's creator + label in their
            // Guestbook Join, which is what makes per-link usage counters possible.
            inviteCreator = bundle.creatorNpub,
            inviteLabel = bundle.label,
        )
    }

    /**
     * The join half shared by every redeem path (link [joinConcordViaInvite], Direct Invite
     * [acceptConcordDirectInvite]): [bundle] is already opened, bounded and owner-proof validated,
     * and not expired. An already-held community only moves forward through a stranded rejoin (a
     * Refounding left us behind and the user re-accepted); otherwise it refuses a community whose
     * roster bans us (fails closed on an unreadable Control Plane, fetched over [servedBy] ∪ the
     * bundle's relays), then stores the secret-bearing entry and announces the Guestbook Join with
     * [inviteCreator]/[inviteLabel] attribution.
     */
    private suspend fun joinValidatedConcordInvite(
        bundle: CommunityInvite,
        servedBy: Set<NormalizedRelayUrl>,
        inviteRef: String?,
        inviteCreator: HexKey?,
        inviteLabel: String?,
        readmitting: ConcordCommunityListEntry? = null,
    ): ConcordInviteResult {
        val relays = servedBy

        // Already a member? Just take the user to the community. Re-following and re-announcing a
        // Guestbook JOIN (kind 3306) would spam the community relays with a fresh join every time an
        // old invite is reopened, so short-circuit to Joined — the screen forwards to the community
        // either way ("take me there", not "join again").
        //
        // The one exception is a membership a Refounding left behind: the background sweep only
        // DETECTS that (a bundle may never move a held community's base on its own, CORD-06 §2), so
        // the user explicitly re-accepting the link is the way forward — the same trust decision as
        // their first join, taken by them.
        val held =
            account.concordChannelList.liveCommunities.value
                .firstOrNull { it.id == bundle.communityId }
        var rejoined: ConcordCommunityListEntry? = null
        if (held != null && readmitting == null) {
            val heldState =
                account.concordSessions
                    .sessionFor(held.id)
                    ?.state
                    ?.value
            // Death wins every race (CORD-02 §9): nothing moves a dissolved community forward.
            if (heldState == null || heldState.dissolved) return ConcordInviteResult.Joined(bundle.communityId)
            rejoined = ConcordActions.rejoinStranded(held, bundle, heldState.authority.isBanned(account.signer.pubKey))
                ?: return ConcordInviteResult.Joined(bundle.communityId)
        }

        // Refuse a link that readmits us after we were removed. A Refounding re-mints every
        // outstanding link onto the new root (CORD-05), and an ex-member keeps the URL and its
        // unlock token forever — so without this the rotation meant to expel them hands them the new
        // keys instead. `recoverStrandedConcordCommunities` has always been ban-gated; this is the
        // other door into the same room.
        //
        // Fails CLOSED on an unreadable plane: the banlist is only knowable once the bundle yields
        // the root, and no verdict means no join. Two things make that safe to insist on rather than
        // a way to brick valid invites:
        //
        //  - the plane is fetched over the SAME relays that just served the bundle, not the relay
        //    list inside the bundle alone, which can be stale (a moved relay, a link minted before a
        //    relay change) and would otherwise refuse a community we can plainly reach;
        //  - it is PAGED, because a single REQ is truncated at the relay's per-filter cap. A missing
        //    older ban edition fails the gate open — it re-admits the very account it exists to
        //    refuse — so the one direction we must not economise on is completeness.
        val joinKeys =
            ConcordActions.controlPlaneKeys(
                communityRoot = bundle.communityRoot.hexToByteArray(),
                communityId = bundle.communityId.hexToByteArray(),
                rootEpoch = bundle.rootEpoch,
                controlPk = bundle.controlPk,
            )
        // Union, not `ifEmpty`: the relays that served the bundle are known-good for this community,
        // and the bundle's own list is the one that goes stale.
        val joinRelays = bundle.relays.mapNotNullTo(mutableSetOf()) { RelayUrlNormalizer.normalizeOrNull(it) } + relays
        val planeWraps = mutableListOf<Event>()
        concordClient.fetchAllPagesFromPool(
            filters = joinRelays.associateWith { listOf(ConcordActions.planeFilter(joinKeys.address)) },
        ) { event, _ -> planeWraps.add(event) }
        val joinEditions = ConcordActions.controlEditions(planeWraps, joinKeys)
        if (joinEditions.isEmpty()) return ConcordInviteResult.NotReachable
        if (AuthorityResolver.resolve(joinEditions, bundle.communityId.hexToByteArray(), bundle.owner).isBanned(account.signer.pubKey)) {
            return ConcordInviteResult.Banned
        }

        // Invite attribution (CORD-05 §1), echoed in the Guestbook Join; a label only rides with a creator.
        val creator = inviteCreator?.lowercase()?.takeIf { HEX64.matches(it) }
        val label = inviteLabel?.takeIf { creator != null && it.isNotBlank() }

        if (rejoined != null) {
            if (!adoptedConcordRotations.add("${rejoined.id}:${rejoined.rootEpoch}")) return ConcordInviteResult.Joined(bundle.communityId)
            Log.i("Concord") { "Stranded rejoin by explicit invite: ${rejoined.id} -> epoch ${rejoined.rootEpoch}" }
            if (!joinConcordCommunity(rejoined, creator, label, planeWraps)) return ConcordInviteResult.NotSaved
            _strandedConcordCommunities.value -= rejoined.id
            return ConcordInviteResult.Joined(bundle.communityId)
        }

        val entry =
            ConcordCommunityListEntry(
                id = bundle.communityId,
                owner = bundle.owner,
                ownerSalt = bundle.ownerSalt,
                root = bundle.communityRoot,
                rootEpoch = bundle.rootEpoch,
                // Read access to the Control Plane, never write (CORD-05 §1). Absent = the
                // community is still pre-split, so we fold it at the legacy address.
                controlPk = bundle.controlPk,
                // Private Channel keys the invite delivered (CORD-03 §1 / CORD-05 §1), so those
                // channels are read and written on their own planes from the first fold.
                privateChannels = ConcordActions.privateChannelKeysOf(bundle),
                relays = bundle.relays,
                name = bundle.name,
                addedAt = TimeUtils.nowMillis(),
                // Anchor for stranded recovery (null for a Direct Invite, which has no link).
                inviteRef = inviteRef,
            )
        // A readmission keeps the held entry's roots, cuts and keys (ConcordDirectInviteInbox.readmittedEntry).
        if (!joinConcordCommunity(entry, creator, label, planeWraps, readmit = readmitting != null)) return ConcordInviteResult.NotSaved
        return ConcordInviteResult.Joined(bundle.communityId)
    }

    // ---- CORD-05 §6 Direct Invites ---------------------------------------------

    /**
     * The Direct Invite inbox: wraps the account's gift-wrap subscription delivers (the NIP-17 inbox,
     * or its invite-only filter when NIP-17 is off) land here, parked until the user accepts or declines.
     */
    val directInviteInbox =
        ConcordDirectInviteInbox(
            account.signer,
            // Muted/blocked senders never park; followed ones outrank strangers when the inbox is full.
            isHidden = { account.isHidden(it) },
            isFollowed = { it in account.followingKeySet() },
        )

    /**
     * Wrap ids of Direct Invites whose Accept is running. A join can take many seconds (it waits on
     * the Community List), and a card left on screen meanwhile had both buttons disabled and nothing
     * to say why, so an invite being accepted is hidden. A failed accept puts it back.
     */
    private val acceptingConcordDirectInvites = MutableStateFlow<Set<HexKey>>(emptySet())

    /**
     * The parked Direct Invites a UI should show, followed senders first, then newest: invites for
     * communities we don't hold (nor left after they were sent), plus catch-ups for ones we do
     * ([ConcordDirectInviteInbox.visible]).
     */
    val pendingConcordDirectInvites: StateFlow<List<ConcordDirectInviteView>> =
        combine(
            visibleConcordDirectInvites(),
            acceptingConcordDirectInvites,
        ) { visible, accepting -> if (accepting.isEmpty()) visible else visible.filterNot { it.wrapId in accepting } }
            // `visible()` re-runs on every session revision and mints new (identity-equal only) views,
            // and each emission clears and rebuilds every Notifications feed. Emit only when something
            // a row shows or ranks by actually moved.
            .distinctUntilChangedBy { list -> list.map { InviteRowKey(it.wrapId, it.catchUp, it.expired, it.followedSender, it.newChannelIds) } }
            .stateIn(account.scope, SharingStarted.Eagerly, emptyList())

    private data class InviteRowKey(
        val wrapId: HexKey,
        val catchUp: Boolean,
        val expired: Boolean,
        val followedSender: Boolean,
        val newChannelIds: List<HexKey>,
    )

    private fun visibleConcordDirectInvites() =
        combine(
            directInviteInbox.pending,
            account.concordChannelList.liveCommunities,
            account.concordChannelList.removedAt,
            account.hiddenUsers.flow,
            // A fold landing can make a parked catch-up admissible or not (the sender's standing).
            combine(account.kind3FollowList.flow, account.concordSessions.revision) { follows, _ -> follows },
        ) { pending, joined, removedAt, _, follows ->
            ConcordDirectInviteInbox.visible(
                pending.values,
                joined,
                removedAt = removedAt,
                isFollowed = { it in follows.authors },
                isHidden = { account.isHidden(it) },
                heldStateOf = { id ->
                    account.concordSessions
                        .sessionFor(id)
                        ?.state
                        ?.value
                },
                me = account.signer.pubKey,
            )
        }

    /**
     * [pendingConcordDirectInvites] as feed rows: New Requests on Messages and cards on Notifications.
     * Eager because the feed filters read `.value` directly, with no subscriber of their own.
     */
    val pendingConcordDirectInviteNotes: StateFlow<List<ConcordDirectInviteNote>> =
        pendingConcordDirectInvites
            .map { list -> list.filterNot { it.catchUp }.map { ConcordDirectInviteNote(it) } }
            .stateIn(account.scope, SharingStarted.Eagerly, emptyList())

    /**
     * The stock Concord relays a Direct Invite sender falls back to when it can't find our relay lists
     * (none published, or none reachable — CORD-05 §6). We can't know which case a sender hit, so they
     * are swept too: an account with no published kind 10050 otherwise never received Armada's invites.
     * Our DM relays are left out — the account's gift-wrap subscription already reads invites there.
     */
    private fun concordDirectInviteStockRelays(): Set<NormalizedRelayUrl> = ConcordActions.directInviteDeliveryRelays(null) - account.dmRelays.flow.value

    private val directInviteSweep = Mutex()

    /**
     * Runs a Direct Invite sweep of the stock relays ([refreshConcordDirectInvites]) in the account's
     * scope, unless one is already running or Concord is turned off in Settings › Messages. Runs in the
     * account's scope because the hub leaves composition whenever it swaps layouts, which cancelled a
     * sweep launched there before any relay answered.
     */
    fun requestConcordDirectInviteSweep() {
        if (!account.chatFeedToggles.isEnabled(ChatFeedType.CONCORD)) return
        account.scope.launch {
            if (!directInviteSweep.tryLock()) return@launch
            try {
                refreshConcordDirectInvites()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Concord", "Direct Invite sweep failed", e)
            } finally {
                directInviteSweep.unlock()
            }
        }
    }

    /**
     * One-shot sweep of the stock Concord relays for Direct Invite wraps
     * (`{"kinds":[1059],"#p":[me],"#k":["3313"]}` since the inbox cursor, rewound by NIP-59's backdate
     * window), offering each to the inbox. A periodic fetch rather than a live subscription, so an
     * account that never gets an invite there doesn't hold those connections open. Returns how many new
     * invites were parked. Read-only: it decrypts, it never joins or contacts a community's relays.
     */
    suspend fun refreshConcordDirectInvites(): Int {
        val relays = concordDirectInviteStockRelays()
        if (relays.isEmpty()) return 0
        val before = directInviteInbox.pending.value.keys
        val filter = ConcordActions.directInvitesFilter(account.signer.pubKey, directInviteInbox.since())
        val wraps = concordClient.fetchAll(filters = relays.associateWith { listOf(filter) })
        wraps.distinctBy { it.id }.forEach { directInviteInbox.offer(it) }
        return (directInviteInbox.pending.value.keys - before).size
    }

    // Two triggers drain (an invite parking, a revision tick); without this both could adopt the same
    // catch-up at once and race their read-modify-write of the community entry.
    private val catchUpDrain = Mutex()

    /**
     * Adopts, without a click, every parked catch-up the held fold says is exactly the delivery a
     * Grant prescribes (Armada `judgeCatchUp`, [ConcordInviteVend.judgeCatchUp]): a staff sender, a
     * recipient who isn't banned, and only live Private Channels our Roles entitle us to. Consent
     * came from the Grant. Anything else waits for a manual Accept. Runs whenever the inbox parks an
     * invite and on the revision tick (a Grant folding late turns a waiting catch-up adoptable), one
     * pass at a time.
     */
    internal suspend fun drainConcordCatchUps() = catchUpDrain.withLock { drainConcordCatchUpsLocked() }

    private suspend fun drainConcordCatchUpsLocked() {
        if (!account.isWriteable()) return
        // Invites still park while Concord is off (the NIP-17 inbox delivers them, and dropping one would
        // lose it for good), but nothing is adopted until it is back on.
        if (!account.chatFeedToggles.isEnabled(ChatFeedType.CONCORD)) return
        val me = account.signer.pubKey
        val now = TimeUtils.nowMillis()
        for (opened in directInviteInbox.pending.value.values) {
            if (opened.isExpired(now)) continue
            val held =
                account.concordChannelList.liveCommunities.value
                    .firstOrNull { it.id.equals(opened.invite.communityId, ignoreCase = true) } ?: continue
            val state =
                account.concordSessions
                    .sessionFor(held.id)
                    ?.state
                    ?.value ?: continue
            if (state.dissolved) continue
            val verdict = ConcordInviteVend.judgeCatchUp(state.authority, state.privateChannelIds, me, opened.sender, opened.invite, held)
            if (verdict != ConcordInviteVend.CatchUpVerdict.ADOPT) continue
            val ids = ConcordInviteVend.catchUpChannelIds(held, opened.invite)
            if (updateConcordEntry(held.id) { cur -> ConcordInviteVend.adoptCatchUp(cur, opened.invite, ids) }) {
                Log.i("Concord") { "Adopted a granted Private Channel key for ${held.id} from ${opened.sender}" }
                directInviteInbox.resolve(opened.wrapId)
            }
        }
    }

    /**
     * The recipient's giftwrap inbox (CORD-05 §6): their kind-10050 DM relays, else NIP-65 read
     * relays — from the cache when we have their lists, fetched otherwise — else the stock set.
     */
    private suspend fun concordDirectInviteDeliveryRelays(recipient: HexKey): Set<NormalizedRelayUrl> {
        val user = account.cache.getOrCreateUser(recipient)
        val dmInbox = user.dmInboxRelayList()?.dialableRelays().orEmpty()
        val cached =
            if (dmInbox.isNotEmpty() || user.authorRelayList() != null) {
                RecipientRelayFetcher.Lists(dmInbox = dmInbox, keyPackage = emptyList(), nip65 = user.authorRelayList())
            } else {
                null
            }
        val lists =
            cached ?: run {
                val seed = DefaultDmIndexerRelays.RELAYS.mapNotNullTo(mutableSetOf()) { RelayUrlNormalizer.normalizeOrNull(it) } + account.outboxRelays.flow.value
                RecipientRelayFetcher.fetchRelayLists(account.client.taggedAs(SubPurpose.RELAY_LISTS), recipient, seed)
            }
        return ConcordActions.directInviteDeliveryRelays(lists)
    }

    /**
     * Hands the keys of [communityId] straight to [recipientPubKey] as a Direct Invite (CORD-05 §6):
     * the §1 bundle — carrying only the Private Channel keys the recipient's Roles entitle them to —
     * sealed by our real key inside an ephemeral, `k`-tagged giftwrap, published to the recipient's
     * inbox relays. It appears in no Registry and never flips the community Public; it cannot be
     * revoked once it lands. [expiresAtMs] (unix ms) bounds its shelf life.
     *
     * No community permission gates it — none could (CORD-05 §6) — but a banned member is refused,
     * like minting, and so is a banned recipient, whom the join would refuse anyway.
     */
    suspend fun sendConcordDirectInvite(
        communityId: String,
        recipientPubKey: HexKey,
        expiresAtMs: Long? = null,
        onlyChannelIds: Set<HexKey>? = null,
    ): ConcordDirectInviteSendResult {
        if (!account.isWriteable()) return ConcordDirectInviteSendResult.NOT_WRITEABLE
        val recipient = recipientPubKey.lowercase()
        val entry =
            account.concordChannelList.liveCommunities.value
                .firstOrNull { it.id == communityId } ?: return ConcordDirectInviteSendResult.NOT_MEMBER
        // The fold decides which Private Channel keys the recipient may receive; no fold, no send.
        val state =
            account.concordSessions
                .sessionFor(communityId)
                ?.state
                ?.value ?: return ConcordDirectInviteSendResult.ROSTER_NOT_LOADED
        val invite =
            when (val draft = ConcordActions.draftDirectInvite(entry, state, account.signer.pubKey, recipient, expiresAtMs, onlyChannelIds)) {
                is ConcordDirectInviteDraft.Refused -> return draft.reason
                is ConcordDirectInviteDraft.Ready -> draft.invite
            }
        val wrap = ConcordActions.buildDirectInvite(account.signer, recipient, invite)
        val relays = concordDirectInviteDeliveryRelays(recipient)
        if (relays.isEmpty()) return ConcordDirectInviteSendResult.NOT_DELIVERED
        val delivered =
            runCatching { account.client.publishAndConfirm(wrap, relays) }
                .onFailure { Log.w("Concord", "direct invite publish failed for $communityId", it) }
                .getOrDefault(false)
        return if (delivered) ConcordDirectInviteSendResult.SENT else ConcordDirectInviteSendResult.NOT_DELIVERED
    }

    /**
     * Accepts the parked Direct Invite [wrapId] (CORD-05 §6) through the same join path as a link:
     * refused once `expires_at` has passed, refused when the roster bans us, and — for a community
     * we already hold — only a catch-up adopting newly granted Private Channel keys on the same base.
     * The Guestbook Join is attributed to the seal-verified sender. **Only from an explicit user
     * action**: this is the first moment anything contacts the community's relays.
     */
    suspend fun acceptConcordDirectInvite(wrapId: HexKey): ConcordInviteResult {
        if (!account.isWriteable()) return ConcordInviteResult.InvalidLink
        // Check-and-set in one step: two accepts of one invite would run two joins and announce two
        // Guestbook JOINs.
        var claimed = false
        acceptingConcordDirectInvites.update { current ->
            claimed = wrapId !in current
            if (claimed) current + wrapId else current
        }
        if (!claimed) return ConcordInviteResult.InProgress
        try {
            return acceptConcordDirectInviteNow(wrapId)
        } finally {
            acceptingConcordDirectInvites.update { it - wrapId }
        }
    }

    private suspend fun acceptConcordDirectInviteNow(wrapId: HexKey): ConcordInviteResult {
        val opened = directInviteInbox.get(wrapId) ?: return ConcordInviteResult.InvalidLink
        val bundle = opened.invite
        val held =
            account.concordChannelList.liveCommunities.value
                .firstOrNull { it.id.equals(bundle.communityId, ignoreCase = true) }
        val heldState =
            held?.let {
                account.concordSessions
                    .sessionFor(it.id)
                    ?.state
                    ?.value
            }
        val result =
            when (val plan = ConcordDirectInviteInbox.acceptPlan(opened, held, heldState, account.signer.pubKey)) {
                DirectInviteAcceptPlan.Expired -> ConcordInviteResult.Expired
                DirectInviteAcceptPlan.Banned -> ConcordInviteResult.Banned
                // No folded roster yet: whether it bans us is unknown, so the invite waits.
                DirectInviteAcceptPlan.RosterNotLoaded -> ConcordInviteResult.NotReachable
                DirectInviteAcceptPlan.NothingNew -> ConcordInviteResult.Joined(bundle.communityId)
                // Keys only, on the held base: no second Guestbook Join. Re-applied to the entry the
                // List holds at write time, so a root imported meanwhile is never written back over.
                is DirectInviteAcceptPlan.CatchUp -> {
                    val ok = writeConcordList { list -> list.update(plan.entry.id) { cur -> ConcordInviteVend.adoptCatchUp(cur, bundle, plan.channelIds) } }
                    if (ok) ConcordInviteResult.Joined(bundle.communityId) else ConcordInviteResult.NotReachable
                }
                DirectInviteAcceptPlan.Join, DirectInviteAcceptPlan.Readmit ->
                    joinValidatedConcordInvite(
                        bundle = bundle,
                        servedBy = emptySet(),
                        inviteRef = null,
                        // Attributed to the seal-verified sender (Armada), never the bundle's claim.
                        inviteCreator = opened.sender,
                        inviteLabel = bundle.label,
                        readmitting = held?.takeIf { plan == DirectInviteAcceptPlan.Readmit },
                    )
            }
        if (result is ConcordInviteResult.Joined) directInviteInbox.resolve(opened.wrapId)
        return result
    }

    /** Declines the parked Direct Invite [wrapId]: its keys are discarded and it never resurfaces. */
    fun declineConcordDirectInvite(wrapId: HexKey): Boolean = directInviteInbox.decline(wrapId)

    /**
     * Post [text] to a Concord channel: derive the channel plane key, build an
     * encrypted-seal kind-1059 wrap authored by that plane key (not our identity),
     * fold it locally for an instant echo, and publish it to the community's relays.
     * The `p` tag is ephemeral, so this never routes through the DM outbox — it goes
     * straight to the community relay set. Returns false if not writeable or the
     * community isn't currently joined/folded.
     */
    suspend fun sendConcordChannelMessage(
        communityId: String,
        channelIdHex: String,
        text: String,
        replyTo: Note? = null,
        replyMode: ReplyMode = ReplyMode.INLINE,
        imetas: List<IMetaTag> = emptyList(),
    ): Boolean {
        if (!account.isWriteable()) return false
        val session = account.concordSessions.sessionFor(communityId) ?: return false
        val entry = session.entry
        // The channel's own plane: root-derived for a Public Channel, its held key for a Private one,
        // and no plane at all (refuse) for a Private Channel whose key we do not hold (CORD-03 §1).
        val plane = session.currentChannelPlane(channelIdHex) ?: return false
        val channelKey = plane.key

        // NIP-30 custom-emoji tags for any `:shortcode:` the user typed, so the message renders the
        // custom image everywhere (the kind-9 rumor carries them; recipients render via the tags).
        val emojiTags =
            account.emoji
                .findEmojiTags(text)
                .map { it.toTagArray() }
                .toTypedArray()

        val parent = replyTo?.event
        // CORD-08 §2: the community timer as folded right now rides inside the signed rumor.
        val timer = session.messageExpirationSecs()
        val wrap =
            when {
                // A minichat reply is a kind-1111 thread comment (carrying encrypted image imetas when
                // the user attached media); an inline reply is a kind-9 message quoting the parent; a
                // fresh post is a plain kind-9 message.
                parent != null && replyMode == ReplyMode.MINICHAT && imetas.isNotEmpty() ->
                    ConcordActions.buildChannelImageReply(account.signer, channelKey, channelIdHex, plane.epoch, parent, text, imetas, TimeUtils.now(), emojiTags, timer)
                parent != null && replyMode == ReplyMode.MINICHAT ->
                    ConcordActions.buildChannelReply(account.signer, channelKey, channelIdHex, plane.epoch, parent, text, TimeUtils.now(), emojiTags, timer)
                parent != null ->
                    ConcordActions.buildChannelInlineReply(account.signer, channelKey, channelIdHex, plane.epoch, parent, text, TimeUtils.now(), emojiTags, timer)
                else ->
                    ConcordActions.buildChannelMessage(account.signer, channelKey, channelIdHex, plane.epoch, text, TimeUtils.now(), emojiTags, timer)
            }
        sendConcordChannelWrap(entry, channelKey, wrap)
        return true
    }

    /**
     * Send a channel message carrying encrypted image attachments ([imetas], built by the composer
     * from the encrypted upload) — Armada's `encryptAttachments` shape. The ciphertext URLs are
     * appended to [text] and each rides as a NIP-92 `imeta` with `aes-gcm` decryption params. With no
     * attachments this is just a plain [sendConcordChannelMessage].
     */
    suspend fun sendConcordChannelImageMessage(
        communityId: String,
        channelIdHex: String,
        text: String,
        imetas: List<IMetaTag>,
    ): Boolean {
        if (imetas.isEmpty()) return sendConcordChannelMessage(communityId, channelIdHex, text)
        if (!account.isWriteable()) return false
        val session = account.concordSessions.sessionFor(communityId) ?: return false
        val entry = session.entry
        val plane = session.currentChannelPlane(channelIdHex) ?: return false
        val channelKey = plane.key
        // Carry NIP-30 custom-emoji tags for any `:shortcode:` in the caption, same as a plain message.
        val emojiTags =
            account.emoji
                .findEmojiTags(text)
                .map { it.toTagArray() }
                .toTypedArray()
        val wrap = ConcordActions.buildChannelImageMessage(account.signer, channelKey, channelIdHex, plane.epoch, text, imetas, TimeUtils.now(), emojiTags, session.messageExpirationSecs())
        sendConcordChannelWrap(entry, channelKey, wrap)
        return true
    }

    /**
     * React to a Concord message with [reaction] (e.g. `"+"`, an emoji). Mirrors
     * [sendConcordChannelMessage]: builds a kind-7 rumor bound to the message's
     * channel/epoch, wraps it on the plane, and publishes it — so the reaction stays
     * inside the encrypted channel (never a plaintext public kind-7 that would leak
     * the message id). [note] must be a Concord channel message (carries a
     * [ConcordChannel] gatherer).
     */
    suspend fun reactToConcordMessage(
        note: Note,
        reaction: String,
    ): Boolean {
        if (!account.isWriteable()) return false
        val channel = note.inGatherers?.firstNotNullOfOrNull { it as? ConcordChannel } ?: return false
        val target = note.event ?: return false
        val communityId = channel.channelId.communityId
        val channelIdHex = channel.channelId.channelId
        val session = account.concordSessions.sessionFor(communityId) ?: return false
        val entry = session.entry

        val plane = session.currentChannelPlane(channelIdHex) ?: return false
        val channelKey = plane.key
        // A custom-emoji reaction is a `:shortcode:` content that needs its NIP-30 `emoji` tag to
        // resolve to an image on the other side; a plain unicode/`+` reaction yields no tags.
        val emojiTags =
            account.emoji
                .findEmojiTags(reaction)
                .map { it.toTagArray() }
                .toTypedArray()
        val wrap = ConcordActions.buildChannelReaction(account.signer, channelKey, channelIdHex, plane.epoch, target, reaction, TimeUtils.now(), emojiTags, session.messageExpirationSecs())
        publishConcordWrap(entry, wrap)
        return true
    }

    /**
     * Edit my own Concord channel message [note] to [newText]. Mirrors
     * [reactToConcordMessage]: builds a kind-3302 [ChannelChat.edit] rumor bound to the
     * message's channel/epoch, wraps it on the plane, and publishes it — so the edit stays
     * inside the encrypted channel (a public edit would e-tag the private rumor id onto
     * public relays). The receiving side overlays the newest edit onto the target message;
     * only the *original author's* edits are applied, so we gate to my own messages — a kind-9
     * message or a kind-1111 thread reply (CORD-03 §3: edits target either by rumor id).
     * Returns false if [note] isn't an editable Concord message I authored.
     */
    suspend fun editConcordChannelMessage(
        note: Note,
        newText: String,
    ): Boolean {
        if (!account.isWriteable()) return false
        val channel = note.inGatherers?.firstNotNullOfOrNull { it as? ConcordChannel } ?: return false
        val target = note.event ?: return false
        // Edits apply to messages and thread replies, and only the author may edit their own.
        if (!isConcordEditable(target)) return false

        val communityId = channel.channelId.communityId
        val channelIdHex = channel.channelId.channelId
        val session = account.concordSessions.sessionFor(communityId) ?: return false
        val entry = session.entry

        val plane = session.currentChannelPlane(channelIdHex) ?: return false
        val channelKey = plane.key
        // Carry NIP-30 custom-emoji tags for any `:shortcode:` in the new text, same as a fresh message.
        val emojiTags =
            account.emoji
                .findEmojiTags(newText)
                .map { it.toTagArray() }
                .toTypedArray()
        // CORD-08 §2: the Edit keeps the ORIGINAL message's deadline (none if it has none), never now + timer.
        val wrap = ConcordActions.buildChannelEdit(account.signer, channelKey, channelIdHex, plane.epoch, target, newText, TimeUtils.now(), emojiTags, expiration = ConcordDisappearing.expirationOf(target))
        publishConcordWrap(entry, wrap)
        return true
    }

    /** True when [target] is a Concord message this account may edit: my own kind-9 message or kind-1111 reply. */
    fun isConcordEditable(target: Event): Boolean = (target is ChatEvent || target is CommentEvent) && target.pubKey == account.signer.pubKey

    /**
     * Delete my own Concord rumors [notes] (messages, thread replies, reactions) in [channel] the
     * way CORD-01 prescribes: a kind-5 rumor with `e` + `k` tags, sealed (20013) and wrapped on the
     * channel's own plane, so only the community sees it. Never a signed kind 5 or a NIP-17 delete,
     * both of which would carry the rumor ids to people and relays outside the community.
     *
     * Each target is retracted on the plane that carried it (its bound epoch), falling back to the
     * channel's current plane when this account no longer holds that one. A member's delete of
     * their own message stays honored after Dissolution (CORD-02 §9), so this is not gated on it.
     * Returns false when nothing could be sent (not writeable, no session, no plane, no own notes).
     */
    suspend fun deleteConcordRumors(
        channel: ConcordChannel,
        notes: List<Note>,
    ): Boolean {
        if (!account.isWriteable()) return false
        val session = account.concordSessions.sessionFor(channel.channelId.communityId) ?: return false
        val channelIdHex = channel.channelId.channelId
        val mine = notes.mapNotNull { it.event }.filter { it.pubKey == account.signer.pubKey }.distinctBy { it.id }
        if (mine.isEmpty()) return false
        val current = session.currentChannelPlane(channelIdHex)
        val byPlane =
            mine.groupBy { target ->
                target.tags.concordEpoch()?.let { session.channelPlaneFor(channelIdHex, it) } ?: current
            }
        var sent = false
        for ((plane, targets) in byPlane) {
            if (plane == null) continue
            val wrap = ConcordActions.buildChannelDelete(account.signer, plane.key, channelIdHex, plane.epoch, targets, TimeUtils.now())
            publishConcordWrap(session.entry, wrap)
            sent = true
        }
        // Self-erasure outranks curation (CORD-04 §7): the delete hides a pinned entry for tracking
        // members at once, but a future member learns of it only through an omitting edition. The
        // author knows their own pins, so when they may write pins they publish it immediately.
        if (sent) omitDeletedConcordPins(channel.channelId.communityId, channelIdHex, mine.mapTo(HashSet()) { it.id })
        return sent
    }

    /**
     * Publish a typing heartbeat (kind-23311, ephemeral 21059) to a Concord channel — call at
     * most every few seconds while composing. Not folded locally (we never show our own typing);
     * ephemeral, so relays broadcast but never store it.
     */
    suspend fun sendConcordTyping(
        communityId: String,
        channelIdHex: String,
    ) {
        if (!account.isWriteable()) return
        val session = account.concordSessions.sessionFor(communityId) ?: return
        // A ban hides every message we send, so continuing to announce that we are typing them is
        // both noise and a contradiction of what the ban told the room. Filtered on the receive side
        // too (ConcordCommunitySession.ingestTyping) — a malicious client would keep sending.
        if (session.state.value
                ?.authority
                ?.isBanned(account.signer.pubKey) == true
        ) {
            return
        }
        val entry = session.entry
        val plane = session.currentChannelPlane(channelIdHex) ?: return
        val wrap = ConcordActions.buildChannelTyping(account.signer, plane.key, channelIdHex, plane.epoch, TimeUtils.now())
        val relays = entry.relays.mapNotNullTo(mutableSetOf()) { RelayUrlNormalizer.normalizeOrNull(it) }
        if (relays.isNotEmpty()) account.client.publish(wrap, relays)
    }

    /** Instant local echo (the session folds it back as a Note) + publish to the community relays. */
    private fun publishConcordWrap(
        entry: ConcordCommunityListEntry,
        wrap: Event,
    ) {
        account.concordSessions.ingest(wrap)
        val relays = entry.relays.mapNotNullTo(mutableSetOf()) { RelayUrlNormalizer.normalizeOrNull(it) }
        if (relays.isNotEmpty()) account.client.publish(wrap, relays)
    }

    /**
     * [publishConcordWrap] for a Control Plane edition whose caller must know it landed: waits for a
     * relay OK (`publish` only queues and never reports acceptance) and folds the wrap into the
     * session only then, so a refused write never shows as done locally. False when no relay of the
     * community accepted it.
     */
    private suspend fun publishConcordWrapConfirmed(
        entry: ConcordCommunityListEntry,
        wrap: Event,
    ): Boolean {
        val relays = entry.relays.mapNotNullTo(mutableSetOf()) { RelayUrlNormalizer.normalizeOrNull(it) }
        if (relays.isEmpty()) return false
        val landed =
            try {
                account.client.publishAndConfirm(wrap, relays)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Concord", "Control edition publish failed for ${entry.id}", e)
                false
            }
        if (landed) account.concordSessions.ingest(wrap)
        return landed
    }

    /**
     * Echo an own Concord channel message into its feed and publish it, driving
     * the bubble's send state as it goes.
     *
     * The echo comes first and is what makes the message appear immediately;
     * the send state is what says whether it actually got out. Relays OK the
     * encrypted [wrap] but the feed shows the inner rumor, so the tracker is
     * keyed by the rumor id — re-opening the wrap to find it always succeeds,
     * since we just built it. Reactions and typing wraps go through
     * [publishConcordWrap] instead: they never become a feed row, so there is
     * no bubble for a send state to live on.
     */
    private fun sendConcordChannelWrap(
        entry: ConcordCommunityListEntry,
        channelKey: GroupKey,
        wrap: Event,
    ) {
        val rumorId = ConcordStreamEnvelope.openOrNull(wrap, channelKey)?.rumor?.id
        if (rumorId == null) {
            publishConcordWrap(entry, wrap)
            return
        }

        account.chatDeliveryTracker.markSending(rumorId) { publishTrackedConcordWrap(entry, wrap, rumorId) }
        account.concordSessions.ingest(wrap)
        publishTrackedConcordWrap(entry, wrap, rumorId)
    }

    /**
     * The publish half of [sendConcordChannelWrap], without the local echo, so
     * a retry re-publishes the wrap instead of folding it into the session a
     * second time. Relays are re-resolved on every attempt: a community whose
     * relay list was empty is the one case worth retrying.
     */
    private fun publishTrackedConcordWrap(
        entry: ConcordCommunityListEntry,
        wrap: Event,
        rumorId: HexKey,
    ) {
        val relays = entry.relays.mapNotNullTo(mutableSetOf()) { RelayUrlNormalizer.normalizeOrNull(it) }
        if (relays.isEmpty()) {
            Log.w("Concord") { "No usable relay for ${entry.id}: channel message ${wrap.id} has nowhere to go" }
            account.chatDeliveryTracker.markFailed(rumorId)
            return
        }
        account.chatDeliveryTracker.trackWrappedPublic(rumorId, wrap.id, relays)
        account.client.publish(wrap, relays)
        account.chatDeliveryTracker.markSent(rumorId)
    }

    // ── Concord roles & moderation (CORD-04) ─────────────────────────────────
    // Each publishes a Control Plane edition; authority is enforced at fold time by
    // every client's AuthorityResolver, so a call by someone who doesn't outrank the
    // target is simply dropped on fold. Owner-authored calls always take effect.

    /**
     * The Control Plane keys for a moderation write, or null when this account cannot
     * publish there: on a split epoch only `control_root` holders can mint a wrap that
     * verifies at the plane's address (CORD-02 §2), and wrapping without the secret
     * throws rather than missigning. Rank and key possession can diverge — a freshly
     * promoted staffer writes only once their `control_wrap` is adopted (CORD-04 §3),
     * and the UI gates on rank — so every moderation verb no-ops through this check
     * instead of crashing on a rank-gated action.
     */
    private fun controlKeysForWrite(session: ConcordCommunitySession): ControlPlaneKeys? {
        val cp = session.controlPlaneKeys()
        if (!cp.canWrite) {
            Log.w("Concord") { "Control write refused for ${session.entry.id}: control_root not held at epoch ${session.entry.rootEpoch} (CORD-02 §2)" }
            return null
        }
        return cp
    }

    /**
     * Whether this account may take the action guarded by [bit] in [session] — and, when [target] is
     * given, take it *against that member* (CORD-04 §3's rank rule, "equal cannot act on equal").
     *
     * Every moderation verb below funnels through this. It used to live only in the composables that
     * drew the buttons, which failed three ways: the screens tested `effectivePermissions`, which
     * ignores the banlist, so a banned staffer still saw the controls; a verb reached from anywhere
     * else (desktop, `amy`, a new screen) inherited no check at all; and holding `control_root` —
     * a spam gate, never authority (CORD-02 §5) — was the only thing actually being enforced.
     *
     * Fails **closed**, with one deliberate exception: the owner is read from [ConcordCommunityListEntry]
     * rather than from the fold, because the community id proves them (CORD-02) and they must stay able
     * to moderate before their Control Plane has finished folding — or through a fold a rogue has
     * damaged. Everyone else needs a resolved roster, so an unfolded community grants nobody else
     * anything.
     */
    private fun isAuthorizedFor(
        session: ConcordCommunitySession,
        bit: Int,
        target: HexKey? = null,
    ): Boolean {
        val me = account.signer.pubKey
        if (session.entry.owner.equals(me, ignoreCase = true)) return true
        val authority = session.state.value?.authority ?: return false
        // hasPermission, never effectivePermissions: the latter reads the roles alone and would let a
        // banned staffer keep acting for as long as they hold the key.
        val allowed = if (target == null) authority.hasPermission(me, bit) else authority.canActOn(me, target, bit)
        if (!allowed) {
            Log.w("Concord") { "Refusing a Concord action in ${session.entry.id}: not authorized for bit $bit${target?.let { " on $it" } ?: ""} (CORD-04 §3)" }
        }
        return allowed
    }

    /** [controlKeysForWrite] gated by [isAuthorizedFor] — the standing check and the key check together. */
    private fun controlKeysForAction(
        session: ConcordCommunitySession,
        bit: Int,
        target: HexKey? = null,
    ): ControlPlaneKeys? {
        if (!isAuthorizedFor(session, bit, target)) return null
        return controlKeysForWrite(session)
    }

    /** Grant [member] exactly [roleIds] (empty list revokes their roles). */
    suspend fun grantConcordRole(
        communityId: String,
        member: HexKey,
        roleIds: List<String>,
    ): Boolean {
        val session = account.concordSessions.sessionFor(communityId) ?: return false
        if (!account.isWriteable()) return false
        val cp = controlKeysForAction(session, ConcordPermissions.MANAGE_ROLES, member) ?: return false
        val before = session.state.value?.authority
        // A Grant that first makes its member staff must deliver the control_root in the same
        // edition (CORD-04 §3) — grantWithStaffDelivery attaches the pairwise wrap when the
        // roles carry a Control-writing bit and we hold the secret to hand over.
        val wrap =
            ConcordModeration.grantWithStaffDelivery(
                actor = account.signer,
                controlPlane = cp,
                communityId = communityId.hexToByteArray(),
                member = member,
                roleIds = roleIds,
                current = session.controlEditions(),
                createdAt = TimeUtils.now(),
                owner = session.entry.owner,
                controlRoot = session.entry.controlRoot?.hexToByteArray(),
                epoch = session.entry.rootEpoch,
            )
        publishConcordWrap(session.entry, wrap)
        // Role-gated channel keys follow the Grant: vend what it opened, rotate what it closed.
        reconcileConcordChannelAccess(communityId, before)
        return true
    }

    /**
     * The default community Admin role: position 1, holding every currently-defined permission —
     * the reference client's `ADMIN_ALL` bit for bit, so an Admin minted here can pin, read the
     * audit log and mention everyone exactly like one minted there. There is no all-powerful bit
     * (CORD-04 §3): a permission added later is not inherited.
     */
    private fun concordAdminRole() =
        RoleEntity(
            name = CONCORD_ADMIN_ROLE,
            position = 1,
            permissions =
                ConcordPermissions
                    .of(
                        ConcordPermissions.MANAGE_ROLES,
                        ConcordPermissions.MANAGE_CHANNELS,
                        ConcordPermissions.MANAGE_METADATA,
                        ConcordPermissions.KICK,
                        ConcordPermissions.BAN,
                        ConcordPermissions.MANAGE_MESSAGES,
                        ConcordPermissions.CREATE_INVITE,
                        ConcordPermissions.VIEW_AUDIT_LOG,
                        ConcordPermissions.MENTION_EVERYONE,
                        ConcordPermissions.PIN_MESSAGES,
                    ).toWire(),
        )

    /**
     * If [note] is a Concord channel message whose author the OWNER may toggle
     * "admin" on, returns `(communityId, memberHex, isAlreadyAdmin)`. Only the owner
     * qualifies — the Admin role sits at position 1 and the resolver requires the
     * granter to *strictly* outrank it, which only the owner (rank 0) does. Null for
     * the owner's own note, the owner as target, or a non-owner actor.
     */
    fun concordAdminTarget(note: Note): Triple<String, HexKey, Boolean>? {
        val channel = note.inGatherers?.firstNotNullOfOrNull { it as? ConcordChannel } ?: return null
        val author = note.author?.pubkeyHex ?: note.event?.pubKey ?: return null
        if (author == account.signer.pubKey) return null
        val communityId = channel.channelId.communityId
        val session = account.concordSessions.sessionFor(communityId) ?: return null
        val state = session.state.value ?: return null
        // Rank alone isn't enough on a split epoch: the Grant edition takes the control_root
        // (CORD-02 §2), so don't offer an action the verb would refuse.
        if (!session.controlPlaneKeys().canWrite) return null
        if (state.authority.isOwner(author) || !state.authority.isOwner(account.signer.pubKey)) return null
        val adminRoleId =
            state.roles.entries
                .firstOrNull { it.value.name == CONCORD_ADMIN_ROLE && it.value.position == 1L }
                ?.key
        val isAdmin = adminRoleId != null && adminRoleId in state.authority.rolesOf(author)
        return Triple(communityId, author, isAdmin)
    }

    /**
     * Promote [member] to the community Admin role, defining that role first if it doesn't exist
     * yet. A Grant replaces the member's whole role set, so Admin is added to the roles they already
     * hold — granting it alone would silently strip, say, a private channel's access role.
     */
    suspend fun makeConcordAdmin(
        communityId: String,
        member: HexKey,
    ): Boolean {
        val session = account.concordSessions.sessionFor(communityId) ?: return false
        if (!account.isWriteable()) return false
        val cp = controlKeysForAction(session, ConcordPermissions.MANAGE_ROLES, member) ?: return false
        val before = session.state.value?.authority

        val existing =
            session.state.value
                ?.roles
                ?.entries
                ?.firstOrNull { it.value.name == CONCORD_ADMIN_ROLE && it.value.position == 1L }
        val roleIdHex =
            existing?.key ?: run {
                val roleId = RandomInstance.bytes(32)
                val roleWrap = ConcordModeration.defineRole(account.signer, cp, communityId.hexToByteArray(), roleId, concordAdminRole(), session.controlEditions(), TimeUtils.now(), owner = session.entry.owner)
                publishConcordWrap(session.entry, roleWrap)
                roleId.toHexKey()
            }

        // An Admin role published before Amethyst wrote `role_id` into Role content is invisible
        // to Armada, which then drops this Grant too. Re-issue it at the same id to heal it.
        if (existing != null && existing.value.roleId.isNullOrEmpty()) {
            val healWrap = ConcordModeration.defineRole(account.signer, cp, communityId.hexToByteArray(), roleIdHex.hexToByteArray(), existing.value, session.controlEditions(), TimeUtils.now(), owner = session.entry.owner)
            publishConcordWrap(session.entry, healWrap)
        }

        // Admin carries every management bit, so this Grant makes its member staff: it must
        // deliver the control_root alongside the rank (CORD-04 §3), or the new admin holds
        // authority it cannot publish under.
        val grantWrap =
            ConcordModeration.grantWithStaffDelivery(
                actor = account.signer,
                controlPlane = cp,
                communityId = communityId.hexToByteArray(),
                member = member,
                roleIds = (before?.rolesOf(member).orEmpty() + roleIdHex).toList(),
                current = session.controlEditions(),
                createdAt = TimeUtils.now(),
                owner = session.entry.owner,
                controlRoot = session.entry.controlRoot?.hexToByteArray(),
                epoch = session.entry.rootEpoch,
            )
        publishConcordWrap(session.entry, grantWrap)
        reconcileConcordChannelAccess(communityId, before)
        return true
    }

    /** Take the Admin role away from [member], keeping every other role they hold (e.g. a channel's access role). */
    suspend fun removeConcordAdmin(
        communityId: String,
        member: HexKey,
    ): Boolean {
        val session = account.concordSessions.sessionFor(communityId) ?: return false
        if (!account.isWriteable()) return false
        val cp = controlKeysForAction(session, ConcordPermissions.MANAGE_ROLES, member) ?: return false
        val state = session.state.value ?: return false
        val before = state.authority
        val adminRoleIds =
            state.roles.entries
                .filter { it.value.name == CONCORD_ADMIN_ROLE && it.value.position == 1L }
                .mapTo(HashSet()) { it.key }
        val kept = before.rolesOf(member) - adminRoleIds
        val grantWrap = ConcordModeration.grant(account.signer, cp, communityId.hexToByteArray(), member, kept.toList(), session.controlEditions(), TimeUtils.now(), owner = session.entry.owner)
        publishConcordWrap(session.entry, grantWrap)
        reconcileConcordChannelAccess(communityId, before)
        return true
    }

    /**
     * If [note] is a Concord channel message whose author this account is allowed to
     * ban — the actor outranks the target and holds the BAN permission, and the target
     * is neither the owner nor the actor — returns `(communityId, memberHex)`. Null
     * otherwise, so the UI offers Ban only where we are willing to act.
     *
     * The rank half is ours alone. CORD-04 rank-gates role grants (`canActOn`) but the
     * BANLIST is a single whole-list entity, so neither this client's fold nor Armada's
     * rank-checks the *contents* of a banlist edition — both gate only on the author's
     * BAN bit (Armada: `banlistGate` → `isAuthorized(.., Permissions.BAN)`, while its
     * role path uses the rank-aware `canActOnPosition`). A moderator's ban of an admin
     * above them is therefore *accepted* by every client today. Since we cannot refuse
     * such a ban without diverging from Armada, we at least refuse to author one — this
     * restricts what we write, never what we accept, so it cannot split consensus.
     * Enforcing it on the fold needs a spec change; see the QA plan's open findings.
     */
    fun concordBanTarget(note: Note): Pair<String, HexKey>? {
        val channel = note.inGatherers?.firstNotNullOfOrNull { it as? ConcordChannel } ?: return null
        val author = note.author?.pubkeyHex ?: note.event?.pubKey ?: return null
        if (author == account.signer.pubKey) return null
        val communityId = channel.channelId.communityId
        val session = account.concordSessions.sessionFor(communityId) ?: return null
        val authority = session.state.value?.authority ?: return null
        // Rank alone isn't enough on a split epoch: the banlist edition takes the control_root
        // (CORD-02 §2), so don't offer an action the verb would refuse.
        if (!session.controlPlaneKeys().canWrite) return null
        if (authority.isOwner(author)) return null
        // The owner short-circuits rather than going through canActOn: canActOn starts at
        // hasPermission, which is false while banned, and a rogue BAN holder *can* currently put
        // the owner on the banlist (see the KDoc) — routing the owner through it would let them be
        // locked out of moderating their own community.
        val canBan = authority.isOwner(account.signer.pubKey) || authority.canActOn(account.signer.pubKey, author, ConcordPermissions.BAN)
        return if (canBan) communityId to author else null
    }

    /**
     * Ban [member] (CORD-04 §5 composition): the Banlist edition first, then — only when the
     * community is **Private** — the Refounding (CORD-06 §3). A Public ban is the Banlist alone
     * (CORD-05 §5): anyone holding a live link can fetch a rotated root straight back out of its
     * bundle, so rotating would cost every member a rekey and sever nobody. The mode is judged with
     * the target's own links left out, since the ban stops honoring their registry
     * ([com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityState.banRequiresRefounding]).
     *
     * Returns whether the ban landed; a Refounding that fails is logged and can be retried with
     * [refoundConcordCommunity].
     */
    suspend fun banConcordMember(
        communityId: String,
        member: HexKey,
    ): Boolean {
        val session = account.concordSessions.sessionFor(communityId) ?: return false
        if (!account.isWriteable()) return false
        val cp = controlKeysForAction(session, ConcordPermissions.BAN, member) ?: return false
        val before = session.state.value?.authority
        val wrap = ConcordModeration.ban(account.signer, cp, communityId.hexToByteArray(), member, session.controlEditions(), TimeUtils.now(), owner = session.entry.owner)
        publishConcordWrap(session.entry, wrap)
        // Judged on the fold that now carries the ban (publishConcordWrap ingests it first).
        val state = session.state.value
        if (state != null && state.banRequiresRefounding(listOf(member))) {
            // The Refounding rotates every held Private Channel to its entitled set (CORD-06 §3).
            if (!refoundConcordCommunity(communityId, setOf(member))) {
                Log.w("Concord") { "Banned $member from the Private community $communityId, but its Refounding did not complete" }
            }
        } else {
            // A Public ban keeps the base, so the Private Channels the target could read are cut by
            // their own rekeys (CORD-04 §6: "a Private-Channel rekey for a channel-scoped cut").
            reconcileConcordChannelAccess(communityId, before)
        }
        return true
    }

    /** Remove [member] from the community banlist. */
    suspend fun unbanConcordMember(
        communityId: String,
        member: HexKey,
    ): Boolean {
        val session = account.concordSessions.sessionFor(communityId) ?: return false
        if (!account.isWriteable()) return false
        val cp = controlKeysForAction(session, ConcordPermissions.BAN, member) ?: return false
        val wrap = ConcordModeration.unban(account.signer, cp, communityId.hexToByteArray(), member, session.controlEditions(), TimeUtils.now(), owner = session.entry.owner)
        publishConcordWrap(session.entry, wrap)
        return true
    }

    /**
     * Kick [member] (CORD-04 §6, the Cooperative Kick): Role Removal first — an empty Grant when they
     * hold roles and this account may strip them (MANAGE_ROLES + outrank; best-effort, as the
     * reference client) — so their rank is gone before the departure lands, *then* the Guestbook
     * directive (kind 3309) citing our Grant (`vac`). Needs KICK and a strict outrank of the target;
     * the directive rides the Guestbook, so no Control write key is needed for it. A kicked member
     * may re-join or be re-invited. Refused on a dissolved community (CORD-02 §9).
     */
    suspend fun kickConcordMember(
        communityId: String,
        member: HexKey,
    ): Boolean {
        val session = account.concordSessions.sessionFor(communityId) ?: return false
        if (!account.isWriteable()) return false
        if (!isAuthorizedFor(session, ConcordPermissions.KICK, member)) return false
        if (session.state.value?.dissolved == true) return false
        val me = account.signer.pubKey
        val authority = session.state.value?.authority
        if (authority != null && authority.rolesOf(member).isNotEmpty() &&
            (authority.isOwner(me) || authority.canActOn(me, member, ConcordPermissions.MANAGE_ROLES))
        ) {
            if (!grantConcordRole(communityId, member, emptyList())) {
                Log.w("Concord") { "Kick of $member in $communityId: could not strip their roles first; kicking anyway" }
            }
        }
        val entry = session.entry
        val guestbook = ConcordActions.guestbookPlane(entry.root.hexToByteArray(), entry.id.hexToByteArray(), entry.rootEpoch)
        val citation =
            session.state.value
                ?.authority
                ?.let { AuthorityCitations.forActor(it, me) }
        val wrap = ConcordActions.buildGuestbookKick(account.signer, guestbook, member, citation, TimeUtils.now())
        publishConcordWrap(entry, wrap)
        return true
    }

    private val _concordKicks = MutableSharedFlow<ConcordKickNotice>(extraBufferCapacity = 16)

    /** One notice per community this account left because an honored Kick named it ([drainConcordKicks]). */
    val concordKicks: SharedFlow<ConcordKickNotice> = _concordKicks.asSharedFlow()

    // Rumor ids of the Kicks already complied with, so a revision tick racing the List write acts once.
    private val handledConcordKicks = ConcurrentSet<HexKey>()

    /**
     * Compliance with a Kick against this account (CORD-04 §6): a compliant client tears the
     * Community down locally. For each joined community whose coalesced Guestbook carries an honored
     * Kick naming us that postdates this membership ([ConcordCommunitySession.kickedMe]), drop the
     * List entry and tombstone it, exactly as a Leave does, and tell the user
     * ([concordKicks]). Network-silent beyond the List write (no Guestbook Leave): the Kick already
     * says we departed. The tombstone never blocks a later re-join — re-adding an entry bumps its
     * `added_at` past the tombstone, and that newer `added_at` puts this Kick behind the new
     * membership. Runs on the Concord revision tick; a Guestbook arrival and a control fold (which can
     * newly honor a parked Kick) both bump it.
     */
    internal suspend fun drainConcordKicks() {
        if (!account.isWriteable()) return
        for (session in account.concordSessions.sessions()) {
            val kick = session.kickedMe() ?: continue
            if (!handledConcordKicks.add(kick.rumorId)) continue
            val communityId = session.entry.id
            val name =
                session.state.value
                    ?.metadata
                    ?.name
            if (leaveConcordCommunity(communityId)) {
                Log.i("Concord") { "Kicked from $communityId by ${kick.author}: left the community locally (CORD-04 §6)" }
                _concordKicks.tryEmit(ConcordKickNotice(communityId, name, kick.author))
            } else {
                handledConcordKicks.remove(kick.rumorId)
            }
        }
    }

    // ── Concord pins (CORD-04 §7) ─────────────────────────────────────────────
    // A Channel's Pin List rides the Control Plane as one replace-entire edition (vsk 11) of
    // self-proving entries. The read side verifies every entry and applies what this client holds
    // (deletes hide, newer Edits mark "edited"); the write side is ConcordPinning, gated here on
    // PIN_MESSAGES + the control write key and serialized so two quick writes never drop each other.

    /** Serializes pin writes: each replaces the list entire, so two in flight would lose one. */
    private val concordPinMutex = Mutex()

    /** Owner, or a PIN_MESSAGES holder per the fold (hasPermission, so a banned holder is not). Silent: UI gating asks this often. */
    private fun holdsConcordPinBit(session: ConcordCommunitySession): Boolean {
        val me = account.signer.pubKey
        if (session.entry.owner.equals(me, ignoreCase = true)) return true
        return session.state.value
            ?.authority
            ?.hasPermission(me, ConcordPermissions.PIN_MESSAGES) == true
    }

    /** True when this account may write [communityId]'s Pin Lists now: the bit, the control write key, a signer. */
    fun canPinConcord(communityId: String): Boolean {
        val session = account.concordSessions.sessionFor(communityId) ?: return false
        return account.isWriteable() && holdsConcordPinBit(session) && session.controlPlaneKeys().canWrite
    }

    /**
     * [channelIdHex]'s verified pins, read from the current head: sealed lists open with the held
     * key of their epoch (else [ConcordChannelPins.sealedUnavailable]), an entry its author deleted
     * is hidden by the delete this account holds for the recomputed rumor id, and an entry behind a
     * newer held Edit is marked edited. Null until the community has folded the channel.
     */
    fun concordChannelPins(
        communityId: String,
        channelIdHex: String,
    ): ConcordChannelPins? {
        val session = account.concordSessions.sessionFor(communityId) ?: return null
        return session.readPins(
            channelIdHex,
            isKilled = { account.cache.deletionIndex.hasBeenDeleted(it.rumorId, it.author) },
            newestEdit = { heldConcordEdit(it.rumorId, it.author) },
        )
    }

    /**
     * [pinned] as the rumor the chat feed would render — its newest proven words over the original's
     * tags (the NIP-92 `imeta` attachments), with the encrypted attachments' keys registered so the
     * shared media pipeline fetches and decrypts them exactly as for a feed message. A pin proves its
     * message without this account ever having held it, so the keys come from the proof itself.
     */
    fun concordPinnedRumor(pinned: ConcordPinnedMessage): Event {
        val rumor = pinned.toRumor()
        account.registerConcordEncryptedImages(rumor)
        return rumor
    }

    /** The author's newest Concord Edit this account holds for [rumorId], or null. */
    private fun heldConcordEdit(
        rumorId: HexKey,
        author: HexKey,
    ): ConcordLocalEdit? {
        val edit =
            account.cache
                .getNoteIfExists(rumorId)
                ?.latestConcordEdit()
                ?.event as? ConcordChatEditEvent ?: return null
        if (edit.pubKey != author) return null
        return ConcordLocalEdit(edit.id, edit.pubKey, edit.content, edit.orderingMs())
    }

    /**
     * For the message action sheet: null when [note] is not a pinnable Concord message or this
     * account cannot write pins there; else whether it is pinned now.
     */
    fun concordPinState(note: Note): Boolean? {
        val event = note.event ?: return null
        if (event !is ChatEvent && event !is CommentEvent) return null
        val channel = note.inGatherers?.firstNotNullOfOrNull { it as? ConcordChannel } ?: return null
        if (!canPinConcord(channel.channelId.communityId)) return null
        val pins = concordChannelPins(channel.channelId.communityId, channel.channelId.channelId) ?: return null
        return pins.isPinned(note.idHex)
    }

    /**
     * Runs one pin write: re-reads the list inside the lock (the previous write was echoed into the
     * session, so this chains onto it), resolves the context, and publishes the edition [op] builds.
     *
     * The publish waits for a relay OK ([ConcordPinOutcome.NOT_CONFIRMED] otherwise). Then, like a
     * ban, the write re-heals: a concurrent curator's edition at the same version may win the fold
     * (CORD-04 §1), silently dropping this change, so when the refolded head is not ours the same
     * [op] runs again on top of the winner — at most [PIN_REHEAL_RETRIES] times.
     */
    private suspend fun writeConcordPins(
        communityId: String,
        channelIdHex: String,
        op: suspend (ConcordCommunitySession, ConcordPinContext) -> ConcordPinWrite,
    ): ConcordPinOutcome =
        concordPinMutex.withLock {
            if (!account.isWriteable()) return@withLock ConcordPinOutcome.NOT_WRITEABLE
            repeat(1 + PIN_REHEAL_RETRIES) {
                val session = account.concordSessions.sessionFor(communityId) ?: return@withLock ConcordPinOutcome.NOT_FOLDED
                val definition =
                    session.state.value
                        ?.channels
                        ?.get(channelIdHex)
                        ?.definition ?: return@withLock ConcordPinOutcome.NOT_FOLDED
                val pins = concordChannelPins(communityId, channelIdHex) ?: return@withLock ConcordPinOutcome.NOT_FOLDED
                val ctx =
                    ConcordPinContext(
                        actor = account.signer,
                        controlPlane = session.controlPlaneKeys(),
                        communityId = communityId.hexToByteArray(),
                        owner = session.entry.owner,
                        current = session.controlEditions(),
                        channelIdHex = channelIdHex,
                        channelIsPrivate = definition.private,
                        currentPlane = session.currentChannelPlane(channelIdHex),
                        pins = pins,
                        authorized = holdsConcordPinBit(session),
                    )
                val write = op(session, ctx)
                // A retry that finds the winner already says what we meant (ALREADY_PINNED, NOT_PINNED,
                // NOTHING_TO_DO) ends here too.
                val wrap = write.wrap ?: return@withLock write.outcome
                if (!publishConcordWrapConfirmed(session.entry, wrap)) return@withLock ConcordPinOutcome.NOT_CONFIRMED
                val ours = ConcordStreamEnvelope.openOrNull(wrap, ctx.controlPlane)?.let { ControlEdition.fromOpened(it) }?.rumorId
                if (ours == null || session.pinHeads.value[channelIdHex]?.rumorId == ours) return@withLock ConcordPinOutcome.PUBLISHED
                Log.i("Concord") { "Pin List edit in $communityId lost the fold to a concurrent edition; re-applying on the winner" }
            }
            // Landed every time but kept losing the tie-break: it is on the relays, just not the head.
            ConcordPinOutcome.PUBLISHED
        }

    /** Pin Concord message [note] into its channel's Pin List, proving it with its original seal. */
    suspend fun pinConcordMessage(note: Note): ConcordPinOutcome {
        val channel = note.inGatherers?.firstNotNullOfOrNull { it as? ConcordChannel } ?: return ConcordPinOutcome.NOT_FOLDED
        val channelIdHex = channel.channelId.channelId
        return writeConcordPins(channel.channelId.communityId, channelIdHex) { session, ctx ->
            val refused = ConcordPinning.refusal(ctx)
            val source = if (refused == null) session.pinSource(channelIdHex, note.idHex) else null
            when {
                refused != null -> ConcordPinWrite(refused)
                source == null -> ConcordPinWrite(ConcordPinOutcome.MESSAGE_UNAVAILABLE)
                else -> ConcordPinning.pin(ctx, source, TimeUtils.now())
            }
        }
    }

    /** Unpin Concord message [note]. */
    suspend fun unpinConcordMessage(note: Note): ConcordPinOutcome {
        val channel = note.inGatherers?.firstNotNullOfOrNull { it as? ConcordChannel } ?: return ConcordPinOutcome.NOT_FOLDED
        return unpinConcordRumor(channel.channelId.communityId, channel.channelId.channelId, note.idHex)
    }

    /** Unpin the entry whose recomputed rumor id is [rumorId] — works for a pin whose message this account never held. */
    suspend fun unpinConcordRumor(
        communityId: String,
        channelIdHex: String,
        rumorId: HexKey,
    ): ConcordPinOutcome = writeConcordPins(communityId, channelIdHex) { _, ctx -> ConcordPinning.unpin(ctx, rumorId, TimeUtils.now()) }

    /** The pinner-style deletion omission: the list without [rumorIds], published now when this account may write pins. */
    private suspend fun omitDeletedConcordPins(
        communityId: String,
        channelIdHex: String,
        rumorIds: Set<HexKey>,
    ) {
        if (!canPinConcord(communityId)) return
        val pins = concordChannelPins(communityId, channelIdHex) ?: return
        if (pins.alive.none { it.rumorId in rumorIds } && pins.killed.none { it.rumorId in rumorIds }) return
        writeConcordPins(communityId, channelIdHex) { _, ctx -> ConcordPinning.omit(ctx, rumorIds, TimeUtils.now()) }
    }

    /**
     * Settle what [channelIdHex]'s head owes keyless readers (CORD-04 §7): drop entries their author
     * erased and attach the newest provable Edit to entries behind one. The caller waits
     * [ConcordPinning.dutyDelayMs] first; this re-reads the head and publishes only if it is still
     * owed, so simultaneous curators collapse to one publisher and a burst of edits costs one write.
     */
    suspend fun settleConcordPins(
        communityId: String,
        channelIdHex: String,
    ): ConcordPinOutcome {
        if (!canPinConcord(communityId)) return ConcordPinOutcome.NOT_AUTHORIZED
        if (concordChannelPins(communityId, channelIdHex)?.owesRepublish != true) return ConcordPinOutcome.NOTHING_TO_DO
        return writeConcordPins(communityId, channelIdHex) { session, ctx ->
            ConcordPinning.settle(ctx, { pinned -> pinned.newerEdit?.let { session.pinSource(channelIdHex, it.rumorId) } }, TimeUtils.now())
        }
    }

    private val concordPinDuties = ConcordPinDutyScheduler()

    /**
     * Schedules, in [scope], the delayed pin duties (CORD-04 §7) this account owes in every joined
     * community where it may write pins: for each Channel with a Pin List head whose read owes a
     * republish, one [settleConcordPins] after the 3–15 s random wait ([ConcordPinDutyScheduler]).
     * Called by the account on the Concord revision tick and when a delete or an Edit lands — the
     * duties no longer depend on the channel screen being open.
     */
    internal fun scheduleConcordPinDuties(scope: CoroutineScope) {
        if (!account.isWriteable()) return
        for (session in account.concordSessions.sessions()) {
            val communityId = session.entry.id
            if (!canPinConcord(communityId)) continue
            for (channelIdHex in session.pinHeads.value.keys) {
                val debt = ConcordPinDutyScheduler.debtOf(concordChannelPins(communityId, channelIdHex))
                concordPinDuties.schedule(scope, ConcordPinDutyScheduler.keyOf(communityId, channelIdHex), debt) {
                    runCatching { settleConcordPins(communityId, channelIdHex) }
                        .onFailure { Log.w("Concord", "pin duty for $channelIdHex in $communityId failed", it) }
                }
            }
        }
    }

    // ── Concord refounding / rekey (CORD-06) ──────────────────────────────────
    // A ban is a soft removal — the banned member still holds the room key and can
    // still decrypt traffic; every client just declines to *show* their posts. A
    // Refounding is the hard removal: it rotates the community_root, so a removed
    // member's key stops working for anything published afterwards.

    /**
     * Remove [removed] from the community absolutely (CORD-06 Refounding): ban them,
     * roll the `community_root`, re-key every retained member (Guestbook membership ∪
     * observed authors ∪ the privileged roster ∪ self) via kind-3303 blobs, and republish the compacted
     * Control Plane under the new root. A removed member keeps the prior root (so
     * their history stays readable) but receives no blob, so they can never decrypt
     * anything published after the rotation.
     *
     * Requires ownership or the BAN permission; returns false otherwise (or if the
     * community isn't joined/writeable, or a target is the owner).
     */
    suspend fun refoundConcordCommunity(
        communityId: String,
        removed: Set<HexKey>,
    ): Boolean {
        if (removed.isEmpty()) return false
        return refound(communityId, removed)
    }

    /**
     * Converts the community to Private (CORD-06 §3): a Refounding with nobody removed, run when its
     * last live invite link is retired (CORD-05 §2/§5). Every member is re-keyed; whoever only ever
     * fetched a link — and so holds the current root without being a member — is left behind.
     * Takes BAN (or ownership), like any Refounding.
     */
    suspend fun privatizeConcordCommunity(communityId: String): Boolean = refound(communityId, emptySet())

    private suspend fun refound(
        communityId: String,
        removed: Set<HexKey>,
    ): Boolean {
        if (!account.isWriteable()) return false
        val session = account.concordSessions.sessionFor(communityId) ?: return false
        val state = session.state.value ?: return false
        // Death wins every race (CORD-02 §9): no epoch advance past a tombstone is honored, so a
        // Refounding of a dissolved community would only strand whoever follows it.
        if (state.dissolved) {
            Log.w("Concord") { "Refusing to refound ${session.entry.id}: the community was dissolved (CORD-02 §9)" }
            return false
        }
        val authority = state.authority
        // hasPermission, not effectivePermissions: a Refounding is the hardest action in the protocol
        // and this guard used to ignore the banlist, so a banned BAN-holder could launch one from the
        // shipping app. Honest receivers refuse such a rotation (drainConcordRekeys checks the same
        // ban-aware predicate), but that is a race against banlist propagation, not a check.
        val iCanBan = authority.isOwner(account.signer.pubKey) || authority.hasPermission(account.signer.pubKey, ConcordPermissions.BAN)
        if (!iCanBan) return false
        val removedLower = removed.mapTo(HashSet()) { it.lowercase() }
        if (removedLower.any { authority.isOwner(it) }) return false
        // Removal is the hardest form of a ban, so it takes the same rank rule (CORD-04 §3): an admin
        // cannot Refound a peer admin out of the community any more than they could ban one. The owner
        // short-circuits, as everywhere else, because canActOn starts at hasPermission.
        if (!authority.isOwner(account.signer.pubKey) &&
            removedLower.any { !authority.canActOn(account.signer.pubKey, it, ConcordPermissions.BAN) }
        ) {
            return false
        }
        // A Refounding writes the current plane (the pre-rotation bans) and the new one (the
        // compaction), so on a split epoch it takes the current control_root (CORD-02 §2). A
        // rank-qualified refounder whose secret hasn't arrived yet must wait for re-delivery.
        val cp = controlKeysForWrite(session) ?: return false
        val entry = session.entry
        val publishTo = entry.relays.mapNotNullTo(mutableSetOf()) { RelayUrlNormalizer.normalizeOrNull(it) }
        if (publishTo.isEmpty()) return false

        // 0. Acquire the whole Control Plane BEFORE the first publish (CORD-06 §3: "If the Refounder
        //    cannot reliably fold all Control events, the Refounding must be aborted"). The live
        //    buffer is whatever the subscription happened to deliver; a paged sweep that a majority
        //    of the community's relays drained is what makes the compaction the whole plane.
        val swept = sweepConcordControlPlane(cp.address, publishTo)
        if (swept == null) {
            Log.w("Concord") { "Refounding ${entry.id} aborted: too few relays served the whole Control Plane" }
            return false
        }

        // 1. Ban the removed members on the current Control Plane so the compacted snapshot —
        //    and thus the new epoch — carries the ban. publishConcordWrap folds it in locally
        //    first, so each subsequent edition chains onto the updated banlist head.
        //    A target the fold already bans (a ban that is composing its Refounding) needs no second edition.
        val alreadyBanned = authority.bannedMembers().mapTo(HashSet()) { it.lowercase() }
        for (target in removedLower) {
            if (target in alreadyBanned) continue
            val banWrap = ConcordModeration.ban(account.signer, cp, communityId.hexToByteArray(), target, session.controlEditions(), TimeUtils.now(), owner = session.entry.owner)
            publishConcordWrap(session.entry, banWrap)
        }

        // 2. Recipient set: everyone we're keeping, minus the removed and the already-banned.
        //    Uses allMembers() — Guestbook joins ∪ OBSERVED AUTHORS ∪ roster ∪ owner — not just the
        //    Guestbook set. Most members never send a Guestbook Join (Amethyst announces one, other
        //    clients need not), so building the set without observed authors silently expelled every
        //    member who had only ever posted: they hold no role, receive no blob, and the Refounding
        //    strands them. That mainly hit cross-client communities, where Armada members are the
        //    bulk of the roster.
        //
        //    Still a floor, not a census (see allMembers): a member who joined without a Guestbook
        //    motion, holds no role, and has never posted leaves no trace to find, so a Refounding
        //    cannot re-key them.
        val recipients =
            (session.allMembers() + account.signer.pubKey)
                .mapTo(HashSet()) { it.lowercase() }
                .apply {
                    removeAll(removedLower)
                    removeAll(authority.bannedMembers())
                }.let { candidates -> boundRecipients(candidates, authority) }

        // 3. Build the refounding: new root, compacted Control Plane, per-recipient rekey blobs.
        //    The keys are RESERVED per (epoch, prior root): a retry after a failed publish re-delivers
        //    the same root instead of minting a sibling that would split the members (CORD-06 §3).
        //    A fresh control_root rides beside the new root at every Refounding (CORD-02 §2), so a
        //    demoted staffer's retained secret dies with the epoch — and a legacy community upgrades
        //    to the split as a side effect of its next ban (CORD-06 §3).
        val priorRoot = entry.root.hexToByteArray()
        val keys = ConcordRefounding.reserveKeys(pendingConcordRefoundings[entry.id], entry.id, entry.rootEpoch, priorRoot)
        pendingConcordRefoundings[entry.id] = keys
        val editions = session.controlEditions()
        // The rotation cites the Grant it acts under (CORD-06 §3 "Authority"); the owner cites none.
        // A non-owner with no Grant in our own fold has nothing to cite, so no receiver would honor it.
        val citation = ConcordReceive.rotationCitation(entry, editions, account.signer.pubKey)
        if (citation == null && !authority.isOwner(account.signer.pubKey)) {
            Log.w("Concord") { "Refounding ${entry.id} aborted: no Grant of ours to cite (CORD-06 §3)" }
            return false
        }
        // The staff set the new secret goes to: the owner plus everyone holding a
        // Control-writing bit (CORD-04 §3). They get the 136-byte blob, every other
        // recipient the 104-byte one carrying the pubkey alone. (The builder mints a
        // blob per recipient, so staff who aren't recipients are simply never reached.)
        val staff = authority.staffMembers()
        val build =
            try {
                ConcordActions.buildRefounding(
                    rotatorSigner = account.signer,
                    communityId = communityId,
                    priorRoot = priorRoot,
                    newRoot = keys.newRoot,
                    newControlRoot = keys.newControlRoot,
                    rootEpoch = entry.rootEpoch,
                    priorControlWraps = (session.controlPlaneWraps() + swept).distinctBy { it.id },
                    priorControlKeys = cp,
                    recipientsXOnly = recipients,
                    staffXOnly = staff,
                    createdAt = TimeUtils.now(),
                    ownerPubKey = entry.owner,
                    authority = citation,
                    // Every head our own fold honors must survive into the new epoch.
                    mustCarry = ConcordRefounding.headVersions(editions, entry.id.hexToByteArray(), entry.owner),
                )
            } catch (e: IncompleteControlPlaneException) {
                Log.w("Concord", "Refounding ${entry.id} aborted: the Control Plane could not be folded in full", e)
                return false
            }

        // 4. The root roll FIRST, each chunk confirmed (CORD-06 §3): the compacted plane is
        //    republished only after the rekey blobs are known to have landed. A chunk no relay
        //    accepted aborts here with nothing adopted; the reserved keys make the retry idempotent.
        for (wrap in build.rekeyWraps) {
            if (!runCatching { account.client.publishAndConfirm(wrap, publishTo) }.getOrDefault(false)) {
                Log.w("Concord") { "Refounding ${entry.id} aborted: a rekey chunk was not accepted by any relay; retrying reuses the same root" }
                return false
            }
        }

        // 5. The compacted Control Plane, at the new epoch's address. The root roll is committed, so
        //    a head that fails to land is reported, not rolled back — members already hold the new
        //    root, and the next Refounding re-compacts from the same signed heads.
        var compactionLanded = true
        for (wrap in build.controlWraps) {
            if (!runCatching { account.client.publishAndConfirm(wrap, publishTo) }.getOrDefault(false)) compactionLanded = false
        }
        if (!compactionLanded) Log.w("Concord") { "Refounding ${entry.id}: some compacted Control Plane heads were not accepted at epoch ${build.newEpoch}" }

        // 5b. Rotate every held Private Channel (CORD-06 §3), each to its OWN entitled set among the
        //     kept members, sealed under the PRIOR root so a base-fork loser can still open it. A
        //     channel that fails to land keeps its key (and is logged): resumable, not atomic.
        val rotatedEntry = rotatePrivateChannelsForRefounding(entry, recipients.toSet(), priorRoot, citation)

        // 6. Adopt the new epoch ourselves. This rebuilds our session under the new root and
        //    re-folds the compacted Control Plane (with the ban), dropping the removed members.
        val adopted = adoptConcordRoot(rotatedEntry, keys.newRoot, build.newEpoch, build.newControlKeys.address.hexToByteArray(), keys.newControlRoot)
        pendingConcordRefoundings.remove(entry.id)

        // 7. Move every link we minted to the new epoch. Without this the Refounding orphans them,
        //    and a member it left out — no rekey blob, no message to miss — has no way back at all.
        //    Uses the entry adoption just wrote: `liveCommunities` decrypts asynchronously, so
        //    reading it here would hand us the epoch we just left and re-mint every link onto it.
        val moved = adopted?.let { refreshConcordInviteLinks(it) } ?: 0
        Log.i("Concord") { "Refounding ${entry.id}: refreshed $moved invite link(s) to epoch ${build.newEpoch}" }
        return compactionLanded
    }

    /**
     * The Refounding's channel duty (CORD-06 §3): every held key of a live Private Channel is
     * rotated to the members still entitled to it ∩ [kept], plus ourselves, sealed under
     * [priorRoot]. Returns [entry] with each rotated channel moved to its new key (the caller adopts
     * the new root from it); a channel whose rotation didn't land keeps its old key.
     */
    private suspend fun rotatePrivateChannelsForRefounding(
        entry: ConcordCommunityListEntry,
        kept: Set<HexKey>,
        priorRoot: ByteArray,
        citation: AuthorityCitation?,
    ): ConcordCommunityListEntry {
        val session = account.concordSessions.sessionFor(entry.id) ?: return entry
        val state = session.state.value ?: return entry
        val me = account.signer.pubKey.lowercase()
        val publishTo = entry.relays.mapNotNullTo(mutableSetOf()) { RelayUrlNormalizer.normalizeOrNull(it) }
        val keptLower = kept.mapTo(HashSet()) { it.lowercase() }
        var next = entry
        for (held in entry.privateChannels) {
            val id = held.channelId.lowercase()
            if (id !in state.privateChannelIds || ConcordChannelKeyring.heldKey(entry, id) == null) continue
            val keep = ConcordPrivateChannels.keepSet(state.authority, id, me).filterTo(HashSet()) { it in keptLower || it == me }
            val newKey = ConcordChannelRekey.mintKey()
            val wraps = ConcordPrivateChannels.buildRotation(account.signer, priorRoot, held, newKey, keep, TimeUtils.now(), citation)
            val landed = wraps.all { runCatching { account.client.publishAndConfirm(it, publishTo) }.getOrDefault(false) }
            if (!landed) {
                Log.w("Concord") { "Refounding ${entry.id}: the rekey of private channel $id did not land; it keeps its key" }
                continue
            }
            next = ConcordChannelKeyring.withRotatedKey(next, id, newKey.toHexKey(), held.epoch + 1) ?: next
        }
        return next
    }

    // Keys reserved for a Refounding in flight, per community (CORD-06 §3): a retry of the same
    // rotation reuses them. Process-local — a restart mid-rotation mints afresh, which is why the
    // rekey chunks are all confirmed before anything is adopted.
    private val pendingConcordRefoundings = ConcurrentMap<String, PendingRefounding>()

    /**
     * Pages the whole Control Plane at [address] off every relay in [relays], or null when fewer
     * than a majority of them drained it (a dead relay must not block rotation forever, but too few
     * would compact a partial plane and roll the community back for everyone who follows).
     */
    private suspend fun sweepConcordControlPlane(
        address: HexKey,
        relays: Set<NormalizedRelayUrl>,
    ): List<Event>? {
        val filter = ConcordActions.planeFilter(address)
        val perRelay =
            coroutineScope {
                relays
                    .map { relay ->
                        async {
                            val events = ArrayList<Event>()
                            val result = runCatchingCancellable { concordClient.fetchAllPages(relay, listOf(filter)) { events.add(it) } }.getOrNull()
                            if (result?.end == PagedFetchResult.End.DRAINED) events else null
                        }
                    }.awaitAll()
            }
        val drained = perRelay.filterNotNull()
        if (drained.size < relays.size / 2 + 1) return null
        return drained.flatten().distinctBy { it.id }
    }

    /**
     * Caps the Refounding recipient set, keeping the members whose standing we can actually vouch
     * for when there are too many.
     *
     * `allMembers()` is the Guestbook ∪ `observedAuthors` ∪ the roster, and the first two are
     * unbounded and attacker-writable: a Guestbook Join is self-signed by any key at all, and every
     * author we decrypt is folded in by design (CORD-02 §5, "observably present"). So each throwaway
     * npub someone posts from, or simply announces, becomes one more mandatory blob in the next
     * Refounding — meaning the attack inflates the cost of its own remedy, and the remedy is the only
     * hard removal Concord has. See B4 in `docs/concord-soft-ban-audit.md`.
     *
     * The roster and the owner are kept unconditionally: they are owner-rooted, so they cannot be
     * padded from outside. The remainder fills the budget, and anything dropped is **logged rather
     * than silently truncated** — a dropped member is stranded on the dead epoch and their only way
     * back is a recovery path that needs to know it happened.
     */
    private fun boundRecipients(
        candidates: Set<HexKey>,
        authority: AuthorityResolver,
    ): List<HexKey> {
        if (candidates.size <= MAX_REFOUNDING_RECIPIENTS) return candidates.toList()

        // The roster goes in whole even if it alone exceeds the budget: it is owner-rooted, so it
        // cannot be padded from outside, and dropping an admin to make room for a stranger inverts
        // the point of the cap.
        val vouched = authority.roleHolders() + authority.staffMembers()
        val kept = LinkedHashSet<HexKey>()
        candidates.filterTo(kept) { it in vouched }
        for (candidate in candidates) {
            if (kept.size >= MAX_REFOUNDING_RECIPIENTS) break
            kept.add(candidate)
        }
        val dropped = candidates.size - kept.size
        if (dropped > 0) {
            Log.w("Concord") {
                "Refounding recipient set trimmed to ${kept.size} of ${candidates.size} " +
                    "(budget $MAX_REFOUNDING_RECIPIENTS, roster kept whole): $dropped member(s) will be " +
                    "stranded on the prior epoch"
            }
        }
        return kept.toList()
    }

    // Rotations we've already adopted ("communityId:epoch"), so a base-rekey wrap still buffered
    // in the pre-rebuild window (the session rebuild off `liveCommunities` is async) is not
    // adopted — and re-published — twice on successive revision ticks.
    private val adoptedConcordRotations = ConcurrentSet<String>()

    /**
     * Persist a rotated access root/epoch for [entry], keeping the prior root as a
     * [HeldRoot], and re-announce our Guestbook membership at the new epoch so the
     * fresh epoch's Guestbook re-seeds (a later Refounding re-keys that membership —
     * without this, cascading removals would lose everyone but the roster). No-op if
     * this exact rotation was already adopted.
     */
    private suspend fun adoptConcordRoot(
        entry: ConcordCommunityListEntry,
        newRoot: ByteArray,
        newEpoch: Long,
        newControlPk: ByteArray? = null,
        newControlRoot: ByteArray? = null,
    ): ConcordCommunityListEntry? {
        if (!adoptedConcordRotations.add("${entry.id}:$newEpoch")) return null
        // The rewrite itself — banking the leaving epoch's address for the anti-rollback floor,
        // dropping stale control material on a legacy rotation, preserving invite_ref and residue —
        // is shared with `amy` in [ConcordReceive.withAdoptedRoot]. Only the persist + publish and
        // the Guestbook re-announce below are Android's.
        val next = ConcordReceive.withAdoptedRoot(entry, newRoot, newEpoch, newControlPk, newControlRoot)
        if (!persistConcordEntry(next)) {
            // Not persisted, so not adopted: let the next drain retry it.
            adoptedConcordRotations.remove("${entry.id}:$newEpoch")
            return null
        }
        announceConcordGuestbookJoin(next, inviteCreator = null, inviteLabel = null)
        return next
    }

    /**
     * Drain any buffered inbound base-rotation rekeys (CORD-06 receive path): for
     * each joined community, look for our new root among the kind-3303 wraps seen at
     * our next base-rekey address. If a role-authorized rotator (owner or a current,
     * non-banned BAN-holder) delivered us one, adopt it. Idempotent — once adopted, the
     * session rebuilds at the new epoch and its next-rekey address moves on, so a stale
     * wrap never re-triggers. Called on every Concord revision tick.
     *
     * Authority is the roster, never key possession: any non-banned BAN-holder may
     * rotate, including for the owner. The owner deliberately does NOT refuse a root
     * authored by someone else — refusing would strand the owner alone on the dead
     * epoch whenever an admin legitimately rotates, and would diverge from Armada,
     * which forks a community across clients. Self-escalation to BAN is prevented
     * upstream by the role rank gate in AuthorityResolver.
     *
     * A rotation carries only (newRoot, newEpoch, rotator); there is no recipient list,
     * so a receiver cannot tell who was left out, and a BAN-holder can evict anyone (the
     * owner included) by omission — nothing on this receive path can prevent it.
     * [recoverStrandedConcordCommunities] detects it; re-opening the invite link rejoins.
     *
     * Also runs the same-epoch race heal (CORD-06 §3): racing rotations converge on the
     * lowest authorized root, and a sibling seen after we adopted replaces our root only
     * when strictly lower. Nothing is adopted for a dissolved community (CORD-02 §9).
     */
    internal suspend fun drainConcordRekeys() {
        if (!account.isWriteable()) return
        for (session in account.concordSessions.sessions()) {
            val entry = session.entry
            val state = session.state.value ?: continue
            // Death wins every race (CORD-02 §9): no epoch advance past a tombstone is honored.
            if (state.dissolved) continue
            val authority = state.authority
            val editions = session.controlEditions()
            // Authority is the roster plus the cited Grant, never key possession (CORD-06 §3):
            // hasPermission (not effectivePermissions, which ignores the banlist) and a `vac` our
            // fold has synced, so a just-demoted admin's rotation is not honored while we lag.
            val honored = { r: ReceivedRefounding -> ConcordReceive.isHonoredRotation(entry, editions, authority, r) }

            val wraps = session.pendingBaseRekeyWraps()
            if (wraps.isNotEmpty()) {
                // Racing authorized rotations converge on the lowest new root (CORD-06 §3).
                val received =
                    ConcordActions.openBaseRekey(
                        wraps = wraps,
                        baseRekey = session.nextBaseRekeyKey(),
                        recipientSigner = account.signer,
                        communityId = entry.id,
                        priorRoot = entry.root.hexToByteArray(),
                        rootEpoch = entry.rootEpoch,
                        accept = honored,
                    )
                if (received != null && received.newEpoch > entry.rootEpoch) {
                    val adopted = adoptConcordRoot(entry, received.newRoot, received.newEpoch, received.newControlPk, received.newControlRoot)
                    // A rotation we did not launch superseded the one we may have had in flight.
                    pendingConcordRefoundings.remove(entry.id)
                    // Move our own links onto the epoch we just adopted. Rotating is not the only way
                    // to end up on a new epoch — being re-keyed is the common one — and a link creator
                    // who is merely re-keyed would otherwise leave every link they handed out pointing
                    // at the dead root.
                    adopted?.let { next ->
                        val moved = refreshConcordInviteLinks(next)
                        if (moved > 0) Log.i("Concord") { "Rekey ${next.id}: refreshed $moved invite link(s) to epoch ${received.newEpoch}" }
                    }
                    continue
                }
            }

            // The same-epoch heal (CORD-06 §3): a racing rotation into the epoch we hold, sealed under
            // the same prior root, wins only when its root is strictly lower. Our losing root stays
            // held so the messages sent into that fork stay readable.
            val siblingKey = session.siblingBaseRekeyKey() ?: continue
            val siblingWraps = session.pendingSiblingRekeyWraps()
            if (siblingWraps.isEmpty()) continue
            val prior = ConcordRefounding.canonicalHeldRoots(entry.heldRoots).firstOrNull { it.epoch == entry.rootEpoch - 1 } ?: continue
            val sibling =
                ConcordActions.openBaseRekey(
                    wraps = siblingWraps,
                    baseRekey = siblingKey,
                    recipientSigner = account.signer,
                    communityId = entry.id,
                    priorRoot = prior.key.hexToByteArray(),
                    rootEpoch = prior.epoch,
                    accept = honored,
                ) ?: continue
            val healed = ConcordReceive.withHealedRoot(entry, sibling) ?: continue
            if (!adoptedConcordRotations.add("${healed.id}:${healed.rootEpoch}:${healed.root}")) continue
            Log.i("Concord") { "Rekey race ${entry.id}: epoch ${entry.rootEpoch} converged on the lower sibling root" }
            account.sendMyPublicAndPrivateOutbox(account.concordChannelList.follow(healed))
            announceConcordGuestbookJoin(healed, inviteCreator = null, inviteLabel = null)
            refreshConcordInviteLinks(healed)
        }
    }

    /**
     * Adopt a `control_root` delivered to us by a staff-making Grant (CORD-04 §3): the
     * promoting edition carries the secret in `control_wrap`, NIP-44-encrypted under the
     * granter↔member pairwise key, so promotion and key delivery are one signed edition
     * with nothing separate to watch an inbox for.
     *
     * Adoption is gated twice and fails closed both times. The secret is adopted only if
     * it derives to exactly the `control_pk` we already hold for the named epoch — a
     * garbage wrap is attributable griefing, nothing worse — and only from a Grant our own
     * fold honors, so a rogue cannot feed us a key by minting an edition nobody accepts.
     * The epoch check matters because compaction re-wraps a Grant head verbatim across
     * Refoundings, so a folded head can legitimately carry a wrap minted for a prior epoch.
     *
     * Idempotent: once the entry holds the secret there is nothing to adopt. Runs on the
     * revision tick, like the rekey drain.
     */
    internal suspend fun drainConcordStaffGrants() {
        if (!account.isWriteable()) return
        for (session in account.concordSessions.sessions()) {
            val entry = session.entry
            val state = session.state.value ?: continue
            // The whole decision — are we staff, does a Grant carry a wrap, does it open, name our
            // epoch, and derive to the control_pk we hold — is shared with `amy` in
            // [ConcordReceive.deliveredControlRoot]. Only the persist + publish below is Android's.
            val delivered = ConcordReceive.deliveredControlRoot(entry, session.controlEditions(), state.authority, account.signer) ?: continue

            persistConcordEntry(entry.withControlRoot(delivered))
        }
    }

    private val _strandedConcordCommunities = MutableStateFlow<Set<String>>(emptySet())

    /**
     * Communities whose own invite link resolves to a higher epoch than we hold — a Refounding left
     * us behind (see [recoverStrandedConcordCommunities]). Re-opening that link rejoins them.
     */
    val strandedConcordCommunities: StateFlow<Set<String>> = _strandedConcordCommunities.asStateFlow()

    // Last time we re-resolved each community's invite_ref, so the recovery sweep rides the
    // Concord revision tick (which fires on every structural change) without turning it into a
    // relay-fetch loop.
    private val lastConcordRecoveryCheck = ConcurrentMap<String, Long>()

    /**
     * Stranded recovery (CORD-05/06 receive path). A Refounding carries only
     * `(newRoot, newEpoch, rotator)` — **no recipient list** — so a member simply left
     * out of the rekey recipient set receives nothing and sits on the dead epoch
     * forever while everyone else moves on. This happens to any member, the owner
     * included, and [drainConcordRekeys] cannot prevent it: there is no message to
     * miss detecting.
     *
     * The signal is the invite link the membership was joined through
     * ([ConcordCommunityListEntry.inviteRef], persisted by [joinConcordViaInvite] and
     * carried through every rotation by [adoptConcordRoot]). The community keeps
     * re-minting its bundle at that same addressable coordinate, so a bundle there at
     * a **strictly higher** epoch than ours says we were left behind. Memberships with
     * no link (direct invites, legacy entries) are inert here.
     *
     * Detection ONLY ([strandedConcordCommunities]). The bundle is not proof of
     * continuity — nothing binds `community_root` to `community_id` — so adopting its
     * root here would let any link creator relocate every member who joined through
     * their link (CORD-06 §2: the base advances only by a verifiable rekey). The way
     * forward is the user explicitly re-opening the link ([joinConcordViaInvite]).
     *
     * Called on the Concord revision tick, but rate-limited per community
     * ([RECOVERY_CHECK_INTERVAL_MS]) — a tick with nothing to do costs a map lookup.
     */
    internal suspend fun recoverStrandedConcordCommunities() {
        if (!account.isWriteable()) return
        val now = TimeUtils.nowMillis()
        for (entry in account.concordChannelList.liveCommunities.value) {
            val inviteRef = entry.inviteRef ?: continue
            val last = lastConcordRecoveryCheck[entry.id]
            if (last != null && now - last < RECOVERY_CHECK_INTERVAL_MS) continue
            lastConcordRecoveryCheck[entry.id] = now

            // Fails CLOSED: no fold, no verdict — a banned member's cold-start window must not read
            // as "not banned". Retried on the next sweep once the roster is known.
            val state =
                account.concordSessions
                    .sessionFor(entry.id)
                    ?.state
                    ?.value
            if (state == null) {
                Log.i("Concord") { "Stranded check deferred for ${entry.id}: control plane not folded yet" }
                lastConcordRecoveryCheck.remove(entry.id)
                continue
            }
            // Death wins every race (CORD-02 §9): a dissolved community is never "behind".
            if (state.dissolved) continue

            val parsed = ConcordActions.parseInviteLink(inviteRef) ?: continue
            val relays =
                (
                    parsed.fragment.relays.mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) } +
                        entry.relays.mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }
                ).toSet()
            if (relays.isEmpty()) continue

            val filters = relays.associateWith { listOf(ConcordActions.bundleFilter(parsed.linkSignerPubKey)) }
            val wraps = concordClient.fetchAll(filters = filters)
            // Only a live bundle counts: an expired/revoked link is not a rotation we missed.
            val bundle = (ConcordActions.classifyInvite(wraps, parsed.linkSignerPubKey, parsed.fragment.token) as? InviteBundleStatus.Live)?.invite ?: continue

            // Detection only (CORD-06 §2): the bundle is not proof of continuity, so it never moves
            // our base. The user re-accepting the link is the way forward (joinConcordViaInvite).
            val stranded = ConcordActions.isStranded(entry, bundle, state.authority.isBanned(account.signer.pubKey))
            _strandedConcordCommunities.value = if (stranded) _strandedConcordCommunities.value + entry.id else _strandedConcordCommunities.value - entry.id
            if (stranded) Log.i("Concord") { "Stranded: ${entry.id} is at epoch ${entry.rootEpoch}, its invite link at ${bundle.rootEpoch}; re-open the link to rejoin" }
        }
    }

    /**
     * Replace the community metadata (name / icon / description / relays) with a new
     * Control-Plane edition. Honored on fold only when this account holds
     * MANAGE_METADATA (or is the owner); dropped otherwise, like every other edition.
     */
    suspend fun editConcordMetadata(
        communityId: String,
        name: String,
        description: String?,
        icon: ImagePointer?,
        banner: ImagePointer?,
        relays: List<String>,
    ): Boolean {
        val session = account.concordSessions.sessionFor(communityId) ?: return false
        if (!account.isWriteable()) return false
        val cp = controlKeysForAction(session, ConcordPermissions.MANAGE_METADATA) ?: return false
        // Start from the folded metadata so a field this form doesn't edit — the CORD-08 timer, above
        // all — is carried forward instead of reset (CORD-02 §6 round-trip). Only from a drained fold:
        // minting over a head we were never served would chain a fresh v1 (or a stale version) that
        // wipes the name, relays and timer — the owner included, who may otherwise act before the fold.
        val folded = session.foldForWrite() ?: return false
        val standing = folded.metadata ?: MetadataEntity()
        val metadata = standing.copy(name = name, icon = icon, banner = banner, description = description, relays = relays)
        // CORD-02 §6 caps are fold gates too: an edition past them would be dropped by every reader.
        if (!ConcordLimits.metadataFits(metadata)) return false
        val wrap = ConcordModeration.editMetadata(account.signer, cp, communityId.hexToByteArray(), metadata, session.controlEditions(), TimeUtils.now(), owner = session.entry.owner, floors = session.controlFloors())
        publishConcordWrap(session.entry, wrap)
        return true
    }

    /**
     * Set [communityId]'s disappearing-messages timer to [secs] seconds, or turn it off when null or
     * below 1 (CORD-08 §1): a metadata edition under MANAGE_METADATA, laid over the folded metadata so
     * nothing else changes. Then, as §4 asks, one kind-1740 timer notice goes into every channel whose
     * key this account holds (a Private Channel without one simply gets none). Returns false when
     * nothing was published (not authorized, no Control write key, or the timer is already [secs]).
     */
    suspend fun setConcordMessageExpiration(
        communityId: String,
        secs: Long?,
    ): Boolean {
        val session = account.concordSessions.sessionFor(communityId) ?: return false
        if (!account.isWriteable()) return false
        val cp = controlKeysForAction(session, ConcordPermissions.MANAGE_METADATA) ?: return false
        val timer = secs?.takeIf { it >= 1 }
        // Same rule as editConcordMetadata: never lay the timer over a head we were not served.
        val folded = session.foldForWrite() ?: return false
        val standing = folded.metadata ?: MetadataEntity()
        if (standing.messageExpirationSecs() == timer) return false
        val wrap = ConcordModeration.setMessageExpiration(account.signer, cp, communityId.hexToByteArray(), standing, timer, session.controlEditions(), TimeUtils.now(), owner = session.entry.owner, floors = session.controlFloors())
        publishConcordWrap(session.entry, wrap)
        postConcordTimerNotices(session, timer ?: 0)
        return true
    }

    /** One CORD-08 §4 timer notice per channel of [session] this account can write. */
    private suspend fun postConcordTimerNotices(
        session: ConcordCommunitySession,
        timerSecs: Long,
    ) {
        val channels =
            session.state.value
                ?.channels
                ?.keys ?: return
        val now = TimeUtils.now()
        for (channelIdHex in channels) {
            val plane = session.currentChannelPlane(channelIdHex) ?: continue
            publishConcordWrap(session.entry, ConcordActions.buildChannelTimerNotice(account.signer, plane.key, channelIdHex, plane.epoch, timerSecs, now))
        }
    }

    /**
     * Dissolve [communityId] for good (CORD-02 §9): publish the owner-signed, `eid`-bound tombstone
     * at the community's dissolved address. Owner-only — every verifier ignores anyone else's — and
     * irreversible: there is no un-dissolve. Returns false when this account is not the owner or
     * cannot sign.
     */
    suspend fun dissolveConcordCommunity(communityId: String): Boolean {
        val session = account.concordSessions.sessionFor(communityId) ?: return false
        if (!account.isWriteable()) return false
        if (!session.entry.owner.equals(account.signer.pubKey, ignoreCase = true)) return false
        val wrap = ConcordDissolution.build(account.signer, communityId)
        publishConcordWrap(session.entry, wrap)
        return true
    }

    /**
     * Create a new public text channel in [communityId] (CORD-03/04 channel edition). Honored at fold
     * only when this account holds MANAGE_CHANNELS (or is the owner); the button should be gated on
     * the same predicate. The channel id is a fresh random 32-byte entity id.
     */
    suspend fun createConcordChannel(
        communityId: String,
        name: String,
        private: Boolean = false,
        accessRoleName: String? = null,
    ): Boolean {
        val session = account.concordSessions.sessionFor(communityId) ?: return false
        if (!account.isWriteable()) return false
        val cp = controlKeysForAction(session, ConcordPermissions.MANAGE_CHANNELS) ?: return false
        if (private) return createPrivateConcordChannel(session, cp, communityId, name, accessRoleName)
        val channelId = RandomInstance.bytes(32)
        val channel = ChannelEntity(name = name.trim())
        // Readers drop an empty or over-64-byte name (CORD-03 §2); never mint one.
        if (!channel.hasValidName()) return false
        val wrap = ConcordModeration.defineChannel(account.signer, cp, communityId.hexToByteArray(), channelId, channel, session.controlEditions(), TimeUtils.now(), owner = session.entry.owner)
        publishConcordWrap(session.entry, wrap)
        return true
    }

    /** Rename an existing channel (chains the next channel edition onto its head). MANAGE_CHANNELS only. */
    suspend fun renameConcordChannel(
        communityId: String,
        channelIdHex: String,
        name: String,
    ): Boolean {
        val session = account.concordSessions.sessionFor(communityId) ?: return false
        if (!account.isWriteable()) return false
        val cp = controlKeysForAction(session, ConcordPermissions.MANAGE_CHANNELS) ?: return false
        // Carry the standing definition forward and change only the name. A ChannelEntity built from
        // scratch defaults `private` to false, so renaming a private channel used to publish an
        // edition declaring it PUBLIC. Fields we don't model ride through ConcordModeration.
        val standing =
            session.state.value
                ?.channels
                ?.get(channelIdHex)
                ?.definition
        val channel = (standing ?: ChannelEntity()).copy(name = name.trim())
        if (!channel.hasValidName()) return false
        val wrap = ConcordModeration.defineChannel(account.signer, cp, communityId.hexToByteArray(), channelIdHex.hexToByteArray(), channel, session.controlEditions(), TimeUtils.now(), owner = session.entry.owner)
        publishConcordWrap(session.entry, wrap)
        return true
    }

    /** Delete (tombstone) a channel — terminal; its id is never reused. MANAGE_CHANNELS only. */
    suspend fun deleteConcordChannel(
        communityId: String,
        channelIdHex: String,
        name: String,
    ): Boolean {
        val session = account.concordSessions.sessionFor(communityId) ?: return false
        if (!account.isWriteable()) return false
        val cp = controlKeysForAction(session, ConcordPermissions.MANAGE_CHANNELS) ?: return false
        // Same as rename: preserve the standing flags so a tombstone does not also silently
        // reclassify the channel it retires.
        val standing =
            session.state.value
                ?.channels
                ?.get(channelIdHex)
                ?.definition
        val channel = (standing ?: ChannelEntity()).copy(name = name.trim(), deleted = true)
        if (!channel.hasValidName()) return false
        val wrap = ConcordModeration.defineChannel(account.signer, cp, communityId.hexToByteArray(), channelIdHex.hexToByteArray(), channel, session.controlEditions(), TimeUtils.now(), owner = session.entry.owner)
        publishConcordWrap(session.entry, wrap)
        return true
    }

    // ── Private Channels (CORD-03 §1-2, CORD-04 §2, CORD-05 §6, CORD-06 §1-3) ──────────────────
    // A Private Channel reads on its own independent key. Its access list is the Roles scoped to it:
    // a Grant that opens one vends the key by Direct Invite, a Grant (or ban) that closes one rotates
    // it to the members still entitled. The protocol decisions live in ConcordPrivateChannels /
    // ConcordChannelRekey (shared with `amy`); only the network and the List write are here.

    /**
     * A new Private Channel (CORD-03 §2): an independent key at channel epoch 0 stored in the List
     * FIRST (a lost List write would orphan the only copy), then its access Role and the channel
     * edition. Nobody holds the Role yet; granting it vends the key ([grantConcordRole]).
     */
    private suspend fun createPrivateConcordChannel(
        session: ConcordCommunitySession,
        cp: ControlPlaneKeys,
        communityId: String,
        name: String,
        accessRoleName: String?,
    ): Boolean {
        val build =
            ConcordPrivateChannels.create(
                actor = account.signer,
                cp = cp,
                communityId = communityId.hexToByteArray(),
                name = name,
                accessRoleName = accessRoleName,
                current = session.controlEditions(),
                authority = session.state.value?.authority,
                owner = session.entry.owner,
                createdAt = TimeUtils.now(),
            ) ?: return false
        if (!updateConcordEntry(communityId) { cur -> ConcordChannelKeyring.withChannelKey(cur, build.key) }) return false
        build.wraps.forEach { publishConcordWrap(session.entry, it) }
        return true
    }

    /**
     * Converts the Public channel [channelIdHex] to Private (CORD-03 §2): a fresh key at the NEXT
     * channel epoch — floored at the highest channel rotation seen on the wire, since a privatiser
     * may never have held an earlier generation and a reused epoch is silent and unrecoverable — plus
     * a new access Role, then the flag. Protects the future only. MANAGE_CHANNELS.
     */
    suspend fun privatizeConcordChannel(
        communityId: String,
        channelIdHex: HexKey,
        accessRoleName: String? = null,
    ): Boolean {
        val session = account.concordSessions.sessionFor(communityId) ?: return false
        if (!account.isWriteable()) return false
        val cp = controlKeysForAction(session, ConcordPermissions.MANAGE_CHANNELS) ?: return false
        val state = session.state.value ?: return false
        val standing = state.channels[channelIdHex]?.definition ?: return false
        if (standing.private) return false
        val floor = observedChannelEpochFloor(session.entry, channelIdHex) ?: return false
        val build =
            ConcordPrivateChannels.privatize(
                actor = account.signer,
                cp = cp,
                entry = session.entry,
                channelIdHex = channelIdHex,
                standing = standing,
                accessRoleName = accessRoleName,
                current = session.controlEditions(),
                authority = state.authority,
                createdAt = TimeUtils.now(),
                observedFloor = floor,
            ) ?: return false
        if (!updateConcordEntry(communityId) { cur -> ConcordChannelKeyring.withChannelKey(cur, build.key) }) return false
        build.wraps.forEach { publishConcordWrap(session.entry, it) }
        return true
    }

    /**
     * The highest channel epoch a rotation of [channelIdHex] was ever published at, read off the
     * rekey addresses every held root derives (CORD-06 §2 addresses need no channel key), or null
     * when the read is inconclusive — a rotation at the window's top may have more above it, and
     * minting on a guess could reuse an epoch (Armada `channelEpochFloor`). 0 when none is seen or no
     * relay answers.
     */
    private suspend fun observedChannelEpochFloor(
        entry: ConcordCommunityListEntry,
        channelIdHex: HexKey,
    ): Long? {
        val relays = entry.relays.mapNotNullTo(mutableSetOf()) { RelayUrlNormalizer.normalizeOrNull(it) }
        if (relays.isEmpty()) return 0
        val channelId = channelIdHex.hexToByteArray()
        val window = HashMap<HexKey, Long>()
        for (root in (listOf(entry.root) + entry.heldRoots.map { it.key }).distinct()) {
            for (epoch in 1L..MAX_PROBED_CHANNEL_EPOCH) window[ConcordChannelRekey.address(root.hexToByteArray(), channelId, epoch).publicKeyHex] = epoch
        }
        val seen = runCatching { concordClient.fetchAll(filters = relays.associateWith { listOf(ConcordActions.planeFilterFor(window.keys.toList())) }) }.getOrDefault(emptyList())
        val highest = seen.mapNotNull { window[it.pubKey] }.maxOrNull() ?: 0
        return if (highest >= MAX_PROBED_CHANNEL_EPOCH) null else highest
    }

    /**
     * Converts the Private channel [channelIdHex] back to Public (CORD-03 §2): the flag only. The
     * held key stays, so its holders keep reading the private era. MANAGE_CHANNELS.
     */
    suspend fun publicizeConcordChannel(
        communityId: String,
        channelIdHex: HexKey,
    ): Boolean {
        val session = account.concordSessions.sessionFor(communityId) ?: return false
        if (!account.isWriteable()) return false
        val cp = controlKeysForAction(session, ConcordPermissions.MANAGE_CHANNELS) ?: return false
        val standing =
            session.state.value
                ?.channels
                ?.get(channelIdHex)
                ?.definition ?: return false
        val wrap = ConcordPrivateChannels.publicize(account.signer, cp, communityId.hexToByteArray(), channelIdHex, standing, session.controlEditions(), session.entry.owner, TimeUtils.now()) ?: return false
        publishConcordWrap(session.entry, wrap)
        return true
    }

    // Channel keys reserved for a rotation in flight, keyed by (community, channel, new epoch,
    // prevcommit): a retry re-delivers the SAME key rather than minting a sibling that would split
    // the members across two keys at one epoch (Armada `mintOrReuseRotationKey`). Process-local.
    private val pendingConcordChannelRotations = ConcurrentMap<String, ByteArray>()

    /**
     * Rotates Private Channel [channelIdHex] (a single-channel Rekey, CORD-06 §1-2) to exactly the
     * members entitled to it today plus ourselves, cutting everyone else. [removed] names who the
     * rotation cuts, for the authority check — the Rotator must hold MANAGE_CHANNELS and strictly
     * outrank each of them; null takes every known member who is not kept. Every chunk must land on
     * a relay before we adopt the new key. Returns whether the rotation was published and adopted.
     */
    suspend fun rekeyConcordChannel(
        communityId: String,
        channelIdHex: HexKey,
        removed: Set<HexKey>? = null,
    ): Boolean {
        val session = account.concordSessions.sessionFor(communityId) ?: return false
        if (!account.isWriteable()) return false
        val state = session.state.value ?: return false
        if (state.dissolved) return false
        val entry = session.entry
        val me = account.signer.pubKey
        val held = ConcordChannelKeyring.heldKey(entry, channelIdHex) ?: return false
        val authority = state.authority
        val keep = ConcordPrivateChannels.keepSet(authority, channelIdHex, me)
        val cut = removed ?: (session.allMembers() - keep)
        if (!ConcordPrivateChannels.canRotate(authority, me, cut)) {
            Log.w("Concord") { "Refusing to rotate $channelIdHex in $communityId: not MANAGE_CHANNELS, or does not outrank a cut member (CORD-06 §3)" }
            return false
        }
        val editions = session.controlEditions()
        val citation = ConcordReceive.rotationCitation(entry, editions, me)
        if (citation == null && !authority.isOwner(me)) return false
        val publishTo = entry.relays.mapNotNullTo(mutableSetOf()) { RelayUrlNormalizer.normalizeOrNull(it) }
        if (publishTo.isEmpty()) return false

        val newEpoch = held.epoch + 1
        val reservation = "${entry.id}:${held.channelId.lowercase()}:$newEpoch:${ConcordChannelRekey.prevCommit(held.epoch, held.key.hexToByteArray())}"
        val newKey = pendingConcordChannelRotations.getOrPut(reservation) { ConcordChannelRekey.mintKey() }
        val wraps = ConcordPrivateChannels.buildRotation(account.signer, entry.root.hexToByteArray(), held, newKey, keep, TimeUtils.now(), citation)
        for (wrap in wraps) {
            if (!runCatching { account.client.publishAndConfirm(wrap, publishTo) }.getOrDefault(false)) {
                Log.w("Concord") { "Channel rekey of $channelIdHex aborted: a chunk was not accepted by any relay; retrying reuses the same key" }
                return false
            }
        }
        // Adopt at once: the rotator must never keep writing under the severed key.
        val adopted =
            updateConcordEntry(entry.id) { cur ->
                if (ConcordChannelKeyring.heldKey(cur, channelIdHex)?.epoch != held.epoch) null else ConcordChannelKeyring.withRotatedKey(cur, channelIdHex, newKey.toHexKey(), newEpoch)
            }
        if (adopted) pendingConcordChannelRotations.remove(reservation)
        return adopted
    }

    /**
     * Follows a roster change with the keys it implies (Armada `handleToggleRole`): every Private
     * Channel whose entitled set moved between [before] and the current fold ([ConcordInviteVend.accessChanges])
     * — a member who gained one is handed its key by Direct Invite (only the channels gained), and
     * one who lost one is cut by rotating it. Only keys we hold can move; a channel we can't vend or
     * rotate is logged, since a revoke that cuts nobody is the failure to hear about.
     */
    private suspend fun reconcileConcordChannelAccess(
        communityId: String,
        before: AuthorityResolver?,
    ) {
        val session = account.concordSessions.sessionFor(communityId) ?: return
        val state = session.state.value ?: return
        if (before == null || state.dissolved) return
        val me = account.signer.pubKey.lowercase()
        val changes = ConcordInviteVend.accessChanges(before, state.authority, state.privateChannelIds)
        if (changes.isEmpty()) return

        val vend = HashMap<HexKey, MutableSet<HexKey>>()
        for (change in changes) {
            val held = ConcordChannelKeyring.heldKey(session.entry, change.channelIdHex)
            if (held == null) {
                Log.w("Concord") { "Access to ${change.channelIdHex} changed, but we hold no key to vend or rotate it" }
                continue
            }
            for (member in change.gained - me) vend.getOrPut(member) { HashSet() }.add(change.channelIdHex)
            val cut = change.lost - me
            if (cut.isNotEmpty() && !rekeyConcordChannel(communityId, change.channelIdHex, cut)) {
                Log.w("Concord") { "Could not rotate ${change.channelIdHex}: ${cut.size} member(s) may keep reading it until someone who can rotates it" }
            }
        }
        for ((member, channels) in vend) {
            val sent = sendConcordDirectInvite(communityId, member, onlyChannelIds = channels)
            if (sent != ConcordDirectInviteSendResult.SENT) Log.w("Concord") { "Could not deliver ${channels.size} channel key(s) to $member: $sent" }
        }
    }

    /**
     * Drains the buffered channel rekeys of every joined community (CORD-06 §2 receive path): for
     * each held Private Channel, a complete, honored rotation carrying our blob off the key we hold
     * moves the key forward; a complete one from a Rotator who outranks us that omits us drops it and
     * records the cut. Runs on the revision tick; idempotent once applied (the held epoch moves on).
     */
    internal suspend fun drainConcordChannelRekeys() {
        if (!account.isWriteable()) return
        for (session in account.concordSessions.sessions()) {
            val state = session.state.value ?: continue
            // Death wins every race (CORD-02 §9).
            if (state.dissolved) continue
            val wraps = session.pendingChannelRekeyWraps()
            if (wraps.isEmpty()) continue
            val entry = session.entry
            val outcomes = ConcordPrivateChannels.receive(entry, wraps, session.controlEditions(), state.authority, account.signer)
            if (outcomes.isEmpty()) continue
            val fromEpochs = entry.privateChannels.associate { it.channelId.lowercase() to it.epoch }
            if (updateConcordEntry(entry.id) { cur -> ConcordPrivateChannels.applyOutcome(cur, outcomes, fromEpochs) }) {
                for ((id, outcome) in outcomes) {
                    when (outcome) {
                        is ChannelRekeyOutcome.Adopted -> Log.i("Concord") { "Channel rekey ${entry.id}/$id: adopted epoch ${outcome.epoch}" }
                        is ChannelRekeyOutcome.Removed -> Log.i("Concord") { "Channel rekey ${entry.id}/$id: cut at epoch ${outcome.epoch}" }
                        ChannelRekeyOutcome.None -> Unit
                    }
                }
            }
        }
    }

    /**
     * Read-only preview of an invite link: parse it, fetch the kind-33301 bundle from
     * the link's relays (+ our outbox), and unlock it with the fragment token — WITHOUT
     * joining. Returns the [CommunityInvite] (name, relays, community coordinates) so a
     * card can show what the link opens, or null if the link is invalid/unreadable.
     */
    suspend fun peekConcordInvite(url: String): CommunityInvite? {
        val parsed = ConcordActions.parseInviteLink(url) ?: return null
        val relays =
            (parsed.fragment.relays.mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) } + account.outboxRelays.flow.value).toSet()
        if (relays.isEmpty()) return null
        val filters = relays.associateWith { listOf(ConcordActions.bundleFilter(parsed.linkSignerPubKey)) }
        val wraps = concordClient.fetchAll(filters = filters)
        // Resolved like a join (newest per coordinate, signer-verified): a revoked link previews as
        // nothing, never as the stale bundle a relay still serves. An expired one still renders.
        return when (val status = ConcordActions.classifyInvite(wraps, parsed.linkSignerPubKey, parsed.fragment.token)) {
            is InviteBundleStatus.Live -> status.invite
            is InviteBundleStatus.Expired -> status.invite
            else -> null
        }
    }

    /**
     * Bootstrap the Concord hub from the network: fetch this account's Community List
     * fragments (kind 33302, CORD-02 §8) and the retired kind-13302 list, and fold them into
     * [LocalCache], so communities we joined on another Concord client with this key surface here.
     *
     * We query a wide relay set because different Concord clients publish this
     * private list to different places: the reference clients (Armada/Vector) push
     * it to the Concord **stock relays** (e.g. relay.ditto.pub), while a user may
     * also have copied it onto their **own** outbox/read relays.
     *
     * Read-only except for one migration: when the relays hold no fragment at all but a 13302
     * list exists, its memberships are written out as fragments. Folding an older copy of
     * either is a no-op, so this is safe to call on every hub open.
     *
     * [extraRelays] are additional relays to query — the bootstrap relays saved on the
     * bottom-bar tabs of pinned communities. A community's private list frequently lives
     * only on the community's own relays (never the user's outbox), so a community pinned
     * to the bottom bar would otherwise never surface when opened cold.
     */
    suspend fun importConcordCommunities(extraRelays: Set<NormalizedRelayUrl> = emptySet()) {
        val stock = InviteRelayDictionary.STOCK.mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }
        val relays = (stock + account.mineRelays.flow.value + account.outboxRelays.flow.value + extraRelays).toSet()
        if (relays.isEmpty()) return
        // The fragmented List (33302) plus the retired single event (13302), read once more as a
        // rescue source so a membership only it carries is migrated by the next write.
        val filter = Filter(kinds = listOf(ConcordCommunityListFragmentEvent.KIND, ConcordCommunityListEvent.KIND), authors = listOf(account.signer.pubKey))
        // Stock relays like relay.ditto.pub can be slow (~10–20s to first response), so give
        // the fetch a generous window to drain every relay before we pick the newest copy.
        val events = concordClient.fetchAll(filters = relays.associateWith { listOf(filter) }, idleTimeoutMs = 30_000L)
        val fragments = events.filterIsInstance<ConcordCommunityListFragmentEvent>()
        val legacy = events.filterIsInstance<ConcordCommunityListEvent>().maxByOrNull { it.createdAt }
        Log.d("Concord") {
            "importConcordCommunities: queried ${relays.size} relays, fetched ${fragments.size} 33302 fragment(s) " +
                "and ${if (legacy == null) "no" else "a"} retired 13302 list"
        }
        fragments.forEach { account.cache.justConsumeMyOwnEvent(it) }
        legacy?.let { account.cache.justConsumeMyOwnEvent(it) }
        // The relays have now been asked: an empty fragment set from here on means "no List yet".
        account.concordChannelList.markRelaysConfirmed()
        // Seed the fragments from the retired event only once the relays confirmed none exist: a
        // seeding write made while fragments are merely unloaded would replace them (CORD-02 §8).
        if (fragments.isEmpty() && legacy != null) {
            try {
                val seeded = account.concordChannelList.republish()
                if (seeded.isNotEmpty()) {
                    Log.d("Concord") { "importConcordCommunities: migrated the 13302 list into ${seeded.size} fragment(s)" }
                    account.sendMyPublicAndPrivateOutbox(seeded)
                }
            } catch (e: ConcordListIncompleteException) {
                Log.w("Concord") { "importConcordCommunities: 13302 migration deferred: ${e.message}" }
            } catch (e: ConcordListTooLargeException) {
                Log.w("Concord") { "importConcordCommunities: 13302 migration deferred: ${e.message}" }
            }
        }
    }

    /**
     * One-shot warm of every channel of [entries] so a community's channel list and the Messages inbox
     * fill in without the user opening each channel one by one. Per channel, a channel read before is
     * caught up from its last-read time (accurate unread badge + the missed messages ready when it
     * opens) while a channel never read pulls only its single newest wrap for a preview — see
     * [ConcordSubscriptionPlanner.channelPreviewFilters].
     *
     * This is deliberately **not** a live subscription: every wrap the drain pulls flows through the
     * global cache connector (`CacheClientConnector` → `LocalCache.justConsume` → `concordSessions.ingest`),
     * so it lands in the channel's message store the previews/unread counts read — and the always-on
     * plane subscription ([RelaySubscriptionsCoordinator.concordChannels]) keeps them fresh afterward.
     * So this only needs to run when a community's channels first fold (the account preload) or its
     * screen is opened. One drain per call: all [entries]' per-channel filters are grouped by relay.
     */
    suspend fun warmConcordChannelPreviews(entries: List<ConcordCommunityListEntry>) {
        val filters =
            entries.flatMap { entry ->
                val state =
                    account.concordSessions
                        .sessionFor(entry.id)
                        ?.state
                        ?.value ?: return@flatMap emptyList()
                ConcordSubscriptionPlanner.channelPreviewFilters(
                    entry,
                    state,
                    lastReadFor = { channelIdHex ->
                        account.loadLastRead(concordChannelLastReadRoute(entry.id, channelIdHex))
                    },
                    accountPubKey = account.userProfile().pubkeyHex,
                )
            }
        if (filters.isEmpty()) return
        val byRelay = filters.groupBy { it.relay }.mapValues { (_, group) -> group.map { it.filter } }
        concordClient.fetchAll(filters = byRelay, idleTimeoutMs = 20_000L)
    }

    /**
     * COMPLETE-mode Control-Plane sync — Armada's plane-sweep discipline for the one plane that must
     * never fold on a truncated edition set.
     *
     * The Control Plane defines the channel list, the roster and the banlist, so a *partial* fold
     * silently drops channels or mis-renders membership. Two ways that happens, both closed here:
     *  - **Forward-cursor gap:** the live plane subscription advances a `since` cursor, so an edition
     *    with a `created_at` below the high-water mark that we never actually ingested — an unban
     *    published while we were offline, a CORD-06 compaction re-wrap under a newly-held epoch — is
     *    never asked for again and stays invisible. This sweep uses **no `since`**: it re-fetches the
     *    whole plane every run.
     *  - **Per-filter cap:** a relay caps a REQ's result (~100/filter on relay.dreamith.to), which can
     *    crop a busy Control Plane. This **pages past the cap** ([fetchAllPages] walks `until`
     *    cursors until a plane is drained), so the fold sees every edition regardless of the cap.
     *
     * Current + every held-prior epoch's Control Plane is swept (the anti-rollback floor folds from the
     * priors). Wraps ingest through the global cache connector → [concordSessions] like every other
     * Concord drain; AUTH is the shared stream-key handler. Merging communities that share a relay into
     * one filter is safe here precisely because we page — the cap no longer truncates. The live control
     * subscription still carries brand-new editions in real time; this is the periodic completeness pass.
     */
    suspend fun syncConcordControlPlanes(entries: List<ConcordCommunityListEntry>) {
        if (entries.isEmpty()) return
        val authorsByRelay = HashMap<NormalizedRelayUrl, MutableSet<String>>()
        for (entry in entries) {
            for (sub in ConcordSubscriptionPlanner.controlPlaneSubs(listOf(entry))) {
                for (relay in sub.relays) authorsByRelay.getOrPut(relay) { HashSet() }.add(sub.pubKeyHex)
            }
        }
        if (authorsByRelay.isEmpty()) return
        // No `since`, no `limit` → fetchAllPages treats each filter as unbounded and pages until a
        // plane is fully drained (empty page), so the whole Control Plane lands regardless of the cap.
        // Paged per relay (8 at a time) rather than through the pool helper, because the drained flag
        // below needs each relay's ending: only DRAINED proves the relay had nothing more.
        val gate = Semaphore(8)
        val perRelay =
            coroutineScope {
                authorsByRelay
                    .map { (relay, authors) ->
                        async {
                            gate.withPermit {
                                val wraps = ArrayList<Event>()
                                val end =
                                    try {
                                        concordClient.fetchAllPages(relay, listOf(ConcordActions.planeFilterFor(authors.toList()))) { wraps.add(it) }.end
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        Log.w("Concord", "Control Plane sweep failed on $relay", e)
                                        null
                                    }
                                Triple(authors, end == PagedFetchResult.End.DRAINED, wraps)
                            }
                        }
                    }.awaitAll()
            }
        // Fold the swept wraps in before flagging anything drained: the global cache connector also
        // ingests them, but asynchronously, and the flag must never run ahead of the fold (the session
        // dedups by wrap id, so the second delivery is a no-op).
        var swept = 0
        for ((_, _, wraps) in perRelay) {
            for (wrap in wraps) {
                account.concordSessions.ingest(wrap)
                swept++
            }
        }
        val drainedAddresses = perRelay.filter { it.second }.flatMapTo(HashSet()) { it.first }
        // One relay drained whole is enough for everyday writes (a dead relay must not freeze pins and
        // metadata forever); the Refounding compaction keeps its own majority rule.
        for (entry in entries) {
            val session = account.concordSessions.sessionFor(entry.id) ?: continue
            if (session.controlPlaneAddress in drainedAddresses) session.markControlDrained()
        }
        Log.d("Concord") { "syncConcordControlPlanes: paged ${authorsByRelay.size} relay(s), swept $swept control wrap(s), ${drainedAddresses.size} plane(s) drained" }
        pruneExpiredConcordRegistries(entries)
    }

    // Communities (at an epoch) whose registry was already checked for elapsed links this process.
    private val registryPruneChecked = ConcurrentSet<String>()

    /**
     * CORD-05 §5: once a community's Control Plane has drained, republish this account's Invite
     * Registry there pruned when it still lists a link the Invite List says has expired (or no longer
     * holds). Otherwise an elapsed link — which nothing else ever retires — keeps the community Public
     * forever, and a Private ban would never Refound. Once per community and epoch per process; the
     * Invite List is read only when some drained community actually has a registry of ours.
     */
    private suspend fun pruneExpiredConcordRegistries(entries: List<ConcordCommunityListEntry>) {
        if (!account.isWriteable()) return
        val me = account.signer.pubKey
        val due =
            entries.filter { entry ->
                val state = account.concordSessions.sessionFor(entry.id)?.foldForWrite() ?: return@filter false
                !state.dissolved && state.registryOf(me).isNotEmpty() && registryPruneChecked.add("${entry.id}@${entry.rootEpoch}")
            }
        if (due.isEmpty()) return
        val list = readConcordInviteList() ?: return
        for (entry in due) {
            // Publishes only when the pruned list differs from the honored one.
            if (publishConcordInviteRegistry(entry, list)) Log.i("Concord") { "Pruned elapsed invite links from this account's registry in ${entry.id}" }
        }
    }
}
