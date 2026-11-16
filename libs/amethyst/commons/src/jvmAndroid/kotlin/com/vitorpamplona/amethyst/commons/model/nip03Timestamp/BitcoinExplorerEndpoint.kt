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
package com.vitorpamplona.amethyst.commons.model.nip03Timestamp

import com.vitorpamplona.quartz.nip03Timestamp.okhttp.OkHttpBitcoinExplorer

/**
 * Picks the Bitcoin block-explorer (Esplora) base URL.
 *
 * This is the single source of truth shared by two features that both talk to
 * the same Esplora API:
 * - OpenTimestamps verification ([TorAwareOkHttpOtsResolverBuilder]).
 * - NIP-BC onchain zaps (the `EsploraBackend` wired in `AppModules`).
 *
 * So a user who configures a custom explorer in the OTS settings gets it for
 * onchain zaps too, and both honour the same Tor preference: a configured
 * [OtsSettings] custom URL always wins; otherwise mempool.space is used when
 * Tor is active (it is reachable over Tor) and blockstream.info when it is not.
 */
object BitcoinExplorerEndpoint {
    /**
     * @param customExplorerUrl A user-configured explorer base URL, or null/blank
     *        for the automatic Tor-aware default. Typically
     *        `OtsSettings.normalizedUrl()`.
     * @param usingTor Whether Bitcoin/"money" traffic is currently routed over Tor.
     */
    fun resolve(
        customExplorerUrl: String?,
        usingTor: Boolean,
    ): String =
        customExplorerUrl?.takeIf { it.isNotBlank() }
            ?: if (usingTor) {
                OkHttpBitcoinExplorer.MEMPOOL_API_URL
            } else {
                OkHttpBitcoinExplorer.BLOCKSTREAM_API_URL
            }

    /**
     * Same as [resolve] but with any trailing slash stripped, for callers that
     * join request paths with a leading `/` (e.g. `"$base/tx/$txid"`).
     */
    fun resolveNormalized(
        customExplorerUrl: String?,
        usingTor: Boolean,
    ): String = resolve(customExplorerUrl, usingTor).trimEnd('/')
}
