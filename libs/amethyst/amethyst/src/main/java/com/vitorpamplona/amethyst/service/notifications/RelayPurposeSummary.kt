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
package com.vitorpamplona.amethyst.service.notifications

import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.purposes
import com.vitorpamplona.amethyst.commons.relays.ui.SubPurposeLabels
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.relay_purpose_browsing
import com.vitorpamplona.amethyst.commons.resources.relay_purpose_line
import com.vitorpamplona.amethyst.commons.resources.relay_purpose_sending
import com.vitorpamplona.amethyst.commons.ui.loadPluralStringRes
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.Log

/**
 * The per-job breakdown behind the always-on notification's relay count.
 *
 * **Only ever shown on request.** The card stays exactly what it was — a count — because that is all
 * most people ever want from an ongoing notification. This is for the moment someone taps
 * "show details" to ask *why* their phone is talking to 40 relays. It is not attached to the
 * notification otherwise: Android auto-expands a lone notification, so anything hung off the
 * expanded view alone would be the default view rather than an opt-in one.
 *
 * A relay usually serves several jobs at once (measured: a typical relay carries four), so these
 * counts deliberately **overlap and sum to more than the relay count**. They answer "how many relays
 * carry my DMs", not "how is the pool partitioned" — there is no partition.
 *
 * Purposes with no label — feeds and whatever screen is open — collapse into one "browsing" line.
 * Those disappear on their own once the app is backgrounded, which is exactly when this notification
 * matters most, so spelling them out would add noise precisely when the user is least interested.
 *
 * A relay with no tagged REQ can still be connected for a real reason: an event of ours is waiting
 * for its OK there. Those get their own "sending" line. Anything still left over is a subscription
 * that never said why it exists — a plain `Filter` that should have been an `ExplainedFilter` — so
 * it is logged with its subscription ids and kinds, which is what finds the producer, rather than shown as a vague
 * "other" line that would hide it.
 */
object RelayPurposeSummary {
    private const val TAG = "RelayPurposeSummary"

    /**
     * Lines for the expanded notification, busiest first. Empty when nothing is attributed yet —
     * the caller must then fall back to the bare count rather than render an empty section.
     */
    suspend fun lines(): List<String> {
        val client = Amethyst.instance.client
        val named = mutableMapOf<SubPurpose, MutableSet<NormalizedRelayUrl>>()
        val browsing = mutableSetOf<NormalizedRelayUrl>()
        val sending = mutableSetOf<NormalizedRelayUrl>()

        client.connectedRelaysFlow().value.forEach { relay ->
            // COUNTs hold a relay open just as REQs do, and are tagged the same way.
            val requests = client.activeRequests(relay)
            val counts = client.activeCounts(relay)
            val purposes = requests.values.flatten().purposes() + counts.values.flatten().purposes()

            purposes.forEach { purpose ->
                if (SubPurposeLabels.isWorthNamingInNotification(purpose)) {
                    named.getOrPut(purpose) { mutableSetOf() }.add(relay)
                } else {
                    browsing.add(relay)
                }
            }

            if (purposes.isEmpty()) {
                if (client.activeOutboxEvents(relay).isNotEmpty()) {
                    sending.add(relay)
                } else {
                    // Sub ids and kinds name the producer; the filters themselves carry pubkeys.
                    Log.d(TAG) {
                        "$relay is connected with no purpose: reqs=${requests.describe()} counts=${counts.describe()}"
                    }
                }
            }
        }

        val lines =
            named.entries
                .sortedWith(compareByDescending<Map.Entry<SubPurpose, Set<NormalizedRelayUrl>>> { it.value.size }.thenBy { it.key.name })
                .map { (purpose, relays) ->
                    loadPluralStringRes(Res.plurals.relay_purpose_line, relays.size, loadStringRes(SubPurposeLabels.labelOf(purpose)), relays.size)
                }.toMutableList()

        if (browsing.isNotEmpty()) {
            lines.add(loadPluralStringRes(Res.plurals.relay_purpose_line, browsing.size, loadStringRes(Res.string.relay_purpose_browsing), browsing.size))
        }
        if (sending.isNotEmpty()) {
            lines.add(loadPluralStringRes(Res.plurals.relay_purpose_line, sending.size, loadStringRes(Res.string.relay_purpose_sending), sending.size))
        }
        return lines
    }

    private fun Map<String, List<Filter>>.describe(): String = entries.joinToString { (subId, filters) -> "$subId${filters.flatMap { it.kinds.orEmpty() }.toSet()}" }
}
