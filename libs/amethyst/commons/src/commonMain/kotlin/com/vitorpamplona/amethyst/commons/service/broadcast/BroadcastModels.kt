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
package com.vitorpamplona.amethyst.commons.service.broadcast

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * Result of a relay's response to an event publish.
 */
@Immutable
sealed class RelayResult {
    /** Relay accepted the event (OK message with success=true) */
    data object Success : RelayResult()

    /** Relay rejected the event (OK message with success=false) */
    data class Error(
        val message: String,
    ) : RelayResult()

    /** Relay did not respond within timeout */
    data object Timeout : RelayResult()

    /** Waiting for relay response */
    data object Pending : RelayResult()

    /** Retry in progress for this relay */
    data object Retrying : RelayResult()
}

/**
 * Overall status of a broadcast operation.
 */
enum class BroadcastStatus {
    /** Currently waiting for relay responses */
    IN_PROGRESS,

    /** All relays accepted the event */
    SUCCESS,

    /** Some relays accepted, some failed */
    PARTIAL,

    /** No relays accepted the event */
    FAILED,
}

/**
 * Tracks a single event broadcast to multiple relays.
 */
@Immutable
data class BroadcastEvent(
    val id: String,
    val event: Event,
    val targetRelays: List<NormalizedRelayUrl>,
    /** The author's NIP-65 outbox relays among [targetRelays]; see [isOut]. */
    val outboxRelays: Set<NormalizedRelayUrl> = emptySet(),
    val startedAt: Long = TimeUtils.now(),
    val results: Map<NormalizedRelayUrl, RelayResult> = emptyMap(),
    /** Dismissed while still sending; the banner skips it unless it comes to need attention. */
    val hidden: Boolean = false,
) {
    /** In progress while any relay has yet to answer or is being retried. */
    val status: BroadcastStatus
        get() =
            when {
                targetRelays.any { results[it].isAwaited() } -> BroadcastStatus.IN_PROGRESS
                results.values.all { it is RelayResult.Success } -> BroadcastStatus.SUCCESS
                results.values.none { it is RelayResult.Success } -> BroadcastStatus.FAILED
                else -> BroadcastStatus.PARTIAL
            }

    /** Number of relays that accepted the event */
    val successCount: Int
        get() = results.count { it.value is RelayResult.Success }

    /** Number of relays that rejected or timed out */
    val failureCount: Int
        get() = results.count { it.value is RelayResult.Error || it.value is RelayResult.Timeout }

    /** Number of relays still pending response */
    val pendingCount: Int
        get() = targetRelays.size - results.size

    /** Total number of target relays */
    val totalRelays: Int
        get() = targetRelays.size

    /** Progress as a fraction (0.0 to 1.0) */
    val progress: Float
        get() = if (totalRelays == 0) 0f else results.size.toFloat() / totalRelays

    /**
     * Whether the event has reached the relays that matter: every outbox relay
     * accepted it, or — when none of the targets is an outbox relay — any one
     * relay did. Slower relays may still be answering.
     */
    val isOut: Boolean
        get() =
            if (outboxRelays.isEmpty()) {
                successCount > 0
            } else {
                outboxRelays.all { results[it] is RelayResult.Success }
            }

    /**
     * Whether the user should be told: an outbox relay rejected the event or
     * timed out and is not being retried, or — when none of the targets is an
     * outbox relay — every relay answered and none accepted. Known as soon as
     * it happens, without waiting for the other relays.
     */
    val needsAttention: Boolean
        get() =
            if (outboxRelays.isEmpty()) {
                status == BroadcastStatus.FAILED
            } else {
                outboxRelays.any { results[it].isFailure() }
            }

    /** List of relays that failed and are not currently retrying */
    val failedRelays: List<NormalizedRelayUrl>
        get() =
            results
                .filter {
                    (it.value is RelayResult.Error || it.value is RelayResult.Timeout) &&
                        it.value !is RelayResult.Retrying
                }.keys
                .toList()

    /** List of relays currently being retried */
    val retryingRelays: List<NormalizedRelayUrl>
        get() = results.filter { it.value is RelayResult.Retrying }.keys.toList()

    /** Creates a copy with an updated relay result */
    fun withResult(
        relay: NormalizedRelayUrl,
        result: RelayResult,
    ): BroadcastEvent = copy(results = results + (relay to result))
}

private fun RelayResult?.isAwaited() = this == null || this is RelayResult.Pending || this is RelayResult.Retrying

private fun RelayResult?.isFailure() = this is RelayResult.Error || this is RelayResult.Timeout

/**
 * The broadcast banner may hide on its own only once every broadcast it shows
 * is out; anything else (still sending, or an outbox relay failed) stays up.
 */
fun Collection<BroadcastEvent>.canAutoDismiss(): Boolean = isNotEmpty() && all { it.isOut }

/**
 * Dismisses [ids] from the banner. A broadcast with a result to show — out, or
 * needing attention — is dropped, since the user has seen it; one still sending
 * is only hidden, so a later failure can bring it back.
 */
fun List<BroadcastEvent>.hiding(ids: Set<String>): ImmutableList<BroadcastEvent> =
    mapNotNull {
        when {
            it.id !in ids -> it
            it.isOut || it.needsAttention -> null
            else -> it.copy(hidden = true)
        }
    }.toImmutableList()

/**
 * Writes the tracker's copy of a broadcast back into the list. The copy never
 * saw a [hiding], so the listed [BroadcastEvent.hidden] is kept until the
 * broadcast needs attention; a hidden broadcast that is out is dropped.
 */
fun List<BroadcastEvent>.replacing(updated: BroadcastEvent): ImmutableList<BroadcastEvent> =
    mapNotNull {
        if (it.id != updated.id) {
            it
        } else {
            val merged = updated.copy(hidden = it.hidden && !updated.needsAttention)
            if (merged.hidden && merged.isOut) null else merged
        }
    }.toImmutableList()
