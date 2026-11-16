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
package com.vitorpamplona.amethyst.commons.service.resourceusage

import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.purposeOrNull
import com.vitorpamplona.amethyst.commons.service.resourceusage.UsageKeys
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.ClosedMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.CountMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EoseMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EventMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.NoticeMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CloseCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Counts relay websocket traffic into the usage ledger. Frame sizes are
 * UTF-16 char counts of the JSON payload — a close proxy for on-wire bytes
 * (relay JSON is ASCII-dominant), consistent with how RelayStats counts.
 * Excludes WS framing/compression; good enough for "which subsystem is
 * eating my data plan" comparisons.
 *
 * Beyond the byte totals this also carries the relay-churn diagnostics: a
 * per-verb split of those same bytes, a connection-lifetime histogram, and
 * per-relay failure/short-session counts. See
 * plans/2026-07-29-relay-churn-diagnostics.md for what each answers and how to
 * read them together.
 *
 * The verb split takes its name straight from the wire label the command already
 * knows (`Command.label()` / `Message.label()`), so `Σ verb == Σ msg` holds by
 * construction and a new subtype needs no change here.
 */
class RelayUsageListener(
    private val accountant: ResourceUsageAccountant,
    private val isMobile: () -> Boolean,
    private val isForeground: () -> Boolean,
    private val nowMs: () -> Long = { elapsedRealtimeMillis() },
) : RelayConnectionListener {
    /**
     * Relay -> when its current session became ready. Touched from the per-relay
     * OkHttp dispatcher threads, hence concurrent. Keyed by [NormalizedRelayUrl] to
     * match the other per-relay caches (`RelayStats`, `RelayLimitsTracker`).
     *
     * Entries are consumed on disconnect. Three ways a session escapes the map
     * unrecorded, all counted rather than prevented — see [UsageKeys.RELAY_LIFE_OVERWRITE],
     * [UsageKeys.RELAY_LIFE_ORPHAN], and [UsageKeys.relayConnects] for process death.
     */
    private val connectedSince = ConcurrentHashMap<NormalizedRelayUrl, Long>()

    /**
     * Relay -> subscription ids currently open on this connection, so a REQ that
     * replaces an in-flight subscription can be told apart from one that opens a new
     * one. Cleared on disconnect, because the relay forgets them too — every REQ
     * after a reconnect is legitimately new.
     *
     * Bounded by the live subscription count per relay (tens), not by session length.
     */
    private val openSubs = ConcurrentHashMap<NormalizedRelayUrl, MutableSet<String>>()

    /**
     * Subscription id -> the purpose that opened it, for attributing inbound frames:
     * an EVENT names only its subscription, never why the client asked for it.
     *
     * Deliberately **not** [openSubs]. Sharing one map conflated two different
     * lifetimes and lost 41 % of the download to `unattributed` in the 2026-08-02
     * reading: a CLOSE removed the id, and a disconnect dropped the whole relay's
     * map, while frames already in flight were still arriving. "Is this subscription
     * open" and "what did this subscription belong to" answer different questions and
     * expire at different times — the second stays true after the first turns false.
     *
     * Keyed by subscription id alone, without the relay. The same id is used across
     * relays for one logical subscription, so the purpose is a property of the id;
     * this also means a frame arriving after a reconnect still attributes.
     *
     * Bounded by [MAX_TRACKED_SUBS] with wholesale eviction rather than an LRU: this
     * is a diagnostic on a hot path, ids are recycled steadily, and a rare reset that
     * sends a few frames to `unattributed` is cheaper than per-frame bookkeeping.
     * `unattributed` staying small is what says the bound is generous enough.
     */
    private val subPurpose = ConcurrentHashMap<String, String>()

    /**
     * Event ids delivered recently, as the first 64 bits of the id.
     *
     * Held as a Long rather than the 64-char hex, which saves the id strings
     * themselves — but a boxed `Long` plus its map node still costs ~56 bytes an
     * entry, so a full window is ~3 MB, not the few hundred KB the raw payload
     * suggests. Worth knowing before raising [MAX_TRACKED_EVENTS] on a 512 MB-class
     * device; an unboxed open-addressed `LongArray` would be ~400 KB if it ever needs
     * to grow. 64 bits makes a collision between distinct ids negligible where a
     * 32-bit hash would not be.
     *
     * The window only needs to span the fan-out, not the session: the same event
     * arrives from every relay carrying it within seconds, so near-term memory
     * catches the duplication this measures. Cleared wholesale at [MAX_TRACKED_EVENTS]
     * for the same reason [subPurpose] is — the alternative is per-frame LRU
     * bookkeeping on the hottest path in the app. A clear undercounts duplicates that
     * straddle it, so the ratio is a floor.
     */
    private val recentEventIds = ConcurrentHashMap.newKeySet<Long>()

    /**
     * Relay -> when this dial was decided, so the pre-request cost can be separated
     * from the handshake. Consumed by whichever of `onConnected`/`onCannotConnect`
     * ends the dial, so an entry never outlives the attempt that made it.
     */
    private val dialStartedAt = ConcurrentHashMap<NormalizedRelayUrl, Long>()

    /** Notice texts already logged, so one wording costs one line however often it arrives. */
    private val loggedNotices = ConcurrentHashMap.newKeySet<String>()

    override fun onSent(
        relay: IRelayClient,
        cmdStr: String,
        cmd: Command,
        success: Boolean,
    ) {
        if (!success) return

        val bytes = cmdStr.length.toLong()
        val mobile = isMobile()
        val fg = isForeground()
        accountant.add(UsageKeys.relayMsg(mobile, fg, received = false), bytes)
        accountant.add(UsageKeys.relayVerb(cmd.label(), received = false, mobile, fg), bytes)

        when (cmd) {
            is ReqCmd -> {
                accountant.add(UsageKeys.relaySubsSent(mobile, fg), 1)

                val purpose = purposeOf(cmd)
                accountant.add(UsageKeys.relayPurposeSent(purpose), 1)
                accountant.add(UsageKeys.relayPurposeBytes(purpose), bytes)

                if (subPurpose.size >= MAX_TRACKED_SUBS) subPurpose.clear()
                subPurpose[cmd.subId] = purpose

                // Already open on this connection, so this REQ replaces a live
                // subscription rather than starting one.
                // computeIfAbsent, not getOrPut: the latter is get-then-put and two
                // threads racing the first REQ after a (re)connect would each build a
                // set, the losing put's subId vanishing with its orphaned set.
                // `sendIfConnected` deliberately calls listeners outside PoolRequests'
                // stripe lock, so same-relay concurrency here is by design.
                val known = openSubs.computeIfAbsent(relay.url) { ConcurrentHashMap.newKeySet() }
                if (!known.add(cmd.subId)) {
                    accountant.add(UsageKeys.relaySubsResent(mobile, fg), 1)
                }
                // Within the window after this relay's connect, so almost certainly
                // part of syncState's replay rather than a user action. A time
                // window because nothing on this side marks a frame as belonging to
                // it; see UsageKeys.relaySubsReplay.
                val since = connectedSince[relay.url]
                if (since != null && nowMs() - since <= UsageKeys.REPLAY_WINDOW_MS) {
                    accountant.add(UsageKeys.relaySubsReplay(mobile, fg), 1)
                }
            }
            is CloseCmd -> {
                accountant.add(UsageKeys.relaySubsClosed(mobile, fg), 1)
                openSubs[relay.url]?.remove(cmd.subId)
            }
        }
    }

    override suspend fun onIncomingMessage(
        relay: IRelayClient,
        msgStr: String,
        msg: Message,
    ) {
        val bytes = msgStr.length.toLong()
        val mobile = isMobile()
        val fg = isForeground()
        accountant.add(UsageKeys.relayMsg(mobile, fg, received = true), bytes)
        accountant.add(UsageKeys.relayVerb(msg.label(), received = true, mobile, fg), bytes)

        // Attribute the inbound side to whoever asked for it. Only frames that name a
        // subscription can be attributed; NOTICE and OK are relay-wide and are left out
        // rather than guessed at, which is why this does not reconcile to msg.rx.
        // Resolved once: the duplicate check below needs the same answer, and an
        // EVENT names only its subscription, never why the client asked for it.
        val purpose = subIdOf(msg)?.let { subPurpose[it] ?: UsageKeys.PURPOSE_UNATTRIBUTED }
        if (purpose != null) {
            accountant.add(UsageKeys.relayPurposeDown(purpose), bytes)
            accountant.add(UsageKeys.relayPurposeDownCount(purpose), 1)
        }

        if (msg is EventMessage) {
            accountant.add(UsageKeys.relayEventsSeen(mobile, fg), 1)
            idPrefix(msg.event.id)?.let { key ->
                if (recentEventIds.size >= MAX_TRACKED_EVENTS) recentEventIds.clear()
                if (!recentEventIds.add(key)) {
                    accountant.add(UsageKeys.relayEventsDup(mobile, fg), 1)
                    accountant.add(UsageKeys.relayEventsDupBytes(mobile, fg), bytes)
                    if (purpose != null) accountant.add(UsageKeys.relayPurposeDupBytes(purpose), bytes)
                }
            }
        }

        // A refused subscription arrives here and nowhere else: the NOTICE carries no
        // subscription id, so RelayReqRefusals (wired to CLOSED) never sees it.
        if (msg is NoticeMessage) {
            val reason = UsageKeys.noticeReason(msg.message)
            accountant.add(UsageKeys.relayNotice(reason), 1)
            if (reason == UsageKeys.NOTICE_UNCLASSIFIED) {
                // The counter alone cannot say whether an absent `toomanysubs` means no
                // refusals or an allowlist that misses how this relay words them.
                //
                // INFO, not DEBUG: a debug build defaults to LogLevel.INFO
                // (Amethyst.DEFAULT_LOG_LEVEL, with VERBOSE_LOGS off), so a DEBUG line
                // here is dropped before it reaches the sink and this said nothing at
                // all. Demote it once the allowlist stops needing evidence.
                //
                // One line per distinct wording rather than per frame: the ledger
                // already has the count, what is missing is the variety. Truncated and
                // capped because the text is server-controlled.
                if (loggedNotices.size < MAX_DISTINCT_NOTICES && loggedNotices.add(msg.message)) {
                    Log.i(TAG) { "Unclassified NOTICE from ${relay.url.url}: ${msg.message.take(MAX_NOTICE_LOG)}" }
                }
            }
        }
    }

    /** Dial attempts. Unlike [onCannotConnect] this really is one per dial. */
    override fun onConnecting(relay: IRelayClient) {
        accountant.add(UsageKeys.relayDials(isMobile(), isForeground()), 1)
        dialStartedAt[relay.url] = nowMs()
    }

    // Every completed (re)connection paid a TCP+TLS handshake; high daily
    // counts are the signature of reconnect churn (flaky network, aggressive
    // relay idle timeouts, Tor bootstrap loops).
    override fun onConnected(
        relay: IRelayClient,
        pingMillis: Int,
        compressed: Boolean,
    ) {
        val mobile = isMobile()
        val fg = isForeground()
        // Doubles as the lifetime histogram's denominator — one session begins here.
        accountant.add(UsageKeys.relayConnects(mobile, fg), 1)
        // Consumed unconditionally: the gap needs a handshake to subtract, but the
        // stamp has to go either way or a dial that cannot be timed leaks its entry.
        val dialedAt = dialStartedAt.remove(relay.url)
        // pingMillis is the transport's own handshake timing; <= 0 means it could
        // not measure it, and a fabricated 0 would be worse than no record.
        if (pingMillis > 0) {
            accountant.add(UsageKeys.relayHandshake(pingMillis.toLong(), mobile, fg), 1)
            if (dialedAt != null) {
                val gap = nowMs() - dialedAt - pingMillis
                if (gap >= 0) accountant.add(UsageKeys.relayDialGap(gap, mobile, fg), 1)
            }
        }

        if (connectedSince.put(relay.url, nowMs()) != null) {
            accountant.add(UsageKeys.RELAY_LIFE_OVERWRITE, 1)
        }
    }

    override fun onDisconnected(relay: IRelayClient) {
        val mobile = isMobile()
        val fg = isForeground()
        accountant.add(UsageKeys.relayDisconnects(mobile, fg), 1)

        openSubs.remove(relay.url)
        val startedAt = connectedSince.remove(relay.url)
        if (startedAt == null) {
            // A dial that never became ready, or a second disconnect for one session.
            accountant.add(UsageKeys.RELAY_LIFE_ORPHAN, 1)
            return
        }

        val elapsed = (nowMs() - startedAt).coerceAtLeast(0)
        accountant.add(UsageKeys.relayLife(elapsed, mobile, fg), 1)
    }

    /**
     * The [SubPurpose] behind a REQ, from the first filter that declares one.
     *
     * One subscription id carries one purpose in practice, so the first is the
     * subscription's. A REQ whose filters are plain [com.vitorpamplona.quartz.nip01Core.relay.filters.Filter]s
     * predates #3832's tagging and is counted separately rather than guessed at.
     */
    private fun purposeOf(cmd: ReqCmd): String =
        cmd.filters
            .firstNotNullOfOrNull { it.purposeOrNull() }
            ?.let { UsageKeys.purposeKeyPart(it) }
            ?: UsageKeys.PURPOSE_UNEXPLAINED

    /**
     * The first 64 bits of an event id, or null if it is not a well-formed id.
     *
     * Parsed in place rather than `substring(0, 16).toULongOrNull(16)`: this runs on
     * every inbound EVENT frame, and the substring would be a String plus its backing
     * array per event, thrown away immediately.
     */
    private fun idPrefix(id: String): Long? {
        if (id.length < 16) return null
        var acc = 0L
        for (i in 0 until 16) {
            val digit = Character.digit(id[i], 16)
            if (digit < 0) return null
            acc = (acc shl 4) or digit.toLong()
        }
        return acc
    }

    /** The subscription a frame belongs to, when it names one. */
    private fun subIdOf(msg: Message): String? =
        when (msg) {
            is EventMessage -> msg.subId
            is EoseMessage -> msg.subId
            is ClosedMessage -> msg.subId
            is CountMessage -> msg.queryId
            else -> null
        }

    override fun onCannotConnect(
        relay: IRelayClient,
        errorMessage: String,
    ) {
        accountant.add(UsageKeys.relayConnectFails(isMobile(), isForeground()), 1)
        // A dial that ends here never reaches onConnected, so nothing else would
        // ever consume its start stamp.
        dialStartedAt.remove(relay.url)
    }

    companion object {
        private const val TAG = "RelayUsage"

        /** NOTICE text is server-controlled; cap what reaches the log. */
        private const val MAX_NOTICE_LOG = 200

        /** Ceiling on distinct wordings held in memory; relay prose is unbounded. */
        private const val MAX_DISTINCT_NOTICES = 200

        /** Ceiling on remembered subscription-id purposes. See [subPurpose]. */
        private const val MAX_TRACKED_SUBS = 4_000

        /** Recent-event-id window. See [recentEventIds]. */
        private const val MAX_TRACKED_EVENTS = 50_000
    }
}
