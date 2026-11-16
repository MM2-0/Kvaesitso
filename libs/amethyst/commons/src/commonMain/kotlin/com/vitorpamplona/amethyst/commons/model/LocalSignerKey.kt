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

import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip89AppHandlers.clientTag.NostrSignerWithClientTag

/**
 * A signer that forwards every call to [inner], adding something on the way (the Android app's
 * resource metering). Lets shared code see through a platform decorator to the signer underneath.
 */
interface ForwardingNostrSigner {
    val inner: NostrSigner
}

/**
 * Strips the app's signer decorators to reach the concrete signer. An account's signer is never
 * the bare one: it is wrapped for the NIP-89 client tag and, on Android, for metering. So a check
 * like `signer is NostrSignerInternal` on the account signer is always false, even for an nsec
 * login; unwrap first.
 */
fun NostrSigner.innermostSigner(): NostrSigner =
    when (this) {
        is NostrSignerWithClientTag -> inner.innermostSigner()
        is ForwardingNostrSigner -> inner.innermostSigner()
        else -> this
    }

/**
 * The account's own private key when this login holds it (a local nsec), seen through any
 * decorators. Null for a remote (NIP-46) or external (NIP-55) signer, and for a read-only login.
 */
fun NostrSigner.localPrivKeyOrNull(): ByteArray? = (innermostSigner() as? NostrSignerInternal)?.keyPair?.privKey
