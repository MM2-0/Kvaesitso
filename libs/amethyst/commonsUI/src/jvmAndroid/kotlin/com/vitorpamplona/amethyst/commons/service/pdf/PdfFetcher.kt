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
package com.vitorpamplona.amethyst.commons.service.pdf

import coil3.disk.DiskCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.coroutines.executeAsync
import java.io.IOException

object PdfFetcher {
    /**
     * Opens a snapshot of the cached PDF for [url], downloading it if necessary, and hands it to
     * [open], which takes ownership: the snapshot is closed here only if [open] throws. While it's
     * open the cache entry cannot be evicted, so the underlying file stays valid for `PdfRenderer`.
     *
     * [open] runs on the IO dispatcher right after the snapshot is created, with no suspension in
     * between, so the caller can record what it built there: if the caller is cancelled before this
     * returns, `withContext` drops the result, and only that record lets the caller close it.
     *
     * Pass the app's Coil disk cache so PDFs share the same LRU eviction and disk budget as
     * images. It is a provider, read on the IO dispatcher: the app builds its cache lazily
     * (statvfs, directory setup), and a card composing on a cold start must not do that on main.
     */
    suspend fun <T> openSnapshot(
        url: String,
        diskCache: () -> DiskCache,
        okHttpClient: (String) -> OkHttpClient,
        open: (DiskCache.Snapshot) -> T,
    ): T =
        withContext(Dispatchers.IO) {
            val snapshot = openOrDownload(url, diskCache, okHttpClient)
            try {
                open(snapshot)
            } catch (t: Throwable) {
                runCatching { snapshot.close() }
                throw t
            }
        }

    /** The fetch itself. Callers run it on the IO dispatcher. */
    private suspend fun openOrDownload(
        url: String,
        diskCache: () -> DiskCache,
        okHttpClient: (String) -> OkHttpClient,
    ): DiskCache.Snapshot {
        val cache = diskCache()
        // Covers the cache-hit fast path too, not just the download below it. openSnapshot()
        // contends on the global DiskLruCache lock, which Coil's cleanup pass holds across a
        // burst of unlink syscalls (see DeferredDeleteFileSystem) — calling it from a caller
        // that happens to be on the main thread stalls the frame for that whole burst, and the
        // hit path is exactly the one a feed takes when a PDF card scrolls back into view.
        cache.openSnapshot(url)?.let { return it }

        val editor = cache.openEditor(url) ?: throw IOException("Unable to open cache editor for $url")
        try {
            val request =
                Request
                    .Builder()
                    .url(url)
                    .get()
                    .build()

            okHttpClient(url).newCall(request).executeAsync().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("PDF download failed: ${response.code}")
                }
                cache.fileSystem.write(editor.data) {
                    val bytes = writeAll(response.body.source())
                    if (bytes == 0L) throw IOException("PDF download failed: empty response body")
                }
            }

            return editor.commitAndOpenSnapshot() ?: throw IOException("Unable to commit cache editor for $url")
        } catch (t: Throwable) {
            runCatching { editor.abort() }
            throw t
        }
    }

    /**
     * Fetches like [fetchSnapshot], runs [block] on the snapshot, and closes it, all on the IO
     * dispatcher. Prefer this to `fetchSnapshot(...).use { }` from a main-thread caller: there the
     * close would run on main, and closing takes the same global DiskLruCache lock as opening.
     * The snapshot is created by the last non-suspending step of the fetch and handed straight
     * to `use`, inside one `withContext`, so a cancellation cannot land between the two and leak
     * it open (which returning a bare snapshot through a `withContext` could).
     */
    suspend fun <T> useSnapshot(
        url: String,
        diskCache: () -> DiskCache,
        okHttpClient: (String) -> OkHttpClient,
        block: (DiskCache.Snapshot) -> T,
    ): T =
        withContext(Dispatchers.IO) {
            openOrDownload(url, diskCache, okHttpClient).use(block)
        }
}
