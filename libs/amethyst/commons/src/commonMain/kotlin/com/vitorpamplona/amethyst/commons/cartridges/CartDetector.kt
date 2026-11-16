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
package com.vitorpamplona.amethyst.commons.cartridges

import androidx.collection.LruCache
import com.vitorpamplona.amethyst.commons.service.http.IRoleBasedHttpClientBuilder
import com.vitorpamplona.kiko.cart.CartLoader
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag

/**
 * Tells game cartridges (`.p8.png`, a 160x205 PNG with the game in its low bits) apart from other
 * images in notes. Posts rarely say what they are (the one seen in the wild is a bare
 * `image.nostr.build/<hash>.png`), so a PNG of unknown size is checked by reading only its
 * header, once per URL.
 */
object CartDetector {
    private val results = LruCache<String, Boolean>(500)

    /** Whether [url] could be a cart, without the network: a PNG whose size is 160x205 or unknown. */
    fun mightBeCart(
        url: String,
        mimeType: String?,
        dim: DimensionTag?,
    ): Boolean {
        if (dim != null) return isCartSize(dim)
        if (mimeType != null) return mimeType.equals("image/png", ignoreCase = true)
        return url.substringBefore('?').substringBefore('#').endsWith(".png", ignoreCase = true)
    }

    /** Whether an image of [dim] has a cart's size. */
    fun isCartSize(dim: DimensionTag): Boolean = dim.width == CartLoader.CART_WIDTH && dim.height == CartLoader.CART_HEIGHT

    /** The answer for [url] if one is known already. */
    fun known(url: String): Boolean? = results[url]

    /**
     * Whether the image at [url] has a cart's shape. Trusts a declared [dim]; otherwise reads the
     * PNG header through [httpClients]. A failed request is not remembered, so it is asked again.
     */
    suspend fun hasCartShape(
        url: String,
        dim: DimensionTag?,
        httpClients: IRoleBasedHttpClientBuilder,
    ): Boolean {
        results[url]?.let { return it }
        val answer =
            if (dim != null) {
                isCartSize(dim)
            } else {
                val header = httpClients.fetchMediaPrefix(url, CartLoader.SHAPE_PREFIX_BYTES) ?: return false
                CartLoader.hasCartShape(header)
            }
        results.put(url, answer)
        return answer
    }

    /** Whether [bytes] are a cart that loads: what an upload checks before it would re-encode a PNG. */
    fun isCartridge(bytes: ByteArray): Boolean = CartLoader.isCart(bytes)
}
