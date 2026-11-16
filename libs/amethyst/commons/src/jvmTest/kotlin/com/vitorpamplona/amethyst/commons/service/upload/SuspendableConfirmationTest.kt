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
package com.vitorpamplona.amethyst.commons.service.upload

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SuspendableConfirmationTest {
    @Test
    fun confirmResumesTheWaiterAndClosesTheDialog() =
        runTest {
            val confirmation = SuspendableConfirmation()
            val answer = async { confirmation.awaitConfirmation() }
            runCurrent()

            assertNotNull(confirmation.state)
            confirmation.state!!.onConfirm()

            assertEquals(true, answer.await())
            assertNull(confirmation.state)
        }

    @Test
    fun twoCallbacksFromTheSameDialogResumeOnlyOnce() =
        runTest {
            // A button tap and an outside-tap dismiss can both land before the dialog recomposes
            // away. The second used to resume the continuation again and throw.
            val confirmation = SuspendableConfirmation()
            val answer = async { confirmation.awaitConfirmation() }
            runCurrent()

            val callbacks = confirmation.state!!
            callbacks.onConfirm()
            callbacks.onCancel()

            assertEquals(true, answer.await())
            assertNull(confirmation.state)
        }

    @Test
    fun cancellingTheWaiterClosesTheDialog() =
        runTest {
            val confirmation = SuspendableConfirmation()
            val answer = async { confirmation.awaitConfirmation() }
            runCurrent()
            assertNotNull(confirmation.state)

            answer.cancel()
            runCurrent()

            assertNull(confirmation.state)
        }

    @Test
    fun aStaleCallbackDoesNotCloseTheNextDialog() =
        runTest {
            val confirmation = SuspendableConfirmation()
            val first = async { confirmation.awaitConfirmation() }
            runCurrent()
            val firstCallbacks = confirmation.state!!
            firstCallbacks.onCancel()
            assertEquals(false, first.await())

            val second = async { confirmation.awaitConfirmation() }
            runCurrent()
            firstCallbacks.onConfirm()

            assertNotNull(confirmation.state)
            confirmation.state!!.onConfirm()
            assertEquals(true, second.await())
        }
}
