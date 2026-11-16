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
package com.vitorpamplona.amethyst.commons.domain.nip46

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RemoteSignerAuthPromptsTest {
    @Test
    fun onlyWebPagesAreShown() {
        assertTrue(RemoteSignerAuthPrompts.isWebPage("https://signer.example/confirm?id=1"))
        assertTrue(RemoteSignerAuthPrompts.isWebPage("HTTPS://SIGNER.EXAMPLE/"))
        assertTrue(RemoteSignerAuthPrompts.isWebPage("http://127.0.0.1:8080/auth"))

        // The signer's address is handed to the platform: none of these may reach it.
        assertFalse(RemoteSignerAuthPrompts.isWebPage("intent://scan#Intent;scheme=zxing;end"))
        assertFalse(RemoteSignerAuthPrompts.isWebPage("javascript:alert(1)"))
        assertFalse(RemoteSignerAuthPrompts.isWebPage("file:///etc/passwd"))
        assertFalse(RemoteSignerAuthPrompts.isWebPage("nostr:npub1abc"))
        assertFalse(RemoteSignerAuthPrompts.isWebPage("https://"))
        assertFalse(RemoteSignerAuthPrompts.isWebPage("https://signer.example/a b"))
        assertFalse(RemoteSignerAuthPrompts.isWebPage(" https://signer.example/"))
        assertFalse(RemoteSignerAuthPrompts.isWebPage(""))
    }

    private fun RemoteSignerAuthPrompts.urls() = pending.value.map { it.url }

    @Test
    fun queuesEachPageOnceUntilDismissed() {
        val prompts = RemoteSignerAuthPrompts()
        prompts.report("https://signer.example/a")
        prompts.report("javascript:alert(1)")
        prompts.report("https://signer.example/a")
        prompts.report("https://signer.example/b")
        assertEquals(listOf("https://signer.example/a", "https://signer.example/b"), prompts.urls())

        prompts.dismiss(prompts.pending.value.first())
        assertEquals(listOf("https://signer.example/b"), prompts.urls())
    }

    @Test
    fun aSignerThatChallengesEverythingCannotStackPrompts() {
        val prompts = RemoteSignerAuthPrompts()
        repeat(20) { prompts.report("https://signer.example/$it") }
        assertEquals(5, prompts.pending.value.size)
        // The newest stays: it is the one a request is still waiting on.
        assertEquals("https://signer.example/19", prompts.urls().last())
        assertEquals("https://signer.example/15", prompts.urls().first())
    }

    @Test
    fun aPageComesDownWhenItsRequestIsOver() {
        val prompts = RemoteSignerAuthPrompts()
        prompts.report("https://signer.example/a")
        prompts.report("https://signer.example/b")
        prompts.settled("https://signer.example/a")
        assertEquals(listOf("https://signer.example/b"), prompts.urls())
    }

    @Test
    fun aPageSharedByTwoRequestsStaysUntilBothAreOver() {
        val prompts = RemoteSignerAuthPrompts()
        // One approval page for the session: a decrypt and a sign both wait on it.
        prompts.report("https://signer.example/session")
        prompts.report("https://signer.example/session")
        assertEquals(1, prompts.pending.value.size)

        prompts.settled("https://signer.example/session")
        assertEquals(listOf("https://signer.example/session"), prompts.urls(), "the sign is still waiting")
        prompts.settled("https://signer.example/session")
        assertEquals(emptyList(), prompts.urls())
    }

    @Test
    fun eachAccountsPageIsItsOwn() {
        val prompts = RemoteSignerAuthPrompts()
        prompts.report("https://signer.example/a", account = "alice")
        prompts.report("https://signer.example/a", account = "bob")
        assertEquals(listOf("alice", "bob"), prompts.pending.value.map { it.account })

        prompts.settled("https://signer.example/a", account = "alice")
        assertEquals(listOf("bob"), prompts.pending.value.map { it.account })
    }
}
