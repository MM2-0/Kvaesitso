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
package com.vitorpamplona.amethyst.commons.util

import com.vitorpamplona.quartz.utils.Log
import java.io.File

/**
 * Deletes this file, warning on real failures only. [File.delete] returns false
 * both when deletion genuinely fails and when the file is already gone (e.g.
 * removed by a concurrent eviction in another thread or process); only the
 * former deserves a warning.
 *
 * @param tag the log tag of the calling component
 * @param what a short noun for the log message, e.g. "thumbnail" or "blob"
 * @return true when the file no longer exists, whether this call deleted it or
 *   it was already gone; false when it still exists and could not be deleted.
 */
fun File.deleteOrWarn(
    tag: String,
    what: String,
): Boolean {
    if (delete() || !exists()) return true
    Log.w(tag) { "Failed to delete $what $absolutePath" }
    return false
}
