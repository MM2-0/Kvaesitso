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
package com.vitorpamplona.amethyst.commons.model.nip46Signer

import com.vitorpamplona.amethyst.commons.connectedApps.nip46.Nip46ClientInfo
import com.vitorpamplona.amethyst.commons.connectedApps.nip46.Nip46ClientStore
import com.vitorpamplona.amethyst.commons.connectedApps.nip46.Nip46PermissionAuthorizer
import com.vitorpamplona.amethyst.commons.connectedApps.signers.AppConnectResult
import com.vitorpamplona.amethyst.commons.connectedApps.signers.AppSignerPolicy
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrOpDecision
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerPermissionLedger
import com.vitorpamplona.amethyst.commons.model.AccountSettings
import com.vitorpamplona.amethyst.commons.model.nip46Signer.Nip46ActivityEntry
import com.vitorpamplona.amethyst.commons.model.nip46Signer.Nip46ActivityLog
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.taggedAs
import com.vitorpamplona.crypto.random.RandomInstance
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestSign
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectEvent
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectURI
import com.vitorpamplona.quartz.nip46RemoteSigner.server.BunkerRequestProcessor
import com.vitorpamplona.quartz.nip46RemoteSigner.server.NostrConnectSignerService
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.runCatchingCancellable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** How many recently-serviced request ids to persist for cross-restart replay dedup. */
private const val MAX_SEEN_IDS = 128

/** How long a pairing waits for the listener to reach the app's relays before answering anyway. */
private const val LISTENER_READY_TIMEOUT_MS = 10_000L

/**
 * How often newly serviced request ids are written to the account settings. Batching keeps a
 * high-throughput client from copying and re-saving the id set on every request; the settings save
 * itself is debounced by 1 s, so this must stay above that or a steady stream would starve it.
 */
private const val SEEN_IDS_FLUSH_MS = 2_000L

/**
 * Auto-forget a connected app after this long with no activity. Each connected NIP-46 app makes the
 * signer hold a background relay subscription indefinitely, so an app paired once and abandoned would
 * leak a relay connection forever; pruning idle apps bounds that growth. Last-used is stamped on
 * connect and on every serviced request, so an app still in use is never pruned.
 */
private const val IDLE_PRUNE_SECONDS = 7 * 24 * 60 * 60L

/**
 * Runs Amethyst as a NIP-46 remote signer ("bunker") for the account, so other
 * apps can sign through it. While [AccountSettings.nip46SignerEnabled] is on, a
 * [NostrConnectSignerService] listens on the user's inbox relays (plus any relays
 * pulled in by a pasted `nostrconnect://` offer) for kind:24133 requests.
 *
 * Two keys are kept apart: a dedicated local [transportSigner] wraps/unwraps the
 * kind-24133 envelope (so the bunker address never reveals the user, and an
 * external NIP-55 account pays no IPC cost for envelope crypto), while the actual
 * sign/encrypt/decrypt and `get_public_key` use the account's identity [signer] —
 * a local key or a NIP-55 external app, whichever the user logged in with.
 *
 * Every request is gated by [Nip46PermissionAuthorizer], i.e. the same
 * "Connected Apps" trust ledger that governs napplets and web origins: a remote
 * client is a connected app under the coordinate `nip46:<signerPubKey>:<clientPubKey>`.
 *
 * The listener restarts whenever the enabled flag or the relay set changes
 * ([collectLatest] cancels the previous run), so editing inbox relays or toggling
 * the feature takes effect immediately.
 */
class Nip46SignerState(
    val signer: NostrSigner,
    val client: INostrClient,
    val ledger: NostrSignerPermissionLedger,
    val clientStore: Nip46ClientStore,
    val inboxRelays: StateFlow<Set<NormalizedRelayUrl>>,
    val scope: CoroutineScope,
    val settings: AccountSettings,
    /** Asks the user when the ledger says ASK (the app's signer dialogs). */
    val consent: Nip46ConsentPrompter,
    /**
     * Whether a relay takes kind-24133 traffic between two keys that aren't its users'. Only those
     * are offered to a client in `switch_relays`: a relay that requires login, payment or restricted
     * writes would refuse the session, and the client, having moved, could not reach us any more.
     */
    val relayAcceptsSignerTraffic: suspend (NormalizedRelayUrl) -> Boolean = { true },
) {
    /** Relays contributed by pasted `nostrconnect://` offers this session, unioned with the inbox set. */
    private val extraRelays = MutableStateFlow<Set<NormalizedRelayUrl>>(emptySet())

    /**
     * One [refreshExtraRelaysFromStore] at a time. Each reads the store, then writes: unserialized,
     * the launch's load can read before a pairing saves its app and write after the pairing's own
     * load, taking the app's relay back out of the listener and losing the app's next request.
     */
    private val extraRelaysRefresh = Mutex()

    /** The relays the running listener is subscribed on; empty while it is stopped or restarting. */
    private val subscribedRelays = MutableStateFlow<Set<NormalizedRelayUrl>>(emptySet())

    /** Clients told to move to the inbox relays (`switch_relays`), until a request shows they did. */
    private val movingClients = MutableStateFlow<Set<HexKey>>(emptySet())

    /** Newest-first, in-memory feed of serviced requests, so the UI can show what apps are doing. */
    val activityLog = Nip46ActivityLog(scope)

    /**
     * Ids of serviced kind-24133 events waiting to be persisted, so a relay replaying stored requests
     * after an app restart is deduped by exact id (see [NostrConnectSignerService.initialSeen]). Only
     * the newest [MAX_SEEN_IDS] are kept, so a drop-oldest channel holds no more than will be saved.
     */
    private val handledIds = Channel<HexKey>(capacity = MAX_SEEN_IDS, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Called by the service for every serviced request, so it only queues the id for [flushHandledIds]. */
    private fun rememberHandledId(eventId: HexKey) {
        handledIds.trySend(eventId)
    }

    /** Merges queued ids into the persisted set at most once per [SEEN_IDS_FLUSH_MS]. Runs for the account's life. */
    private suspend fun flushHandledIds() {
        // Insertion-ordered so the oldest id is evicted first; confined to this coroutine.
        val recent = LinkedHashSet(settings.nip46SeenRequestIds.value)
        while (true) {
            recent.add(handledIds.receive())
            while (true) {
                recent.add(handledIds.tryReceive().getOrNull() ?: break)
            }
            while (recent.size > MAX_SEEN_IDS) {
                recent.iterator().let {
                    it.next()
                    it.remove()
                }
            }
            settings.changeNip46SeenRequestIds(recent.toSet())
            delay(SEEN_IDS_FLUSH_MS)
        }
    }

    /**
     * The dedicated per-account transport signer that wraps the kind-24133 envelope — a local key
     * unrelated to the account identity, so the bunker address/traffic doesn't reveal who it is for,
     * and (unlike the identity signer) an external NIP-55 account pays no IPC cost for envelope crypto.
     * Generated + persisted lazily on first use so accounts that never enable the signer mint nothing.
     *
     * Rebuilt from the persisted key on every call rather than cached, so [rotateAddress] takes effect:
     * the service-restart trigger includes [AccountSettings.nip46TransportKey], and this reads the
     * current value — deriving a keypair from stored bytes is cheap enough for the per-restart cost.
     */
    private fun transportSigner(): NostrSignerInternal = NostrSignerInternal(KeyPair(ensureTransportKeyBytes()))

    /** All relays the signer listens on: the account inbox plus any nostrconnect offer relays. */
    val listeningRelays: StateFlow<Set<NormalizedRelayUrl>> =
        combine(inboxRelays, extraRelays) { inbox, extra -> inbox + extra }
            .stateIn(scope, SharingStarted.Eagerly, inboxRelays.value)

    private val authorizer =
        Nip46PermissionAuthorizer(
            ledger = ledger,
            signerPubKey = signer.pubKey,
            validateSecret = { clientPubKey, offered ->
                // A new app pairs with the current bunker secret; an already-connected app
                // re-authenticates by identity (it already holds a trust level in the ledger).
                val secret = settings.nip46BunkerSecret.value
                (secret.isNotEmpty() && offered == secret) ||
                    ledger.hasPolicy(Nip46PermissionAuthorizer.coordinateFor(signer.pubKey, clientPubKey))
            },
            onConnected = { clientPubKey, request ->
                // A bunker-flow client talks to us on the inbox relays we always listen on, so we
                // only persist its self-declared display metadata (never as authorization — just a label).
                val meta = request.clientMetadata
                if (meta != null && !meta.isEmpty()) {
                    clientStore.store(
                        Nip46PermissionAuthorizer.coordinateFor(signer.pubKey, clientPubKey),
                        Nip46ClientInfo(name = meta.name, url = meta.url, image = meta.image),
                    )
                }
            },
            clientStore = clientStore,
            // A forgotten client's relays are gone from the store now; recompute the listen set so we
            // stop listening on them this session instead of waiting for a restart.
            onDisconnected = { refreshExtraRelaysFromStore() },
            // Interactive consent through the shared signer dialogs: a trust-level picker on first
            // connect, and an allow/deny prompt whenever the ledger says ASK (dangerous kinds,
            // decryption, DMs, or a PARANOID app). Same surface + ledger as napplet/browser signing.
            connectConsent = consent::requestConnect,
            // The account's own signer goes to the bridge so a decrypt request can be decrypted
            // BEFORE the prompt — the dialog shows the actual plaintext instead of an opaque
            // "wants to read your private messages". Local only; nothing is disclosed until approval.
            opConsent = { coordinate, clientPubKey, op, request ->
                consent.requestOp(coordinate, clientPubKey, op, request, signer)
            },
        )

    init {
        scope.launch { flushHandledIds() }

        // extraRelays is a live projection of the persisted client store (the nostrconnect apps' own
        // relays). Load it on start so paired apps stay reachable across restarts; it is refreshed
        // whenever a client connects or is forgotten (bunker-flow apps use the inbox relays instead).
        // Prune apps idle past IDLE_PRUNE_SECONDS first so we don't re-subscribe to a relay only an
        // abandoned app used — forget() already refreshes extraRelays, and we refresh again in case
        // nothing was pruned.
        scope.launch(Dispatchers.IO) {
            runCatchingCancellable { authorizer.pruneIdle(IDLE_PRUNE_SECONDS) }
                .onFailure { Log.w("NIP46Signer") { "idle prune failed: ${it.message}" } }
            refreshExtraRelaysFromStore()
        }

        scope.launch(Dispatchers.IO) {
            combine(settings.nip46SignerEnabled, listeningRelays, settings.nip46TransportKey) { enabled, relays, transportKey ->
                Triple(enabled, relays, transportKey)
            }
                // Inbox/relay/key StateFlows can re-emit an identical value; without this every duplicate
                // would tear the subscription down and re-open it on every relay for no reason. Including
                // the transport key here makes rotateAddress() re-subscribe under the fresh key.
                .distinctUntilChanged()
                .collectLatest { (enabled, relays, transportKey) ->
                    if (!enabled) return@collectLatest
                    if (!signer.isWriteable()) {
                        Log.w("NIP46Signer") { "signer not writeable; cannot host a bunker" }
                        return@collectLatest
                    }
                    if (relays.isEmpty()) return@collectLatest

                    // Run under the key that triggered this run, never one read later: combine can hear
                    // setEnabled's flag before the key it set first, and a listener started under the
                    // new key would then be restarted by that key's late emission, after the pairing
                    // ack, losing the app's first request. With no usable key yet, mint one if needed
                    // and let its emission start the listener.
                    val transportKeyBytes = parseTransportKey(transportKey)
                    if (transportKeyBytes == null) {
                        ensureTransportKeyBytes()
                        return@collectLatest
                    }

                    // Envelope wrapped with the local transport key; the actual work (and get_public_key)
                    // uses the account's identity signer inside the processor.
                    val processor = BunkerRequestProcessor(signer, { listeningRelays.value }, authorizer, switchRelays = ::relaysToMoveTo)
                    val service =
                        NostrConnectSignerService(
                            // The bunker REQ is built inside Quartz, so it is tagged at the client.
                            client = client.taggedAs(SubPurpose.SIGNER),
                            transportSigner = NostrSignerInternal(KeyPair(transportKeyBytes)),
                            processor = processor,
                            relays = relays,
                            onServiced = { request, clientPubKey, error ->
                                Log.d("NIP46Signer") { "${request.method} from ${clientPubKey.take(8)}… → ${error ?: "ok"}" }
                                activityLog.record(
                                    Nip46ActivityEntry(
                                        atSeconds = TimeUtils.now(),
                                        clientPubKey = clientPubKey,
                                        method = request.method,
                                        kind = (request as? BunkerRequestSign)?.event?.kind,
                                        error = error,
                                    ),
                                )
                            },
                            // Seed dedup with the ids we serviced last session so an app restart doesn't
                            // re-sign a relay's replay of the same stored requests (matched by exact id).
                            initialSeen = settings.nip46SeenRequestIds.value,
                            onHandledId = { id -> rememberHandledId(id) },
                            onRequestSeenOn = ::onRequestSeenOn,
                            onListening = { subscribedRelays.value = it },
                        )
                    try {
                        service.run()
                    } finally {
                        subscribedRelays.value = emptySet()
                    }
                }
        }
    }

    /**
     * NIP-46 `switch_relays`: a `nostrconnect://` app talks to us on relays it picked, which we then
     * listen on for good. Move it to the account's inbox relays, where a `bunker://` address points
     * every app. Null (stay) for an app already there, or when no inbox relay would carry the session.
     */
    private suspend fun relaysToMoveTo(clientPubKey: HexKey): Set<NormalizedRelayUrl>? {
        val session = sessionRelays(clientPubKey)
        val inbox = inboxRelays.value
        if (session.isEmpty() || inbox.containsAll(session)) return null

        // In parallel: each NIP-11 lookup may wait seconds, and the client's request timeout runs meanwhile.
        val open =
            coroutineScope {
                inbox.map { relay -> async { relay.takeIf { relayAcceptsSignerTraffic(it) } } }.awaitAll()
            }.filterNotNull().toSet()
        if (open.isEmpty()) return null
        movingClients.update { it + clientPubKey }
        return open
    }

    /**
     * The relays an app offered when it paired (`nostrconnect://`); empty for a `bunker://` app,
     * which uses the inbox relays.
     */
    private suspend fun sessionRelays(clientPubKey: HexKey): Set<NormalizedRelayUrl> =
        clientStore
            .load(authorizer.coordinateFor(clientPubKey))
            ?.relays
            ?.mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }
            ?.toSet()
            ?: emptySet()

    /**
     * A request from an app told to move that arrives on an inbox relay outside its own set proves
     * it moved: only then are its own relays forgotten. Dropping them on the answer alone would
     * strand an app that never got it, since it would keep asking where we no longer listen.
     *
     * Forgotten in the store only: the listen set follows the next time it is rebuilt from the store
     * (a launch, or an app paired or forgotten). Rebuilding it here would restart the listener under
     * the very request that proved the move, and cancel it.
     */
    private fun onRequestSeenOn(
        clientPubKey: HexKey,
        relay: NormalizedRelayUrl,
    ) {
        if (clientPubKey !in movingClients.value || relay !in inboxRelays.value) return
        scope.launch(Dispatchers.IO) {
            val session = sessionRelays(clientPubKey)
            if (session.isEmpty() || relay in session) return@launch
            movingClients.update { it - clientPubKey }
            val coordinate = authorizer.coordinateFor(clientPubKey)
            val info = clientStore.load(coordinate) ?: return@launch
            clientStore.store(coordinate, info.copy(relays = emptySet()))
            Log.d("NIP46Signer") { "${clientPubKey.take(8)}… moved to the inbox relays; ${session.joinToString { it.url }} go at the next launch" }
        }
    }

    /** Whether the account is currently advertising itself as a signer. */
    val enabled: StateFlow<Boolean> get() = settings.nip46SignerEnabled

    fun setEnabled(enabled: Boolean) {
        if (enabled) {
            // Settle the secret and transport key BEFORE flipping the flag, so the listener starts
            // under the final key (the trigger may still hear the flag first; see the listener in init).
            ensureSecret()
            ensureTransportKeyBytes()
        }
        settings.changeNip46SignerEnabled(enabled)
    }

    /** The `bunker://<transport-pubkey>?relay=…&secret=…` string to paste into another app. Generates keys/secret if needed. */
    fun bunkerUri(): String {
        val secret = ensureSecret()
        // Advertise the transport key, not the identity key, so the address doesn't reveal who we are.
        return NostrConnectURI.buildBunker(transportSigner().pubKey, inboxRelays.value, secret)
    }

    /** Replaces the pairing secret with a fresh one, revoking the ability of not-yet-connected apps to use the old one. */
    fun regenerateSecret(): String {
        val fresh = RandomInstance.randomChars(32)
        settings.changeNip46BunkerSecret(fresh)
        return fresh
    }

    /**
     * The anti-spam "burn it down" action: mints a brand-new transport key (and pairing secret), so
     * the old `bunker://` address goes dark — anyone who had it (a spammer included) can no longer
     * reach us, and every app talking to the old transport pubkey is dropped. The running service
     * re-subscribes under the new key because [AccountSettings.nip46TransportKey] feeds the restart
     * trigger. Legit apps re-pair by re-scanning the new address; their trust survives because the
     * Connected-Apps coordinate keys off the stable identity pubkey, not the transport key.
     */
    fun rotateAddress(): String {
        val fresh = KeyPair()
        settings.changeNip46TransportKey(fresh.privKey!!.toHexKey())
        regenerateSecret()
        return NostrConnectURI.buildBunker(fresh.pubKey.toHexKey(), inboxRelays.value, settings.nip46BunkerSecret.value)
    }

    /** Recomputes [extraRelays] from the persisted client store — the source of truth for nostrconnect relays. */
    private suspend fun refreshExtraRelaysFromStore() =
        extraRelaysRefresh.withLock {
            extraRelays.value =
                clientStore
                    .all()
                    .filterKeys { Nip46PermissionAuthorizer.belongsTo(it, signer.pubKey) }
                    .values
                    .flatMap { it.relays }
                    .mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }
                    .toSet()
        }

    /**
     * Forgets a connected client (the user's "Forget" action): revokes its grant, drops its stored
     * metadata/relays, and stops listening on relays that only it used. Same path as a client-sent
     * `logout`, so both are consistent.
     */
    suspend fun forgetClient(clientPubKey: HexKey) = authorizer.forget(clientPubKey)

    /** Returns the current pairing secret, generating and persisting one the first time. */
    private fun ensureSecret(): String {
        val current = settings.nip46BunkerSecret.value
        if (current.isNotEmpty()) return current
        val fresh = RandomInstance.randomChars(32)
        settings.changeNip46BunkerSecret(fresh)
        return fresh
    }

    /** A stored transport private key's bytes, or null if it is unset or corrupt. */
    private fun parseTransportKey(stored: String): ByteArray? = stored.takeIf { it.length == 64 }?.let { runCatching { it.hexToByteArray() }.getOrNull() }

    /** The transport private key bytes, generating and persisting a fresh keypair the first time (or if corrupt). */
    private fun ensureTransportKeyBytes(): ByteArray {
        val existing = parseTransportKey(settings.nip46TransportKey.value)
        if (existing != null) return existing
        val fresh = KeyPair()
        settings.changeNip46TransportKey(fresh.privKey!!.toHexKey())
        return fresh.privKey!!
    }

    /** Whether the app behind [clientPubKey] was approved before, so pairing it again skips the consent prompt. */
    suspend fun isPaired(clientPubKey: HexKey): Boolean = ledger.hasPolicy(authorizer.coordinateFor(clientPubKey))

    /**
     * The client-initiated (`nostrconnect://`) pairing flow: parse a client's
     * offer, send the connect ack that echoes its secret (so the client learns
     * our signer pubkey), register it as a connected app, and start listening on
     * its relays. Enables the signer if it was off.
     */
    suspend fun connectViaNostrConnect(uri: String): ConnectResult {
        val offer = NostrConnectURI.parseNostrConnect(uri) ?: return ConnectResult.InvalidUri
        if (offer.relays.isEmpty()) return ConnectResult.NoRelays
        if (!signer.isWriteable()) return ConnectResult.NotWriteable

        val coordinate = authorizer.coordinateFor(offer.clientPubKey)
        val requestedOps = Nip46PermissionAuthorizer.parsePerms(offer.perms)
        val firstContact = !ledger.hasPolicy(coordinate)

        // First contact: get informed consent — the app's identity, the perms it declared, and a trust
        // level — BEFORE we publish the ack or grant anything. A re-pair keeps the existing trust and
        // per-op decisions the user may have since changed (e.g. an op set to DENY), so it skips the prompt.
        val grantedPolicy: AppSignerPolicy? =
            if (firstContact) {
                when (val result = consent.requestNostrConnectConsent(coordinate, offer.name, offer.url, offer.image, requestedOps)) {
                    is AppConnectResult.Connected -> result.policy
                    AppConnectResult.Blocked, AppConnectResult.Cancelled -> return ConnectResult.Declined
                }
            } else {
                null
            }

        return try {
            if (grantedPolicy != null) {
                ledger.setPolicy(coordinate, grantedPolicy)
                // The user just reviewed and approved these declared perms, so honor them — including
                // sensitive ones — unless they chose PARANOID (ask every time, pre-grant nothing).
                if (grantedPolicy != AppSignerPolicy.PARANOID) {
                    requestedOps.forEach { ledger.setOpDecision(coordinate, it, NostrOpDecision.ALLOW) }
                }
            }
            ledger.updateLastUsed(coordinate)
            // Persist the app's label + its relays so it survives a restart, then start listening now.
            clientStore.store(
                coordinate,
                Nip46ClientInfo(name = offer.name, url = offer.url, image = offer.image, relays = offer.relays.map { it.url }.toSet()),
            )
            refreshExtraRelaysFromStore()

            setEnabled(true)

            // The app answers the ack at once, on its own relays, and kind 24133 is ephemeral: answer
            // only once the (re)started listener is subscribed there, or that first request is lost and
            // the app waits out its retry. Paired and granted above, so the request is served as well.
            withTimeoutOrNull(LISTENER_READY_TIMEOUT_MS) {
                subscribedRelays.first { it.containsAll(offer.relays) }
            } ?: Log.w("NIP46Signer") { "listener not on the app's relays yet; sending the pairing ack anyway" }

            // Echo the offer secret back to the client — authored by the transport key so the client
            // learns THAT as our remote-signer pubkey (not our identity). Only after consent.
            val ack = BunkerResponse(newSubId(), offer.secret, null)
            val reply = NostrConnectEvent.create(ack, offer.clientPubKey, transportSigner())
            client.publish(reply, offer.relays)
            ConnectResult.Connected(offer.clientPubKey, offer.name)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("NIP46Signer") { "nostrconnect pairing failed: ${e.message}" }
            ConnectResult.Failed(e.message ?: "unknown error")
        }
    }

    sealed interface ConnectResult {
        data class Connected(
            val clientPubKey: String,
            val name: String?,
        ) : ConnectResult

        data object InvalidUri : ConnectResult

        data object NoRelays : ConnectResult

        data object NotWriteable : ConnectResult

        /** The user reviewed the connect request and declined (cancelled or blocked). */
        data object Declined : ConnectResult

        data class Failed(
            val reason: String,
        ) : ConnectResult
    }
}
