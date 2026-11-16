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
package com.vitorpamplona.amethyst.commons.viewmodels.nip88Polls

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.nip88Polls.PollTallyPolicy
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nip88Polls.poll.tags.PollType
import com.vitorpamplona.quartz.nip88Polls.response.PollResponseEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PollResultsViewModelTest {
    private val pollId = "a".repeat(64)
    private val me = "f".repeat(64)
    private val followed = "e".repeat(64)
    private val stranger = "d".repeat(64)
    private val muted = "c".repeat(64)

    private val userCache = mutableMapOf<HexKey, User>()

    // viewModelScope is Dispatchers.Main, and uiState is a WhileSubscribed stateIn — nothing runs
    // until something subscribes.
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Subscribes long enough for the real state to be produced.
     *
     * Reading `uiState.first()` would return `stateIn`'s seed — computed before any collector
     * exists, so with no follow set and no backfill — which is exactly the state no user ever sees.
     */
    private fun TestScope.stateOf(vm: PollResultsViewModel): PollResultsUiState {
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect { } }
        advanceUntilIdle()
        return vm.uiState.value.also { job.cancel() }
    }

    private fun user(pubKey: HexKey): User = userCache.getOrPut(pubKey) { User(pubKey) { addr -> Note(addr.toValue()) } }

    /** A single-choice poll on "red" / "green" / "blue", open forever. */
    private fun pollNote(type: PollType = PollType.SINGLE_CHOICE): Note {
        val event =
            PollEvent(
                id = pollId,
                pubKey = "b".repeat(64),
                createdAt = 100,
                tags =
                    arrayOf(
                        arrayOf("polltype", if (type == PollType.MULTI_CHOICE) "multiplechoice" else "singlechoice"),
                        arrayOf("option", "red", "Red"),
                        arrayOf("option", "green", "Green"),
                        arrayOf("option", "blue", "Blue"),
                    ),
                content = "Pick a colour",
                sig = "0".repeat(128),
            )
        val note = Note(pollId)
        note.loadEvent(event, user(event.pubKey), emptyList())
        note.pollState().updatePolicy(PollTallyPolicy.from(event))
        return note
    }

    private fun vote(
        note: Note,
        id: HexKey,
        pubKey: HexKey,
        options: List<String>,
        createdAt: Long = 150,
    ) {
        val event =
            PollResponseEvent(
                id = id,
                pubKey = pubKey,
                createdAt = createdAt,
                tags = arrayOf(arrayOf("e", pollId)) + options.map { arrayOf("response", it) },
                content = "",
                sig = "0".repeat(128),
            )
        val responseNote = Note(id)
        responseNote.loadEvent(event, user(pubKey), emptyList())
        note.pollState().addResponse(responseNote)
    }

    private fun TestScope.viewModel(
        note: Note,
        hidden: Set<HexKey> = emptySet(),
        follows: Set<HexKey> = setOf(followed),
        loader: PollResponseLoader? = null,
    ) = PollResultsViewModel(
        pollNote = note,
        forKey = me,
        isHidden = { it in hidden },
        follows = MutableStateFlow(follows),
        hiddenChanges = MutableStateFlow(Unit),
        loader = loader,
        // Keep the state build and the backfill on the test scheduler; in production these are
        // Default and IO so a big poll's re-sorts never land on the UI thread.
        computeContext = UnconfinedTestDispatcher(testScheduler),
        loadContext = UnconfinedTestDispatcher(testScheduler),
    )

    @Test
    fun optionsKeepPollOrderAndCarryCounts() =
        runTest {
            val note = pollNote()
            vote(note, "1".repeat(64), me, listOf("blue"))
            vote(note, "2".repeat(64), followed, listOf("blue"))
            vote(note, "3".repeat(64), stranger, listOf("red"))

            val state = stateOf(viewModel(note))

            // Declaration order, not "winner first" and not tally-map order.
            assertEquals(listOf("red", "green", "blue"), state.options.map { it.code })
            assertEquals(listOf(1, 0, 2), state.options.map { it.voters })
            assertEquals("blue", state.options.first { it.isWinning }.code)
            assertEquals(3, state.totalVoters)
        }

    @Test
    fun votersAreOrderedMeThenFollowsThenRest() =
        runTest {
            val note = pollNote()
            vote(note, "1".repeat(64), stranger, listOf("red"))
            vote(note, "2".repeat(64), followed, listOf("red"))
            vote(note, "3".repeat(64), me, listOf("red"))

            val state = stateOf(viewModel(note))

            assertEquals(listOf(me, followed, stranger), state.voters.map { it.user.pubkeyHex })
        }

    @Test
    fun selectingAnOptionScopesTheListButNotTheSummary() =
        runTest {
            val note = pollNote()
            vote(note, "1".repeat(64), me, listOf("red"))
            vote(note, "2".repeat(64), followed, listOf("blue"))

            val vm = viewModel(note)
            vm.selectOption("blue")
            val state = stateOf(vm)

            assertEquals(listOf(followed), state.voters.map { it.user.pubkeyHex })
            // The bars above the list must not move while the list is filtered.
            assertEquals(2, state.totalVoters)
            assertEquals(listOf(1, 0, 1), state.options.map { it.voters })
        }

    @Test
    fun selectingTheSameOptionTwiceClearsTheFilter() =
        runTest {
            val note = pollNote()
            vote(note, "1".repeat(64), me, listOf("red"))
            vote(note, "2".repeat(64), followed, listOf("blue"))

            val vm = viewModel(note)
            vm.selectOption("blue")
            vm.selectOption("blue")

            assertNull(vm.selectedOption.value)
            assertEquals(2, stateOf(vm).voters.size)
        }

    @Test
    fun mutedVotersAreHiddenButStillCounted() =
        runTest {
            val note = pollNote()
            vote(note, "1".repeat(64), me, listOf("red"))
            vote(note, "2".repeat(64), muted, listOf("red"))

            val state = stateOf(viewModel(note, hidden = setOf(muted)))

            assertEquals(listOf(me), state.voters.map { it.user.pubkeyHex })
            assertEquals(1, state.hiddenVoters)
            // Dropping them from the maths would misreport the poll, not protect anyone.
            assertEquals(2, state.totalVoters)
            assertEquals(1.0f, state.options.first { it.code == "red" }.percent)
        }

    @Test
    fun multiChoiceRowsListEveryOptionThatPersonPicked() =
        runTest {
            val note = pollNote(PollType.MULTI_CHOICE)
            vote(note, "1".repeat(64), me, listOf("blue", "red"))

            val state = stateOf(viewModel(note))
            val row = state.voters.single()

            // Poll order, so two voters who picked the same pair read identically.
            assertEquals(listOf("red", "blue"), row.codes)
            assertEquals(listOf("Red", "Blue"), row.labels)
            assertEquals(1, state.totalVoters)
            assertEquals(2, state.totalSelections)
        }

    @Test
    fun avatarStackIsCappedAndOrderedByRelevance() =
        runTest {
            val note = pollNote()
            // Six voters on one option: the stack shows AVATAR_STACK of them.
            vote(note, "1".repeat(64), me, listOf("red"))
            vote(note, "2".repeat(64), followed, listOf("red"))
            listOf("3", "4", "5", "6").forEach { seed ->
                vote(note, seed.repeat(64), "${seed}a".repeat(32), listOf("red"))
            }

            val red = stateOf(viewModel(note)).options.first { it.code == "red" }

            assertEquals(PollResultsViewModel.AVATAR_STACK, red.topVoters.size)
            assertEquals(me, red.topVoters[0].pubkeyHex)
            assertEquals(followed, red.topVoters[1].pubkeyHex)
        }

    @Test
    fun twoUserInstancesForOnePersonYieldOneRow() =
        runTest {
            val note = pollNote()
            val pubkey = "9".repeat(64)

            // The cache normally hands out one User per pubkey, but an eviction and re-create can
            // leave two live instances. Two rows sharing a LazyColumn key is a crash, not a
            // cosmetic duplicate — so the row list has to collapse them.
            listOf("1", "2").forEachIndexed { i, id ->
                val event =
                    PollResponseEvent(
                        id = id.repeat(64),
                        pubKey = pubkey,
                        createdAt = 150L + i,
                        tags = arrayOf(arrayOf("e", pollId), arrayOf("response", "red")),
                        content = "",
                        sig = "0".repeat(128),
                    )
                val responseNote = Note(event.id)
                // A fresh User object each time, deliberately bypassing the test's user cache.
                responseNote.loadEvent(event, User(pubkey) { addr -> Note(addr.toValue()) }, emptyList())
                note.pollState().addResponse(responseNote)
            }

            val state = stateOf(viewModel(note))

            assertEquals(1, state.voters.count { it.user.pubkeyHex == pubkey })
            assertEquals(state.voters.size, state.voters.distinctBy { it.user.pubkeyHex }.size)
        }

    @Test
    fun aDrawLeavesEveryOptionUncrowned() =
        runTest {
            val note = pollNote()
            vote(note, "1".repeat(64), me, listOf("red"))
            vote(note, "2".repeat(64), followed, listOf("blue"))

            val state = stateOf(viewModel(note))

            assertTrue(state.options.none { it.isWinning }, "a 1-1 poll has no winner to highlight")
        }

    @Test
    fun myVoteIsReportedForHighlightingTheOption() =
        runTest {
            val note = pollNote()
            vote(note, "1".repeat(64), me, listOf("green"))

            assertEquals(listOf("green"), stateOf(viewModel(note)).myVote)
        }

    @Test
    fun aNewVoteFlowsThroughToTheState() =
        runTest {
            val note = pollNote()
            val vm = viewModel(note)
            assertEquals(0, stateOf(vm).totalVoters)

            vote(note, "1".repeat(64), stranger, listOf("red"))

            assertEquals(1, stateOf(vm).totalVoters)
        }

    @Test
    fun relayReportBelowWhatWeHoldNeverClaimsIncompleteness() =
        runTest {
            val note = pollNote()
            vote(note, "1".repeat(64), me, listOf("red"))
            vote(note, "2".repeat(64), followed, listOf("red"))

            // An HLL estimate can land under the truth; the floor keeps the footer honest.
            val vm =
                viewModel(
                    note,
                    loader = { PollLoadReport(reported = 1, approximate = true, relaysAsked = 3, relaysAnswered = 1) },
                )

            val state = stateOf(vm)
            assertEquals(2, state.reportedResponses)
            assertTrue(!state.isIncomplete)
        }

    @Test
    fun relayReportAboveWhatWeHoldSurfacesTheShortfall() =
        runTest {
            val note = pollNote()
            vote(note, "1".repeat(64), me, listOf("red"))

            val vm =
                viewModel(
                    note,
                    loader = { PollLoadReport(reported = 412, approximate = true, relaysAsked = 6, relaysAnswered = 4) },
                )

            val state = stateOf(vm)
            assertEquals(412, state.reportedResponses)
            assertEquals(1, state.loadedResponses)
            assertTrue(state.isIncomplete)
            assertTrue(state.reportIsApproximate)
            assertEquals(4, state.relaysAnswered)
        }

    @Test
    fun aLoaderThatThrowsLeavesTheTallyUsable() =
        runTest {
            val note = pollNote()
            vote(note, "1".repeat(64), me, listOf("red"))

            val vm = viewModel(note, loader = { error("relay exploded") })

            val state = stateOf(vm)
            assertNull(state.reportedResponses)
            assertEquals(1, state.totalVoters)
        }
}
