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

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * How to finish publishing a mined template if the original in-memory
 * continuation is gone (process death). Callers pass one of these alongside
 * their richer live continuation; it is flattened into a [PersistedPoWJob]
 * the platform restorer can replay headlessly.
 */
sealed class PoWReplay {
    /** Sign and broadcast to the account's computed outbox relays. */
    class Broadcast(
        val extras: List<Event> = emptyList(),
    ) : PoWReplay()

    /** Sign and publish to exactly [relays] (e.g. a NIP-29 group host). */
    class ToRelays(
        val relays: List<NormalizedRelayUrl>,
    ) : PoWReplay()

    /** Sign and park in the scheduled-post store for [publishAtSec]. */
    class Schedule(
        val publishAtSec: Long,
        val extras: List<Event> = emptyList(),
    ) : PoWReplay()

    fun toRecord(
        id: String,
        accountPubkey: String,
        template: EventTemplate<*>,
        difficulty: Int,
    ): PersistedPoWJob =
        when (this) {
            is Broadcast ->
                PersistedPoWJob(
                    id = id,
                    accountPubkey = accountPubkey,
                    kind = template.kind,
                    difficulty = difficulty,
                    templateJson = template.toJson(),
                    replayType = PersistedPoWJob.REPLAY_BROADCAST,
                    extraEventsJson = extras.map { it.toJson() },
                    createdAtSec = TimeUtils.now(),
                )

            is ToRelays ->
                PersistedPoWJob(
                    id = id,
                    accountPubkey = accountPubkey,
                    kind = template.kind,
                    difficulty = difficulty,
                    templateJson = template.toJson(),
                    replayType = PersistedPoWJob.REPLAY_RELAYS,
                    relayUrls = relays.map { it.url },
                    createdAtSec = TimeUtils.now(),
                )

            is Schedule ->
                PersistedPoWJob(
                    id = id,
                    accountPubkey = accountPubkey,
                    kind = template.kind,
                    difficulty = difficulty,
                    templateJson = template.toJson(),
                    replayType = PersistedPoWJob.REPLAY_SCHEDULE,
                    extraEventsJson = extras.map { it.toJson() },
                    publishAtSec = publishAtSec,
                    createdAtSec = TimeUtils.now(),
                )
        }
}
