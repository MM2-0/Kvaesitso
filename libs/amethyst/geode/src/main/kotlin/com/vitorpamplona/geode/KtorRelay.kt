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

import com.vitorpamplona.geode.server.ClientAddresses
import com.vitorpamplona.geode.server.HttpCommandSettings
import com.vitorpamplona.geode.server.Nip11HttpRoute
import com.vitorpamplona.geode.server.Nip86HttpRoute
import com.vitorpamplona.geode.server.NipFEHttpRoute
import com.vitorpamplona.geode.server.RelayHttpExtension
import com.vitorpamplona.geode.server.WebSocketSessionPump
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.NoticeMessage
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.toHttp
import com.vitorpamplona.quartz.nip01Core.relay.server.RelaySession
import com.vitorpamplona.quartz.nip86RelayManagement.server.Nip86HttpHandler
import com.vitorpamplona.quartz.nipFERelayOverHttp.HttpRelayHandler
import io.ktor.server.application.install
import io.ktor.server.application.serverConfig
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.get
import io.ktor.server.routing.options
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap

/**
 * Hosts a [RelayEngine] over a real `ws://` endpoint backed by Ktor + CIO.
 *
 * Use this when something other than the in-process
 * [com.vitorpamplona.quartz.nip01Core.relay.server.inprocess.InProcessWebSocket]
 * needs to talk to the relay — Android instrumented tests, the `cli`
 * tooling, external clients, or a standalone "run a Nostr relay"
 * process.
 *
 * For unit-test wiring inside a single JVM, prefer [InProcessRelays] + the
 * in-process socket — same protocol, no socket overhead.
 *
 * Lifecycle:
 * ```
 * val server = KtorRelay(RelayEngine(url = ...)).start()
 * println("listening on ${server.url}")
 * // ... do stuff ...
 * server.stop()
 * ```
 */
class KtorRelay(
    val relay: RelayEngine,
    val host: String = "127.0.0.1",
    /** Pass 0 to let the OS pick a free port. Read [url] after [start] to learn it. */
    val port: Int = 0,
    val path: String = "/",
    /**
     * Ktor CIO acceptor-thread count. `null` keeps Ktor's default.
     * Lift on machines with many cores when targeting 10k+
     * concurrent connections — see `[network]` config docs.
     */
    val connectionGroupSize: Int? = null,
    /** Ktor CIO worker-thread count. `null` keeps Ktor's default. */
    val workerGroupSize: Int? = null,
    /** Ktor CIO call-handling thread count. `null` keeps Ktor's default. */
    val callGroupSize: Int? = null,
    /**
     * NIP-FE: relay commands POSTed to the relay's URL, beside NIP-86. On by default; null turns them
     * off, every POST going to NIP-86 again (and the operator should then drop `FE` from NIP-11).
     */
    val httpCommands: HttpCommandSettings? = HttpCommandSettings(),
    /**
     * Who a websocket or NIP-FE request counts against for per-client limits (rate limits,
     * NIP-FE admission): the peer, or the client a trusted reverse proxy reports. Kept even
     * when [httpCommands] is off, since websockets need it too.
     */
    val clientAddresses: ClientAddresses =
        ClientAddresses(
            httpCommands?.trustedProxies ?: emptySet(),
            httpCommands?.clientAddressHeader ?: HttpCommandSettings().clientAddressHeader,
        ),
    /**
     * Largest WebSocket frame the relay accepts; a bigger one closes the socket with 1009. Ktor
     * allocates a frame's buffer as soon as its header names the length, so without a cap a single
     * header claiming 2 GB costs that much memory before any payload arrives.
     */
    val maxFrameSize: Long = DEFAULT_MAX_FRAME_SIZE,
    /** More routes on the relay's port, beside its own (a dev GRASP git host: [com.vitorpamplona.geode.grasp.GraspGitHost]). */
    val extensions: List<RelayHttpExtension> = emptyList(),
) {
    companion object {
        /** Room for a large contact list or long-form article; far below what could exhaust the heap. */
        const val DEFAULT_MAX_FRAME_SIZE: Long = 1024 * 1024
    }

    /**
     * NIP-86 HTTP adapter. Wraps the engine's [RelayEngine.nip86Server]
     * with the NIP-98 HTTP flow ([Nip86HttpHandler]) and binds it to
     * Ktor's request lifecycle. The admin pubkey allow-list lives on
     * the engine, not here — turning admin on is a relay-level
     * decision, not a transport-level one.
     *
     * The NIP-98 binding URL is derived from `relay.url` via [toHttp]
     * because NIP-86 mandates it: admin requests target the same URI
     * as the WebSocket endpoint, just with `http(s)://` instead of
     * `ws(s)://`. This makes accidental misconfiguration impossible —
     * the operator's configured `[info].relay_url` is the single
     * source of truth.
     *
     * Always wired: when the engine's admin allow-list is empty, the
     * handler runs the same flow and the allow-list check fails with
     * [Nip86HttpHandler.Response.NotAdmin] → 403. No "admin enabled?"
     * branch.
     */
    private val nip86Route =
        Nip86HttpRoute(
            handler = Nip86HttpHandler(server = relay.nip86Server, publicUrl = relay.url.toHttp()),
        )

    private val nip11Route = Nip11HttpRoute(liveJson = { relay.info.json })

    /**
     * NIP-FE. Each request runs on its own session of the same engine, so the websocket's policies
     * and limits apply. A NIP-98 token must name `relay.url` read as http(s), or one of the
     * configured alternate URLs (a .onion).
     */
    private val nipFERoute =
        httpCommands?.let { settings ->
            val origins = (listOf(relay.url) + settings.alternateUrls).map { it.toHttp() }
            NipFEHttpRoute(
                handler = HttpRelayHandler(relay.server, origins = { origins }, deadline = settings.deadline),
                settings = settings,
                clients = clientAddresses,
            )
        }

    private var engine: CIOApplicationEngine? = null
    private var resolvedPort: Int = -1

    /**
     * Set when [stop] begins. Once true, the WebSocket handler refuses
     * new upgrades — Ktor's `engine.stop` will eventually do this too,
     * but Ktor's grace window means new connections can land between
     * `notifyShutdown` and the actual port-close, missing the NOTICE
     * we just sent to existing clients.
     */
    @Volatile
    private var shuttingDown: Boolean = false

    /**
     * Active client sessions, registered when their WebSocket handler
     * runs and removed on disconnect. Exposed (read-only) so [stop] can
     * NOTICE every connected client during graceful drain, and so tests
     * can assert lifecycle bookkeeping.
     */
    private val activeSessions: MutableSet<RelaySession> = ConcurrentHashMap.newKeySet()

    /** Number of WebSocket sessions currently connected to the server. */
    val activeSessionCount: Int get() = activeSessions.size

    /** `ws://host:port/path` — only valid after [start]. */
    val url: String
        get() {
            check(resolvedPort != -1) { "Server not started" }
            return "ws://$host:$resolvedPort$path"
        }

    /**
     * Binds the Ktor engine. Returns once the engine reports ready, so
     * [url] is safe to read on the very next line.
     */
    fun start(): KtorRelay {
        // Snapshot the constructor-supplied overrides into locals so
        // the `configure` lambda below can assign to its receiver
        // without the names colliding with outer properties.
        val connGrp = connectionGroupSize
        val workGrp = workerGroupSize
        val callGrp = callGroupSize
        val bindHost = host
        val bindPort = port
        val server =
            embeddedServer(
                factory = CIO,
                rootConfig =
                    serverConfig {
                        module {
                            install(WebSockets) { maxFrameSize = this@KtorRelay.maxFrameSize }
                            routing {
                                // NIP-11: GET on the relay URL with Accept:
                                // application/nostr+json returns the relay info doc.
                                // We mount this *before* the webSocket route so Ktor
                                // serves NIP-11 for plain HTTP GETs and only upgrades
                                // to a WebSocket when the request is a WS upgrade.
                                get(path) {
                                    nip11Route.handle(call)
                                }
                                // Two POSTs share the relay URL, told apart by Content-Type:
                                //  - NIP-86: application/nostr+json+rpc with a NIP-98 signed
                                //    Authorization header → JSON-RPC dispatch. Always mounted; an
                                //    empty admin allow-list just means every call fails it (403).
                                //  - NIP-FE: anything else is one REQ/COUNT/EVENT frame, answered
                                //    as NDJSON in the socket's own frames.
                                post(path) {
                                    val commands = nipFERoute
                                    if (commands != null && commands.isCommand(call)) commands.handle(call) else nip86Route.handle(call)
                                }
                                nipFERoute?.let { route ->
                                    options(path) { route.preflight(call) }
                                }
                                extensions.forEach { it.install(this) }
                                webSocket(path) {
                                    if (shuttingDown) {
                                        // Just return — Ktor closes the WS for us.
                                        return@webSocket
                                    }
                                    WebSocketSessionPump(this).pump(
                                        server = relay.server,
                                        registerSession = activeSessions::add,
                                        unregisterSession = activeSessions::remove,
                                        clientAddress = clientAddresses.of(call),
                                    )
                                }
                            }
                        }
                    },
                configure = {
                    connector {
                        host = bindHost
                        port = bindPort
                    }
                    // Keep Ktor defaults unless the operator overrode
                    // them — Ktor's per-CPU sizing is sensible for
                    // most deployments, and over-threading hurts L1/L2
                    // locality at low connection counts.
                    connGrp?.let { connectionGroupSize = it }
                    workGrp?.let { workerGroupSize = it }
                    callGrp?.let { callGroupSize = it }
                },
            )
        server.start(wait = false)
        engine = server.engine
        // Ktor 3.x made resolvedConnectors() suspend. We block here so
        // start() returns synchronously with [url] readable on the next line.
        resolvedPort =
            runBlocking {
                server.engine
                    .resolvedConnectors()
                    .first()
                    .port
            }
        return this
    }

    /**
     * Graceful shutdown. Safe to call multiple times.
     *
     * 1. Sends a NOTICE("closing: …") to every currently-connected
     *    client so well-behaved clients know to reconnect later.
     * 2. Stops the Ktor engine: rejects new connections immediately,
     *    then waits up to [gracePeriodMillis] for active WebSocket
     *    handlers to finish whatever they're processing (so an in-flight
     *    `EVENT` lands its `OK` reply before the socket dies). After
     *    the grace window, in-progress handlers are cancelled and the
     *    engine waits up to [timeoutMillis] - [gracePeriodMillis] for
     *    that cancellation to complete.
     *
     * Defaults to 5 s grace / 10 s total — generous enough that a
     * SQLite write + reply round-trip can land for typical event
     * sizes. Override either with a tighter budget if your operator
     * knows their workload.
     */
    fun stop(
        gracePeriodMillis: Long = 5_000,
        timeoutMillis: Long = 10_000,
    ) {
        val e = engine ?: return
        // Order: (1) refuse new connections so they don't slip in and
        // miss the NOTICE; (2) NOTICE every existing session so
        // well-behaved clients reconnect later; (3) hand off to Ktor
        // for the grace + timeout dance.
        shuttingDown = true
        notifyShutdown()
        e.stop(gracePeriodMillis, timeoutMillis)
        engine = null
        resolvedPort = -1
    }

    /**
     * Best-effort NOTICE to every active client. Failures are
     * swallowed — a flaky socket on its way out is exactly the case
     * where a NOTICE will fail anyway, and the client's read of the
     * close frame is the authoritative shutdown signal.
     */
    private fun notifyShutdown() {
        val notice = NoticeMessage("closing: relay is shutting down — please reconnect later")
        activeSessions.forEach { session ->
            runCatching { session.send(notice) }
        }
    }
}
