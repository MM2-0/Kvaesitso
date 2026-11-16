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
 * A Nostr-specific cryptographic operation that requires the internal signer.
 * Used to gate signing and encryption independently, per app.
 */
sealed interface NostrSignerOp {
    /** Sign (and optionally publish) an event of the given [kind]. */
    data class SignKind(
        val kind: Int,
    ) : NostrSignerOp

    /** Encrypt a message (NIP-04 or NIP-44). */
    data object Encrypt : NostrSignerOp

    /** Decrypt a message (NIP-04 or NIP-44) from ANYONE — the broad grant. */
    data object Decrypt : NostrSignerOp

    /**
     * Decrypt messages from one [counterparty] only — the narrow alternative to [Decrypt].
     *
     * A single "always allow decrypt" grant lets an app read every private conversation the user has,
     * forever. Replacing [Decrypt] with this would be worse, though: a DM client would prompt once per
     * conversation, training users to approve everything. So this exists *alongside* [Decrypt]: the
     * consent dialog offers both, and the authorizer honours a narrow grant when the broad one is
     * still ASK. Never auto-granted — only a deliberate "always allow for X" writes one.
     */
    data class DecryptFrom(
        val counterparty: String,
    ) : NostrSignerOp

    /** Stable storage key for this operation, used as a DataStore key fragment. */
    val key: String
        get() =
            when (this) {
                is SignKind -> "sign:$kind"
                Encrypt -> "encrypt"
                Decrypt -> "decrypt"
                is DecryptFrom -> "decrypt:$counterparty"
            }

    companion object {
        fun fromKey(key: String): NostrSignerOp? =
            when {
                key == "encrypt" -> Encrypt
                key == "decrypt" -> Decrypt
                // Additive: the broad grant keeps its bare "decrypt" key, so no stored key migrates.
                key.startsWith("decrypt:") -> key.removePrefix("decrypt:").ifBlank { null }?.let { DecryptFrom(it) }
                key.startsWith("sign:") -> key.removePrefix("sign:").toIntOrNull()?.let { SignKind(it) }
                else -> null
            }
    }
}
