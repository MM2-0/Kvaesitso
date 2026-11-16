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
package com.vitorpamplona.amethyst.commons.relayClient.preload

import com.vitorpamplona.amethyst.commons.model.User

/**
 * Preloads user metadata and avatar images for feed items.
 * Coordinates between metadata subscription (rate-limited) and image preloading.
 *
 * Priority order:
 * 1. Metadata (display names, avatar URLs) - FIRST
 * 2. Avatar images (prefetch when metadata arrives) - SECOND
 */
class MetadataPreloader(
    private val rateLimiter: MetadataRateLimiter,
    private val imagePrefetcher: ImagePrefetcher? = null,
) {
    /**
     * Queue users for metadata preloading.
     * If user already has metadata, prefetch their avatar image.
     * Otherwise, queue for metadata subscription.
     */
    fun preloadForUsers(users: Collection<User>) {
        users.forEach { user ->
            val metadata =
                user
                    .metadata()
                    .flow.value
                    ?.info
            if (metadata != null) {
                // Already have metadata, prefetch avatar
                metadata.picture?.let { avatarUrl ->
                    imagePrefetcher?.prefetch(avatarUrl)
                }
            } else {
                // Need to fetch metadata first
                rateLimiter.enqueue(user.pubkeyHex)
            }
        }
    }

    /**
     * Queue a single user for metadata preloading.
     */
    fun preloadForUser(user: User) {
        val metadata =
            user
                .metadata()
                .flow.value
                ?.info
        if (metadata != null) {
            metadata.picture?.let { avatarUrl ->
                imagePrefetcher?.prefetch(avatarUrl)
            }
        } else {
            rateLimiter.enqueue(user.pubkeyHex)
        }
    }

    /**
     * Called when metadata arrives for a user.
     * Triggers avatar image prefetch.
     */
    fun onMetadataReceived(user: User) {
        user.metadata().flow.value?.info?.picture?.let { avatarUrl ->
            imagePrefetcher?.prefetch(avatarUrl)
        }
    }

    /**
     * Prefetch avatar images for users that already have metadata.
     */
    fun prefetchAvatars(users: Collection<User>) {
        users.forEach { user ->
            user.metadata().flow.value?.info?.picture?.let { avatarUrl ->
                imagePrefetcher?.prefetch(avatarUrl)
            }
        }
    }
}

/**
 * Interface for image prefetching.
 * Platform-specific implementations use Coil (Android) or similar (Desktop).
 */
interface ImagePrefetcher {
    /**
     * Prefetch an image URL into the cache.
     */
    fun prefetch(url: String)

    /**
     * Prefetch multiple image URLs.
     */
    fun prefetchAll(urls: Collection<String>) {
        urls.forEach { prefetch(it) }
    }
}
