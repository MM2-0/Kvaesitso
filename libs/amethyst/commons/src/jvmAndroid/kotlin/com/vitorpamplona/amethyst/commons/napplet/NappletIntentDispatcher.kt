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

import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletIntentRequest
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletProtocolJson
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * NAP-INTENT, the runtime side: resolves an invocation to a handler in the user's [catalog], starts
 * the handler with [launch] and delivers the convention to it once its binding says it is ready.
 * One per host (the Android broker service, the desktop runtime), shared by every running surface
 * [K], which is also what [launch] hands back for the started target.
 *
 * Resolution follows NAP-INTENT's precedence: an explicit `handler` (a catalog id; anything not
 * installed is refused, never swapped for another), then the user's default (the first handler in
 * their favorites order), which is always there when any handler is; a recommendation is never
 * needed past that, and is ignored as the spec allows. `choose` needs a chooser Amethyst doesn't have
 * yet, so it resolves only when there is a single candidate.
 *
 * An accepted invocation is the runtime's to deliver: it is kept for its target, independent of the
 * source, until the target's binding is ready ([NappletIntentRequest.Ready]) or [retainSeconds] pass.
 */
class NappletIntentDispatcher<K : Any>(
    private val catalog: () -> NappletIntentCatalog,
    private val launch: suspend (NappletIntentHandler) -> K?,
    private val retainSeconds: Long = RETAIN_SECONDS,
    private val now: () -> Long = { TimeUtils.now() },
) {
    private class Pending(
        val envelope: String,
        val at: Long,
    )

    private val lock = Any()
    private val pending = HashMap<K, MutableList<Pending>>()
    private val ready = HashMap<K, (String) -> Unit>()

    /**
     * Acts on [request] from the surface [key], running as [source], whose pushes go to [deliver],
     * and returns the reply to send back with the request's id, or null when there is none.
     */
    suspend fun handle(
        key: K,
        source: NappletIdentity,
        deliver: (String) -> Unit,
        request: NappletIntentRequest,
    ): String? =
        when (request) {
            is NappletIntentRequest.Ready -> {
                val due =
                    synchronized(lock) {
                        ready[key] = deliver
                        pending.remove(key).orEmpty().filter { fresh(it) }
                    }
                due.forEach { deliver(it.envelope) }
                null
            }
            is NappletIntentRequest.Available -> NappletProtocolJson.encodeIntentAvailableResult(catalog().availability(request.archetype))
            is NappletIntentRequest.Handlers -> NappletProtocolJson.encodeIntentHandlersResult(catalog().allAvailability())
            is NappletIntentRequest.Invoke -> invoke(source, request)
        }

    private suspend fun invoke(
        source: NappletIdentity,
        request: NappletIntentRequest.Invoke,
    ): String {
        // The convention is authoritative: its archetype and action must be the request's.
        if (!NappletIntentCatalog.isConvention(request.convention) || request.convention != "napplet:${request.archetype}/${request.action}") {
            return NappletProtocolJson.encodeIntentInvokeResult(request, null, "invalid convention")
        }
        val candidates = catalog().candidatesFor(request.convention)
        if (candidates.isEmpty()) return NappletProtocolJson.encodeIntentInvokeResult(request, null, "no handler")
        val handler =
            when (val asked = request.handler) {
                null, DEFAULT -> candidates.first()
                CHOOSE -> candidates.singleOrNull() ?: return NappletProtocolJson.encodeIntentInvokeResult(request, null, "user cancelled")
                // Only a napplet the user installed may be named, and it must take this convention.
                else -> candidates.firstOrNull { it.id == asked } ?: return NappletProtocolJson.encodeIntentInvokeResult(request, null, "no handler")
            }
        val target = launch(handler) ?: return NappletProtocolJson.encodeIntentInvokeResult(request, null, "invoke rejected")
        val envelope = NappletProtocolJson.encodeIntentDeliver(NappletIntentCatalog.catalogId(source), request.archetype, request.action, request.convention, request.payload)
        // A target that is already up (its binding said so) gets it now; a new one when it is ready.
        val now =
            synchronized(lock) {
                val live = ready[target]
                if (live == null) pending.getOrPut(target) { mutableListOf() }.add(Pending(envelope, now()))
                live
            }
        now?.invoke(envelope)
        return NappletProtocolJson.encodeIntentInvokeResult(request, handler.id)
    }

    /** The surface [key] went away: nothing more is delivered to it. */
    fun remove(key: K) {
        synchronized(lock) {
            ready.remove(key)
            pending.remove(key)
        }
    }

    /** [remove] for every surface [matches] picks. */
    fun removeAll(matches: (K) -> Boolean) {
        synchronized(lock) {
            ready.keys.removeAll(matches)
            pending.keys.removeAll(matches)
        }
    }

    private fun fresh(delivery: Pending) = now() - delivery.at <= retainSeconds

    companion object {
        /** How long an accepted delivery waits for its target to be ready. */
        const val RETAIN_SECONDS = 120L

        private const val DEFAULT = "default"
        private const val CHOOSE = "choose"
    }
}
