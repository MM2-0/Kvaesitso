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
package com.vitorpamplona.amethyst.commons.napplet.blobs

import com.vitorpamplona.amethyst.commons.napplet.gateways.NappletResourceFetcher
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.Call
import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Shared Blossom-blob HTTP fetcher, used by the hosts' blob stores and the prefetcher on Android and
 * desktop, so blobs always travel the same path with the same limits. No OkHttp disk cache here — durability
 * is the content-addressed [NappletBlobCache], which (unlike OkHttp's journaled cache) is multi-process
 * safe.
 */
object NappletBlobHttp {
    const val MAX_BLOB_BYTES = 20L * 1024 * 1024

    /**
     * An OkHttp client routed through the Tor SOCKS proxy when [proxyPort] > 0, else direct.
     *
     * [publicOnly] is for fetches nobody asked for (a card prefetching as it scrolls by): a server a
     * stranger named must not make the app reach the user's router, localhost or anything else on
     * their network, so every address a host resolves to must be public, redirects included, and an
     * https fetch is never redirected to plain http. Over Tor the exit resolves names, and it can't
     * reach the user's network anyway.
     */
    fun client(
        proxyPort: Int,
        publicOnly: Boolean = false,
    ): OkHttpClient {
        val builder =
            OkHttpClient
                .Builder()
                // Bound a single blob fetch end-to-end: a stalled Tor exit (which trickles bytes under
                // the 10s read timeout) must not block the WebView worker thread that called serve()
                // indefinitely.
                .callTimeout(BLOB_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (proxyPort > 0) builder.proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", proxyPort)))
        if (publicOnly) {
            builder
                .followSslRedirects(false)
                .cookieJar(CookieJar.NO_COOKIES)
                .dns { hostname ->
                    Dns.SYSTEM.lookup(hostname).also { addresses ->
                        if (addresses.isEmpty() || !addresses.all { NappletResourceFetcher.isPublicAddress(it) }) {
                            throw UnknownHostException("$hostname does not resolve to a public address")
                        }
                    }
                }
                // OkHttp never asks the Dns for an IP literal (https://192.168.1.1/): check the address
                // each hop actually connected to. Through a SOCKS proxy it is unresolved, and skipped.
                .addNetworkInterceptor { chain ->
                    val address =
                        chain
                            .connection()
                            ?.route()
                            ?.socketAddress
                            ?.address
                    if (address != null && !NappletResourceFetcher.isPublicAddress(address)) {
                        throw IOException("${chain.request().url.host} is not a public address")
                    }
                    chain.proceed(chain.request())
                }
        }
        return builder.build()
    }

    private const val BLOB_CALL_TIMEOUT_SECONDS = 45L

    /** What one fetch brought: the body ([bytes], null when refused or failed) and how much of it came in. */
    class Download(
        val bytes: ByteArray?,
        val received: Long,
    )

    /** Downloads [url], or null on error / when the body exceeds [MAX_BLOB_BYTES] (declared length). */
    fun download(
        client: OkHttpClient,
        url: String,
        maxBytes: Long = MAX_BLOB_BYTES,
    ): ByteArray? = read(client.newCall(request(url)), url, maxBytes).bytes

    /**
     * [download], stopped when the caller is cancelled: a card scrolled off screen doesn't keep up to
     * [MAX_BLOB_BYTES] coming in on a connection nobody will use. The call is cancelled, which ends a
     * blocking read that coroutine cancellation alone can't reach.
     */
    suspend fun downloadCancellable(
        client: OkHttpClient,
        url: String,
        maxBytes: Long = MAX_BLOB_BYTES,
    ): ByteArray? = downloadCounted(client, url, maxBytes).bytes

    /**
     * [downloadCancellable], also saying how many bytes came in whatever the outcome: a budget that
     * counted only bodies it kept would let a server send junk (or too much) for free.
     */
    suspend fun downloadCounted(
        client: OkHttpClient,
        url: String,
        maxBytes: Long = MAX_BLOB_BYTES,
    ): Download =
        coroutineScope {
            val call = client.newCall(request(url))
            val reading = async(Dispatchers.IO) { read(call, url, maxBytes) }
            try {
                reading.await()
            } catch (e: CancellationException) {
                call.cancel()
                throw e
            }
        }

    private fun request(url: String) =
        Request
            .Builder()
            .url(url)
            .get()
            .build()

    private fun read(
        call: Call,
        url: String,
        maxBytes: Long,
    ): Download {
        var received = 0L
        return try {
            call.execute().use { r ->
                val declared = r.body.contentLength()
                if (!r.isSuccessful || declared > maxBytes) {
                    Download(null, 0)
                } else {
                    // A server that sends no length is still held to the cap: buffer one byte past
                    // it and refuse anything that fills it. (Okio, not InputStream.readNBytes, which
                    // Android only has from API 33.)
                    val source = r.body.source()
                    val tooBig =
                        try {
                            source.request(maxBytes + 1)
                        } finally {
                            received = source.buffer.size
                        }
                    if (tooBig) Download(null, received) else Download(source.buffer.readByteArray(), received)
                }
            }
        } catch (e: Exception) {
            if (!call.isCanceled()) Log.w("NappletBlobHttp", "Blob fetch failed for $url", e)
            Download(null, received)
        }
    }
}
