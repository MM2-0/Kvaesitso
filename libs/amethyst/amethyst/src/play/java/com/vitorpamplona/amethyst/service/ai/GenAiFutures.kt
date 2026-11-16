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
package com.vitorpamplona.amethyst.service.ai

import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Completes the continuation on whichever thread finished the future. */
private val DIRECT_EXECUTOR = Executor { it.run() }

/**
 * Awaits an ML Kit GenAI [ListenableFuture], deliberately **detaching** when the caller is
 * cancelled instead of cancelling the future.
 *
 * Cancelling one of these futures crashes the process from a thread we don't own. When
 * `genai-rewriting` 1.0.0-beta1 starts an inference it asks AiCore for an
 * `ICancellationCallback` and registers it as the future's cancellation listener with no null
 * check (`zzbh.attachCompleter` builds `new zzbt(handle)`). AiCore is free to answer with a
 * null binder — `Parcel.readStrongBinder()` then returns null and the handle is null — so the
 * listener holds nothing, and the moment the future is cancelled `zzby.zzk(null)` dereferences
 * it:
 *
 * ```
 * Thread: AiCoreClientWorker-thread-5
 * java.lang.NullPointerException: Attempt to invoke interface method
 *   'void com.google.android.gms.internal.mlkit_genai_rewriting.zzp.zzd()' on a null object reference
 *     at com.google.android.gms.internal.mlkit_genai_rewriting.zzby.zzk
 *     at com.google.android.gms.internal.mlkit_genai_rewriting.zzbt.run
 *     at java.util.concurrent.ThreadPoolExecutor.runWorker
 * ```
 *
 * The NPE is thrown on ML Kit's own worker pool, so no `try`/`catch` on our side can see it:
 * it goes straight to the default uncaught handler and takes the app down. The composer
 * cancels these futures as a matter of course — a keystroke replaces the in-flight batch of
 * tones, and leaving the composer cancels `viewModelScope` — which is what made a beta-library
 * race a routine crash.
 *
 * Detaching leaves the inference to finish with nobody listening for its result. That wastes a
 * little on-device compute; cancelling wastes the process. There is nothing to fix on the ML
 * Kit side either — `genai-rewriting` has shipped no version past `1.0.0-beta1`.
 */
internal suspend fun <T> ListenableFuture<T>.awaitDetached(): T =
    suspendCancellableCoroutine { continuation ->
        addListener(
            {
                // A detached caller is already gone: don't even read the result.
                if (continuation.isActive) {
                    try {
                        continuation.resume(get())
                    } catch (e: CancellationException) {
                        continuation.cancel(e)
                    } catch (e: ExecutionException) {
                        continuation.resumeWithException(e.cause ?: e)
                    } catch (e: Exception) {
                        continuation.resumeWithException(e)
                    }
                }
            },
            DIRECT_EXECUTOR,
        )
        // Deliberately no invokeOnCancellation { cancel(true) }: cancelling is the crash.
    }
