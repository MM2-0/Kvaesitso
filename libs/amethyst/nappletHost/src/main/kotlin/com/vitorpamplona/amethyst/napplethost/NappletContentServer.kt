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
package com.vitorpamplona.amethyst.napplethost

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import com.vitorpamplona.amethyst.commons.napplet.HostProfile
import com.vitorpamplona.amethyst.commons.napplet.NappletDownloadProgress
import com.vitorpamplona.amethyst.commons.napplet.NappletSiteServer
import com.vitorpamplona.amethyst.commons.napplet.WebxdcLaunch
import com.vitorpamplona.amethyst.commons.napplet.blobs.NappletBlobCache
import com.vitorpamplona.amethyst.commons.napplet.blobs.NappletBlobHttp
import com.vitorpamplona.amethyst.commons.napplet.blobs.NappletBlobStore
import com.vitorpamplona.amethyst.commons.napplet.blobs.NappletFiles
import com.vitorpamplona.amethyst.commons.napplet.blobs.PaperFiles
import com.vitorpamplona.amethyst.commons.napplet.webxdc.WebxdcBundles
import com.vitorpamplona.amethyst.commons.napplet.webxdc.WebxdcFiles
import com.vitorpamplona.amethyst.commons.napplet.webxdc.WebxdcRuntime
import com.vitorpamplona.quartz.nip5aStaticWebsites.tags.PathTag
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException

/**
 * The WebView side of the shared [NappletSiteServer]: serves the trusted shell and the launch's
 * sha256-verified blobs over the internal `https://napplet.local` origins, and defers everything
 * else to the WebView. Blobs come from the shared content-addressed cache (warmed by the prefetcher
 * while the card was on screen, so opening is instant); a miss falls back to a download, routed
 * through Tor when [proxyPort] is set, that re-fills the cache.
 *
 * This is the host's resource edge, kept separate from the Activity lifecycle and the broker bridge:
 * given a [WebResourceRequest] it returns the [WebResourceResponse] or null to defer to the WebView.
 */
class NappletContentServer(
    paths: List<PathTag>,
    servers: List<String>,
    proxyPort: Int,
    cacheDir: File,
    // This site's cache partition (NappletBlobCache.partitionOf): files cached for other sites or
    // accounts are fetched again, so the site can't tell what else this device opened.
    cachePartition: String,
    shellHtmlBytes: ByteArray,
    shimJs: String,
    // The applet's own per-applet origin (a distinct napplet.local subdomain). The shell is on
    // NappletWebContract.ORIGIN; app blobs are served here so the applet has a real, isolated origin.
    appOrigin: String,
    // The host posture: a WEBSITE nSite is a normal web app — NIP-07 window.nostr provider, normal
    // network (no app CSP; off-origin requests defer to the WebView), unlike a locked NAPPLET.
    profile: HostProfile = HostProfile.NAPPLET,
    // Exact NAP domains the trusted main process authorized for this launch. The injected prelude
    // projects only these objects; absence is the NIP-5D capability-availability signal.
    declaredDomains: List<String> = emptyList(),
    // Embedded surfaces (a windowless Service) can't host the soft keyboard, so the shim installs the
    // IME proxy agent that relays the focused field to the host's keyboard. The full-screen Activity
    // host has a native keyboard and leaves this false.
    imeProxy: Boolean = false,
    // A webxdc app's archive and who runs it; its files come from the unpacked archive instead of paths.
    webxdc: WebxdcLaunch? = null,
    // A front page's one checked document; served from memory instead of paths.
    paper: ByteArray? = null,
) {
    // This instance's own client (its threads and connections end with close()).
    private val client = NappletBlobHttp.client(proxyPort)

    private val blobs: NappletFiles =
        if (paper != null) {
            PaperFiles(paper)
        } else if (webxdc != null) {
            WebxdcFiles(
                WebxdcBundles(WebxdcBundles.dirFor(cacheDir)),
                WebxdcFiles.downloadClient(client),
                webxdc.url,
                webxdc.hash,
                servers,
                WebxdcRuntime.script(webxdc.selfAddr, webxdc.selfName, webxdc.isAppSender),
            )
        } else {
            NappletBlobStore(NappletBlobCache(NappletBlobCache.dirFor(cacheDir)).forSite(cachePartition), client, paths, servers)
        }

    private val server = NappletSiteServer(blobs, shellHtmlBytes, shimJs, appOrigin, profile, declaredDomains, imeProxy)

    /**
     * Whether [requestPath] is cached or can be fetched now. Used by the host to probe availability for
     * the loading screen before showing the WebView. A fetch warms the cache as a side effect, so the
     * subsequent WebView request serves from disk. [onDownload] hears how far a large download (a
     * webxdc archive) has come while the probe waits on it, on the probing thread.
     */
    fun isAvailable(
        requestPath: String,
        onDownload: ((NappletDownloadProgress?) -> Unit)? = null,
    ): Boolean =
        runBlocking {
            val watch = onDownload?.let { report -> launch { server.download.collect(report) } }
            try {
                server.isAvailable(requestPath)
            } finally {
                watch?.cancel()
            }
        }

    /**
     * Releases the per-instance OkHttp client (its dispatcher thread pool + connection pool) and cancels
     * any in-flight blob fetch. A new content server is built per embedded tab, so without this each
     * opened-then-closed napplet/nSite tab would leak OkHttp threads and keep-alive connections.
     */
    fun close() {
        // First, so a webxdc download stops at its next read instead of moving to the next server.
        server.close()
        runCatching {
            client.dispatcher.cancelAll()
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }

    /** The response for [request], or null to let the WebView handle it. Runs on a WebView worker thread. */
    fun serve(request: WebResourceRequest): WebResourceResponse? {
        // A document navigation accepts text/html; a sub-resource (js/css/img) does not.
        val acceptsHtml = request.requestHeaders["Accept"]?.contains(NappletSiteServer.MIME_HTML, ignoreCase = true) == true
        val response = runBlocking { server.serve(request.method, request.url.toString(), acceptsHtml) } ?: return null
        // The file may be trimmed away before it opens; an exception here would crash the WebView.
        val body =
            try {
                response.open()
            } catch (e: IOException) {
                Log.w("NappletContentServer", "Could not open ${request.url}", e)
                return WebResourceResponse("text/plain", "utf-8", 502, "Bad Gateway", emptyMap(), ByteArrayInputStream(ByteArray(0)))
            }
        return WebResourceResponse(
            response.mime,
            response.charset,
            response.status,
            response.reason,
            response.headers,
            body,
        )
    }
}
