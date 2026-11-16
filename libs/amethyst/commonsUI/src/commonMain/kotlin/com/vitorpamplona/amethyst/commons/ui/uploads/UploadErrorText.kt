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
package com.vitorpamplona.amethyst.commons.ui.uploads

import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.avif_metadata_strip_failed
import com.vitorpamplona.amethyst.commons.resources.blossom_payment_required
import com.vitorpamplona.amethyst.commons.resources.could_not_check_downloaded_file
import com.vitorpamplona.amethyst.commons.resources.could_not_download_from_the_server
import com.vitorpamplona.amethyst.commons.resources.could_not_open_the_compressed_file
import com.vitorpamplona.amethyst.commons.resources.failed_to_upload_media
import com.vitorpamplona.amethyst.commons.resources.file_too_large_to_encrypt
import com.vitorpamplona.amethyst.commons.resources.login_with_a_private_key_to_be_able_to_upload
import com.vitorpamplona.amethyst.commons.resources.server_did_not_provide_a_url_after_uploading
import com.vitorpamplona.amethyst.commons.resources.server_returned_different_file
import com.vitorpamplona.amethyst.commons.resources.upload_cancelled
import com.vitorpamplona.amethyst.commons.service.uploads.UploadError
import com.vitorpamplona.amethyst.commons.service.uploads.UploadingState
import org.jetbrains.compose.resources.StringResource

/** The message template for this upload failure; fill it with [UploadingState.Error.params]. */
val UploadingState.Error.errorResource: StringResource
    get() =
        when (error) {
            UploadError.COULD_NOT_CHECK_DOWNLOADED_FILE -> Res.string.could_not_check_downloaded_file
            UploadError.COULD_NOT_OPEN_COMPRESSED_FILE -> Res.string.could_not_open_the_compressed_file
            UploadError.LOGIN_WITH_PRIVATE_KEY -> Res.string.login_with_a_private_key_to_be_able_to_upload
            UploadError.FAILED_TO_UPLOAD_MEDIA -> Res.string.failed_to_upload_media
            UploadError.BLOSSOM_PAYMENT_REQUIRED -> Res.string.blossom_payment_required
            UploadError.SERVER_DID_NOT_PROVIDE_URL -> Res.string.server_did_not_provide_a_url_after_uploading
            UploadError.COULD_NOT_DOWNLOAD_FROM_SERVER -> Res.string.could_not_download_from_the_server
            UploadError.SERVER_RETURNED_DIFFERENT_FILE -> Res.string.server_returned_different_file
            UploadError.FILE_TOO_LARGE_TO_ENCRYPT -> Res.string.file_too_large_to_encrypt
            UploadError.AVIF_METADATA_STRIP_FAILED -> Res.string.avif_metadata_strip_failed
            UploadError.UPLOAD_CANCELLED -> Res.string.upload_cancelled
        }
