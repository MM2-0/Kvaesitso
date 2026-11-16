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

/** Why an upload failed. The UI names it with `UploadingState.Error.errorResource`. */
enum class UploadError {
    COULD_NOT_CHECK_DOWNLOADED_FILE,
    COULD_NOT_OPEN_COMPRESSED_FILE,
    LOGIN_WITH_PRIVATE_KEY,
    FAILED_TO_UPLOAD_MEDIA,
    BLOSSOM_PAYMENT_REQUIRED,
    SERVER_DID_NOT_PROVIDE_URL,
    COULD_NOT_DOWNLOAD_FROM_SERVER,
    SERVER_RETURNED_DIFFERENT_FILE,
    FILE_TOO_LARGE_TO_ENCRYPT,
    AVIF_METADATA_STRIP_FAILED,
    UPLOAD_CANCELLED,
}
