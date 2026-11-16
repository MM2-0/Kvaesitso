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
package com.vitorpamplona.nestsclient

import com.vitorpamplona.nestsclient.moq.SubscribeHandle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

class NestsListenerCatalogTest {
    private class IetfStyleListener : NestsListener {
        override val state: StateFlow<NestsListenerState> =
            MutableStateFlow<NestsListenerState>(NestsListenerState.Idle).asStateFlow()

        override suspend fun subscribeSpeaker(
            speakerPubkeyHex: String,
            maxLatencyMs: Long,
        ): SubscribeHandle = throw UnsupportedOperationException("not under test")

        // Intentionally does NOT override subscribeCatalog so the
        // interface's default body fires.

        override suspend fun close() = Unit
    }

    @Test
    fun ietfDefaultListenerRejectsCatalogSubscribe() =
        runTest {
            // The catalog channel is moq-lite only; the IETF listener
            // path doesn't define one, so the interface default throws
            // UnsupportedOperationException rather than silently
            // returning a subscription that will never deliver data.
            val listener = IetfStyleListener()
            assertFailsWith<UnsupportedOperationException> {
                listener.subscribeCatalog("speakerPubkey")
            }
        }

    @Test
    fun ietfDefaultListenerAnnouncesFlowThrowsAtCallTime() =
        runTest {
            // The announce-prefix channel is moq-lite only as well —
            // the IETF reference path's announces() default body
            // throws UnsupportedOperationException at CALL time,
            // matching subscribeCatalog's timing. Callers can
            // `runCatching { listener.announces() }` once per session
            // rather than wrapping every collect.
            val listener = IetfStyleListener()
            assertFailsWith<UnsupportedOperationException> {
                listener.announces()
            }
        }
}
