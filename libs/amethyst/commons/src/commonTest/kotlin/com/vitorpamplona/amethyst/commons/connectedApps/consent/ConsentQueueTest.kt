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
package com.vitorpamplona.amethyst.commons.connectedApps.consent

import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerOp
import com.vitorpamplona.amethyst.commons.connectedApps.signers.SignerOpGrant
import com.vitorpamplona.amethyst.commons.napplet.NappletConsentInfo
import com.vitorpamplona.amethyst.commons.napplet.NappletIdentity
import com.vitorpamplona.amethyst.commons.napplet.NappletPromptQueues
import com.vitorpamplona.amethyst.commons.napplet.permissions.GrantState
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConsentQueueTest {
    private fun signing(account: String) = SignerConsentInfo("App", "a:app", NostrSignerOp.SignKind(1), "Sign a note", "hi", accountPubKey = account)

    private fun capability() = NappletConsentInfo("App", "a:app", "Storage", "Store data", allowAlways = true)

    @Test
    fun anAnsweredQuestionReturnsItsAnswerAndLeaves() =
        runTest {
            val queue = ConsentQueue<String, Int>(unanswered = -1)
            val answer = async { queue.ask("may I?") }
            yield()
            val request = queue.pending.value.single()
            assertEquals("may I?", queue.info(request.token))
            queue.answer(request.token, 7)
            assertEquals(7, answer.await())
            assertTrue(queue.pending.value.isEmpty())
            assertNull(queue.info(request.token))
        }

    @Test
    fun aDismissedQuestionFailsClosed() =
        runTest {
            val queue = ConsentQueue<String, SignerOpGrant>(unanswered = SignerOpGrant.DenyOnce)
            val answer = async { queue.ask("sign?") }
            yield()
            queue.dismiss(
                queue.pending.value
                    .single()
                    .token,
            )
            assertEquals(SignerOpGrant.DenyOnce, answer.await())
        }

    @Test
    fun aClosedAppsQuestionsAreRefusedAndNoOneElses() =
        runTest {
            val queue = ConsentQueue<String, GrantState>(unanswered = GrantState.ASK)
            val closed = async { queue.ask("a", app = "closed") }
            val open = async { queue.ask("b", app = "open") }
            yield()
            queue.dismissApp("closed")
            assertEquals(GrantState.ASK, closed.await())
            assertEquals(listOf("open"), queue.pending.value.map { it.app })
            queue.pending.value
                .single()
                .answer(GrantState.ALLOW_ONCE)
            assertEquals(GrantState.ALLOW_ONCE, open.await())
        }

    @Test
    fun aCallerThatGivesUpLeavesTheQueueAndTakesDownItsDialog() =
        runTest {
            val queue = ConsentQueue<String, Int>(unanswered = -1)
            var shown = 0
            var settled = 0
            val answer =
                withTimeoutOrNull(1_000) {
                    queue.ask("sign?", onQueued = { shown++ }, onSettled = { settled++ })
                }
            assertNull(answer)
            assertEquals(1, shown)
            assertEquals(1, settled, "the dialog is taken down even though the caller was cancelled")
            assertTrue(queue.pending.value.isEmpty())
        }

    @Test
    fun questionsFromSeveralQueuesSortOldestFirst() =
        runTest {
            val app = NappletIdentity("f".repeat(64), "app", "h")
            val queues = NappletPromptQueues()
            val signing = signing("alice")
            val capability = capability()
            val signed = async { queues.signerOp(app, signing) }
            yield()
            val allowed = async { queues.capability(app, capability) }
            yield()
            val waiting = (queues.capabilities.pending.value + queues.signerOps.pending.value).sortedBy { it.order }
            assertEquals(listOf<Any>(signing, capability), waiting.map { it.info })

            queues.dismissApp(app.coordinate)
            assertEquals(SignerOpGrant.DenyOnce, signed.await())
            assertEquals(GrantState.ASK, allowed.await())
        }

    @Test
    fun requestsThatNameNoAccountAreStillShown() =
        runTest {
            val queue = ConsentQueue<SignerConsentInfo, SignerOpGrant>(unanswered = SignerOpGrant.DenyOnce)
            val unnamed = SignerConsentInfo("App", "a:app", NostrSignerOp.SignKind(1), "Sign a note", "hi")
            val answers = List(2) { async { queue.ask(unnamed) } }
            async { queue.ask(signing("alice")) }
            yield()
            assertEquals(
                2,
                queue.pending.value
                    .oldestAccountGroup()
                    .size,
                "the oldest requests, with no account, form the sheet",
            )
            queue.pending.value.forEach { it.dismiss() }
            answers.forEach { assertEquals(SignerOpGrant.DenyOnce, it.await()) }
        }

    @Test
    fun aBatchNeverMixesAccounts() =
        runTest {
            val queue = ConsentQueue<SignerConsentInfo, SignerOpGrant>(unanswered = SignerOpGrant.DenyOnce)
            val answers = listOf("alice", "bob", "alice").map { account -> async { queue.ask(signing(account)) } }
            yield()
            val group = queue.pending.value.oldestAccountGroup()
            assertEquals(listOf("alice", "alice"), group.map { it.info.accountPubKey })
            group.forEach { it.answer(SignerOpGrant.AllowOnce) }
            // An answered request leaves the queue once its caller resumes.
            yield()
            assertEquals(
                listOf("bob"),
                queue.pending.value
                    .oldestAccountGroup()
                    .map { it.info.accountPubKey },
            )
            queue.pending.value.forEach { it.dismiss() }
            assertEquals(listOf(SignerOpGrant.AllowOnce, SignerOpGrant.DenyOnce, SignerOpGrant.AllowOnce), answers.map { it.await() })
        }
}
