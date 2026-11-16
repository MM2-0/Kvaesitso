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
package com.vitorpamplona.amethyst.commons.actions

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip57Zaps.splits.ZapSplitSetup
import com.vitorpamplona.quartz.nip57Zaps.splits.ZapSplitSetupLnAddress
import com.vitorpamplona.quartz.nip57Zaps.splits.zapSplitSetup
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent
import kotlin.math.round

/**
 * Resolves the set of recipients for a NIP-57 zap on a given event.
 *
 * Mirrors the split-resolution logic in Amethyst's
 * `service/ZapPaymentHandler.kt` (Android) so non-UI callers — amy CLI,
 * Gemini App Functions adapter, automation scripts — pay the same
 * recipients the in-app flow would. Without this resolver, a naive
 * "zap the event author" path silently misroutes funds on any note that
 * carries `zap` tags, live-activity host tags, or app-definition metadata.
 *
 * The resolution order matches Amethyst:
 *  1. NIP-57 zap-split tags on the event (`["zap", ...]`).
 *  2. NIP-53 live-activity hosts (kind:30311 only).
 *  3. NIP-89 app definition's own LN address (kind:31990 only).
 *  4. The event author as the sole recipient.
 *
 * Recipients without a resolvable LN address are dropped silently — the
 * caller is responsible for surfacing that to the user. This matches the
 * `mapNotNull` shape of the in-app flow.
 */
object ZapSplitResolver {
    /**
     * One zap recipient. The total payment is divided among recipients
     * proportional to [weight] / sum(weights); [shareMillisats] applies
     * the same rounding the in-app flow does.
     */
    data class Recipient(
        /** LN address ready to hand to [shareMillisats] + an LNURL-pay flow. */
        val lnAddress: String,
        /** Pubkey of the recipient, or null when the split tag carried only an LN address. */
        val pubkey: HexKey?,
        /** Relative weight in the split. 1.0 when not otherwise specified. */
        val weight: Double,
        /** Relay hint the split tag carried, if any — for receipt routing. */
        val relay: NormalizedRelayUrl?,
    )

    /**
     * Rounds a per-split share to whole sats (millisats granularity of 1_000).
     * Matches `ZapPaymentHandler.calculateZapValue` so sums line up exactly
     * with what an Amethyst user would see on-screen.
     */
    fun shareMillisats(
        totalMillisats: Long,
        weight: Double,
        totalWeight: Double,
    ): Long {
        if (totalWeight <= 0.0) return 0L
        val shareValue = totalMillisats * (weight / totalWeight)
        return round(shareValue / 1000f).toLong() * 1000
    }

    /**
     * Resolve the list of zap recipients for [zappedEvent].
     *
     * @param lookupLnAddress called to resolve a pubkey to an LN address. For
     *   amy this reads kind:0 metadata from the local store; for the Android
     *   adapter it reads `User.lnAddress()` from the live cache. Return null
     *   when no LN address is known — the recipient is dropped.
     *
     * @return ordered list of recipients with LN addresses resolved. Empty
     *   list when no recipient has a usable LN address.
     */
    suspend fun resolve(
        zappedEvent: Event,
        lookupLnAddress: suspend (HexKey) -> String?,
    ): List<Recipient> {
        val splits = zappedEvent.zapSplitSetup()

        val raw: List<Recipient?> =
            when {
                splits.isNotEmpty() ->
                    splits.map { setup ->
                        when (setup) {
                            is ZapSplitSetupLnAddress ->
                                Recipient(
                                    lnAddress = setup.lnAddress,
                                    pubkey = null,
                                    weight = setup.weight,
                                    relay = null,
                                )
                            is ZapSplitSetup -> {
                                val ln = lookupLnAddress(setup.pubKeyHex)
                                if (ln != null) {
                                    Recipient(
                                        lnAddress = ln,
                                        pubkey = setup.pubKeyHex,
                                        weight = setup.weight,
                                        relay = setup.relay,
                                    )
                                } else {
                                    null
                                }
                            }
                        }
                    }

                zappedEvent is LiveActivitiesEvent && zappedEvent.hasHost() ->
                    zappedEvent.hosts().map { host ->
                        val ln = lookupLnAddress(host.pubKey)
                        if (ln != null) {
                            Recipient(
                                lnAddress = ln,
                                pubkey = host.pubKey,
                                weight = 1.0,
                                relay = host.relayHint,
                            )
                        } else {
                            null
                        }
                    }

                zappedEvent is AppDefinitionEvent -> {
                    val appLn = zappedEvent.appMetaData()?.lnAddress()
                    val ln = appLn ?: lookupLnAddress(zappedEvent.pubKey)
                    if (ln != null) {
                        listOf(
                            Recipient(
                                lnAddress = ln,
                                // appMetaData has no pubkey association; only attribute when we fell back to the author.
                                pubkey = if (appLn == null) zappedEvent.pubKey else null,
                                weight = 1.0,
                                relay = null,
                            ),
                        )
                    } else {
                        listOf(null)
                    }
                }

                else -> {
                    val ln = lookupLnAddress(zappedEvent.pubKey)
                    if (ln != null) {
                        listOf(
                            Recipient(
                                lnAddress = ln,
                                pubkey = zappedEvent.pubKey,
                                weight = 1.0,
                                relay = null,
                            ),
                        )
                    } else {
                        listOf(null)
                    }
                }
            }

        return raw.filterNotNull()
    }
}
