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
package com.vitorpamplona.amethyst.commons.relayClient.diagnostics

import com.vitorpamplona.quartz.nip01Core.kotlinSerialization.KotlinSerializationMapper
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.MessageDecoder
import com.vitorpamplona.quartz.utils.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

actual fun kiteDiagnosticParser(): MessageDecoder = KiteDiagnosticDecoder()

/**
 * TEMPORARY: parses every relay frame with Quartz's default decoder (Kite) and, when it throws,
 * parses the same frame again with Quartz's kotlinx.serialization decoder, an independent and
 * lenient parser, to tell a Kite bug from a bad frame. (Jackson was the second opinion until
 * Quartz 2.4.0 dropped it.) Filter the log with `adb logcat -s KiteDiag`.
 *
 * - **kotlinx parses it:** a Kite bug. Logged as a warning with Kite's exception and the
 *   whole frame (split into numbered lines, since logcat cuts a line at about 4 KB), and
 *   kotlinx's message is returned so the app still gets the event.
 * - **kotlinx rejects it too:** a bad frame, not a Kite bug. Logged as info with both
 *   errors and the frame's start, then the original exception goes up as before.
 *
 * Each distinct frame is logged once; relays resend the same frame, and a burst of
 * duplicates would bury the rest.
 */
class KiteDiagnosticDecoder(
    private val delegate: MessageDecoder = MessageDecoder.Default,
) : MessageDecoder {
    private val seen = ConcurrentHashMap.newKeySet<Int>()
    private val kiteBugs = AtomicLong()
    private val badFrames = AtomicLong()

    override fun decode(text: String): Message =
        try {
            delegate.decode(text)
        } catch (kiteError: Exception) {
            val second =
                try {
                    KotlinSerializationMapper.fromJsonToMessage(text)
                } catch (secondError: Exception) {
                    reportBadFrame(text, kiteError, secondError)
                    throw kiteError
                }
            reportKiteBug(text, kiteError)
            second
        }

    private fun firstTime(text: String): Boolean {
        if (seen.size >= MAX_SEEN) seen.clear()
        return seen.add(text.hashCode())
    }

    private fun reportKiteBug(
        text: String,
        error: Exception,
    ) {
        val n = kiteBugs.incrementAndGet()
        if (!firstTime(text)) return
        Log.w(TAG, "Kite bug #$n: Kite rejected a frame kotlinx parses (${text.length} chars): $error", error)
        val chunks = (text.length + CHUNK - 1) / CHUNK
        val shown = minOf(chunks, MAX_CHUNKS)
        for (i in 0 until shown) {
            val part = text.substring(i * CHUNK, minOf(text.length, (i + 1) * CHUNK))
            Log.w(TAG, "Kite bug #$n frame ${i + 1}/$chunks: $part")
        }
        if (shown < chunks) Log.w(TAG, "Kite bug #$n frame truncated after $shown of $chunks parts")
    }

    private fun reportBadFrame(
        text: String,
        kiteError: Exception,
        secondError: Exception,
    ) {
        val n = badFrames.incrementAndGet()
        if (!firstTime(text)) return
        Log.i(TAG) {
            "Bad frame #$n, rejected by Kite and kotlinx (${text.length} chars). Kite: $kiteError. " +
                "kotlinx: $secondError. Frame starts: ${text.take(BAD_FRAME_PREVIEW)}"
        }
    }

    companion object {
        const val TAG = "KiteDiag"
        private const val CHUNK = 3000
        private const val MAX_CHUNKS = 40
        private const val BAD_FRAME_PREVIEW = 300
        private const val MAX_SEEN = 10_000
    }
}
