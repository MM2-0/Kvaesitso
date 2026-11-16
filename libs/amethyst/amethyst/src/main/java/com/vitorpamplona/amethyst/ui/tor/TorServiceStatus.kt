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
package com.vitorpamplona.amethyst.ui.tor

/**
 * Two independent facts about Tor that used to coincide and no longer do.
 *
 * The SOCKS proxy binds within ~130ms of process start, but the directory download it needs before
 * it can build a circuit takes 12-34s on a cold install. While `create_bootstrapped` blocked until
 * both were true, one "Active" could honestly mean both. Under `BootstrapBehavior::OnDemand` the
 * proxy is usable immediately and streams wait for their own circuits, so the two facts diverge by
 * that whole window — and callers want different ones. Read [socksPort] to route bytes and
 * [isFullyBootstrapped] to tell a user (or a watchdog) whether Tor is actually working; matching on
 * the variants directly is how you end up with the wrong one, because both look like "Active".
 */
sealed class TorServiceStatus {
    /** Proxy bound and the directory is ready: circuits build immediately. */
    data class Active(
        val port: Int,
    ) : TorServiceStatus()

    /**
     * Proxy bound and routable, directory still downloading. Dials sent here are not lost — each
     * stream waits for its own circuit — but they will not complete until the download lands.
     */
    data class Bootstrapping(
        val port: Int,
    ) : TorServiceStatus()

    object Off : TorServiceStatus()

    /** No proxy yet: the native client is still being created. Nothing is routable. */
    object Connecting : TorServiceStatus()

    /**
     * Where to send bytes, or null if there is nowhere to send them.
     *
     * [Bootstrapping] counts. Routing to it queues the dial behind the directory download, which is
     * what we want; treating it as "no proxy" is what made every dial fall back to the Orbot
     * default port 9050, where nothing listens, and fail instantly into backoff.
     */
    val socksPort: Int?
        get() =
            when (this) {
                is Active -> port
                is Bootstrapping -> port
                else -> null
            }

    /** Tor can build circuits right now. What a user is told, and what the watchdogs judge. */
    val isFullyBootstrapped: Boolean
        get() = this is Active
}
