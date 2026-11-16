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
package com.vitorpamplona.amethyst.commons.relayClient.diagnostics

import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.ClosedMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EoseMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EventMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.NoticeMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.AuthCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.concurrent.ConcurrentMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Debug-only cold-start census: what every relay in the pool actually did, and why the ones
 * that failed, failed.
 *
 * The existing instrumentation cannot answer this. [com.vitorpamplona.quartz.nip01Core.relay.client.stats.RelayStats]
 * has counters but no dump path and no failure taxonomy; `RelaySpeedLogger` counts events per
 * second but not connection outcomes; `RelayLogger` prints one line per event, which at the
 * few-hundred-relay cardinality of the outbox model is thousands of lines to grep rather than
 * a table to read.
 *
 * What this adds: connection outcome bucketed **by cause**, REQ/EOSE/CLOSED accounting per
 * relay, and time-to-first-EOSE — so a boot can be read as "N relays served us, M were
 * refused for reason R, K were never reachable".
 *
 * Attach only in debug builds; it holds one small record per relay for the process lifetime.
 */
@OptIn(ExperimentalAtomicApi::class)
class BootRelayDiagnostics(
    val client: INostrClient,
    // 5s first: the pool is assembled and dialling well before 20s, and the early datapoint is what
    // distinguishes "slow to connect" from "connected fine, slow to serve". Affordable because the
    // rollup is now 3 INFO lines rather than 5 — the ===== banners moved to DEBUG.
    val dumpAtSeconds: List<Long> = listOf(5, 20, 45, 90),
) {
    companion object {
        const val TAG = "BootRelayDiag"

        /**
         * Buckets a connection failure by what actually went wrong. The distinction that
         * matters most here is *ours vs theirs*: a SOCKS refusal is our Tor proxy declining
         * to open a stream and says nothing about the relay, but it reaches the relay client
         * through the same path as a genuine relay failure and is charged to the relay.
         */
        fun classify(error: String): String =
            when {
                error.contains("SOCKS", ignoreCase = true) -> "tor-socks"
                error.contains("127.0.0.1") -> "tor-proxy-down"
                error.contains("UnknownHostException") -> "dns"
                error.contains("SSLHandshakeException") || error.contains("SSLPeerUnverified") -> "tls"
                error.contains("SocketTimeoutException") -> "timeout"
                error.contains("Server Misconfigured") -> "http-" + (Regex("Response: (\\d+)").find(error)?.groupValues?.get(1) ?: "?")
                error.contains("ConnectException") -> "refused"
                error.contains("Connection reset") -> "reset"
                else -> "other"
            }
    }

    class RelayRecord {
        val tentatives = AtomicInt(0)
        val opens = AtomicInt(0)
        val disconnects = AtomicInt(0)
        val reqsSent = AtomicInt(0)
        val authsSent = AtomicInt(0)
        val events = AtomicInt(0)
        val eoses = AtomicInt(0)
        val notices = AtomicInt(0)

        /** failure cause -> count, see [classify]. */
        val failures = ConcurrentMap<String, AtomicInt>()

        /** CLOSED machine-readable prefix (or "unprefixed") -> count. */
        val closed = ConcurrentMap<String, AtomicInt>()

        val firstOpenAtMs = AtomicLong(0L)
        val firstEoseAtMs = AtomicLong(0L)

        fun bump(
            map: ConcurrentMap<String, AtomicInt>,
            key: String,
        ) = map.getOrPut(key) { AtomicInt(0) }.addAndFetch(1)
    }

    private val records = ConcurrentMap<NormalizedRelayUrl, RelayRecord>()
    private val startedAtMs = TimeUtils.nowMillis()

    private fun record(url: NormalizedRelayUrl) = records.getOrPut(url) { RelayRecord() }

    private fun elapsed() = TimeUtils.nowMillis() - startedAtMs

    private val listener =
        object : RelayConnectionListener {
            override fun onConnecting(relay: IRelayClient) {
                record(relay.url).tentatives.addAndFetch(1)
            }

            override fun onConnected(
                relay: IRelayClient,
                pingMillis: Int,
                compressed: Boolean,
            ) {
                val r = record(relay.url)
                r.opens.addAndFetch(1)
                r.firstOpenAtMs.compareAndSet(0L, elapsed())
            }

            override fun onCannotConnect(
                relay: IRelayClient,
                errorMessage: String,
            ) {
                val r = record(relay.url)
                r.bump(r.failures, classify(errorMessage))
            }

            override fun onDisconnected(relay: IRelayClient) {
                record(relay.url).disconnects.addAndFetch(1)
            }

            override fun onSent(
                relay: IRelayClient,
                cmdStr: String,
                cmd: Command,
                success: Boolean,
            ) {
                val r = record(relay.url)
                when (cmd) {
                    is ReqCmd -> r.reqsSent.addAndFetch(1)
                    is AuthCmd -> r.authsSent.addAndFetch(1)
                    else -> Unit
                }
            }

            override suspend fun onIncomingMessage(
                relay: IRelayClient,
                msgStr: String,
                msg: Message,
            ) {
                val r = record(relay.url)
                when (msg) {
                    is EventMessage -> r.events.addAndFetch(1)
                    is EoseMessage -> {
                        r.eoses.addAndFetch(1)
                        r.firstEoseAtMs.compareAndSet(0L, elapsed())
                    }
                    is NoticeMessage -> r.notices.addAndFetch(1)
                    is ClosedMessage -> r.bump(r.closed, prefixOf(msg.message))
                    else -> Unit
                }
            }
        }

    /** First token of a NIP-01 machine-readable CLOSED/OK message, or "unprefixed". */
    private fun prefixOf(message: String?): String {
        val text = message?.trim().orEmpty()
        if (text.isEmpty()) return "empty"
        val head = text.substringBefore(':', "")
        return if (head.isNotEmpty() && head.length < 20 && !head.contains(' ')) head else "unprefixed"
    }

    init {
        client.addConnectionListener(listener)
        // A coroutine instead of a daemon thread: KMP-portable and finishes after
        // the last scheduled census instead of holding a parked thread.
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            var last = 0L
            dumpAtSeconds.forEach { at ->
                delay((at - last) * 1000)
                last = at
                dump(at)
            }
        }
    }

    fun detach() = client.removeConnectionListener(listener)

    /**
     * One line per relay plus a rollup. Kept to a single Log call per line so the whole census
     * survives logcat's per-tag rate limiting on a busy boot.
     *
     * The rollup goes out at INFO — it is the one boot line worth reading by default, and it
     * carries the aggregate that per-socket failure logging used to spell out a few hundred
     * times. The per-relay WASTE/SERVE tables are DEBUG: useful when you are chasing a specific
     * relay, too long (up to 45 lines a census) to sit in the default log.
     */
    fun dump(atSeconds: Long) {
        val snapshot = records.snapshot()

        val served = snapshot.filter { it.value.events.load() > 0 }
        val opened = snapshot.filter { it.value.opens.load() > 0 }
        val neverOpened = snapshot.filter { it.value.opens.load() == 0 }

        val causeTotals = mutableMapOf<String, Int>()
        val closedTotals = mutableMapOf<String, Int>()
        snapshot.values.forEach { r ->
            r.failures.snapshot().forEach { (k, v) -> causeTotals[k] = (causeTotals[k] ?: 0) + v.load() }
            r.closed.snapshot().forEach { (k, v) -> closedTotals[k] = (closedTotals[k] ?: 0) + v.load() }
        }

        Log.d(TAG) { "===== boot census @${atSeconds}s =====" }
        Log.i(TAG) {
            "census @${atSeconds}s pool=${snapshot.size} opened=${opened.size} served_events=${served.size} never_opened=${neverOpened.size} " +
                "dials=${snapshot.values.sumOf { it.tentatives.load() }} " +
                "events=${snapshot.values.sumOf { it.events.load() }} " +
                "reqs=${snapshot.values.sumOf { it.reqsSent.load() }} " +
                "auths=${snapshot.values.sumOf { it.authsSent.load() }}"
        }
        Log.i(TAG) { "census @${atSeconds}s failures_by_cause=${causeTotals.byCountDesc()}" }
        Log.i(TAG) { "census @${atSeconds}s closed_by_prefix=${closedTotals.byCountDesc()}" }

        // Relays that cost us dials and gave nothing back, worst first: the wasted-effort list.
        Log.d(TAG, "--- top wasted dials (no events received) ---")
        snapshot
            .filter { it.value.events.load() == 0 }
            .entries
            .sortedByDescending { it.value.tentatives.load() }
            .take(25)
            .forEach { (url, r) ->
                Log.d(TAG) {
                    "WASTE ${url.url} dials=${r.tentatives.load()} opens=${r.opens.load()} " +
                        "fail=[${r.failures.snapshot().entries.joinToString { "${it.key}:${it.value.load()}" }}] " +
                        "closed=[${r.closed.snapshot().entries.joinToString { "${it.key}:${it.value.load()}" }}] " +
                        "reqs=${r.reqsSent.load()} eose=${r.eoses.load()}"
                }
            }

        // The relays actually carrying the boot, so a suppression change can be checked for
        // coverage loss rather than just CLOSED reduction.
        Log.d(TAG, "--- top event providers ---")
        served.entries
            .sortedByDescending { it.value.events.load() }
            .take(20)
            .forEach { (url, r) ->
                Log.d(TAG) {
                    "SERVE ${url.url} events=${r.events.load()} reqs=${r.reqsSent.load()} eose=${r.eoses.load()} " +
                        "openMs=${r.firstOpenAtMs.load()} eoseMs=${r.firstEoseAtMs.load()} dials=${r.tentatives.load()}"
                }
            }
        Log.d(TAG) { "===== end census @${atSeconds}s =====" }
    }
}

/** Buckets rendered highest-count first — the shape both census summary lines want. */
private fun Map<String, Int>.byCountDesc() = entries.sortedByDescending { it.value }.joinToString { "${it.key}:${it.value}" }
