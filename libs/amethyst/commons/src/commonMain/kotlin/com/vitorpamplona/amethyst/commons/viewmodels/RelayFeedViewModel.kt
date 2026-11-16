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
package com.vitorpamplona.amethyst.commons.viewmodels

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.feeds.InvalidatableContent
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.nip01Core.RelayInfo
import com.vitorpamplona.amethyst.commons.model.nip01Core.Wrapper
import com.vitorpamplona.amethyst.commons.relays.dialableReadRelays
import com.vitorpamplona.amethyst.commons.relays.dialableRelays
import com.vitorpamplona.amethyst.commons.relays.dialableWriteRelays
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip17Dm.settings.DmRelayListEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch

class MyRelayInfo(
    val url: NormalizedRelayUrl,
    val lastEvent: Long,
    val counter: Int,
)

@Stable
class RelayFeedViewModel :
    ViewModel(),
    InvalidatableContent {
    val order =
        compareByDescending<MyRelayInfo> { it.counter }
            .thenByDescending { it.lastEvent }
            .thenBy { it.url.url }

    // The rows below render, and fetch NIP-11 for, any user's relays, so they read the lists as
    // someone else's: a stranger's local relay is never shown or contacted.
    var currentUser: MutableStateFlow<User?> = MutableStateFlow(null)

    fun convert(
        relays: Set<NormalizedRelayUrl>?,
        relayState: Wrapper,
    ): List<MyRelayInfo> = convert(relays, relayState.data)

    fun convert(
        relays: Set<NormalizedRelayUrl>?,
        relayState: Map<NormalizedRelayUrl, RelayInfo>?,
    ): List<MyRelayInfo> {
        if (relays == null) return emptyList()
        return relays
            .map { relay ->
                val info = relayState?.get(relay)
                if (info != null) {
                    MyRelayInfo(relay, info.lastEvent, info.counter)
                } else {
                    MyRelayInfo(relay, 0, 0)
                }
            }.sortedWith(order)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val nip65OutboxFlow =
        currentUser
            .transformLatest { user ->
                if (user != null) {
                    emitAll(
                        combine(
                            flow =
                                user.nip65RelayListNote.flow().metadata.stateFlow.map { nip65 ->
                                    (nip65.note.event as? AdvertisedRelayListEvent)?.dialableWriteRelays()?.toSet() ?: emptySet()
                                },
                            flow2 = user.relayState().flow(),
                            transform = ::convert,
                        ),
                    )
                } else {
                    emit(emptyList())
                }
            }.onStart {
                emit(convert((currentUser.value?.nip65RelayListNote?.event as? AdvertisedRelayListEvent)?.dialableWriteRelays()?.toSet(), currentUser.value?.relayState()?.data))
            }.flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val nip65InboxFlow =
        currentUser
            .transformLatest { user ->
                if (user != null) {
                    emitAll(
                        combine(
                            flow =
                                user.nip65RelayListNote.flow().metadata.stateFlow.map { nip65 ->
                                    (nip65.note.event as? AdvertisedRelayListEvent)?.dialableReadRelays()?.toSet() ?: emptySet()
                                },
                            flow2 = user.relayState().flow(),
                            transform = ::convert,
                        ),
                    )
                } else {
                    emit(emptyList())
                }
            }.onStart {
                emit(convert((currentUser.value?.nip65RelayListNote?.event as? AdvertisedRelayListEvent)?.dialableReadRelays()?.toSet(), currentUser.value?.relayState()?.data))
            }.flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val dmInboxFlow =
        currentUser
            .transformLatest { user ->
                if (user != null) {
                    emitAll(
                        combine(
                            flow =
                                user.dmRelayListNote
                                    .flow()
                                    .metadata.stateFlow
                                    .map { dmNote ->
                                        (dmNote.note.event as? DmRelayListEvent)?.dialableRelays()?.toSet() ?: emptySet()
                                    },
                            flow2 = user.relayState().flow(),
                            transform = ::convert,
                        ),
                    )
                } else {
                    emit(emptyList())
                }
            }.onStart {
                emit(convert((currentUser.value?.dmRelayListNote?.event as? DmRelayListEvent)?.dialableRelays()?.toSet(), currentUser.value?.relayState()?.data))
            }.flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    override val isRefreshing: MutableState<Boolean> = mutableStateOf(false)

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            refreshSuspended()
        }
    }

    suspend fun refreshSuspended() {
        try {
            isRefreshing.value = true

            delay(1000)
        } finally {
            isRefreshing.value = false
        }
    }

    @OptIn(FlowPreview::class)
    fun subscribeTo(user: User) {
        if (currentUser.value != user) {
            currentUser.tryEmit(user)
        }
    }

    fun unsubscribeTo(user: User) {
        if (currentUser.value == user) {
            currentUser.tryEmit(null)
            invalidateData()
        }
    }

    override fun invalidateData(ignoreIfDoing: Boolean) {
        currentUser.tryEmit(currentUser.value)
    }

    override fun onCleared() {
        Log.d("Init") { "OnCleared: ${this::class.simpleName}" }
    }
}
