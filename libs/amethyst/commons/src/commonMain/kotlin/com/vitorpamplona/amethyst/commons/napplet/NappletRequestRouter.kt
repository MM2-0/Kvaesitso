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

import com.vitorpamplona.amethyst.commons.napplet.fs.NappletFsSession
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletIncRequest
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletIntentRequest
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletProtocolJson
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletResponse
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

/**
 * The host-agnostic orchestration brain: turns one raw applet envelope into an [Outcome] a platform
 * host acts on. It owns the decode → broker → encode flow, the fire-and-forget edge ops
 * (`relay.close`, `resource.cancel`), and the subscribe-vs-reply decision — everything *except* the
 * transport (Messenger / IPC / in-process) and the live relay subscription, which are the host's job.
 *
 * Both the Android `:napplet` service and a future desktop host route through here, so their wire
 * behavior can't drift. Domain availability is injected before scripts; no shell handshake is required.
 */
object NappletRequestRouter {
    sealed interface Outcome {
        /** Nothing to do (e.g. a malformed fire-and-forget message). */
        data object Ignore : Outcome

        /** Send this `.result` payload back, correlated to the request's id. */
        data class Reply(
            val payload: String,
            val response: NappletResponse? = null,
        ) : Outcome

        /**
         * Open a live relay subscription; the host streams `relay.event`/`relay.eose`/`relay.closed` by
         * [subId], or, for an [outbox] one, `outbox.event`/`outbox.closed` on the [relays] the broker chose.
         * Null [relays] means the account's own. [mayDecrypt] is the app's decrypt grant
         * ([NappletBroker.mayDecrypt]): what the host asks before it decrypts an event it streams.
         */
        data class OpenSubscription(
            val subId: String,
            val filters: List<Filter>,
            val relays: Set<NormalizedRelayUrl>? = null,
            val outbox: Boolean = false,
            val mayDecrypt: suspend (peer: HexKey) -> Boolean = { false },
        ) : Outcome

        /** Stop the live subscription [subId] (fire-and-forget; no reply). */
        data class CloseSubscription(
            val subId: String,
        ) : Outcome

        /** Push these envelope(s) to the applet immediately, unkeyed (e.g. an EOSE closing a refused sub). */
        data class Push(
            val payloads: List<String>,
        ) : Outcome

        /**
         * A NAP-INC message for the host's `NappletIncBus`, which routes it to the other napplets and
         * may answer it. Only for a napplet that declared `inc`.
         */
        data class Inc(
            val request: NappletIncRequest,
        ) : Outcome

        /**
         * A NAP-INTENT message for the host's `NappletIntentDispatcher`, which resolves the handler
         * among the user's installed napplets, starts it and delivers to it. Only for a napplet that
         * declared `intent`.
         */
        data class Intent(
            val request: NappletIntentRequest,
        ) : Outcome
    }

    /**
     * The [Outcome] of an `intent.*` [payload], or null when it is not one. Never suspends; the host
     * acts on [Outcome.Intent] where it can launch a napplet. Without the `intent` declaration, the
     * requests that expect an answer are refused and the rest dropped.
     */
    fun routeIntent(
        declared: Set<NappletCapability>,
        payload: String,
        requestType: String? = runCatching { NappletProtocolJson.readType(payload) }.getOrNull(),
    ): Outcome? {
        if (requestType == null || !requestType.startsWith("intent.")) return null
        val request = runCatching { NappletProtocolJson.decodeIntentRequest(payload) }.getOrNull()
        val hasId = runCatching { NappletProtocolJson.readId(payload) }.getOrNull() != null
        if (request == null) {
            return if (hasId && requestType in NappletProtocolJson.INTENT_REQUEST_TYPES) {
                Outcome.Reply(NappletProtocolJson.encodeIntentFailure(requestType, if (requestType == "intent.invoke") "invalid convention" else "malformed request"))
            } else {
                Outcome.Ignore
            }
        }
        if (NappletCapability.INTENT in declared) return Outcome.Intent(request)
        return if (hasId && request !is NappletIntentRequest.Ready) Outcome.Reply(NappletProtocolJson.encodeIntentFailure(requestType, "intent unavailable")) else Outcome.Ignore
    }

    /**
     * The [Outcome] of an `inc.*` [payload], or null when it is not one. Never suspends, so a host
     * calls it on the thread the message arrived on, before anything that could reorder it: two
     * emits, or a channel's messages, must reach the bus in the order the napplet sent them. A host
     * that already read the envelope's type passes it as [requestType] to save a parse.
     *
     * NAP-INC is authorized on the `inc` declaration alone. Without it, the requests that expect an
     * answer get one that says so and the rest are dropped. A known request that is malformed gets an
     * error when it has an id to answer; an unknown `inc.*` type stays silent, as other unknown types do.
     */
    fun routeInc(
        declared: Set<NappletCapability>,
        payload: String,
        requestType: String? = runCatching { NappletProtocolJson.readType(payload) }.getOrNull(),
    ): Outcome? {
        if (requestType == null || !requestType.startsWith("inc.")) return null
        val request = runCatching { NappletProtocolJson.decodeIncRequest(payload) }.getOrNull()
        if (request == null) {
            val id = runCatching { NappletProtocolJson.readId(payload) }.getOrNull()
            return if (id != null && requestType in NappletProtocolJson.INC_REQUEST_TYPES) {
                Outcome.Reply(NappletProtocolJson.encodeIncFailure(requestType, "malformed request"))
            } else {
                Outcome.Ignore
            }
        }
        if (NappletCapability.INC in declared) return Outcome.Inc(request)
        return when (request) {
            is NappletIncRequest.Subscribe -> Outcome.Reply(NappletProtocolJson.encodeIncSubscribeResult(INC_UNAVAILABLE))
            is NappletIncRequest.ChannelOpen -> Outcome.Reply(NappletProtocolJson.encodeIncChannelOpenResult(null, null, INC_UNAVAILABLE))
            is NappletIncRequest.ChannelList -> Outcome.Reply(NappletProtocolJson.encodeIncChannelListResult(emptyList()))
            else -> Outcome.Ignore
        }
    }

    private const val INC_UNAVAILABLE = "inc unavailable"

    /**
     * [files] is the surface's NAP-FS session ([NappletFsSession]), which the host makes per running
     * surface; null where the host offers no files, and every `fs.*` request is then `unsupported`.
     */
    suspend fun route(
        broker: NappletBroker,
        identity: NappletIdentity,
        declared: Set<NappletCapability>,
        payload: String,
        files: NappletFsSession? = null,
    ): Outcome {
        val requestType = runCatching { NappletProtocolJson.readType(payload) }.getOrNull() ?: return Outcome.Ignore
        if (requestType.startsWith("inc.")) return routeInc(declared, payload, requestType) ?: Outcome.Ignore
        if (requestType.startsWith("intent.")) return routeIntent(declared, payload, requestType) ?: Outcome.Ignore

        // Fire-and-forget edge ops that never reach the broker.
        when (requestType) {
            "relay.close", "outbox.close" -> {
                val subId = runCatching { NappletProtocolJson.readSubId(payload) }.getOrNull()
                return if (subId != null) Outcome.CloseSubscription(subId) else Outcome.Ignore
            }
            "resource.cancel" -> return Outcome.Ignore
        }

        val request =
            runCatching { NappletProtocolJson.decodeRequest(payload) }.getOrElse {
                // A subscription that can't open ends at once: its handle waits for outbox.closed.
                if (requestType == "outbox.subscribe") {
                    val subId = runCatching { NappletProtocolJson.readSubId(payload) }.getOrNull() ?: return Outcome.Ignore
                    return Outcome.Push(listOf(NappletProtocolJson.encodeOutboxClosed(subId, "invalid filter")))
                }
                if (requestType.startsWith("fs.") && runCatching { NappletProtocolJson.readId(payload) }.getOrNull() != null) {
                    return Outcome.Reply(NappletProtocolJson.encodeFsMalformed(requestType))
                }
                return if (runCatching { NappletProtocolJson.readId(payload) }.getOrNull() != null) {
                    val failure = if (requestType.startsWith("resource.")) NappletResponse.ResourceFailure("invalid-request", "Malformed resource request.") else NappletResponse.Failed("Malformed request.")
                    Outcome.Reply(NappletProtocolJson.encodeResponse(requestType, failure), failure)
                } else {
                    Outcome.Ignore
                }
            } ?: return Outcome.Ignore

        val response = broker.handle(identity, request, declared, files)

        // A subscription streams pushes keyed by subId instead of sending a .result.
        if (requestType == "relay.subscribe") {
            val subId =
                runCatching { NappletProtocolJson.readSubId(payload) }.getOrNull()
                    ?: return Outcome.Ignore
            return if (response is NappletResponse.Subscribed) {
                Outcome.OpenSubscription(
                    subId,
                    runCatching { NappletProtocolJson.decodeFilterList(payload) }.getOrDefault(emptyList()),
                    mayDecrypt = broker.decryptGate(identity),
                )
            } else {
                // Not authorized → close it immediately with an empty EOSE.
                Outcome.Push(listOf(NappletProtocolJson.encodeRelayEose(subId)))
            }
        }

        if (requestType == "outbox.subscribe") {
            val subId =
                runCatching { NappletProtocolJson.readSubId(payload) }.getOrNull()
                    ?: return Outcome.Ignore
            return if (response is NappletResponse.OutboxSubscribed) {
                Outcome.OpenSubscription(subId, runCatching { NappletProtocolJson.decodeFilterList(payload) }.getOrDefault(emptyList()), response.relays, outbox = true)
            } else {
                val reason = (response as? NappletResponse.Failed)?.reason ?: "policy denied"
                Outcome.Push(listOf(NappletProtocolJson.encodeOutboxClosed(subId, reason)))
            }
        }

        return Outcome.Reply(NappletProtocolJson.encodeResponse(requestType, response), response)
    }
}
