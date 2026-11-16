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

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.nip88Polls.PollResponsesCache
import com.vitorpamplona.amethyst.commons.model.nip88Polls.PollResponsesCache.ResponseTally
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nip88Polls.poll.tags.PollType
import com.vitorpamplona.quartz.utils.runCatchingCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KClass

/** One option's share of the poll, ready to draw. */
@Immutable
class PollOptionResult(
    val code: String,
    val label: String,
    val voters: Int,
    /** Share of *voters*, so multiple-choice bars can sum past 100%. */
    val percent: Float,
    val isWinning: Boolean,
    /** The first few voters in relevance order, for the avatar stack. */
    val topVoters: List<User>,
)

/** One person's vote: who they are, what they picked, and when. */
@Immutable
class PollVoterRow(
    val user: User,
    val codes: List<String>,
    val labels: List<String>,
    val createdAt: Long,
)

@Immutable
class PollResultsUiState(
    val options: List<PollOptionResult> = emptyList(),
    val voters: List<PollVoterRow> = emptyList(),
    val totalVoters: Int = 0,
    val totalSelections: Int = 0,
    val ignoredVotes: Int = 0,
    val lateVotes: Int = 0,
    /** Responses stamped before the poll existed. Distinct from [lateVotes] — see the tally. */
    val backdatedVotes: Int = 0,
    val hiddenVoters: Int = 0,
    val myVote: List<String> = emptyList(),
    val type: PollType = PollType.SINGLE_CHOICE,
    val endsAt: Long? = null,
    /** Raw kind-1018 events the tally has, however they were counted. */
    val loadedResponses: Int = 0,
    /** What the relays say exists, floored by [loadedResponses]. Null when nobody could say. */
    val reportedResponses: Int? = null,
    val reportIsApproximate: Boolean = false,
    val relaysAnswered: Int = 0,
    /** True while we are still asking the relays how many votes exist. */
    val isCheckingCompleteness: Boolean = false,
    /**
     * False only for the placeholder state handed out before the first tally is folded. Lets the UI
     * tell "this poll has no votes" apart from "we have not looked yet" without flashing the former.
     */
    val hasTally: Boolean = false,
) {
    /** True only when we can actually show that responses are missing. */
    val isIncomplete get() = reportedResponses != null && reportedResponses > loadedResponses
}

/**
 * Screen state for the poll results page.
 *
 * Reads the live tally off the poll [Note] — no second cache — and turns it into rows the UI can
 * render without doing set arithmetic in composition. Every rebuild after the first runs on
 * [computeContext]: a busy poll emits one tally per vote consumed, and re-sorting thousands of
 * voters that many times on the UI thread would drop frames for the whole drain. It is IO, not
 * Default, because the rebuild reads [User]s and calls the account's mute check, both of which can
 * wait on locks, and Default has only one thread per core. The only
 * interactive state is [selectedOption], which scopes the voter list without touching the summary.
 *
 * Muting is applied here rather than in the tally: a muted voter still counts toward the totals
 * (they did vote, and hiding them from the maths would misreport the poll), they are just not
 * listed, and [PollResultsUiState.hiddenVoters] says how many.
 */
@Stable
class PollResultsViewModel(
    private val pollNote: Note,
    private val forKey: HexKey,
    private val isHidden: (HexKey) -> Boolean,
    follows: Flow<Set<HexKey>>,
    hiddenChanges: Flow<Any?>,
    private val loader: PollResponseLoader? = null,
    // Injected so tests can drive both on the test scheduler; production never passes them.
    private val computeContext: CoroutineContext = Dispatchers.IO,
    private val loadContext: CoroutineContext = Dispatchers.IO,
) : ViewModel() {
    companion object {
        /** Faces shown per option before collapsing into a "+N" chip. */
        const val AVATAR_STACK = PollResponsesCache.GALLERY_FACES
    }

    private val _selectedOption = MutableStateFlow<String?>(null)
    val selectedOption = _selectedOption.asStateFlow()

    private val completeness = MutableStateFlow(CompletenessState())

    private class CompletenessState(
        val running: Boolean = false,
        val report: PollLoadReport? = null,
    )

    init {
        // The votes themselves arrive on the screen's subscription. This asks the one thing a
        // subscription cannot answer — how many exist that we were not sent — so the page can say
        // when it is showing fewer than the relays hold.
        if (loader != null) {
            viewModelScope.launch(loadContext) {
                val poll = awaitPollEvent()
                completeness.value = CompletenessState(running = true)
                val report = runCatchingCancellable { loader.load(poll) }.getOrNull()
                completeness.value = CompletenessState(running = false, report = report)
            }
        }
    }

    /** Responses regularly beat their poll to the client; there is nothing to query until it lands. */
    private suspend fun awaitPollEvent(): PollEvent =
        pollNote.event as? PollEvent
            ?: pollNote
                .flow()
                .metadata.stateFlow
                .map { it.note.event as? PollEvent }
                .filterNotNull()
                .first()

    val uiState: StateFlow<PollResultsUiState> =
        combine(
            pollNote.pollState().responses,
            follows,
            _selectedOption,
            hiddenChanges,
            completeness,
        ) { tally, followSet, selected, _, load ->
            build(tally, followSet, selected, load)
        }.flowOn(computeContext)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                // A placeholder, not a tally: building the real one here would sort every voter on
                // whatever thread constructs the ViewModel — the composition thread — which is the
                // one poll where that hurts, the thousand-voter one this screen exists for. The
                // combine above delivers the real state from Default, and `hasTally` keeps the UI
                // from reading this placeholder as an empty poll.
                initialValue = PollResultsUiState(),
            )

    fun selectOption(code: String?) {
        _selectedOption.value = if (_selectedOption.value == code) null else code
    }

    private fun build(
        tally: ResponseTally,
        follows: Set<HexKey>,
        selected: String?,
        load: CompletenessState,
    ): PollResultsUiState {
        val event = pollNote.event as? PollEvent

        // Poll order, not tally order, so the rows never reshuffle as votes arrive. Falls back to
        // whatever the responses referenced while the kind-1068 event is still in flight.
        val options = event?.options().orEmpty()
        val codesInOrder = if (options.isNotEmpty()) options.map { it.code } else tally.tally.keys.sorted()
        val labels = options.associate { it.code to it.label }

        val byRelevance =
            compareByDescending<User> { it.pubkeyHex == forKey }
                .thenByDescending { it.pubkeyHex in follows }
                .thenBy { it.pubkeyHex }

        val totalVoters = tally.totalVoters()
        // Once, not once per option: winning() walks every bucket.
        val winner = tally.winning()

        val optionResults =
            codesInOrder.map { code ->
                val voters = tally.tally[code].orEmpty()
                PollOptionResult(
                    code = code,
                    label = labels[code] ?: code,
                    voters = voters.size,
                    percent = if (totalVoters > 0) voters.size.toFloat() / totalVoters else 0f,
                    isWinning = code == winner,
                    topVoters = topByRelevance(voters, byRelevance, AVATAR_STACK),
                )
            }

        // Invert the tally once: voter -> the codes they picked, in poll order.
        //
        // Keyed by pubkey rather than by User instance. The cache normally hands out one User per
        // pubkey, but an eviction and re-create would leave two live instances for one person —
        // and two rows sharing a key is a hard crash in LazyColumn, not a cosmetic duplicate.
        val picks = LinkedHashMap<HexKey, Pair<User, MutableList<String>>>()
        codesInOrder.forEach { code ->
            tally.tally[code]?.forEach { user ->
                picks.getOrPut(user.pubkeyHex) { user to mutableListOf() }.second.add(code)
            }
        }

        var hidden = 0
        var myPicks: List<String> = emptyList()
        val rows =
            picks
                .mapNotNull { (pubkey, entry) ->
                    val (user, codes) = entry
                    if (pubkey == forKey) myPicks = codes

                    if (isHidden(pubkey)) {
                        hidden++
                        return@mapNotNull null
                    }
                    if (selected != null && selected !in codes) return@mapNotNull null

                    PollVoterRow(
                        user = user,
                        codes = codes,
                        labels = codes.map { labels[it] ?: it },
                        createdAt = tally.votes[user]?.createdAt ?: 0L,
                    )
                }.sortedWith { a, b -> byRelevance.compare(a.user, b.user) }

        return PollResultsUiState(
            options = optionResults,
            voters = rows,
            totalVoters = totalVoters,
            totalSelections = tally.totalSelections(),
            ignoredVotes = tally.ignoredVotes,
            lateVotes = tally.lateVotes,
            backdatedVotes = tally.backdatedVotes,
            hiddenVoters = hidden,
            myVote = myPicks,
            type = event?.pollType() ?: PollType.SINGLE_CHOICE,
            endsAt = event?.endsAt(),
            loadedResponses = tally.allResponses.size,
            // Floor the relays' figure with what we hold: an HLL estimate can land under the true
            // value, and a relay that never answered can only make the number too small.
            reportedResponses = load.report?.reported?.coerceAtLeast(tally.allResponses.size),
            reportIsApproximate = load.report?.approximate ?: false,
            relaysAnswered = load.report?.relaysAnswered ?: 0,
            isCheckingCompleteness = load.running,
            hasTally = true,
        )
    }

    /**
     * The [n] highest-ranked voters without sorting the rest — the stack shows a handful and a
     * "+N" chip, so ordering the tail is pure waste on a poll with thousands of them.
     */
    private fun topByRelevance(
        voters: Collection<User>,
        order: Comparator<User>,
        n: Int,
    ): List<User> {
        if (voters.size <= n) return voters.sortedWith(order)

        val top = ArrayList<User>(n + 1)
        voters.forEach { user ->
            val found = top.binarySearch(user, order)
            val at = if (found < 0) -found - 1 else found
            if (at < n) {
                top.add(at, user)
                if (top.size > n) top.removeAt(n)
            }
        }
        return top
    }

    class Factory(
        private val pollNote: Note,
        private val forKey: HexKey,
        private val isHidden: (HexKey) -> Boolean,
        private val follows: Flow<Set<HexKey>>,
        private val hiddenChanges: Flow<Any?>,
        private val loader: PollResponseLoader? = null,
    ) : ViewModelProvider.Factory {
        // The multiplatform signature — commonMain has no java.lang.Class.
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(
            modelClass: KClass<T>,
            extras: CreationExtras,
        ): T = PollResultsViewModel(pollNote, forKey, isHidden, follows, hiddenChanges, loader) as T
    }
}
