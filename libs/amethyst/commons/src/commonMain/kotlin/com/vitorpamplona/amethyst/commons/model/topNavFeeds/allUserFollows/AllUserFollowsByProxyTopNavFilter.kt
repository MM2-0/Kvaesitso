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
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.author.AuthorsTopNavPerRelayFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.author.AuthorsTopNavPerRelayFilterSet
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * This is a big OR filter on all fields.
 */
@Immutable
class AllUserFollowsByProxyTopNavFilter(
    val authors: Set<String>,
    val proxyRelays: Set<NormalizedRelayUrl>,
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

    // forces the use of the Proxy on all connections, replacing the outbox model.
    override fun toPerRelayFlow(cache: ICacheProvider): Flow<AuthorsTopNavPerRelayFilterSet> =
        MutableStateFlow(
            AuthorsTopNavPerRelayFilterSet(
                proxyRelays.associateWith {
                    AuthorsTopNavPerRelayFilter(
                        authors = authors,
                    )
                },
            ),
        )

    override fun startValue(cache: ICacheProvider): AuthorsTopNavPerRelayFilterSet {
        // forces the use of the Proxy on all connections, replacing the outbox model.
        return AuthorsTopNavPerRelayFilterSet(
            proxyRelays.associateWith {
                AuthorsTopNavPerRelayFilter(
                    authors = authors,
                )
            },
        )
    }
}
