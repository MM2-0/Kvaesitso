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
package com.vitorpamplona.amethyst.commons.model.cordn

import com.vitorpamplona.amethyst.commons.cordn.CordnMigrationStores
import com.vitorpamplona.amethyst.commons.cordn.CordnStorageLayout
import com.vitorpamplona.amethyst.commons.cordn.FileBackedCordnScopeFactory
import com.vitorpamplona.amethyst.commons.cordn.FileCordnCoordinatorStore
import com.vitorpamplona.amethyst.commons.cordn.FileCordnGroupStore
import com.vitorpamplona.amethyst.commons.cordn.FileCordnHandoffStore
import com.vitorpamplona.amethyst.commons.cordn.FileCordnKeyPackageStore
import com.vitorpamplona.amethyst.commons.cordn.coordinatorRelayPool
import com.vitorpamplona.amethyst.commons.model.cordnGroups.CordnGroupList
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.taggedAs
import com.vitorpamplona.amethyst.commons.util.deleteRecursivelyQuietly
import com.vitorpamplona.amethyst.commons.util.platformFileSystem
import com.vitorpamplona.cordn.appMultiDevice.CordnHandoffCode
import com.vitorpamplona.cordn.appMultiDevice.CordnMigration
import com.vitorpamplona.cordn.appMultiDevice.CordnMigrationSnapshot
import com.vitorpamplona.cordn.client.CoordinatorConfig
import com.vitorpamplona.cordn.client.CoordinatorHealth
import com.vitorpamplona.cordn.client.CordnBackup
import com.vitorpamplona.cordn.client.CordnBlobCipher
import com.vitorpamplona.cordn.client.CordnCoordinatorLinkFactory
import com.vitorpamplona.cordn.client.CordnCoordinatorRegistry
import com.vitorpamplona.cordn.client.CordnCoverage
import com.vitorpamplona.cordn.client.CordnGroupManager
import com.vitorpamplona.cordn.client.CordnHandoffState
import com.vitorpamplona.cordn.client.CordnInvitation
import com.vitorpamplona.cordn.client.CordnInvitations
import com.vitorpamplona.cordn.client.CordnInviteOutcome
import com.vitorpamplona.cordn.client.CordnKeyPackageRow
import com.vitorpamplona.cordn.client.CordnLinks
import com.vitorpamplona.cordn.client.CordnLocalState
import com.vitorpamplona.cordn.client.CordnRoomState
import com.vitorpamplona.cordn.client.CordnSession
import com.vitorpamplona.cordn.client.CordnSyncLoop
import com.vitorpamplona.cordn.client.CordnSyncSource
import com.vitorpamplona.cordn.client.rememberOpen
import com.vitorpamplona.cordn.spec00Coordinator.CoordinatorServerInfo
import com.vitorpamplona.cordn.spec00Coordinator.CordnCoordinatorDiscovery
import com.vitorpamplona.cordn.spec00Coordinator.JoinRequest
import com.vitorpamplona.cordn.spec01GroupMetadata.CordnGroupMetadata
import com.vitorpamplona.cordn.spec02Envelopes.CordnDeliveredMessage
import com.vitorpamplona.quartz.contextvm.core.CvmKinds
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAll
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.runCatchingCancellable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.Path
import kotlin.coroutines.cancellation.CancellationException

/**
 * One account's cordn feature, assembled.
 *
 * This is the wiring Stage A left open on purpose: every piece below it is
 * shared KMP code that knows nothing about Amethyst, and this class supplies
 * the two things only the running app has — a relay pool and the account's
 * signer.
 *
 * ## The ephemeral signer is created here, once, and never persisted
 *
 * `spec/00.md` §8 splits the identity a coordinator sees: the account key
 * signs what must be attributable (publishing a KeyPackage, posting to a
 * group), and a throwaway key signs everything else, so the coordinator cannot
 * link a session's reads to an account. Which key signs which call is fixed by
 * `CoordinatorMethod` and not a choice made here; what IS decided here is that
 * the throwaway key lives as long as this runtime and no longer. Persisting it
 * would quietly undo the split — a "session" key reused across launches is
 * just a second account key with worse ergonomics.
 */
class CordnRuntime(
    private val accountSigner: NostrSigner,
    private val client: INostrClient,
    private val filesDir: Path,
    private val scope: CoroutineScope,
    private val cipher: CordnBlobCipher,
    /**
     * How a coordinator connection is opened. The default is the real one.
     *
     * A seam for the same reason [cipher] is one: the production path needs a
     * relay, a transport and a live coordinator, so without it nothing in this
     * class — the KeyPackage pool rule, purge, restore — could be tested at
     * all. The ephemeral signer stays inside the default, because §8's
     * identity split is not a thing a caller should be able to widen.
     */
    private val links: CordnCoordinatorLinkFactory = CordnLinks.over(accountSigner) { relays -> coordinatorRelayPool(client, relays) },
) {
    private val coordinatorStore =
        FileCordnCoordinatorStore(
            CordnStorageLayout.accountDirectoryFor(filesDir, accountSigner.pubKey),
            cipher,
        )

    /**
     * Whether this device has handed its groups to another one.
     *
     * Read before the first sync loop starts (see [restore]); checked on every
     * path that would advance an epoch, because two devices committing from one
     * leaf fork the ratchet tree irrecoverably.
     */
    val handoff =
        CordnHandoffState(
            FileCordnHandoffStore(CordnStorageLayout.accountDirectoryFor(filesDir, accountSigner.pubKey)),
        )

    /**
     * See the class KDoc: per-runtime, never written to disk. It holds [handoff], so a device that
     * has handed its groups away refuses to open or reuse a session (a label change aside).
     */
    private val registry =
        CordnCoordinatorRegistry(
            accountPubKey = accountSigner.pubKey,
            scopes = FileBackedCordnScopeFactory(filesDir, cipher, links),
            handoff = handoff,
        )

    /** Every cordn room this account is in, for the inbox and the screens. */
    val groups = CordnGroupList(accountSigner.pubKey)

    /**
     * A running sync loop, the manager it was built over, and its log watcher.
     *
     * The manager is the part that matters. [CordnCoordinatorRegistry] hands
     * back a **new** session, with a new manager over a freshly opened
     * transport, whenever a coordinator's relays are corrected -- the transport
     * is bound to them, so it cannot be carried over. [CordnSyncLoop] captures
     * its source for the life of the loop, so keeping the old loop across that
     * swap left it polling a transport that had just been closed: the
     * coordinator retried forever, its rooms never updated again, and the only
     * way out was restarting the app.
     */
    private class SyncLoopHandle(
        val loop: CordnSyncLoop,
        val source: CordnSyncSource,
        val watcher: Job,
        /**
         * The one-shot profile fetch. Held so it can be cancelled with the rest:
         * it is launched into the runtime scope, and an unheld launch outlives a
         * logout or a forget for as long as its relays take to answer.
         */
        val prefetch: Job,
    )

    private val loops = mutableMapOf<HexKey, SyncLoopHandle>()
    private val lock = Mutex()

    val coordinators = registry.coordinators

    /**
     * The session for [config], opening and starting it if it is new.
     *
     * Every epoch-advancing path goes through here — creating a group, joining
     * one, accepting a Welcome, sending — so this is where a handed-off device
     * is stopped: [registry] holds [handoff] and refuses. Reads of state already
     * in memory are left alone: showing a user the conversations they had is
     * harmless, and refusing it would make a failed migration look like data loss.
     */
    suspend fun session(config: CoordinatorConfig): CordnSession {
        val session = registry.session(config)
        lock.withLock {
            // Rebuilt, not only created: an existing loop whose source is not
            // this session's manager is one the registry has already orphaned by
            // reopening the transport under it. Stopped before the replacement
            // starts, because two loops on one coordinator would file every
            // delivery twice.
            val current = loops[config.pubKey]
            if (current == null || current.source !== session.manager) {
                current?.let {
                    it.watcher.cancel()
                    it.loop.stop()
                }

                val loop =
                    CordnSyncLoop(
                        source = session.manager,
                        onDelivery = { file(config.pubKey, it) },
                    )
                loop.start(scope)

                // A coordinator is a Nostr identity, and CEP-23/CEP-17 say it
                // may publish a kind 0 and a kind 10002 like anybody else. Its
                // own relays are where those live, so they are fetched here,
                // from the relays this account already talks to for cordn.
                //
                // All three are ordinary public events about this pubkey, and now
                // all three are typed, so the global cache connector files them and
                // the cache does the keeping: newest-wins per (kind, pubkey) for the
                // replaceable ones, and verification before anything trusts them.
                // Nothing is parsed or stored by hand here any more. The fetch
                // remains only to aim the request at the coordinator's OWN relays,
                // which is where its announcement lives and is the one thing the
                // generic data sources cannot know until its relay list is cached.
                //
                // Not a nicety -- it is what keeps the *outbox discovery* for
                // this pubkey off this account's home relays. Rendering a
                // coordinator with UserPicture/observeUserNameByHex puts it in
                // LocalCache as a User, and UserOutboxFinderSubAssembler then
                // asks who it is: given a cached relay list it has an outbox for
                // the pubkey and issues no filter at all
                // (`if (noOutboxList.isEmpty()) return null`), while without one
                // pickRelaysToLoadUsers falls through to this account's index and
                // home relays -- telling them the account is interested in a
                // pubkey that CEP-6 announcements publicly identify as a
                // coordinator. Relay hints alone would not close it either: the
                // search broadens anyway below three hints, and a coordinator
                // usually lists one or two.
                //
                // It is not a complete seal, and the KDoc above must not be read
                // as one: this only silences the assembler that gates on a
                // missing relay list. UserReportsSubAssembler and
                // UserCardsSubAssembler ask this account's own relays about the
                // pubkey either way, and a coordinator that publishes no kind
                // 10002 falls back to the broad path regardless. What this buys
                // is the largest of those queries, not silence.
                //
                // The global CacheClientConnector files whatever comes back, so
                // there is nothing to consume here, and failure is silent on
                // purpose: a coordinator with no profile is ordinary, and the
                // screens already fall back to the key.
                val prefetch =
                    scope.launch {
                        runCatchingCancellable {
                            client.fetchAll(
                                filters =
                                    config.relays.associateWith {
                                        listOf(
                                            ExplainedFilter(
                                                kinds =
                                                    listOf(
                                                        MetadataEvent.KIND,
                                                        AdvertisedRelayListEvent.KIND,
                                                        CvmKinds.SERVER_ANNOUNCEMENT,
                                                    ),
                                                authors = listOf(config.pubKey),
                                                purpose = SubPurpose.COORDINATOR_GROUPS,
                                            ),
                                        )
                                    },
                            )
                        }
                    }
                // A coordinator that stops answering is otherwise invisible:
                // the rooms are there, they are simply never updated again, and
                // the whole sync path logged nothing at all. Only the failing
                // state is worth a line, and only when it changes.
                val watcher =
                    scope.launch {
                        loop.state
                            .filterIsInstance<CordnSyncLoop.State.Retrying>()
                            .distinctUntilChanged()
                            .collect {
                                Log.w(TAG, "coordinator ${config.pubKey.take(8)}\u2026 not syncing (attempt ${it.attempt}, retry in ${it.inMs}ms): ${it.reason}")
                            }
                    }

                loops[config.pubKey] = SyncLoopHandle(loop, session.manager, watcher, prefetch)
            }
        }
        // Whatever the store already held, so a relaunch shows its rooms
        // before the first message of the session arrives.
        session.manager.gids.value
            .forEach {
                refresh(session, it)
                // Read positions come back with the rooms, not when a room is
                // opened: an inbox that shows every room as unread until it is
                // visited is worse than one with no unread state at all.
                val saved = session.manager.roomState(it)
                val room = groups.get(config.pubKey, it)
                room?.restoreState(saved.draft, saved.lastReadCursor)
                // The last message, for the inbox line. One small key per group
                // rather than the whole log: the conversation itself loads when
                // a room is opened. Without it every cordn room read "No
                // messages yet" after a relaunch, however much had been said.
                session.manager.storedMessageSummary(it)?.let { summary -> room?.restorePreview(summary.newest) }
            }
        remember()
        maintainKeyPackages(session)
        return session
    }

    /**
     * Keeps the KeyPackage pool healthy on a coordinator this account already
     * uses — and does nothing at all on one it does not.
     *
     * The condition is the whole design. Publishing a KeyPackage is an
     * attributable act under the account key (§8.4): it tells the coordinator
     * this account exists and is invitable, which is not something to do on
     * someone's behalf at login. But once a KeyPackage IS published there, the
     * coordinator already knows, and letting the pool drain silently has a
     * cost with no matching benefit — `kp_take` consumes a single-use package,
     * so the pool falls as people invite us, and an empty pool means the next
     * invitation fails for a reason the inviter sees and the invitee never
     * does.
     *
     * So: never the first package, always the ones after it. Publishing the
     * first one stays an explicit act on the key-package screen or a join
     * request.
     *
     * Runs detached and swallows failures: this is upkeep, and a coordinator
     * that will not take a KeyPackage must not stop its groups from syncing.
     */
    private fun maintainKeyPackages(session: CordnSession) {
        scope.launch {
            try {
                session.keyPackages.maintain()
            } catch (e: Exception) {
                Log.w(TAG, "could not top up key packages on ${session.coordinatorPubKey.take(8)}\u2026: ${e.message}", e)
            }
        }
    }

    /**
     * Creates a group on [config] and returns the `gid` it was given.
     *
     * The `gid` is a random UUID, which is what cordn's own client uses. It is
     * the caller's to choose (`spec/00.md` §4) and the coordinator never
     * interprets it — the one hard rule is that it must not be derived from the
     * MLS `group_id`, which is secret.
     *
     * Nothing is sent anywhere. Creating a group is a local MLS operation; the
     * coordinator learns the group exists when the first Commit or message is
     * posted to it. That is why this succeeds against a coordinator that is
     * down, and why a group with no other members has told the coordinator
     * nothing at all.
     */
    suspend fun createGroup(
        config: CoordinatorConfig,
        metadata: CordnGroupMetadata,
        gid: String = CordnGroupManager.newGid(),
    ): String {
        val session = session(config)
        session.manager.createGroup(gid, metadata)
        refresh(session, gid)
        return gid
    }

    /**
     * Which of [roster] this coordinator could deliver a Welcome to.
     *
     * The question the create-group screen is really asking. An invitation is a
     * Welcome left on the coordinator addressed to a KeyPackage the invitee
     * published *there* -- so a coordinator that holds no KeyPackage for
     * somebody cannot be told to invite them at all, and picking it means
     * leaving them out. One `kp_list` answers for the whole roster at once,
     * which is why this takes a set rather than a pubkey.
     *
     * Only answers for a coordinator this account already has a session with.
     * Opening one is what commits to a coordinator, and a screen that is still
     * only *looking* must not do that on the user's behalf -- so a discovery
     * offer reports [CordnCoverage.answered] false rather than being probed.
     */
    suspend fun coverage(
        coordinatorPubKey: HexKey,
        roster: Set<HexKey>,
    ): CordnCoverage = registry.coverage(coordinatorPubKey, roster)

    /**
     * Creates the group, then invites [invitees] into it one at a time.
     *
     * Not one operation, and the result says so. `createGroup` and each
     * `invite` are separate calls to the coordinator, so the group can exist
     * with only some of the roster invited -- and nothing that happens to an
     * invitation can undo the group. The caller gets a row per invitee instead
     * of one thrown exception, because "three of five went out" is the state
     * the user has to be shown, not an error to report.
     *
     * One commit for the whole roster, via [CordnGroupManager.inviteAll]. A
     * loop of single invites was not just N epochs and N round trips: an Add is
     * applied locally before it is posted, so one failed post left the group
     * forked and every later invite in the loop building on the fork.
     */
    suspend fun createGroupAndInvite(
        config: CoordinatorConfig,
        metadata: CordnGroupMetadata,
        invitees: List<HexKey>,
    ): CordnGroupCreation {
        val gid = createGroup(config, metadata)
        val session = requireSession(config.pubKey)

        val outcomes =
            try {
                // Order follows the roster, not the batch: this is what the screen lists.
                session.manager.inviteAll(gid, invitees).outcomes(invitees)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The commit itself failed, so nobody was added. One failure
                // for everyone rather than a made-up per-person story.
                Log.w(TAG, "could not invite into $gid: ${e.message}", e)
                invitees.map { CordnInviteOutcome(it, failure = e) }
            }

        refresh(session, gid)
        return CordnGroupCreation(gid, outcomes)
    }

    fun sessionOrNull(coordinatorPubKey: HexKey): CordnSession? = registry.sessionOrNull(coordinatorPubKey)

    /**
     * Renames a coordinator, for this device only.
     *
     * The user's own word for it, which is the only name here that means
     * anything: a coordinator cannot prove one, and both the names it can
     * publish (a kind 0 under CEP-23, the CEP-6 announcement's surface) are its
     * own claim. Until now a label could only be given while adding a
     * coordinator by hand, so one added from discovery -- or restored on a new
     * device -- could never be named at all.
     *
     * Goes through the registry rather than [session]: the loop is already
     * running and must not be rebuilt, and renaming is local bookkeeping that
     * advances no epoch, so it is allowed on a device that has handed its
     * groups away. A label-only change no longer reopens the transport, so this
     * costs one write to disk.
     */
    suspend fun relabel(
        coordinatorPubKey: HexKey,
        label: String?,
    ) {
        val existing = registry.sessionOrNull(coordinatorPubKey) ?: return
        registry.session(existing.config.copy(label = label?.trim()?.ifEmpty { null }))
        remember()
    }

    /**
     * Every invitation waiting for this account, opened but not answered.
     *
     * One round trip per open coordinator, made when someone asks to see their
     * invitations and at no other time. There is no background poll on purpose:
     * every call to a coordinator is metadata (§8), so a badge that stayed
     * up to date would mean telling each coordinator how often this account
     * opens the app.
     *
     * A coordinator that fails to answer is reported rather than logged and
     * dropped. "We could not ask" and "there is nothing for you" look the same
     * on screen and mean opposite things.
     */
    suspend fun invitations(): CordnInvitations = registry.invitations()

    /** Joins the group [invitation] opens, and shows it in the inbox. */
    suspend fun accept(invitation: CordnInvitation): String {
        val session =
            registry.sessionOrNull(invitation.coordinator.pubKey)
                ?: throw IllegalStateException("no session for ${invitation.coordinator.pubKey}")
        val gid = session.manager.accept(invitation.welcome)
        refresh(session, gid)
        return gid
    }

    /** Retires [invitation] without joining. There is no undo; see `decline`. */
    suspend fun decline(invitation: CordnInvitation) {
        registry.sessionOrNull(invitation.coordinator.pubKey)?.manager?.decline(invitation.welcome)
    }

    /**
     * Asks to be added to [gid] on [config].
     *
     * Publishes a fresh KeyPackage first, because there is no way to be added
     * without one: `spec/00.md` §4.2 gives cordn no KeyPackage event kind, so
     * the coordinator is the only place an inviter can find it. This is the
     * one moment where publishing is unambiguously what the user asked for —
     * they are asking strangers to add them — which is why it happens here
     * rather than silently at login (§8.4).
     */
    suspend fun requestToJoin(
        config: CoordinatorConfig,
        gid: String,
    ): Long = session(config).requestToJoin(gid)

    /**
     * Everyone asking to join [gid], or an empty list if we hold no session.
     *
     * Fetched when asked for, never polled — the same §8 reasoning as
     * [invitations]. **Any member can answer these**, not only an admin:
     * cordn's `admin_pubkeys` is presentation metadata and nothing enforces
     * it, so there is no admin check to make here and a UI that implied one
     * would be inventing a boundary the protocol does not have.
     */
    suspend fun joinRequests(
        coordinatorPubKey: HexKey,
        gid: String,
    ): List<JoinRequest> =
        registry
            .sessionOrNull(coordinatorPubKey)
            ?.manager
            ?.pendingJoinRequests()
            ?.filter { it.gid == gid }
            .orEmpty()

    /**
     * The three admin commits, each followed by the refresh that makes them
     * visible.
     *
     * The manager does the MLS and the posting; only [refresh] pushes the new
     * member list, epoch and metadata into the [CordnGroupChatroom] the screens
     * render. A caller that reached the manager directly -- which the group info
     * screen did -- got a commit that worked and a roster that went on showing
     * the group as it was before, until a message happened to arrive or the app
     * was relaunched. Every other mutation here already pairs the two; these are
     * the ones that were missing it.
     */
    suspend fun invite(
        coordinatorPubKey: HexKey,
        gid: String,
        targetPubKey: HexKey,
    ) {
        val session = requireSession(coordinatorPubKey)
        session.manager.invite(gid, targetPubKey)
        refresh(session, gid)
    }

    suspend fun removeMember(
        coordinatorPubKey: HexKey,
        gid: String,
        targetPubKey: HexKey,
    ) {
        val session = requireSession(coordinatorPubKey)
        session.manager.removeMember(gid, targetPubKey)
        refresh(session, gid)
    }

    suspend fun updateGroupMetadata(
        coordinatorPubKey: HexKey,
        gid: String,
        metadata: CordnGroupMetadata,
    ) {
        val session = requireSession(coordinatorPubKey)
        session.manager.updateGroupMetadata(gid, metadata)
        refresh(session, gid)
    }

    /**
     * Changes [gid]'s metadata by [change], applied to what the group carries now, so an edit of
     * some fields (a rename) keeps the rest (the icon, the image).
     */
    suspend fun updateGroupMetadata(
        coordinatorPubKey: HexKey,
        gid: String,
        change: (CordnGroupMetadata) -> CordnGroupMetadata,
    ) {
        val session = requireSession(coordinatorPubKey)
        session.manager.updateGroupMetadata(gid, change)
        refresh(session, gid)
    }

    private fun requireSession(coordinatorPubKey: HexKey): CordnSession =
        registry.sessionOrNull(coordinatorPubKey)
            ?: throw IllegalStateException("no session for $coordinatorPubKey")

    /** Adds the account behind [request] to its group. */
    suspend fun acceptJoinRequest(
        coordinatorPubKey: HexKey,
        request: JoinRequest,
    ) {
        val session =
            registry.sessionOrNull(coordinatorPubKey)
                ?: throw IllegalStateException("no session for $coordinatorPubKey")
        session.manager.acceptJoinRequest(request)
        refresh(session, request.gid)
    }

    /** Retires [request] without adding anyone. */
    suspend fun declineJoinRequest(
        coordinatorPubKey: HexKey,
        request: JoinRequest,
    ) {
        registry.sessionOrNull(coordinatorPubKey)?.manager?.declineJoinRequest(request)
    }

    /**
     * Reopens the coordinators this account used last time, and starts syncing.
     *
     * Call at login. Without it a cordn group is unreachable after a relaunch:
     * its MLS state is still on disk, but a `gid` with no coordinator is not a
     * group anyone can open, and nothing else on the device knows which
     * coordinator serves it.
     */
    suspend fun restore() {
        handoff.restore()
        // A handed-off device does not open sessions at all. Its stores stay on
        // disk so the handoff can be undone, but a live sync loop would fetch,
        // decrypt and self-echo alongside the device that took over.
        if (handoff.handedOff.value) return
        start(coordinatorStore.load())
    }

    /** Opens every configured coordinator and starts syncing. */
    suspend fun start(configs: List<CoordinatorConfig>) {
        configs.forEach {
            try {
                session(it)
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                // One unreachable coordinator must not stop the others: they
                // are independent authorities and a user with two of them has
                // two unrelated sets of groups.
                Log.w(TAG, "could not open coordinator ${it.pubKey.take(8)}…: ${e.message}", e)
            }
        }
    }

    /** Stops every loop and closes every transport. Call at logout. */
    suspend fun stop() {
        lock.withLock {
            loops.values.forEach {
                it.watcher.cancel()
                it.prefetch.cancel()
                it.loop.stop()
            }
            loops.clear()
        }
        registry.close()
        groups.clear()
    }

    /** Drops one coordinator, leaving its stored groups on disk. */
    suspend fun forget(coordinatorPubKey: HexKey) {
        lock.withLock {
            loops.remove(coordinatorPubKey)?.let {
                it.watcher.cancel()
                it.prefetch.cancel()
                it.loop.stop()
            }
        }
        registry.forget(coordinatorPubKey)
        remember(forgotten = coordinatorPubKey)
    }

    /**
     * Forgets [coordinatorPubKey] **and destroys everything stored for it**.
     *
     * Separate from [forget] because they are different decisions and only one
     * of them is reversible. Forgetting closes the session and leaves the MLS
     * state on disk, so re-adding the coordinator brings the groups back.
     * Purging deletes the ratchet trees, the cursors and the KeyPackage
     * private halves — after which those groups cannot be rejoined, only
     * re-entered by a fresh invitation, because MLS state cannot be rebuilt
     * from anywhere else.
     *
     * The coordinator is not told. It keeps whatever it already had; purging
     * is about this device, not about undoing the exposure, and a UI that
     * implied otherwise would be selling a deletion nobody can perform.
     */
    suspend fun purge(coordinatorPubKey: HexKey) {
        forget(coordinatorPubKey)
        groups.forgetCoordinator(coordinatorPubKey)
        withContext(Dispatchers.IO) {
            platformFileSystem.deleteRecursivelyQuietly(CordnStorageLayout.directoryFor(filesDir, accountSigner.pubKey, coordinatorPubKey))
        }
    }

    /**
     * What this account has published on [coordinatorPubKey], and which of
     * them this device can still open.
     *
     * Both halves matter and neither implies the other. The coordinator's
     * listing is the truth about what an inviter can take; the local store is
     * the truth about whether the resulting Welcome can be opened. A package
     * listed there with no private half here belongs to another device of this
     * account — or to an install that is gone, in which case anyone using it
     * sends a Welcome nobody will ever read.
     */
    suspend fun keyPackages(coordinatorPubKey: HexKey): List<CordnKeyPackageRow> =
        registry
            .sessionOrNull(coordinatorPubKey)
            ?.keyPackages
            ?.inventory()
            .orEmpty()

    /**
     * Which identities [coordinatorPubKey] can deliver a Welcome to, or null.
     *
     * For annotating people you are *about* to invite. cordn can only add a
     * member by spending a KeyPackage they published to this coordinator, and
     * `kp_list` is the only non-destructive way to learn who did -- `kp_take`
     * by identity consumes one of their single-use packages
     * (`spec/00.md` §"Coordinator retrieval behavior"), so it can never be
     * used as a probe. One call answers for everybody, which is why this hands
     * back the whole set instead of taking a pubkey.
     *
     * Null is **not** an empty set. It means the question went unanswered, and
     * a caller that renders it as "cannot be added" turns a network failure
     * into a claim about a person. Distinguish them.
     */
    suspend fun identitiesWithKeyPackages(coordinatorPubKey: HexKey): Set<HexKey>? {
        val session = registry.sessionOrNull(coordinatorPubKey) ?: return null
        return session.keyPackages.identitiesWithKeyPackages()
    }

    /** Publishes one KeyPackage. An attributable act under the account key (§8.4). */
    suspend fun publishKeyPackage(
        coordinatorPubKey: HexKey,
        lastResort: Boolean = false,
    ) {
        val session =
            registry.sessionOrNull(coordinatorPubKey)
                ?: throw IllegalStateException("no session for $coordinatorPubKey")
        session.keyPackages.publishNew(lastResort)
    }

    /** Withdraws [refs], coordinator first. See `CordnKeyPackages.withdraw`. */
    suspend fun withdrawKeyPackages(
        coordinatorPubKey: HexKey,
        refs: List<String>,
    ): List<String> =
        registry
            .sessionOrNull(coordinatorPubKey)
            ?.keyPackages
            ?.withdraw(refs)
            .orEmpty()

    /**
     * Collects everything a replacement device would need.
     *
     * Reads from the stores rather than from memory, so a coordinator whose
     * session failed to open is still exported — a backup that silently
     * omitted the groups the app could not reach today would be worst
     * precisely when it is needed.
     */
    suspend fun exportArchive(passphrase: String): ByteArray {
        val configs = coordinatorStore.load()
        val state = withContext(Dispatchers.IO) { CordnLocalState.read(configs.map { it.pubKey }, ::storesFor) }
        return CordnBackup.seal(CordnBackup.Archive.of(accountSigner.pubKey, configs, state), passphrase)
    }

    /**
     * Replaces this device's cordn state with [sealed]'s.
     *
     * **Replaces, and is not a merge.** An MLS state import is a cloneable
     * identity: two devices holding one group's state and both committing fork
     * the ratchet tree, and MLS does not recover — every message after the
     * fork silently fails to decrypt for somebody. Merging would produce that
     * on purpose. Restoring is for a device that has taken over from another,
     * which is what §5.3 means by keeping multi-device a non-goal.
     *
     * An archive from a different account is refused outright: restoring one
     * account's groups under another's key gives a device MLS state whose
     * credentials name somebody else, and every Commit it made would be
     * rejected by the rest of the group.
     */
    suspend fun importArchive(
        sealed: ByteArray,
        passphrase: String,
    ) {
        val archive = CordnBackup.open(sealed, passphrase)
        require(archive.accountPubKey == accountSigner.pubKey) {
            "this backup belongs to a different account"
        }

        stop()

        withContext(Dispatchers.IO) {
            // The old state goes first. Leaving it would merge two devices'
            // histories for any gid present in both, which is the one outcome
            // this must never produce.
            platformFileSystem.deleteRecursivelyQuietly(filesDir / "cordn" / accountSigner.pubKey)
            CordnLocalState.write(archive.toLocalState(), ::storesFor)
        }

        // The marker went with the directory above; the in-memory flag has to
        // follow, or every coordinator refuses to open until a relaunch. This
        // device now holds the restored copy, as after adoptMigration.
        handoff.resume()
        coordinatorStore.save(archive.coordinators)
        start(archive.coordinators)
    }

    /**
     * Snapshots every group for a handoff, read off disk.
     *
     * Reads from the stores rather than memory for the same reason
     * [exportArchive] does: a coordinator whose session failed to open today is
     * still migrated, because a handoff that silently omitted the groups the
     * app could not reach would be wrong precisely when it matters.
     */
    suspend fun migrationSnapshot(): CordnMigrationSnapshot =
        withContext(Dispatchers.IO) {
            CordnMigrationStores.read(filesDir, accountSigner.pubKey, cipher, coordinatorStore.load())
        }

    /**
     * Coordinators announcing themselves on [relays], newest first.
     *
     * Touches no coordinator: it reads the CEP-6 announcements they already
     * published, so nothing discovered here learns this account exists. That
     * is the whole reason discovery can be offered before the user has
     * committed to anything — see [CordnCoordinatorDiscovery].
     */
    suspend fun discover(relays: Set<NormalizedRelayUrl>): CordnCoordinatorDiscovery.Result = CordnCoordinatorDiscovery(client.taggedAs(SubPurpose.COORDINATOR_GROUPS)).discover(relays)

    /**
     * Publishes a handoff and stands this device down.
     *
     * The order is deliberate. Everything is stored and advertised first, and
     * only a migration that got that far marks the device — a failed publish
     * that had already locked would leave the account on a phone that refuses
     * to send from the only copy of its state.
     */
    suspend fun handOff(
        migration: CordnMigration,
        relays: Set<NormalizedRelayUrl>,
    ): CordnHandoffCode {
        val code = migration.publish(migrationSnapshot(), relays)
        stop()
        handoff.markHandedOff()
        return code
    }

    /** Takes this device back after a handoff that did not complete. */
    suspend fun cancelHandOff() {
        handoff.resume()
        start(coordinatorStore.load())
    }

    /**
     * Adopts a snapshot another device published, replacing what is here.
     *
     * **Replaces, and is not a merge** — the same rule [importArchive] states,
     * and for the same reason: two devices holding one group's state and both
     * committing fork the ratchet tree, and MLS does not recover.
     */
    suspend fun adoptMigration(snapshot: CordnMigrationSnapshot) {
        require(snapshot.accountPubKey == accountSigner.pubKey) {
            "this migration belongs to a different account"
        }

        stop()

        val configs =
            withContext(Dispatchers.IO) {
                CordnMigrationStores.write(filesDir, accountSigner.pubKey, cipher, snapshot)
            }

        // A device that just adopted state is emphatically not handed off, even
        // if it had handed off before: it now holds the newest copy.
        handoff.resume()
        coordinatorStore.save(configs)
        start(configs)
    }

    /** What [coordinatorPubKey] says about itself, or null if it is not open. */
    suspend fun serverInfo(coordinatorPubKey: HexKey): CoordinatorServerInfo? = registry.sessionOrNull(coordinatorPubKey)?.serverInfo()

    /** Live health for [coordinatorPubKey], as its calls have observed it. */
    fun health(coordinatorPubKey: HexKey): StateFlow<CoordinatorHealth.State>? = registry.sessionOrNull(coordinatorPubKey)?.health?.state

    /**
     * Writes down which coordinators are open, so the next launch finds them.
     *
     * Failing to persist must not fail the session it follows: the coordinator
     * is already open and working, and losing the record costs a re-entry next
     * launch rather than the group.
     */
    private suspend fun remember(forgotten: HexKey? = null) {
        try {
            coordinatorStore.rememberOpen(registry.coordinators.value, forgotten)
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            Log.w(TAG, "could not persist the coordinator list: ${e.message}", e)
        }
    }

    private fun file(
        coordinatorPubKey: HexKey,
        delivery: CordnGroupManager.Delivery,
    ) {
        when (delivery) {
            is CordnGroupManager.Delivery.Message ->
                groups.add(
                    coordinatorPubKey,
                    delivery.gid,
                    CordnDeliveredMessage(delivery.received.envelope, delivery.cursor),
                )

            // The rest still get a room. A group whose history we cannot
            // read, or that only moved an epoch, is a group we are in —
            // showing nothing would read as a bug rather than as the
            // forward secrecy it actually is.
            is CordnGroupManager.Delivery.Undecryptable,
            is CordnGroupManager.Delivery.EpochAdvanced,
            is CordnGroupManager.Delivery.Echo,
            -> groups.getOrCreate(coordinatorPubKey, delivery.gid)
        }

        // After every delivery, not only after an EpochAdvanced: a Commit that
        // renames the group or changes its membership arrives as an ordinary
        // ingestion, and the room's name and member list are read off MLS
        // state rather than stored, so they are only correct if re-read.
        sessionOrNull(coordinatorPubKey)?.let { refresh(it, delivery.gid) }
    }

    /** One coordinator's encrypted stores on disk, for [CordnLocalState]. */
    private fun storesFor(coordinatorPubKey: HexKey): CordnLocalState.Stores {
        val dir = CordnStorageLayout.directoryFor(filesDir, accountSigner.pubKey, coordinatorPubKey)
        return CordnLocalState.Stores(FileCordnGroupStore(dir, cipher), FileCordnKeyPackageStore(dir, cipher))
    }

    /** Makes sure [gid] has a room, and points it at the current MLS state. */
    private fun refresh(
        session: CordnSession,
        gid: String,
    ) {
        val room = groups.getOrCreate(session.coordinatorPubKey, gid)
        session.manager.group(gid)?.let { room.refreshFrom(it) }
    }

    /**
     * Restores a room's draft and read position from disk, once.
     *
     * Called when a screen opens the room rather than at sync, because it is
     * the only moment it matters and because every room's state would
     * otherwise be read at login for rooms nobody opens.
     */
    suspend fun restoreRoomState(
        coordinatorPubKey: HexKey,
        gid: String,
    ) {
        val session = registry.sessionOrNull(coordinatorPubKey) ?: return
        val room = groups.get(coordinatorPubKey, gid) ?: return
        val saved = session.manager.roomState(gid)
        room.restoreState(saved.draft, saved.lastReadCursor)
        // The conversation itself. Through addAll, so the annotation fold, the
        // ordering and `newest` are all rebuilt by exactly the code that
        // handles live delivery — a room restored down a second path would be
        // a second set of rules to keep in step.
        room.addAll(session.manager.storedMessages(gid))
    }

    /** Persists [gid]'s draft and read position. */
    suspend fun saveRoomState(
        coordinatorPubKey: HexKey,
        gid: String,
    ) {
        val session = registry.sessionOrNull(coordinatorPubKey) ?: return
        val room = groups.get(coordinatorPubKey, gid) ?: return
        session.manager.saveRoomState(gid, CordnRoomState(room.draft.value, room.lastReadCursor.value))
    }

    companion object {
        private const val TAG = "CordnRuntime"
    }
}

/** A group that now exists, and how its invitations went. */
data class CordnGroupCreation(
    val gid: String,
    val outcomes: List<CordnInviteOutcome>,
) {
    val failed: List<CordnInviteOutcome> get() = outcomes.filterNot { it.sent }
}
