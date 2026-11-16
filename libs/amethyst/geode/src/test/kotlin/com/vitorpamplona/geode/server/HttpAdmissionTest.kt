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
package com.vitorpamplona.geode.server

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals

class HttpAdmissionTest {
    @Test
    fun aClientOverItsShareIsBusyAndARelayOverItsCapIsTooWhileOthersStillRun() =
        runBlocking {
            val admission = HttpAdmission(maxConcurrent = 2, maxPerClient = 1)
            val release = CompletableDeferred<Unit>()
            val a = async { admission.admit("a") { release.await() } }
            val b = async { admission.admit("b") { release.await() } }
            while (admission.inFlight < 2) yield()

            assertEquals(HttpAdmission.Verdict.CLIENT_BUSY, admission.admit("a") {})
            assertEquals(HttpAdmission.Verdict.RELAY_BUSY, admission.admit("c") {})

            release.complete(Unit)
            assertEquals(HttpAdmission.Verdict.ADMITTED, a.await())
            assertEquals(HttpAdmission.Verdict.ADMITTED, b.await())
            assertEquals(0, admission.inFlight)
            assertEquals(HttpAdmission.Verdict.ADMITTED, admission.admit("a") {})
            assertEquals(HttpAdmission.Verdict.ADMITTED, admission.admit("c") {})
        }

    @Test
    fun zeroIsNoLimit() =
        runBlocking {
            val admission = HttpAdmission(maxConcurrent = 0, maxPerClient = 0)
            val release = CompletableDeferred<Unit>()
            val held = List(50) { async { admission.admit("a") { release.await() } } }
            while (admission.inFlight < 50) yield()
            assertEquals(HttpAdmission.Verdict.ADMITTED, admission.admit("a") {})
            release.complete(Unit)
            held.forEach { assertEquals(HttpAdmission.Verdict.ADMITTED, it.await()) }
        }

    @Test
    fun aFailingRequestStillLeaves() =
        runBlocking {
            val admission = HttpAdmission(maxConcurrent = 1, maxPerClient = 1)
            runCatching { admission.admit("a") { error("boom") } }
            assertEquals(0, admission.inFlight)
            assertEquals(HttpAdmission.Verdict.ADMITTED, admission.admit("a") {})
        }
}
