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

// Wire-format discriminators for moq-lite control flow.
//
// The four enums below share a common job: they're varint / byte tags
// the wire-format parser (`MoqLiteCodec`) reads at message boundaries to
// decide which body to decode next. None of them carry payload data
// themselves; the data classes that DO live in `MoqLiteMessages.kt`.
//
// Source: `kixelated/moq/rs/moq-lite/src/lite/{stream,announce,subscribe}.rs`.

/**
 * ControlType varint discriminator written as the first datum on every
 * client-initiated bidi stream. Selects which message body the peer
 * should expect to read next.
 *
 * Source: `rs/moq-lite/src/lite/stream.rs:7-15`.
 */
enum class MoqLiteControlType(
    val code: Long,
) {
    /** Lite-01/02 only — unused on Lite-03. Reserved here for completeness. */
    Session(0L),
    Announce(1L),
    Subscribe(2L),
    Fetch(3L),
    Probe(4L),

    /**
     * Graceful relay-shutdown signal. moq-rs's `Publisher::run` accepts
     * `ControlType::Goaway = 5` (`rs/moq-lite/src/lite/publisher.rs`)
     * to migrate a publisher to a different relay node. We don't act
     * on it today — recognising the type code prevents
     * [MoqLiteSession.handleInboundBidi] from silently FINing the
     * bidi as an unknown control type, which would lose the relay's
     * shutdown notification. Wire body decoding is left as a follow-up.
     */
    Goaway(5L),
    ;

    companion object {
        private val byCode = entries.associateBy { it.code }

        fun fromCode(code: Long): MoqLiteControlType? = byCode[code]
    }
}

/**
 * DataType varint written as the first byte of every uni stream that
 * carries payload. Lite-03 currently uses `Group=0` only.
 *
 * Source: `rs/moq-lite/src/lite/stream.rs:32-36`.
 */
enum class MoqLiteDataType(
    val code: Long,
) {
    Group(0L),
    ;

    companion object {
        private val byCode = entries.associateBy { it.code }

        fun fromCode(code: Long): MoqLiteDataType? = byCode[code]
    }
}

/**
 * Status byte at the head of an [MoqLiteAnnounce] payload. `Active=1`
 * is sent on first publish; `Ended=0` on explicit unannounce. Disconnect
 * is NOT signalled with `Ended` — the bidi just closes.
 *
 * Source: `rs/moq-lite/src/lite/announce.rs:84-90`.
 */
enum class MoqLiteAnnounceStatus(
    val code: Int,
) {
    Ended(0),
    Active(1),
    ;

    companion object {
        fun fromCode(code: Int): MoqLiteAnnounceStatus? =
            when (code) {
                0 -> Ended
                1 -> Active
                else -> null
            }
    }
}

/**
 * Type discriminator at the head of a SubscribeResponse on the response
 * side of a Subscribe bidi. Wire layout (Lite-03+):
 *
 *   type   varint  (0 = Ok, 1 = Drop)
 *   body   size-prefixed bytes
 *
 * The type sits OUTSIDE the body size prefix — see
 * `rs/moq-lite/src/lite/subscribe.rs::SubscribeResponse::encode` (the
 * `_` arm covers Lite03+). Earlier drafts wrapped type+body in one
 * outer size prefix; Lite03 split them.
 */
enum class MoqLiteSubscribeResponseType(
    val code: Long,
) {
    Ok(0L),
    Drop(1L),
    ;

    companion object {
        private val byCode = entries.associateBy { it.code }

        fun fromCode(code: Long): MoqLiteSubscribeResponseType? = byCode[code]
    }
}
