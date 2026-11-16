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
package com.vitorpamplona.amethyst.commons.connectedApps.signers

/**
 * The user's top-level trust decision for one app's access to the internal Nostr signer.
 * Set once on first connection; governs all future signing/encryption operations unless
 * overridden by a per-operation [NostrOpDecision] in the [NostrSignerPermissionLedger].
 */
enum class AppSignerPolicy {
    /** Auto-approve all signing and encryption operations (except payments, which always prompt). */
    FULL_TRUST,

    /**
     * Auto-approve the most common, harmless operations: additive public content events — short
     * notes, reposts, reactions, picture posts, public chat, highlights, comments, and user status
     * (see [NostrSignerPermissionLedger.REASONABLE_SIGN_KINDS]) — plus encryption; ask before
     * anything that could spend money, overwrite account config, delete content, or reveal private
     * data (including decryption). A reasonable default for most apps.
     */
    REASONABLE,

    /** Prompt before every single signing or encryption operation. */
    PARANOID,
}
