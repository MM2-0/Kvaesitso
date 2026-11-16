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
package com.vitorpamplona.amethyst.model.nip03Timestamp

import com.vitorpamplona.amethyst.commons.model.nip03Timestamp.BitcoinExplorerEndpoint
import com.vitorpamplona.quartz.nip03Timestamp.okhttp.OkHttpBitcoinExplorer
import org.junit.Assert.assertEquals
import org.junit.Test

class BitcoinExplorerEndpointTest {
    @Test
    fun defaultsToMempoolWhenTorIsActive() {
        assertEquals(
            OkHttpBitcoinExplorer.MEMPOOL_API_URL,
            BitcoinExplorerEndpoint.resolve(customExplorerUrl = null, usingTor = true),
        )
    }

    @Test
    fun defaultsToBlockstreamWhenTorIsInactive() {
        assertEquals(
            OkHttpBitcoinExplorer.BLOCKSTREAM_API_URL,
            BitcoinExplorerEndpoint.resolve(customExplorerUrl = null, usingTor = false),
        )
    }

    @Test
    fun aConfiguredCustomUrlAlwaysWins() {
        val custom = "https://my.esplora.example/api/"
        assertEquals(custom, BitcoinExplorerEndpoint.resolve(custom, usingTor = true))
        assertEquals(custom, BitcoinExplorerEndpoint.resolve(custom, usingTor = false))
    }

    @Test
    fun blankCustomUrlFallsBackToTheDefault() {
        assertEquals(
            OkHttpBitcoinExplorer.BLOCKSTREAM_API_URL,
            BitcoinExplorerEndpoint.resolve(customExplorerUrl = "   ", usingTor = false),
        )
    }

    @Test
    fun normalizedFormStripsTrailingSlashForPathJoining() {
        assertEquals(
            "https://my.esplora.example/api",
            BitcoinExplorerEndpoint.resolveNormalized("https://my.esplora.example/api/", usingTor = true),
        )
        // The mempool default carries a trailing slash; normalized form drops it
        // so callers can safely do "$base/tx/$txid".
        assertEquals(
            OkHttpBitcoinExplorer.MEMPOOL_API_URL.trimEnd('/'),
            BitcoinExplorerEndpoint.resolveNormalized(customExplorerUrl = null, usingTor = true),
        )
    }
}
