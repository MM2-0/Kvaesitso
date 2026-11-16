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
package com.vitorpamplona.amethyst.commons.account

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/** One saved login: what the account switcher lists and what a switch hands back to the store. */
@Immutable
@Serializable
data class AccountInfo(
    val npub: String,
    val hasPrivKey: Boolean = false,
    val loggedInWithExternalSigner: Boolean = false,
    val isTransient: Boolean = false,
    /** Signs through a NIP-46 remote signer (a `bunker://` login). */
    val loggedInWithRemoteSigner: Boolean = false,
    /** False when the private key is not kept on this device and is asked for at each launch. */
    val keyRemembered: Boolean = true,
) {
    /** Whether this login can sign: a key on this device, a signer app, or a remote signer. */
    fun canSign() = hasPrivKey || loggedInWithExternalSigner || loggedInWithRemoteSigner
}
