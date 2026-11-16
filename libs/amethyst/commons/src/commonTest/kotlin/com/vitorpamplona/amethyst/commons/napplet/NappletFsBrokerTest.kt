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
package com.vitorpamplona.amethyst.commons.napplet

import com.vitorpamplona.amethyst.commons.napplet.fs.FakeNappletFilePort
import com.vitorpamplona.amethyst.commons.napplet.fs.NappletFsError
import com.vitorpamplona.amethyst.commons.napplet.fs.NappletFsSession
import com.vitorpamplona.amethyst.commons.napplet.fs.NappletPickedFile
import com.vitorpamplona.amethyst.commons.napplet.permissions.GrantState
import com.vitorpamplona.amethyst.commons.napplet.permissions.InMemoryNappletPermissionStore
import com.vitorpamplona.amethyst.commons.napplet.permissions.NappletPermissionLedger
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletRequest
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletResponse
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** How the broker gates NAP-FS: consent covers picking; what was picked needs no further question. */
class NappletFsBrokerTest {
    private val signer = NostrSignerInternal(KeyPair("00".repeat(31).plus("07").hexToByteArray()))
    private val applet = NappletIdentity(authorPubKey = "aa".repeat(32), identifier = "fotohop", instanceId = "run-1")

    private class CountingPrompt(
        private val answer: GrantState,
    ) : NappletConsentPrompt {
        val asked = mutableListOf<NappletRequest>()

        override suspend fun request(
            identity: NappletIdentity,
            capability: NappletCapability,
            request: NappletRequest,
        ): GrantState {
            asked += request
            return answer
        }
    }

    private val port = FakeNappletFilePort()
    private val files = NappletFsSession(port)
    private val inlineAsks = NappletInlineAsks()

    private fun broker(prompt: NappletConsentPrompt) = NappletBroker(signer, NappletPermissionLedger(InMemoryNappletPermissionStore()), prompt, inlineAsks = inlineAsks)

    @Test
    fun aPickAsksOnceAndWhatItGrantsNeedsNoQuestion() =
        runTest {
            val prompt = CountingPrompt(GrantState.ALLOW_SESSION)
            val broker = broker(prompt)
            port.disk["h"] = "hi".encodeToByteArray()
            port.opens.addLast(listOf(NappletPickedFile("h", "a.txt", 2)))

            assertIs<NappletResponse.FsInfoResult>(broker.handle(applet, NappletRequest.FsInfo, setOf(NappletCapability.FS), files))
            assertTrue(prompt.asked.isEmpty())

            val picked = assertIs<NappletResponse.FsPicked>(broker.handle(applet, NappletRequest.FsPick(NappletRequest.FsPickKind.FILE), setOf(NappletCapability.FS), files))
            assertEquals(1, prompt.asked.size)
            val read = broker.handle(applet, NappletRequest.FsRead(picked.entries.single().path), setOf(NappletCapability.FS), files)
            assertEquals("hi", assertIs<NappletResponse.FsReadResult>(read).bytes.decodeToString())
            assertEquals(1, prompt.asked.size)
        }

    @Test
    fun aDeclinedPickNeverOpensThePicker() =
        runTest {
            val broker = broker(CountingPrompt(GrantState.DENY))
            val response = broker.handle(applet, NappletRequest.FsPick(NappletRequest.FsPickKind.SAVE_FILE), setOf(NappletCapability.FS), files)
            assertIs<NappletResponse.Denied>(response)
            assertEquals(0, port.pickerShown)
        }

    @Test
    fun anUndeclaredDomainIsRefused() =
        runTest {
            val response = broker(CountingPrompt(GrantState.ALLOW_ALWAYS)).handle(applet, NappletRequest.FsInfo, setOf(NappletCapability.THEME), files)
            assertIs<NappletResponse.Denied>(response)
        }

    @Test
    fun aHostWithoutFilesAnswersUnsupported() =
        runTest {
            val response = broker(CountingPrompt(GrantState.ALLOW_ALWAYS)).handle(applet, NappletRequest.FsInfo, setOf(NappletCapability.FS), null)
            assertEquals(NappletResponse.FsFailure(NappletFsError.UNSUPPORTED), response)
        }

    @Test
    fun anAppInsideANoteNeverOpensAPickerEvenWithAStandingGrant() =
        runTest {
            val ledger = NappletPermissionLedger(InMemoryNappletPermissionStore())
            val inline = applet.copy(inline = true, instanceId = "note-run")
            ledger.record(inline, NappletCapability.FS, GrantState.ALLOW_ALWAYS)
            val prompt = CountingPrompt(GrantState.ALLOW_ALWAYS)
            val broker = NappletBroker(signer, ledger, prompt, inlineAsks = inlineAsks)

            val response = broker.handle(inline, NappletRequest.FsPick(NappletRequest.FsPickKind.FILE), setOf(NappletCapability.FS), files)
            assertEquals(NappletResponse.FsFailure(NappletFsError.POLICY_DENIED), response)
            assertEquals(0, port.pickerShown)
            assertTrue(prompt.asked.isEmpty())
            // The note offers to open the app, where the pick can happen.
            assertTrue(inlineAsks.asked("note-run").first())
        }
}
