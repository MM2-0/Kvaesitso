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
package com.vitorpamplona.amethyst.commons.cordn

import com.vitorpamplona.cordn.client.CordnHandedOffException
import com.vitorpamplona.cordn.client.CordnHandoffState
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The flag the whole migration design rests on.
 *
 * Two phones holding one MLS leaf and both committing fork the ratchet tree,
 * and MLS does not recover: after the fork every message silently fails to
 * decrypt for somebody. Migration is only safe because the old phone stops —
 * so the cases that matter are the ones where it fails to stop, or stops and
 * cannot be brought back.
 */
class CordnHandoffStateTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun `a fresh device has not handed off`() =
        runTest {
            val state = state()
            state.restore()

            assertFalse(state.handedOff.value)
            state.requireNotHandedOff()
        }

    @Test
    fun `a handed-off device refuses to write`() =
        runTest {
            val state = state()
            state.markHandedOff()

            assertTrue(state.handedOff.value)
            val thrown = assertThrows(CordnHandedOffException::class.java) { state.requireNotHandedOff() }
            assertTrue(thrown.message!!.contains("fork"))
        }

    @Test
    fun `the flag survives a restart`() =
        runTest {
            // The case that matters: a device that forgot it had handed off
            // would resume committing on the next launch, which is the fork.
            state().markHandedOff()

            val afterRestart = state()
            afterRestart.restore()

            assertTrue(afterRestart.handedOff.value)
        }

    @Test
    fun `a handoff can be undone, and the undo survives a restart`() =
        runTest {
            // A migration can fail after the export — a flat battery, a QR that
            // will not scan. Locking irreversibly would strand the account on
            // the device that still holds the only copy of its state.
            val state = state()
            state.markHandedOff()

            state.resume()

            assertFalse(state.handedOff.value)
            state.requireNotHandedOff()

            val afterRestart = state()
            afterRestart.restore()
            assertFalse(afterRestart.handedOff.value)
        }

    @Test
    fun `restore reflects what is on disk, not what this instance did`() =
        runTest {
            val first = state()
            first.restore()
            assertFalse(first.handedOff.value)

            state().markHandedOff()
            first.restore()

            assertTrue(first.handedOff.value)
        }

    private fun state() = CordnHandoffState(FileCordnHandoffStore(folder.root.toOkioPath()))
}
