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

import com.vitorpamplona.amethyst.commons.notices.ActionFailed
import com.vitorpamplona.amethyst.commons.notices.SignerNotice
import com.vitorpamplona.amethyst.commons.notices.UserNoticeSink
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException

private const val TAG = "SignerErrors"

/**
 * Runs [action] and returns its result. When the signer refuses (the user rejected the request in
 * their signer app, it timed out, the key is read-only, or the signer is gone), it logs and returns
 * null instead of throwing: a rejection is the user's answer, and uncaught in a `viewModelScope`
 * launch it would crash the app.
 *
 * For code that holds only an `Account`. A ViewModel that has a `BaseAccountViewModel` uses its
 * `launchSigner` / `reportSignerErrors` instead, which also tell the user about read-only keys
 * and missing signer apps.
 */
suspend inline fun <T> logSignerErrors(
    tag: String,
    action: () -> T,
): T? =
    try {
        action()
    } catch (e: SignerExceptions) {
        Log.w(tag, "Signer refused", e)
        null
    }

/**
 * Runs [action] and tells the user, through [notices], why it failed when that is something they
 * can act on (a read-only key, a missing signer app, a refusal, data it could not use); quieter
 * signer answers are only logged. Cancellation still propagates.
 *
 * What `BaseAccountViewModel.launchSigner` and `reportSignerErrors` run their work in.
 */
suspend fun notifySignerErrors(
    notices: UserNoticeSink,
    action: suspend () -> Unit,
) {
    try {
        action()
    } catch (e: CancellationException) {
        // MUST stay ahead of the IllegalStateException arm below: java.util.concurrent
        // .CancellationException extends IllegalStateException, so without this every cancelled
        // signer coroutine — a superseded debounce, a scope torn down mid-sign — would be
        // swallowed there and shown to the user as a "signer" toast reading
        // "JobCancellationException: StandaloneCoroutine was cancelled". Cancellation is not a
        // signer failure and must propagate for structured concurrency to work.
        throw e
    } catch (_: SignerExceptions.ReadOnlyException) {
        notices.notify(SignerNotice.ReadOnly)
    } catch (_: SignerExceptions.UnauthorizedDecryptionException) {
        notices.notify(SignerNotice.UnauthorizedDecryption)
    } catch (_: SignerExceptions.SignerNotFoundException) {
        notices.notify(SignerNotice.SignerNotFound)
    } catch (e: SignerExceptions.TimedOutException) {
        Log.w(TAG, "TimedOutException", e)
    } catch (e: SignerExceptions.NothingToDecrypt) {
        Log.w(TAG, "NothingToDecrypt", e)
    } catch (e: SignerExceptions.CouldNotPerformException) {
        Log.w(TAG, "CouldNotPerformException", e)
    } catch (e: SignerExceptions.ManuallyUnauthorizedException) {
        Log.w(TAG, "ManuallyUnauthorizedException", e)
    } catch (e: SignerExceptions.AutomaticallyUnauthorizedException) {
        Log.w(TAG, "AutomaticallyUnauthorizedException", e)
    } catch (e: SignerExceptions.RunningOnBackgroundWithoutAutomaticPermissionException) {
        Log.w(TAG, "TimedOutRunningOnBackgroundWithoutAutomaticPermissionExceptionException", e)
    } catch (e: SignerExceptions) {
        // Any other refusal (e.g. a bunker without the method): the signer's answer, not a crash.
        Log.w(TAG, "SignerExceptions", e)
    } catch (e: IllegalStateException) {
        notices.notify(SignerNotice.Failed(e))
    } catch (e: IllegalArgumentException) {
        // Data the action could not use (malformed JSON, a bad key or tag from a relay or
        // another app): viewModelScope has no handler, so letting it escape kills the app.
        Log.w(TAG, "Action failed on data it could not use", e)
        notices.notify(ActionFailed(e))
    }
}

/**
 * Runs [action], logging instead of throwing when it fails on data it could not use: bad input
 * from a relay or another app throws [IllegalArgumentException] (Kite's parse errors, a NIP-55
 * signer's malformed reply), and uncaught in a `viewModelScope` launch it would crash the app.
 */
suspend inline fun logUnusableData(
    tag: String,
    action: () -> Unit,
) {
    try {
        action()
    } catch (e: IllegalArgumentException) {
        Log.w(tag, "Background task failed on data it could not use", e)
    }
}
