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

import com.vitorpamplona.amethyst.commons.napplet.NappletCapability
import com.vitorpamplona.amethyst.commons.napplet.NappletIdentity
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletRequest

// ---------------------------------------------------------------------------
// First-connect dialog
// ---------------------------------------------------------------------------

/** The user's response to the "Connect to Nostr" first-connection dialog. */
sealed interface AppConnectResult {
    /** The user accepted and chose a trust level. */
    data class Connected(
        val policy: AppSignerPolicy,
    ) : AppConnectResult

    /** The user chose to block this app permanently. */
    data object Blocked : AppConnectResult

    /** The user dismissed the dialog without making a choice. */
    data object Cancelled : AppConnectResult
}

/**
 * Shows the "Connect to Nostr" first-connection dialog for [identity] and suspends
 * until the user makes a choice. The result drives the [AppSignerPolicy] stored in
 * [NostrSignerPermissionLedger] and the bulk capability grant in [NappletBroker][com.vitorpamplona.amethyst.commons.napplet.NappletBroker].
 */
fun interface NostrConnectPrompt {
    /**
     * [declared] is the capability set the connection would pre-grant. It has to reach the dialog:
     * accepting bulk-grants every one of them as ALLOW_ALWAYS, so a dialog that showed only the
     * app's title and icon would be asking for consent to something it never disclosed.
     */
    suspend fun request(
        identity: NappletIdentity,
        declared: Set<NappletCapability>,
    ): AppConnectResult
}

// ---------------------------------------------------------------------------
// Per-operation consent dialog
// ---------------------------------------------------------------------------

/**
 * The user's response to a per-signing-operation consent dialog.
 * The broker records any "remember" variant before returning [isAllowed].
 */
sealed interface SignerOpGrant {
    /** Whether the in-flight request may proceed. */
    val isAllowed: Boolean

    /** Allow this one request; prompt again next time. */
    data object AllowOnce : SignerOpGrant {
        override val isAllowed = true
    }

    /** Allow and remember: don't ask again for [op]. */
    data class AllowForOp(
        val op: NostrSignerOp,
    ) : SignerOpGrant {
        override val isAllowed = true
    }

    /** Allow for the current broker session only — not persisted across app restarts. */
    data class AllowForSession(
        val op: NostrSignerOp,
    ) : SignerOpGrant {
        override val isAllowed = true
    }

    /** Allow and remember until [expiresAt] (Unix epoch seconds). */
    data class AllowUntil(
        val op: NostrSignerOp,
        val expiresAt: Long,
    ) : SignerOpGrant {
        override val isAllowed = true
    }

    /** Allow and upgrade to [AppSignerPolicy.FULL_TRUST] for all future requests. */
    data object AllowAll : SignerOpGrant {
        override val isAllowed = true
    }

    /** Deny this one request; prompt again next time. */
    data object DenyOnce : SignerOpGrant {
        override val isAllowed = false
    }

    /** Deny and remember: always deny [op]. */
    data class DenyForOp(
        val op: NostrSignerOp,
    ) : SignerOpGrant {
        override val isAllowed = false
    }
}

/**
 * Prompts the user to authorize (or deny) a specific Nostr operation for [identity].
 * Suspends until the user answers. [DenyOnce] is the safe default when dismissed.
 */
fun interface NostrSignerConsentPrompt {
    suspend fun request(
        identity: NappletIdentity,
        op: NostrSignerOp,
        request: NappletRequest,
    ): SignerOpGrant
}
