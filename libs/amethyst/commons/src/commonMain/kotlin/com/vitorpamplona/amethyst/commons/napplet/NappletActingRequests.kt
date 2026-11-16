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

/**
 * The napplet / website requests that ACT for the user or use their key — publish, pay, upload, notify,
 * open a file picker or write a file the user picked, and a website's NIP-07 sign / encrypt / decrypt — as opposed to reading.
 * A host holds these while the user isn't looking at the surface (it's parked off-screen, or the app is in
 * the background): pausing the WebView stops animations and media but not JavaScript, so without this an
 * "allow always" napplet or site could keep publishing, paying, signing or decrypting while the user looks
 * elsewhere. Reads (`getPublicKey`, relay queries, …) keep flowing, so a preloaded surface still fills in.
 *
 * NAP-INC (`inc.*`) is not held: it only passes data between the user's own napplets, signs nothing and
 * leaves the app nowhere, and whatever a receiving napplet does with it is held on that napplet's own
 * surface. Holding some `inc.*` messages and not others would also reorder them (an emit held, the
 * channel close after it sent at once), and a napplet in the background could never answer a peer.
 */
object NappletActingRequests {
    private val ACTING =
        setOf(
            "relay.publish",
            "relay.publishEncrypted",
            "outbox.publish",
            // NAP-COMMON's actions publish a follow list, a reaction or a report as the user.
            "common.follow",
            "common.unfollow",
            "common.react",
            "common.report",
            // NAP-INTENT: opens another napplet over whatever the user is doing.
            "intent.invoke",
            "value.payInvoice",
            "upload.upload",
            "notify.create",
            // NAP-FS: a picker pops up over whatever the user is doing, and a write changes their file.
            "fs.pickFile",
            "fs.pickFiles",
            "fs.pickDirectory",
            "fs.pickSaveFile",
            "fs.write",
            // NIP-07 (website posture). Decrypt counts too: it hands the page plaintext it couldn't read.
            "nostr.signEvent",
            "nostr.nip44Encrypt",
            "nostr.nip44Decrypt",
        )

    fun actsForUser(requestType: String?): Boolean = requestType in ACTING

    // Reads whose results are decrypted with the user's key (the encrypted events they return).
    private val DECRYPTING_READS = setOf("relay.query", "outbox.query", "outbox.getEvent")

    /** Whether [requestType] is a read decrypted with the user's key, which waits until someone is looking. */
    fun readsForUser(requestType: String?): Boolean = requestType in DECRYPTING_READS
}
