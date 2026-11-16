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

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import android.widget.Toast
import androidx.core.net.toUri
import com.vitorpamplona.amethyst.commons.browser.BrowserDownloadRules
import com.vitorpamplona.amethyst.commons.napplet.blobs.NappletBlobHttp
import com.vitorpamplona.quartz.utils.Log
import okhttp3.Request
import java.io.File
import java.io.OutputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import com.vitorpamplona.amethyst.commons.R as CommonsR

/**
 * Saves what a page downloads — `<a download>`, `Content-Disposition: attachment`, `data:` URLs, and
 * `blob:` URLs (which only the page can read, so the browser-extras script hands their bytes over) — into
 * the system Downloads collection, the way Chrome does.
 *
 * A page can start a download without any gesture, so everything it starts arrives as a [DownloadOffer]
 * that the surface shows on a consent card; nothing is fetched or written until the user taps Save. That
 * covers both entry points: the WebView `DownloadListener` ([offerListenerDownload]) and the
 * `browser.download` bridge envelope ([offerInline]), which any top-frame script can post directly. The
 * long-press "Download image" item ([download]) is the one ungated path: the user's own tap starts it.
 *
 * Network downloads follow the page's own route: through the Tor SOCKS proxy when the site is on Tor (OkHttp
 * leaves SOCKS hosts unresolved, so even DNS goes through Tor), directly otherwise. They carry the page's
 * cookies from its own per-account storage profile and its user agent, so a logged-in download works.
 */
object BrowserDownloads {
    private const val TAG = "BrowserDownloads"

    /** Cap for bytes a page hands over for a blob:/data: download (they travel as base64 over the bridge). */
    const val MAX_INLINE_BYTES = BrowserDownloadRules.MAX_INLINE_BYTES

    private val io = Executors.newSingleThreadExecutor { Thread(it, "napplet-downloads").apply { isDaemon = true } }

    // Decoding an inline payload (up to 25 MiB) is kept off the main thread, and off [io] so a long
    // transfer already running there doesn't hold the next consent card back.
    private val decoder = Executors.newSingleThreadExecutor { Thread(it, "napplet-download-decode").apply { isDaemon = true } }
    private val main = Handler(Looper.getMainLooper())

    /**
     * A page-initiated download, resolved to what its consent card shows: [save] stores one file named
     * [fileName], typed by that name's extension (so the system can't append a different one), and until
     * it runs no byte has been fetched or written. [save] runs at most once, however often it is called.
     */
    class DownloadOffer internal constructor(
        val fileName: String,
        /** The size in bytes: exact for inline data, the server's advertised length otherwise; -1 when unknown. */
        val sizeBytes: Long,
        /** The host a network download is fetched from (it can differ from the page's), or null for inline data. */
        val sourceHost: String?,
        private val start: (Context) -> Unit,
    ) {
        private val started = AtomicBoolean(false)

        /** Whether [fileName] is something that can be installed or run, which the card warns about. */
        val risky: Boolean get() = BrowserDownloadRules.isRisky(fileName)

        fun save(context: Context) {
            if (started.compareAndSet(false, true)) start(context.applicationContext)
        }
    }

    /**
     * The long-press "Download image" item: the user's tap is the gesture, so it saves without a card.
     * [cookie] must be read on the main thread (from the tab's own profile) before calling; the transfer
     * itself runs on a background thread.
     */
    fun download(
        context: Context,
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        cookie: String?,
        proxyPort: Int,
    ) {
        val app = context.applicationContext
        if (url.startsWith("data:", ignoreCase = true)) {
            saveDataUrl(app, url, null)
            return
        }
        if (!isHttp(url)) return
        startNetworkDownload(app, url, networkName(url, contentDisposition, mimeType), userAgent, cookie, proxyPort)
    }

    /**
     * A WebView `DownloadListener` hit: a download the page started. Calls [onReady] exactly once, on the
     * main thread, with the offer for the consent card (null when the URL can't be saved). [contentLength] is
     * the size the listener reported (<= 0 when unknown); [cookie] must be read on the main thread from
     * the tab's own profile. Nothing is fetched until the offer's save runs.
     */
    fun offerListenerDownload(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        contentLength: Long,
        cookie: String?,
        proxyPort: Int,
        onReady: (DownloadOffer?) -> Unit,
    ) {
        if (url.startsWith("data:", ignoreCase = true)) {
            offerInline(url, null, onReady)
            return
        }
        if (!isHttp(url)) {
            onReady(null)
            return
        }
        val name = networkName(url, contentDisposition, mimeType)
        onReady(
            DownloadOffer(name, contentLength.takeIf { it > 0 } ?: -1L, url.toUri().host) { app ->
                startNetworkDownload(app, url, name, userAgent, cookie, proxyPort)
            },
        )
    }

    /**
     * A page's inline download (a `browser.download` envelope's `data:` URL). Decodes it off the main
     * thread, then calls [onReady] exactly once, on the main thread, with the offer (its size is the true
     * decoded length), or null when the payload is malformed or oversized and is refused without a card.
     */
    fun offerInline(
        dataUrl: String,
        suggestedName: String?,
        onReady: (DownloadOffer?) -> Unit,
    ) {
        decoder.execute {
            // Throwable, not Exception: an OutOfMemoryError here must refuse the download, not kill the
            // process (and every tab in it) or leave the caller waiting on a callback that never comes.
            val offer =
                try {
                    BrowserDownloadRules.decodeDataUrl(dataUrl)?.let { data ->
                        val name = safeName(suggestedName, data.mimeType)
                        DownloadOffer(name, data.bytes.size.toLong(), null) { app -> writeInBackground(app, name, data.bytes) }
                    }
                } catch (e: Throwable) {
                    Log.w(TAG, "Inline download refused", e)
                    null
                }
            main.post { onReady(offer) }
        }
    }

    private fun isHttp(url: String) = url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)

    private fun networkName(
        url: String,
        contentDisposition: String?,
        mimeType: String?,
    ) = safeName(URLUtil.guessFileName(url, contentDisposition, mimeType), mimeType)

    private fun startNetworkDownload(
        app: Context,
        url: String,
        name: String,
        userAgent: String?,
        cookie: String?,
        proxyPort: Int,
    ) {
        toast(app, app.getString(CommonsR.string.browser_download_started, name))
        io.execute {
            val ok =
                runCatching {
                    val request =
                        Request
                            .Builder()
                            .url(url)
                            .apply {
                                userAgent?.takeIf { it.isNotBlank() }?.let { header("User-Agent", it) }
                                cookie?.takeIf { it.isNotBlank() }?.let { header("Cookie", it) }
                            }.get()
                            .build()
                    // No end-to-end call timeout: a large file over Tor legitimately takes minutes. The
                    // client's read timeout still ends a transfer that stalls completely.
                    val client =
                        NappletBlobHttp
                            .client(proxyPort)
                            .newBuilder()
                            .callTimeout(0, TimeUnit.SECONDS)
                            .build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) error("HTTP ${response.code}")
                        write(app, name) { out -> response.body.byteStream().use { it.copyTo(out) } }
                    }
                }.onFailure { Log.w(TAG, "Download failed for $url", it) }
                    .getOrDefault(false)
            toast(app, app.getString(if (ok) CommonsR.string.browser_download_saved else CommonsR.string.browser_download_failed, name))
        }
    }

    /** Saves a `data:` URL (`data:[mime][;base64],payload`) the user asked for directly, decoded off the main thread. */
    private fun saveDataUrl(
        app: Context,
        dataUrl: String,
        suggestedName: String?,
    ) {
        decoder.execute {
            val data = runCatching { BrowserDownloadRules.decodeDataUrl(dataUrl) }.getOrNull() ?: return@execute
            writeInBackground(app, safeName(suggestedName, data.mimeType), data.bytes)
        }
    }

    private fun writeInBackground(
        app: Context,
        name: String,
        bytes: ByteArray,
    ) {
        if (bytes.size > MAX_INLINE_BYTES) return
        io.execute {
            val ok = runCatching { write(app, name) { it.write(bytes) } }.getOrDefault(false)
            toast(app, app.getString(if (ok) CommonsR.string.browser_download_saved else CommonsR.string.browser_download_failed, name))
        }
    }

    /**
     * Writes into the public Downloads collection (Android 10+, no permission needed), or into the app's
     * own Downloads folder on older versions, where writing the shared one would need a storage permission
     * the app doesn't hold.
     */
    private fun write(
        context: Context,
        name: String,
        body: (OutputStream) -> Unit,
    ): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values =
                ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    // Typed by the approved name alone: a page- or server-supplied type that disagrees with
                    // the extension would make MediaStore append its own ("invoice.pdf" -> "invoice.pdf.apk").
                    put(MediaStore.Downloads.MIME_TYPE, mimeTypeOf(name))
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
            return try {
                resolver.openOutputStream(uri)?.use(body) ?: error("No output stream")
                resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
                true
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        }
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return false
        dir.mkdirs()
        File(dir, name).outputStream().use(body)
        return true
    }

    /**
     * The file name to save under: the page's suggestion made safe ([BrowserDownloadRules.safeFileName]),
     * else "download" + the MIME's extension.
     */
    private fun safeName(
        suggested: String?,
        mimeType: String?,
    ): String {
        BrowserDownloadRules.safeFileName(suggested)?.let { return it }
        val ext = mimeType?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
        return if (ext != null) "download.$ext" else "download"
    }

    /** The MIME type [name]'s extension implies, or octet-stream, which MediaStore stores under the name as given. */
    private fun mimeTypeOf(name: String): String =
        name
            .substringAfterLast('.', "")
            .lowercase()
            .takeIf { it.isNotEmpty() }
            ?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) }
            ?: "application/octet-stream"

    private fun toast(
        context: Context,
        text: String,
    ) = main.post { Toast.makeText(context, text, Toast.LENGTH_SHORT).show() }
}
