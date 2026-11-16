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
package com.vitorpamplona.amethyst.commons.connectedApps.nip46

import com.vitorpamplona.amethyst.commons.connectedApps.signers.AppConnectResult
import com.vitorpamplona.amethyst.commons.connectedApps.signers.AppSignerPolicy
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrOpDecision
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerOp
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerPermissionLedger
import com.vitorpamplona.amethyst.commons.connectedApps.signers.SignerOpGrant
import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestConnect
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestNip04Decrypt
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestNip04Encrypt
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestNip44Decrypt
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestNip44Encrypt
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestSign
import com.vitorpamplona.quartz.nip46RemoteSigner.server.Nip46ConnectDecision
import com.vitorpamplona.quartz.nip46RemoteSigner.server.Nip46RequestAuthorizer
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Bridges the NIP-46 signer core to Amethyst's shared "Connected Apps" trust
 * model: a remote client that signs through Amethyst is a connected app just
 * like a napplet or a sandboxed web origin, gated by the same
 * [NostrSignerPermissionLedger] (per-app [AppSignerPolicy] + per-op
 * [NostrOpDecision]) and surfaced on the same management screen.
 *
 * A NIP-46 client is identified by its transport pubkey, mapped to the ledger
 * coordinate `nip46:<signerPubKey>:<clientPubKey>` (see [coordinateFor]) so it
 * lives in its own namespace next to `browser:<origin>` and napplet
 * `<author>:<id>` keys, and so the same client paired with two accounts on one
 * device gets independent grants.
 *
 * Authorization mirrors the napplet path:
 *  - each signing/encryption/decryption request maps to a [NostrSignerOp]
 *    ([sign:kind][NostrSignerOp.SignKind] / [encrypt][NostrSignerOp.Encrypt] /
 *    [decrypt][NostrSignerOp.Decrypt]). `ALLOW` proceeds, `DENY` is refused, and
 *    `ASK` triggers [opConsent] — the interactive prompt through the shared signer
 *    consent dialog (with in-memory session grants and a remembered per-op result).
 *    When no [opConsent] is wired (headless CLI, tests) `ASK` fails closed, so the
 *    signer only ever performs pre-granted operations.
 *  - a `connect` request first validates the pairing secret via
 *    [validateSecret]; on first contact (no standing policy) it asks [connectConsent]
 *    for the trust level, falling back to [defaultPolicyOnConnect] when no prompt is
 *    wired; an existing policy is never downgraded. [onConnected] then runs so the
 *    host can record display metadata.
 */
class Nip46PermissionAuthorizer(
    val ledger: NostrSignerPermissionLedger,
    /** The user's own signer pubkey — namespaces this account's grants in the app-global store. */
    val signerPubKey: HexKey,
    /** Validates the connect secret for a client (bunker secret, or the offer secret in the nostrconnect flow). */
    val validateSecret: suspend (clientPubKey: HexKey, offeredSecret: String?) -> Boolean,
    /** Trust level assigned to a freshly paired app that has no policy yet. */
    val defaultPolicyOnConnect: AppSignerPolicy = AppSignerPolicy.REASONABLE,
    /** Invoked after a successful connect so the host can persist display metadata (name/url/image). */
    val onConnected: (suspend (clientPubKey: HexKey, request: BunkerRequestConnect) -> Unit)? = null,
    /** Persisted client metadata/relays; cleared on logout so a disconnected app leaves nothing behind. */
    val clientStore: Nip46ClientStore? = null,
    /**
     * Invoked after a client is fully forgotten ([onLogout]/[forget]) so the host can react in the
     * running session — e.g. stop listening on relays that only that client used, instead of waiting
     * for the next restart.
     */
    val onDisconnected: (suspend (clientPubKey: HexKey) -> Unit)? = null,
    /**
     * Interactive first-connect consent. When a client connects with a valid secret but has no
     * standing policy, this is asked (showing the app's metadata) and its [AppConnectResult] decides
     * the trust level. `null` (headless CLI, tests) keeps the non-interactive behavior: auto-register
     * at [defaultPolicyOnConnect].
     */
    val connectConsent: (suspend (coordinate: String, clientPubKey: HexKey, request: BunkerRequestConnect) -> AppConnectResult)? = null,
    /**
     * Interactive per-operation consent. Asked when the ledger's standing decision for a request is
     * [NostrOpDecision.ASK]; the returned [SignerOpGrant] both decides the in-flight request and is
     * recorded (remember/session/deny variants). `null` keeps the non-interactive behavior: ASK is
     * treated as deny, so a headless signer only ever performs pre-granted operations.
     */
    val opConsent: (suspend (coordinate: String, clientPubKey: HexKey, op: NostrSignerOp, request: BunkerRequest) -> SignerOpGrant)? = null,
) : Nip46RequestAuthorizer {
    // Session-only ("allow until the signer restarts") grants: coordinate + op key, held in memory
    // and never persisted — mirrors the napplet broker's sessionAllows. Guarded by [throttleLock].
    private val sessionAllows = mutableSetOf<String>()

    // A high-throughput client can authorize many signs per second; last-used is display-only,
    // so coalesce the DataStore write to at most one per client per LAST_USED_THROTTLE_SECS
    // instead of writing the whole per-client preferences file on every request.
    private val lastUsedThrottle = mutableMapOf<String, Long>()
    private val throttleLock = KmpLock()

    // Serializes first-connect consent. The service now handles requests concurrently so per-op prompts
    // can batch, but the connect prompt is a separate single dialog — this keeps two clients connecting
    // at once from stacking two connect dialogs; the second waits for the first to resolve. A coroutine
    // Mutex (not KmpLock) because the guarded region awaits a user dialog and must suspend, not block.
    private val connectLock = Mutex()

    /** The ledger coordinate for [clientPubKey] under this account. */
    fun coordinateFor(clientPubKey: HexKey): String = coordinateFor(signerPubKey, clientPubKey)

    private suspend fun touchLastUsed(coordinate: String) {
        val now = TimeUtils.now()
        val shouldWrite =
            throttleLock.withLock {
                val previous = lastUsedThrottle[coordinate] ?: 0L
                if (now - previous >= LAST_USED_THROTTLE_SECS) {
                    lastUsedThrottle[coordinate] = now
                    true
                } else {
                    false
                }
            }
        if (shouldWrite) ledger.updateLastUsed(coordinate, now)
    }

    /**
     * A client is paired exactly when the ledger holds a trust level for its coordinate — which is
     * what a successful `connect` writes (and what "Forget" removes). Because it is persisted, a
     * client stays paired across restarts, so a returning app that does not re-`connect` still reads
     * the identity it was already granted.
     */
    override suspend fun isPaired(clientPubKey: HexKey): Boolean = ledger.hasPolicy(coordinateFor(clientPubKey))

    override suspend fun onConnect(
        clientPubKey: HexKey,
        request: BunkerRequestConnect,
    ): Nip46ConnectDecision {
        if (!validateSecret(clientPubKey, request.secret)) {
            return Nip46ConnectDecision.Reject("invalid secret")
        }

        val coordinate = coordinateFor(clientPubKey)
        // Serialize first-contact consent so two concurrent connects don't stack dialogs. The
        // hasPolicy re-check inside the lock also means a client that connected on another in-flight
        // request isn't prompted twice.
        val rejection =
            connectLock.withLock {
                if (ledger.hasPolicy(coordinate)) {
                    null
                } else {
                    // First contact: ask the user (if a prompt is wired) which trust level to grant; a
                    // headless signer with no prompt falls back to the non-interactive default.
                    when (val consent = connectConsent?.invoke(coordinate, clientPubKey, request)) {
                        null -> {
                            ledger.setPolicy(coordinate, defaultPolicyOnConnect)
                            null
                        }
                        is AppConnectResult.Connected -> {
                            ledger.setPolicy(coordinate, consent.policy)
                            null
                        }
                        AppConnectResult.Blocked -> "blocked by user"
                        AppConnectResult.Cancelled -> "connection declined"
                    }
                }
            }
        if (rejection != null) return Nip46ConnectDecision.Reject(rejection)
        touchLastUsed(coordinate)
        onConnected?.invoke(clientPubKey, request)

        // Echo the offered secret when present (the client validates it); otherwise ack.
        val echo = request.secret?.takeIf { it.isNotEmpty() } ?: ACK
        return Nip46ConnectDecision.Accept(echo)
    }

    override suspend fun authorize(
        clientPubKey: HexKey,
        request: BunkerRequest,
    ): Boolean {
        // Requests the core routes here always map to an op; if a future method
        // is added that does not, default to allow (the core only gates
        // sign/encrypt/decrypt, so this branch is a safety net).
        val op = request.toSignerOp() ?: return true
        val coordinate = coordinateFor(clientPubKey)
        // A decrypt request also carries a narrower op ("decrypt messages from THIS counterparty").
        // A standing narrow grant satisfies the request without widening the broad one.
        val narrowOp = request.toNarrowSignerOp()

        val allowed =
            when (ledger.decide(coordinate, op)) {
                NostrOpDecision.ALLOW -> true
                // An explicit DENY on the broad op is final — a narrow grant never overrides it.
                NostrOpDecision.DENY -> false
                // ASK: honor a live session grant first, then any narrower standing/session grant,
                // otherwise prompt the user (if wired). No prompt → deny, so a headless signer only
                // ever performs pre-granted operations.
                NostrOpDecision.ASK -> {
                    if (isSessionAllowed(coordinate, op)) {
                        true
                    } else if (narrowOp != null && isNarrowAllowed(coordinate, narrowOp)) {
                        true
                    } else {
                        askOpConsent(coordinate, clientPubKey, op, request)
                    }
                }
            }
        if (allowed) touchLastUsed(coordinate)
        return allowed
    }

    private suspend fun askOpConsent(
        coordinate: String,
        clientPubKey: HexKey,
        op: NostrSignerOp,
        request: BunkerRequest,
    ): Boolean {
        val grant = opConsent?.invoke(coordinate, clientPubKey, op, request) ?: return false
        ledger.record(coordinate, grant)
        if (grant is SignerOpGrant.AllowForSession) {
            // Use the GRANT's op, not the requested one: the dialog may have returned a narrower op
            // ("only from this counterparty"), and a session grant must be no wider than what was given.
            throttleLock.withLock { sessionAllows.add(sessionKey(coordinate, grant.op)) }
        }
        return grant.isAllowed
    }

    /** True when a standing or session grant exists for the narrower [narrowOp] (e.g. decrypt-from-X). */
    private suspend fun isNarrowAllowed(
        coordinate: String,
        narrowOp: NostrSignerOp,
    ): Boolean =
        isSessionAllowed(coordinate, narrowOp) ||
            // Only an explicit per-op override counts. decide() would otherwise fall through to the
            // app's policy, and FULL_TRUST/REASONABLE would answer for an op nobody ever granted.
            ledger.store.loadOpDecision(coordinate, narrowOp)?.let { ledger.decide(coordinate, narrowOp) == NostrOpDecision.ALLOW } ?: false

    private suspend fun isSessionAllowed(
        coordinate: String,
        op: NostrSignerOp,
    ): Boolean = throttleLock.withLock { sessionAllows.contains(sessionKey(coordinate, op)) }

    private fun sessionKey(
        coordinate: String,
        op: NostrSignerOp,
    ): String = "$coordinate|${op.key}"

    override suspend fun onLogout(clientPubKey: HexKey) = forget(clientPubKey)

    /**
     * Fully disconnects [clientPubKey], from either the client's `logout` request or the user's
     * "Forget" action: drops its standing grant (so it must pair again), its persisted metadata/relays,
     * and its in-memory throttle entry, then signals [onDisconnected] so the running session can stop
     * listening on relays that only this client used.
     */
    suspend fun forget(clientPubKey: HexKey) {
        val coordinate = coordinateFor(clientPubKey)
        ledger.revokeAll(coordinate)
        clientStore?.remove(coordinate)
        throttleLock.withLock {
            lastUsedThrottle.remove(coordinate)
            sessionAllows.removeAll { it.startsWith("$coordinate|") }
        }
        onDisconnected?.invoke(clientPubKey)
    }

    /**
     * Forgets every app under this account that hasn't been used in [maxIdleSeconds]. Each connected
     * NIP-46 app makes the signer hold a background relay subscription forever, so an app the user
     * paired once and abandoned would keep a relay connection alive indefinitely; this bounds that
     * growth. `last-used` is stamped on connect and on every serviced operation, so an app that is
     * still signing is never pruned. Returns the client pubkeys that were forgotten.
     */
    suspend fun pruneIdle(
        maxIdleSeconds: Long,
        now: Long = TimeUtils.now(),
    ): List<HexKey> {
        val cutoff = now - maxIdleSeconds
        val stale =
            ledger.store
                .allPolicies()
                .keys
                .filter { belongsTo(it, signerPubKey) }
                .filter { (ledger.lastUsed(it) ?: 0L) < cutoff }
                .mapNotNull { clientPubKeyOf(it) }
        stale.forEach { forget(it) }
        return stale
    }

    companion object {
        /** Ledger coordinate namespace for NIP-46 remote-signer clients. */
        const val COORDINATE_PREFIX = "nip46"

        private const val ACK = "ack"

        /** Minimum seconds between persisted last-used updates for one client (write-coalescing). */
        private const val LAST_USED_THROTTLE_SECS = 60L

        /**
         * The Connected-Apps ledger coordinate for a NIP-46 client, namespaced by
         * the user's signer so the same client paired with two accounts on one
         * device gets independent grants: `nip46:<signerPubKey>:<clientPubKey>`.
         */
        fun coordinateFor(
            signerPubKey: HexKey,
            clientPubKey: HexKey,
        ): String = "$COORDINATE_PREFIX:$signerPubKey:$clientPubKey"

        /** True when [coordinate] is a NIP-46 grant belonging to [signerPubKey]. */
        fun belongsTo(
            coordinate: String,
            signerPubKey: HexKey,
        ): Boolean = coordinate.startsWith("$COORDINATE_PREFIX:$signerPubKey:")

        /** The client pubkey of a `nip46:<signer>:<client>` coordinate, or `null` if it is not one. */
        fun clientPubKeyOf(coordinate: String): HexKey? = if (coordinate.startsWith("$COORDINATE_PREFIX:")) coordinate.substringAfterLast(':') else null

        /** The signer (account identity) pubkey of a `nip46:<signer>:<client>` coordinate, or `null`. */
        fun signerPubKeyOf(coordinate: String): HexKey? =
            if (coordinate.startsWith("$COORDINATE_PREFIX:")) {
                coordinate.substringAfter("$COORDINATE_PREFIX:").substringBefore(':').ifBlank { null }
            } else {
                null
            }

        /**
         * Parses a NIP-46 `perms` string (the comma-separated permission list a client advertises in
         * its `nostrconnect://…?perms=` offer) into the [NostrSignerOp]s it asks for. Tokens follow the
         * de-facto convention: `sign_event:<kind>`, `nip04_encrypt`/`nip44_encrypt` (→ [NostrSignerOp.Encrypt]),
         * `nip04_decrypt`/`nip44_decrypt` (→ [NostrSignerOp.Decrypt]); `get_public_key`, `connect`, `ping`
         * and any unrecognized/blank token are ignored (get_public_key is always allowed and needs no grant).
         * A bare `sign_event` with no kind is ignored — Amethyst grants per kind, so a kindless request is
         * too broad to honor.
         */
        fun parsePerms(perms: String?): List<NostrSignerOp> =
            perms
                ?.split(',')
                ?.mapNotNull { token ->
                    when (val t = token.trim().lowercase()) {
                        "nip04_encrypt", "nip44_encrypt", "encrypt" -> NostrSignerOp.Encrypt
                        "nip04_decrypt", "nip44_decrypt", "decrypt" -> NostrSignerOp.Decrypt
                        else ->
                            if (t.startsWith("sign_event:")) {
                                t.removePrefix("sign_event:").toIntOrNull()?.let { NostrSignerOp.SignKind(it) }
                            } else {
                                null
                            }
                    }
                }?.distinct()
                .orEmpty()

        /** Maps a signing/encryption/decryption [BunkerRequest] to the [NostrSignerOp] it needs. */
        fun BunkerRequest.toSignerOp(): NostrSignerOp? =
            when (this) {
                is BunkerRequestSign -> NostrSignerOp.SignKind(event.kind)
                is BunkerRequestNip04Encrypt -> NostrSignerOp.Encrypt
                is BunkerRequestNip44Encrypt -> NostrSignerOp.Encrypt
                is BunkerRequestNip04Decrypt -> NostrSignerOp.Decrypt
                is BunkerRequestNip44Decrypt -> NostrSignerOp.Decrypt
                else -> null
            }

        /**
         * The NARROWER op a request could be granted, or `null` when it has no narrower form.
         *
         * Only decryption has one today: `decrypt` reveals private conversations, and one broad
         * "always allow" hands over every conversation forever — so the consent dialog can also offer
         * "always allow for THIS counterparty" ([NostrSignerOp.DecryptFrom]). Encryption and signing
         * have no equivalent: their counterparty/kind is already the thing being granted.
         */
        fun BunkerRequest.toNarrowSignerOp(): NostrSignerOp? =
            when (this) {
                is BunkerRequestNip04Decrypt -> NostrSignerOp.DecryptFrom(pubKey)
                is BunkerRequestNip44Decrypt -> NostrSignerOp.DecryptFrom(pubKey)
                else -> null
            }

        /** The counterparty a decrypt request names, or `null` for any other request. */
        fun BunkerRequest.decryptCounterparty(): HexKey? =
            when (this) {
                is BunkerRequestNip04Decrypt -> pubKey
                is BunkerRequestNip44Decrypt -> pubKey
                else -> null
            }
    }
}
