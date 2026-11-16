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
package com.vitorpamplona.amethyst.commons.connectedApps.consent

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Questions waiting on the user, for a host to show however its platform shows a dialog: Android
 * launches an Activity with the [Request.token], the desktop opens a window for each app's oldest
 * question. [ask] suspends until the question is answered.
 *
 * Every question fails closed: one left without an answer (its dialog closed, its app gone, its
 * caller cancelled) resolves to [unanswered], which never grants anything.
 */
class ConsentQueue<I, A>(
    private val unanswered: A,
) {
    class Request<I, A> internal constructor(
        /** Names the question where only a string can travel (an Activity's extras). */
        val token: String,
        /** The app asking (a napplet's or site's coordinate), for a host that shows questions per app. */
        val app: String?,
        val info: I,
        /** Arrival order across every queue, so a host can show several queues' questions oldest first. */
        val order: Long,
        private val unanswered: A,
    ) {
        internal val result = CompletableDeferred<A>()

        fun answer(value: A) {
            result.complete(value)
        }

        /** Answers "no" without the user. */
        fun dismiss() = answer(unanswered)
    }

    private val mutablePending = MutableStateFlow<List<Request<I, A>>>(emptyList())

    /** The questions waiting, oldest first. */
    val pending: StateFlow<List<Request<I, A>>> = mutablePending.asStateFlow()

    /**
     * Queues [info] and waits for its answer. [onQueued] runs once the question is in [pending] (to
     * show it); [onSettled] once it has left, however it ended, cancellation included (to take down
     * whatever showed it).
     */
    suspend fun ask(
        info: I,
        app: String? = null,
        onQueued: suspend (Request<I, A>) -> Unit = {},
        onSettled: suspend (Request<I, A>) -> Unit = {},
    ): A {
        val request = Request(newToken(), app, info, nextArrival(), unanswered)
        mutablePending.update { it + request }
        try {
            onQueued(request)
            return request.result.await()
        } finally {
            mutablePending.update { it - request }
            withContext(NonCancellable) { onSettled(request) }
        }
    }

    fun info(token: String): I? = find(token)?.info

    fun answer(
        token: String,
        value: A,
    ) {
        find(token)?.answer(value)
    }

    /** Answers "no" without the user, when its dialog closed without a choice. */
    fun dismiss(token: String) {
        find(token)?.dismiss()
    }

    /** Answers "no" for every question [app] left behind when it closed. */
    fun dismissApp(app: String) {
        pending.value.forEach { if (it.app == app) it.dismiss() }
    }

    private fun find(token: String) = pending.value.firstOrNull { it.token == token }

    companion object {
        @OptIn(ExperimentalAtomicApi::class)
        private val arrivals = AtomicLong(0L)

        @OptIn(ExperimentalAtomicApi::class)
        private fun nextArrival() = arrivals.incrementAndFetch()

        @OptIn(ExperimentalUuidApi::class)
        private fun newToken() = Uuid.random().toString()
    }
}
