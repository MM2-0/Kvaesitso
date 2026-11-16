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
package com.vitorpamplona.amethyst.commons.model.buzz

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The set of Buzz workspace channels the user has **starred** (favorited), keyed by channel id (the
 * NIP-29 `h`/UUID, globally unique on Buzz). Starred channels float to the top of the community view.
 *
 * There is no Nostr event for a personal star — it's the client's own bookkeeping — so, like
 * [BuzzWorkspaces], it is mirrored to disk by the platform
 * ([com.vitorpamplona.amethyst] `BuzzChannelStarPreferences`) and restored at startup.
 *
 * **One instance per account** (`Account.buzzChannelStars`). A star is by definition personal: it
 * says which channels *this user* wants pinned to the top of the community view. While it was a
 * process-wide singleton, one account's favorites reordered and badged every other logged-in
 * account's channel list — and switching accounts silently rewrote the set they shared.
 */
class BuzzChannelStars {
    private val starred = MutableStateFlow<Set<String>>(emptySet())

    /** The starred channel ids; the community view collects this to pin + badge them. */
    val flow: StateFlow<Set<String>> = starred

    fun isStarred(channelId: String): Boolean = channelId in starred.value

    /** Flips [channelId]'s star. Returns the new state (true = now starred). */
    fun toggle(channelId: String): Boolean {
        while (true) {
            val current = starred.value
            val next = if (channelId in current) current - channelId else current + channelId
            if (starred.compareAndSet(current, next)) return channelId in next
        }
    }

    /** Replaces the whole set — used to restore from disk at startup. */
    fun restore(ids: Set<String>) {
        starred.value = ids
    }
}
