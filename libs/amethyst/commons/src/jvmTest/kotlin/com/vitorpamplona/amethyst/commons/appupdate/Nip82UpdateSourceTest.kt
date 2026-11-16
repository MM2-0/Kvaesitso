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
package com.vitorpamplona.amethyst.commons.appupdate

import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.BasicOkHttpWebSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertFailsWith

class Nip82UpdateSourceTest {
    /**
     * A relay nobody answers is "could not check", not "nothing new": the manager turns the first
     * into a failed check it retries soon, and the second into "up to date" for 12 hours.
     */
    @Test
    fun noRelayAnsweringIsAFailureNotAnEmptyAnswer() =
        runBlocking<Unit> {
            val closedPort = ServerSocket(0).use { it.localPort }
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            try {
                val client = NostrClient(BasicOkHttpWebSocket.Builder { OkHttpClient() }, parentScope = scope)
                val source = Nip82UpdateSource(client, relays = listOf("ws://127.0.0.1:$closedPort"), idleTimeoutMs = 3_000)
                assertFailsWith<UpdateSourceUnreachableException> {
                    source.findUpdate("1.0.0", UpdateTarget("linux-x86_64", listOf(DesktopPackage.APPIMAGE)))
                }
            } finally {
                scope.cancel()
            }
        }
}
