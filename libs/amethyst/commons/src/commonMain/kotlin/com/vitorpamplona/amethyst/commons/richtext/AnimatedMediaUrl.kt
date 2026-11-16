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
package com.vitorpamplona.amethyst.commons.richtext

/** True for GIF urls (path ends in `.gif`, optionally followed by a query or fragment). */
fun isAnimatedGifUrl(url: String): Boolean =
    url.endsWith(".gif", ignoreCase = true) ||
        url.contains(".gif?", ignoreCase = true) ||
        url.contains(".gif#", ignoreCase = true)

/** True for AVIF urls (path ends in `.avif`, optionally followed by a query or fragment). */
fun isAvifUrl(url: String): Boolean =
    url.endsWith(".avif", ignoreCase = true) ||
        url.contains(".avif?", ignoreCase = true) ||
        url.contains(".avif#", ignoreCase = true)

/** True for GIF/AVIF urls, which may animate and so bypass still-image pipelines like the avatar thumbnail cache. */
fun isAnimatedMediaUrl(url: String): Boolean = isAnimatedGifUrl(url) || isAvifUrl(url)
