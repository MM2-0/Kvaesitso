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

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.payments.PaymentSource
import com.vitorpamplona.amethyst.commons.notices.ClinkDebitFailed
import com.vitorpamplona.amethyst.commons.notices.InvoiceNotice
import com.vitorpamplona.amethyst.commons.notices.NwcFailure
import com.vitorpamplona.amethyst.commons.notices.UserNotice
import com.vitorpamplona.amethyst.commons.notices.UserNoticeResolver
import com.vitorpamplona.amethyst.commons.notices.ZapNotice
import com.vitorpamplona.amethyst.commons.notices.nwcFailure
import com.vitorpamplona.amethyst.commons.notices.report
import com.vitorpamplona.amethyst.commons.service.lnurl.LightningInvoiceResolver
import com.vitorpamplona.amethyst.commons.service.lnurl.LnurlHttpTransport
import com.vitorpamplona.amethyst.commons.tor.MoneyOpRelayRouting
import com.vitorpamplona.quartz.experimental.clink.pointers.NDebit
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.NwcErrorCode
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.NwcTransactionMetadata
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapReceiptEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapRequestEvent
import com.vitorpamplona.quartz.nip57Zaps.splits.ZapSplitSetup
import com.vitorpamplona.quartz.nip57Zaps.splits.ZapSplitSetupLnAddress
import com.vitorpamplona.quartz.nip57Zaps.splits.zapSplitSetup
import com.vitorpamplona.quartz.nip57Zaps.validate.LnurlForm
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.mapNotNullAsync
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.math.round

class ZapPaymentHandler(
    val account: Account,
    private val moneyOpRelays: MoneyOpRelayRouting,
    private val notices: UserNoticeResolver,
) {
    @Immutable
    data class Payable(
        val info: MyZapSplitSetup,
        val amountMilliSats: Long,
        val invoice: String,
        // The signed kind 9734 this invoice was fetched with, and the message on it.
        // Carried so the NWC payment can name the payee (NWC-06 `metadata`); null for
        // a NONZAP split, which has no zap request to send.
        val zapRequest: ZapRequestEvent? = null,
        val message: String = "",
    )

    data class UnverifiedZapSplitSetup(
        val lnAddress: String?,
        val weight: Double = 1.0,
        val relay: NormalizedRelayUrl? = null,
        val user: User? = null,
        val bolt12Offer: String? = null,
    )

    data class MyZapSplitSetup(
        val lnAddress: String,
        val weight: Double = 1.0,
        val relay: NormalizedRelayUrl? = null,
        val user: User? = null,
    )

    /**
     * A recipient routed over BOLT12 (NIP-B1): they publish a kind:10058 [offer] and we
     * hold an NWC wallet. [lnAddress] is their BOLT11 route, kept so a refused offer can
     * fall back to a regular zap (see [payViaBolt12]); null when they publish none.
     */
    data class Bolt12Recipient(
        val user: User,
        val offer: String,
        val weight: Double = 1.0,
        val lnAddress: String? = null,
        val relay: NormalizedRelayUrl? = null,
    )

    suspend fun zap(
        note: Note,
        amountMilliSats: Long,
        pollOption: Int?,
        message: String,
        showErrorIfNoLnAddress: Boolean,
        lnurl: LnurlHttpTransport,
        onError: (String, String, User?) -> Unit,
        onProgress: (percent: Float) -> Unit,
        onPayViaIntent: (ImmutableList<Payable>) -> Unit,
        zapType: ZapReceiptEvent.ZapType,
    ) = withContext(Dispatchers.IO) {
        val noteEvent = note.event
        val zapSplitSetup = noteEvent?.zapSplitSetup()

        val unverifiedZapsToSend =
            when {
                !zapSplitSetup.isNullOrEmpty() -> {
                    zapSplitSetup.map { setup ->
                        when (setup) {
                            is ZapSplitSetupLnAddress -> {
                                UnverifiedZapSplitSetup(
                                    lnAddress = setup.lnAddress,
                                    weight = setup.weight,
                                )
                            }

                            is ZapSplitSetup -> {
                                val user = LocalCache.checkGetOrCreateUser(setup.pubKeyHex)
                                UnverifiedZapSplitSetup(
                                    lnAddress = user?.lnAddress(),
                                    weight = setup.weight,
                                    relay = setup.relay,
                                    user = user,
                                    bolt12Offer = user?.bolt12Offers()?.firstOrNull(),
                                )
                            }
                        }
                    }
                }

                noteEvent is LiveActivitiesEvent && noteEvent.hasHost() -> {
                    noteEvent.hosts().map {
                        val user = LocalCache.checkGetOrCreateUser(it.pubKey)
                        val lnAddress = user?.lnAddress()
                        UnverifiedZapSplitSetup(lnAddress, relay = it.relayHint, user = user, bolt12Offer = user?.bolt12Offers()?.firstOrNull())
                    }
                }

                noteEvent is AppDefinitionEvent -> {
                    val appLud16 = noteEvent.appMetaData()?.lnAddress()
                    if (appLud16 != null) {
                        listOf(UnverifiedZapSplitSetup(appLud16))
                    } else {
                        val author = note.author
                        listOf(UnverifiedZapSplitSetup(author?.lnAddress(), user = author, bolt12Offer = author?.bolt12Offers()?.firstOrNull()))
                    }
                }

                else -> {
                    val author = note.author
                    listOf(
                        UnverifiedZapSplitSetup(
                            lnAddress = author?.lnAddress(),
                            user = author,
                            bolt12Offer = author?.bolt12Offers()?.firstOrNull(),
                        ),
                    )
                }
            }

        // BOLT12-first: a recipient who publishes a kind:10058 offer is zapped over
        // BOLT12 when our default NWC wallet advertises the nwc#2 `pay` method (needed for
        // the payer proof). Otherwise — no wallet, or a wallet without `pay` — the recipient
        // stays on lightning, so an unsupported wallet degrades gracefully instead of erroring.
        val canBolt12 = account.zaps.canZapViaBolt12()

        val bolt12Recipients =
            unverifiedZapsToSend.mapNotNull {
                val user = it.user
                if (canBolt12 && it.bolt12Offer != null && user != null) {
                    Bolt12Recipient(user, it.bolt12Offer, it.weight, it.lnAddress, it.relay)
                } else {
                    null
                }
            }
        val bolt12Users = bolt12Recipients.mapTo(HashSet()) { it.user }

        val zapsToSend =
            unverifiedZapsToSend.mapNotNull {
                // Never lightning-zap a recipient already routed over BOLT12.
                if (it.user != null && it.user in bolt12Users) {
                    null
                } else if (it.lnAddress != null) {
                    MyZapSplitSetup(it.lnAddress, it.weight, it.relay, it.user)
                } else {
                    null
                }
            }

        if (showErrorIfNoLnAddress) {
            // Only a recipient with neither a BOLT12 route nor an lnAddress is unpayable.
            val errors =
                unverifiedZapsToSend.filter {
                    val routedBolt12 = canBolt12 && it.bolt12Offer != null && it.user != null
                    !routedBolt12 && it.lnAddress.isNullOrBlank()
                }
            errors.forEach {
                onError.report(notices, ZapNotice.MissingLnAddress(it.user?.toBestDisplayName()), it.user)
            }
        }

        // Weight is shared across both lanes so splits stay proportional regardless of rail.
        val totalWeight = bolt12Recipients.sumOf { it.weight } + zapsToSend.sumOf { it.weight }
        if (totalWeight <= 0.0) {
            onProgress(0.00f)
            return@withContext
        }

        onProgress(0.02f)

        // --- Lightning lane -----------------------------------------------------------
        if (zapsToSend.isNotEmpty()) {
            zapOverLightning(
                zapsToSend = zapsToSend,
                note = note,
                pollOption = pollOption,
                message = message,
                zapType = zapType,
                totalAmountMilliSats = amountMilliSats,
                totalWeight = totalWeight,
                lnurl = lnurl,
                onError = onError,
                onProgress = onProgress,
                onPayViaIntent = onPayViaIntent,
            )
        }

        // --- BOLT12 lane --------------------------------------------------------------
        if (bolt12Recipients.isNotEmpty()) {
            payViaBolt12(
                recipients = bolt12Recipients,
                note = note,
                pollOption = pollOption,
                totalAmountMilliSats = amountMilliSats,
                totalWeight = totalWeight,
                message = message,
                zapType = zapType,
                lnurl = lnurl,
                onError = onError,
                onProgress = { onProgress(it * 0.25f + 0.75f) },
                onPayViaIntent = onPayViaIntent,
            )
        }

        onProgress(1f)
    }

    /**
     * The BOLT11 lane: signs one kind 9734 per recipient, fetches each invoice from
     * the recipient's LNURL, then settles through the default payment source. Used
     * for every lnAddress recipient of a zap, and again by [payViaBolt12] for a
     * recipient whose offer the wallet refused. [onProgress] spans 0.05..1.0.
     */
    private suspend fun zapOverLightning(
        zapsToSend: List<MyZapSplitSetup>,
        note: Note,
        pollOption: Int?,
        message: String,
        zapType: ZapReceiptEvent.ZapType,
        totalAmountMilliSats: Long,
        totalWeight: Double,
        lnurl: LnurlHttpTransport,
        onError: (String, String, User?) -> Unit,
        onProgress: (percent: Float) -> Unit,
        onPayViaIntent: (ImmutableList<Payable>) -> Unit,
    ) {
        val splitZapRequests = signAllZapRequests(note, pollOption, message, zapType, zapsToSend, totalAmountMilliSats, totalWeight)
        if (splitZapRequests.isEmpty()) return

        onProgress(0.05f)

        val payables =
            assembleAllInvoices(
                requests = splitZapRequests,
                totalAmountMilliSats = totalAmountMilliSats,
                message = message,
                lnurl = lnurl,
                onError = onError,
                onProgress = { onProgress(it * 0.7f + 0.05f) },
                totalWeight = totalWeight,
            )
        if (payables.isEmpty()) return

        onProgress(0.75f)

        // Route through the user's selected default payment source. A CLINK debit takes
        // precedence over NWC when it is the chosen default; NWC-only users are unaffected
        // (defaultPaymentSource() resolves to their NWC wallet). No source -> wallet app.
        when (val source = account.settings.defaultPaymentSource()) {
            is PaymentSource.ClinkDebit -> {
                payViaClinkDebit(payables, source.wallet.pointer, onError = onError, onProgress = {
                    onProgress(it * 0.25f + 0.75f)
                })
            }

            is PaymentSource.Nwc -> {
                payViaNWC(payables, note, onError = onError, onProgress = {
                    onProgress(it * 0.25f + 0.75f) // keeps within range.
                })
            }

            null -> {
                onPayViaIntent(payables.toImmutableList())
            }
        }
    }

    private suspend fun calculateZapValue(
        amountMilliSats: Long,
        weight: Double,
        totalWeight: Double,
    ): Long {
        val shareValue = amountMilliSats * (weight / totalWeight)
        val roundedZapValue = round(shareValue / 1000f).toLong() * 1000
        return roundedZapValue
    }

    class ZapRequestReady(
        val inputSetup: MyZapSplitSetup,
        val zapRequest: ZapRequestEvent?,
    )

    suspend fun signAllZapRequests(
        note: Note,
        pollOption: Int?,
        message: String,
        zapType: ZapReceiptEvent.ZapType,
        zapsToSend: List<MyZapSplitSetup>,
        totalAmountMilliSats: Long,
        // Shared across the lightning + BOLT12 lanes so a mixed split stays proportional.
        totalWeight: Double = zapsToSend.sumOf { it.weight },
    ): List<ZapRequestReady> =
        mapNotNullAsync(zapsToSend) { next: MyZapSplitSetup ->
            // makes sure the author receives the zap event
            val authorRelayList = note.author?.inboxRelays()?.toSet() ?: emptySet()

            // makes sure the zap split user receives the zap event
            val userRelayList = next.user?.inboxRelays()?.toSet() ?: emptySet()

            val noteEvent = note.event

            // Per NIP-57 §6: the zap request SHOULD carry `amount` (in msats) and
            // `lnurl` so the recipient's LNURL provider — and clients reading the
            // resulting receipt — can validate them under Appendix F.
            val splitAmount = calculateZapValue(totalAmountMilliSats, next.weight, totalWeight)
            val splitLnurl = LnurlForm.toUrl(next.lnAddress)?.let(LnurlForm::urlToBech32)

            val zapRequest =
                if (zapType != ZapReceiptEvent.ZapType.NONZAP && noteEvent != null) {
                    account.zaps.createZapRequestFor(
                        event = noteEvent,
                        pollOption = pollOption,
                        message = message,
                        zapType = zapType,
                        toUser = next.user,
                        additionalRelays = userRelayList + authorRelayList,
                        amountMillisats = splitAmount,
                        lnurl = splitLnurl,
                    )
                } else {
                    null
                }

            ZapRequestReady(next, zapRequest)
        }

    suspend fun assembleAllInvoices(
        requests: List<ZapRequestReady>,
        totalAmountMilliSats: Long,
        message: String,
        lnurl: LnurlHttpTransport,
        onError: (String, String, User?) -> Unit,
        onProgress: (percent: Float) -> Unit,
        // Shared across the lightning + BOLT12 lanes so a mixed split stays proportional.
        totalWeight: Double = requests.sumOf { it.inputSetup.weight },
    ): List<Payable> {
        var progressAllPayments = 0.00f

        return mapNotNullAsync(requests) { splitZapRequestPair: ZapRequestReady ->
            try {
                assembleInvoice(
                    lud16 = splitZapRequestPair.inputSetup.lnAddress,
                    splitSetup = splitZapRequestPair.inputSetup,
                    nostrZapRequest = splitZapRequestPair.zapRequest,
                    zapValue = calculateZapValue(totalAmountMilliSats, splitZapRequestPair.inputSetup.weight, totalWeight),
                    message = message,
                    lnurl = lnurl,
                    onProgressStep = { percentStepForThisPayment ->
                        progressAllPayments += percentStepForThisPayment / requests.size
                        onProgress(progressAllPayments)
                    },
                )
            } catch (e: LightningInvoiceResolver.LightningAddressError) {
                onError.report(notices, e.notice, splitZapRequestPair.inputSetup.user)
                null
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                onError.report(notices, InvoiceNotice.ReceiverFailed(e.message), null)
                null
            }
        }
    }

    class Paid(
        payable: Payable,
        success: Boolean,
    )

    suspend fun payViaNWC(
        payables: List<Payable>,
        note: Note,
        onError: (String, String, User?) -> Unit,
        onProgress: (percent: Float) -> Unit,
    ): List<Paid> {
        val progress = PaymentProgress(payables.size, onProgress)

        return mapNotNullAsync(
            items = payables,
            runRequestFor = { payable: Payable ->
                account.zaps.sendZapPaymentRequestFor(
                    bolt11 = payable.invoice,
                    zappedNote = note,
                    // Dropped unless the wallet advertises NWC-06 — see NwcSignerState.
                    metadata =
                        NwcTransactionMetadata.build(
                            zapRequest = payable.zapRequest,
                            recipientIdentifier = payable.info.lnAddress,
                            comment = payable.message,
                        ),
                    onResponse = { response ->
                        account.scope.launch {
                            progress.step()
                            response.nwcFailure()?.let { failure ->
                                onError.report(notices, ZapNotice.PayInvoiceFailed(failure), payable.info.user)
                            }
                        }
                    },
                    onTimeout = {
                        account.scope.launch {
                            onError.report(notices, ZapNotice.PayInvoiceFailed(NwcFailure.TimedOut), payable.info.user)
                        }
                    },
                )

                progress.step()

                Paid(payable, true)
            },
        )
    }

    /**
     * BOLT12 zap rail (NIP-B1). For each recipient that publishes a kind:10058 offer,
     * signs a 9737 intent, pays the offer over NWC with the intent-bound `payer_note`,
     * and (if the returned proof validates) publishes a 9736 zap — see
     * [Account.sendBolt12Zap]. Fire-and-forget like [payViaNWC]: dispatch is optimistic
     * and settlement/errors surface later through the async NWC response.
     *
     * When the wallet refuses the offer before attempting a payment — it resolves the
     * offer itself, so a stale, expired or unsupported offer fails there — and the
     * recipient also publishes a lightning address, the same share is re-sent as a
     * regular BOLT11 zap through [zapOverLightning], silently. The BOLT12 error is
     * shown when there is no BOLT11 route or when the refusal does not qualify
     * ([Bolt12LightningFallback]: a failed payment attempt may still settle, and a
     * refusal about our own wallet would repeat on BOLT11). A paid-but-no-receipt
     * outcome and a wallet that never answers are never retried either.
     */
    suspend fun payViaBolt12(
        recipients: List<Bolt12Recipient>,
        note: Note,
        pollOption: Int?,
        totalAmountMilliSats: Long,
        totalWeight: Double,
        message: String,
        zapType: ZapReceiptEvent.ZapType,
        lnurl: LnurlHttpTransport,
        onError: (String, String, User?) -> Unit,
        onProgress: (percent: Float) -> Unit,
        onPayViaIntent: (ImmutableList<Payable>) -> Unit,
    ) {
        val progress = PaymentProgress(recipients.size, onProgress)

        mapNotNullAsync(recipients) { recipient: Bolt12Recipient ->
            suspend fun reportBolt12Error(notice: UserNotice) = onError.report(notices, notice, recipient.user)

            account.zaps.sendBolt12Zap(
                zappedEvent = note.event,
                recipientPubKey = recipient.user.pubkeyHex,
                offer = recipient.offer,
                amountMillisats = calculateZapValue(totalAmountMilliSats, recipient.weight, totalWeight),
                message = message,
                zapType = zapType,
                onError = { failure -> account.scope.launch { reportBolt12Error(ZapNotice.Bolt12ReceiptFailed(failure)) } },
                onNotPaid = { code, detail ->
                    val lnAddress = recipient.lnAddress
                    if (lnAddress != null && Bolt12LightningFallback.shouldRetry(code)) {
                        Log.i("ZapPaymentHandler") { "BOLT12 offer refused ($code: $detail); re-sending over BOLT11 to $lnAddress" }
                        try {
                            zapOverLightning(
                                zapsToSend = listOf(MyZapSplitSetup(lnAddress, recipient.weight, recipient.relay, recipient.user)),
                                note = note,
                                pollOption = pollOption,
                                message = message,
                                zapType = zapType,
                                totalAmountMilliSats = totalAmountMilliSats,
                                totalWeight = totalWeight,
                                lnurl = lnurl,
                                onError = onError,
                                // The zap's own progress finished when the BOLT12 request was
                                // dispatched; the retry settles in the background like NWC does.
                                onProgress = {},
                                onPayViaIntent = onPayViaIntent,
                            )
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            // Nothing was paid on either rail. Report it as the lightning failure it
                            // is, rather than letting [sendBolt12Zap]'s catch call it "paid, no receipt".
                            Log.w("ZapPaymentHandler", "BOLT11 fallback failed after a refused BOLT12 offer", e)
                            reportBolt12Error(ZapNotice.FallbackFailed(e.message ?: e.toString()))
                        }
                    } else {
                        // bolt12_payment_failed always formats a detail; a wallet may send neither
                        // message nor a recognised code.
                        reportBolt12Error(ZapNotice.Bolt12PaymentFailed(detail ?: (code ?: NwcErrorCode.OTHER).name))
                    }
                },
                onTimeout = {
                    account.scope.launch {
                        // No response callback will fire, so account for the settlement step here.
                        reportBolt12Error(ZapNotice.Bolt12PaymentTimedOut)
                        progress.step()
                    }
                },
                onProcessed = { progress.step() },
            )

            progress.step()

            recipient
        }
    }

    /**
     * Thread-safe progress accumulator for the parallel pay rails. Each payable advances in two
     * half-steps (request dispatched, then response/settlement), reported as a 0..1 fraction.
     * The counter is atomic because `mapNotNullAsync` runs the payables concurrently and the
     * response half-step fires from an async callback, so plain `+=` would lose updates.
     */
    @OptIn(ExperimentalAtomicApi::class)
    private class PaymentProgress(
        payableCount: Int,
        private val onProgress: (percent: Float) -> Unit,
    ) {
        private val totalSteps = (payableCount * 2).coerceAtLeast(1)
        private val done = AtomicInt(0)

        fun step() = onProgress(done.incrementAndFetch().toFloat() / totalSteps)
    }

    /**
     * Pays each zap invoice by asking the user's CLINK debit service (kind 21002) to
     * settle the BOLT-11. The service authorizes against the account identity.
     *
     * Fire-and-forget, like the NWC rail ([payViaNWC]): the request is dispatched on the
     * account scope and each payable is reported paid optimistically so the zap UI completes
     * promptly. A `GFY`/failure (or no reply within the debit timeout) surfaces later through
     * [onError] rather than blocking the zap on the service's response.
     */
    suspend fun payViaClinkDebit(
        payables: List<Payable>,
        pointer: NDebit,
        onError: (String, String, User?) -> Unit,
        onProgress: (percent: Float) -> Unit,
    ): List<Paid> {
        val progress = PaymentProgress(payables.size, onProgress)

        return mapNotNullAsync(
            items = payables,
            runRequestFor = { payable: Payable ->
                account.scope.launch {
                    val response = ClinkDebitPayer.payInvoice(account, moneyOpRelays, pointer, payable.invoice)
                    progress.step()
                    if (response?.isOk() != true) {
                        onError.report(notices, ClinkDebitFailed(response?.failureDetail()), payable.info.user)
                    }
                }

                progress.step()

                Paid(payable, true)
            },
        )
    }

    private suspend fun assembleInvoice(
        lud16: String,
        splitSetup: MyZapSplitSetup,
        nostrZapRequest: ZapRequestEvent?,
        zapValue: Long,
        message: String,
        lnurl: LnurlHttpTransport,
        onProgressStep: (percent: Float) -> Unit,
    ): Payable {
        var progressThisPayment = 0.00f

        // Only the request the provider actually accepted may be claimed as bound to
        // this invoice; see lnAddressInvoice's onZapRequestSent.
        var sentZapRequest: ZapRequestEvent? = null

        val invoice =
            LightningInvoiceResolver(lnurl).lnAddressInvoice(
                lnAddress = lud16,
                milliSats = zapValue,
                message = message,
                nostrRequest = nostrZapRequest,
                onProgress = {
                    val step = it - progressThisPayment
                    progressThisPayment = it
                    onProgressStep(step)
                },
                onZapRequestSent = { sentZapRequest = it },
            )

        onProgressStep(1 - progressThisPayment)

        return Payable(
            info = splitSetup,
            amountMilliSats = zapValue,
            invoice = invoice,
            zapRequest = sentZapRequest,
            message = message,
        )
    }
}
