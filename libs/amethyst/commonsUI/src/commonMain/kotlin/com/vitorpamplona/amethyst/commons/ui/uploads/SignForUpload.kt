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
import com.vitorpamplona.amethyst.commons.resources.failed_to_upload_media_no_details
import com.vitorpamplona.amethyst.commons.resources.login_with_a_private_key_to_be_able_to_sign_events
import com.vitorpamplona.amethyst.commons.resources.read_only_user
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.utils.Log

/**
 * Runs a signing step of an upload, such as the server's auth event, and returns its result,
 * or null when the signer refused. The attachment is then left out, so the composer must not
 * show it as uploaded:
 * - a read-only key gets the "log in with a private key" message through [onError];
 * - a rejection in the signer app is the user's own answer, so it is only logged;
 * - anything else (timed out, signer missing, ...) is reported through [onError].
 */
suspend fun <T> signForUpload(
    onError: (title: String, message: String) -> Unit,
    action: suspend () -> T,
): T? =
    try {
        action()
    } catch (_: SignerExceptions.ReadOnlyException) {
        onError(
            loadStringRes(Res.string.read_only_user),
            loadStringRes(Res.string.login_with_a_private_key_to_be_able_to_sign_events),
        )
        null
    } catch (e: SignerExceptions.ManuallyUnauthorizedException) {
        Log.w("SignForUpload", "Rejected in the signer", e)
        null
    } catch (e: SignerExceptions.AutomaticallyUnauthorizedException) {
        Log.w("SignForUpload", "Rejected by the signer's saved policy", e)
        null
    } catch (e: SignerExceptions) {
        onError(loadStringRes(Res.string.failed_to_upload_media_no_details), e.message ?: e::class.simpleName.orEmpty())
        null
    }
