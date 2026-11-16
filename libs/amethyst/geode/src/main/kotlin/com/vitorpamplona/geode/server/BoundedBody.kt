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
package com.vitorpamplona.geode.server

import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveChannel
import io.ktor.utils.io.readAvailable

/**
 * Reads the request body up to [cap] bytes. Returns null when it is larger — by its declared
 * `Content-Length` or by what actually arrives — without reading past the cap; the caller answers 413.
 * The buffer is sized to the declared length, or grows from a small start, so a 50-byte command does
 * not cost a cap-sized allocation.
 */
internal suspend fun readBoundedBody(
    call: ApplicationCall,
    cap: Int,
): ByteArray? {
    val declared = call.request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
    if (declared != null && declared > cap) return null
    val ch = call.receiveChannel()
    // One byte past the declared length, so a body longer than it claimed is still caught at the cap.
    var buf = ByteArray(if (declared != null) declared.toInt() + 1 else minOf(cap + 1, INITIAL_BODY_BUFFER))
    var pos = 0
    while (pos <= cap) {
        if (pos == buf.size) buf = buf.copyOf(minOf(cap + 1, buf.size * 2))
        val read = ch.readAvailable(buf, pos, buf.size - pos)
        if (read <= 0) break
        pos += read
    }
    if (pos > cap) return null
    return if (pos == buf.size) buf else buf.copyOf(pos)
}

private const val INITIAL_BODY_BUFFER = 4 * 1024
