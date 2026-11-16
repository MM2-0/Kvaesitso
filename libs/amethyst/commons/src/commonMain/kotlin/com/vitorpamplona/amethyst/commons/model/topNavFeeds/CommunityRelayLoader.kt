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
package com.vitorpamplona.amethyst.commons.model.topNavFeeds

import com.vitorpamplona.amethyst.commons.model.NoteState
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import com.vitorpamplona.quartz.utils.mapOfSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

class CommunityRelayLoader {
    companion object {
        fun communitiesPerRelay(
            communityNotes: Array<NoteState>,
            cache: ICacheProvider,
        ): Map<NormalizedRelayUrl, Set<HexKey>> =
            mapOfSet {
                communityNotes.forEach { communityNote ->
                    val relays =
                        (communityNote.note.event as? CommunityDefinitionEvent)
                            ?.relayUrls()
                            ?.ifEmpty { null }
                            ?: cache.relayHints.hintsForAddress(communityNote.note.idHex)

                    relays.forEach {
                        add(it, communityNote.note.idHex)
                    }
                }
            }

        fun <T> communitiesPerRelaySnapshot(
            communities: Set<HexKey>,
            cache: ICacheProvider,
            transformation: (Map<NormalizedRelayUrl, Set<HexKey>>) -> T,
        ): T {
            val noteMetadata =
                communities
                    .mapNotNull { addressId ->
                        cache
                            .checkGetOrCreateAddressableNote(addressId)
                            ?.flow()
                            ?.metadata
                            ?.stateFlow
                            ?.value
                    }.toTypedArray()
            return transformation(communitiesPerRelay(noteMetadata, cache))
        }

        fun <T> toCommunitiesPerRelayFlow(
            communities: Set<HexKey>,
            cache: ICacheProvider,
            transformation: (Map<NormalizedRelayUrl, Set<HexKey>>) -> T,
        ): Flow<T> {
            val noteMetadataFlows =
                communities.mapNotNull { addressId ->
                    cache
                        .checkGetOrCreateAddressableNote(addressId)
                        ?.flow()
                        ?.metadata
                        ?.stateFlow
                }

            return if (noteMetadataFlows.isEmpty()) {
                MutableStateFlow(transformation(emptyMap()))
            } else {
                combine(noteMetadataFlows) { communityNotes ->
                    transformation(communitiesPerRelay(communityNotes, cache))
                }
            }
        }
    }
}
