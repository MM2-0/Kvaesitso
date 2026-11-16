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
package com.vitorpamplona.amethyst.commons.devtools

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.util.logSignerErrors
import com.vitorpamplona.crypto.random.RandomInstance
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.AuthCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CloseCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CountCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.EventCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocket
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip42RelayAuth.RelayAuthEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi

enum class FrameDirection {
    SENT,
    RECEIVED,
    SYSTEM,
}

/** A connection event the console reports in its own words, not the relay's. */
enum class ConsoleSystemEvent {
    CONNECTING,
    CONNECTED,
    CLOSED,
    FAILED,
    INVALID_URL,
    SEND_FAILED,
    WAITING_FOR_TOR,
}

@Immutable
data class ConsoleFrame(
    val id: Long,
    val direction: FrameDirection,
    val text: String,
    val atMillis: Long,
    /** First element of the JSON array (`EVENT`, `EOSE`, `OK`...), when there is one. */
    val label: String? = null,
    val system: ConsoleSystemEvent? = null,
)

enum class ConsoleConnection {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
}

/** The live socket, for the connection bar: which relay, how fast it opened, whether it compresses. */
@Immutable
data class ConsoleLink(
    val relay: NormalizedRelayUrl,
    val pingMillis: Int,
    val compression: Boolean,
)

/**
 * A manual WebSocket console for one relay, in the spirit of websocketking.com: open a socket,
 * type any frame, see every frame both ways, unparsed. It deliberately bypasses the app's
 * relay pool — no subscription bookkeeping, no automatic AUTH, no reconnects — so what a
 * developer sees is exactly what the relay sent.
 *
 * Sockets come from the app's [WebsocketBuilder], so a relay routed over Tor in the app is
 * routed over Tor here too.
 */
class RelayConsoleViewModel(
    private val websocketBuilder: WebsocketBuilder,
    private val signer: NostrSigner?,
) : ViewModel() {
    private val _frames = MutableStateFlow<PersistentList<ConsoleFrame>>(persistentListOf())
    val frames: StateFlow<ImmutableList<ConsoleFrame>> = _frames

    private val _connection = MutableStateFlow(ConsoleConnection.DISCONNECTED)
    val connection: StateFlow<ConsoleConnection> = _connection

    private val _link = MutableStateFlow<ConsoleLink?>(null)
    val link: StateFlow<ConsoleLink?> = _link

    /** REQ subscriptions sent and not yet closed by either side, newest last: what CLOSE offers. */
    private val _openSubs = MutableStateFlow<ImmutableList<String>>(persistentListOf())
    val openSubs: StateFlow<ImmutableList<String>> = _openSubs

    /** The last NIP-42 challenge the relay sent; the screen asks before answering it. */
    private val _pendingChallenge = MutableStateFlow<String?>(null)
    val pendingChallenge: StateFlow<String?> = _pendingChallenge

    // Read from the signing coroutine (AUTH) as well as the main thread.
    @Volatile private var socket: WebSocket? = null
    private var relay: NormalizedRelayUrl? = null

    // Frames arrive on OkHttp's delivery thread while sends append from the main thread, and the
    // id keys the log's LazyColumn: a duplicate would crash it.
    @OptIn(ExperimentalAtomicApi::class)
    private val nextId = AtomicLong(0L)

    val canSign: Boolean get() = signer?.isWriteable() == true

    fun connect(url: String) {
        disconnect()
        val normalized = RelayUrlNormalizer.normalizeOrNull(url.trim())
        if (normalized == null) {
            system(ConsoleSystemEvent.INVALID_URL, url)
            return
        }
        if (!websocketBuilder.canConnect(normalized)) {
            system(ConsoleSystemEvent.WAITING_FOR_TOR, normalized.url)
            return
        }
        relay = normalized
        _connection.value = ConsoleConnection.CONNECTING
        system(ConsoleSystemEvent.CONNECTING, normalized.url)

        val listener =
            object : WebSocketListener {
                override fun onOpen(
                    pingMillis: Int,
                    compression: Boolean,
                ) {
                    _connection.value = ConsoleConnection.CONNECTED
                    _link.value = ConsoleLink(normalized, pingMillis, compression)
                    system(ConsoleSystemEvent.CONNECTED, "${pingMillis}ms" + if (compression) ", deflate" else "")
                }

                override suspend fun onMessage(text: String) {
                    val label = labelOf(text)
                    if (label == "AUTH") _pendingChallenge.value = challengeOf(text)
                    if (label == "CLOSED") subIdOf(text)?.let(::forgetSub)
                    append(FrameDirection.RECEIVED, text, label)
                }

                override fun onClosed(
                    code: Int,
                    reason: String,
                ) {
                    _connection.value = ConsoleConnection.DISCONNECTED
                    _link.value = null
                    system(ConsoleSystemEvent.CLOSED, "$code $reason".trim())
                }

                override fun onFailure(
                    t: Throwable,
                    code: Int?,
                    response: String?,
                ) {
                    _connection.value = ConsoleConnection.DISCONNECTED
                    _link.value = null
                    system(ConsoleSystemEvent.FAILED, listOfNotNull(code?.toString(), t.message, response).joinToString(" "))
                }
            }

        // Built and dialed right here: connect() only enqueues the dial. Doing it on a coroutine let
        // a disconnect() land before the socket existed, and the dial then opened a session no one
        // could close.
        socket = websocketBuilder.build(normalized, listener).also { it.connect() }
    }

    fun disconnect() {
        socket?.disconnect()
        socket = null
        _pendingChallenge.value = null
        _openSubs.value = persistentListOf()
        _link.value = null
        _connection.value = ConsoleConnection.DISCONNECTED
    }

    fun send(text: String): Boolean {
        val payload = text.trim()
        if (payload.isEmpty()) return false
        val sent = socket?.send(payload) == true
        if (sent) {
            val label = labelOf(payload)
            // Only a REQ stays open; a COUNT ends with its answer, so there is nothing to CLOSE.
            when (label) {
                "REQ" -> subIdOf(payload)?.let { sub -> _openSubs.update { (it - sub + sub).toImmutableList() } }
                "CLOSE" -> subIdOf(payload)?.let(::forgetSub)
            }
            append(FrameDirection.SENT, payload, label)
        } else {
            system(ConsoleSystemEvent.SEND_FAILED, payload.take(80))
        }
        return sent
    }

    /** A REQ or COUNT frame for [filter], built by the same serializer [send] puts on the wire. */
    fun frameFor(
        verb: String,
        subId: String,
        filter: Filter,
    ) = if (verb == "COUNT") CountCmd(subId, listOf(filter)).toJson() else ReqCmd(subId, listOf(filter)).toJson()

    fun close(subId: String) = send(CloseCmd(subId).toJson())

    /** Publishes an already-signed event: a bare event JSON is wrapped in `["EVENT", …]`. */
    fun sendEvent(event: Event) = send(EventCmd(event).toJson())

    fun dismissAuth() {
        _pendingChallenge.value = null
    }

    private fun forgetSub(subId: String) = _openSubs.update { (it - subId).toImmutableList() }

    /** Signs a kind 22242 for the pending challenge with the account's key and sends it. */
    fun answerAuth() {
        val challenge = _pendingChallenge.value ?: return
        val relay = relay ?: return
        val signer = signer?.takeIf { it.isWriteable() } ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val event = logSignerErrors("RelayConsole") { RelayAuthEvent.create(relay, challenge, signer) } ?: return@launch
            if (send(AuthCmd(event).toJson())) _pendingChallenge.value = null
        }
    }

    fun clear() {
        _frames.value = persistentListOf()
    }

    override fun onCleared() {
        disconnect()
        super.onCleared()
    }

    private fun system(
        event: ConsoleSystemEvent,
        detail: String,
    ) = append(FrameDirection.SYSTEM, detail, null, event)

    private fun append(
        direction: FrameDirection,
        text: String,
        label: String?,
        system: ConsoleSystemEvent? = null,
    ) {
        @OptIn(ExperimentalAtomicApi::class)
        val frame = ConsoleFrame(nextId.fetchAndAdd(1), direction, text, TimeUtils.nowMillis(), label, system)
        _frames.update { current ->
            val next = current.adding(frame)
            if (next.size > MAX_FRAMES) next.removingAt(0) else next
        }
    }

    companion object {
        const val MAX_FRAMES = 500

        private val LABEL = Regex("^\\s*\\[\\s*\"([A-Za-z_-]+)\"")
        private val SUB_ID = Regex("^\\s*\\[\\s*\"[A-Z]+\"\\s*,\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
        private val AUTH_CHALLENGE = Regex("^\\s*\\[\\s*\"AUTH\"\\s*,\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")

        fun labelOf(frame: String): String? = LABEL.find(frame)?.groupValues?.get(1)

        fun challengeOf(frame: String): String? = AUTH_CHALLENGE.find(frame)?.groupValues?.get(1)

        /** The second element of a REQ, COUNT, CLOSE or CLOSED frame. */
        fun subIdOf(frame: String): String? = SUB_ID.find(frame)?.groupValues?.get(1)

        fun newSubId() = "console-" + RandomInstance.int(100000)
    }
}
