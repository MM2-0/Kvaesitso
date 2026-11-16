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
package com.vitorpamplona.amethyst.commons.nip34Git

import androidx.collection.LruCache
import com.vitorpamplona.quartz.nip34Git.git.GitRepoSnapshot

/**
 * Process-wide cache of fetched default-branch repository snapshots, keyed by repository
 * address (`kind:pubkey:dTag`).
 *
 * A snapshot is a shallow clone fetched over the network, so re-cloning on every screen — or
 * during the share-to-image capture, which only waits ~1s for content to settle — produces
 * empty stats. Caching the default-branch snapshot lets the project home, the feed repo card
 * and the image renderer reuse it synchronously and avoids the repeated fetch the user sees
 * when switching screens. Bounded so a few large repos can't grow memory without limit.
 */
object GitRepoSnapshotCache {
    private val cache = LruCache<String, GitRepoSnapshot>(8)

    fun get(key: String?): GitRepoSnapshot? = key?.let { cache.get(it) }

    fun put(
        key: String?,
        snapshot: GitRepoSnapshot,
    ) {
        if (key != null) cache.put(key, snapshot)
    }
}
