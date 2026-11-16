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
package com.vitorpamplona.amethyst.commons.model.topNavFeeds.allUserFollows

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.IFeedTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.OutboxRelayLoader
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.author.AuthorsTopNavPerRelayFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.author.AuthorsTopNavPerRelayFilterSet
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine

/**
 * This is a big OR filter on all fields.
 */
@Immutable
class AllUserFollowsByOutboxTopNavFilter(
    val authors: Set<String>,
    val defaultRelays: StateFlow<Set<NormalizedRelayUrl>>,
    val blockedRelays: StateFlow<Set<NormalizedRelayUrl>>,
) : IFeedTopNavFilter {
    override fun matchAuthor(pubkey: HexKey): Boolean = pubkey in authors

    override fun match(noteEvent: Event): Boolean =
        when (noteEvent) {
            is LiveActivitiesEvent -> {
                noteEvent.participantsIntersect(authors)
            }

            is CommentEvent -> {
                // ignore follows and checks only the root scope
                noteEvent.pubKey in authors
            }

            else -> {
                noteEvent.pubKey in authors
            }
        }

    override fun toPerRelayFlow(cache: ICacheProvider): Flow<AuthorsTopNavPerRelayFilterSet> {
        val authorsPerRelay = OutboxRelayLoader().toAuthorsPerRelayFlow(authors, cache) { it }

        return combine(authorsPerRelay, defaultRelays, blockedRelays) { perRelayAuthors, default, blockedRelays ->
            val allRelays = perRelayAuthors.keys.filter { it !in blockedRelays }.ifEmpty { default }

            AuthorsTopNavPerRelayFilterSet(
                allRelays.associateWith {
                    AuthorsTopNavPerRelayFilter(
                        authors = perRelayAuthors[it] ?: emptySet(),
                    )
                },
            )
        }
    }

    override fun startValue(cache: ICacheProvider): AuthorsTopNavPerRelayFilterSet {
        val authorsPerRelay = OutboxRelayLoader().authorsPerRelaySnapshot(authors, cache) { it }

        val allRelays = authorsPerRelay.keys.filter { it !in blockedRelays.value }.ifEmpty { defaultRelays.value }

        return AuthorsTopNavPerRelayFilterSet(
            allRelays.associateWith {
                AuthorsTopNavPerRelayFilter(
                    authors = authorsPerRelay[it] ?: emptySet(),
                )
            },
        )
    }
}
