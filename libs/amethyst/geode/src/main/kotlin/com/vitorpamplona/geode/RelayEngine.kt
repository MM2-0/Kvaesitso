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
package com.vitorpamplona.geode

import com.vitorpamplona.geode.config.RuntimeConfig
import com.vitorpamplona.geode.config.RuntimeConfigData
import com.vitorpamplona.geode.config.seedInto
import com.vitorpamplona.geode.config.snapshotOf
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.server.NostrServer
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.EmptyPolicy
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.IRelayPolicy
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import com.vitorpamplona.quartz.nip11RelayInfo.Nip11RelayInformation
import com.vitorpamplona.quartz.nip43RelayMembers.server.RelayMembershipServer
import com.vitorpamplona.quartz.nip77Negentropy.NegentropySettings
import com.vitorpamplona.quartz.nip86RelayManagement.server.BanListPolicy
import com.vitorpamplona.quartz.nip86RelayManagement.server.BanStore
import com.vitorpamplona.quartz.nip86RelayManagement.server.Nip86Server
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.coroutines.CoroutineContext

/**
 * A self-contained Nostr relay scoped to a single URL. Wraps a [NostrServer]
 * over an [EventStore] (defaults to an in-memory SQLite database).
 *
 * Speaks NIP-01 (REQ/EVENT/EOSE/CLOSE), NIP-11 (relay info via [info]),
 * NIP-42 (AUTH — supply [policyBuilder] = `{ FullAuthPolicy(url) }` or
 * stack one with [com.vitorpamplona.quartz.nip01Core.relay.server.policies.IRelayPolicy.plus]),
 * NIP-45 (COUNT) and NIP-50 (search via the SQLite FTS index).
 *
 * Two transports:
 *   - [com.vitorpamplona.quartz.nip01Core.relay.server.inprocess.InProcessWebSocket] /
 *     [InProcessRelays] — no socket, fastest path, ideal
 *     for unit tests inside one JVM.
 *   - [KtorRelay] — Ktor `embeddedServer` listening on a real port.
 *     Use when external clients need to connect (`cli`, instrumented tests,
 *     standalone deployment).
 */
class RelayEngine(
    val url: NormalizedRelayUrl,
    val store: IEventStore = EventStore(dbName = null, relay = url, indexStrategy = RelayIndexingStrategy),
    /**
     * Runtime configuration handle — owns the persistence path (when
     * any), the NIP-11 doc seed, and the seed for the NIP-86 ban /
     * allow / kind lists. Mirrors how [store] is built externally and
     * passed in: tests use the in-memory default
     * (`RuntimeConfig(file=null, seed=...)`); `Main.kt` builds one
     * from `StaticConfig` so first-boot defaults flow from TOML and
     * subsequent admin mutations are persisted next to the SQLite
     * event store.
     *
     * The default constructs an in-memory-only handle whose seed
     * advertises the supported NIPs via [RelayInfo.default].
     */
    private val runtimeConfig: RuntimeConfig =
        RuntimeConfig(
            file = null,
            seed = RuntimeConfigData(info = RelayInfo.default(url).document),
        ),
    policyBuilder: () -> IRelayPolicy = { EmptyPolicy },
    parentContext: CoroutineContext = SupervisorJob(),
    /**
     * Run Schnorr signature verification in parallel inside the
     * [com.vitorpamplona.quartz.nip01Core.relay.server.backend.IngestQueue]
     * instead of serially in the policy chain. Enables the Tier-3
     * win in `geode/plans/2026-05-07-event-ingestion-batching.md`.
     *
     * When set, callers MUST omit `VerifyPolicy` from [policyBuilder]
     * — having both verifies the same event twice for no benefit.
     * `Main.kt` skips `VerifyPolicy` when this flag is on.
     */
    parallelVerify: Boolean = false,
    /**
     * NIP-77 server-side tuning (frame cap, snapshot cap,
     * per-connection session cap). Defaults to strfry-parity values.
     */
    negentropySettings: NegentropySettings = NegentropySettings.Default,
    /**
     * Pubkeys allowed to invoke NIP-86 admin RPCs. Empty (the default)
     * effectively disables the admin API: every dispatch returns
     * `not authorized`. Transports (e.g. [KtorRelay] over HTTP) read
     * [nip86Server] and bind it to their wire protocol — the engine
     * owns *who* is admin; the transport owns *how* admins authenticate.
     */
    adminPubkeys: Set<HexKey> = emptySet(),
    /**
     * The relay's own identity. When set, the NIP-11 doc advertises its
     * pubkey as `self` (overriding whatever the persisted doc says), and
     * it signs the relay-authored NIP-43 events. Null (the default) leaves
     * `self` as configured and the relay unable to sign anything.
     */
    relayKey: KeyPair? = null,
    /**
     * NIP-43 membership (see `geode/plans/2026-09-27-nip43-membership.md`).
     * When on — requires [relayKey] — the NIP-86 pubkey allow list is the
     * member list and gates writes even while empty, kind 28934 / 28936
     * join and leave requests are answered by [membershipServer], the
     * relay publishes kinds 13534 / 33534 / 8000 / 8001 (and NIP-09
     * deletions for removed roles) signed by [relayKey], the NIP-86 role
     * and invite-code methods are offered, and NIP-11 advertises 43. Off
     * (the default): none of that — the role / claim RPCs are not even
     * advertised, and NIP-43 is stripped from `supported_nips`.
     */
    val membership: Boolean = false,
    /** How far a join / leave request's `created_at` may be from now. */
    membershipRequestWindowSeconds: Long = RelayMembershipServer.DEFAULT_REQUEST_WINDOW_SECONDS,
) : AutoCloseable {
    init {
        require(!membership || relayKey != null) { "NIP-43 membership needs the relay's own key (relayKey) to sign its events" }
    }

    private val boot: RuntimeConfigData = runtimeConfig.effective()

    /** Signs as the relay's NIP-11 `self`; null when no [relayKey] was configured. */
    val relaySigner: NostrSignerSync? = relayKey?.let { NostrSignerSync(it) }

    /**
     * Live NIP-11 doc. Mutable via [updateInfo] so NIP-86 admin RPCs
     * can swap it atomically; readers (NIP-11 GET) re-read every
     * request so changes are visible immediately. The `!!` is load-
     * bearing: the seed always has a non-null info, so a null here
     * means a manually corrupted state file — fail loud over serving
     * empty NIP-11.
     */
    @Volatile
    var info: RelayInfo = RelayInfo(boot.info!!.advertisingIdentity())
        private set

    /**
     * Stamps the boot-time NIP-11 doc with what this engine actually runs:
     * `self` = [relaySigner]'s pubkey, and NIP-43 in `supported_nips` iff
     * [membership] is on — a persisted or operator-written doc may say
     * otherwise, and clients only send join requests to relays that
     * advertise 43.
     */
    private fun Nip11RelayInformation.advertisingIdentity(): Nip11RelayInformation {
        val nips = supported_nips
        val doc =
            when {
                membership && (nips == null || NIP_43 !in nips) -> {
                    copy(supported_nips = ((nips ?: emptyList()) + NIP_43).sortedBy { it.toIntOrNull() ?: Int.MAX_VALUE })
                }

                !membership && nips != null && NIP_43 in nips -> {
                    copy(supported_nips = nips - NIP_43)
                }

                else -> {
                    this
                }
            }
        return relaySigner?.let { doc.copy(self = it.pubKey) } ?: doc
    }

    /** Mutates the live NIP-11 doc and persists the snapshot. */
    fun updateInfo(transform: (Nip11RelayInformation) -> Nip11RelayInformation) {
        info = RelayInfo(transform(info.document))
        snapshot()
    }

    /**
     * Runtime-mutable ban / allow lists. Mutated by [Nip86Server] via
     * NIP-86 RPCs; consulted on every EVENT by [BanListPolicy]. Seeded
     * at boot from the persisted snapshot (or the static [runtimeConfig]
     * seed on first boot) without firing the mutation hook.
     */
    val banStore: BanStore =
        BanStore(
            onMutation = {
                snapshot()
                // Covers mutations outside the RPC / join paths (which
                // sync synchronously) — e.g. direct BanStore calls.
                membershipServer?.requestSync()
            },
        ).apply { boot.seedInto(this) }

    /**
     * NIP-86 admin RPC dispatcher. Transport-agnostic — `KtorRelay`
     * wraps it with `Nip86HttpHandler` for HTTP/NIP-98; an in-process
     * tool could call `dispatch(adminPubkey, req)` directly.
     */
    val nip86Server: Nip86Server =
        Nip86Server(
            banStore = banStore,
            infoHolder =
                object : Nip86Server.InfoHolder {
                    override fun get(): Nip11RelayInformation = info.document

                    override fun set(info: Nip11RelayInformation) = updateInfo { info }
                },
            onBan = { filter -> store.delete(filter) },
            allowList = adminPubkeys,
            // Without a NIP-43 engine the role / claim methods would be
            // silent no-ops, so they're only offered with membership on.
            nip43Methods = membership,
            // Republish before the RPC answers, so a client that reads the
            // relay right after an admin change sees the new events.
            afterMutation = { membershipServer?.sync() },
        )

    /**
     * Writes the current state (NIP-11 doc + ban lists) to disk via
     * [RuntimeConfig.save] — no-op when no state file is configured.
     * Best-effort: I/O failures are logged to stderr and swallowed so
     * an unwritable disk doesn't take the relay down.
     */
    fun snapshot() {
        runCatching {
            runtimeConfig.save(snapshotOf(info.document, banStore))
        }.onFailure {
            System.err.println("warning: failed to write relay state file: ${it.message}")
        }
    }

    val server =
        NostrServer(
            store,
            // Always prepend BanListPolicy so NIP-86 admin actions bite.
            // EmptyPolicy from the caller means "no extra policies" — use
            // BanListPolicy alone; otherwise stack so both must accept.
            policyBuilder = {
                val user = policyBuilder()
                val banList = BanListPolicy(banStore, membersOnly = membership)
                if (user === EmptyPolicy) banList else user + banList
            },
            parentContext = parentContext,
            parallelVerify = parallelVerify,
            negentropySettings = negentropySettings,
        )

    /** Background scope for [RelayMembershipServer.requestSync]; cancelled on [close]. */
    private val membershipScope = CoroutineScope(parentContext + SupervisorJob(parentContext[Job]))

    /**
     * The NIP-43 engine, when [membership] is on: answers join / leave
     * requests on every connection and republishes the relay-signed
     * membership events whenever the [banStore] changes.
     */
    val membershipServer: RelayMembershipServer? =
        if (membership) {
            RelayMembershipServer(
                signer = relaySigner!!,
                banStore = banStore,
                publish = ::publishOwn,
                load = { filter -> store.query<Event>(filter) },
                scope = membershipScope,
                relayName = url.url,
                requestWindowSeconds = membershipRequestWindowSeconds,
            )
        } else {
            null
        }

    init {
        membershipServer?.let {
            server.eventCommandHandler = it
            // Bring the stored 13534 / 33534 in line with the boot state
            // (first boot, a key change, a hand-edited state file).
            it.requestSync()
        }
    }

    /**
     * Stores a relay-authored event: through the group-commit writer and
     * live fan-out like any publish, but skipping the policy chain (the
     * relay's own events aren't subject to its write rules) and signature
     * verification (we just signed it).
     */
    private suspend fun publishOwn(event: Event): Boolean {
        val outcome = CompletableDeferred<IEventStore.InsertOutcome>()
        server.ingest(event, skipVerify = true) { outcome.complete(it) }
        return when (val result = outcome.await()) {
            IEventStore.InsertOutcome.Accepted -> {
                true
            }

            else -> {
                Log.w("RelayEngine") { "relay-signed kind ${event.kind} not stored: $result" }
                false
            }
        }
    }

    override fun close() {
        membershipScope.cancel()
        server.close()
    }

    companion object {
        private const val NIP_43 = "43"
    }
}
