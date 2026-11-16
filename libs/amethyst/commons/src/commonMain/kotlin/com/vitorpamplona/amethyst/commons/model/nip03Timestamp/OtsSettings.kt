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

import androidx.compose.runtime.Stable
import com.vitorpamplona.quartz.nip03Timestamp.BitcoinExplorerUrls
import kotlinx.serialization.Serializable

/**
 * Immutable data class representing the current OTS blockchain explorer config.
 *
 * When a custom URL is configured, it is used instead of the automatic
 * Tor-aware selection (Mempool when Tor is active, Blockstream otherwise).
 * This gives users control over which explorer observes their OTS verifications.
 */
@Serializable
@Stable
data class OtsSettings(
    /**
     * Custom blockchain explorer base API URL.
     * When null/blank, the default Tor-aware selection is used.
     * Must be a Mempool-compatible REST API (e.g. https://mempool.space/api/).
     */
    val customExplorerUrl: String? = null,
) {
    /** True when the user has configured a custom explorer URL. */
    val hasCustomExplorer: Boolean get() = !customExplorerUrl.isNullOrBlank()

    /**
     * Returns the normalized custom URL (trailing slash ensured) or null if not set.
     */
    fun normalizedUrl(): String? {
        val url = customExplorerUrl?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return if (url.endsWith("/")) url else "$url/"
    }

    companion object {
        val DEFAULT = OtsSettings()

        val KNOWN_EXPLORERS =
            listOf(
                BitcoinExplorerUrls.MEMPOOL to "mempool.space (Tor-friendly)",
                BitcoinExplorerUrls.BLOCKSTREAM to "blockstream.info",
            )

        fun isValidUrl(url: String): Boolean {
            val trimmed = url.trim()
            if (trimmed.isBlank()) return false
            return trimmed.startsWith("http://") || trimmed.startsWith("https://")
        }
    }
}
