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
package com.vitorpamplona.amethyst.commons.napplet.protocol

import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerOp

/**
 * Maps a [NappletRequest] to the [NostrSignerOp] it represents, or `null` if the request
 * does not involve signing or encryption.
 *
 * This lives on the napplet side (not in the generic signer-permission layer) because it
 * is the napplet protocol's own translation into the shared [NostrSignerOp] vocabulary.
 */
fun NappletRequest.toSignerOp(): NostrSignerOp? =
    when (this) {
        is NappletRequest.Publish -> NostrSignerOp.SignKind(kind)
        is NappletRequest.OutboxPublish -> NostrSignerOp.SignKind(template.kind)
        is NappletRequest.SignEvent -> NostrSignerOp.SignKind(kind)
        // What leaves is a signed event of [kind], so that is what is gated: as "encrypt" alone, an
        // app allowed to encrypt could publish a follow list, a deletion or a relay list unasked.
        is NappletRequest.PublishEncrypted -> NostrSignerOp.SignKind(kind)
        is NappletRequest.Nip44Encrypt -> NostrSignerOp.Encrypt
        // The broad grant, matching what NIP-46's nip44_decrypt maps to. The narrower
        // DecryptFrom(peer) exists, but only the NIP-46 authorizer honours it today; recording one
        // here would be a grant this broker never reads back, so the user would re-prompt forever.
        is NappletRequest.Nip44Decrypt -> NostrSignerOp.Decrypt
        else -> null
    }

/**
 * The NARROWER op a request may alternatively be granted — today only
 * [NostrSignerOp.DecryptFrom], i.e. "always allow, but only for this counterparty". Mirrors the
 * NIP-46 authorizer's `toNarrowSignerOp`.
 *
 * A single broad "always allow decrypt" hands an app every private conversation the user will ever
 * have; this is the granular alternative the consent dialog offers alongside it. `null` for every
 * request without a counterparty — signing and encryption already name the thing being granted.
 */
fun NappletRequest.toNarrowSignerOp(): NostrSignerOp? =
    when (this) {
        is NappletRequest.Nip44Decrypt -> NostrSignerOp.DecryptFrom(peer)
        else -> null
    }

/**
 * The counterparty whose conversation a decrypt request asks to read, or `null` for every other
 * request. Scoped to decryption to match the NIP-46 authorizer and what the consent dialog
 * documents: it drives "X wants to read your messages with Alice", a categorically different
 * decision from the encrypt/sign case, where the counterparty is already part of what the user
 * is composing.
 */
fun NappletRequest.counterpartyPubKey(): String? =
    when (this) {
        is NappletRequest.Nip44Decrypt -> peer
        else -> null
    }
