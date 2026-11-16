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
package com.vitorpamplona.amethyst.commons.wot

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateMap
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Friends-of-friends trust score computed from the active user's follow
 * graph. For every pubkey X the score is the count of accounts in the
 * active user's follow set who also follow X.
 *
 * ## Reactivity model
 *
 * Scores are exposed via a Compose-observable [SnapshotStateMap]. Consumers
 * that read a **single key** (`scores[pubkey]`) recompose only when that
 * key changes — this is `SnapshotStateMap`'s built-in per-key observation
 * and applies whether or not the writer wraps in a snapshot block.
 * Consumers that iterate the map or read `size` recompose on **any**
 * mutation.
 *
 * The writer wraps each op in [Snapshot.withMutableSnapshot] to *coalesce*
 * an op's writes into a single Compose commit — so a Kind3 op that
 * touches N reverse-index targets emits one invalidation, not N. It does
 * not confer additional per-key isolation on top of `SnapshotStateMap`'s
 * own semantics.
 *
 * ## Concurrency
 *
 * All internal state is mutated from a single writer coroutine
 * ([writerLoop]) on [writerDispatcher] (default [Dispatchers.IO]), so
 * concurrent [applyKind3] / [onFollowSetChange] / [markReadyOnce] calls
 * from different threads are serialized without extra locking.
 *
 * ## Lifecycle
 *
 * Call [close] on account switch / logout so the writer coroutine exits
 * and the ops channel is released. Post-close ops are silently dropped.
 */
@Stable
class WoTService(
    private val scope: CoroutineScope,
    /** Dispatcher for the internal writer coroutine. Tests override with `Dispatchers.Unconfined` for synchronous behavior. */
    private val writerDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AutoCloseable {
    /**
     * Sparse per-pubkey score map. Entries with count 0 are removed
     * (not stored as 0) to keep the Compose subscriber tracking tight.
     * Callers should read as `scores[pubkey] ?: 0`.
     */
    private val _scores: SnapshotStateMap<HexKey, Int> = mutableStateMapOf()
    val scores: SnapshotStateMap<HexKey, Int> get() = _scores

    // Reverse index: target pubkey → set of my-follows who follow them.
    private val reverseIndex = HashMap<HexKey, MutableSet<HexKey>>()

    // Per-follower cached follow set (excluding self / follower itself).
    // Enables diff-based updates when a follower republishes their kind-3.
    private val perFollowerSnapshot = HashMap<HexKey, Set<HexKey>>()

    private var myFollows: Set<HexKey> = emptySet()
    private var selfPubkey: HexKey? = null
    private var readyMarked = false
    private var disabled = false

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _isDisabled = MutableStateFlow(false)

    /**
     * True when the active user's follow set exceeds [MAX_FOLLOWS] and WoT
     * scoring has been shut off. Callers that dispatch the batch kind-3
     * REQ must gate on this — a disabled service silently accepts and
     * ignores all subsequent [applyKind3] calls, so a caller that keeps
     * flooding kind-3s wastes bandwidth for nothing.
     */
    val isDisabled: StateFlow<Boolean> = _isDisabled.asStateFlow()

    private val ops = Channel<Op>(capacity = Channel.UNLIMITED)

    init {
        scope.launch(writerDispatcher) { writerLoop() }
    }

    /** Update the active user's follow set (and self pubkey). */
    fun onFollowSetChange(
        newFollows: Set<HexKey>,
        newSelf: HexKey?,
    ) {
        ops.trySend(Op.FollowSet(newFollows, newSelf))
    }

    /**
     * Ingest a kind-3 event for a followed pubkey. Ignored when the
     * event's author isn't in the current follow set. Follow lists are
     * capped at [MAX_FOLLOWS_PER_EVENT] to bound CPU cost against a
     * hostile publisher.
     */
    fun applyKind3(
        follower: HexKey,
        follows: Set<HexKey>,
    ) {
        val bounded =
            if (follows.size > MAX_FOLLOWS_PER_EVENT) {
                follows.take(MAX_FOLLOWS_PER_EVENT).toSet()
            } else {
                follows
            }
        ops.trySend(Op.Kind3(follower, bounded))
    }

    /**
     * Mark the service as ready to render badges. Idempotent — subsequent
     * calls are no-ops. Trigger from the first EOSE on the batch kind-3
     * REQ, or from a startup-timeout fallback, whichever fires first.
     */
    fun markReadyOnce() {
        ops.trySend(Op.MarkReady)
    }

    /** Clear all state. Used on logout / account switch. */
    fun clear() {
        ops.trySend(Op.Clear)
    }

    /**
     * Returns a plain [Map] snapshot of current scores for headless
     * callers (e.g. the amy CLI) that don't run inside a Compose
     * composition. O(N) copy from the underlying [SnapshotStateMap].
     */
    fun scoresSnapshot(): Map<HexKey, Int> = HashMap(_scores)

    private sealed interface Op {
        data class FollowSet(
            val newFollows: Set<HexKey>,
            val newSelf: HexKey?,
        ) : Op

        data class Kind3(
            val follower: HexKey,
            val follows: Set<HexKey>,
        ) : Op

        data object MarkReady : Op

        data object Clear : Op
    }

    private suspend fun writerLoop() {
        for (op in ops) {
            Snapshot.withMutableSnapshot {
                when (op) {
                    is Op.FollowSet -> handleFollowSet(op.newFollows, op.newSelf)
                    is Op.Kind3 -> handleKind3(op.follower, op.follows)
                    Op.MarkReady -> handleMarkReady()
                    Op.Clear -> handleClear()
                }
            }
        }
    }

    private fun handleFollowSet(
        newFollows: Set<HexKey>,
        newSelf: HexKey?,
    ) {
        // Guardrail — massive follow lists don't produce a useful WoT signal.
        // Do this BEFORE assigning myFollows so applyKind3's `follower in
        // myFollows` gate doesn't accidentally credit anyone once the
        // caller keeps pumping kind-3s in (a caller that fails to gate on
        // isDisabled would otherwise fully repopulate reverseIndex/_scores
        // and defeat the guardrail — see PR #3483 review finding 2).
        if (newFollows.size > MAX_FOLLOWS) {
            reverseIndex.clear()
            perFollowerSnapshot.clear()
            _scores.clear()
            myFollows = emptySet()
            selfPubkey = newSelf
            disabled = true
            _isDisabled.value = true
            handleMarkReady()
            return
        }

        val removed = myFollows - newFollows
        myFollows = newFollows
        selfPubkey = newSelf
        // Follow set is back within limits (or was already) — re-enable if
        // we had previously flipped disabled=true.
        if (disabled) {
            disabled = false
            _isDisabled.value = false
        }

        // Uncredit any follower we're no longer following.
        removed.forEach { follower ->
            val prevFollows = perFollowerSnapshot.remove(follower) ?: return@forEach
            prevFollows.forEach { target ->
                val set = reverseIndex[target] ?: return@forEach
                set.remove(follower)
                if (set.isEmpty()) reverseIndex.remove(target)
                updateScore(target)
            }
        }
        // Added followers will be credited when their kind-3 arrives via applyKind3.
    }

    private fun handleKind3(
        follower: HexKey,
        follows: Set<HexKey>,
    ) {
        if (disabled) return
        if (follower !in myFollows) return

        val old = perFollowerSnapshot[follower] ?: emptySet()
        val excluded = setOfNotNull(follower, selfPubkey)
        val effective = follows - excluded
        val added = effective - old
        val removed = old - effective
        perFollowerSnapshot[follower] = effective

        added.forEach { target ->
            reverseIndex.getOrPut(target) { hashSetOf() }.add(follower)
            updateScore(target)
        }
        removed.forEach { target ->
            val set = reverseIndex[target] ?: return@forEach
            set.remove(follower)
            if (set.isEmpty()) reverseIndex.remove(target)
            updateScore(target)
        }
    }

    private fun handleMarkReady() {
        if (!readyMarked) {
            readyMarked = true
            _isReady.value = true
        }
    }

    private fun handleClear() {
        reverseIndex.clear()
        perFollowerSnapshot.clear()
        _scores.clear()
        myFollows = emptySet()
        selfPubkey = null
        readyMarked = false
        _isReady.value = false
        disabled = false
        _isDisabled.value = false
    }

    /**
     * Cancel the writer coroutine and release the ops channel. Call from
     * account-switch / logout paths. Post-close [applyKind3] / [onFollowSetChange]
     * / [markReadyOnce] / [clear] calls are silently dropped (the `trySend`
     * on a closed [Channel] fails without throwing).
     *
     * Idempotent; safe to call multiple times.
     */
    override fun close() {
        ops.close()
    }

    private fun updateScore(target: HexKey) {
        val n = reverseIndex[target]?.size ?: 0
        if (n > 0) _scores[target] = n else _scores.remove(target)
    }

    companion object {
        /** Skip WoT entirely for accounts following more than this many pubkeys. */
        const val MAX_FOLLOWS = 2000

        /** Cap follows per kind-3 event to bound CPU cost against a hostile publisher. */
        const val MAX_FOLLOWS_PER_EVENT = 5000
    }
}
