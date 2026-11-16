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
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip57Zaps.ZapReceiptEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapRequestEvent

/**
 * NIP-57 zap-request building + LN address extraction.
 *
 * Returns a signed [ZapRequestEvent] (kind:9734) — the artifact a caller
 * hands to a LNURL-pay callback to receive a BOLT11 invoice.
 *
 * **Caller responsibilities** that the Amethyst Android flow handles but
 * these builders do not:
 *
 *  * **Use [buildEventZapRequestsForSplits] for events.** A naive call to
 *    [buildEventZapRequest] on a note carrying NIP-57 zap-split tags, NIP-53
 *    live-activity host tags, or NIP-89 app metadata silently misroutes
 *    funds to a single recipient. The splits variant is what the
 *    foreground UI uses and what amy `zap event` calls.
 *  * **Lightning round-trip.** LNURL endpoint fetch, BOLT11 invoice
 *    retrieval, and optional NIP-47 NWC payment all live outside these
 *    builders. `LightningAddressResolver` (in commons/jvmAndroid) covers
 *    the LNURL + invoice steps.
 *  * **Receipt verification.** When the kind:9735 receipt arrives, validate
 *    it against the LNURL provider's `nostrPubkey` (NIP-57 Appendix F) —
 *    primed via `LnurlEndpointCache` on Android.
 *  * **Onchain zaps** (NIP-BC) are a separate flow — see `OnchainZapSender`
 *    in commons. These builders only cover Lightning.
 *
 * Pattern matches [FollowActions] and [SearchActions]: shared, pure logic
 * usable from amy CLI, the Android App Functions adapter for Gemini, and
 * any other non-UI consumer.
 */
object ZapActions {
    /** Convert sats to millisats — LN-side amount unit. */
    fun satsToMillisats(sats: Long): Long = sats * 1000L

    /**
     * Extract the LN address (Lightning Address or LNURL) from a kind:0
     * metadata event. Prefers `lud16` (Lightning Address, `user@domain`)
     * over `lud06` (raw LNURL). Returns null when the user has no LN
     * details published.
     */
    fun extractLnAddress(metadata: MetadataEvent): String? = metadata.contactMetaData()?.lnAddress()

    /**
     * Build a NIP-57 profile zap request — pays [recipientPubkey] directly,
     * not attached to any specific event.
     *
     * [inboxRelays] becomes the `["relays", ...]` tag of the zap request:
     * the LN provider publishes the kind:9735 zap *receipt* to these
     * relays. These should be the sender's read-side (NIP-65 inbox)
     * relays so the sender's clients see the receipt land.
     *
     * Pass [lnurl] when known to stamp it as a tag on the request — some
     * receipt validators key off it.
     */
    suspend fun buildUserZapRequest(
        signer: NostrSigner,
        recipientPubkey: HexKey,
        amountMillisats: Long,
        inboxRelays: Set<NormalizedRelayUrl>,
        comment: String = "",
        zapType: ZapReceiptEvent.ZapType = ZapReceiptEvent.ZapType.PUBLIC,
        lnurl: String? = null,
    ): ZapRequestEvent =
        ZapRequestEvent.create(
            userHex = recipientPubkey,
            relays = inboxRelays,
            signer = signer,
            message = comment,
            zapType = zapType,
            amountMillisats = amountMillisats,
            lnurl = lnurl,
        )

    /**
     * Build a NIP-57 event-zap request — pays the author of
     * [zappedEvent] in the context of that specific event. Override
     * [toUserPubkey] when the payment should go to a co-author or
     * delegated recipient (zap splits); when null the zap targets
     * `zappedEvent.pubKey`.
     *
     * **Caller beware:** This builds a single zap request to a single
     * recipient. Notes carrying NIP-57 zap-split tags, NIP-53
     * live-activity hosts, or NIP-89 app metadata expect the payment to
     * be divided across multiple parties. Use [buildEventZapRequestsForSplits]
     * for the split-aware path; that's what the Amethyst foreground UI does.
     */
    suspend fun buildEventZapRequest(
        signer: NostrSigner,
        zappedEvent: Event,
        amountMillisats: Long,
        inboxRelays: Set<NormalizedRelayUrl>,
        comment: String = "",
        zapType: ZapReceiptEvent.ZapType = ZapReceiptEvent.ZapType.PUBLIC,
        toUserPubkey: HexKey? = null,
        pollOption: Int? = null,
        lnurl: String? = null,
    ): ZapRequestEvent =
        ZapRequestEvent.create(
            zappedEvent = zappedEvent,
            relays = inboxRelays,
            signer = signer,
            pollOption = pollOption,
            message = comment,
            zapType = zapType,
            toUserPubHex = toUserPubkey,
            amountMillisats = amountMillisats,
            lnurl = lnurl,
        )

    /**
     * One signed zap request for one split recipient, with the share of
     * the total payment already computed.
     */
    data class ZapRequestForSplit(
        val recipient: ZapSplitResolver.Recipient,
        val amountMillisats: Long,
        val request: ZapRequestEvent,
    )

    /**
     * Split-aware version of [buildEventZapRequest]: resolves the recipient
     * list via [ZapSplitResolver], computes per-recipient shares with
     * [ZapSplitResolver.shareMillisats] (rounded to whole sats — matches the
     * Amethyst UI), and signs one zap request per recipient.
     *
     * Each request's relay-list tag includes [senderInboxRelays] union the
     * recipient's own inbox relays (resolved via [lookupInboxRelays]), so
     * the eventual kind:9735 zap receipt is published to both parties'
     * read-side relays. This matches `ZapPaymentHandler.signAllZapRequests`.
     *
     * Sum of returned `amountMillisats` may differ from [totalAmountMillisats]
     * by a few hundred millisats due to whole-sat rounding — same drift the
     * in-app flow has.
     *
     * Recipients with no resolvable LN address are dropped at the resolver
     * step; callers that want to surface "missing LN" warnings should call
     * [ZapSplitResolver.resolve] separately first.
     */
    suspend fun buildEventZapRequestsForSplits(
        signer: NostrSigner,
        zappedEvent: Event,
        totalAmountMillisats: Long,
        senderInboxRelays: Set<NormalizedRelayUrl>,
        lookupLnAddress: suspend (HexKey) -> String?,
        lookupInboxRelays: suspend (HexKey) -> Set<NormalizedRelayUrl> = { emptySet() },
        comment: String = "",
        zapType: ZapReceiptEvent.ZapType = ZapReceiptEvent.ZapType.PUBLIC,
        pollOption: Int? = null,
    ): List<ZapRequestForSplit> {
        val recipients = ZapSplitResolver.resolve(zappedEvent, lookupLnAddress)
        if (recipients.isEmpty()) return emptyList()
        val totalWeight = recipients.sumOf { it.weight }

        // Author inbox always travels with the zap so the author's clients
        // see the receipt even when paying a split recipient. Mirrors the
        // `authorRelayList + userRelayList` union in ZapPaymentHandler.
        val authorInbox = lookupInboxRelays(zappedEvent.pubKey)

        return recipients.map { recipient ->
            val share = ZapSplitResolver.shareMillisats(totalAmountMillisats, recipient.weight, totalWeight)
            val recipientInbox = recipient.pubkey?.let { lookupInboxRelays(it) }.orEmpty()
            val allRelays = senderInboxRelays + recipientInbox + authorInbox
            val request =
                ZapRequestEvent.create(
                    zappedEvent = zappedEvent,
                    relays = allRelays,
                    signer = signer,
                    pollOption = pollOption,
                    message = comment,
                    zapType = zapType,
                    toUserPubHex = recipient.pubkey,
                    amountMillisats = share,
                    lnurl = null,
                )
            ZapRequestForSplit(recipient = recipient, amountMillisats = share, request = request)
        }
    }
}
