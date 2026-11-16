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
package com.vitorpamplona.amethyst.commons.napplet.permissions

/**
 * A user's standing decision for one (napplet, capability) pair.
 *
 * Persistence differs by scope, which is what makes these distinct rather than a
 * single boolean:
 * - [DENY] / [ALLOW_ALWAYS] are **persistent** — written to the [NappletPermissionStore].
 * - [ALLOW_SESSION] lives **in memory** for the lifetime of one ledger instance.
 * - [ALLOW_ONCE] is **transient** — it authorizes exactly the in-flight request and is
 *   never recorded, so the next request prompts again.
 * - [ASK] is the absence of a standing decision: prompt the user.
 */
enum class GrantState {
    ASK,
    ALLOW_ONCE,
    ALLOW_SESSION,
    ALLOW_ALWAYS,
    DENY,
    ;

    /** Whether this state authorizes the current request to proceed. */
    val allowsExecution: Boolean
        get() = this == ALLOW_ONCE || this == ALLOW_SESSION || this == ALLOW_ALWAYS
}

/** The broker's verdict for a request after consulting standing grants. */
enum class PermissionDecision {
    /** A standing grant authorizes execution; run without prompting. */
    ALLOW,

    /** A standing denial blocks execution; reject without prompting. */
    DENY,

    /** No standing decision exists; the user must be prompted. */
    ASK,
}
