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

import okio.Path

actual abstract class MediaUri {
    actual abstract override fun toString(): String
}

/** A picked file addressed by a path or URL string. */
class StringMediaUri(
    val value: String,
) : MediaUri() {
    override fun toString(): String = value

    // Value equality, like android.net.Uri: the upload pipeline tells a temp file from the
    // user's own by comparing addresses, and deletes the temp one.
    override fun equals(other: Any?): Boolean = other is StringMediaUri && other.value == value

    override fun hashCode(): Int = value.hashCode()
}

actual fun MediaUri.lastPathSegmentOrNull(): String? = lastPathSegmentOf(toString())

actual fun mediaUriOfFile(path: Path): MediaUri = StringMediaUri(path.toString())
