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
package com.vitorpamplona.nestsclient.interop

import java.io.File
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Path

/**
 * Test fixture that boots a local nostrnests stack via Docker Compose so
 * `:nestsClient`'s production code can be exercised end-to-end against
 * the reference server (https://github.com/nostrnests/nests).
 *
 * Mirrors the pattern used by quic-kmp's `InteropRunner` against aioquic:
 *
 *   - opt-in via `-DnestsInterop=true` system property; default test runs
 *     skip via `assumeNestsInterop()` so CI without Docker stays green
 *   - clones the nostrnests repo at a pinned commit on first use, caches
 *     under `~/.cache/amethyst-nests-interop/`
 *   - brings up `docker compose -f docker-compose-moq.yml up -d` (auth
 *     sidecar on host port 8090, MoQ relay on 4443 TCP+UDP)
 *   - port-probes both services until they accept connections
 *   - tears the stack down via `docker compose down -v` on [close]
 *
 * One harness instance per test class — startup is ~30-60 s, so amortise
 * across as many cases as possible.
 */
class NostrNestsHarness private constructor(
    private val workDir: File?,
    val authBaseUrl: String,
    val moqEndpoint: String,
) : AutoCloseable {
    private var stopped = false

    override fun close() {
        if (stopped) return
        stopped = true
        // External mode: caller owns the lifecycle of moq-auth +
        // moq-relay. Nothing for us to tear down.
        val dir = workDir ?: return
        runDocker(dir, "down", "-v", "--remove-orphans")
    }

    companion object {
        /** System property gate. Tests should call [assumeNestsInterop] first. */
        const val ENABLE_PROPERTY = "nestsInterop"

        /**
         * Bypass-Docker mode. When `-DnestsInteropExternal=true`, the
         * harness doesn't try to bring up containers — it assumes the
         * caller has already started `moq-auth` on [AUTH_HOST_PORT] and
         * `moq-relay` on [MOQ_HOST_PORT] (e.g. via
         * `nests/dev-config/start-dev.sh`). Useful on machines without a
         * Docker daemon (sandboxes, restricted CI), and for fast
         * iteration since each test no longer pays the
         * `docker compose up` / `docker compose down` cost.
         *
         * The harness still port-probes + health-checks both endpoints
         * before declaring "ready", so a half-started external stack
         * fails the same way a half-started Docker stack would.
         */
        const val EXTERNAL_PROPERTY = "nestsInteropExternal"

        /** Tests that depend on the harness call this in `@BeforeTest`. */
        fun isEnabled(): Boolean = System.getProperty(ENABLE_PROPERTY) == "true"

        private fun isExternal(): Boolean = System.getProperty(EXTERNAL_PROPERTY) == "true"

        /**
         * The `nostrnests/nests` revision the harness checks out — this is
         * the repo that supplies the `moq-auth` token-minting sidecar.
         *
         * NOT currently pinned: it tracks `main`. That's a known drift risk
         * — `moq-auth`'s JWT format must stay compatible with the
         * `moq-relay` version pinned in [DEFAULT_MOQ_REVISION]
         * (`moq-relay-v0.10.10`). If nostrnests advances `moq-auth` past
         * what 0.10.10 accepts, the pair desyncs again and the relay
         * rejects every connection with `failed to decode the token`. Pin
         * this to a SHA known-compatible with 0.10.10 once one is
         * identified. Override at runtime via `-DnestsInteropRev=<sha-or-branch>`.
         */
        const val DEFAULT_REVISION = "main"

        const val AUTH_HOST_PORT = 8090
        const val MOQ_HOST_PORT = 4443

        private const val REPO_URL = "https://github.com/nostrnests/nests.git"
        private const val MOQ_REPO_URL = "https://github.com/kixelated/moq.git"

        /**
         * Pin the upstream `kixelated/moq` revision the harness builds
         * `moq-relay` from. This MUST match the version nostrnests's
         * `moq-auth` sidecar mints tokens for — otherwise the relay
         * rejects every connection with `failed to decode the token`
         * (the JWKS / JWT-signature handling drifted across releases).
         *
         * nostrnests pins `moq-relay --version 0.10.10` in their own
         * `nests-relay/Dockerfile.relay`, so 0.10.10 is the version their
         * `moq-auth` is written against. The `docker-compose-moq.yml` this
         * harness uses builds `moq-relay` from `./moq` source instead of
         * `cargo install`ing it, so we have to check out the matching tag
         * ourselves. Plain `main` builds whatever is latest (0.10.25+),
         * which moq-auth's tokens no longer satisfy.
         *
         * Override at runtime via `-DnestsInteropMoqRev=<sha-or-tag>`.
         */
        const val DEFAULT_MOQ_REVISION = "moq-relay-v0.10.10"

        private const val COMPOSE_FILE = "docker-compose-moq.yml"
        private const val PORT_READY_TIMEOUT_MS = 90_000L
        private const val PORT_PROBE_INTERVAL_MS = 500L

        /**
         * Process-level singleton instance. Once a test class brings the
         * stack up, every other test class in the same JVM run shares
         * the same containers — bringing the moq-relay Cargo build down
         * and back up between every `@BeforeClass` is both slow (~30 s
         * compile) and flaky (port-bind / network-create races leave
         * the second start in a half-broken state). Container teardown
         * is registered once with the JVM as a shutdown hook.
         */
        @Volatile private var sharedInstance: NostrNestsHarness? = null
        private val sharedLock = Any()

        /**
         * Bring the stack up if not already running, returning the
         * shared instance for this JVM run. Subsequent callers reuse
         * the same containers. Use this in @BeforeClass.
         */
        fun shared(): NostrNestsHarness {
            sharedInstance?.let { return it }
            synchronized(sharedLock) {
                sharedInstance?.let { return it }
                val instance = doStart()
                Runtime
                    .getRuntime()
                    .addShutdownHook(
                        Thread({
                            runCatching { instance.close() }
                        }, "NostrNestsHarness-shutdown"),
                    )
                sharedInstance = instance
                return instance
            }
        }

        /**
         * Bring the stack up. Caller must `close()` to tear it down — use
         * `try-with-resources` / Kotlin's `use { … }`.
         *
         * Most callers should use [shared] instead so the stack is
         * reused across test classes.
         */
        fun start(): NostrNestsHarness = doStart()

        private fun doStart(): NostrNestsHarness {
            check(isEnabled()) {
                "NostrNestsHarness.start called without -D$ENABLE_PROPERTY=true. " +
                    "Call NostrNestsHarness.assumeNestsInterop() first to skip cleanly."
            }

            if (isExternal()) return startExternal()

            val workDir = ensureRepo()
            // The compose file `build:`s `./moq` — the upstream moq-rs
            // sources — but nostrnests does NOT ship that directory; it
            // expects each developer to clone `kixelated/moq` into it.
            ensureMoqSource(workDir)
            // moq-relay needs TLS certs to bring up its WebTransport
            // listener; nostrnests ships a self-signed-cert generator
            // we run on first invocation.
            ensureDevCerts(workDir)
            // Bring everything up detached. The compose file's services log
            // to stdout; we don't tail them — failures surface via
            // port-probe timeouts.
            runDocker(workDir, "up", "-d")
            try {
                waitForPort("127.0.0.1", AUTH_HOST_PORT, PORT_READY_TIMEOUT_MS)
                // moq-auth's Node runtime opens the listen socket
                // before its handlers are wired, so the first POST that
                // arrives in the gap can RST-on-write. Wait for /health
                // to actually return 200 — that proves the request
                // pipeline is live and avoids the SocketException that
                // otherwise hits the first test of the second class.
                waitForHealth("http://127.0.0.1:$AUTH_HOST_PORT/health", PORT_READY_TIMEOUT_MS)
                // moq-relay fetches its JWK set from moq-auth at startup
                // and exits — with no retry — if that GET is refused.
                // The compose file declares no `depends_on`, so on a cold
                // `up -d` moq-relay races moq-auth's Node boot, loses,
                // logs "failed to GET JWKS … Connection refused", and
                // dies; every later QUIC handshake then fails with
                // "read loop exited (socket closed or peer closed)".
                // Now that moq-auth is confirmed healthy, (re)start
                // moq-relay: this is a no-op if it survived the race and
                // a clean restart if it didn't, so it always comes up
                // against a live auth sidecar.
                runDocker(workDir, "up", "-d", "moq-relay")
                waitForPort("127.0.0.1", MOQ_HOST_PORT, PORT_READY_TIMEOUT_MS)
            } catch (t: Throwable) {
                // Capture container state + recent logs BEFORE tearing
                // down so the failure message is actually actionable.
                // Otherwise we get "Port 8090 not ready in 90 s" with
                // zero clue whether moq-auth crashed, the build failed,
                // or something else was binding the port.
                val diagnostics = captureFailureDiagnostics(workDir)
                runCatching { runDocker(workDir, "down", "-v", "--remove-orphans") }
                throw IllegalStateException(
                    "harness start failed: ${t.message}\n--- diagnostics ---\n$diagnostics",
                    t,
                )
            }
            return NostrNestsHarness(
                workDir = workDir,
                authBaseUrl = "http://127.0.0.1:$AUTH_HOST_PORT",
                // moq-rs terminates TLS with a self-signed cert in dev —
                // production tests pair this with PermissiveCertificateValidator.
                // The path under this base is the namespace literal (per
                // moq-rs `claims.root` matching); the `:nestsClient` connect
                // helpers append `/<namespace>?jwt=<token>` themselves.
                //
                // Host MUST be `localhost`, not `127.0.0.1`: the QUIC client
                // sends the host as TLS SNI, RFC 6066 §3 forbids IP literals
                // there, and a strict relay (rustls) rejects an IP-literal SNI
                // and then has no SNI to select its dev cert on — the handshake
                // stalls. `localhost` is a valid hostname and matches the dev
                // cert moq-relay generates. The UDP socket itself still goes to
                // IPv4 loopback: QuicWebTransportFactory resolves the host with
                // an IPv4 preference (`localhost` often resolves to ::1 first,
                // and Docker's IPv6 UDP forwarding blackholes), while keeping
                // `localhost` as the SNI name.
                moqEndpoint = "https://localhost:$MOQ_HOST_PORT/",
            )
        }

        /**
         * "External" path: caller has already started moq-auth +
         * moq-relay (e.g. via `nests/dev-config/start-dev.sh` or a
         * `cargo install moq-relay` build kicked off by hand). We just
         * port-probe + health-check, then return a harness whose
         * [close] is a no-op.
         *
         * Skip Docker entirely — useful on machines without a Docker
         * daemon, and dramatically faster on developer iteration.
         */
        private fun startExternal(): NostrNestsHarness {
            try {
                waitForPort("127.0.0.1", AUTH_HOST_PORT, PORT_READY_TIMEOUT_MS)
                waitForHealth("http://127.0.0.1:$AUTH_HOST_PORT/health", PORT_READY_TIMEOUT_MS)
                // moq-relay is UDP only — the Docker compose forwarder
                // happens to open TCP on 4443 too, which is what the
                // Docker-mode probe relies on. Bare-metal moq-relay
                // doesn't, so a TCP probe fails with ConnectException
                // even though the UDP listener is healthy. Skip it
                // here; the actual QUIC handshake from the test will
                // surface a real connection problem if there is one.
            } catch (t: Throwable) {
                throw IllegalStateException(
                    "external moq-auth / moq-relay not reachable on 127.0.0.1:" +
                        "$AUTH_HOST_PORT and 127.0.0.1:$MOQ_HOST_PORT — " +
                        "did you start the stack? See nests/dev-config/start-dev.sh.\n" +
                        "Original error: ${t.message}",
                    t,
                )
            }
            return NostrNestsHarness(
                workDir = null,
                authBaseUrl = "http://127.0.0.1:$AUTH_HOST_PORT",
                // `localhost`, not `127.0.0.1` — see the doStart() return for
                // why an IP-literal host breaks the TLS SNI / cert selection.
                moqEndpoint = "https://localhost:$MOQ_HOST_PORT/",
            )
        }

        /**
         * Convenience for `@BeforeTest`: throws JUnit's `AssumptionViolated`
         * (via `org.junit.Assume`) when the property isn't set, which JUnit
         * reports as "skipped" rather than "failed". Falls back to a regular
         * exception if JUnit's Assume isn't on the classpath.
         */
        fun assumeNestsInterop() {
            if (isEnabled()) return
            val msg = "Skipping nostrnests interop test — set -D$ENABLE_PROPERTY=true to enable"
            try {
                val assume = Class.forName("org.junit.Assume")
                val assumeTrue =
                    assume.getMethod("assumeTrue", String::class.java, Boolean::class.javaPrimitiveType)
                assumeTrue.invoke(null, msg, false)
            } catch (e: java.lang.reflect.InvocationTargetException) {
                // Unwrap so JUnit sees the real AssumptionViolatedException
                // (treated as "skipped") instead of the reflection wrapper
                // (treated as "failed").
                throw e.targetException ?: e
            } catch (_: ClassNotFoundException) {
                throw IllegalStateException(msg)
            }
        }

        // ----- internals -----

        private fun cacheRoot(): Path {
            val home = System.getProperty("user.home") ?: "/tmp"
            val root = Path.of(home, ".cache", "amethyst-nests-interop")
            Files.createDirectories(root)
            return root
        }

        private fun ensureRepo(): File {
            val rev = System.getProperty("nestsInteropRev") ?: DEFAULT_REVISION
            val target = cacheRoot().resolve("nests").toFile()
            if (!target.exists()) {
                runProcess(cacheRoot().toFile(), "git", "clone", REPO_URL, "nests")
            }
            // Always fetch + checkout the requested revision so test runs
            // are reproducible even if `main` advances.
            runProcess(target, "git", "fetch", "origin", "--quiet")
            runProcess(target, "git", "checkout", "--quiet", rev)
            return target
        }

        /**
         * Clone (and refresh) the upstream `kixelated/moq` repo into
         * `nestsRepo/moq`. The nostrnests compose file `build:`s
         * `./moq` directly, but the directory is NOT shipped in the
         * nostrnests repo (and is NOT a submodule); each developer is
         * expected to set it up. We do that automatically here.
         */
        private fun ensureMoqSource(nestsRepo: File) {
            val moqDir = File(nestsRepo, "moq")
            val rev = System.getProperty("nestsInteropMoqRev") ?: DEFAULT_MOQ_REVISION
            if (!moqDir.exists()) {
                runProcess(nestsRepo, "git", "clone", MOQ_REPO_URL, "moq")
            }
            // Reproducible runs: pin to the requested revision. `--tags` so a
            // release-tag pin like `moq-relay-v0.10.10` is checkout-able even
            // on a repo first cloned before that tag existed.
            runProcess(moqDir, "git", "fetch", "origin", "--tags", "--quiet")
            runProcess(moqDir, "git", "checkout", "--quiet", rev)
        }

        /**
         * Run `dev-config/generate-certs.sh` if `dev-config/certs/`
         * doesn't already hold a chain — moq-relay's container mounts
         * that directory read-only and refuses to start without it.
         * The script is idempotent (it bails out if the certs already
         * exist), so we always invoke it; logs go through [runProcess]
         * so a failure surfaces as a clear `IllegalStateException`.
         */
        private fun ensureDevCerts(nestsRepo: File) {
            val certs = File(nestsRepo, "dev-config/certs")
            if (certs.exists() && File(certs, "fullchain.pem").exists()) return
            val script = File(nestsRepo, "dev-config/generate-certs.sh")
            check(script.exists()) {
                "expected dev-config/generate-certs.sh in the nostrnests checkout but it's missing"
            }
            // chmod +x defensively (ownerOnly = false) in case git
            // didn't preserve the bit on Windows / some CI checkouts.
            script.setExecutable(true, false)
            runProcess(File(nestsRepo, "dev-config"), "./generate-certs.sh")
        }

        private fun runDocker(
            workDir: File,
            vararg args: String,
        ) {
            runProcess(workDir, "docker", "compose", "-f", COMPOSE_FILE, *args)
        }

        private fun runProcess(
            workDir: File,
            vararg args: String,
        ) {
            val process =
                ProcessBuilder(*args)
                    .directory(workDir)
                    .redirectErrorStream(true)
                    .start()
            val output = process.inputStream.bufferedReader().readText()
            val exit = process.waitFor()
            check(exit == 0) {
                "${args.joinToString(" ")} exited with code $exit\n--- output ---\n$output"
            }
        }

        /**
         * Snapshot `docker compose ps` + the tail of each service's
         * logs into a single string for inclusion in a thrown
         * exception message. Best-effort — never throws, so it can
         * be called from a [start] catch handler without masking the
         * original error.
         */
        private fun captureFailureDiagnostics(workDir: File): String =
            buildString {
                append("== docker compose ps ==\n")
                append(captureDocker(workDir, listOf("ps")))
                for (service in listOf("moq-auth", "moq-relay", "strfry")) {
                    append("\n== docker compose logs --tail 30 $service ==\n")
                    append(captureDocker(workDir, listOf("logs", "--tail", "30", service)))
                }
            }

        /**
         * Run a docker compose subcommand and return its combined
         * stdout/stderr; on any failure return the error text instead
         * of throwing.
         */
        private fun captureDocker(
            workDir: File,
            args: List<String>,
        ): String =
            try {
                val process =
                    ProcessBuilder(listOf("docker", "compose", "-f", COMPOSE_FILE) + args)
                        .directory(workDir)
                        .redirectErrorStream(true)
                        .start()
                process.inputStream
                    .bufferedReader()
                    .readText()
                    .also { process.waitFor() }
            } catch (t: Throwable) {
                "(failed to capture: ${t.message})"
            }

        private fun waitForPort(
            host: String,
            port: Int,
            timeoutMs: Long,
        ) {
            val deadline = System.currentTimeMillis() + timeoutMs
            var lastError: Throwable? = null
            while (System.currentTimeMillis() < deadline) {
                try {
                    Socket(host, port).use { return }
                } catch (t: Throwable) {
                    lastError = t
                    Thread.sleep(PORT_PROBE_INTERVAL_MS)
                }
            }
            throw IllegalStateException(
                "Port $host:$port did not become ready within ${timeoutMs}ms",
                lastError,
            )
        }

        /**
         * Poll [healthUrl] until it returns 200. moq-auth opens its
         * listen socket before its handlers are ready; without this,
         * the first POST that arrives during that window gets a TCP
         * RST. Uses HttpURLConnection so we don't pull a heavy HTTP
         * client into the harness.
         */
        private fun waitForHealth(
            healthUrl: String,
            timeoutMs: Long,
        ) {
            val deadline = System.currentTimeMillis() + timeoutMs
            var lastError: Throwable? = null
            val url = java.net.URI(healthUrl).toURL()
            while (System.currentTimeMillis() < deadline) {
                try {
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 1_000
                    conn.readTimeout = 1_000
                    conn.requestMethod = "GET"
                    try {
                        if (conn.responseCode == 200) return
                    } finally {
                        conn.disconnect()
                    }
                } catch (t: Throwable) {
                    lastError = t
                }
                Thread.sleep(PORT_PROBE_INTERVAL_MS)
            }
            throw IllegalStateException(
                "$healthUrl did not return 200 within ${timeoutMs}ms",
                lastError,
            )
        }
    }
}
