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
package com.vitorpamplona.amethyst.commons.model

/**
 * The outcome of retiring an invite link (CORD-05 §2). Retiring the **last** live link flips the
 * community Private, which is a Refounding (CORD-05 §5, CORD-06 §3); the two `PRIVATIZED` outcomes
 * say whether that Refounding happened.
 */
enum class ConcordRevokeResult {
    /** The link could not be retired (nothing changed on the wire). */
    FAILED,

    /** The link is retired; other live links keep the community Public (or it was already Private). */
    REVOKED,

    /** The last live link is retired and the community was Refounded, so it is Private now. */
    PRIVATIZED,

    /**
     * The last live link is retired, so the community reads Private, but the Refounding did not run:
     * this account cannot Refound (it takes BAN) or the rotation failed. Someone holding BAN must
     * rotate the keys, or whoever already fetched a link keeps the current root.
     */
    PRIVATIZED_REFOUND_PENDING,

    ;

    val revoked: Boolean get() = this != FAILED
}
