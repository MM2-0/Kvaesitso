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
package com.vitorpamplona.amethyst.commons.model.marmotGroups

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.model.nip88Polls.PollResponseRule
import com.vitorpamplona.amethyst.commons.model.nip88Polls.PollResponsesCache
import com.vitorpamplona.marmot.foundation.appEvents.MarmotPolls
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip88Polls.response.PollResponseEvent

/**
 * MDK's reading of the votes on one poll ([MarmotPolls.castVote]), handed to the shared tally
 * ([PollResponsesCache]) by the group the poll lives in. The rule itself is marmot-kmp's; this
 * only adapts it to the cache.
 */
@Immutable
data class MarmotPollResponseRule(
    val pollId: HexKey,
    val poll: MarmotPolls.Definition,
) : PollResponseRule {
    override fun cast(response: PollResponseEvent): Set<String>? = MarmotPolls.castVote(pollId, poll, response)

    companion object {
        /** The rule of [poll], or null when the poll itself breaks MDK's profile. */
        fun of(poll: Event): MarmotPollResponseRule? = MarmotPolls.parsePoll(poll)?.let { MarmotPollResponseRule(poll.id, it) }
    }
}
