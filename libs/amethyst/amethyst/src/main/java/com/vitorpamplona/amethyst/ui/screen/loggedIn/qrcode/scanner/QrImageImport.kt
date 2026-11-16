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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.qrcode.scanner

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.graphics.scale
import com.vitorpamplona.amethyst.commons.qrcode.ImageCodesOutcome
import com.vitorpamplona.amethyst.commons.qrcode.ScanResult
import com.vitorpamplona.amethyst.commons.qrcode.assembleStructuredAppend
import com.vitorpamplona.amethyst.commons.qrcode.outcome
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.qr_scanner_partial_sequence_note
import com.vitorpamplona.amethyst.commons.resources.qr_scanner_sequence_progress
import com.vitorpamplona.amethyst.commons.ui.loadPluralStringRes
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * Decoding a QR code out of an image the user already has.
 *
 * This is the single biggest gap the camera-only scanner left: most QR codes people need to scan
 * in a Nostr client arrive as a screenshot, a photo in a chat, or an image saved from a website.
 * Before this, the only way to use one was to display it on a second screen and photograph it.
 */
object QrImageImport {
    /**
     * Longest edge we downscale a picked image to for the first attempt.
     *
     * Detection does not improve above this, and full-resolution phone photos are 50+ megapixels
     * of mostly-wall that make a thorough pass take seconds.
     */
    private const val FIRST_PASS_MAX_EDGE = 2_000

    /**
     * Most pixels any pass decodes: 16 MP, 64 MB as ARGB_8888.
     *
     * Bitmap pixels live in native memory, so going past this does not reliably throw an
     * [OutOfMemoryError] we could catch — a 108 MP photo at full size is ~430 MB, which can push
     * the process into the low-memory killer instead. A pixel budget rather than an edge cap, so a
     * long scrolling screenshot (1080x10000, 11 MP) still gets its full-resolution pass: capping
     * its 10000-pixel edge left a narrow code in it at a couple of pixels per module.
     */
    private const val MAX_DECODE_PIXELS = 16_000_000L

    /** Below this, a code is likely too few pixels per module; the upscale pass is worth trying. */
    private const val SMALL_IMAGE_EDGE = 600

    /** Decodes every QR code in the image at [uri], hardest-effort, with a retry ladder. */
    suspend fun decode(
        context: Context,
        uri: Uri,
        decoder: BarcodeDecoder,
    ): List<ScanResult> =
        withContext(Dispatchers.IO) {
            val bounds = readBounds(context, uri) ?: return@withContext emptyList()
            val width = bounds.outWidth
            val height = bounds.outHeight
            val longestEdge = max(width, height)
            if (width <= 0 || height <= 0) return@withContext emptyList()

            // Cheapest first, then doubling the resolution while it stays under MAX_DECODE_PIXELS.
            // A code that a downscaled pass misses because its modules blurred together often
            // survives one step up, and a code in a small thumbnail often needs *more* pixels per
            // module than it shipped with (the upscale pass below).
            fun pixelsAt(sample: Int) = (width / sample).toLong() * (height / sample)
            val sampleSizes =
                buildList {
                    var sample = sampleSizeFor(longestEdge, FIRST_PASS_MAX_EDGE)
                    add(sample)
                    while (sample > 1 && pixelsAt(sample / 2) <= MAX_DECODE_PIXELS) {
                        sample /= 2
                        add(sample)
                    }
                }

            // Checked between passes because a pass itself is one long blocking call into JNI
            // and cannot be interrupted, so closing the scanner mid-ladder should not leave the
            // remaining passes grinding away on an IO thread for a result nobody wants.
            for (sampleSize in sampleSizes) {
                ensureActive()
                val found = decodeAt(context, uri, sampleSize, upscale = false, decoder)
                if (found.isNotEmpty()) return@withContext found
            }

            if (longestEdge <= SMALL_IMAGE_EDGE) {
                ensureActive()
                val found = decodeAt(context, uri, sampleSize = 1, upscale = true, decoder)
                if (found.isNotEmpty()) return@withContext found
            }

            emptyList()
        }

    /**
     * What the picture at [uri] should lead to: its codes decoded, multi-part codes joined, and
     * the open/choose/explain decision made — shared by the scanner's image button and the
     * share-sheet screen so the two cannot drift apart.
     */
    suspend fun outcome(
        context: Context,
        uri: Uri,
        decoder: BarcodeDecoder,
    ): ImageCodesOutcome = assembleStructuredAppend(decode(context, uri, decoder)).outcome()

    /** "Captured N of M parts", pluralised on the total exactly as the live scanner's hint is. */
    suspend fun partialProgressText(
        captured: Int,
        total: Int,
    ): String = loadPluralStringRes(Res.plurals.qr_scanner_sequence_progress, total, captured, total)

    /** The chooser's line about a half-captured sequence next to the codes it offers, if any. */
    suspend fun partialNote(partial: Pair<Int, Int>?): String? =
        partial?.let { (captured, total) ->
            loadPluralStringRes(Res.plurals.qr_scanner_partial_sequence_note, total, captured, total)
        }

    /** Text sitting on the clipboard, or null when there is none. */
    fun clipboardText(context: Context): String? {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return null
        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip
            .getItemAt(0)
            ?.coerceToText(context)
            ?.toString()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    /**
     * An image sitting on the clipboard, or null when there is none.
     *
     * The MIME check matters: a copied link is a clip with a uri too, and without it every such
     * clip went to the bitmap decoder and the clipboard *text* was never reachable — Paste could
     * not paste an npub, which is most of what it is for.
     */
    fun clipboardImage(context: Context): Uri? {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return null
        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount == 0) return null

        val uri = clip.getItemAt(0)?.uri ?: return null
        val declared = (0 until clip.description.mimeTypeCount).map { clip.description.getMimeType(it) }
        val looksLikeImage =
            declared.any { it.startsWith("image/") } ||
                context.contentResolver.getType(uri)?.startsWith("image/") == true

        return uri.takeIf { looksLikeImage }
    }

    private fun decodeAt(
        context: Context,
        uri: Uri,
        sampleSize: Int,
        upscale: Boolean,
        decoder: BarcodeDecoder,
    ): List<ScanResult> {
        val original = loadBitmap(context, uri, sampleSize) ?: return emptyList()
        var upscaled: Bitmap? = null
        return try {
            if (upscale) upscaled = original.scale(original.width * 2, original.height * 2)
            decoder.decode(upscaled ?: original, DecodeEffort.Thorough)
        } catch (e: OutOfMemoryError) {
            // Not an Exception, so a plain `catch (e: Exception)` misses it. The ladder stays
            // under MAX_DECODE_PIXELS, but the upscale pass and zxing-cpp's own working buffers
            // still allocate, and failing to read a picture must never take the app down with it.
            Log.w("QrScanner", "Ran out of memory decoding a picked image", e)
            emptyList()
        } catch (e: Exception) {
            Log.w("QrScanner", "Could not decode picked image", e)
            emptyList()
        } finally {
            upscaled?.recycle()
            original.recycle()
        }
    }

    private fun readBounds(
        context: Context,
        uri: Uri,
    ): BitmapFactory.Options? =
        try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            options
        } catch (e: Exception) {
            Log.w("QrScanner", "Could not read image bounds", e)
            null
        }

    /**
     * Always an ARGB_8888 software bitmap: zxing-cpp reads the pixels over JNI, and a
     * hardware-backed bitmap has no pixels to read.
     */
    private fun loadBitmap(
        context: Context,
        uri: Uri,
        sampleSize: Int,
    ): Bitmap? =
        try {
            val options =
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        } catch (e: OutOfMemoryError) {
            Log.w("QrScanner", "Ran out of memory loading a picked image", e)
            null
        } catch (e: Exception) {
            Log.w("QrScanner", "Could not load picked image", e)
            null
        }

    /** The power-of-two `inSampleSize` that brings [longestEdge] to at most [target]. */
    private fun sampleSizeFor(
        longestEdge: Int,
        target: Int,
    ): Int {
        var sample = 1
        while (longestEdge / sample > target) sample *= 2
        return sample
    }
}
