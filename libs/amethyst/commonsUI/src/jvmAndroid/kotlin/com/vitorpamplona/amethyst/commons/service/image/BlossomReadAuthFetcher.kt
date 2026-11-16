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
package com.vitorpamplona.amethyst.commons.service.image

import coil3.Extras
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.network.HttpException
import coil3.network.httpHeaders
import coil3.request.Options
import com.vitorpamplona.amethyst.commons.service.http.BlossomReadAuthInterceptor
import com.vitorpamplona.amethyst.commons.service.http.BlossomReadAuthTokenProvider
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Retries an auth-gated Blossom blob with a signed BUD-01 `t=get` token when the
 * anonymous fetch comes back `401`.
 *
 * This is the half of the read-auth flow that has to wait for a signature.
 * `BlossomReadAuthInterceptor` cannot: it runs on an OkHttp dispatcher thread,
 * so waiting there holds one of the 16 per-host slots and stalls every other
 * image from the same host. `Fetcher.fetch()` is `suspend`, so the wait costs a
 * suspended coroutine and nothing else.
 *
 * [build] produces the underlying network fetcher, optionally carrying an
 * `Authorization` header. Deliberately a `(String?) -> Fetcher` lambda rather
 * than taking Coil's [Options] directly — the retry decision is then testable
 * without an Android `Context` to construct [Options] with.
 */
class BlossomReadAuthFetcher(
    private val url: String,
    private val auth: BlossomReadAuthTokenProvider,
    private val build: (authHeader: String?) -> Fetcher,
) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        try {
            return build(null).fetch()
        } catch (e: HttpException) {
            // Coil's NetworkFetcher throws HttpException for any non-2xx/304,
            // which is how a 401 reaches us with its code intact.
            if (e.response.code != HTTP_UNAUTHORIZED) throw e

            val httpUrl = url.toHttpUrlOrNull() ?: throw e
            // Gate only: read-auth applies to Blossom blob URLs, but the token is
            // scoped to the host (BUD-11 `server` tag) and carries no `x` tag.
            BlossomReadAuthInterceptor.blossomHashOrNull(httpUrl.encodedPath) ?: throw e
            val header = auth.header(httpUrl.host) ?: throw e

            return build(header).fetch()
        }
    }

    companion object {
        private const val HTTP_UNAUTHORIZED = 401
    }
}

/**
 * Wraps [build] in read-auth handling when a token provider is configured, and
 * returns the plain fetcher when it isn't (tests, pre-configuration call sites).
 */
fun readAuthAware(
    url: String,
    auth: BlossomReadAuthTokenProvider?,
    build: (authHeader: String?) -> Fetcher,
): Fetcher =
    if (auth == null) {
        build(null)
    } else {
        BlossomReadAuthFetcher(url, auth, build)
    }

/**
 * Copy of these options carrying [header] as `Authorization`, or the same
 * options when there is no header. Coil's `NetworkFetcher` builds its request
 * from `options.httpHeaders`, so this is how the retry gets authenticated.
 */
fun Options.withAuthHeader(header: String?): Options =
    if (header == null) {
        this
    } else {
        copy(
            extras =
                extras
                    .newBuilder()
                    .set(
                        Extras.Key.httpHeaders,
                        httpHeaders.newBuilder().set("Authorization", header).build(),
                    ).build(),
        )
    }
