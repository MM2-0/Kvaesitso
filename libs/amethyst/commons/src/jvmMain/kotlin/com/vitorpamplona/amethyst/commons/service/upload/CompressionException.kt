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

/**
 * Typed failures from the image-compression pipeline. The fail-loud
 * dialog renders [message] directly; subclass identity drives the
 * "Send Original" decision (e.g., InputTooLarge → user can still
 * bypass; UnsupportedFormat → bypass uploads raw bytes).
 *
 * `Exception(message, cause)` already wires `cause` into the
 * `Throwable.cause` slot — DO NOT call `initCause` again, doing so
 * throws `IllegalStateException("Can't overwrite cause …")` at
 * construction time.
 */
sealed class CompressionException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    /** Source format has no usable decoder in v1 (AVIF, HEIC). */
    class UnsupportedFormat(
        val format: String,
    ) : CompressionException("Format not supported: $format")

    /**
     * Source pixel count exceeds [ImageReencoder.MAX_INPUT_PIXELS]
     * (50 megapixels by default). Guards against memory-bomb inputs.
     */
    class InputTooLarge(
        val pixels: Long,
        val limit: Long,
    ) : CompressionException("Image has $pixels pixels — exceeds the $limit limit")

    /** Decoder or encoder threw — wrap the cause. */
    class EncodeFailed(
        cause: Throwable,
    ) : CompressionException("Image encode failed: ${cause.message ?: cause::class.simpleName}", cause)
}
