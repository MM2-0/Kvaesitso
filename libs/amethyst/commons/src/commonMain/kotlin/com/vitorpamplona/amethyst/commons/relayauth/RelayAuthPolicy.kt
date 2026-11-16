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
package com.vitorpamplona.amethyst.commons.relayauth

/**
 * The top-level mode for authenticating with relays (NIP-42).
 * Per-relay overrides stored in [RelayAuthPermissionStore] always take precedence.
 */
enum class RelayAuthPolicy {
    /** Authenticate with every relay that requests it. */
    ALWAYS,

    /** Never authenticate; do not reveal your identity to relay operators via NIP-42. */
    NEVER,

    /**
     * Apply the per-situation [RelayAuthCustomToggles]: authenticate only for the categories the
     * user turned on (own relays/venues, reading or messaging follows, messaging strangers).
     * Situations no toggle covers fall through to an explicit prompt ([RelayAuthVerdict.ASK]).
     */
    CUSTOM,
}

/** A persisted per-relay override decision. */
enum class RelayAuthDecision {
    ALLOW,
    DENY,
}
