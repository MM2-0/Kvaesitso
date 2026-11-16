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
package com.vitorpamplona.amethyst.commons.model.nipFAAgentKeys

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nipFAAgentKeys.AgentKeyEvent
import com.vitorpamplona.quartz.nipFAAgentKeys.tags.AgentKeyTag
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgentKeyEditorTest {
    private val signer = NostrSignerInternal(KeyPair())
    private val claude = AgentKeyTag("sk-ant-test", "https://api.anthropic.com/v1")

    private suspend fun signed(vararg keys: AgentKeyTag): AgentKeyEvent = signer.sign(AgentKeyEvent.build(keys.toList(), signer, 1_800_000_000))

    private class Recorder {
        val kept = mutableListOf<Event>()
        val sent = mutableListOf<Event>()
    }

    private fun TestScope.editor(
        recorder: Recorder,
        read: suspend () -> Unit = {},
        rewrite: suspend (AgentKeyEditKind, AgentKeyTag) -> AgentKeyEvent? = { _, key -> signed(key) },
        current: () -> AgentKeyEvent? = { null },
        accepted: suspend () -> Boolean = { true },
        /** A screen shows the editor's state. */
        watched: Boolean = true,
    ) = AgentKeyEditor(
        scope = backgroundScope,
        readBeforeWriting = read,
        rewrite = rewrite,
        current = current,
        keepHere = { recorder.kept += it },
        publish = {
            recorder.sent += it
            accepted()
        },
    ).also { if (watched) it.attach() }

    @Test
    fun busyFromTheTapUntilARelayTakesIt() =
        runTest {
            val relaysRead = CompletableDeferred<Unit>()
            val relayAnswer = CompletableDeferred<Boolean>()
            val recorder = Recorder()
            val editor = editor(recorder, read = { relaysRead.await() }, accepted = { relayAnswer.await() })

            val result = editor.submit(AgentKeyEditKind.SAVE, claude)!!
            // Busy right away, before the relays were even read.
            assertEquals(AgentKeyEditState.Busy(AgentKeyEditKind.SAVE, claude.url), editor.state.value)
            // A second tap while it saves starts nothing.
            assertNull(editor.submit(AgentKeyEditKind.REMOVE, claude))

            relaysRead.complete(Unit)
            runCurrent()
            // Kept on this device, so the screen shows the new key while the relays answer.
            assertEquals(AgentKeyEditState.Busy(AgentKeyEditKind.SAVE, claude.url, savedHere = true), editor.state.value)
            assertEquals(1, recorder.kept.size)

            relayAnswer.complete(true)
            assertEquals(AgentKeyEditState.Done(AgentKeyEditKind.SAVE, claude.url), result.await())
            assertEquals(result.await(), editor.state.value)
        }

    @Test
    fun relaysThatDidNotAnswerChangeNothing() =
        runTest {
            val recorder = Recorder()
            val editor = editor(recorder, read = { throw AgentKeysNoQuorumException(3, 1) })
            val ended = editor.run(AgentKeyEditKind.SAVE, claude)
            assertEquals(AgentKeyEditState.Failed(AgentKeyEditKind.SAVE, claude.url, AgentKeyEditFailure.NO_QUORUM), ended)
            assertTrue(recorder.kept.isEmpty())
            assertTrue(recorder.sent.isEmpty())
        }

    @Test
    fun noWriteRelaysIsSaidSo() =
        runTest {
            val recorder = Recorder()
            val editor = editor(recorder, read = { throw AgentKeysNoWriteRelaysException() })
            assertEquals(AgentKeyEditState.Failed(AgentKeyEditKind.REMOVE, claude.url, AgentKeyEditFailure.NO_WRITE_RELAYS), editor.run(AgentKeyEditKind.REMOVE, claude))
            assertTrue(recorder.sent.isEmpty())
        }

    @Test
    fun anyFailedEditCanBeTriedAgain() =
        runTest {
            val recorder = Recorder()
            var accept = false
            val existing = signed(claude)
            var calls = 0
            // The first make-default is kept here but no relay takes it; the retry changes nothing more and re-sends it.
            val editor = editor(recorder, rewrite = { _, key -> if (calls++ == 0) signed(key) else null }, current = { existing }, accepted = { accept })
            assertIs<AgentKeyEditState.Failed>(editor.run(AgentKeyEditKind.MAKE_DEFAULT, claude))
            accept = true
            val again = editor.retry()!!
            assertEquals(AgentKeyEditState.Done(AgentKeyEditKind.MAKE_DEFAULT, claude.url), again.await())
            assertEquals(listOf<Event>(existing), recorder.sent.drop(1))
            // Nothing failed: nothing to retry.
            assertNull(editor.retry())
        }

    @Test
    fun theBusyStateSaysWhichEdit() =
        runTest {
            val hold = CompletableDeferred<Unit>()
            val editor = editor(Recorder(), read = { hold.await() })
            editor.submit(AgentKeyEditKind.REMOVE, claude)
            // The Save button doesn't say "Saving" while a removal is on its way.
            assertTrue(editor.state.value.isBusyWith(AgentKeyEditKind.REMOVE))
            assertFalse(editor.state.value.isBusyWith(AgentKeyEditKind.SAVE))
            hold.complete(Unit)
        }

    @Test
    fun anEditThatEndsAfterItsScreenWentLeavesNothingStale() =
        runTest {
            val hold = CompletableDeferred<Unit>()
            val editor = editor(Recorder(), read = { hold.await() }, accepted = { false }, watched = false)
            val release = editor.attach()
            val result = editor.submit(AgentKeyEditKind.SAVE, claude)!!
            // The screen goes while the edit is on its way.
            release()
            hold.complete(Unit)
            assertIs<AgentKeyEditState.Failed>(result.await())
            // The next time the screen opens it doesn't show a failure it never saw.
            assertEquals(AgentKeyEditState.Idle, editor.state.value)
        }

    @Test
    fun aSignerThatRefusesIsSaidSo() =
        runTest {
            val editor = editor(Recorder(), rewrite = { _, _ -> throw SignerExceptions.ManuallyUnauthorizedException("no") })
            val ended = editor.run(AgentKeyEditKind.MAKE_DEFAULT, claude)
            assertIs<AgentKeyEditState.Failed>(ended)
            assertEquals(AgentKeyEditFailure.SIGNER_REFUSED, ended.failure)
        }

    @Test
    fun noRelayTakingItIsNotAccepted() =
        runTest {
            val recorder = Recorder()
            val editor = editor(recorder, accepted = { false })
            val ended = editor.run(AgentKeyEditKind.SAVE, claude)
            assertEquals(AgentKeyEditState.Failed(AgentKeyEditKind.SAVE, claude.url, AgentKeyEditFailure.NOT_ACCEPTED), ended)
            // It is on this device all the same.
            assertEquals(1, recorder.kept.size)
        }

    @Test
    fun aRetryThatChangesNothingSendsWhatIsThere() =
        runTest {
            val recorder = Recorder()
            val existing = signed(claude)
            val editor = editor(recorder, rewrite = { _, _ -> null }, current = { existing })
            assertEquals(AgentKeyEditState.Done(AgentKeyEditKind.SAVE, claude.url), editor.run(AgentKeyEditKind.SAVE, claude))
            assertTrue(recorder.kept.isEmpty())
            assertEquals(listOf<Event>(existing), recorder.sent)
        }

    @Test
    fun clearingKeepsAnEditOnItsWay() =
        runTest {
            val hold = CompletableDeferred<Unit>()
            val editor = editor(Recorder(), read = { hold.await() })
            editor.submit(AgentKeyEditKind.REMOVE, claude)
            editor.clearResult()
            assertTrue(editor.state.value.isBusy)
            hold.complete(Unit)
            runCurrent()
            editor.clearResult()
            assertEquals(AgentKeyEditState.Idle, editor.state.value)
        }
}
