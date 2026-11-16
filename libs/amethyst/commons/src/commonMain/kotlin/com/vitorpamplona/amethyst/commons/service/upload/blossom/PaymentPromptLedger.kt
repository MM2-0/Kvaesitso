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
package com.vitorpamplona.amethyst.commons.service.upload.blossom

import com.vitorpamplona.amethyst.commons.util.ConcurrentSet

/**
 * Bounds how often one user action may raise a BUD-07 payment prompt.
 *
 * The mirror flow retries a target after paying it, and that retry catches every
 * exception — including a second `BlossomPaymentException`. Left unbounded, a
 * server that pockets the preimage and answers 402 again drives an indefinite
 * pay-prompt cycle: pay → 402 → prompt → pay → 402 → … Each cycle is a real
 * payment, so "the user can always tap cancel" is not an adequate answer.
 *
 * Rule: a given (blob, server) pair may prompt at most once per user-initiated
 * action. [beginUserAction] resets the ledger; everything downstream of that tap
 * — including the post-payment retry — goes through [shouldPrompt].
 */
class PaymentPromptLedger {
    private val prompted = ConcurrentSet<String>()

    /** The user tapped mirror/sync: a fresh budget of one prompt per target. */
    fun beginUserAction() {
        prompted.clear()
    }

    /**
     * True the first time this (blob, server) pair asks for payment in the current
     * user action; false on every subsequent 402 from the same pair.
     */
    fun shouldPrompt(
        hash: String,
        server: String,
    ): Boolean = prompted.add("$hash|$server")
}
