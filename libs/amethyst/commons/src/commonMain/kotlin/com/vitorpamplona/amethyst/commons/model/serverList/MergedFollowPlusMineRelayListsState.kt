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
package com.vitorpamplona.amethyst.commons.model.serverList

import com.vitorpamplona.amethyst.commons.model.edits.PrivateStorageRelayListState
import com.vitorpamplona.amethyst.commons.model.localRelays.LocalRelayListState
import com.vitorpamplona.amethyst.commons.model.nip02FollowLists.FollowListOutboxOrProxyRelays
import com.vitorpamplona.amethyst.commons.model.nip65RelayList.Nip65RelayListState
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

class MergedFollowPlusMineRelayListsState(
    val followsOutboxOrProxyRelayList: FollowListOutboxOrProxyRelays,
    val nip65RelayList: Nip65RelayListState,
    val privateOutboxRelayList: PrivateStorageRelayListState,
    val localRelayList: LocalRelayListState,
    val scope: CoroutineScope,
) {
    fun mergeLists(lists: Array<Set<NormalizedRelayUrl>>): Set<NormalizedRelayUrl> = lists.reduce { acc, set -> acc + set }

    val flow: StateFlow<Set<NormalizedRelayUrl>> =
        combine(
            listOf(
                followsOutboxOrProxyRelayList.flow,
                nip65RelayList.outboxFlow,
                nip65RelayList.inboxFlow,
                privateOutboxRelayList.flow,
                localRelayList.flow,
            ),
            ::mergeLists,
        ).onStart {
            emit(
                mergeLists(
                    arrayOf(
                        followsOutboxOrProxyRelayList.flow.value,
                        nip65RelayList.outboxFlow.value,
                        nip65RelayList.inboxFlow.value,
                        privateOutboxRelayList.flow.value,
                        localRelayList.flow.value,
                    ),
                ),
            )
        }.flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                mergeLists(
                    arrayOf(
                        followsOutboxOrProxyRelayList.flow.value,
                        nip65RelayList.outboxFlow.value,
                        nip65RelayList.inboxFlow.value,
                        privateOutboxRelayList.flow.value,
                        localRelayList.flow.value,
                    ),
                ),
            )
}
