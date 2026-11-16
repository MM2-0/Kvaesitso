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
package com.vitorpamplona.amethyst.commons.model.nip88Polls

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.UserDependencies
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip88Polls.response.PollResponseEvent
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

@Stable
class PollResponsesCache : UserDependencies {
    companion object {
        /**
         * Faces an avatar gallery shows before the rest collapse into "+N".
         *
         * Lives with the tally because both readers of a tally — the feed card and the results
         * screen — draw the same gallery, and two copies of this number drift.
         */
        const val GALLERY_FACES = 4

        val DefaultFeedOrder: Comparator<PollResponseEvent> =
            compareByDescending<PollResponseEvent> { it.createdAt }.thenBy { it.id }
    }

    /**
     * An immutable snapshot of the tally.
     *
     * Built on persistent collections so [add] can fold one response in while sharing structure
     * with the snapshot the UI is still reading. Rebuilding from scratch per arrival — the obvious
     * implementation — is O(n) each time and therefore O(n²) across a poll's lifetime; a backfill
     * draining a few thousand votes turns that into millions of map operations and one full copy
     * per event.
     */
    class ResponseTally
        private constructor(
            val allResponses: PersistentSet<Note>,
            /**
             * The poll's own rules. Null until the kind-1068 event arrives — responses often show
             * up first — in which case the tally stays permissive rather than dropping votes it
             * cannot yet validate.
             */
            val policy: PollTallyPolicy?,
            /** One response per author — the latest one that is actually eligible. */
            val votes: PersistentMap<User, PollResponseEvent>,
            /** Option code -> the voters who picked it. */
            val tally: PersistentMap<String, PersistentSet<User>>,
            /** Voters whose response cast at least one valid option code. */
            val countedVoters: PersistentSet<User>,
            /** Responses stamped after the poll closed, excluded from [votes]. */
            val lateVotes: Int,
            /**
             * Responses stamped before the poll was published, excluded from [votes]. Counted apart
             * from [lateVotes] because the two read completely differently to a reader: one is a
             * vote that missed the deadline, the other is a backdated event on a poll that may
             * still be open, and saying "arrived after the poll closed" about it is simply false.
             */
            val backdatedVotes: Int,
        ) {
            constructor() : this(
                persistentSetOf(),
                null,
                persistentMapOf(),
                persistentMapOf(),
                persistentSetOf(),
                0,
                0,
            )

            /** Voters whose response cast no valid option code at all, excluded from [tally]. */
            val ignoredVotes: Int get() = votes.size - countedVoters.size

            /**
             * The single option with the most voters, or null when nothing leads.
             *
             * A draw has no winner. `maxByOrNull` would hand the crown — the green highlight and
             * the check — to whichever tied option happened to come first, which on a 1-1 poll is
             * an outright lie about the result.
             */
            fun winning(): String? {
                var best: String? = null
                var bestCount = 0
                var drawn = false

                tally.forEach { (code, voters) ->
                    when {
                        voters.size > bestCount -> {
                            best = code
                            bestCount = voters.size
                            drawn = false
                        }

                        voters.size == bestCount -> drawn = true
                    }
                }

                return if (drawn || bestCount == 0) null else best
            }

            /**
             * Distinct people whose vote counts. This is the denominator for every percentage: on a
             * multiple-choice poll someone who ticks three boxes is still one voter, so the bars
             * read as "share of people" and may sum past 100%.
             */
            fun totalVoters() = countedVoters.size

            /** Boxes ticked across all voters. Equal to [totalVoters] on a single-choice poll. */
            fun totalSelections() = tally.entries.sumOf { it.value.size }

            /** The option codes this response actually casts under the current rules. */
            private fun accepted(event: PollResponseEvent): Set<String> {
                policy?.rule?.let { return it.cast(event) ?: emptySet() }
                val codes = event.responses()
                return policy?.accept(codes) ?: codes.toSet()
            }

            private fun withVoteRemoved(
                user: User,
                event: PollResponseEvent,
            ): Pair<PersistentMap<String, PersistentSet<User>>, PersistentSet<User>> {
                var newTally = tally
                accepted(event).forEach { code ->
                    val remaining = newTally[code]?.removing(user)
                    newTally =
                        when {
                            remaining == null -> newTally
                            // Drop the key rather than leave an empty set: winning() and
                            // totalSelections() both read every entry.
                            remaining.isEmpty() -> newTally.removing(code)
                            else -> newTally.putting(code, remaining)
                        }
                }
                return newTally to countedVoters.removing(user)
            }

            /**
             * Folds one response in, returning `this` unchanged when it adds nothing — which lets
             * the caller skip an emission rather than churn every collector.
             */
            fun add(note: Note): ResponseTally {
                if (note in allResponses) return this

                val withNote = allResponses.adding(note)
                val event =
                    note.event as? PollResponseEvent
                        ?: return ResponseTally(withNote, policy, votes, tally, countedVoters, lateVotes, backdatedVotes)

                if (policy != null && policy.isAfterWindow(event.createdAt)) {
                    return ResponseTally(withNote, policy, votes, tally, countedVoters, lateVotes + 1, backdatedVotes)
                }

                if (policy != null && policy.isBeforeWindow(event.createdAt)) {
                    return ResponseTally(withNote, policy, votes, tally, countedVoters, lateVotes, backdatedVotes + 1)
                }

                val author = note.author ?: return ResponseTally(withNote, policy, votes, tally, countedVoters, lateVotes, backdatedVotes)

                // Under a room's rule a response that breaks it is no vote, so it cannot displace one.
                val rule = policy?.rule
                if (rule != null && rule.cast(event) == null) {
                    return ResponseTally(withNote, policy, votes, tally, countedVoters, lateVotes, backdatedVotes)
                }

                // Latest-per-author, ties keeping the one already held — same rule as latestByAuthor.
                // A room's rule breaks a tie by the larger id instead, as MDK does.
                val current = votes[author]
                if (current != null &&
                    (current.createdAt > event.createdAt || (current.createdAt == event.createdAt && (rule == null || current.id >= event.id)))
                ) {
                    return ResponseTally(withNote, policy, votes, tally, countedVoters, lateVotes, backdatedVotes)
                }

                // A re-vote has to retract the old one first, or the voter lingers in the options
                // they used to hold.
                var newTally = tally
                var newVoters = countedVoters
                if (current != null) {
                    val (t, v) = withVoteRemoved(author, current)
                    newTally = t
                    newVoters = v
                }

                val codes = accepted(event)
                if (codes.isNotEmpty()) {
                    newVoters = newVoters.adding(author)
                    codes.forEach { code ->
                        newTally = newTally.putting(code, (newTally[code] ?: persistentSetOf()).adding(author))
                    }
                }

                return ResponseTally(withNote, policy, votes.putting(author, event), newTally, newVoters, lateVotes, backdatedVotes)
            }

            /**
             * Rebuilds from a response set — the O(n) path, used when a response disappears or the
             * rules change under us. Both are rare; incremental removal would need a per-author
             * index to find the next-latest vote, which is not worth carrying for a delete.
             */
            fun rebuild(
                responses: PersistentSet<Note> = allResponses,
                newPolicy: PollTallyPolicy? = policy,
            ): ResponseTally {
                var rebuilt = ResponseTally(persistentSetOf(), newPolicy, persistentMapOf(), persistentMapOf(), persistentSetOf(), 0, 0)
                responses.forEach { rebuilt = rebuilt.add(it) }
                return rebuilt
            }
        }

    val responses = MutableStateFlow(ResponseTally())

    fun addResponse(note: Note) {
        responses.update { it.add(note) }
    }

    fun removeResponse(deleteNote: Note) {
        // if it's not already there, quick exit
        if (deleteNote !in responses.value.allResponses) return

        responses.update { it.rebuild(responses = it.allResponses.removing(deleteNote)) }
    }

    /**
     * Hands the tally the poll's rules, recomputing it in place. Idempotent — the common case is
     * the same poll event arriving again from another relay.
     */
    fun updatePolicy(newPolicy: PollTallyPolicy) {
        // The cache re-derives the poll's NIP-88 rules on every response; a room's rule, once its
        // poll is in the room, outlives that. Merged inside the update: a room setting its rule on
        // another thread must not be overwritten by a refresh that read the policy before it.
        responses.update { current ->
            val merged = if (newPolicy.rule == null) newPolicy.copy(rule = current.policy?.rule) else newPolicy
            if (current.policy == merged) current else current.rebuild(newPolicy = merged)
        }
    }

    fun ResponseTally.filterTo(
        code: String,
        forKey: HexKey,
        priority: Set<HexKey>,
    ): TallyResults {
        val comparator = compareByDescending<User> { it.pubkeyHex == forKey }.thenByDescending { it.pubkeyHex in priority }.thenBy { it.pubkeyHex }

        val usersThatVotedForThisOption = tally[code] ?: emptySet()

        val voters = totalVoters()

        // Share of voters, not share of selections: a multiple-choice voter who ticks three boxes
        // is one person, and a bar that reads "70%" should mean "7 in 10 people". Identical to the
        // selections basis on a single-choice poll.
        val percent =
            if (voters > 0) {
                usersThatVotedForThisOption.size.toFloat() / voters.toFloat()
            } else {
                0f
            }

        // Deliberately not sorted here: this runs on the UI thread for every visible poll card, and
        // the feed only ever draws four avatars. TallyResults sorts on demand.
        return TallyResults(usersThatVotedForThisOption, comparator, percent, code == winning())
    }

    fun currentTally(
        code: String,
        forKey: HexKey,
        priorityAccounts: Set<HexKey>,
    ): TallyResults = responses.value.filterTo(code, forKey, priorityAccounts)

    fun tallyFlow(
        code: String,
        forKey: HexKey,
        priorityAccounts: Flow<Set<HexKey>>,
    ): Flow<TallyResults> =
        combine(responses, priorityAccounts) { responses, priority ->
            responses.filterTo(code, forKey, priority)
        }

    /** The options [user]'s counted vote picks, empty when they have not voted. */
    fun selectionOf(user: User): Set<String> =
        responses.value.tally
            .filterValues { user in it }
            .keys

    // Counted, not merely present: a response whose option codes the poll doesn't recognise casts
    // no vote, so its author should still be offered the voting controls.
    fun hasPubKeyVoted(user: User): Boolean = responses.value.countedVoters.contains(user)

    fun hasPubKeyVotedFlow(user: User): Flow<Boolean> = responses.map { it.countedVoters.contains(user) }.distinctUntilChanged()
}

/**
 * One option's share of a poll, with its voters kept unsorted until somebody needs them ordered.
 *
 * The feed card builds one of these per option, on the UI thread, every time the tally changes —
 * and then draws four avatars. Sorting every voter of a busy poll to pick four is the kind of work
 * that only shows up as dropped frames while scrolling, so [users] is lazy and [topUsers] never
 * sorts the tail at all.
 */
@Stable
class TallyResults(
    private val voters: Collection<User> = emptyList(),
    private val order: Comparator<User> = compareBy { it.pubkeyHex },
    val percent: Float = 0.0f,
    val isWinning: Boolean = false,
) {
    val size get() = voters.size

    /** Whether this option holds a vote from [pubkeyHex], without materialising the ordered list. */
    fun contains(pubkeyHex: HexKey) = voters.any { it.pubkeyHex == pubkeyHex }

    /** Every voter, in order. Materialised once, and only for screens that list them all. */
    val users: List<User> by lazy(LazyThreadSafetyMode.PUBLICATION) { voters.sortedWith(order) }

    /**
     * The first [n] voters in order, chosen by a bounded insertion rather than a full sort — O(m·n)
     * with n fixed at a handful, against O(m log m) to then throw all but [n] away.
     */
    fun topUsers(n: Int): List<User> {
        if (n <= 0 || voters.isEmpty()) return emptyList()
        if (voters.size <= n) return users

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
}
