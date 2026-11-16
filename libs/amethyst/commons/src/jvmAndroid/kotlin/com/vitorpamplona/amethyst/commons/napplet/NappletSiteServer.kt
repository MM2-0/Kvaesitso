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
package com.vitorpamplona.amethyst.commons.napplet

import com.vitorpamplona.amethyst.commons.napplet.blobs.NappletFiles
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.flow.StateFlow
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.io.SequenceInputStream

/**
 * What a host serves for one napplet or nSite, whatever its browser: the trusted shell on
 * [NappletWebContract.SHELL_URL] and the launch's verified files on its own [appOrigin]. Android's
 * WebView and the desktop's Chromium only translate requests in and [Response]s out, so the rules
 * below can't drift between them.
 *
 * - A NIP-5D napplet's shell carries its sole `/index.html` as an opaque-origin `srcdoc`, with the
 *   prelude injected outside its aggregate hash; its requests anywhere else are 404s.
 * - A NIP-5A site's shell frames [appOrigin]. Every file there is a verified blob from its event;
 *   a document navigation to a path the event doesn't list falls back to `/`, so client-side routes
 *   survive deep links and reloads, while a missing script or image stays a 404. HTML gets the
 *   prelude with NIP-07. Requests to other hosts are the browser's to send ([serve] returns null).
 */
class NappletSiteServer(
    private val blobs: NappletFiles,
    private val shellHtml: ByteArray,
    private val shimJs: String,
    private val appOrigin: String,
    private val profile: HostProfile,
    // Exact NAP domains the trusted side authorized for this launch. The prelude projects only these
    // objects; absence is the NIP-5D capability-availability signal.
    private val declaredDomains: List<String>,
    // A surface with no keyboard of its own (Android's windowless embedded tabs) relays the focused
    // field to the host's keyboard through the shim.
    private val imeProxy: Boolean = false,
    // Markup the host adds to the shell's head, before anything else runs there (the desktop's bridge).
    private val shellHead: String = "",
) {
    /**
     * One HTTP response, for the host to hand its browser. A file the page loads as it is (a script,
     * wasm, an image) streams from the cache through [open]; nothing else is held for it.
     */
    class Response(
        val status: Int,
        val reason: String,
        val mime: String,
        /** Null when the body has no text encoding. */
        val charset: String?,
        val headers: Map<String, String>,
        val length: Long,
        private val source: () -> InputStream,
    ) {
        constructor(
            status: Int,
            reason: String,
            mime: String,
            charset: String?,
            headers: Map<String, String>,
            body: ByteArray,
        ) : this(status, reason, mime, charset, headers, body.size.toLong(), { ByteArrayInputStream(body) })

        /** [mime] with its charset, for a `Content-Type` header. */
        val contentType: String get() = if (charset != null) "$mime; charset=$charset" else mime

        /** The body, from the start. */
        fun open(): InputStream = source()

        fun readBody(): ByteArray = open().use { it.readBytes() }
    }

    // The prelude every page of this launch gets: built once, prepended to each page's bytes.
    private val prelude by lazy {
        if (profile == HostProfile.WEBXDC) return@lazy NappletWebContract.webxdcPrelude()
        // A paper runs no script, so it gets none: not even the guards, which are scripts too. It
        // gets only markup naming its charset, which JCEF drops from the header.
        if (profile == HostProfile.PAPER) return@lazy NappletWebContract.PAPER_PRELUDE.encodeToByteArray()
        NappletWebContract.preludePrefix(
            shimJs = shimJs,
            declaredDomains = declaredDomains,
            locked = profile == HostProfile.NAPPLET,
            injectNip07 = profile.injectsNip07,
            imeProxy = imeProxy,
        )
    }

    /**
     * Whether [requestPath] is cached or can be fetched now. The hosts probe the entry point with it
     * before showing anything; a fetch also warms the cache for the page's own request.
     */
    suspend fun isAvailable(requestPath: String): Boolean = blobs.isAvailable(requestPath)

    /** The launch ended: any download its files started stops. */
    fun close() = blobs.close()

    /** The download [isAvailable] is waiting on, while it runs, for a loading screen's bar. */
    val download: StateFlow<NappletDownloadProgress?> get() = blobs.download

    /**
     * The response for a request to [url], or null when the browser should handle it itself (a
     * site's request to another host). [acceptsHtml] is whether the
     * request is a document navigation.
     */
    suspend fun serve(
        method: String,
        url: String,
        acceptsHtml: Boolean,
    ): Response? {
        if (!method.equals("GET", ignoreCase = true)) {
            // Nothing here answers anything but a GET. Left to the browser, a POST to the app's own
            // host would go out and look that host up: a lookup that says which app is running.
            val internal = url == NappletWebContract.SHELL_URL || url == appOrigin || url.startsWith("$appOrigin/")
            return if (internal || !profile.allowsOffOrigin) methodNotAllowed() else null
        }
        return try {
            when {
                url == NappletWebContract.SHELL_URL -> shell()
                url == appOrigin || url.startsWith("$appOrigin/") -> appResource(url, acceptsHtml)
                // A napplet or webxdc app has no network; a site is a normal web app.
                profile.allowsOffOrigin -> null
                else -> notFound()
            }
        } catch (e: IOException) {
            // A cached file trimmed away between finding and reading it: an error for this request,
            // never an exception on the browser's thread (one there takes the whole WebView down).
            Log.w(TAG, "Could not read $url", e)
            unavailable()
        }
    }

    private suspend fun shell(): Response {
        val artifactParts =
            if (profile == HostProfile.NAPPLET) {
                val entry = blobs.find(INDEX_PATH) ?: return notFound()
                listOf(prelude, entry.readBytes())
            } else {
                null
            }
        val page = NappletWebContract.shell(shellHtml, profile, appOrigin, artifactParts, shellHead)
        return Response(200, "OK", MIME_HTML, "utf-8", mapOf("Content-Security-Policy" to page.csp) + NO_STORE, page.html)
    }

    private suspend fun appResource(
        url: String,
        acceptsHtml: Boolean,
    ): Response {
        val requestPath =
            url
                .removePrefix(appOrigin)
                .substringBefore('?')
                .substringBefore('#')
                .ifEmpty { "/" }

        // A document navigation to a path the event doesn't list falls back to the page itself, so
        // client-side routes survive deep links and reloads; a missing script or image stays a 404.
        // A listed file no server returned is an error, not the home page in its place.
        val blob =
            blobs.find(requestPath)
                ?: when {
                    blobs.isListed(requestPath) -> return unavailable()
                    acceptsHtml && requestPath != "/" -> blobs.find("/")
                    else -> null
                } ?: return notFound()

        val (mime, charset) = splitContentType(blob.contentType)
        // A locked napplet gets the strict app CSP; a site sets its own.
        val headers = (profile.appCsp?.let { mapOf("Content-Security-Policy" to it) } ?: emptyMap()) + NO_STORE
        return if (mime.equals(MIME_HTML, ignoreCase = true)) {
            // Streamed behind the prelude, never read whole: an archive's page can be any size.
            Response(200, "OK", mime, charset, headers, prelude.size + blob.length) { SequenceInputStream(ByteArrayInputStream(prelude), blob.open()) }
        } else {
            Response(200, "OK", mime, charset, headers, blob.length, blob::open)
        }
    }

    companion object {
        const val INDEX_PATH = "/index.html"
        const val MIME_HTML = "text/html"

        // Every response is built from verified bytes on each request; nothing is for the browser
        // to keep.
        private val NO_STORE = mapOf("Cache-Control" to "no-store")

        private const val TAG = "NappletSiteServer"

        fun notFound(): Response = Response(404, "Not Found", "text/plain", "utf-8", emptyMap(), ByteArray(0))

        private fun methodNotAllowed(): Response = Response(405, "Method Not Allowed", "text/plain", "utf-8", mapOf("Allow" to "GET"), ByteArray(0))

        /** A file the event lists that couldn't be fetched or read. */
        fun unavailable(): Response = Response(502, "Bad Gateway", "text/plain", "utf-8", emptyMap(), ByteArray(0))

        /** Splits a content type into its MIME type and charset (UTF-8 for text-like types). */
        fun splitContentType(contentType: String): Pair<String, String?> {
            val mime = contentType.substringBefore(';').trim().ifEmpty { "application/octet-stream" }
            val charset =
                contentType.substringAfter("charset=", "").trim().ifEmpty { null }
                    ?: if (mime.startsWith("text/") || mime.endsWith("javascript") || mime.endsWith("json")) "utf-8" else null
            return mime to charset
        }
    }
}
