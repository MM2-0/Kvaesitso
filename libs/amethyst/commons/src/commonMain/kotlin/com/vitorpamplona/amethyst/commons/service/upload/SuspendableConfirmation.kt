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

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume

data class ConfirmationCallbacks(
    val onConfirm: () -> Unit,
    val onCancel: () -> Unit,
)

@Stable
class SuspendableConfirmation {
    var state by mutableStateOf<ConfirmationCallbacks?>(null)
        private set

    private val mutex = Mutex()

    suspend fun awaitConfirmation(): Boolean =
        mutex.withLock {
            suspendCancellableCoroutine { continuation ->
                var callbacks: ConfirmationCallbacks? = null

                // The dialog can fire two of its callbacks (a button tap and an outside-tap
                // dismiss) before the recomposition that removes it; only the first may resume.
                fun answer(confirmed: Boolean) {
                    if (state === callbacks) state = null
                    if (continuation.isActive) continuation.resume(confirmed)
                }

                callbacks =
                    ConfirmationCallbacks(
                        onConfirm = { answer(true) },
                        onCancel = { answer(false) },
                    )
                state = callbacks

                // An upload cancelled while waiting must not leave a dialog with dead buttons.
                continuation.invokeOnCancellation { if (state === callbacks) state = null }
            }
        }
}
