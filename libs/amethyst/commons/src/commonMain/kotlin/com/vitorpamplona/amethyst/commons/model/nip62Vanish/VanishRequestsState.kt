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
package com.vitorpamplona.amethyst.commons.model.nip62Vanish

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.taggedAs
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchFirst
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip62RequestToVanish.RequestToVanishEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

@Stable
data class VanishEventItem(
    val event: RequestToVanishEvent,
    val relays: List<NormalizedRelayUrl>,
    val isAllRelays: Boolean,
    val complianceResults: MutableStateFlow<Map<NormalizedRelayUrl, ComplianceStatus>> =
        MutableStateFlow(
            relays.associateWith { ComplianceStatus.UNTESTED },
        ),
)

enum class ComplianceStatus {
    UNTESTED,
    TESTING,
    COMPLIANT,
    NON_COMPLIANT,
    ERROR,
}

class VanishRequestsState(
    val signer: NostrSigner,
    val cache: LocalCache,
    val client: INostrClient,
    val scope: CoroutineScope,
) {
    val noteFlow =
        cache
            .observeNotes(
                Filter(
                    kinds = listOf(RequestToVanishEvent.KIND),
                    authors = listOf(signer.pubKey),
                ),
            ).stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = emptyList(),
            )

    val testableFlow =
        noteFlow
            .map { notes ->
                notes.mapNotNull {
                    val noteEvent = it.event
                    if (noteEvent is RequestToVanishEvent) {
                        VanishEventItem(noteEvent, noteEvent.vanishFromRelays(), noteEvent.vanishFromAllRelays())
                    } else {
                        null
                    }
                }
            }.stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(10000),
                initialValue = emptyList(),
            )

    suspend fun testVanishCompliance(
        item: VanishEventItem,
        relay: NormalizedRelayUrl,
    ) {
        item.complianceResults.update {
            it + (relay to ComplianceStatus.TESTING)
        }

        try {
            val foundEvent =
                withContext(Dispatchers.IO) {
                    client.taggedAs(SubPurpose.RELAY_INFO, "Vanish compliance check").fetchFirst(
                        relay = relay,
                        filter =
                            Filter(
                                authors = listOf(item.event.pubKey),
                                until = item.event.createdAt - 1,
                                limit = 1,
                            ),
                    )
                }

            item.complianceResults.update {
                it + (
                    relay to
                        if (foundEvent != null) {
                            ComplianceStatus.NON_COMPLIANT
                        } else {
                            ComplianceStatus.COMPLIANT
                        }
                )
            }
        } catch (e: Exception) {
            // A cancelled check has no result. Reporting ERROR would show the relay as
            // having answered badly when it was never asked.
            if (e is CancellationException) throw e
            item.complianceResults.update {
                it + (relay to ComplianceStatus.ERROR)
            }
        }
    }
}
