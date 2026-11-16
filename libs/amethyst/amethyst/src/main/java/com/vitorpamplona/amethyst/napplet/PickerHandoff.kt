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
package com.vitorpamplona.amethyst.napplet

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Hands a pick from the main process's Services to an invisible picker host ([PickerHostActivity]):
 * the request waits here under a one-time token, the host is started with nothing but that token,
 * reads what to show ([pendingFor]) and reports back ([complete]). Every request completes exactly
 * once, null meaning nothing was chosen.
 *
 * Shared by the page file input of the embedded surfaces ([WebFileChooserCoordinator]) and NAP-FS
 * ([NappletFsPickerCoordinator]), so both settle the same way on every exit of their host.
 */
class PickerHandoff<A, R : Any>(
    private val host: Class<out Activity>,
    private val tag: String,
) {
    private class Pending<A, R>(
        val ask: A,
        val onResult: (R?) -> Unit,
    ) {
        // Completed when the host picks the request up: a start the system dropped never does.
        val started = CompletableDeferred<Unit>()
    }

    private val pending = ConcurrentHashMap<String, Pending<A, R>>()

    /**
     * Starts the host for [ask]. [onResult] always runs once, including when no host could be
     * started at all. Returns the request's token.
     */
    fun start(
        context: Context,
        ask: A,
        onResult: (R?) -> Unit,
    ): String {
        val token = UUID.randomUUID().toString()
        pending[token] = Pending(ask, onResult)
        val launch =
            Intent(context, host)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_TOKEN, token)
        runCatching { context.startActivity(launch) }
            .onFailure { e ->
                Log.w(tag, "Could not start the picker host", e)
                complete(token, null)
            }
        return token
    }

    /**
     * [start], suspended until the result. Android can drop an activity start without an error (a
     * start it counts as from the background), so a host that has not picked the request up within
     * [startTimeoutMs] ends it as nothing chosen instead of leaving the caller waiting for good. Once
     * the host is up it reports on every exit, so the wait after that is the user's. Cancelling the
     * caller ends the request.
     */
    suspend fun await(
        context: Context,
        ask: A,
        startTimeoutMs: Long,
    ): R? {
        val result = CompletableDeferred<R?>()
        val token = start(context, ask) { result.complete(it) }
        try {
            val started = pending[token]?.started
            if (started != null && withTimeoutOrNull(startTimeoutMs) { started.await() } == null) {
                Log.w(tag, "The picker host never started")
                complete(token, null)
            }
            return result.await()
        } finally {
            complete(token, null)
        }
    }

    /** Called by the host to learn what to show, or null when nothing waits under [token] any more. */
    fun pendingFor(token: String): A? = pending[token]?.also { it.started.complete(Unit) }?.ask

    /** The outcome of [token]'s request; null = nothing chosen. Resolves at most once. */
    fun complete(
        token: String,
        result: R?,
    ) {
        pending.remove(token)?.onResult?.invoke(result)
    }

    companion object {
        const val EXTRA_TOKEN = "picker_handoff_token"
    }
}

/**
 * An invisible main-process host for one [PickerHandoff] request. It exists only long enough to run
 * the pick ([launch]) and report it, and it reports on every exit (a choice, a cancel, a system
 * teardown), because the caller waits on it.
 *
 * A recreated instance has lost the in-flight request its result would be matched against
 * (`configChanges` keeps this rare: a system kill, not a rotation), and relaunching would stack a
 * second picker on the first, so it reports nothing chosen instead.
 */
abstract class PickerHostActivity<A, R : Any>(
    private val handoff: PickerHandoff<A, R>,
) : ComponentActivity() {
    private var token: String? = null
    private var reported = false

    /** Shows the picker for [ask]; the result comes back through [report]. */
    protected abstract fun launch(ask: A)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val token = intent.getStringExtra(PickerHandoff.EXTRA_TOKEN)
        this.token = token
        val ask = token?.let { handoff.pendingFor(it) }
        // Nothing waits under this token (the caller went away, or the process restarted).
        if (ask == null || savedInstanceState != null) {
            finish()
            return
        }
        launch(ask)
    }

    protected fun report(result: R?) {
        if (reported) return
        reported = true
        token?.let { handoff.complete(it, result) }
    }

    /** Fail-open toward the caller: any unreported teardown still settles its request. */
    override fun finish() {
        report(null)
        super.finish()
    }

    /** The system can destroy this host without [finish] (a low-memory kill under the picker). */
    override fun onDestroy() {
        report(null)
        super.onDestroy()
    }
}
