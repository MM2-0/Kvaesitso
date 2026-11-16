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
package com.vitorpamplona.geode.config

import cc.ekblad.toml.decode
import cc.ekblad.toml.model.TomlValue
import cc.ekblad.toml.tomlMapper
import com.vitorpamplona.geode.RelayInfo
import com.vitorpamplona.geode.server.HttpCommandSettings
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.RateLimiter
import com.vitorpamplona.quartz.nip11RelayInfo.Nip11RelayInformation
import java.io.File
import kotlin.time.Duration.Companion.seconds

/**
 * Operator-facing **boot-time** configuration. Parsed once from a TOML
 * file at startup and treated as immutable thereafter — anything that
 * needs to change while the relay is running lives in [RuntimeConfig].
 *
 * Section layout matches nostr-rs-relay's `config.toml` so existing
 * configs can be ported with little churn. Every section is optional;
 * CLI flags override file values where both exist.
 */
data class StaticConfig(
    val info: InfoSection = InfoSection(),
    val network: NetworkSection = NetworkSection(),
    val database: DatabaseSection = DatabaseSection(),
    val options: OptionsSection = OptionsSection(),
    val authorization: AuthorizationSection = AuthorizationSection(),
    val admin: AdminSection = AdminSection(),
    val identity: IdentitySection = IdentitySection(),
    val membership: MembershipSection = MembershipSection(),
    val negentropy: NegentropySection = NegentropySection(),
    val http: HttpSection = HttpSection(),
    val rate_limits: RateLimitsSection = RateLimitsSection(),
    /** `[[mirror]]` entries — upstream relays this relay streams from. */
    val mirror: List<MirrorSection> = emptyList(),
) {
    fun resolveInfo(
        fullTextSearch: Boolean = true,
        httpCommands: Boolean = http.enabled,
    ): RelayInfo =
        RelayInfo(
            Nip11RelayInformation(
                name = info.name ?: RelayInfo.NAME,
                description = info.description ?: RelayInfo.DESCRIPTION,
                pubkey = info.pubkey,
                contact = info.contact,
                icon = info.icon,
                software = info.software ?: RelayInfo.SOFTWARE,
                version = info.version ?: RelayInfo.VERSION,
                supported_nips =
                    info.supported_nips
                        // An explicit [info] nips list is operator-authoritative;
                        // the default list stays honest about search and HTTP commands.
                        ?: RelayInfo.SUPPORTED_NIPS.filter { (fullTextSearch || it != "50") && (httpCommands || it != "FE") },
                privacy_policy = info.privacy_policy,
                terms_of_service = info.terms_of_service,
                relay_countries = info.relay_countries,
                language_tags = info.language_tags,
                tags = info.tags,
            ),
        )

    data class InfoSection(
        val relay_url: String? = null,
        val name: String? = null,
        val description: String? = null,
        val pubkey: String? = null,
        val contact: String? = null,
        val icon: String? = null,
        val software: String? = null,
        val version: String? = null,
        /** NIP numbers, and the hex-named NIPs as strings: `[1, 11, 42, "FE"]`. */
        val supported_nips: List<String>? = null,
        val privacy_policy: String? = null,
        val terms_of_service: String? = null,
        val relay_countries: List<String>? = null,
        val language_tags: List<String>? = null,
        val tags: List<String>? = null,
    )

    /**
     * Bind config + Ktor CIO thread-pool sizes. The three `*_size`
     * fields default to Ktor's per-CPU sizing; lift them on big-VM
     * deployments targeting 10k+ concurrent connections.
     */
    data class NetworkSection(
        val host: String = "0.0.0.0",
        val port: Int = 7447,
        val path: String = "/",
        val connection_group_size: Int? = null,
        val worker_group_size: Int? = null,
        val call_group_size: Int? = null,
    )

    data class DatabaseSection(
        /**
         * Which [com.vitorpamplona.quartz.nip01Core.store.IEventStore]
         * implementation backs the relay. Recognised keywords (case-
         * insensitive):
         *
         *  - `"sqlite"` (default): quartz's SQLite-backed
         *    [com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore] —
         *    honours every `[database]` knob below ([in_memory]/[file],
         *    [readers], [mmap_size], …). The right choice for real traffic.
         *  - `"fs"`: quartz's filesystem
         *    [com.vitorpamplona.quartz.nip01Core.store.fs.FsEventStore],
         *    one JSON file per event under the directory named by [file]
         *    (which becomes a directory, not a db file, for this backend).
         *    Human-inspectable with `cat`/`jq`; the SQLite-only knobs are
         *    ignored.
         *
         * Any other value is treated as the fully-qualified class name of a
         * custom `IEventStore` on the classpath, instantiated reflectively —
         * see [com.vitorpamplona.geode.StoreFactory]. This is the "plug in
         * any implementation" escape hatch: the class must implement
         * `IEventStore` and expose one of the public constructors
         * `(NormalizedRelayUrl?, IndexingStrategy)`, `(NormalizedRelayUrl?)`,
         * or `()`.
         */
        val backend: String = "sqlite",
        /** True keeps an in-memory SQLite db (default — events vanish on restart). */
        val in_memory: Boolean = true,
        val file: String? = null,
        /**
         * Reader-connection pool size. `null` keeps quartz's default (4).
         * Only meaningful for file-backed stores; in-memory databases
         * share the single writer connection regardless.
         */
        val readers: Int? = null,
        /**
         * `PRAGMA mmap_size` in bytes, e.g. `268435456` for 256 MiB.
         * Maps the database file into memory so reads skip the pread
         * syscall + page-cache copy. `null` keeps SQLite's default (off).
         */
        val mmap_size: Long? = null,
        /**
         * `PRAGMA temp_store = MEMORY` — keeps sort/temp b-trees for
         * large queries in RAM instead of temp files.
         */
        val temp_store_memory: Boolean = false,
        /**
         * Refresh query-planner statistics (`PRAGMA analysis_limit;
         * PRAGMA optimize`) every this many seconds. Incremental and
         * usually a no-op, but keeps the planner from drifting onto the
         * wrong index as the corpus grows/changes shape. `null` = never.
         */
        val optimize_interval_seconds: Long? = null,
    )

    data class OptionsSection(
        val reject_future_seconds: Int? = null,
        val require_auth: Boolean = false,
        /**
         * Advertise NIP-42 AUTH without requiring it: the relay sends the
         * challenge and records clients that authenticate, but EVENT/REQ/COUNT
         * still work for clients that never do. Ignored when [require_auth] is
         * true (mandatory AUTH already sends the challenge).
         */
        val optional_auth: Boolean = false,
        /**
         * Defaults to `true`: any relay accepting real traffic should
         * verify Schnorr signatures, and verifying-by-default closes
         * the footgun of forgetting the flag. Set false only for
         * trusted-input scenarios (test fixtures, mirror replays).
         */
        val verify_signatures: Boolean = true,
        /** CPU fan-out verification in the IngestQueue. No-op when [verify_signatures] is false. */
        val parallel_verify: Boolean = true,
        /**
         * NIP-50 full-text search. On by default. When off, no FTS index
         * is created or maintained (inserts skip tokenization — a
         * measurable share of ingest cost), NIP-11 stops advertising
         * NIP-50, and REQ filters carrying a `search` term match nothing.
         * Turn off when search isn't needed, or to compare against relays
         * that don't offer NIP-50 at all (strfry, for example).
         */
        val full_text_search: Boolean = true,
        /**
         * Where the mirror catch-up's resume state lives: the per-upstream
         * `created_at` coverage bands (quartz's `SyncCoverage`). Without it
         * every restart re-syncs each upstream's whole backfill window —
         * a full re-download for an upstream without NIP-77. Defaults to
         * `<database file>.sync-coverage.json` when the store is
         * file-backed; an in-memory store keeps no resume state (bands
         * only pay off across restarts).
         */
        val mirror_sync_state_file: String? = null,
    )

    /**
     * NIP-77 negentropy tuning. Defaults track strfry (`hoytech/strfry`)
     * so a Geode relay accepts the same workload shape and exchanges
     * the same NEG-MSG round-trip size:
     *
     *  - [frame_size_limit]: strfry's hard-coded `Negentropy ne(storage, 500'000)`.
     *  - [max_sync_events]: strfry's `relay__negentropy__maxSyncEvents`;
     *    NEG-OPEN over this returns `["NEG-ERR", "<subId>", "blocked: too many query results"]`.
     *  - [max_sessions_per_connection]: NEG sessions held by one connection;
     *    overflow returns NOTICE `"too many concurrent NEG requests"`.
     */
    data class NegentropySection(
        val frame_size_limit: Long = 500_000L,
        val max_sync_events: Int = 1_000_000,
        val max_sessions_per_connection: Int = 200,
        /**
         * Keep an always-current in-memory `(created_at, id)` set so
         * full-corpus NEG-OPENs skip the table scan + seal (strfry
         * parity). ~140 B per stored event of heap; on by default. Only
         * built once the first full-corpus NEG-OPEN arrives, and only
         * when the corpus fits `max_sync_events` (an over-cap corpus
         * answers NEG-ERR from a capped scan instead).
         */
        val live_index: Boolean = true,
    )

    /**
     * NIP-FE: relay commands over HTTP — one REQ, COUNT or EVENT frame POSTed to the relay's URL,
     * answered as NDJSON in the socket's own frames. Each request is its own connection, so the
     * concurrency caps here stand in for the websocket's per-connection limits.
     */
    data class HttpSection(
        val enabled: Boolean = true,
        /** Requests running at once across all clients; over it, 503. 0 = no limit. */
        val max_concurrent_requests: Int = 256,
        /** Requests one client address may run at once; over it, 429. 0 = no limit. */
        val max_requests_per_client: Int = 16,
        /** How long a request body may take to arrive before the request is dropped with 408. */
        val body_timeout_seconds: Long = 10,
        /** How long one answer may run before it ends on a `CLOSED` line. */
        val deadline_seconds: Long = 30,
        /** Largest request body read; larger is 413. */
        val max_body_bytes: Int = 512 * 1024,
        /** Gzip streamed answers when the client sends `Accept-Encoding: gzip`, flushed line by line. */
        val gzip: Boolean = true,
        /** `Retry-After` on the relay's own 429 and 503. */
        val retry_after_seconds: Int = 1,
        /**
         * Other `ws(s)://` URLs this relay is reachable at (a `.onion` beside the clearnet name).
         * A NIP-98 token's `u` may name any of them, read as http(s), or `[info].relay_url`.
         */
        val alternate_urls: List<String> = emptyList(),
        /**
         * Addresses of the reverse proxies in front of the relay. Only from these peers is
         * [client_address_header] believed when counting a client's requests.
         */
        val trusted_proxies: List<String> = emptyList(),
        val client_address_header: String = "X-Forwarded-For",
    ) {
        /** The transport settings, or null when commands over HTTP are off. */
        fun toSettings(): HttpCommandSettings? =
            if (!enabled) {
                null
            } else {
                HttpCommandSettings(
                    maxConcurrent = max_concurrent_requests,
                    maxPerClient = max_requests_per_client,
                    bodyTimeout = body_timeout_seconds.seconds,
                    deadline = deadline_seconds.seconds,
                    maxBodyBytes = max_body_bytes,
                    compress = gzip,
                    retryAfterSeconds = retry_after_seconds,
                    alternateUrls = alternate_urls.map { it.normalizeRelayUrl() },
                    trustedProxies = trusted_proxies.toSet(),
                    clientAddressHeader = client_address_header,
                )
            }
    }

    /**
     * Per-client command rates, counted across all of a client's websockets and NIP-FE requests
     * (the client is its address, or what a trusted proxy reports; see `[http].trusted_proxies`).
     * Over a rate, the command is refused as NIP-01 `rate-limited:` with the back-off in
     * milliseconds as the frame's `params` — `["OK", id, false, "rate-limited: …", "1500"]` —
     * so a client that waits it out gets through. 0 turns a rate off (the default).
     *
     *  - [events_per_minute] / [event_burst]: EVENTs. AUTH is never limited.
     *  - [reqs_per_minute] / [req_burst]: REQs, COUNTs and NEG-OPENs. CLOSE is never limited.
     *
     * A burst of 0 means "the per-minute rate", so a quiet client may spend a minute's worth at
     * once and then one every `60 / rate` seconds.
     */
    data class RateLimitsSection(
        val events_per_minute: Int = 0,
        val event_burst: Int = 0,
        val reqs_per_minute: Int = 0,
        val req_burst: Int = 0,
    ) {
        /** The limiters, built once and shared by every connection; null when every rate is off. */
        fun toRateLimits(): RateLimits? {
            val events = limiter(events_per_minute, event_burst)
            val reqs = limiter(reqs_per_minute, req_burst)
            return if (events == null && reqs == null) null else RateLimits(events, reqs)
        }

        private fun limiter(
            perMinute: Int,
            burst: Int,
        ): RateLimiter? = if (perMinute <= 0) null else RateLimiter(perMinute, if (burst > 0) burst else perMinute)
    }

    data class AuthorizationSection(
        val pubkey_whitelist: List<String> = emptyList(),
        val pubkey_blacklist: List<String> = emptyList(),
        val kind_whitelist: List<Int> = emptyList(),
        val kind_blacklist: List<Int> = emptyList(),
    )

    /**
     * One upstream relay to mirror, declared as a `[[mirror]]` TOML array
     * entry. The relay dials [url] itself, subscribes to everything newer
     * than `now - backfill_seconds`, and ingests the stream through the
     * same group-commit writer as client publishes (reconnects and
     * re-subscribes automatically).
     *
     * [trusted] is the relay-to-relay trust switch (strfry's model):
     * `true` skips Schnorr signature verification for events arriving on
     * this connection — sound only when the upstream verifies its own
     * ingest, which is why it defaults to `false` (mirror-but-verify).
     * The identity being trusted is the URL this relay dialed (TLS-
     * authenticated for `wss://`), never anything a peer claims.
     */
    data class MirrorSection(
        val url: String,
        val trusted: Boolean = false,
        /** How far back the initial subscription reaches. 0 = live-only. */
        val backfill_seconds: Long = 0L,
        /**
         * Optional NIP-01 filter as a JSON object string (strfry-router
         * parity), e.g. `'{"kinds":[0,1,3],"#t":["nostr"]}'`. Scopes what
         * this upstream is asked for AND what it is allowed to deliver —
         * every received event is re-checked against it before ingest, so
         * even a trusted upstream can't push events outside the declared
         * scope. `since` is managed by the mirror (see [backfill_seconds])
         * and `limit` is transport-level, so both are ignored if present.
         * Omitted = mirror everything. For several disjoint filters, add
         * several `[[mirror]]` entries with the same url.
         */
        val filter: String? = null,
        /**
         * Flow direction, strfry-router's `dir`: `"down"` (pull from the
         * upstream — the default), `"up"` (push this relay's matching
         * events to it), or `"both"`. Both-way mirrors suppress echoes
         * (an event pulled down is not pushed straight back).
         */
        val dir: String = "down",
    )

    /**
     * NIP-86 admin. [pubkeys] non-empty opens the POST endpoint at the
     * relay path; only NIP-98 tokens signed by these pubkeys dispatch.
     * The NIP-98 URL binding uses `[info].relay_url` with the scheme
     * swapped (`ws(s)://` → `http(s)://`) per NIP-86 — set
     * `[info].relay_url` to the canonical public URL when behind TLS
     * termination or a reverse proxy.
     */
    data class AdminSection(
        val pubkeys: List<String> = emptyList(),
        /**
         * JSON snapshot path for [RuntimeConfig] (ban lists + live
         * NIP-11 doc). Convention: place next to the SQLite event file,
         * e.g. `events.db` ↔ `events.db.admin.json`.
         */
        val state_file: String? = null,
    )

    /**
     * The relay's own Nostr identity — the NIP-11 `self` key that signs
     * relay-authored events (NIP-43 membership lists, roles, add/remove).
     * Resolved by [RelayIdentity.resolve]:
     *
     *  - [secret_key]: the key itself, `nsec1…` or 64-char hex. Wins over
     *    [secret_key_file].
     *  - [secret_key_file]: a file holding the key (nsec or hex). Created
     *    with a freshly generated key (owner-only permissions) when it
     *    doesn't exist yet.
     *
     * Neither set: no identity, unless [MembershipSection.enabled] needs
     * one — then the key lives in `<[AdminSection.state_file]>.relay-key`
     * (generated on first boot), or, with no state file either, in memory
     * only (a new identity every restart, with a warning).
     */
    data class IdentitySection(
        val secret_key: String? = null,
        val secret_key_file: String? = null,
    )

    /**
     * NIP-43 relay membership. [enabled] turns the relay members-only: the
     * NIP-86 pubkey allow list becomes the member list (and gates writes
     * even while empty), kind 28934 join requests carrying an invite code
     * from NIP-86 `createclaim` add members, kind 28936 leave requests
     * remove them, and the relay publishes signed kind 13534 / 33534 /
     * 8000 / 8001 events. Needs the relay identity ([IdentitySection]).
     * [request_window_seconds] bounds how far a join/leave request's
     * `created_at` may drift from the relay's clock.
     */
    data class MembershipSection(
        val enabled: Boolean = false,
        val request_window_seconds: Long = 300,
    )

    /**
     * Boot-time sanity check for values the TOML types can't constrain.
     * Throws [IllegalArgumentException] (fail-loud at startup) rather
     * than letting a nonsensical knob degrade the running relay — a zero
     * reader pool hangs every query, a non-positive optimize interval
     * busy-loops the writer. Call once after parsing, before building
     * the store.
     */
    fun validate() {
        require(http.max_concurrent_requests >= 0) { "[http].max_concurrent_requests must be >= 0 (0 = no limit), got ${http.max_concurrent_requests}" }
        require(http.max_requests_per_client >= 0) { "[http].max_requests_per_client must be >= 0 (0 = no limit), got ${http.max_requests_per_client}" }
        require(http.body_timeout_seconds > 0) { "[http].body_timeout_seconds must be > 0, got ${http.body_timeout_seconds}" }
        require(http.deadline_seconds > 0) { "[http].deadline_seconds must be > 0, got ${http.deadline_seconds}" }
        require(http.max_body_bytes > 0) { "[http].max_body_bytes must be > 0, got ${http.max_body_bytes}" }
        require(rate_limits.events_per_minute >= 0) { "[rate_limits].events_per_minute must be >= 0 (0 = no limit), got ${rate_limits.events_per_minute}" }
        require(rate_limits.event_burst >= 0) { "[rate_limits].event_burst must be >= 0 (0 = events_per_minute), got ${rate_limits.event_burst}" }
        require(rate_limits.reqs_per_minute >= 0) { "[rate_limits].reqs_per_minute must be >= 0 (0 = no limit), got ${rate_limits.reqs_per_minute}" }
        require(rate_limits.req_burst >= 0) { "[rate_limits].req_burst must be >= 0 (0 = reqs_per_minute), got ${rate_limits.req_burst}" }
        require(http.retry_after_seconds >= 0) { "[http].retry_after_seconds must be >= 0, got ${http.retry_after_seconds}" }
        database.readers?.let {
            require(it >= 1) { "[database].readers must be >= 1 (got $it); a 0/negative pool can never answer a query" }
        }
        require(membership.request_window_seconds > 0) {
            "[membership].request_window_seconds must be > 0 (got ${membership.request_window_seconds})"
        }
        database.optimize_interval_seconds?.let {
            require(it > 0) {
                "[database].optimize_interval_seconds must be > 0 (got $it); a non-positive interval busy-loops PRAGMA optimize under the writer mutex"
            }
        }
    }

    companion object {
        private val mapper =
            tomlMapper {
                // `supported_nips = [1, 11, "FE"]`: numbered NIPs are integers, hex-named ones strings.
                decoder { it: TomlValue.Integer -> it.value.toString() }
            }

        fun fromToml(toml: String): StaticConfig = mapper.decode<StaticConfig>(toml)

        fun fromFile(file: File): StaticConfig = mapper.decode<StaticConfig>(file.toPath())

        /**
         * URL the relay advertises in NIP-11 and NIP-42 challenges:
         * `info.relay_url` if set, else built from `network.host:port/path`
         * with `0.0.0.0` rewritten to `127.0.0.1`.
         */
        fun advertisedUrl(config: StaticConfig): NormalizedRelayUrl = (config.info.relay_url ?: defaultUrl(config.network)).normalizeRelayUrl()

        private fun defaultUrl(net: NetworkSection): String {
            val host = if (net.host == "0.0.0.0") "127.0.0.1" else net.host
            return "ws://$host:${net.port}${net.path}"
        }
    }
}
