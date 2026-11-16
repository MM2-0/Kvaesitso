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
package com.vitorpamplona.amethyst.commons.relayClient.subscriptions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.vitorpamplona.amethyst.commons.relayClient.composeSubscriptionManagers.ComposeSubscriptionManager
import com.vitorpamplona.amethyst.commons.relayClient.composeSubscriptionManagers.MutableComposeSubscriptionManager
import com.vitorpamplona.amethyst.commons.relayClient.composeSubscriptionManagers.MutableQueryState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val UNSUBSCRIBE_GRACE_MILLIS = 30_000L

/**
 * A lifecycle-aware version of [KeyDataSourceSubscription] that subscribes
 * when the lifecycle reaches STARTED and unsubscribes 30 seconds after it
 * reaches STOPPED. If the lifecycle returns to STARTED before the grace
 * period elapses, the pending unsubscribe is cancelled and the subscription
 * keeps running uninterrupted.
 *
 * The grace window absorbs short app switches (copy a snippet from another
 * app, dismiss a notification, glance at recents) without tearing down and
 * rebuilding the relay REQ — which would otherwise lose EOSE state and
 * trigger a refetch on return.
 *
 * Lifecycle transitions are observed with a main-thread [LifecycleEventObserver],
 * which fires synchronously during `onStop`/`onStart`. Detecting the transition
 * via a background-dispatched flow instead delivered `ON_STOP` up to ~60s late on
 * a backgrounded device (the collector only resumed on the next relay keep-alive
 * tick), leaving feeds connected long after the app was paused. Only the grace
 * *delay* runs on a [Dispatchers.IO] scope, so it isn't gated by the UI
 * frame clock (which stops ticking while backgrounded); returning to STARTED
 * cancels the pending unsubscribe before it fires.
 *
 * Use this for heavy feed subscriptions (home, video, discovery, chatroom list)
 * that should NOT run when the app is truly in the background. When an
 * always-on notification service keeps the relay client connected, these
 * subscriptions would otherwise leak bandwidth on feeds nobody is viewing.
 *
 * Lightweight subscriptions that should always run (account metadata, notifications,
 * gift wraps) should continue using the regular [KeyDataSourceSubscription].
 */
@Composable
fun <T> LifecycleAwareKeyDataSourceSubscription(
    state: T,
    dataSource: ComposeSubscriptionManager<T>,
) {
    LifecycleAwareSubscription(
        key = state,
        subscribe = { dataSource.subscribe(state) },
        unsubscribe = { dataSource.unsubscribe(state) },
    )
}

@Composable
fun <T> LifecycleAwareKeyDataSourceSubscription(
    states: List<T>,
    dataSource: ComposeSubscriptionManager<T>,
) {
    LifecycleAwareSubscription(
        key = states,
        subscribe = { dataSource.subscribe(states) },
        unsubscribe = { dataSource.unsubscribe(states) },
    )
}

@Composable
fun <T : MutableQueryState> LifecycleAwareKeyDataSourceSubscription(
    state: T,
    dataSource: MutableComposeSubscriptionManager<T>,
) {
    LifecycleAwareSubscription(
        key = state,
        subscribe = { dataSource.subscribe(state) },
        unsubscribe = { dataSource.unsubscribe(state) },
    )
}

@Composable
private fun LifecycleAwareSubscription(
    key: Any?,
    subscribe: () -> Unit,
    unsubscribe: () -> Unit,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    DisposableEffect(key, lifecycle) {
        // Only the grace delay runs on a background scope so it isn't gated by the UI
        // frame clock, which stops ticking while the app is backgrounded.
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        // graceJob is only ever read/written from the main thread (observer callbacks),
        // so no synchronization is needed. subscribe()/unsubscribe() are idempotent
        // (reference-counted map ops), so re-issuing subscribe() on each ON_START is safe.
        var graceJob: Job? = null

        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> {
                        graceJob?.cancel()
                        graceJob = null
                        subscribe()
                    }

                    Lifecycle.Event.ON_STOP -> {
                        graceJob?.cancel()
                        graceJob =
                            scope.launch {
                                if (UNSUBSCRIBE_GRACE_MILLIS > 0) delay(UNSUBSCRIBE_GRACE_MILLIS)
                                // Back on Main, where ON_START runs: the two can no longer
                                // interleave. If ON_START cancelled this job first, the hop
                                // is skipped; if this runs first, ON_START's subscribe()
                                // follows it and wins. Unsubscribing here off-thread let a
                                // late unsubscribe land after a fresh subscribe and leave a
                                // visible feed without its REQ.
                                withContext(Dispatchers.Main) { unsubscribe() }
                            }
                    }

                    else -> {}
                }
            }

        lifecycle.addObserver(observer)

        onDispose {
            lifecycle.removeObserver(observer)
            scope.cancel()
            // Idempotent: removing an absent key is a cheap no-op. Guarantees the
            // subscription is released even if the grace timer was still pending.
            unsubscribe()
        }
    }
}
