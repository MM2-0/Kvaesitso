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

import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.quartz.nip01Core.relay.client.single.basic.BasicRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.MachineReadablePrefix.AUTH_REQUIRED
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.MachineReadablePrefix.BLOCKED
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.MachineReadablePrefix.ERROR
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.MachineReadablePrefix.INVALID
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.MachineReadablePrefix.RATE_LIMITED
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.MachineReadablePrefix.RESTRICTED
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.MachineReadablePrefix.UNSUPPORTED
import com.vitorpamplona.quartz.nip66RelayMonitor.reachability.RelayObserver
import com.vitorpamplona.quartz.utils.concurrent.ConcurrentMap

/**
 * Counter-key grammar for the resource-usage ledger. Keys are flat strings so
 * the on-disk store is schema-free — adding a counter never needs a migration.
 *
 * Dimensions:
 *  - network: `mobile` (cellular/metered) vs `wifi` (everything else)
 *  - visibility: `fg` (an activity is started) vs `bg`
 *  - direction: `rx` (downloaded) vs `tx` (uploaded)
 *
 * Counters are sizes, durations, and counts only — never content, and never a
 * full URL, and never a relay name.
 * See plans/2026-07-12-resource-usage-ledger.md.
 *
 * ## Reserved segments — read before adding a counter
 *
 * [sumMatching] matches by dot-segment *membership*, not by prefix, and every
 * headline figure in [UsageSummary] is built from it. A new key that happens to
 * contain one of these segments silently joins that sum:
 *
 *     rx  tx  msg  connms  connects  connfails  reqs  bursts  activems
 *     worker  runs  + every value in [HTTP_ROLES]
 *
 * Concretely: a key named `relay.rx.event.mobile.bg` would be counted by
 * `traffic(MOBILE, BG)` *in addition to* `relay.msg.mobile.bg.rx`, doubling the
 * reported data usage and halving the effective threshold of the
 * background-mobile-data alert. That is why the relay verb split below uses
 * `up`/`down` rather than `tx`/`rx`.
 *
 * `ResourceUsageLedgerTest.newKeysDoNotDisturbSummary` is the regression guard:
 * it asserts [UsageSummary.from] is value-identical with and without every key
 * this object can produce.
 */
object UsageKeys {
    const val MOBILE = "mobile"
    const val WIFI = "wifi"
    const val FG = "fg"
    const val BG = "bg"
    const val RX = "rx"
    const val TX = "tx"

    /** HTTP subsystems, matching IRoleBasedHttpClientBuilder's roles. */
    const val ROLE_IMAGE = "image"
    const val ROLE_VIDEO = "video"
    const val ROLE_UPLOADS = "uploads"
    const val ROLE_MONEY = "money"
    const val ROLE_NIP05 = "nip05"
    const val ROLE_PREVIEW = "preview"
    const val ROLE_PUSH = "push"

    /** Catch-all for HTTP requests that reach the shared clients without a role tag. */
    const val ROLE_OTHER = "other"

    val HTTP_ROLES = listOf(ROLE_IMAGE, ROLE_VIDEO, ROLE_UPLOADS, ROLE_MONEY, ROLE_NIP05, ROLE_PREVIEW, ROLE_PUSH, ROLE_OTHER)

    /**
     * `mobile.bg` — the network x visibility pair every counter is split by.
     *
     * Table-backed rather than interpolated: this is evaluated on every relay frame
     * and every HTTP response, and there are only four possible answers. Declared
     * first because the key tables below are built from it at class-init.
     */
    fun dim(
        mobile: Boolean,
        foreground: Boolean,
    ): String = DIMS[dimIndex(mobile, foreground)]

    private val DIMS = arrayOf("$WIFI.$BG", "$WIFI.$FG", "$MOBILE.$BG", "$MOBILE.$FG")

    private fun dimIndex(
        mobile: Boolean,
        foreground: Boolean,
    ): Int = (if (mobile) 2 else 0) or (if (foreground) 1 else 0)

    /**
     * The four `<prefix>.<dim>[.<suffix>]` keys, indexed by [dimIndex].
     *
     * Used for every `relay.*` key, because all of them are built from a relay
     * callback — per frame for [relayMsg]/[relayVerb], per dial/connect/disconnect
     * for the rest. The `net.*` builders below still interpolate: their key space is
     * role x dim x metric and they are called once per HTTP response, so the table
     * would be larger and buy less. If you add a `relay.*` counter, table it.
     */
    private fun dimKeys(
        prefix: String,
        suffix: String? = null,
    ): Array<String> {
        val tail = if (suffix == null) "" else ".$suffix"
        return Array(DIMS.size) { "$prefix.${DIMS[it]}$tail" }
    }

    /** `net.image.mobile.bg.rx` — HTTP bytes for a subsystem. */
    fun net(
        role: String,
        mobile: Boolean,
        foreground: Boolean,
        received: Boolean,
    ): String = "net.$role.${dim(mobile, foreground)}.${if (received) RX else TX}"

    /** `net.image.mobile.bg.reqs` — HTTP request count for a subsystem. */
    fun netReqs(
        role: String,
        mobile: Boolean,
        foreground: Boolean,
    ): String = "net.$role.${dim(mobile, foreground)}.reqs"

    /** `net.image.mobile.bg.activems` — wall time spent actively transferring. */
    fun netActiveMs(
        role: String,
        mobile: Boolean,
        foreground: Boolean,
    ): String = "net.$role.${dim(mobile, foreground)}.activems"

    /** `net.bursts.mobile.bg` — estimated radio wake-ups caused by HTTP traffic. */
    fun radioBursts(
        mobile: Boolean,
        foreground: Boolean,
    ): String = "net.bursts.${dim(mobile, foreground)}"

    /** `relay.msg.mobile.bg.rx` — approximate relay websocket payload bytes. */
    fun relayMsg(
        mobile: Boolean,
        foreground: Boolean,
        received: Boolean,
    ): String = (if (received) RELAY_MSG_RX else RELAY_MSG_TX)[dimIndex(mobile, foreground)]

    private val RELAY_MSG_RX = dimKeys("relay.msg", RX)
    private val RELAY_MSG_TX = dimKeys("relay.msg", TX)

    /** `relay.connms.mobile.bg` — Σ(open relay connections × elapsed ms). */
    fun relayConnMs(
        mobile: Boolean,
        foreground: Boolean,
    ): String = RELAY_CONNMS[dimIndex(mobile, foreground)]

    private val RELAY_CONNMS = dimKeys("relay.connms")

    /**
     * `relay.connects.mobile.bg` — completed relay (re)connections: each one paid a
     * TCP+TLS handshake.
     *
     * Also the [relayLife] histogram's denominator — one session begins per
     * `onConnected` — which is what makes the histogram's deficit measurable rather
     * than assumed:
     *
     *     connects − Σ life buckets − orphan = still open at report time + lost to process death
     *
     * Without that subtraction a leak, a still-open session and a session lost to a
     * background kill are indistinguishable.
     */
    fun relayConnects(
        mobile: Boolean,
        foreground: Boolean,
    ): String = RELAY_CONNECTS[dimIndex(mobile, foreground)]

    private val RELAY_CONNECTS = dimKeys("relay.connects")

    /**
     * `relay.connfails.mobile.bg` — every `onCannotConnect`.
     *
     * NOT a failed-dial count, despite the name. `BasicRelayClient.onFailure`
     * raises `onCannotConnect` with no `isReady` test, so a connection that lived
     * for ten minutes and then dropped increments both this and [relayConnects]
     * from a single dial. Use [relayDials] for the actual number of dials.
     */
    fun relayConnectFails(
        mobile: Boolean,
        foreground: Boolean,
    ): String = RELAY_CONNFAILS[dimIndex(mobile, foreground)]

    private val RELAY_CONNFAILS = dimKeys("relay.connfails")

    /**
     * `relay.dials.mobile.bg` — dial attempts, from `onConnecting`.
     *
     * Fires exactly once per dial, after the transport gate and the connect mutex
     * and before the socket is built, so this is the honest denominator that
     * [relayConnectFails] is not.
     */
    fun relayDials(
        mobile: Boolean,
        foreground: Boolean,
    ): String = RELAY_DIALS[dimIndex(mobile, foreground)]

    private val RELAY_DIALS = dimKeys("relay.dials")

    /** `relay.disc.mobile.bg` — every `onDisconnected`, whatever the cause. */
    fun relayDisconnects(
        mobile: Boolean,
        foreground: Boolean,
    ): String = RELAY_DISC[dimIndex(mobile, foreground)]

    private val RELAY_DISC = dimKeys("relay.disc")

    /**
     * `relay.subs.sent.mobile.bg` — REQ commands sent.
     *
     * A count to sit beside the `relay.verb.up.req` byte total: PR #3832 raised the
     * number of live subscriptions per relay (background accounts now subscribe too)
     * and measured refusals against nos.lol's cap of 20. Divided by [relayConnects]
     * this is REQs per connection, which is what separates "we reconnect too often"
     * from "each reconnect asks for too much" — different fixes.
     */
    fun relaySubsSent(
        mobile: Boolean,
        foreground: Boolean,
    ): String = RELAY_SUBS_SENT[dimIndex(mobile, foreground)]

    private val RELAY_SUBS_SENT = dimKeys("relay.subs.sent")

    /** `relay.subs.closed.mobile.bg` — CLOSE commands sent; sent minus closed is net subscription growth. */
    fun relaySubsClosed(
        mobile: Boolean,
        foreground: Boolean,
    ): String = RELAY_SUBS_CLOSED[dimIndex(mobile, foreground)]

    private val RELAY_SUBS_CLOSED = dimKeys("relay.subs.closed")

    /**
     * `relay.subs.replay.mobile.bg` — REQs sent within [REPLAY_WINDOW_MS] of that
     * relay's connect, i.e. the post-connect resubscribe burst.
     *
     * An estimate, not an exact split. `PoolRequests.syncState` replays every desired
     * filter from a coroutine launched at `onConnected`, but nothing on the listener
     * side marks a frame as belonging to it, so this is a time window. It is the
     * measurement behind the source report's inference that ~24 KB per connection
     * "is exactly the size of a full REQ subscription replay" — which was arithmetic
     * on a daily total, not an observation.
     */
    fun relaySubsReplay(
        mobile: Boolean,
        foreground: Boolean,
    ): String = RELAY_SUBS_REPLAY[dimIndex(mobile, foreground)]

    private val RELAY_SUBS_REPLAY = dimKeys("relay.subs.replay")

    /** How long after a connect a REQ still counts as part of the resubscribe burst. */
    const val REPLAY_WINDOW_MS = 2_000L

    /**
     * `relay.notice.toomanysubs` — NOTICE frames by reason.
     *
     * Exists because a refused subscription is otherwise invisible. Per PR #3832's
     * own known issue, `ERROR: too many concurrent REQs` arrives as a NOTICE, which
     * carries no subscription id and so never reaches `RelayReqRefusals.onRefused`
     * (wired to CLOSED only). The relay drops the REQ while the client still believes
     * it is live — it never EOSEs, its `since` never advances, and `syncState` then
     * re-requests its full backlog on every reconnect, forever. A non-trivial count
     * here means subscription pressure is a *cause* of the download volume rather
     * than a symptom of the reconnect count.
     *
     * The reason comes from a fixed allowlist, never from the relay's text. Relay
     * prose is server-controlled and this key is persisted for 30 days; `RelayObserver`
     * had to fix exactly this bug, where free-form CLOSED prose became its own tally
     * key and cardinality grew with the number of distinct sentences relays wrote.
     */
    fun relayNotice(reason: String): String = RELAY_NOTICE[reason] ?: RELAY_NOTICE.getValue(NOTICE_UNCLASSIFIED)

    const val NOTICE_TOO_MANY_SUBS = "toomanysubs"
    const val NOTICE_RATE_LIMITED = "ratelimited"
    const val NOTICE_AUTH_REQUIRED = "authrequired"
    const val NOTICE_RESTRICTED = "restricted"
    const val NOTICE_INVALID = "invalid"
    const val NOTICE_BLOCKED = "blocked"
    const val NOTICE_ERROR = "error"
    const val NOTICE_UNSUPPORTED = "unsupported"

    /** The query itself was too expensive — too many kinds/steps/filters. Observed on nostr.land, relay.layer.systems. */
    const val NOTICE_QUERY_COST = "querycost"

    /** The relay refuses REQs outright. Observed on sendit.nosflare.com. */
    const val NOTICE_REQ_REFUSED = "reqrefused"

    /** Relay chatter that costs bytes but means nothing — keepalives, per-query PERF telemetry. */
    const val NOTICE_BENIGN = "benign"

    /** Deliberately not `other`: that is an [HTTP_ROLES] value and a reserved segment. */
    const val NOTICE_UNCLASSIFIED = "unclassified"

    val NOTICE_REASONS =
        listOf(
            NOTICE_TOO_MANY_SUBS,
            NOTICE_RATE_LIMITED,
            NOTICE_AUTH_REQUIRED,
            NOTICE_RESTRICTED,
            NOTICE_INVALID,
            NOTICE_BLOCKED,
            NOTICE_ERROR,
            NOTICE_UNSUPPORTED,
            NOTICE_QUERY_COST,
            NOTICE_REQ_REFUSED,
            NOTICE_BENIGN,
            NOTICE_UNCLASSIFIED,
        )

    private val RELAY_NOTICE = NOTICE_REASONS.associateWith { "relay.notice.$it" }

    /**
     * Classifies a NOTICE into one of [NOTICE_REASONS].
     *
     * Matches on content markers rather than the NIP-01 machine-readable prefix
     * alone, because the case this exists for does not have a useful one: strfry
     * sends `ERROR: too many concurrent REQs`, whose prefix is just `error`.
     * [RelayObserver.prefixOf] is consulted for the standard prefixes it does
     * handle correctly.
     */
    fun noticeReason(message: String): String {
        val text = message.lowercase()
        // Several relays prefix a NOTICE with the subscription id it concerns
        // ("Kgo0HH: closed: too many steps"), which makes the *subscription id* the
        // machine-readable prefix and hides the real one. Try the remainder too.
        val prefix = RelayObserver.prefixOf(message)
        val inner = RelayObserver.prefixOf(message.substringAfter(':', ""))
        val prefixes = setOf(prefix, inner)
        return when {
            "too many" in text && ("req" in text || "subscription" in text || "concurrent" in text) -> NOTICE_TOO_MANY_SUBS
            // A cost refusal is still a refusal: the REQ is dropped, so it never
            // EOSEs and its `since` never advances.
            "too many" in text || "too costly" in text || "too expensive" in text -> NOTICE_QUERY_COST
            "does not accept" in text || "denied" in text || "not accepting" in text -> NOTICE_REQ_REFUSED
            "keepalive" in text || "perf:" in text -> NOTICE_BENIGN
            // Wire codes come from the enum rather than being hand-written a third
            // time (after the enum itself and the NOTICE_* key segments).
            RATE_LIMITED.code in prefixes || ("rate" in text && "limit" in text) -> NOTICE_RATE_LIMITED
            AUTH_REQUIRED.code in prefixes || ("auth" in text && "required" in text) -> NOTICE_AUTH_REQUIRED
            RESTRICTED.code in prefixes -> NOTICE_RESTRICTED
            INVALID.code in prefixes -> NOTICE_INVALID
            BLOCKED.code in prefixes -> NOTICE_BLOCKED
            UNSUPPORTED.code in prefixes -> NOTICE_UNSUPPORTED
            ERROR.code in prefixes -> NOTICE_ERROR
            else -> NOTICE_UNCLASSIFIED
        }
    }

    /**
     * The bar that decides reconnect behaviour, and so also what counts as a short
     * session: below it a disconnect keeps the growing backoff,
     * at or above it the backoff resets to 1s. (`NostrClient.KEEP_ALIVE_INTERVAL_MS`
     * is the same 60s, but it is private, so this reads the one that is public.)
     *
     * Derived rather than copied: the plan retunes `STABLE_CONNECTION_IN_SECS` once
     * the histogram is read, and a hand-written 60_000 here would silently stop
     * meaning "session that kept the backoff growing" at that point.
     * `UsageKeyHelpersTest.lifeBucketsAreHalfOpen` asserts the resulting bucket
     * labels, so a retune surfaces as a test failure rather than as a histogram that
     * quietly answers the wrong question.
     */
    const val SHORT_SESSION_MS = BasicRelayClient.STABLE_CONNECTION_IN_SECS * 1_000L

    /**
     * A half-open millisecond histogram: ascending upper [bounds], the derived
     * bucket labels, and the `<prefix>.<bucket>.<dim>` key table they index.
     *
     * Shared by all three histograms below (`relay.life`, `relay.hs`, `relay.gap`),
     * which differ only in their bounds and in whether the label reads in seconds or
     * milliseconds. Deriving the names from the bounds is what keeps a bound change
     * from leaving a label lying about it.
     */
    private class MsHistogram(
        prefix: String,
        private val bounds: LongArray,
        label: (Long) -> String,
    ) {
        init {
            // [indexOf] linear-scans for the first bound greater than the elapsed
            // time, so the bounds must ascend. One of relay.life's is derived from
            // BasicRelayClient.STABLE_CONNECTION_IN_SECS, and raising that to five
            // minutes — exactly the retune commit 2 of the churn plan contemplates —
            // would push it past the two bounds after it. The buckets between would
            // become unreachable and the labels would start lying. Fail at class-init
            // with the reason rather than as a puzzling boundary-test failure.
            require(bounds.asList() == bounds.sorted()) {
                "$prefix bounds must ascend, got ${bounds.toList()}. " +
                    "SHORT_SESSION_MS is ${SHORT_SESSION_MS}ms — reorder the bounds to match."
            }
        }

        val names = Array(bounds.size + 1) { i -> if (i < bounds.size) "lt${label(bounds[i])}" else "gte${label(bounds.last())}" }

        private val keys = Array(names.size) { dimKeys("$prefix.${names[it]}") }

        fun indexOf(ms: Long): Int {
            for (i in bounds.indices) {
                if (ms < bounds[i]) return i
            }
            return bounds.size
        }

        fun nameOf(ms: Long): String = names[indexOf(ms)]

        fun key(
            ms: Long,
            mobile: Boolean,
            foreground: Boolean,
        ): String = keys[indexOf(ms)][dimIndex(mobile, foreground)]
    }

    /**
     * Bounds for the [relayLife] histogram deliberately straddle [SHORT_SESSION_MS]:
     * a mean cannot tell a tight cluster sitting on that bar from a bimodal mix;
     * this can.
     */
    private val LIFE = MsHistogram("relay.life", longArrayOf(5_000, 30_000, SHORT_SESSION_MS, 120_000, 300_000)) { "${it / 1000}s" }

    fun lifeBucket(elapsedMs: Long): String = LIFE.nameOf(elapsedMs)

    /**
     * `relay.life.lt60s.mobile.bg` — connections that closed after living this long.
     * [relayConnects] is the denominator; see its doc for the deficit equation.
     */
    fun relayLife(
        elapsedMs: Long,
        mobile: Boolean,
        foreground: Boolean,
    ): String = LIFE.key(elapsedMs, mobile, foreground)

    /**
     * `relay.life.overwrite` — a connect arrived for a relay that already had an
     * unconsumed start stamp.
     *
     * Expected during a pool teardown: `NostrClient` runs `disconnect()` then
     * `connect()` synchronously, so a stale failure callback for the old socket
     * can land after the new socket is already open. The new stamp overwrites the
     * old, and the stale disconnect then consumes the new one — booking a
     * near-zero lifetime for a session that never ended. Bias runs toward `lt5s`,
     * so read this before reading the histogram's short buckets.
     */
    const val RELAY_LIFE_OVERWRITE = "relay.life.overwrite"

    /** `relay.life.orphan` — a disconnect with no matching start stamp. */
    const val RELAY_LIFE_ORPHAN = "relay.life.orphan"

    /**
     * `relay.verb.up.req.mobile.bg` / `relay.verb.down.eose.mobile.bg` — the
     * [relayMsg] bytes, split by wire verb. Parameterised on direction for the same
     * reason [relayMsg] is: one memo strategy, not two.
     *
     * `verb` is the command's own `label()` (`REQ`, `EVENT`, ...) lowercased, so a
     * new `Command`/`Message` subtype maps itself and nothing can land in a
     * catch-all bucket unnoticed. Deliberately `up`/`down` rather than `tx`/`rx` —
     * see the reserved-segment note above.
     *
     * Keys are memoized because this is on the per-frame path: the verb x dim space
     * is a handful of entries, so steady state is a map lookup and an array index
     * with no string building at all.
     */
    fun relayVerb(
        verb: String,
        received: Boolean,
        mobile: Boolean,
        foreground: Boolean,
    ): String =
        (if (received) VERB_DOWN_KEYS else VERB_UP_KEYS)
            .getOrPut(verb) { dimKeys("relay.verb.${if (received) DOWN else UP}.${verb.lowercase()}") }[dimIndex(mobile, foreground)]

    private const val UP = "up"
    private const val DOWN = "down"

    private val VERB_UP_KEYS = ConcurrentMap<String, Array<String>>()
    private val VERB_DOWN_KEYS = ConcurrentMap<String, Array<String>>()

    /**
     * `relay.purpose.home_feed.sent` / `.bytes` — REQ frames and REQ bytes by the
     * [SubPurpose] that asked for them.
     *
     * The counter that turns "REQ traffic is 64 % of upload" into an actionable
     * name. Purpose travels on the filter itself (PR #3832's `ExplainedFilter`,
     * which survives the `copy(since = …)` assemblers do after every EOSE), so this
     * is a read, not a new registry.
     *
     * Cardinality is the enum, so it is bounded and stable. [PURPOSE_UNEXPLAINED] is
     * its own bucket rather than folded into the enum's OTHER: a filter carrying no
     * purpose at all means an assembler #3832 did not reach, which is a different
     * fact from one that declared itself uncategorised — and if that bucket is large,
     * the attribution below cannot be trusted.
     *
     * Memoized for the same reason [relayVerb] is, and more urgently: [relayPurposeDown]
     * and [relayPurposeDownCount] both run on every inbound frame that names a
     * subscription, so interpolating would build two strings per frame on the hottest
     * path in the app. The purpose space is the enum plus the three fallbacks below.
     */
    fun relayPurposeSent(purpose: String): String = purposeKeys(purpose)[P_SENT]

    fun relayPurposeBytes(purpose: String): String = purposeKeys(purpose)[P_BYTES]

    /**
     * `relay.purpose.moderation.down` — bytes received on subscriptions opened for
     * that purpose, resolved through the subscription id the frame carries.
     *
     * The upload counters answer "who is asking"; this answers "who is being
     * answered", which is the larger number: inbound EVENT payload is ~74 % of relay
     * traffic against ~26 % outbound. Without it, a fix to the REQ churn can only be
     * credited with the upload it removes, when the interesting question is how much
     * of the download it was causing — every re-subscription can make the relay
     * re-send everything that matches.
     */
    fun relayPurposeDown(purpose: String): String = purposeKeys(purpose)[P_DOWN]

    /**
     * `relay.purpose.home_feed.downn` — inbound frames, alongside the bytes.
     *
     * Bytes alone cannot separate "many small events delivered repeatedly" from "few
     * large ones", and those want opposite fixes. With a count, `down / downn` is the
     * average frame size per purpose, and the total frame count set against
     * `crypto.verify.count` — which the cache pays once per event it accepts — bounds
     * how much of the download is the same events arriving from different relays
     * under the outbox fan-out.
     */
    fun relayPurposeDownCount(purpose: String): String = purposeKeys(purpose)[P_DOWNN]

    /**
     * `relay.purpose.user_profile.dupbytes` — of that purpose's inbound bytes, how
     * many carried an event already delivered.
     *
     * [relayEventsDupBytes] measures duplication across the whole client, which says
     * how much is wasted but not where. The seventh reading needs exactly this split:
     * `user_profile` was 57 % of download at ~17 KB per event, and whether that is
     * mostly the same events arriving from many relays or mostly distinct large ones
     * points at completely different fixes — suppress redundant delivery, or stop
     * fetching the large thing per relay.
     */
    fun relayPurposeDupBytes(purpose: String): String = purposeKeys(purpose)[P_DUPBYTES]

    private const val P_SENT = 0
    private const val P_BYTES = 1
    private const val P_DOWN = 2
    private const val P_DOWNN = 3
    private const val P_DUPBYTES = 4

    private val PURPOSE_SUFFIXES = arrayOf("sent", "bytes", "down", "downn", "dupbytes")

    private val PURPOSE_KEYS = ConcurrentMap<String, Array<String>>()

    /** The five `relay.purpose.<purpose>.*` keys, indexed by the `P_` constants above. */
    private fun purposeKeys(purpose: String): Array<String> = PURPOSE_KEYS.getOrPut(purpose) { Array(PURPOSE_SUFFIXES.size) { "relay.purpose.$purpose.${PURPOSE_SUFFIXES[it]}" } }

    /** A frame whose subscription id we never saw opened — counters wiped mid-session, or a sub from before this connection. */
    const val PURPOSE_UNATTRIBUTED = "unattributed"

    /** A REQ whose filters carry no [ExplainedFilter] purpose. */
    const val PURPOSE_UNEXPLAINED = "unexplained"

    /** The enum's own OTHER, renamed: bare `other` is an [HTTP_ROLES] value and a reserved segment. */
    const val PURPOSE_OTHER = "otherpurpose"

    /**
     * The key segment for a [SubPurpose]. The one place the enum is turned into a
     * key, so the reserved-segment rename cannot drift between the producer and the
     * test that guards it — which is exactly how it drifted the first time.
     *
     * Tabled by ordinal: this runs per REQ, and `name.lowercase()` would allocate a
     * fresh String each time for one of a dozen fixed answers.
     */
    fun purposeKeyPart(purpose: SubPurpose): String = PURPOSE_PARTS[purpose.ordinal]

    private val PURPOSE_PARTS =
        Array(SubPurpose.entries.size) {
            val purpose = SubPurpose.entries[it]
            if (purpose == SubPurpose.OTHER) PURPOSE_OTHER else purpose.name.lowercase()
        }

    /**
     * `relay.subs.resent.mobile.bg` — a REQ for a subscription id this relay already
     * has open on the current connection.
     *
     * The distinction the churn question turns on. A REQ that opens a new
     * subscription is work; a REQ that replaces one already in flight is the client
     * changing its mind, and at ~1 KB each that is pure cost. Measured against
     * [relaySubsSent] it says what fraction of the upload is re-subscription rather
     * than subscription.
     */
    fun relaySubsResent(
        mobile: Boolean,
        foreground: Boolean,
    ): String = RELAY_SUBS_RESENT[dimIndex(mobile, foreground)]

    private val RELAY_SUBS_RESENT = dimKeys("relay.subs.resent")

    /** Half-open bounds, in ms, shared by the two connect-timing histograms below. */
    private val CONNECT_BOUNDS_MS = longArrayOf(100, 500, 2_000, 10_000, 30_000)

    /**
     * `relay.hs.lt500ms.wifi.fg` — the websocket upgrade round-trip, as the
     * transport measured it.
     *
     * This is `onConnected`'s `pingMillis`, which `BasicOkHttpWebSocket` computes as
     * `receivedResponseAtMillis - sentRequestAtMillis` and which this listener
     * previously discarded. PR #3843 is the cautionary tale: `RelayObserver` derived
     * the same quantity from `onConnecting -> onConnected` instead, and on a large
     * fan-out published a 33.5 s median that was the client's own backlog rather than
     * relay latency. Those timestamps bracket the request itself, so everything
     * before it — queueing, DNS, TCP, TLS — is excluded. Zero or negative means the
     * transport could not time it, and nothing is recorded rather than a fabricated 0.
     */
    fun relayHandshake(
        ms: Long,
        mobile: Boolean,
        foreground: Boolean,
    ): String = HANDSHAKE.key(ms, mobile, foreground)

    private val HANDSHAKE = MsHistogram("relay.hs", CONNECT_BOUNDS_MS) { "${it}ms" }

    /**
     * `relay.gap.lt2000ms.wifi.fg` — everything between deciding to dial and the
     * upgrade request going out: dispatcher queueing, DNS, TCP, TLS.
     *
     * `(onConnected wall clock - onConnecting wall clock) - handshake`. This is the
     * share of connect latency the app is responsible for rather than the relay, and
     * it is what decides whether a high never-became-ready rate is relays being
     * unreachable or ~500 simultaneous dials saturating name resolution and sockets.
     * The dispatcher's own cap is not the constraint on a phone
     * (`maxRequests = 1024`, `maxRequestsPerHost = 10`), so a large value here points
     * at resolution and socket setup, not at a queue.
     */
    fun relayDialGap(
        ms: Long,
        mobile: Boolean,
        foreground: Boolean,
    ): String = DIAL_GAP.key(ms, mobile, foreground)

    private val DIAL_GAP = MsHistogram("relay.gap", CONNECT_BOUNDS_MS) { "${it}ms" }

    /**
     * `relay.events.seen.wifi.fg` — inbound EVENT frames, and of those, how many
     * carried an event id already delivered recently.
     *
     * The outbox model asks many relays for the same authors, so one event is
     * delivered once per relay that carries it. Relay download is ~65 % of all data
     * on the release build, so the duplication factor decides whether the largest
     * number in the ledger is content or repetition — a question no other counter
     * here can answer, and one [VERIFY_COUNT] only proxies (it counts what the cache
     * accepted, not what arrived, and only while dedup-before-verify holds).
     *
     * [relayEventsDupBytes] is the number that matters: bytes that arrived and were
     * already held.
     */
    fun relayEventsSeen(
        mobile: Boolean,
        foreground: Boolean,
    ): String = RELAY_EV_SEEN[dimIndex(mobile, foreground)]

    fun relayEventsDup(
        mobile: Boolean,
        foreground: Boolean,
    ): String = RELAY_EV_DUP[dimIndex(mobile, foreground)]

    fun relayEventsDupBytes(
        mobile: Boolean,
        foreground: Boolean,
    ): String = RELAY_EV_DUPB[dimIndex(mobile, foreground)]

    private val RELAY_EV_SEEN = dimKeys("relay.events.seen")
    private val RELAY_EV_DUP = dimKeys("relay.events.dup")
    private val RELAY_EV_DUPB = dimKeys("relay.events.dupbytes")

    /**
     * `relay.trigger.netid` — reconnect *decisions*, by cause, not reconnects
     * performed.
     *
     * Two reasons this is an upper bound, both of which matter when reading it:
     * `NostrClient.reconnect` emits into a debounced flow that `subscribe` /
     * `count` / `publish` / `onDisconnected` also feed very frequently, so a
     * teardown can be coalesced away before it runs; and the flow's initial value
     * fires one teardown per client construction with no trigger attributed.
     */
    fun relayTrigger(cause: String): String = "relay.trigger.$cause"

    const val TRIGGER_NETID = "netid"
    const val TRIGGER_TRANSPORT = "transport"
    const val TRIGGER_TOR_POLICY = "torpolicy"
    const val TRIGGER_CLASSIFICATION = "class"
    const val TRIGGER_COLD_START = "coldstart"
    const val TRIGGER_OFF = "off"
    const val TRIGGER_BUZZ = "buzz"

    /** `worker.scheduledPost.runs` */
    fun workerRuns(worker: String): String = "worker.$worker.runs"

    const val WAKELOCK_NOTIF_MS = "wakelock.notif.ms"
    const val WAKELOCK_NOTIF_COUNT = "wakelock.notif.count"
    const val APP_STARTS = "app.starts"

    /** Whole-process CPU time (user+system) — the honest aggregate of parsing, crypto, coroutines, and UI. */
    const val CPU_MS = "cpu.ms"

    /** Time with at least one activity STARTED — the denominator that makes the other counters interpretable. */
    const val APP_FG_MS = "app.fgms"

    /** Event signature verifications (LocalCache.justVerify). */
    const val VERIFY_COUNT = "crypto.verify.count"
    const val VERIFY_US = "crypto.verify.us"

    /** Media (video/audio) playback time — decoder + screen + streaming all at once. */
    const val MEDIA_PLAY_MS = "media.playms"

    /** NIP-13 proof-of-work mining: full-core CPU for as long as it runs. */
    const val POW_MS = "pow.ms"
    const val POW_SESSIONS = "pow.sessions"

    /** In-app (Arti) Tor: circuit crypto + directory/guard keep-alives while up; each start pays a bootstrap. */
    const val TOR_MS = "tor.ms"
    const val TOR_STARTS = "tor.starts"

    /**
     * Always-on notification relay service: uptime (the mode context for its
     * relay connections) and starts — each start beyond the first is churn
     * (watchdog alarm or auto-restart re-launching a killed service).
     */
    const val ALWAYS_ON_MS = "service.alwayson.ms"
    const val ALWAYS_ON_STARTS = "service.alwayson.starts"

    /** Calls and NIP-53 audio rooms: mic + Opus + a live media connection. */
    const val CALL_MS = "call.ms"
    const val CALL_SESSIONS = "call.sessions"
    const val NESTS_MS = "nests.ms"
    const val NESTS_SESSIONS = "nests.sessions"

    /** Time spent actively listening for GPS/location updates (geohash tagging). */
    const val LOCATION_MS = "location.ms"

    /**
     * `screen.Home.ms` — time a screen was visible while the app was in the
     * foreground. PRIVACY: only the route's base NAME is ever recorded, never
     * its navigation arguments — "Profile" is tracked, whose profile is not
     * (see ScreenTimeIntegrator.screenNameOf, which strips them).
     */
    fun screenMs(screen: String): String = "$SCREEN_PREFIX$screen.ms"

    const val SCREEN_PREFIX = "screen."

    /**
     * NIP-04/44 decryptions and encryptions through account signers. Durations
     * are only metered for local-key signers (CPU cost); external/remote
     * signer waits are IPC/network, tracked by the sign/decrypt counts alone.
     */
    const val DECRYPT_COUNT = "crypto.decrypt.count"
    const val DECRYPT_US = "crypto.decrypt.us"
    const val ENCRYPT_COUNT = "crypto.encrypt.count"
    const val ENCRYPT_US = "crypto.encrypt.us"

    /** `sign.nip46.count` — signatures by signer kind: local key, NIP-55 (Amber IPC), NIP-46 (relay round-trip). */
    fun signs(kind: String): String = "sign.$kind.count"

    const val SIGNER_LOCAL = "local"
    const val SIGNER_NIP46 = "nip46"
    const val SIGNER_NIP55 = "nip55"

    /**
     * Measured battery drain (percent points while discharging), split by
     * visibility. Not app-isolated — it's the ground truth the other counters
     * get correlated against across reports.
     */
    const val BATTERY_DRAIN_FG = "battery.drain.fg"
    const val BATTERY_DRAIN_BG = "battery.drain.bg"

    /**
     * Sums every counter whose key matches all the given dot-delimited parts.
     *
     * Scans segments in place rather than `key.split('.')`: [UsageSummary.from] makes
     * ~54 of these passes over the whole day bucket, the usage screen builds 16
     * summaries per entry on the main thread, and the churn counters roughly tripled
     * the key count — none of which can ever match (that is what
     * `noNewKeyContainsAReservedSegment` guarantees), so every split was pure waste.
     */
    fun Map<String, Long>.sumMatching(vararg parts: String): Long {
        var total = 0L
        for ((key, value) in this) {
            if (parts.all { key.hasSegment(it) }) total += value
        }
        return total
    }

    /** True when `segment` is one of this key's whole dot-delimited segments. */
    private fun String.hasSegment(segment: String): Boolean {
        var from = 0
        while (from <= length) {
            var end = indexOf('.', from)
            if (end < 0) end = length
            if (end - from == segment.length && regionMatches(from, segment, 0, segment.length)) return true
            from = end + 1
        }
        return false
    }
}
