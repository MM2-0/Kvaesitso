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
package com.vitorpamplona.amethyst.commons.napplet

import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * The key a napplet, nSite or website's signer decisions (trust level, remembered per-op answers)
 * are stored under: `napplet:<account pubkey>:<app coordinate>`. Per account on purpose, so one
 * account's "always allow" never signs for another account on the same device.
 *
 * Everything that reads or writes those decisions must build the key here. The Connected Apps
 * screens used the bare app coordinate, so they never found the broker's decisions: Forget left
 * them in place (an app forgotten went on signing logins without a prompt), changing the trust
 * level did nothing, and the list showed each stored key as a separate app titled with an
 * account's pubkey.
 */
object NappletSignerKey {
    const val PREFIX = "napplet"

    fun of(
        accountPubKey: HexKey,
        appCoordinate: String,
    ): String = "$PREFIX:$accountPubKey:$appCoordinate"

    /** The app coordinate behind [key] when it belongs to [accountPubKey]; null for another account's key. */
    fun appCoordinateOf(
        key: String,
        accountPubKey: HexKey,
    ): String? {
        val prefix = "$PREFIX:$accountPubKey:"
        return if (key.startsWith(prefix)) key.substring(prefix.length) else null
    }

    fun isSignerKey(key: String): Boolean = key.startsWith("$PREFIX:")
}
