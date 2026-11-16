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

import com.vitorpamplona.amethyst.commons.model.cache.EventCache
import com.vitorpamplona.amethyst.commons.model.nip88Polls.PollResponsesCache
import com.vitorpamplona.marmot.foundation.appEvents.MarmotPolls
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nip88Polls.response.PollResponseEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * A Marmot group's polls are counted by the shared tally ([PollResponsesCache]) under the rule
 * the group hands it ([MarmotPollResponseRule], over marmot-kmp's [MarmotPolls.castVote]), which is MDK 0.14's (`crates/traits/src/polls.rs`).
 * These are the cases where MDK and NIP-88's lenient reading disagree; getting one wrong shows a
 * group a different result than White Noise does.
 */
class MarmotPollTallyTest {
    private val alice = "a".repeat(64)
    private val bob = "b".repeat(64)
    private val carol = "c".repeat(64)

    private fun poll(
        type: MarmotPolls.PollType = MarmotPolls.PollType.SINGLE_CHOICE,
        endsAt: Long? = 200,
    ): PollEvent {
        val tags = MarmotPolls.pollTags(100, "Drink?", listOf("Tea", "Coffee", "Water"), type, endsAt)!!
        return PollEvent("1".repeat(64), alice, 100, tags, "Drink?", "")
    }

    private fun vote(
        id: Char,
        voter: String,
        createdAt: Long,
        pollId: String,
        vararg options: String,
    ) = PollResponseEvent(id.toString().repeat(64), voter, createdAt, MarmotPolls.responseTags(pollId, options.toList())!!, "", "")

    /** The poll in a Marmot room, then [votes], the way the group delivers them. */
    private fun tallyInGroup(
        poll: PollEvent,
        votes: List<PollResponseEvent>,
    ): PollResponsesCache {
        val cache = EventCache()
        cache.justConsume(poll, null, true)
        val pollNote = cache.getNoteIfExists(poll.id)!!
        MarmotGroupChatroom("f".repeat(64)).addMessageSync(pollNote)
        votes.forEach { cache.justConsume(it, null, true) }
        return pollNote.pollState()
    }

    private fun PollResponsesCache.counts() = listOf("0", "1", "2").map { responses.value.tally[it]?.size ?: 0 }

    @Test
    fun newestValidVotePerVoterCounts() {
        val p = poll()
        val tally =
            tallyInGroup(
                p,
                listOf(
                    vote('2', bob, 110, p.id, "0"),
                    vote('3', bob, 120, p.id, "1"),
                    // Two options on a single-choice poll: no vote, so bob's 120 vote stands.
                    // NIP-88 would count its first option instead.
                    vote('4', bob, 130, p.id, "0", "1"),
                    vote('5', carol, 150, p.id, "1"),
                    // After the deadline: not a vote.
                    vote('6', alice, 250, p.id, "2"),
                ),
            )
        assertEquals(listOf(0, 2, 0), tally.counts())
        assertEquals(2, tally.responses.value.totalVoters())
    }

    @Test
    fun unknownOptionVoidsTheWholeResponse() {
        val p = poll(MarmotPolls.PollType.MULTIPLE_CHOICE)
        val tally = tallyInGroup(p, listOf(vote('2', bob, 110, p.id, "0", "9")))
        assertEquals(0, tally.responses.value.totalVoters())
    }

    @Test
    fun tiesBreakOnEventId() {
        val p = poll()
        val tally = tallyInGroup(p, listOf(vote('3', bob, 110, p.id, "2"), vote('2', bob, 110, p.id, "0")))
        assertEquals(listOf(0, 0, 1), tally.counts())
    }

    @Test
    fun theGroupsRuleOutlivesTheCachesPolicyRefresh() {
        // Every new response makes the cache re-derive the poll's NIP-88 policy; the group's rule
        // must stay, or the second vote below would be read leniently.
        val p = poll()
        val tally = tallyInGroup(p, listOf(vote('2', bob, 110, p.id, "1"), vote('3', bob, 120, p.id, "0", "2")))
        assertNotNull(
            tally.responses.value.policy
                ?.rule,
        )
        assertEquals(listOf(0, 1, 0), tally.counts())
    }

    /** Votes travel inside the group unsigned; a signed one came from outside, by the poll's id alone. */
    @Test
    fun aSignedResponseFromOutsideTheGroupDoesNotVote() {
        val p = poll()
        val outsider = PollResponseEvent("9".repeat(64), carol, 120, MarmotPolls.responseTags(p.id, listOf("2"))!!, "", "f".repeat(128))
        val tally = tallyInGroup(p, listOf(vote('2', bob, 110, p.id, "0"), outsider))
        assertEquals(listOf(1, 0, 0), tally.counts())
    }
}
