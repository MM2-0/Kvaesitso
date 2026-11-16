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
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.net.ConnectException

/**
 * OkHttp interceptor that transparently rewrites media downloads of
 * sha256-keyed blobs to a local Blossom cache running on `127.0.0.1:24242`,
 * per https://github.com/hzrd149/blossom/blob/master/implementations/local-blossom-cache.md
 *
 * This is the only place feed media, videos and profile pictures are routed
 * to the cache: callers keep the real URL, so the Tor decision, Coil/ExoPlayer
 * cache keys and decryption-key lookups all see the origin.
 *
 * Bridging is **opt-in**: only requests the media loaders mark with the
 * [MEDIA_HEADER] header are considered (see [LocalBlossomMediaCallFactory]).
 * Anything else whose last path segment merely looks like a hash — a BUD-02
 * `GET /list/<pubkey>`, uploads, deletes, `HEAD` presence checks — must reach
 * its server. The marker header is always stripped, so no server ever sees it;
 * that is why Tor-proxied clients carry a non-bridging instance ([bridges] =
 * false) instead of none: Tor-routed media skips the cache but is still cleaned.
 *
 * A marked `GET` is rewritten when [shouldBridge] returns `true`, it carries no
 * `Authorization` (an auth-gated blob goes to the host the token was signed
 * for), its last path segment is `<sha256>[.ext]`, and the host isn't already
 * the cache. The original scheme+host(+path prefix) is passed as the `xs=`
 * hint so the cache can fetch upstream on miss.
 *
 * Encrypted blobs are looked up in [keyCache] by URL after this interceptor
 * runs, so their decryption key is registered under the rewritten URL too.
 *
 * When the cache refuses the connection (the app was closed since the last
 * probe), [onUnreachable] is told so the bridge can switch off, and the
 * request falls back to its original URL instead of failing.
 */
class LocalBlossomCacheRedirectInterceptor(
    private val keyCache: EncryptionKeyCache? = null,
    private val onUnreachable: () -> Unit = {},
    val bridges: Boolean = true,
    // Last, so the `LocalBlossomCacheRedirectInterceptor { enabled }` trailing-lambda form binds here.
    // Told whether the request is a profile picture, for the "profile pictures only" setting.
    private val shouldBridge: (profilePicture: Boolean) -> Boolean,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val marked = chain.request()
        val kind = marked.header(MEDIA_HEADER)
        val request = if (kind != null) marked.newBuilder().removeHeader(MEDIA_HEADER).build() else marked

        if (request.method != "GET") return chain.proceed(request)

        if (isLocalCache(request.url)) {
            // Already addressed to the cache (a resolved `blossom:` URI): there is no
            // original URL to fall back to here, but a dead cache must still be reported.
            try {
                return chain.proceed(request)
            } catch (e: ConnectException) {
                onUnreachable()
                throw e
            }
        }

        if (!bridges || kind == null || request.header("Authorization") != null) return chain.proceed(request)

        if (!shouldBridge(kind == PROFILE_PICTURE)) return chain.proceed(request)

        val rewritten = rewriteIfApplicable(request.url) ?: return chain.proceed(request)

        keyCache?.get(request.url.toString())?.let { keyCache.add(rewritten.toString(), it) }

        val bridged =
            try {
                chain.proceed(
                    request
                        .newBuilder()
                        .url(rewritten)
                        .build(),
                )
            } catch (e: ConnectException) {
                onUnreachable()
                return chain.proceed(request)
            }

        if (bridged.isSuccessful) return bridged

        // The cache answered, but not with the blob: it does not hold it and could not fetch it
        // from `xs` either (not every cache implements that, and the one that does can be offline,
        // still warming, or rate-limited). A miss is the ordinary state of a cache and must never
        // be worse than having no cache at all, so the origin is asked directly — without this the
        // 404 reached the caller and the image, video or encrypted file simply failed to load.
        //
        // Not reported through [onUnreachable]: the cache is alive and answering, so switching the
        // bridge off would be the wrong conclusion.
        bridged.close()
        return chain.proceed(request)
    }

    private fun isLocalCache(url: HttpUrl): Boolean = url.port == LOCAL_CACHE_PORT && (url.host == LOCAL_CACHE_HOST || url.host.equals("localhost", ignoreCase = true))

    private fun rewriteIfApplicable(url: HttpUrl): HttpUrl? {
        val host = url.host
        if (host == LOCAL_CACHE_HOST || host.equals("localhost", ignoreCase = true)) return null

        val (shaSegmentIndex, sha, ext) = findSha256AndExtensionInPath(url) ?: return null
        val serverBase = buildServerBase(url, shaSegmentIndex)

        return "$LOCAL_CACHE_BASE/$sha.$ext"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("xs", serverBase)
            .build()
    }

    private fun findSha256AndExtensionInPath(url: HttpUrl): Triple<Int, String, String>? {
        // Per Blossom (BUD-01) the last path segment must be exactly
        // `<sha256>` or `<sha256>.<ext>`. URLs whose filename merely embeds a
        // 64-char hex (e.g. "nostr.build_<sha>.jpg") aren't Blossom blobs and
        // the bridge must leave them alone — rewriting them would point the
        // local cache at a fallback `xs=` server that doesn't host the blob.
        // Prefix segments are preserved verbatim via `buildServerBase`.
        val lastIndex = url.pathSegments.lastIndex
        if (lastIndex < 0) return null
        val segment = url.pathSegments[lastIndex]
        val match = BLOSSOM_LAST_SEGMENT_REGEX.matchEntire(segment) ?: return null
        val sha = match.groupValues[1].lowercase()
        val ext = guessExtensionFrom(segment, sha) ?: "bin"
        return Triple(lastIndex, sha, ext)
    }

    /**
     * Builds the URL prefix that the local cache should append `/<sha>` to.
     * Preserves any path prefix the upstream CDN uses (e.g. `/i` for
     * `https://cdn.nostr.build/i/<sha>`) so the cache can fetch the blob
     * from its actual location on miss.
     */
    private fun buildServerBase(
        url: HttpUrl,
        shaSegmentIndex: Int,
    ): String {
        val origin = "${url.scheme}://${url.host}" + if (url.port != HttpUrl.defaultPort(url.scheme)) ":${url.port}" else ""
        if (shaSegmentIndex == 0) return origin
        val prefixSegments = url.pathSegments.subList(0, shaSegmentIndex)
        return "$origin/" + prefixSegments.joinToString("/")
    }

    private fun guessExtensionFrom(
        segment: String,
        sha: String,
    ): String? {
        val idx = segment.indexOf(sha, ignoreCase = true)
        val after = if (idx >= 0) segment.substring(idx + sha.length) else return null
        if (!after.startsWith('.')) return null
        val rest = after.substring(1).lowercase()
        if (rest.isEmpty() || rest.length > 8) return null
        if (!rest.all { it.isLetterOrDigit() }) return null
        return rest
    }

    companion object {
        const val LOCAL_CACHE_HOST = "127.0.0.1"
        const val LOCAL_CACHE_PORT = 24242
        const val LOCAL_CACHE_BASE = "http://$LOCAL_CACHE_HOST:$LOCAL_CACHE_PORT"
        private val BLOSSOM_LAST_SEGMENT_REGEX = Regex("^([0-9a-fA-F]{64})(?:\\.[^./]+)?$")

        /** Marks a request as a media download the local cache may serve. Stripped before sending. */
        const val MEDIA_HEADER = LocalBlossomCacheHeaders.MEDIA_HEADER
        const val MEDIA = LocalBlossomCacheHeaders.MEDIA
        const val PROFILE_PICTURE = LocalBlossomCacheHeaders.PROFILE_PICTURE
    }
}

/**
 * Marks every call it creates as a media download ([LocalBlossomCacheRedirectInterceptor.MEDIA_HEADER]),
 * keeping a marker the request already carries (e.g. a profile picture's).
 */
class LocalBlossomMediaCallFactory(
    private val delegate: Call.Factory,
    private val kind: String = LocalBlossomCacheRedirectInterceptor.MEDIA,
) : Call.Factory {
    override fun newCall(request: Request): Call =
        if (request.header(LocalBlossomCacheRedirectInterceptor.MEDIA_HEADER) != null) {
            delegate.newCall(request)
        } else {
            delegate.newCall(request.newBuilder().header(LocalBlossomCacheRedirectInterceptor.MEDIA_HEADER, kind).build())
        }
}
