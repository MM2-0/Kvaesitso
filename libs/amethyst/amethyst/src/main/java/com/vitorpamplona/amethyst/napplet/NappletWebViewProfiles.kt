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
package com.vitorpamplona.amethyst.napplet

import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import java.security.MessageDigest

/**
 * Mints the opaque per-account WebView storage-profile name the sandbox partitions cookies,
 * localStorage, IndexedDB and service workers by.
 *
 * Every embedded app follows the currently-selected account, and each account gets its OWN storage
 * jar: switching from A to B hands B a clean jar, switching back to A restores A's session intact
 * (this is a partition, not a wipe).
 *
 * The name is a hash rather than the pubkey because the `:napplet` sandbox must never learn which
 * account it is running for — it only gets a stable, meaningless token. Stability is what makes
 * sessions survive a switch, so the derivation must never change once shipped.
 *
 * The applying half lives in `:nappletHost` (`NappletWebViewProfile`), which validates the shape
 * before handing it to `ProfileStore`.
 */
object NappletWebViewProfiles {
    /** Domain separator so this hash can never collide with another use of SHA-256(pubkey). */
    private const val DOMAIN = "amethyst-webview-profile-v1:"

    /** 128 bits of a SHA-256 is far past collision-proof for a handful of on-device accounts. */
    private const val NAME_LENGTH = 32

    /** The profile name for the account embedded apps currently run as, or null when logged out. */
    fun current(): String? = forPubKey(Amethyst.instance.nappletAccountScope())

    /** Stable profile name for [pubKey]; null for a blank scope (no account -> shared default jar). */
    fun forPubKey(pubKey: HexKey): String? {
        if (pubKey.isBlank()) return null

        return MessageDigest
            .getInstance("SHA-256")
            .digest((DOMAIN + pubKey).toByteArray())
            .toHexKey()
            .take(NAME_LENGTH)
    }
}
