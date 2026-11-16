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
package com.vitorpamplona.amethyst.service.images

import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import com.vitorpamplona.amethyst.commons.service.image.DeferredDeleteFileSystem
import okio.FileSystem

/**
 * Wraps a network [Fetcher] so the disk-cache file it hands back is addressed through
 * [FileSystem.SYSTEM] itself instead of through our [DeferredDeleteFileSystem] wrapper.
 *
 * Coil reaches for the platform `android.graphics.ImageDecoder` only when an image source's
 * file system is **referentially** [FileSystem.SYSTEM] (`ImageSource.toImageDecoderSourceOrNull`,
 * verified against Coil 3.5.0):
 *
 * ```
 * if (fileSystem === FileSystem.SYSTEM) {
 *     val file = fileOrNull()
 *     if (file != null) return ImageDecoder.createSource(file.toFile())
 * }
 * ```
 *
 * Coil's `NetworkFetcher` stamps the source it builds with `diskCache.fileSystem`, and ours is a
 * [DeferredDeleteFileSystem] wrapper, so that identity check failed for **every** image the app
 * fetched from the network. Two decoders quietly lost their fast path because of it:
 *
 * - `AnimatedImageDecoder` fell back to
 *   `ImageDecoder.createSource(source.squashToDirectByteBuffer())`, pulling the whole encoded
 *   animation onto the heap and then copying it into an equally large direct `ByteBuffer` that
 *   lives as long as the `AnimatedImageDrawable`. Replaying that over a 69.8 MB GIF measured
 *   66 MB of heap plus 66 MB of native memory, and animated results are never memory-cached
 *   (`DrawableImage.shareable` is false), so the feed paid it again on every scroll back.
 * - `StaticImageDecoder.Factory` declined outright (it returns null when it cannot get an
 *   `ImageDecoder.Source`), so every still image decoded through `BitmapFactoryDecoder`.
 *
 * Only deletes are deferred by the wrapper — reads already go straight to [FileSystem.SYSTEM] —
 * so handing decoders the same file on the real system file system is equivalent, and it is what
 * Coil does when no wrapper is installed.
 *
 * This sits at the fetcher rather than at each decoder on purpose: one seam fixes every decoder
 * at once, it keeps Coil's own decoder registration (and with it the parallelism semaphore the
 * bitmap decoders share), and it is the only place that still knows [diskCacheKey], which
 * `FileImageSource` exposes internally and cannot be copied off an existing source.
 */
class SystemFileSystemFetcher(
    private val delegate: Fetcher,
    private val diskCacheKey: String,
) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        val result = delegate.fetch()
        if (result !is SourceFetchResult) return result

        val onSystem = result.source.onSystemFileSystem(diskCacheKey) ?: return result

        return SourceFetchResult(onSystem, result.mimeType, result.dataSource)
    }
}

/** This fetcher, with any disk-cache-backed result re-homed onto [FileSystem.SYSTEM]. */
fun Fetcher.onSystemFileSystem(diskCacheKey: String): Fetcher = SystemFileSystemFetcher(this, diskCacheKey)

/**
 * The same bytes, addressed through [FileSystem.SYSTEM], or null when there is nothing to gain:
 * already on it, wrapped in something other than a [DeferredDeleteFileSystem] (which may rewrite
 * paths or serve different bytes, so the file underneath is not ours to hand out), or not backed
 * by a file yet.
 *
 * [ImageSource.fileOrNull] is used rather than [ImageSource.file] on purpose — the latter would
 * materialise a temp copy of a stream-backed source, which is the very cost this exists to avoid.
 */
fun ImageSource.onSystemFileSystem(diskCacheKey: String): ImageSource? {
    if (fileSystem === FileSystem.SYSTEM) return null

    var unwrapped: FileSystem = fileSystem
    while (unwrapped is DeferredDeleteFileSystem) unwrapped = unwrapped.delegate
    if (unwrapped !== FileSystem.SYSTEM) return null

    val file = fileOrNull() ?: return null

    return ImageSource(
        file = file,
        fileSystem = FileSystem.SYSTEM,
        diskCacheKey = diskCacheKey,
        // Takes ownership of the source it replaces. Only the returned source reaches the engine,
        // and the engine closes exactly one source per fetch, so this is what releases the
        // disk-cache snapshot holding `file` open.
        closeable = this,
        metadata = metadata,
    )
}
