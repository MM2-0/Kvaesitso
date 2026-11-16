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
package com.vitorpamplona.amethyst.commons.feeds.custom

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
data class FeedDefinition(
    val id: String,
    val name: String,
    val emoji: String,
    val pinned: Boolean,
    val pinOrder: Int,
    val source: FeedSource,
    val refreshMode: RefreshMode,
    val createdAt: Long,
)

@Immutable
sealed interface FeedSource {
    @Immutable
    data class Filter(
        val hashtags: ImmutableList<String> = persistentListOf(),
        val authors: ImmutableList<HexKey> = persistentListOf(),
        val relays: ImmutableList<String> = persistentListOf(),
        val excludeAuthors: ImmutableList<HexKey> = persistentListOf(),
        val excludeKeywords: ImmutableList<String> = persistentListOf(),
        val kinds: ImmutableList<Int> = persistentListOf(),
    ) : FeedSource

    @Immutable
    data class PeopleList(
        val kind: Int,
        val pubkey: HexKey,
        val dTag: String,
    ) : FeedSource

    @Immutable
    data class InterestSet(
        val kind: Int,
        val pubkey: HexKey,
        val dTag: String,
    ) : FeedSource

    @Immutable
    data class DVM(
        val kind: Int,
        val pubkey: HexKey,
        val dTag: String,
    ) : FeedSource

    @Immutable
    data class SingleRelay(
        val url: String,
    ) : FeedSource

    @Immutable
    data object Global : FeedSource

    @Immutable
    data object Following : FeedSource
}

enum class RefreshMode {
    LIVE_STREAM,
    POLL_5MIN,
}
