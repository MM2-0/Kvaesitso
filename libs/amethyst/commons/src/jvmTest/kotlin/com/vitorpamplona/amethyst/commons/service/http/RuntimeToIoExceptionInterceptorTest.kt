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
package com.vitorpamplona.amethyst.commons.service.http

import okhttp3.Call
import okhttp3.Callback
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RuntimeToIoExceptionInterceptorTest {
    /**
     * OkHttp accepts this URL but throws `IllegalArgumentException: unexpected host` on the
     * host it encoded (`stardroid.xn--app)-3z8b`) when it connects. On an enqueued call it
     * reports the failure and then rethrows the exception on the Dispatcher thread, which
     * crashes the app. With the interceptor it is only an [IOException] in onFailure.
     */
    @Test
    fun malformedHostFailsTheCallInsteadOfCrashingTheDispatcherThread() {
        val uncaught = CopyOnWriteArrayList<Throwable>()
        val executor =
            Executors.newCachedThreadPool { runnable ->
                Thread(runnable).apply { setUncaughtExceptionHandler { _, e -> uncaught.add(e) } }
            }
        val client =
            OkHttpClient
                .Builder()
                .dispatcher(Dispatcher(executor))
                .addInterceptor(RuntimeToIoExceptionInterceptor())
                .build()

        val failures = CopyOnWriteArrayList<IOException>()
        val done = CountDownLatch(1)
        client.newCall(Request.Builder().url("https://stardroid.app)⭐/.well-known/lnurlp/name").build()).enqueue(
            object : Callback {
                override fun onFailure(
                    call: Call,
                    e: IOException,
                ) {
                    failures.add(e)
                    done.countDown()
                }

                override fun onResponse(
                    call: Call,
                    response: Response,
                ) {
                    response.close()
                    done.countDown()
                }
            },
        )

        assertTrue(done.await(10, TimeUnit.SECONDS))
        executor.shutdown()
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS))

        assertEquals(emptyList(), uncaught.toList())
        assertEquals(1, failures.size)
        assertTrue(failures[0].cause is IllegalArgumentException, "cause was ${failures[0].cause}")
    }
}
