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
package com.vitorpamplona.amethyst.commons.relays.nip11RelayInfo

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.net.ConnectException

class Nip11RetrieverTest {
    @Test
    fun unreachableServerReportsFailToReachServer() =
        runBlocking {
            // Inject a fake client that simulates connection refused without building
            // a real OkHttpClient (which fails in amethyst JVM unit tests because
            // the Android OkHttp variant tries to detect Android via android.util.Log).
            val retriever =
                Nip11Retriever { _ ->
                    throw ConnectException("Connection refused")
                }
            val relay = NormalizedRelayUrl("ws://127.0.0.1:14591/")

            var errorCode: Nip11ErrorCode? = null
            retriever.loadRelayInfo(
                relay = relay,
                onInfo = { fail("Expected an error, got relay info") },
                onError = { _, code, _ -> errorCode = code },
            )

            assertEquals(Nip11ErrorCode.FAIL_TO_REACH_SERVER, errorCode)
        }
}
