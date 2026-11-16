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
package com.vitorpamplona.amethyst.commons.relayClient.paging

import com.vitorpamplona.amethyst.commons.model.privateChats.DmHistoryTuning
import com.vitorpamplona.amethyst.commons.util.ConcurrentSet
import com.vitorpamplona.quartz.nip01Core.relay.client.paging.RelayLoadingCursors
import com.vitorpamplona.quartz.nip01Core.relay.client.paging.RelayPagingProgress
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.concurrent.Volatile

/**
 * Atomic snapshot of a [BackwardRelayPager]'s display state. Every field is recomputed together in one
 * pass ([BackwardRelayPager.status]'s producer), so a consumer collects ONE flow and never sees a torn
 * mix (e.g. an updated [relayCount] against a still-stale [relayProgress]) or pays for several separate
 * recompositions per page settle. [BackwardRelayPager.loadingMore] is deliberately NOT folded in here: it
 * is debounced on its own timer in the load tracker, decoupled from this recompute.
 */
data class PagingStatus(
    // Nothing more reachable right now: every relay is done or stalled. See the pager doc — not "caught up".
    val exhausted: Boolean = false,
    // Relays currently fetching a page (for an "asking N relays" status line).
    val relayCount: Int = 0,
    // Not-done relays that can't be reached right now (auth CLOSE / unreachable / silent).
    val stalledCount: Int = 0,
    // Oldest `createdAt` reached across all relays (the deepest cursor), or null before any delivery.
    val reachedBack: Long? = null,
    // Per-relay window position (reached / done / stalled) — what a caller's per-relay progress UI renders.
    val relayProgress: Map<NormalizedRelayUrl, RelayPagingProgress> = emptyMap(),
)

/**
 * Reusable **per-relay backward pagination** engine: pages a set of relays back through history,
 * **one page at a time, per relay, on demand**, by `until`+`limit` ([RelayLoadingCursors]) — with each
 * relay advancing independently the moment *it* settles, never paced by the slowest one.
 *
 * This is the **single-active orchestrator** around the paging state. The state itself (the per-relay
 * [RelayLoadingCursors]) does NOT live here — the caller holds it on whatever object owns the scope, so
 * its lifetime matches that object. One orchestrator drives whichever scope is currently bound; calling
 * [bind] repoints it at that scope's cursors. This is safe as long as the caller only advances the bound
 * scope (e.g. the one the user is viewing), so a backgrounded scope produces no callbacks to mis-route.
 *
 * What it owns (all transient, recomputed on each [bind]): the in-flight + silence tracking
 * ([PerRelayLoadTracker]), the stalled-relay set, and the display flows — one atomic [status] snapshot
 * ([PagingStatus]) plus the separately-debounced [loadingMore]. The persistent cursors and the pinned
 * history floor live on the bound [RelayLoadingCursors].
 *
 * What it does NOT own (the caller supplies these — they are protocol- and framework-specific):
 *  - **Building the actual REQ filters.** The caller reads [armedRelays] + [requestedUntilFor] and
 *    assembles its own `RelayBasedFilter`s (the kinds / authors / `#p` tags differ per query).
 *  - **The subscription lifecycle.** The caller wires its `INostrClient` subscription and forwards
 *    relay callbacks here via [onEvent] / [onEose] / [onClosed] / [onCannotConnect], then re-issues
 *    its filter after [advance] / [advanceAll] return true.
 *  - **Which scope's cursors + which relays.** Supplied together by [bind].
 *  - **Whether the scope may load at all.** The `isEnabled` gate passed to [bind] (e.g. the user turned
 *    the protocol off in Settings › Messages). While it is false the pager refuses to advance and reports
 *    [PagingStatus.exhausted] with no relays, so no window-limit marker or spinner asks for pages that
 *    would never be requested. The owner calls [onEnabledChanged] when the gate flips.
 *
 * ### Done vs stalled (read [exhausted] with care)
 * A relay is **done** once it answers an empty page (gap-proof: nothing older). A relay that won't
 * answer right now — auth CLOSE, unreachable, or silent past the tracker's window — is flagged
 * **stalled** but kept (its subscription stays open; re-[advance] retries it). [exhausted] flips true
 * once every relay is *done or stalled* — "nothing more reachable right now", which is NOT the same as
 * "fully caught up". Callers that render a terminal state should split on [stalledCount]: `exhausted &&
 * stalledCount == 0` is genuinely caught up; `exhausted && stalledCount > 0` stopped early and may be
 * missing messages.
 *
 * Not internally synchronized beyond the primitives it composes; intended to be driven from one owning
 * scope with relay callbacks serialized per relay (as the relay IO layer delivers them).
 */
class BackwardRelayPager(
    // Short label for the DMPagination logs (e.g. "giftwrap.history", "convo.nip04.history").
    private val name: String,
    // Asked of every relay per page; large on purpose (a whole band in one page), and caps per-request
    // volume. A relay returning fewer is its own cap, NOT exhaustion — only an empty page ends a relay.
    val pageLimit: Int = DEFAULT_PAGE_LIMIT,
    // How far below "now" the history floor sits — paging starts here and walks backward. Defaults to
    // the shared live-tail boundary ([DmHistoryTuning]): everything newer is the always-on tail's job.
    private val liveTailSeconds: Long = DmHistoryTuning.liveTailSeconds,
) {
    private val loadTracker = PerRelayLoadTracker(name, onSilenced = ::onSilenced)

    // The active scope, set by [bind]: its persistent per-relay cursors (which live on the owning domain
    // object) and the lookup for the relay set it fans out to.
    @Volatile
    private var cursors: RelayLoadingCursors? = null

    @Volatile
    private var relaysFor: () -> Collection<NormalizedRelayUrl>? = { null }

    @Volatile
    private var isEnabled: () -> Boolean = { true }

    // Who the current binding is for (see [bind]'s `owner`), so [unbind] from an ended subscription
    // leaves a binding a newer subscription made alone.
    @Volatile
    private var boundOwner: Any? = null

    // Relays not advancing for the active scope (auth CLOSE / unreachable / silent). Transient: cleared
    // and recomputed on each [bind]; a stalled relay is kept (its sub stays open) and retried on advance.
    private val stalledRelays = ConcurrentSet<NormalizedRelayUrl>()

    /**
     * True while any relay is mid-page. Starts false (an idle engine isn't "loading"). Kept apart from
     * [status] on purpose: the load tracker debounces this flow's falling edge on its own timer, decoupled
     * from the [publishStatus] recompute, so folding it into the snapshot would miss that delayed flip.
     */
    val loadingMore: StateFlow<Boolean> = loadTracker.loading

    private val _status = MutableStateFlow(PagingStatus())

    /** One atomic snapshot of the display state (exhausted / counts / reached / per-relay progress), all
     * recomputed together in [publishStatus] so consumers collect ONE flow and never see a torn mix. */
    val status: StateFlow<PagingStatus> = _status.asStateFlow()

    // The session-pinned floor for the active scope — kept on its cursors so it persists with the scope
    // and does not drift forward on recompute (which would re-trigger an undelivered relay's loader).
    private fun floor(): Long {
        val c = cursors ?: return TimeUtils.now() - liveTailSeconds
        return c.floor ?: (TimeUtils.now() - liveTailSeconds).also { c.floor = it }
    }

    /**
     * Repoints to a scope (call on subscribe / when the active scope changes): its persistent
     * [scopeCursors] (held on the caller's scope object), the [scope] for the silence watchdog, the
     * [isEnabled] gate (false = this scope must not load at all) and the [relaysForScope] lookup. Resets the transient orchestration (in-flight, stalled) and recomputes
     * the display flows from the bound cursors — so a previously-paged scope restores its progress
     * instead of restarting.
     *
     * [owner] names the subscription this binding serves (the owner's subscription key: its user, or
     * its id), for [unbind] when that subscription ends.
     */
    fun bind(
        scopeCursors: RelayLoadingCursors,
        scope: CoroutineScope,
        isEnabled: () -> Boolean = { true },
        owner: Any = scopeCursors,
        relaysForScope: () -> Collection<NormalizedRelayUrl>?,
    ) {
        cursors = scopeCursors
        relaysFor = relaysForScope
        this.isEnabled = isEnabled
        boundOwner = owner
        loadTracker.bind(scope)
        loadTracker.reset()
        stalledRelays.clear()
        publishStatus()
    }

    /**
     * Lets go of the scope bound for [owner], when the subscription it served ended. The pager lives as
     * long as the app, and what a binding holds (the cursors, the relay lookup and the gate, which
     * capture the account or the conversation, and the account's scope) would otherwise stay reachable
     * after the account logs out or the conversation closes, until some other scope is bound. A binding
     * made since for another owner is left alone. The cursors keep their progress for the next [bind].
     */
    fun unbind(owner: Any) {
        if (boundOwner != owner) return
        boundOwner = null
        cursors = null
        relaysFor = { null }
        isEnabled = { true }
        loadTracker.unbind()
        stalledRelays.clear()
        _status.value = PagingStatus()
    }

    /**
     * Whether [c] is the currently-bound scope's cursor object. The orchestrator is single-active: it can
     * only correctly process callbacks for the bound scope. A caller whose subscription may still be alive
     * for a *just-backgrounded* scope (navigation overlap, a second pane) must gate its forwarded callbacks
     * on this — otherwise a late EOSE from scope A would move scope B's cursors. The cursor object is the
     * scope identity (one per `Chatroom`/`ChatroomList`), so reference identity is the check.
     */
    fun isBoundTo(c: RelayLoadingCursors): Boolean = c === cursors

    /**
     * Re-reads the bound scope's `isEnabled` gate after it flipped. Turning it off also drops the
     * in-flight and stalled tracking: the owner's REQ goes away with the gate, so those pages will never
     * settle and would otherwise leave the spinner up until the silence watchdog fires. The pager itself
     * keeps the cursors; turning a DM protocol off also unloads its messages, and ChatFeedToggles resets
     * the cursors then, so turning it back on pages again from the newest.
     */
    fun onEnabledChanged() {
        if (!isEnabled()) {
            loadTracker.reset()
            stalledRelays.clear()
        }
        publishStatus()
    }

    // --- Filter building support: the caller assembles the actual REQ from these. ---

    /** Relays of the active scope that have been advanced (armed) and aren't done — i.e. carry a REQ. */
    fun armedRelays(relays: Collection<NormalizedRelayUrl>): List<NormalizedRelayUrl> = cursors?.armedRelays(relays) ?: emptyList()

    /** The `until` [relay]'s next page should carry (null if it isn't armed / no scope bound). */
    fun requestedUntilFor(relay: NormalizedRelayUrl): Long? = cursors?.requestedUntilFor(relay)

    // --- Demand-driven advance (the caller re-issues its filter when these return true). ---

    /** Steps a single [relay] to its next, older page. @return true if it actually advanced. */
    fun advance(relay: NormalizedRelayUrl): Boolean {
        if (!arm(relay)) return false
        publishStatus()
        return true
    }

    /** Steps every not-done, not-in-flight relay of the active scope one page. For a scope too small to scroll. */
    fun advanceAll(): Boolean {
        if (!isEnabled()) return false
        val relays = relaysFor() ?: return false
        var any = false
        relays.forEach { if (arm(it)) any = true }
        if (any) publishStatus()
        return any
    }

    // Moves one relay's cursor to its next page and marks it in-flight. Returns false if it can't advance
    // (scope disabled, no scope bound, unknown relay, already fetching, or already done). Caller batches the recompute.
    private fun arm(relay: NormalizedRelayUrl): Boolean {
        if (!isEnabled()) return false
        val c = cursors ?: return false
        val relays = relaysFor() ?: return false
        if (relay !in relays) return false
        if (loadTracker.isInFlight(relay)) return false
        if (!c.advance(relay, floor())) return false
        stalledRelays.remove(relay)
        loadTracker.onAdvance(relay)
        return true
    }

    // --- Subscription callbacks: the owner forwards these from its SubscriptionListener. ---
    // All of them are dropped while the scope is disabled: the owner resets the cursors when it turns
    // the scope off, and a page still in flight at that moment would otherwise land on the fresh
    // cursors — an empty one marking the relay done for good, a short one pinning its reached point.

    /** Records one delivered event for [relay] (a sign of life + a page tally entry). */
    fun onEvent(
        relay: NormalizedRelayUrl,
        createdAt: Long,
    ) {
        if (!isEnabled()) return
        loadTracker.onActivity()
        cursors?.onEvent(relay, createdAt)
        stalledRelays.remove(relay)
    }

    /** Finalizes [relay]'s page on EOSE. @return true if this EOSE is the one that marked it done. */
    fun onEose(relay: NormalizedRelayUrl): Boolean {
        if (!isEnabled()) return false
        val c = cursors ?: return false
        stalledRelays.remove(relay)
        c.onEose(relay)
        loadTracker.onSettled(relay)
        val done = c.isDone(relay)
        publishStatus()
        return done
    }

    /** [relay] rejected the REQ (e.g. auth-required): settle it and flag it stalled (kept, retryable). */
    fun onClosed(
        relay: NormalizedRelayUrl,
        message: String,
    ) {
        if (!isEnabled()) return
        loadTracker.onSettled(relay)
        markStalled(relay, "CLOSED: $message")
        publishStatus()
    }

    /** [relay] is unreachable right now: settle it and flag it stalled (kept, retryable). */
    fun onCannotConnect(
        relay: NormalizedRelayUrl,
        message: String,
    ) {
        if (!isEnabled()) return
        loadTracker.onSettled(relay)
        markStalled(relay, "cannot connect: $message")
        publishStatus()
    }

    // The tracker's silence watchdog fired: the still-pending relays went quiet after their REQ. Flag them
    // stalled but kept, so the window can settle instead of hanging on a dead relay.
    private fun onSilenced(relays: Set<NormalizedRelayUrl>) {
        relays.forEach { markStalled(it, "no response (silence timeout)") }
        publishStatus()
    }

    private fun markStalled(
        relay: NormalizedRelayUrl,
        reason: String,
    ) {
        if (stalledRelays.add(relay)) Log.d(TAG) { "[$name] ${relay.url} stalled — $reason (kept, advance to retry)" }
    }

    // --- Display-state recompute (one atomic snapshot from the bound cursors). ---

    /**
     * Recomputes the whole [status] snapshot from the active scope's cursors and publishes it in one
     * emission, so consumers never see a torn mix of fields nor pay for several recompositions per settle.
     *
     * `exhausted` is computed here too: nothing more is reachable once every relay is done (empty page) or
     * stalled (unreachable) — a merely parked relay (more to load, just not advancing) keeps it false. An
     * empty / unbound scope leaves `exhausted` at its previous value (mirrors the old recompute's
     * early-return), so a transient empty relay set never flips it spuriously.
     */
    private fun publishStatus() {
        if (!isEnabled()) {
            // A disabled scope has nothing reachable and no relays to show.
            _status.value = PagingStatus(exhausted = true)
            return
        }
        val c = cursors
        val relays = relaysFor() ?: emptySet()
        val floor = floor()
        val prev = _status.value
        val exhausted =
            if (c == null || relays.isEmpty()) {
                prev.exhausted
            } else {
                relays.none { !c.isDone(it) && it !in stalledRelays }
            }
        if (exhausted && !prev.exhausted && c != null) {
            val done = relays.filter { c.isDone(it) }.map { it.url }
            val stuck = relays.filter { it in stalledRelays && !c.isDone(it) }.map { it.url }
            Log.d(TAG) { "[$name] window settled (nothing more reachable) — done=$done stalled=$stuck" }
        }
        _status.value =
            PagingStatus(
                exhausted = exhausted,
                relayCount = loadTracker.count(),
                stalledCount = relays.count { it in stalledRelays && c?.isDone(it) != true },
                reachedBack = c?.deepestReached(relays, floor),
                relayProgress =
                    relays.associateWith { relay ->
                        RelayPagingProgress(
                            reachedUntil = c?.reachedUntilFor(relay, floor) ?: floor,
                            done = c?.isDone(relay) ?: false,
                            stalled = relay in stalledRelays && c?.isDone(relay) != true,
                        )
                    },
            )
    }

    companion object {
        private const val TAG = "DMPagination"

        const val DEFAULT_PAGE_LIMIT = 10000
    }
}
