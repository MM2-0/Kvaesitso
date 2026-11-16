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
package com.vitorpamplona.amethyst.commons.ui.note.share

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.ui.note.externalLinkForNote

/** Which of the two payloads the QR code currently encodes. */
enum class QrPayloadMode {
    /**
     * An `https://njump.to/…` link. The default, because a stock phone camera will offer to
     * open an http(s) URL but may treat a bare custom scheme as inert text.
     */
    Web,

    /**
     * A `nostr:nevent1…` / `nostr:naddr1…` URI. Resolves without a web round-trip and is what
     * in-app scanners expect.
     */
    Nostr,
}

/**
 * The string encoded into the QR code for [note] in [mode].
 *
 * Both payloads carry the note's relay hint: [externalLinkForNote] builds its URL from
 * `toNAddr()` / `toNEvent()`, which already call `relayHintUrl()`.
 */
fun qrPayloadFor(
    note: Note,
    mode: QrPayloadMode,
): String =
    when (mode) {
        QrPayloadMode.Web -> externalLinkForNote(note)
        QrPayloadMode.Nostr -> note.toNostrUri()
    }
