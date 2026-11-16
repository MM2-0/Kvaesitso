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
package com.vitorpamplona.amethyst.commons.service

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.nip47WalletConnect.NwcSignerState
import com.vitorpamplona.amethyst.commons.model.payments.PaymentSource
import com.vitorpamplona.amethyst.commons.notices.ClinkDebitFailed
import com.vitorpamplona.amethyst.commons.notices.InvoiceNotice
import com.vitorpamplona.amethyst.commons.notices.NwcFailure
import com.vitorpamplona.amethyst.commons.notices.UserNotice
import com.vitorpamplona.amethyst.commons.notices.V4VNotice
import com.vitorpamplona.amethyst.commons.notices.nwcFailure
import com.vitorpamplona.amethyst.commons.service.lnurl.LightningInvoiceResolver
import com.vitorpamplona.amethyst.commons.service.lnurl.LnurlHttpTransport
import com.vitorpamplona.amethyst.commons.tor.MoneyOpRelayRouting
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.NwcMethod
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.PayKeysendMethod
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.Response
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.TlvRecord
import com.vitorpamplona.quartz.nip57Zaps.ZapReceiptEvent
import com.vitorpamplona.quartz.nip57Zaps.validate.LnurlForm
import com.vitorpamplona.quartz.podcasts.PodcastBoostagram
import com.vitorpamplona.quartz.podcasts.PodcastValue
import com.vitorpamplona.quartz.podcasts.PodcastValueShare
import com.vitorpamplona.quartz.utils.mapNotNullAsync
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Executes a Podcasting-2.0 value-for-value (V4V) split: takes a [PodcastValue] block and a total
 * amount, computes each recipient's share ([PodcastValue.computeShares]) and pays them.
 *
 * This is the V4V analogue of [ZapPaymentHandler]. The recipients are raw Lightning destinations
 * declared in the value block, but the two kinds are paid very differently:
 *
 * - [PodcastValue.TYPE_LNADDRESS] — resolved to a BOLT-11 via LNURL-pay and paid through the user's
 *   default payment source (NWC, CLINK debit, or — when none is set — handed to an external wallet
 *   via [onPayInvoicesViaIntent]). Same rails as a zap, and when `asZap` is set each share also
 *   carries a NIP-57 zap request, so a Nostr-aware lnaddress provider issues a real **zap receipt**
 *   (this is what lets the standard zap button drive a V4V split with its usual icon/counter UI).
 *   Per-minute streaming pays with `asZap = false` to avoid publishing a receipt every minute.
 * - [PodcastValue.TYPE_NODE] — paid by **keysend** (NIP-47 `pay_keysend`) carrying the Podcasting-2.0
 *   boostagram TLV ([PodcastValue.PODCAST_TLV_RECORD]) plus any per-recipient custom TLV. There is no
 *   LNURL endpoint and no invoice, so keysend can never produce a zap receipt regardless of `asZap`.
 *   Keysend is only available over NWC, so node recipients are skipped (with an error) when no NWC
 *   wallet is set up.
 */
class V4VPaymentHandler(
    val account: Account,
    private val moneyOpRelays: MoneyOpRelayRouting,
) {
    /** A resolved lnaddress share ready to pay: the share plus the BOLT-11 fetched for it. */
    class InvoicePayable(
        val share: PodcastValueShare,
        val invoice: String,
    )

    suspend fun pay(
        value: PodcastValue,
        totalMilliSats: Long,
        boostagram: PodcastBoostagram,
        zappedNote: Note?,
        lnurl: LnurlHttpTransport,
        onError: (UserNotice) -> Unit,
        onProgress: (percent: Float) -> Unit,
        onPayInvoicesViaIntent: (invoices: List<String>) -> Unit,
        asZap: Boolean = false,
        zapType: ZapReceiptEvent.ZapType = ZapReceiptEvent.ZapType.PUBLIC,
    ) = withContext(Dispatchers.IO) {
        val shares = value.computeShares(totalMilliSats)
        if (shares.isEmpty()) {
            onError(V4VNotice.NoRecipients)
            return@withContext
        }

        val nodeShares = shares.filter { it.recipient.type == PodcastValue.TYPE_NODE }
        val lnAddressShares = shares.filter { it.recipient.type == PodcastValue.TYPE_LNADDRESS }

        onProgress(0.05f)

        // Keysend (node) recipients can only be paid over NWC.
        if (nodeShares.isNotEmpty()) {
            if (account.nip47SignerState.hasWalletConnectSetup()) {
                if (defaultWalletMayKeysend()) {
                    payNodeSharesViaKeysend(nodeShares, boostagram, onError)
                } else {
                    onError(V4VNotice.KeysendNotSupported)
                }
            } else {
                onError(V4VNotice.KeysendRequiresNwc)
            }
        }

        if (lnAddressShares.isNotEmpty()) {
            val payables =
                assembleInvoices(
                    shares = lnAddressShares,
                    message = boostagram.message.orEmpty(),
                    asZap = asZap,
                    zapType = zapType,
                    zappedNote = zappedNote,
                    lnurl = lnurl,
                    onError = onError,
                    onProgress = { onProgress(it * 0.6f + 0.1f) },
                )
            payInvoices(payables, zappedNote, onError, onPayInvoicesViaIntent) {
                onProgress(it * 0.25f + 0.7f)
            }
        }

        onProgress(1f)
    }

    /**
     * `pay_keysend` lives in the NWC-04 extension. Only a wallet whose info event publishes an
     * `extensions` tag without 04 (and doesn't list `pay_keysend` in its content) is skipped;
     * a legacy wallet, or one whose info is unknown, is still asked, as before extensions
     * existed. See [com.vitorpamplona.quartz.nip47WalletConnect.events.NwcInfoEvent.mayUseExtensionMethod].
     */
    private suspend fun defaultWalletMayKeysend(): Boolean {
        val walletUri = account.nip47SignerState.defaultWalletUri.value ?: return true
        val info = withTimeoutOrNull(NwcSignerState.NIP44_NEGOTIATION_WAIT_MS) { account.nwcInfoCache.currentOrFetch(walletUri) }
        return info?.mayUseExtensionMethod(NwcMethod.PAY_KEYSEND) != false
    }

    /** Hex-encodes a TLV value string as NIP-47 `pay_keysend` requires (UTF-8 bytes → hex). */
    private suspend fun hexTlv(value: String): String = value.encodeToByteArray().toHexKey()

    private suspend fun payNodeSharesViaKeysend(
        shares: List<PodcastValueShare>,
        boostagram: PodcastBoostagram,
        onError: (UserNotice) -> Unit,
    ) {
        val metadataTlv = TlvRecord(PodcastValue.PODCAST_TLV_RECORD, hexTlv(boostagram.toJson()))

        shares.forEach { share ->
            val pubkey = share.recipient.address ?: return@forEach

            val tlvRecords = mutableListOf(metadataTlv)
            val customType = share.recipient.customKey?.toLongOrNull()
            val customValue = share.recipient.customValue
            if (customType != null && customValue != null) {
                tlvRecords.add(TlvRecord(customType, hexTlv(customValue)))
            }

            val request =
                PayKeysendMethod.create(
                    amount = share.amountMilliSats,
                    pubkey = pubkey,
                    tlvRecords = tlvRecords,
                )

            account.zaps.sendNwcRequest(
                request = request,
                onResponse = { response: Response? ->
                    response.nwcFailure()?.let { onError(V4VNotice.PaymentFailed(it)) }
                },
                onTimeout = { onError(V4VNotice.PaymentFailed(NwcFailure.TimedOut)) },
            )
        }
    }

    private suspend fun assembleInvoices(
        shares: List<PodcastValueShare>,
        message: String,
        asZap: Boolean,
        zapType: ZapReceiptEvent.ZapType,
        zappedNote: Note?,
        lnurl: LnurlHttpTransport,
        onError: (UserNotice) -> Unit,
        onProgress: (percent: Float) -> Unit,
    ): List<InvoicePayable> {
        // When paying as a zap, attach a NIP-57 request to each share so the recipient's LNURL
        // provider mints a zappable invoice and publishes a receipt. The receipt is attributed to
        // the zapped note (toUser = null) since a value-block lnaddress is a raw payee, not
        // necessarily a Nostr identity. Send to the show/episode author's inbox so they see it.
        val noteEvent = zappedNote?.event
        val authorRelays = zappedNote?.author?.inboxRelays()?.toSet() ?: emptySet()

        var progress = 0f
        return mapNotNullAsync(shares) { share: PodcastValueShare ->
            val lnAddress = share.recipient.address ?: return@mapNotNullAsync null
            try {
                val nostrRequest =
                    if (asZap && noteEvent != null) {
                        account.zaps.createZapRequestFor(
                            event = noteEvent,
                            pollOption = null,
                            message = message,
                            zapType = zapType,
                            toUser = null,
                            additionalRelays = authorRelays,
                            amountMillisats = share.amountMilliSats,
                            lnurl = LnurlForm.toUrl(lnAddress)?.let(LnurlForm::urlToBech32),
                        )
                    } else {
                        null
                    }

                val invoice =
                    LightningInvoiceResolver(lnurl).lnAddressInvoice(
                        lnAddress = lnAddress,
                        milliSats = share.amountMilliSats,
                        message = message,
                        nostrRequest = nostrRequest,
                        onProgress = {},
                    )
                progress += 1f / shares.size
                onProgress(progress)
                InvoicePayable(share, invoice)
            } catch (e: LightningInvoiceResolver.LightningAddressError) {
                onError(e.notice)
                null
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                onError(InvoiceNotice.Unexpected(e.message))
                null
            }
        }
    }

    private suspend fun payInvoices(
        payables: List<InvoicePayable>,
        zappedNote: Note?,
        onError: (UserNotice) -> Unit,
        onPayInvoicesViaIntent: (List<String>) -> Unit,
        onProgress: (percent: Float) -> Unit,
    ) {
        if (payables.isEmpty()) return

        when (val source = account.settings.defaultPaymentSource()) {
            is PaymentSource.Nwc -> {
                var done = 0
                payables.forEach { payable ->
                    account.zaps.sendZapPaymentRequestFor(
                        bolt11 = payable.invoice,
                        zappedNote = zappedNote,
                        onResponse = { response ->
                            response.nwcFailure()?.let { onError(V4VNotice.PaymentFailed(it)) }
                        },
                        onTimeout = { onError(V4VNotice.PaymentFailed(NwcFailure.TimedOut)) },
                    )
                    done++
                    onProgress(done.toFloat() / payables.size)
                }
            }

            is PaymentSource.ClinkDebit -> {
                var done = 0
                payables.forEach { payable ->
                    val response = ClinkDebitPayer.payInvoice(account, moneyOpRelays, source.wallet.pointer, payable.invoice)
                    if (response?.isOk() != true) {
                        onError(ClinkDebitFailed(response?.failureDetail()))
                    }
                    done++
                    onProgress(done.toFloat() / payables.size)
                }
            }

            null -> {
                onPayInvoicesViaIntent(payables.map { it.invoice })
                onProgress(1f)
            }
        }
    }
}
