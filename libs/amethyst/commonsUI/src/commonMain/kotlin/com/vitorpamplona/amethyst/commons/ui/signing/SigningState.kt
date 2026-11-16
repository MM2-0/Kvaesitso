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
package com.vitorpamplona.amethyst.commons.ui.signing

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.signing_failed
import com.vitorpamplona.amethyst.commons.resources.signing_operation_failed
import com.vitorpamplona.amethyst.commons.resources.signing_rejected
import com.vitorpamplona.amethyst.commons.resources.signing_timed_out
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.cancellation.CancellationException

sealed class SigningOpState {
    data object Idle : SigningOpState()

    data object Pending : SigningOpState()

    /**
     * Signing is in flight AND has a known step count — typically a NIP-17
     * group send via a remote signer (bunker), where the UI can usefully show
     * "Encrypting via remote signer ([current] of [total])".
     *
     * Treated as Pending for all is-pending checks via [isPending] below;
     * existing callers that branch on `is Pending` keep working unchanged.
     * New callers can render the counter when [SigningOpState] is `Progress`.
     */
    data class Progress(
        val current: Int,
        val total: Int,
        val label: String? = null,
    ) : SigningOpState()

    data class Error(
        val message: String,
    ) : SigningOpState()
}

/** True when signing is in flight, regardless of whether step counts are known. */
fun SigningOpState.isPending(): Boolean = this is SigningOpState.Pending || this is SigningOpState.Progress

/**
 * Global signing status — any [SigningState] instance updates this when signing starts/ends.
 * Observe [globalState] from a screen-level composable to show a persistent status bar.
 */
@OptIn(ExperimentalAtomicApi::class)
object GlobalSigningStatus {
    var globalState by mutableStateOf<SigningOpState>(SigningOpState.Idle)
        private set

    private val activeCount = AtomicInt(0)

    fun onPending() {
        activeCount.addAndFetch(1)
        globalState = SigningOpState.Pending
    }

    fun onIdle() {
        if (activeCount.addAndFetch(-1) <= 0) {
            activeCount.store(0)
            globalState = SigningOpState.Idle
        }
    }

    fun onError(message: String) {
        activeCount.addAndFetch(-1)
        globalState = SigningOpState.Error(message)
    }
}

/**
 * Per-component state holder for remote signer operations.
 * Tracks Idle/Pending/Error — no Success state (existing UI handles success).
 * Error resets to Idle on next execute() call (tap = retry).
 * Also updates [GlobalSigningStatus] for screen-level status bar.
 */
@Stable
class SigningState {
    var state by mutableStateOf<SigningOpState>(SigningOpState.Idle)
        private set

    var errorMessage: String? = null
        private set

    suspend fun <T> execute(block: suspend () -> T): T? {
        if (state.isPending()) return null
        state = SigningOpState.Pending
        GlobalSigningStatus.onPending()
        errorMessage = null
        return try {
            val result = withContext(Dispatchers.IO) { block() }
            state = SigningOpState.Idle
            GlobalSigningStatus.onIdle()
            result
        } catch (e: CancellationException) {
            state = SigningOpState.Idle
            GlobalSigningStatus.onIdle()
            throw e
        } catch (e: SignerExceptions.TimedOutException) {
            setError(loadStringRes(Res.string.signing_timed_out))
            null
        } catch (e: SignerExceptions.ManuallyUnauthorizedException) {
            setError(loadStringRes(Res.string.signing_rejected))
            null
        } catch (e: SignerExceptions) {
            setError(e.message ?: loadStringRes(Res.string.signing_failed))
            null
        } catch (e: Exception) {
            setError(e.message ?: loadStringRes(Res.string.signing_operation_failed))
            null
        }
    }

    /**
     * Update the in-flight signing state with a progress counter. Use during
     * multi-step operations (NIP-17 group send via bunker, batch zaps) to
     * show the user how far the in-flight op has progressed.
     *
     * Only takes effect while [state] is Pending or Progress — no-op
     * otherwise so callers don't have to gate on Idle/Error themselves.
     */
    fun updateProgress(
        current: Int,
        total: Int,
        label: String? = null,
    ) {
        if (!state.isPending()) return
        state = SigningOpState.Progress(current, total, label)
    }

    private fun setError(message: String) {
        errorMessage = message
        state = SigningOpState.Error(message)
        GlobalSigningStatus.onError(message)
    }
}
