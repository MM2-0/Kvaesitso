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
package com.vitorpamplona.amethyst.commons.tor

sealed class TorServiceStatus {
    data class Active(
        val port: Int,
    ) : TorServiceStatus()

    data object Off : TorServiceStatus()

    data object Connecting : TorServiceStatus()

    data class Error(
        val message: String,
    ) : TorServiceStatus()

    /**
     * Where to send bytes, or null. Mirrors the Android status class so callers express intent
     * rather than matching variants. No `Bootstrapping` here on purpose: the desktop backend drives
     * an external Tor, so it never observes the routable-but-not-yet-bootstrapped window that the
     * in-process Arti client has, and a variant nothing emits is dead weight (see [Error], which is
     * only ever constructed by `DesktopTorManager`).
     */
    val socksPort: Int?
        get() = (this as? Active)?.port

    /** Tor can build circuits right now. */
    val isFullyBootstrapped: Boolean
        get() = this is Active
}
