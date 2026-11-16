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
package com.vitorpamplona.amethyst.commons.model.backups

import androidx.collection.LruCache
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.isAddressable
import com.vitorpamplona.quartz.nip01Core.core.isReplaceable

/**
 * Ids of the replaceable/addressable events this app instance produced (signed here, or
 * restored from its own on-disk backup) since the process started.
 *
 * The backup guard uses it to tell "the user edited this list in Amethyst" from "a newer
 * version came in from a relay", i.e. from another client. Only the latter can silently
 * wipe data another app did not know about, so only the latter is ever questioned.
 *
 * Fed from the cache's `justConsumeMyOwnEvent` choke point, which every local publish goes
 * through. Only replaceable and addressable kinds are tracked, and the ids live in a bounded
 * LRU: only the latest few versions of each list matter, since an older version is already
 * superseded by the time the backup guard sees a newer one.
 */
object LocallySignedEvents {
    private const val MAX_TRACKED = 500

    // androidx LruCache synchronizes internally, so the relay and UI threads can share it.
    private val ids = LruCache<HexKey, Boolean>(MAX_TRACKED)

    fun mark(event: Event) {
        if (event.kind.isReplaceable() || event.kind.isAddressable()) {
            ids.put(event.id, true)
        }
    }

    fun contains(eventId: HexKey): Boolean = ids[eventId] != null
}
