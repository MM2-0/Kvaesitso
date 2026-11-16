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
package com.vitorpamplona.amethyst.service.uploads.hls

import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploadResult
import java.io.File

/**
 * Abstraction over a blob upload transport so the HLS publish orchestrator can stay
 * unit-testable. Production wiring adapts this to either
 * [com.vitorpamplona.amethyst.service.uploads.nip96.Nip96Uploader] or
 * [com.vitorpamplona.amethyst.service.uploads.blossom.BlossomUploader]. The HLS orchestrator
 * wraps this in an [com.davotoula.lightcompressor.hls.HlsUploaded]-returning lambda for
 * [com.davotoula.lightcompressor.hls.HlsUploadHelper.run]; the library itself drives the
 * per-rendition map of [MediaUploadResult]s back into
 * [com.davotoula.lightcompressor.hls.HlsUploadResult.uploads] so per-rendition sha256/size
 * can flow into the NIP-71 event's imeta tags.
 *
 * The optional [onProgress] callback is invoked as bytes flow to the wire so the UI can show
 * a smooth per-file progress bar instead of the coarse "file N of M" counter. Callers that
 * don't need byte progress pass nothing — the default is a no-op.
 */
fun interface HlsBlobUploader {
    suspend fun upload(
        file: File,
        contentType: String,
        onProgress: (bytesWritten: Long, totalBytes: Long) -> Unit,
    ): MediaUploadResult
}
