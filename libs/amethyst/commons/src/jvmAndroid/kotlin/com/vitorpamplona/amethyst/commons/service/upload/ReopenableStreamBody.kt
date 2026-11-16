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
package com.vitorpamplona.amethyst.commons.service.upload

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSource
import okio.source
import java.io.InputStream

/**
 * An upload body that opens a fresh stream from [openStream] on every [writeTo].
 *
 * OkHttp writes a body more than once when it retries: a pooled connection the server or a
 * NAT dropped while idle, a `408`, a refused HTTP/2 stream. A body that wraps one already-open
 * [InputStream] hands that retry a stream the first attempt consumed and closed, so the upload
 * fails with `IOException: Stream Closed` (or sends an empty body) and the real error is lost.
 *
 * [onProgress] reports the bytes of the current attempt; it restarts from zero on a retry.
 */
class ReopenableStreamBody(
    private val contentType: MediaType?,
    private val contentLength: Long,
    private val onProgress: ((bytesWritten: Long, totalBytes: Long) -> Unit)? = null,
    private val openStream: () -> InputStream,
) : RequestBody() {
    override fun contentType() = contentType

    override fun contentLength() = contentLength

    override fun writeTo(sink: BufferedSink) {
        val source = openStream().source()
        if (onProgress == null) {
            source.use(sink::writeAll)
        } else {
            // ForwardingSource.close delegates to the wrapped source, so one `use` on the
            // outermost wrapper closes the stream.
            val countingSource =
                object : ForwardingSource(source) {
                    var totalRead = 0L

                    override fun read(
                        sink: Buffer,
                        byteCount: Long,
                    ): Long {
                        val n = super.read(sink, byteCount)
                        if (n > 0) {
                            totalRead += n
                            onProgress(totalRead, contentLength)
                        }
                        return n
                    }
                }
            countingSource.use { sink.writeAll(it) }
        }
    }
}
