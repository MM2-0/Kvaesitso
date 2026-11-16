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
package com.vitorpamplona.amethyst.commons.model.nip85TrustedAssertions

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.UserDependencies
import com.vitorpamplona.amethyst.commons.relays.EOSERelayList
import com.vitorpamplona.amethyst.commons.util.PlatformNumberFormatter
import com.vitorpamplona.amethyst.commons.wot.network.TrustNetwork
import com.vitorpamplona.amethyst.commons.wot.network.TrustNetworkState
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ServiceProviderTag
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.UserAssertionEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class UserCardsCache : UserDependencies {
    val receivedCards = MutableStateFlow(mapOf<User, AddressableNote>())

    /** Tracks EOSE (End Of Stored Events) for relay subscriptions */
    val latestEOSEs = EOSERelayList()

    fun addCard(note: AddressableNote) {
        val author = note.author ?: return

        val cardBy = receivedCards.value[author]

        // if it's already there, quick exit
        if (cardBy != null && cardBy == note) return

        receivedCards.update {
            val author = note.author
            if (author == null) {
                it
            } else {
                it + (author to note)
            }
        }
    }

    fun removeCard(note: AddressableNote) {
        val author = note.author ?: return
        val cardBy = receivedCards.value[author]

        // if it's not already there, quick exit
        if (cardBy == null || cardBy != note) return

        receivedCards.update {
            val author = note.author
            if (author == null) {
                it
            } else {
                val reportsByInner = it[author]
                if (reportsByInner == null) {
                    it
                } else {
                    it - author
                }
            }
        }
    }

    fun rankFlow(trustProviderList: TrustProviderListState) =
        combineTransform(receivedCards, trustProviderList.liveUserRankProvider) { cards, provider ->
            if (provider != null) {
                val flow =
                    cards.firstNotNullOfOrNull {
                        if (it.key.pubkeyHex == provider.pubkey) {
                            it.value
                                .flow()
                                .metadata.stateFlow
                        } else {
                            null
                        }
                    }

                if (flow != null) {
                    emitAll(flow)
                } else {
                    emit(null)
                }
            } else {
                emit(null)
            }
        }.map {
            (it?.note?.event as? UserAssertionEvent)?.rank()
        }.flowOn(Dispatchers.IO)

    private val formatter = PlatformNumberFormatter()

    fun followerCountStrFlow(trustProviderList: TrustProviderListState) =
        combineTransform(receivedCards, trustProviderList.liveUserFollowerCount) { cards, provider ->
            if (provider != null) {
                val flow =
                    cards.firstNotNullOfOrNull {
                        if (it.key.pubkeyHex == provider.pubkey) {
                            it.value
                                .flow()
                                .metadata.stateFlow
                        } else {
                            null
                        }
                    }

                if (flow != null) {
                    emitAll(flow)
                } else {
                    emit(null)
                }
            } else {
                emit(null)
            }
        }.map {
            val value = (it?.note?.event as? UserAssertionEvent)?.followerCount()

            if (value != null && value > 0) {
                formatter.format(value.toLong())
            } else {
                "--"
            }
        }.flowOn(Dispatchers.IO)

    /**
     * [subject]'s rank: from the local trust network when it holds the rank provider's cards
     * (including a newer card seen since its last sync), otherwise from the card received for
     * this profile.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun rankFlow(
        trustProviderList: TrustProviderListState,
        trustNetwork: TrustNetworkState,
        subject: HexKey,
    ): Flow<Int?> =
        servingNetwork(trustProviderList.liveUserRankProvider, trustNetwork.network)
            .flatMapLatest { served ->
                if (served != null) {
                    trustNetwork.overlay.map { trustNetwork.rankOf(subject) }.distinctUntilChanged()
                } else {
                    rankFlow(trustProviderList)
                }
            }.flowOn(Dispatchers.IO)

    /** [subject]'s follower count for display, from the trust network when it serves that provider. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun followerCountStrFlow(
        trustProviderList: TrustProviderListState,
        trustNetwork: TrustNetworkState,
        subject: HexKey,
    ): Flow<String> =
        servingNetwork(trustProviderList.liveUserFollowerCount, trustNetwork.network)
            .flatMapLatest { served ->
                if (served != null) {
                    trustNetwork.overlay
                        .map {
                            val value = trustNetwork.followersOf(subject)
                            if (value != null && value > 0) formatter.format(value.toLong()) else "--"
                        }.distinctUntilChanged()
                } else {
                    followerCountStrFlow(trustProviderList)
                }
            }.flowOn(Dispatchers.IO)

    /** The loaded index when it was built from [provider]'s cards, else null. */
    private fun servingNetwork(
        provider: StateFlow<ServiceProviderTag?>,
        network: StateFlow<TrustNetwork?>,
    ): Flow<TrustNetwork?> =
        combine(provider, network) { p, n -> if (p != null && n != null && n.isFrom(p)) n else null }
            // Same index, same answers: a header-only change (an update that found nothing) must
            // not restart every profile's flow.
            .distinctUntilChanged { a, b -> a?.index === b?.index }
}
