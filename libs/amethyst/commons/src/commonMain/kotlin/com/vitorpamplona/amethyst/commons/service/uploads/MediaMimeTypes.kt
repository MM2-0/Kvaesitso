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
package com.vitorpamplona.amethyst.commons.service.uploads

// RFC 9081 defines image/avif for both still and animated AVIF.
// image/avif-sequence is NOT IANA-registered and is intentionally not handled.
const val AVIF_MIME = "image/avif"
const val AVIF_EXTENSION = "avif"

fun isAvif(contentType: String?): Boolean = contentType?.equals(AVIF_MIME, ignoreCase = true) == true

/**
 * AVIF-only fallback for upload filename extensions.
 *
 * Returns `"avif"` when [contentType] is `image/avif`, `null` for everything else.
 * Callers chain this after `MimeTypeMap.getSingleton().getExtensionFromMimeType(...)`
 * because older Android versions of MimeTypeMap don't know AVIF, and some upload
 * servers reject extension-less filenames. This helper is intentionally not a
 * general MIME-to-extension utility — handle non-AVIF types via MimeTypeMap.
 */
fun extensionFromMimeType(contentType: String?): String? =
    when {
        isAvif(contentType) -> AVIF_EXTENSION
        else -> null
    }
