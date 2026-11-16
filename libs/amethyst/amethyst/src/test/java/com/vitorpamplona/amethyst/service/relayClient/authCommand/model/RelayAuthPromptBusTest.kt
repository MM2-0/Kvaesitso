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
package com.vitorpamplona.amethyst.service.relayClient.authCommand.model

import com.vitorpamplona.amethyst.commons.relayClient.auth.RelayAuthPromptBus
import com.vitorpamplona.amethyst.commons.relayClient.auth.UserAuthChoice
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayAuthPromptBusTest {
    private val relay = NormalizedRelayUrl("wss://auth.relay.test")
    private val alice = "a".repeat(64)
    private val bob = "b".repeat(64)

    private suspend fun RelayAuthPromptBus.ask(account: String = alice) = requestDecision(relay, emptyList(), account, isMyOwnRelay = false)

    @Test
    fun deliversTheUsersChoiceToTheWaitingCaller() =
        runTest {
            val bus = RelayAuthPromptBus()

            val collector = async { bus.prompts.first() }
            val caller = async { bus.ask() }

            collector.await().respond(UserAuthChoice.ALWAYS_ALLOW)
            assertEquals(UserAuthChoice.ALWAYS_ALLOW, caller.await())
        }

    @Test
    fun concurrentChallengesForSameRelayAndAccountShareOnePrompt() =
        runTest {
            val bus = RelayAuthPromptBus()

            // Capture the single surfaced prompt before the two challenges fire.
            val surfaced = async { bus.prompts.first() }
            val first = async { bus.ask() }
            val second = async { bus.ask() }

            surfaced.await().respond(UserAuthChoice.ALLOW_ONCE)

            // Both waiters get the one answer. If the second had NOT been deduped it would have
            // surfaced its own unanswered prompt and timed out to DISMISS — so this proves dedup.
            assertEquals(UserAuthChoice.ALLOW_ONCE, first.await())
            assertEquals(UserAuthChoice.ALLOW_ONCE, second.await())
        }

    @Test
    fun twoAccountsOnTheSameRelayAreAskedSeparately() =
        runTest {
            val bus = RelayAuthPromptBus()

            // The dialog names the account whose npub would be revealed, so answering it for one
            // account must never speak for another. Dedup is keyed by (relay, account), not relay.
            val surfaced = async { bus.prompts.take(2).toList() }
            val forAlice = async { bus.ask(alice) }
            val forBob = async { bus.ask(bob) }

            val prompts = surfaced.await()
            assertEquals(setOf(alice, bob), prompts.map { it.askingAccount }.toSet())

            prompts.first { it.askingAccount == alice }.respond(UserAuthChoice.ALWAYS_ALLOW)
            prompts.first { it.askingAccount == bob }.respond(UserAuthChoice.BLOCK)

            assertEquals(UserAuthChoice.ALWAYS_ALLOW, forAlice.await())
            assertEquals(UserAuthChoice.BLOCK, forBob.await())
        }

    @Test
    fun unansweredPromptTimesOutToDismiss() =
        runTest {
            val bus = RelayAuthPromptBus(timeoutMs = 1_000L)

            // No one ever responds; the call must not hang, it resolves to DISMISS.
            assertEquals(UserAuthChoice.DISMISS, bus.ask())
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun retainsPromptForALateSubscriberSoItIsNotLost() =
        runTest {
            val bus = RelayAuthPromptBus()

            // A challenge fires while NO host is collecting (cold start / account switch).
            val caller = async { bus.ask() }
            runCurrent() // let the emit happen with no subscriber present

            // A host subscribes late; the replayed prompt must still reach it (not be lost, which
            // would strand the caller until the timeout and then silently DISMISS).
            bus.prompts.first().respond(UserAuthChoice.ALLOW_ONCE)
            assertEquals(UserAuthChoice.ALLOW_ONCE, caller.await())
        }

    @Test
    fun aQueuedPromptsClockStartsWhenItIsShown() =
        runTest {
            val bus = RelayAuthPromptBus(timeoutMs = 1_000L)
            val relayA = NormalizedRelayUrl("wss://a.relay.test")
            val relayB = NormalizedRelayUrl("wss://b.relay.test")

            val surfaced = async { bus.prompts.take(2).toList() }
            val callerA = async { bus.requestDecision(relayA, emptyList(), alice, isMyOwnRelay = false) }
            val callerB = async { bus.requestDecision(relayB, emptyList(), alice, isMyOwnRelay = false) }
            val prompts = surfaced.await()

            // The host renders one dialog at a time, so B sits queued and invisible while the user
            // works through A. Only A is on screen, so only A's clock is running.
            prompts[0].markShown()
            delay(600)
            prompts[0].respond(UserAuthChoice.ALLOW_ONCE)
            assertEquals(UserAuthChoice.ALLOW_ONCE, callerA.await())

            // B's turn. Its own window opens now — well past the point where a single deadline
            // measured from the challenge would already have expired it unseen.
            prompts[1].markShown()
            delay(600)
            prompts[1].respond(UserAuthChoice.ALWAYS_ALLOW)

            assertEquals(UserAuthChoice.ALWAYS_ALLOW, callerB.await())
        }

    /**
     * "Always/Never, all relays" is answered on one dialog and applies to the prompts still queued
     * behind it, which the host resolves without ever showing them. A prompt that is answered but not
     * marked shown sits in the queue-wait window — up to five minutes — before its caller reads the
     * answer already sitting in the deferred, so the relay it belongs to goes unauthenticated for that
     * long despite the user having answered. Marking it shown is what makes the answer land now.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun anAnswerFannedOutToAQueuedPromptLandsWithoutWaitingOutTheQueueWindow() =
        runTest {
            val clock = testScheduler
            val bus = RelayAuthPromptBus(timeoutMs = 1_000L, queueWaitMs = 300_000L)
            val relayA = NormalizedRelayUrl("wss://a.relay.test")
            val relayB = NormalizedRelayUrl("wss://b.relay.test")

            val surfaced = async { bus.prompts.take(2).toList() }
            val callerA = async { bus.requestDecision(relayA, emptyList(), alice, isMyOwnRelay = false) }
            val callerB = async { bus.requestDecision(relayB, emptyList(), alice, isMyOwnRelay = false) }
            val prompts = surfaced.await()

            // Only A is on screen. The user's account-wide answer resolves B too, sight unseen.
            prompts[0].markShown()
            val start = clock.currentTime
            prompts.forEach {
                it.markShown()
                it.respond(UserAuthChoice.ALWAYS_ALLOW_EVERYWHERE)
            }

            assertEquals(UserAuthChoice.ALWAYS_ALLOW_EVERYWHERE, callerA.await())
            assertEquals(UserAuthChoice.ALWAYS_ALLOW_EVERYWHERE, callerB.await())
            assertTrue("the queued relay waited ${clock.currentTime - start}ms for an answer it already had", clock.currentTime - start < 1_000)
        }

    @Test
    fun aPromptNoHostCanEverShowStillTimesOut() =
        runTest {
            // Nothing collects the flow, so nobody can answer. The relay coroutine must not hang
            // waiting for a dialog that will never appear.
            val bus = RelayAuthPromptBus(timeoutMs = 1_000L, queueWaitMs = 60_000L)

            assertEquals(UserAuthChoice.DISMISS, bus.ask())
        }

    @Test
    fun aSecondChallengeNeverResolvesTheDialogTheUserIsLookingAt() =
        runTest {
            val bus = RelayAuthPromptBus(timeoutMs = 1_000L)

            val surfaced = async { bus.prompts.first() }
            val owner = async { bus.ask() }
            val rider = async { bus.ask() }
            val prompt = surfaced.await()

            // The prompt is queued behind another dialog well past the rider's old deadline. The
            // rider has no dialog of its own, so it must simply wait: if it ran its own clock it
            // would complete the shared deferred at 1000ms and yank this prompt away before the
            // user ever saw it.
            delay(1_500)
            prompt.markShown()
            delay(300)
            prompt.respond(UserAuthChoice.ALWAYS_ALLOW)

            assertEquals(UserAuthChoice.ALWAYS_ALLOW, owner.await())
            assertEquals(UserAuthChoice.ALWAYS_ALLOW, rider.await())
        }
}
