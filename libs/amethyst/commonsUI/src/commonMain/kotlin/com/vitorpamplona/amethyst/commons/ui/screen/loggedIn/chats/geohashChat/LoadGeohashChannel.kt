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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.geohashChat

import androidx.compose.runtime.Composable
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.geohashChat.GeohashChatChannel
import com.vitorpamplona.amethyst.commons.ui.note.produceStateIfNotNull

/**
 * Resolves the [GeohashChatChannel] for [geohash] from LocalCache, creating an
 * empty one if the cell hasn't been seen yet, then hands it to [content].
 * Mirrors `LoadEphemeralChatChannel` so the geohash room plugs into the same
 * feed + subscription plumbing every other channel uses.
 */
@Composable
fun LoadGeohashChannel(
    geohash: String,
    content: @Composable (GeohashChatChannel) -> Unit,
) {
    val channel =
        produceStateIfNotNull(LocalCache.getGeohashChannelIfExists(geohash), geohash) {
            value = LocalCache.getOrCreateGeohashChannel(geohash)
        }

    channel.value?.let { content(it) }
}
