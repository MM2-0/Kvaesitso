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

import com.vitorpamplona.geode.config.BannedEntry
import com.vitorpamplona.geode.config.MirrorFilterValidator
import com.vitorpamplona.geode.config.RateLimits
import com.vitorpamplona.geode.config.RelayIdentity
import com.vitorpamplona.geode.config.RuntimeConfig
import com.vitorpamplona.geode.config.RuntimeConfigData
import com.vitorpamplona.geode.config.StaticConfig
import com.vitorpamplona.geode.mirror.MirrorDirection
import com.vitorpamplona.geode.mirror.MirrorUpstream
import com.vitorpamplona.geode.mirror.MirrorWorker
import com.vitorpamplona.geode.mirror.SyncCoverageFile
import com.vitorpamplona.geode.server.ClientAddresses
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.EmptyPolicy
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.IRelayPolicy
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.RejectFutureEventsPolicy
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.VerifyAuthOnlyPolicy
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.VerifyPolicy
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import com.vitorpamplona.quartz.nip01Core.store.NdjsonImportExport
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import com.vitorpamplona.quartz.nip77Negentropy.NegentropySettings
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Standalone entry point. The first argument may be a verb:
 *
 *   geode [relay] [flags]      serve the relay (default when no verb is given)
 *   geode import [flags] [FILE…]   bulk-load NDJSON events into the store
 *   geode export [flags]           dump the store as NDJSON to stdout
 *
 * `import`/`export` are geode's equivalent of `strfry import` / `strfry export`:
 * one JSON event per line, the interchange format for seeding a relay, migrating
 * between relays, or taking a backup. `import` reads the given files (or stdin
 * when none are named), verifies signatures by default (same as the relay;
 * `--no-verify` to skip), and prints a read/imported/rejected summary to stderr.
 * `export` streams every stored event, newest-first, to stdout. Both stream, so
 * a multi-million-event corpus round-trips in roughly constant memory. They share
 * the relay's `--db`/`--config`/`--no-search` flags so the same store is targeted.
 *
 * Run with:
 *   ./gradlew :geode:run --args="--config /etc/geode.toml"
 *   ./gradlew :geode:run --args="import --db relay.sqlite corpus.ndjson"
 * or
 *   java -cp ... com.vitorpamplona.geode.MainKt --port 7447
 *
 * Configuration precedence (highest to lowest):
 *   1. CLI flags (`--host`, `--port`, …)
 *   2. TOML file passed via `--config <path>`
 *   3. Built-in defaults (host=0.0.0.0, port=7447, in-memory db, …)
 *
 * Every section is enforced: `[info]` populates the NIP-11 doc,
 * `[network]` controls the bind, `[database]` chooses the SQLite path,
 * `[options]` toggles AUTH/verify/future-skew, `[identity]` holds the
 * relay's own key (NIP-11 `self`), `[membership]` turns on NIP-43, and
 * `[authorization]` seeds the runtime
 * [com.vitorpamplona.quartz.nip86RelayManagement.server.BanStore] on
 * first boot (see [com.vitorpamplona.geode.config.RuntimeConfig]).
 *
 * CLI flags:
 *   --config <file>    TOML config (see config.example.toml)
 *   --host <addr>      bind address (default from config or 0.0.0.0)
 *   --port <n>         tcp port (default from config or 7447, 0 to autobind)
 *   --path <p>         ws path (default from config or /)
 *   --info <file>      NIP-11 doc file (overrides [info] section)
 *   --db <file>        sqlite db path (overrides [database].file)
 *   --store <backend>  event-store backend (overrides [database].backend):
 *                      "sqlite" (default), "fs", or the fully-qualified
 *                      class name of a custom IEventStore on the classpath.
 *   --auth             require NIP-42 AUTH (sets options.require_auth = true)
 *   --optional-auth    advertise NIP-42 AUTH but don't require it (sets
 *                      options.optional_auth = true; ignored when --auth is set)
 *   --no-verify        DO NOT verify event signatures (off by default
 *                      verify is on; use only for trusted-input
 *                      scenarios like fixture replay).
 *   --no-search        disable NIP-50 full-text search (sets
 *                      options.full_text_search = false): no FTS index is
 *                      built or maintained, NIP-11 stops advertising 50,
 *                      and `search` filters match nothing. Skips the
 *                      per-event tokenization cost on ingest.
 */
fun main(args: Array<String>) {
    // Terminal flags handled before verb dispatch so a packaged binary has a
    // fast, exit-0 command that never opens a store or binds a port — the smoke
    // test the Homebrew formula, the .deb/.rpm post-install check, and the
    // Docker image healthcheck all invoke.
    if (args.any { it == "--version" || it == "-V" }) {
        println("geode ${BuildConfig.VERSION}")
        return
    }
    if (args.any { it == "--help" || it == "-h" }) {
        printHelp()
        return
    }
    when (args.firstOrNull()?.takeUnless { it.startsWith("--") }) {
        "import" -> runImport(args.copyOfRange(1, args.size))
        "export" -> runExport(args.copyOfRange(1, args.size))
        // Explicit `relay` verb or no verb at all → serve. A bare `geode --port …`
        // (no verb) stays valid so existing invocations don't change.
        "relay" -> serve(args.copyOfRange(1, args.size))
        else -> serve(args)
    }
}

private fun printHelp() {
    println(
        """
        geode ${BuildConfig.VERSION} - a standalone Nostr relay.

        Usage:
          geode [relay] [flags]         serve the relay (default when no verb is given)
          geode import [flags] [FILE...] bulk-load NDJSON events into the store
          geode export [flags]          dump the store as NDJSON to stdout

        Common flags:
          --config <file>    TOML config (see config.example.toml)
          --host <addr>      bind address (default 0.0.0.0)
          --port <n>         tcp port (default 7447, 0 to autobind)
          --path <p>         websocket path (default /)
          --db <file>        sqlite db path (persistent storage)
          --store <backend>  event-store backend: sqlite (default), fs, or a
                             fully-qualified IEventStore class name
          --auth             require NIP-42 AUTH
          --optional-auth    advertise NIP-42 AUTH but don't require it
          --no-verify        do NOT verify event signatures (trusted input only)
          --no-search        disable NIP-50 full-text search
          --version, -V      print the version and exit
          --help, -h         print this help and exit

        Docs: https://github.com/vitorpamplona/amethyst/blob/main/geode/README.md
        """.trimIndent(),
    )
}

/**
 * Opens the store the `import`/`export` verbs operate on, honoring the same
 * `--db`/`--config`/`--no-search` selection the relay uses so a verb targets the
 * exact store the server would.
 */
private class StoreContext(
    /** Human-readable location of the store's bytes, for the verb's summary line. */
    val dbFile: String?,
    val store: IEventStore,
)

private fun openStore(a: Args): StoreContext {
    val config =
        (a.opt(CONFIG_FLAG)?.let { StaticConfig.fromFile(File(it)) } ?: StaticConfig())
            .withStoreOverride(a.opt(STORE_FLAG))
    val fullTextSearch = !a.flag(NO_SEARCH_FLAG) && config.options.full_text_search
    // The verbs operate on the same store the server would, honoring the same
    // `[database].backend` selection — unscoped (relay = null) because bulk
    // load/dump has no single relay to attribute NIP-62 cascades to.
    val store =
        StoreFactory.open(
            config = config,
            relay = null,
            fullTextSearch = fullTextSearch,
            dbOverride = a.opt("--db"),
        )
    // Where the bytes land, for the summary line. Only the SQLite in-memory
    // case has no location; the FS store always has a directory.
    val sqlite =
        config.database.backend
            .trim()
            .lowercase() in StoreFactory.SQLITE_BACKEND_KEYWORDS
    val location = a.opt("--db") ?: config.database.file?.takeUnless { sqlite && config.database.in_memory }
    return StoreContext(location, store)
}

private fun runImport(args: Array<String>) {
    val a = parseArgs(args)
    val config = a.opt(CONFIG_FLAG)?.let { StaticConfig.fromFile(File(it)) } ?: StaticConfig()
    // Verify by default, matching the relay's stance — `import` won't trust a
    // file's signatures any more than the relay trusts a client's. `--no-verify`
    // is the trusted-input escape hatch (fixture replay, a dump from a relay you
    // already trust).
    val verify = !a.flag(NO_VERIFY_FLAG) && config.options.verify_signatures
    val ctx = openStore(a)
    try {
        val stats =
            runBlocking {
                if (a.positionals.isEmpty()) {
                    System.`in`.bufferedReader().useLines { NdjsonImportExport.import(ctx.store, it, verify) }
                } else {
                    var acc = NdjsonImportExport.ImportStats.ZERO
                    for (file in a.positionals) {
                        acc += File(file).bufferedReader().useLines { NdjsonImportExport.import(ctx.store, it, verify) }
                    }
                    acc
                }
            }
        System.err.println(
            "geode import: read=${stats.read} imported=${stats.imported} " +
                "rejected=${stats.rejected} failed=${stats.failed} " +
                "invalid-sig=${stats.invalid} malformed=${stats.malformed} " +
                "→ ${ctx.dbFile ?: "(in-memory — not persisted; pass --db)"}",
        )
    } finally {
        ctx.store.close()
    }
}

private fun runExport(args: Array<String>) {
    val a = parseArgs(args)
    val ctx = openStore(a)
    try {
        val out = System.out.bufferedWriter()
        val n = runBlocking { NdjsonImportExport.export(ctx.store, out) }
        out.flush()
        System.err.println("geode export: $n events from ${ctx.dbFile ?: "(in-memory — empty)"}")
    } finally {
        ctx.store.close()
    }
}

private fun serve(args: Array<String>) {
    val a = parseArgs(args)

    val config: StaticConfig =
        (
            a
                .opt(CONFIG_FLAG)
                ?.let { StaticConfig.fromFile(File(it)) }
                ?: StaticConfig()
        ).withStoreOverride(a.opt(STORE_FLAG))
    config.validate()

    val host = a.opt("--host") ?: config.network.host
    val port = a.opt("--port")?.toInt() ?: config.network.port
    val path = a.opt("--path") ?: config.network.path

    val cliInfoFile = a.opt("--info")?.let { File(it) }
    val requireAuth = a.flag("--auth") || config.options.require_auth
    // Optional AUTH advertises the challenge without gating commands on it.
    // Mandatory AUTH already sends the challenge, so it wins when both are set.
    val optionalAuth = !requireAuth && (a.flag("--optional-auth") || config.options.optional_auth)
    // Verify is on by default; only disable when the operator explicitly
    // opts out (CLI `--no-verify` or `[options].verify_signatures = false`
    // in the config).
    val verifySigs = !a.flag(NO_VERIFY_FLAG) && config.options.verify_signatures
    // Parallel verify is on whenever signature checking is on; the
    // IngestQueue handles it instead of VerifyPolicy. Operators can
    // force the legacy in-policy path with `--no-parallel-verify` or
    // `[options].parallel_verify = false`.
    val parallelVerify =
        verifySigs && !a.flag("--no-parallel-verify") && config.options.parallel_verify
    // NIP-50 search is on by default; `--no-search` (or
    // `[options].full_text_search = false`) trades it for cheaper ingest —
    // e.g. to match relays that don't implement NIP-50 at all.
    val fullTextSearch = !a.flag(NO_SEARCH_FLAG) && config.options.full_text_search

    // Advertised URL: explicit `info.relay_url` wins, then build from
    // host/port/path. 0.0.0.0 bind → 127.0.0.1 in the URL so NIP-42
    // challenges are well-formed.
    val advertisedHost = if (host == "0.0.0.0") "127.0.0.1" else host
    val advertisedUrl =
        (config.info.relay_url ?: "ws://$advertisedHost:$port$path").normalizeRelayUrl()

    val info =
        cliInfoFile?.let { RelayInfo.fromFile(it) }
            ?: config.resolveInfo(fullTextSearch)

    // Deployment tuning from `[database]` — off by default; the quartz
    // library defaults stay tuned for the app-side stores.
    val extraPragmas =
        buildList {
            config.database.mmap_size?.let { add("PRAGMA mmap_size = $it;") }
            if (config.database.temp_store_memory) add("PRAGMA temp_store = MEMORY;")
        }
    val store =
        StoreFactory.open(
            config = config,
            relay = advertisedUrl,
            fullTextSearch = fullTextSearch,
            dbOverride = a.opt("--db"),
            extraPragmas = extraPragmas,
        )

    // Built once: every connection's policy shares these, so the limits are per client, not per socket.
    val rateLimits = config.rate_limits.toRateLimits()
    val policyBuilder: () -> IRelayPolicy = {
        composePolicy(config, advertisedUrl, requireAuth, optionalAuth, verifySigs, parallelVerify, rateLimits)
    }

    // Static [authorization] feeds the runtime BanStore only when no
    // state file exists yet. As soon as the operator's first admin RPC
    // writes the file, BanListPolicy consults the persisted lists
    // exclusively — later edits to [authorization] in the TOML are
    // ignored at boot.
    val runtimeConfig =
        RuntimeConfig(
            file = config.admin.state_file?.let { File(it) },
            seed =
                RuntimeConfigData(
                    info = info.document,
                    allowedPubkeys = config.authorization.pubkey_whitelist.map { BannedEntry(it) },
                    bannedPubkeys = config.authorization.pubkey_blacklist.map { BannedEntry(it) },
                    allowedKinds = config.authorization.kind_whitelist,
                    disallowedKinds = config.authorization.kind_blacklist,
                ),
        )
    val negentropySettings =
        NegentropySettings(
            frameSizeLimit = config.negentropy.frame_size_limit,
            maxSyncEvents = config.negentropy.max_sync_events,
            maxSessionsPerConnection = config.negentropy.max_sessions_per_connection,
        )
    // The relay's own key (NIP-11 `self`): explicit config, else generated
    // next to the admin state file when NIP-43 membership needs one.
    val relayKey =
        RelayIdentity.resolve(
            identity = config.identity,
            needed = config.membership.enabled,
            stateFile = config.admin.state_file,
        )
    val relay =
        RelayEngine(
            advertisedUrl,
            store,
            runtimeConfig,
            policyBuilder,
            parallelVerify = parallelVerify,
            negentropySettings = negentropySettings,
            adminPubkeys = config.admin.pubkeys.toSet(),
            relayKey = relayKey,
            membership = config.membership.enabled,
            membershipRequestWindowSeconds = config.membership.request_window_seconds,
        )
    val server =
        KtorRelay(
            relay,
            host = host,
            port = port,
            path = path,
            connectionGroupSize = config.network.connection_group_size,
            workerGroupSize = config.network.worker_group_size,
            callGroupSize = config.network.call_group_size,
            httpCommands = config.http.toSettings(),
            // Websockets count per client too, so the proxy settings apply even with NIP-FE off.
            clientAddresses = ClientAddresses(config.http.trusted_proxies.toSet(), config.http.client_address_header),
        ).start()

    // `[[mirror]]` upstreams: dial each configured relay and stream its
    // events into the local store. `trusted = true` entries skip Schnorr
    // verification for that connection (relay-to-relay trust) — only
    // meaningful while verify is on; with --no-verify nothing verifies
    // anyway. Never mirror ourselves: a self-URL would echo every local
    // publish back forever.
    val upstreams =
        config.mirror.map { m ->
            // The optional scope filter is parsed eagerly so a malformed
            // JSON object fails the boot, not the first delivery. Its
            // since/limit are stripped: the mirror owns the time window
            // (backfill_seconds) and never bounds the subscription.
            val scope =
                m.filter?.let { json ->
                    // Strict-validate FIRST: the deserializer is tolerant
                    // (unknown keys skipped, wrong-typed entries dropped),
                    // so a typo like `{"kindss":[4]}` would silently parse
                    // to an empty filter — widening a trusted upstream's
                    // scope to the whole firehose. This filter is the
                    // containment boundary for `trusted = true`, so a typo
                    // must fail the boot, not the boundary.
                    MirrorFilterValidator.validate(m.url, json)
                    val parsed =
                        try {
                            OptimizedJsonMapper.fromJsonTo<Filter>(json)
                        } catch (e: Exception) {
                            throw IllegalArgumentException(
                                "[[mirror]] filter for ${m.url} is not a valid NIP-01 filter object: $json",
                                e,
                            )
                        }
                    parsed.copy(since = null, limit = null)
                }
            val direction =
                MirrorDirection.parse(m.dir)
                    ?: throw IllegalArgumentException(
                        "[[mirror]] dir for ${m.url} must be \"down\", \"up\" or \"both\" (got \"${m.dir}\")",
                    )
            MirrorUpstream(
                url = m.url.normalizeRelayUrl(),
                trusted = m.trusted,
                backfillSeconds = m.backfill_seconds,
                filter = scope,
                direction = direction,
            )
        }
    // Never mirror ourselves — a self-URL echoes every local publish
    // back forever. Compare scheme-insensitively (ws:// vs wss:// for the
    // same host is still us) and ignoring the trailing slash. This can't
    // catch a public URL that resolves to this bind behind a proxy, nor a
    // `--port 0` autobind, so it's a guardrail against the obvious typo,
    // not a proof of non-self-reference.
    val advertisedIdentity = advertisedUrl.displayUrl()
    require(upstreams.none { it.url.displayUrl() == advertisedIdentity }) {
        "[[mirror]] must not list this relay's own URL ($advertisedUrl)"
    }
    // Resume state for the mirror catch-up, following the admin state-file
    // convention: next to the event database unless configured. Keyed to the
    // store's ACTUAL persistence, not to `database.file` being set: a
    // volatile store with a persistent coverage file would claim, on the
    // next boot, that an empty database already holds the backfill window —
    // and the mirror would never fetch it.
    val sqliteBackend =
        config.database.backend
            .trim()
            .lowercase() in StoreFactory.SQLITE_BACKEND_KEYWORDS
    val persistentLocation =
        a.opt("--db") ?: config.database.file?.takeUnless { sqliteBackend && config.database.in_memory }
    val syncCoverage =
        if (upstreams.isEmpty() || persistentLocation == null) {
            if (upstreams.isNotEmpty() && config.options.mirror_sync_state_file != null) {
                Log.w("Main") { "mirror_sync_state_file ignored: the event store is in-memory, so saved coverage would outlive the events it describes" }
            }
            null
        } else {
            SyncCoverageFile(File(config.options.mirror_sync_state_file ?: "$persistentLocation.sync-coverage.json"))
        }
    val mirror =
        if (upstreams.isEmpty()) {
            null
        } else {
            MirrorWorker(
                upstreams = upstreams,
                server = relay.server,
                // The store lets the catch-up reconcile against what we already
                // hold (download only the diff, like `strfry sync`).
                store = store,
                // Production mirrors how strfry does it: NIP-77 "sync" catch-up
                // for the historical window, then live REQ tail. Auto-falls back
                // to paged REQ for upstreams without NIP-77.
                negentropyBackfill = true,
                // Without NIP-77 the catch-up is a paged re-download; the
                // coverage bands remember what previous boots already walked.
                coverage = syncCoverage?.coverage,
            ).also { it.start() }
        }

    // Periodic query-planner statistics refresh (`PRAGMA optimize`).
    // Incremental and usually a no-op; failures are swallowed — a missed
    // refresh only means slightly staler planner stats until the next tick.
    // SQLite-only: the `optimize()` PRAGMA has no meaning for other backends,
    // so the loop only runs when the configured store is the SQLite one.
    val maintenanceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    (store as? EventStore)?.let { sqlite ->
        config.database.optimize_interval_seconds?.let { secs ->
            maintenanceScope.launch {
                while (true) {
                    delay(secs * 1000)
                    try {
                        sqlite.optimize()
                    } catch (e: CancellationException) {
                        throw e // shutdown cancelled us; don't swallow it
                    } catch (e: Exception) {
                        // A missed refresh only means slightly staler planner
                        // stats until the next tick — log and keep the loop.
                        // Errors (OOM, etc.) are NOT swallowed.
                        println("PRAGMA optimize failed: ${e.message}")
                    }
                }
            }
        }
    }

    Runtime.getRuntime().addShutdownHook(
        Thread {
            // Each step wrapped so a throw in any stage doesn't skip
            // `relay.close()` (which closes the SQLite store). The mirror
            // and maintenance go first: stop touching the store before the
            // queue and store beneath them shut down.
            runCatching { maintenanceScope.cancel() }
            runCatching { mirror?.close() }
            // After the mirror, so the final flush carries the last bands.
            runCatching { syncCoverage?.close() }
            runCatching { server.stop() }
            runCatching { relay.close() }
        },
    )

    println("geode listening on ${server.url}")
    println("NIP-11 info doc: curl -H 'Accept: application/nostr+json' http://$advertisedHost:$port$path")
    relay.relaySigner?.let { println("relay identity (NIP-11 self): ${it.pubKey}") }
    if (relay.membership) {
        println("NIP-43 membership on: members-only writes; join with a kind 28934 request carrying an invite code (NIP-86 createclaim)")
    }
    if (upstreams.isNotEmpty()) {
        val trusted = upstreams.count { it.trusted }
        println("mirroring ${upstreams.size} upstream relay(s), $trusted trusted (signature verification skipped)")
    }

    // Park the main thread; shutdown hook handles teardown.
    Thread.currentThread().join()
}

/**
 * Builds the policy stack for one connection from the config.
 *
 * Order matters — cheap rejection paths run before expensive ones:
 *   1. Per-client rate limits (a flood is turned away before anything else runs)
 *   2. AUTH (drops everything if not authenticated)
 *   3. Future-timestamp rejection
 *   4. Signature verification (most expensive — Schnorr verify)
 */
private fun composePolicy(
    config: StaticConfig,
    advertisedUrl: NormalizedRelayUrl,
    requireAuth: Boolean,
    optionalAuth: Boolean,
    verifySigs: Boolean,
    parallelVerify: Boolean,
    rateLimits: RateLimits?,
): IRelayPolicy {
    val pieces = mutableListOf<IRelayPolicy>()

    rateLimits?.let { pieces += it.policy() }

    if (requireAuth) {
        pieces += SignInPolicy(advertisedUrl)
    } else if (optionalAuth) {
        pieces += OptionalSignInPolicy(advertisedUrl)
    }

    config.options.reject_future_seconds?.let { secs ->
        pieces += RejectFutureEventsPolicy(secs)
    }

    if (verifySigs) {
        // When parallel verify is on, the IngestQueue handles EVENT
        // verification on the writer's CPU fan-out — but AUTH events
        // bypass the queue, so we still need the policy chain to
        // verify those. `VerifyAuthOnlyPolicy` does exactly that.
        pieces += if (parallelVerify) VerifyAuthOnlyPolicy else VerifyPolicy
    }

    return pieces.fold<IRelayPolicy, IRelayPolicy>(EmptyPolicy) { acc, p ->
        if (acc === EmptyPolicy) p else acc + p
    }
}

private class Args(
    private val opts: Map<String, String>,
    private val flags: Set<String>,
    /** Non-`--` operands, in order (e.g. the NDJSON files for `import`). */
    val positionals: List<String>,
) {
    fun opt(k: String) = opts[k]

    fun flag(k: String) = k in flags
}

private const val CONFIG_FLAG = "--config"
private const val NO_VERIFY_FLAG = "--no-verify"
private const val NO_SEARCH_FLAG = "--no-search"
private const val STORE_FLAG = "--store"

/**
 * Apply a `--store <backend>` CLI override on top of the parsed
 * `[database].backend`. `null` (flag absent) leaves the config untouched, so
 * the TOML value — or its `"sqlite"` default — stands.
 */
private fun StaticConfig.withStoreOverride(backend: String?): StaticConfig = if (backend == null) this else copy(database = database.copy(backend = backend))

/**
 * Boolean flags that never take a value. Listing them explicitly is what lets a
 * trailing positional survive after a flag — `import --no-verify corpus.ndjson`
 * must read `corpus.ndjson` as a file, not as `--no-verify`'s value.
 */
private val BOOLEAN_FLAGS =
    setOf("--auth", "--optional-auth", NO_VERIFY_FLAG, "--no-parallel-verify", NO_SEARCH_FLAG)

private fun parseArgs(args: Array<String>): Args {
    val opts = mutableMapOf<String, String>()
    val flags = mutableSetOf<String>()
    val positionals = mutableListOf<String>()
    var i = 0
    while (i < args.size) {
        val a = args[i]
        if (a.startsWith("--")) {
            // Support both `--key value` and `--key=value`. Splitting
            // on the first `=` lets operators paste config values that
            // happen to contain `=` (e.g. NIP-11 contact emails) by
            // using the space-separated form.
            val eq = a.indexOf('=')
            when {
                eq > 0 -> {
                    opts[a.substring(0, eq)] = a.substring(eq + 1)
                    i += 1
                }
                a in BOOLEAN_FLAGS -> {
                    flags += a
                    i += 1
                }
                else -> {
                    val next = args.getOrNull(i + 1)
                    if (next != null && !next.startsWith("--")) {
                        opts[a] = next
                        i += 2
                    } else {
                        flags += a
                        i += 1
                    }
                }
            }
        } else {
            positionals += a
            i += 1
        }
    }
    return Args(opts, flags, positionals)
}
