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
package com.vitorpamplona.nestsclient.moq.lite

import kotlinx.coroutines.flow.Flow

// Caller-facing handles returned by MoqLiteSession.subscribe / .announce,
// plus the typed protocol-level rejection exception. Kept together
// because they share a single concern: "what the consumer gets back
// from a subscribe / announce request and how it surfaces failure."

/**
 * Active subscription handle returned by [MoqLiteSession.subscribe].
 * [frames] emits every frame the publisher pushes; [unsubscribe]
 * FINs the bidi to signal "no longer interested" (moq-lite has no
 * UNSUBSCRIBE message — FIN is the protocol).
 */
class MoqLiteSubscribeHandle internal constructor(
    val id: Long,
    val ok: MoqLiteSubscribeOk,
    val frames: Flow<MoqLiteFrame>,
    private val unsubscribeAction: suspend () -> Unit,
    private val broadcastEndedCountProbe: () -> Long = { 0L },
) {
    suspend fun unsubscribe() = unsubscribeAction()

    /** How many `Ended` announces the session has seen for this broadcast, read live. */
    val broadcastEndedCount: Long get() = broadcastEndedCountProbe()
}

/**
 * Active announce-discovery handle returned by [MoqLiteSession.announce].
 * [updates] emits every [MoqLiteAnnounce] update the relay streams
 * back; [close] FINs the bidi to stop receiving updates.
 */
class MoqLiteAnnouncesHandle internal constructor(
    val updates: Flow<MoqLiteAnnounce>,
    private val close: suspend () -> Unit,
) {
    suspend fun close() = close.invoke()
}

/**
 * Active probe handle returned by [MoqLiteSession.probe]. Mirrors
 * kixelated's `Subscriber::run_probe_stream`
 * (`rs/moq-lite/src/lite/subscriber.rs`): subscriber opens a bidi,
 * writes `ControlType::Probe`, then reads size-prefixed
 * [MoqLiteProbe] messages indefinitely until the publisher FINs or
 * the consumer calls [close].
 *
 * [updates] is a cold flow that emits every Probe message the
 * publisher pushes — typically a single bitrate hint at session
 * start, but a publisher running an ABR codec MAY emit multiple
 * over time. For a fixed-rate Opus producer (which is what
 * Amethyst's nests speaker is) the publisher emits one and FINs;
 * the flow then completes naturally and consumers see end-of-flow.
 */
class MoqLiteProbeHandle internal constructor(
    val updates: Flow<MoqLiteProbe>,
    private val close: suspend () -> Unit,
) {
    suspend fun close() = close.invoke()
}

/** Thrown when subscribe is rejected (Drop) or the response stream dies. */
class MoqLiteSubscribeException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
