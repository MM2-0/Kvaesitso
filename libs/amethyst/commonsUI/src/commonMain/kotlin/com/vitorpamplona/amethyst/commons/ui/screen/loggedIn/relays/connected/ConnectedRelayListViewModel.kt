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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.connected

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.common.BasicRelaySetupInfo
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.common.BasicRelaySetupInfoModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip56Reports.ReportEvent

@Stable
class ConnectedRelayListViewModel : BasicRelaySetupInfoModel() {
    override fun relayListBuilder(): List<BasicRelaySetupInfo> {
        val relayList = getRelayList()

        return relayList
            .map {
                val reqs = account.client.activeRequests(it)

                val users = mutableSetOf<HexKey>()
                reqs.forEach { (_, filters) ->
                    filters.forEach { filter ->
                        val isReportFilter = filter.kinds?.size == 1 && filter.kinds?.firstOrNull() == ReportEvent.KIND
                        if (!isReportFilter) {
                            filter.authors?.forEach { pubkey ->
                                users.add(pubkey)
                            }
                            filter.tags?.get("p")?.forEach { pubkey ->
                                users.add(pubkey)
                            }
                        }
                    }
                }

                BasicRelaySetupInfo(
                    relay = it,
                    relayStat = accountViewModel.host.relayStats.get(it),
                    forcesTor =
                        accountViewModel.host.torRelayEvaluation.value
                            .useTor(it),
                    users = users.mapNotNull { hex -> LocalCache.checkGetOrCreateUser(hex) },
                )
            }.distinctBy { it.relay }
            .sortedBy { it.relayStat.receivedBytes }
            .reversed()
    }

    override fun getRelayList(): List<NormalizedRelayUrl> =
        account.client
            .availableRelaysFlow()
            .value
            .sorted()

    override suspend fun saveRelayList(urlList: List<NormalizedRelayUrl>) {
        // Connected relays are read-only; saving is not applicable
    }
}
