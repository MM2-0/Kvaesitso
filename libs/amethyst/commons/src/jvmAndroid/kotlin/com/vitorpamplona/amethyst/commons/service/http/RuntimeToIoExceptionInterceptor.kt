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

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Turns any unchecked exception thrown inside the call chain into an [IOException].
 *
 * OkHttp's async calls (`enqueue`, and `executeAsync` from okhttp-coroutines, which
 * enqueues) report an [IOException] to the callback but RETHROW anything else on the
 * "OkHttp Dispatcher" thread, where nothing catches it and the whole app dies. The
 * caller's own try/catch never sees it.
 *
 * OkHttp itself throws one: it accepts `https://stardroid.app)⭐` when parsing (the label
 * becomes `xn--app)-3z8b`) but rejects that same host with
 * `IllegalArgumentException: unexpected host` when it opens the connection. Any URL that
 * comes from a user-written field (lud16, website, image link) can carry such a host, so
 * this must be the first interceptor: it wraps every other one and the connection
 * step, and the call then fails like any other network error.
 */
class RuntimeToIoExceptionInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response =
        try {
            chain.proceed(chain.request())
        } catch (e: RuntimeException) {
            throw IOException("Request to ${chain.request().url.host} failed: $e", e)
        }
}
