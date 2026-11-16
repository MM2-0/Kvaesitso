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

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.collections.immutable.toImmutableList
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Stable
class FeedBuilderState(
    initial: FeedDefinition? = null,
) {
    var name by mutableStateOf(initial?.name ?: "")
    var emoji by mutableStateOf(initial?.emoji ?: "")
    var refreshMode by mutableStateOf(initial?.refreshMode ?: RefreshMode.LIVE_STREAM)

    val hashtags =
        mutableStateListOf<String>().apply {
            (initial?.source as? FeedSource.Filter)?.hashtags?.let { addAll(it) }
        }
    val authors =
        mutableStateListOf<HexKey>().apply {
            (initial?.source as? FeedSource.Filter)?.authors?.let { addAll(it) }
        }
    val relays =
        mutableStateListOf<String>().apply {
            (initial?.source as? FeedSource.Filter)?.relays?.let { addAll(it) }
        }
    val excludeAuthors =
        mutableStateListOf<HexKey>().apply {
            (initial?.source as? FeedSource.Filter)?.excludeAuthors?.let { addAll(it) }
        }
    val excludeKeywords =
        mutableStateListOf<String>().apply {
            (initial?.source as? FeedSource.Filter)?.excludeKeywords?.let { addAll(it) }
        }
    val kinds =
        mutableStateListOf<Int>().apply {
            (initial?.source as? FeedSource.Filter)?.kinds?.let { addAll(it) }
        }

    val isValid: Boolean
        get() = name.isNotBlank() && (hashtags.isNotEmpty() || authors.isNotEmpty() || relays.isNotEmpty())

    private val editId: String? = initial?.id

    @OptIn(ExperimentalUuidApi::class)
    fun toDefinition(): FeedDefinition {
        val source =
            FeedSource.Filter(
                hashtags = hashtags.toImmutableList(),
                authors = authors.toImmutableList(),
                relays = relays.toImmutableList(),
                excludeAuthors = excludeAuthors.toImmutableList(),
                excludeKeywords = excludeKeywords.toImmutableList(),
                kinds = kinds.toImmutableList(),
            )
        return FeedDefinition(
            id = editId ?: Uuid.random().toString(),
            name = name,
            emoji = emoji,
            pinned = false,
            pinOrder = Int.MAX_VALUE,
            source = source,
            refreshMode = refreshMode,
            createdAt = TimeUtils.now(),
        )
    }
}
