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
package com.vitorpamplona.amethyst.commons.ui.note.elements

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.commons.richtext.CachedRichTextParser
import com.vitorpamplona.amethyst.commons.richtext.HashTagSegment
import com.vitorpamplona.amethyst.commons.ui.components.ClickableTextColor
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.theme.HalfTopPadding
import com.vitorpamplona.amethyst.commons.ui.theme.lessImportantLink
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags

@Composable
fun DisplayUncitedHashtags(
    event: Event,
    callbackUri: String? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    DisplayUncitedHashtags(event, event.content, callbackUri, accountViewModel, nav)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DisplayUncitedHashtags(
    event: Event,
    content: String,
    callbackUri: String? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // Keyed on what it reads: an edit (NIP-41) or a step through the versions changes [content]
    // while the card stays composed, and the row has to follow the text it sits under.
    val unusedHashtags by
        produceState(initialValue = emptyList<String>(), event, content, callbackUri) {
            val tagsInEvent = event.hashtags()
            if (tagsInEvent.isEmpty()) {
                value = emptyList()
            } else {
                val state = CachedRichTextParser.parseText(content, event.tags.toImmutableListOfLists(), callbackUri)

                val tagsInContent =
                    state
                        .paragraphs
                        .flatMap { paragraphState ->
                            paragraphState.words.mapNotNull {
                                if (it is HashTagSegment) {
                                    it.hashtag
                                } else {
                                    null
                                }
                            }
                        }

                val unusedHashtags =
                    tagsInEvent.filterNot { eventTag ->
                        tagsInContent.any { contentTag ->
                            eventTag.equals(contentTag, true)
                        }
                    }

                value = unusedHashtags
            }
        }

    if (unusedHashtags.isNotEmpty()) {
        FlowRow(
            modifier = HalfTopPadding,
        ) {
            unusedHashtags.forEach { hashtag ->
                ClickableTextColor(
                    text = "#$hashtag ",
                    onClick = { nav.nav(Route.Hashtag(hashtag.lowercase())) },
                    linkColor = MaterialTheme.colorScheme.lessImportantLink,
                )
            }
        }
    }
}
