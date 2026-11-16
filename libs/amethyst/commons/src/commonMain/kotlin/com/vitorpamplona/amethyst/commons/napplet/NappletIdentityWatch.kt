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
package com.vitorpamplona.amethyst.commons.napplet

import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletProtocolJson
import com.vitorpamplona.quartz.utils.concurrent.ConcurrentMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Streams `identity.changed` pushes to an applet that registered `napplet.identity.onChanged`. It
 * collects the active user's public key ([pubKey]) and, on every *subsequent* change (the current
 * value is dropped — the applet already has it via `getPublicKey`), encodes and pushes the new key
 * (or `""` when no account is signed in) to the caller-supplied sink.
 *
 * Watches are keyed by the trusted launch token so concurrent surfaces cannot replace each other's
 * streams. A watch starts only after that surface successfully obtains its public-key snapshot.
 */
class NappletIdentityWatch(
    private val scope: CoroutineScope,
    private val pubKey: (boundPubKey: String) -> Flow<String>,
) {
    private val jobs = ConcurrentMap<String, Job>()

    fun start(
        watchId: String,
        boundPubKey: String,
        push: (String) -> Unit,
    ) {
        // A surface re-created under the same launch token (a tab re-arming its session) starts a new watch
        // with its own sink; replace the old stream rather than keep pushing to the gone surface's Messenger.
        jobs.remove(watchId)?.cancel()
        jobs.getOrPut(watchId) {
            val id = watchId
            scope
                .launch {
                    pubKey(boundPubKey)
                        .distinctUntilChanged()
                        .drop(1)
                        .collect { push(NappletProtocolJson.encodeIdentityChanged(it)) }
                }.also { job ->
                    job.invokeOnCompletion { jobs.remove(id, job) }
                }
        }
    }

    /** Stops the watch started under [watchId], if any. */
    fun stop(watchId: String) {
        jobs.remove(watchId)?.cancel()
    }

    fun stopAll() {
        jobs.snapshot().values.forEach { it.cancel() }
        jobs.clear()
    }
}
