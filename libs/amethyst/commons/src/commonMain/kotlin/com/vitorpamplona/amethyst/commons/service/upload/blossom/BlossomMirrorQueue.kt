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
package com.vitorpamplona.amethyst.commons.service.upload.blossom

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.service.upload.BlossomBlobClient
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/** Aggregate progress of a running "sync all my blobs to all my servers" sweep. */
@Immutable
data class BlossomSyncState(
    val total: Int,
    val done: Int,
    val failed: Int,
    val running: Boolean,
) {
    val succeeded get() = done - failed
    val fraction get() = if (total == 0) 0f else done.toFloat() / total
}

/** One completed mirror step, streamed so an open manager screen can flip its pill live. */
@Immutable
data class BlossomMirrorResult(
    val hash: HexKey,
    val server: String,
    val ok: Boolean,
)

/**
 * App-level BUD-04 mirror queue, modeled on the PoW publish queue: it runs on the
 * application IO scope so a sweep keeps going while the
 * user navigates the app, and exposes [state] for a floating progress banner mounted
 * at the navigation root. Servers that require payment are skipped (counted as
 * failed) — those are paid for individually from the manager screen.
 */
class BlossomMirrorQueue(
    private val scope: CoroutineScope,
    /** Builds the client that talks to one target server (proxying, Tor and pooling are the platform's). */
    private val clientFor: (serverBaseUrl: String) -> BlossomBlobClient,
    /** Invoked (on the foreground thread that called [start]) when a sweep begins, to start the FGS. */
    private val onActive: () -> Unit = {},
) {
    data class Task(
        val hash: HexKey,
        val sourceUrl: String,
        val size: Long?,
        /** Descriptor MIME, used when a target lacks `/mirror` and we fall back to `/upload`. */
        val contentType: String?,
        val targets: List<String>,
    )

    private val _state = MutableStateFlow<BlossomSyncState?>(null)
    val state: StateFlow<BlossomSyncState?> = _state.asStateFlow()

    private val _results = MutableSharedFlow<BlossomMirrorResult>(extraBufferCapacity = 128)
    val results: SharedFlow<BlossomMirrorResult> = _results.asSharedFlow()

    private var job: Job? = null

    val isRunning get() = _state.value?.running == true

    /**
     * Enqueue a sweep. Returns true when this call actually started one; false (no-op) when a
     * sweep is already running or there's nothing to do — the caller can tell the difference so
     * it doesn't report an import/sync as started when the work was silently dropped.
     */
    fun start(
        account: Account,
        tasks: List<Task>,
    ): Boolean {
        if (isRunning) return false
        val work = tasks.flatMap { t -> t.targets.map { t to it } }
        if (work.isEmpty()) return false

        // Publish the active state and start the foreground service synchronously (we're on the
        // foreground thread here, which is what dataSync FGS starts require) before the sweep runs.
        _state.value = BlossomSyncState(total = work.size, done = 0, failed = 0, running = true)
        onActive()

        // Parallelize ACROSS servers but stay serial WITHIN a server, so every server gets a
        // steady one-at-a-time stream (no hammering / rate-limit storms) while all of the user's
        // servers work at once. Wall-clock ≈ the slowest single server's queue, not the sum.
        val byServer: Map<String, List<Task>> = work.groupBy({ it.second }, { it.first })

        job =
            scope.launch {
                byServer
                    .map { (target, serverTasks) ->
                        async {
                            for (task in serverTasks) {
                                val ok = mirrorOne(account, task, target)
                                _results.tryEmit(BlossomMirrorResult(task.hash, target, ok))
                                _state.update { it?.copy(done = it.done + 1, failed = it.failed + if (ok) 0 else 1) }
                            }
                        }
                    }.awaitAll()
                _state.update { it?.copy(running = false) }
            }
        return true
    }

    private suspend fun mirrorOne(
        account: Account,
        task: Task,
        target: String,
    ): Boolean =
        try {
            val auth = account.createBlossomUploadAuth(task.hash, task.size ?: 0L, "Mirror ${task.hash}").toAuthorizationHeader()
            clientFor(target)
                .mirrorOrUpload(task.sourceUrl, task.hash, task.contentType ?: DEFAULT_MIME_TYPE, target, auth)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("BlossomMirrorQueue", "mirror ${task.hash} -> $target failed", e)
            false
        }

    /** Cancel a running sweep and clear the banner. */
    fun cancel() {
        job?.cancel()
        job = null
        _state.value = null
    }

    /** Dismiss the finished-summary banner (no effect while still running). */
    fun dismiss() {
        if (!isRunning) _state.value = null
    }

    companion object {
        /** Fallback MIME for a `/upload` when the source descriptor carried no type. */
        private const val DEFAULT_MIME_TYPE = "application/octet-stream"
    }
}
