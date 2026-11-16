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
package com.vitorpamplona.amethyst.commons.service.pow

import kotlinx.serialization.Serializable

/**
 * Durable record of a template mining job so a post survives process death:
 * everything needed to re-mine and re-send with no lambda captured — the
 * unsigned template plus a flat replay descriptor the platform layer turns
 * back into the right sign+broadcast call.
 *
 * Replay types are interpreted by the platform restorer (see the Android
 * `PowJobRestorer`): [REPLAY_BROADCAST] signs and broadcasts to the computed
 * outbox relays, [REPLAY_RELAYS] publishes to [relayUrls], [REPLAY_SCHEDULE]
 * signs and parks the event in the scheduled-post store for [publishAtSec].
 */
@Serializable
data class PersistedPoWJob(
    val id: String,
    val accountPubkey: String,
    val kind: Int,
    val difficulty: Int,
    /** Unsigned template for the template replay types; empty for [REPLAY_WRAPS]. */
    val templateJson: String,
    val replayType: String,
    val relayUrls: List<String> = emptyList(),
    /** Extra pre-signed events to broadcast; for [REPLAY_WRAPS], the signed seals. */
    val extraEventsJson: List<String> = emptyList(),
    val publishAtSec: Long? = null,
    /** [REPLAY_WRAPS]: recipient of each seal in [extraEventsJson], same order. */
    val recipientPubkeys: List<String> = emptyList(),
    /** [REPLAY_WRAPS]: expiration delta to stamp on each wrap. */
    val wrapExpirationDelta: Long? = null,
    val createdAtSec: Long = 0,
) {
    companion object {
        const val REPLAY_BROADCAST = "broadcast"
        const val REPLAY_RELAYS = "relays"
        const val REPLAY_SCHEDULE = "schedule"

        /** Mine one gift wrap per pre-signed seal, then broadcast the wraps. */
        const val REPLAY_WRAPS = "wraps"
    }
}

/**
 * Where the queue checkpoints its persistable jobs. Implementations must be
 * safe to call from any thread and should apply writes in call order; both
 * methods are fire-and-forget from the queue's perspective.
 */
interface PoWJobPersistence {
    /** Upserts [job] (re-saving the same id on restore is expected). */
    fun save(job: PersistedPoWJob)

    /** Drops [jobId] once the job finished, failed, or was cancelled. */
    fun remove(jobId: String)
}
