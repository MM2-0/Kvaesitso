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
package com.vitorpamplona.amethyst.commons.sno

import androidx.compose.runtime.Stable
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.Options
import com.vitorpamplona.amethyst.commons.blurhash.PlatformImage
import com.vitorpamplona.amethyst.commons.service.image.toCoilImage
import com.vitorpamplona.quartz.cyberspace.deck0003Sno.SnoPayload

/**
 * A Simple Nostr Object to draw, addressed by the event that carried it.
 *
 * [cacheKey] is what Coil's memory cache keys on, so it has to name everything
 * that changes the pixels: the event, the view, the size — and the colours.
 *
 * The geometry needs no part in the key, because an addressable object
 * republished under the same `d` arrives as a new event id. The colours are not
 * like that. DECK-0003 §1.3a lets `colors` index a palette held in *another*
 * event, and until that event is fetched the object is drawn against the
 * built-in (§1.3b), so one event id legitimately produces two different
 * pictures within a second of each other. Keyed on the id alone, the first
 * would be served from memory forever and the fetch would buy nothing.
 */
@Stable
data class SnoObjectToRender(
    val payload: SnoPayload,
    val eventId: String,
    val size: Int,
    val yawDegrees: Float = SnoRasterizer.DEFAULT_YAW_DEGREES,
    val pitchDegrees: Float = SnoRasterizer.DEFAULT_PITCH_DEGREES,
    val background: Int = 0,
) {
    val cacheKey: String get() = "sno:$eventId:$size:$yawDegrees:$pitchDegrees:$background:${colourSignature()}"

    /**
     * A stand-in for the resolved palette: the colours themselves, hashed.
     *
     * The palette reference cannot serve instead, because what changes is not
     * which palette was named but whether it has arrived yet, and the payload
     * carries only the name. The colours are already resolved to ARGB by the
     * parser, so they are the thing that actually differs.
     */
    private fun colourSignature(): Int = 31 * payload.colors.contentHashCode() + (payload.faceColors?.contentHashCode() ?: 0)
}

/**
 * Draws an SNO into Coil's pipeline, so a thumbnail is rasterised once and then
 * served from the memory cache like any other image.
 *
 * The same shape as [com.vitorpamplona.amethyst.commons.service.image.BlurHashFetcher]:
 * pixels out of a headless decoder in `commons`, into a [PlatformImage], into a
 * Coil image. Nothing here is platform-specific.
 */
@Stable
class SnoFetcher(
    private val data: SnoObjectToRender,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val pixels =
            SnoRasterizer.render(
                payload = data.payload,
                width = data.size,
                height = data.size,
                yawDegrees = data.yawDegrees,
                pitchDegrees = data.pitchDegrees,
                background = data.background,
            )

        return ImageFetchResult(
            image = PlatformImage.create(pixels, data.size, data.size).toCoilImage(),
            isSampled = false,
            dataSource = DataSource.MEMORY,
        )
    }

    object Factory : Fetcher.Factory<SnoObjectToRender> {
        override fun create(
            data: SnoObjectToRender,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher = SnoFetcher(data)
    }

    object SKeyer : Keyer<SnoObjectToRender> {
        override fun key(
            data: SnoObjectToRender,
            options: Options,
        ): String = data.cacheKey
    }
}
