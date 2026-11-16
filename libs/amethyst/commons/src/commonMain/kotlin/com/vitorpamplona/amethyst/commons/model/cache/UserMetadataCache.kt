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
package com.vitorpamplona.amethyst.commons.model.cache

import com.vitorpamplona.quartz.nip01Core.metadata.UserMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Simple data class for cached user display info
 */
data class UserDisplayInfo(
    val pubkey: String,
    val displayName: String?,
    val pictureUrl: String?,
    val nip05: String?,
)

/**
 * Lightweight user metadata cache for UI display.
 * Can be used by any feature (chess, chat, etc.) that needs user info.
 *
 * Thread-safe via StateFlow updates.
 */
class UserMetadataCache {
    private val _metadata = MutableStateFlow<Map<String, UserMetadata>>(emptyMap())
    val metadata: StateFlow<Map<String, UserMetadata>> = _metadata.asStateFlow()

    private val _pubkeysNeeded = MutableStateFlow<Set<String>>(emptySet())
    val pubkeysNeeded: StateFlow<Set<String>> = _pubkeysNeeded.asStateFlow()

    /**
     * Add or update metadata for a pubkey
     */
    fun put(
        pubkey: String,
        userMetadata: UserMetadata,
    ) {
        _metadata.value = _metadata.value + (pubkey to userMetadata)
        _pubkeysNeeded.value = _pubkeysNeeded.value - pubkey
    }

    /**
     * Get metadata for a pubkey (may be null if not cached)
     */
    fun get(pubkey: String): UserMetadata? = _metadata.value[pubkey]

    /**
     * Check if metadata is cached for a pubkey
     */
    fun contains(pubkey: String): Boolean = _metadata.value.containsKey(pubkey)

    /**
     * Request metadata for a pubkey (adds to needed set if not cached)
     */
    fun request(pubkey: String) {
        if (!contains(pubkey) && pubkey !in _pubkeysNeeded.value) {
            _pubkeysNeeded.value = _pubkeysNeeded.value + pubkey
        }
    }

    /**
     * Request metadata for multiple pubkeys
     */
    fun requestAll(pubkeys: Collection<String>) {
        val newNeeded = pubkeys.filter { !contains(it) && it !in _pubkeysNeeded.value }
        if (newNeeded.isNotEmpty()) {
            _pubkeysNeeded.value = _pubkeysNeeded.value + newNeeded
        }
    }

    /**
     * Get display name for a pubkey, falling back to truncated pubkey
     */
    fun getDisplayName(pubkey: String): String {
        val meta = get(pubkey)
        return meta?.bestName() ?: formatPubkeyShort(pubkey)
    }

    /**
     * Get profile picture URL for a pubkey
     */
    fun getPictureUrl(pubkey: String): String? = get(pubkey)?.profilePicture()

    /**
     * Get display info for a pubkey (for UI binding)
     */
    fun getDisplayInfo(pubkey: String): UserDisplayInfo {
        val meta = get(pubkey)
        return UserDisplayInfo(
            pubkey = pubkey,
            displayName = meta?.bestName(),
            pictureUrl = meta?.profilePicture(),
            nip05 = meta?.nip05,
        )
    }

    /**
     * Clear metadata that was requested but hasn't been fetched
     * (call when pubkeys are no longer needed)
     */
    fun clearNeeded(pubkeys: Collection<String>) {
        _pubkeysNeeded.value = _pubkeysNeeded.value - pubkeys.toSet()
    }

    /**
     * Clear all cached metadata
     */
    fun clear() {
        _metadata.value = emptyMap()
        _pubkeysNeeded.value = emptySet()
    }

    companion object {
        /**
         * Format pubkey for display (npub-style truncation)
         */
        fun formatPubkeyShort(pubkey: String): String =
            if (pubkey.length > 12) {
                "${pubkey.take(8)}...${pubkey.takeLast(4)}"
            } else {
                pubkey
            }
    }
}
