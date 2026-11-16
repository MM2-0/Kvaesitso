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
package com.vitorpamplona.amethyst.service.uploads.blossom.bud10

import com.vitorpamplona.amethyst.commons.service.http.IRoleBasedHttpClientBuilder
import com.vitorpamplona.amethyst.commons.service.upload.blossom.bud10.BlossomServerResolver
import com.vitorpamplona.quartz.nipB7Blossom.BlossomServersEvent
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class BlossomServerResolverTest {
    private val sha = "b1674191a88ec5cdd733e4240a81803105dc412d6c6708d53ab94fc248f4f553"
    private val author = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After
    fun tearDown() = scope.cancel()

    /** A resolver whose author server-list lookups are counted and answered by [serverList]. */
    private fun resolver(
        lookups: AtomicInteger,
        serverList: () -> Flow<BlossomServersEvent>,
    ) = BlossomServerResolver(
        loggedInUsers = { emptyList() },
        blossomServers = { addresses ->
            lookups.incrementAndGet()
            addresses.map { serverList() }
        },
        httpClientBuilder = mockk<IRoleBasedHttpClientBuilder>(relaxed = true),
        scope = scope,
    )

    @Test
    fun concurrentLookupsOfTheSameUriShareOneResolution() =
        runBlocking {
            val lookups = AtomicInteger(0)
            // The author's server list never arrives, like a relay that is slow to answer.
            val resolver = resolver(lookups) { flow { awaitCancellation() } }
            val uri = "blossom:$sha.jpg?as=$author"

            val callers = List(5) { scope.launch { resolver.findServers(uri) } }
            delay(300)

            assertEquals(1, lookups.get())
            callers.forEach { it.cancel() }
        }

    @Test
    fun anUnresolvableUriIsNotRetriedUntilTheMissExpiresOrCachesAreCleared() =
        runBlocking {
            val lookups = AtomicInteger(0)
            // No hints and no authors: resolves to nothing right away.
            val resolver = resolver(lookups) { flow { } }
            val uri = "blossom:$sha.jpg"

            assertNull(resolver.findServers(uri))
            assertNull(resolver.findServers(uri))
            assertEquals(1, lookups.get())

            resolver.clearCaches()
            assertNull(resolver.findServers(uri))
            assertEquals(2, lookups.get())
        }
}
