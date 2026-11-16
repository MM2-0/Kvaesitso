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

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.collections.immutable.toImmutableList
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class FeedDefinitionBuilder {
    var name: String = ""
    var emoji: String = ""
    var refreshMode: RefreshMode = RefreshMode.LIVE_STREAM
    private var source: FeedSource? = null

    fun filter(init: FilterBuilder.() -> Unit) {
        source = FilterBuilder().apply(init).build()
    }

    fun fromPeopleList(
        kind: Int = 30000,
        pubkey: HexKey,
        dTag: String,
    ) {
        source = FeedSource.PeopleList(kind, pubkey, dTag)
    }

    fun fromDvm(
        kind: Int = 31990,
        pubkey: HexKey,
        dTag: String,
    ) {
        source = FeedSource.DVM(kind, pubkey, dTag)
    }

    fun fromRelay(url: String) {
        source = FeedSource.SingleRelay(url)
    }

    fun global() {
        source = FeedSource.Global
    }

    fun following() {
        source = FeedSource.Following
    }

    fun build(): FeedDefinition =
        FeedDefinition(
            id = generateId(),
            name = name,
            emoji = emoji,
            pinned = false,
            pinOrder = Int.MAX_VALUE,
            source = source ?: error("FeedDefinition requires a source"),
            refreshMode = refreshMode,
            createdAt = TimeUtils.now(),
        )

    @OptIn(ExperimentalUuidApi::class)
    private fun generateId(): String = Uuid.random().toString()
}

class FilterBuilder {
    val hashtags = mutableListOf<String>()
    val authors = mutableListOf<HexKey>()
    val relays = mutableListOf<String>()
    val excludeAuthors = mutableListOf<HexKey>()
    val excludeKeywords = mutableListOf<String>()
    val kinds = mutableListOf<Int>()

    fun build(): FeedSource.Filter =
        FeedSource.Filter(
            hashtags = hashtags.toImmutableList(),
            authors = authors.toImmutableList(),
            relays = relays.toImmutableList(),
            excludeAuthors = excludeAuthors.toImmutableList(),
            excludeKeywords = excludeKeywords.toImmutableList(),
            kinds = kinds.toImmutableList(),
        )
}

inline fun feedDefinition(init: FeedDefinitionBuilder.() -> Unit): FeedDefinition = FeedDefinitionBuilder().apply(init).build()

fun defaultFeeds(): List<FeedDefinition> =
    listOf(
        FeedDefinition(
            id = "default-following",
            name = "Following",
            emoji = "\uD83C\uDFE0",
            pinned = true,
            pinOrder = 0,
            source = FeedSource.Following,
            refreshMode = RefreshMode.LIVE_STREAM,
            createdAt = 0L,
        ),
        FeedDefinition(
            id = "default-global",
            name = "Global",
            emoji = "\uD83C\uDF10",
            pinned = true,
            pinOrder = 1,
            source = FeedSource.Global,
            refreshMode = RefreshMode.LIVE_STREAM,
            createdAt = 0L,
        ),
    )
