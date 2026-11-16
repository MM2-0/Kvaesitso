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
package com.vitorpamplona.geode.interop

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.File
import java.net.ServerSocket
import java.net.Socket
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Reciprocal interop test: Geode's NIP-77 client driving a real
 * `strfry` instance.
 *
 * **Opt-in.** Skipped unless `STRFRY_BIN` env var (or
 * `-Dstrfry.bin=...`) points at a `strfry` binary. When unset, the
 * test prints a `[skip]` line and returns. Mirrors the gate
 * `LoadBenchmark` uses for `-DrunLoadBenchmark`:
 *
 *   STRFRY_BIN=/usr/local/bin/strfry ./gradlew :geode:test \
 *       --tests "*GeodeVsStrfryNegentropySyncTest*"
 *
 * The strfry process is booted on a free loopback port with a fresh
 * temp LMDB dir. We feed events into it via the NIP-01 EVENT wire
 * (no `strfry import`), then run [InteropSyncDriver] against it.
 * Strfry's `RelayNegentropy.cpp` answers the same NIP-77 wire we test
 * against Geode — passing both is byte-shape interop, not just
 * "passes our own tests".
 */
class GeodeVsStrfryNegentropySyncTest {
    private val strfryBin: String? =
        System.getenv("STRFRY_BIN") ?: System.getProperty("strfry.bin")
    private val enabled = strfryBin != null

    private lateinit var strfryDir: File
    private var strfryProcess: Process? = null
    private val httpClient by lazy { OkHttpClient.Builder().build() }

    @AfterTest
    fun teardown() {
        strfryProcess?.destroy()
        strfryProcess?.waitFor()
        if (::strfryDir.isInitialized) strfryDir.deleteRecursively()
    }

    /**
     * Boots a strfry instance in a temp LMDB dir on a free port.
     * Writes the smallest config strfry accepts, starts the
     * subprocess, and polls until the WebSocket port is reachable.
     *
     * The config is intentionally minimal — strfry's defaults
     * (negentropy on, sane limits) are what we want to test against.
     * Adding speculative config keys risks failing on schema drift.
     */
    private fun startStrfry(): String {
        val port = ServerSocket(0).use { it.localPort }
        strfryDir = createTempDirectory(prefix = "strfry-interop-").toFile()
        val configFile = File(strfryDir, "strfry.conf")
        configFile.writeText(
            """
            db = "${strfryDir.absolutePath}/strfry-db"
            relay {
                bind = "127.0.0.1"
                port = $port
                nofiles = 0
            }
            """.trimIndent(),
        )
        File(strfryDir, "strfry-db").mkdirs()
        strfryProcess =
            ProcessBuilder(strfryBin, "--config", configFile.absolutePath, "relay")
                .redirectErrorStream(true)
                .redirectOutput(File(strfryDir, "strfry.log"))
                .start()

        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            runCatching {
                Socket("127.0.0.1", port).close()
                return "ws://127.0.0.1:$port/"
            }
            Thread.sleep(100)
        }
        throw IllegalStateException(
            "strfry did not start within 5s; log: " +
                File(strfryDir, "strfry.log").readText(),
        )
    }

    private fun makeEvents(count: Int): List<Event> {
        val signer = NostrSignerSync(KeyPair())
        val now = 1_700_000_000L
        return List(count) { i ->
            signer.sign(TextNoteEvent.build("strfry-interop-$i", createdAt = now + i))
        }
    }

    /**
     * Push every event in [events] to the relay at [wsUrl] over a
     * one-shot WebSocket, waiting for an `OK` response per event.
     *
     * Used to seed strfry from the same `Event` objects Geode's
     * `Relay.preload` accepts — that way both sides start from a
     * byte-identical corpus.
     */
    private suspend fun publishToStrfry(
        wsUrl: String,
        events: List<Event>,
    ) {
        val incoming = Channel<String>(UNLIMITED)
        val ws =
            httpClient.newWebSocket(
                Request.Builder().url(wsUrl.replace("ws://", "http://")).build(),
                object : WebSocketListener() {
                    override fun onMessage(
                        webSocket: WebSocket,
                        text: String,
                    ) {
                        incoming.trySend(text)
                    }

                    override fun onFailure(
                        webSocket: WebSocket,
                        t: Throwable,
                        response: Response?,
                    ) {
                        incoming.close(t)
                    }
                },
            )
        try {
            for (e in events) {
                check(ws.send("""["EVENT",${OptimizedJsonMapper.toJson(e)}]""")) {
                    "publish to strfry failed"
                }
            }
            var oks = 0
            withTimeout(30_000) {
                while (oks < events.size) {
                    if (incoming.receive().startsWith("[\"OK\"")) oks++
                }
            }
        } finally {
            ws.close(1000, "preload-done")
        }
    }

    @Test
    fun geodeReconcilesAgainstStrfryRelay() =
        runBlocking {
            if (!enabled) {
                println("[skip] GeodeVsStrfryNegentropySyncTest — set STRFRY_BIN=/path/to/strfry to enable")
                return@runBlocking
            }
            val strfryWs = startStrfry()

            // Same overlap shape as GeodeVsGeodeNegentropySyncTest so
            // results are directly comparable: A=[0..14], local=[5..19].
            val all = makeEvents(20)
            val strfryEvents = all.subList(0, 15)
            val localEvents = all.subList(5, 20)

            publishToStrfry(strfryWs, strfryEvents)

            val filter = Filter(kinds = listOf(1))

            // The wire we care about: kmp-negentropy (client) talking
            // to strfry's `Negentropy ne(storage, 500'000)` (server).
            // Symmetric difference must match the Geode-vs-Geode case.
            val res = InteropSyncDriver(httpClient).negotiate(strfryWs, filter, localEvents)
            assertNull(res.error, "Geode↔strfry NEG must not error: ${res.error}")
            assertEquals(
                strfryEvents.subList(0, 5).map { it.id }.toSet(),
                res.needIds,
                "client should NEED [0..4] from strfry",
            )
            assertEquals(
                localEvents.subList(10, 15).map { it.id }.toSet(),
                res.haveIds,
                "client should announce HAVE for [15..19]",
            )
            assertTrue(res.rounds <= 16, "expected ≤16 NEG-MSG rounds, got ${res.rounds}")
        }
}
