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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.indexer

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.relays.ui.CountFilter
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.profiles
import com.vitorpamplona.amethyst.commons.resources.relay_settings_lower2
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.common.BasicRelaySetupInfoModel
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent

@Stable
class IndexerRelayListViewModel : BasicRelaySetupInfoModel() {
    override fun getRelayList(): List<NormalizedRelayUrl> =
        account.indexerRelayList.flowNoDefaults.value
            .toList()

    override suspend fun saveRelayList(urlList: List<NormalizedRelayUrl>) {
        account.saveIndexerRelayList(urlList)
    }

    override fun countFilters(relayUrl: NormalizedRelayUrl): List<CountFilter> =
        listOf(
            CountFilter(
                label = Res.string.profiles,
                filter = Filter(kinds = listOf(MetadataEvent.KIND)),
            ),
            CountFilter(
                label = Res.string.relay_settings_lower2,
                filter = Filter(kinds = listOf(AdvertisedRelayListEvent.KIND)),
            ),
        )
}
