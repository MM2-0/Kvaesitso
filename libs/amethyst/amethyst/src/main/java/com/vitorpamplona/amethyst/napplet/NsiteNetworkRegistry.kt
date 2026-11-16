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
import com.vitorpamplona.amethyst.commons.napplet.NsiteNetworkChoices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * The app-wide [NsiteNetworkChoices]: whether each nSite routes through Tor or over the open web
 * (a site never set follows the Tor settings' "Applets and Websites" switch), on the same `napplet_network` file as before, so nothing migrates.
 *
 * Lives only in the **main process**: the launcher reads it, and [NappletBrokerService] writes it
 * when the key-free `:napplet` sandbox relays a toggle over IPC. The sandbox never touches it, and
 * [Amethyst.instance] is deliberately unset there.
 */
object NsiteNetworkRegistry {
    private val choices by lazy {
        NsiteNetworkChoices(Amethyst.instance.appStores.getDataStore("napplet_network"), CoroutineScope(SupervisorJob() + Dispatchers.IO)) {
            Amethyst.instance.torPrefs.value.appletsAndWebsitesViaTor.value
        }
    }

    /** Starts loading the on-disk choices ahead of the first read. Idempotent. */
    fun preload() {
        choices
    }

    /**
     * Suspends until the on-disk choices have loaded, so [useTor] reflects the user's remembered
     * per-site choices instead of the bare Tor default. The startup preloader awaits this before
     * making any routing decision, so a site the user pinned to the open web isn't wrongly treated
     * as Tor on a cold start.
     */
    suspend fun awaitReady() = choices.awaitReady()

    /** Whether [coordinate] routes through Tor: the user's choice, else "Applets and Websites". */
    fun useTor(coordinate: String): Boolean = choices.useTor(coordinate)

    /** Records [useTor] for [coordinate] in memory and persists it. */
    fun set(
        coordinate: String,
        useTor: Boolean,
    ) = choices.set(coordinate, useTor)
}
