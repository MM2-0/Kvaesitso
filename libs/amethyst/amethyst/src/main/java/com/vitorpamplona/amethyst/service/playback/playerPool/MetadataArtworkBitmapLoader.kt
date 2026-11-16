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
@file:OptIn(UnstableApi::class)

package com.vitorpamplona.amethyst.service.playback.playerPool

import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.OptIn
import androidx.core.graphics.scale
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors

/**
 * Scales loaded artwork down to [maxDimensionPx] so the platform never has to scale it itself.
 *
 * android.media.session.MediaSession.setMetadata() re-builds the metadata through
 * MediaMetadata.Builder.build(), which scales every bitmap-valued key larger than the platform's
 * own config_mediaMetadataBitmapMaxSize. media3 stores the *same* Bitmap instance under both
 * METADATA_KEY_DISPLAY_ICON and METADATA_KEY_ALBUM_ART (LegacyConversions), so that one instance is
 * scaled twice inside a single build() call. AOSP leaves the source untouched, but on ROMs that
 * recycle it while scaling the second pass throws
 *
 *     IllegalArgumentException: cannot use a recycled source in createBitmap
 *
 * on the main thread, inside a Guava callback the app cannot intercept. Handing the session artwork
 * that already fits removes the scaling step, and with it the crash.
 *
 * [maxDimensionPx] is a lambda rather than a constant because the limit is a dp value: it changes
 * with the display density, and the app survives density changes without restarting.
 *
 * [delegate] must decode a fresh bitmap per request and keep no reference to it — caps free the
 * pre-scale original. media3's own caching wrapper sits above this loader, not below it, so the
 * bitmap handed out here is the only one that outlives the call.
 *
 * loadBitmapFromMetadata is deliberately not overridden — [BitmapLoader]'s default implementation
 * routes back through [decodeBitmap]/[loadBitmap] here, while delegating it would call the same two
 * methods on the delegate and skip the cap.
 */
class MetadataArtworkBitmapLoader(
    private val delegate: BitmapLoader,
    private val maxDimensionPx: () -> Int,
) : BitmapLoader {
    override fun supportsMimeType(mimeType: String): Boolean = delegate.supportsMimeType(mimeType)

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = capped(delegate.decodeBitmap(data))

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> = capped(delegate.loadBitmap(uri))

    /**
     * Runs on whichever thread completed the load (directExecutor), never on the main thread. A
     * throw here fails the future, which media3 logs as a failed artwork load instead of crashing.
     */
    private fun capped(future: ListenableFuture<Bitmap>): ListenableFuture<Bitmap> =
        Futures.transform(
            future,
            { bitmap -> bitmap.capTo(maxDimensionPx()) },
            MoreExecutors.directExecutor(),
        )
}

private fun Bitmap.capTo(maxDimension: Int): Bitmap {
    val target = fitArtworkWithin(width, height, maxDimension) ?: return this
    val scaled = scale(target.width, target.height)
    // The pre-scale bitmap is ours alone (see the delegate contract above) and, because the decoder
    // is allowed to overshoot the cap to keep the subsampling close to it, holds up to 4x the pixels
    // of the copy. Freeing it here rather than waiting for the collector keeps that overshoot from
    // stacking up on the decoder thread. Bitmap.scale hands back the source when nothing changed, so
    // only a real copy makes the original garbage.
    if (scaled !== this) recycle()
    return scaled
}

/** The size a [width] x [height] bitmap is scaled to, or null when it already fits. */
data class ArtworkSize(
    val width: Int,
    val height: Int,
)

/**
 * Mirrors android.media.MediaMetadata.Builder.scaleBitmap(): one scale factor for both axes,
 * truncated to whole pixels, so the result is exactly what the platform would have produced and
 * never one pixel over the limit. Returns null when nothing needs to change — a bitmap that already
 * fits, or a limit that couldn't be resolved.
 */
fun fitArtworkWithin(
    width: Int,
    height: Int,
    maxDimension: Int,
): ArtworkSize? {
    if (maxDimension <= 0) return null
    if (width <= maxDimension && height <= maxDimension) return null

    val scale = minOf(maxDimension.toFloat() / width, maxDimension.toFloat() / height)
    return ArtworkSize(
        width = (width * scale).toInt().coerceIn(1, maxDimension),
        height = (height * scale).toInt().coerceIn(1, maxDimension),
    )
}
