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
package com.vitorpamplona.amethyst.commons.ui.note.creators.invoice

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.hashtags.CustomHashTagIcons
import com.vitorpamplona.amethyst.commons.hashtags.Lightning
import com.vitorpamplona.amethyst.commons.model.navigation.routeFor
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.clink_lightning_offer
import com.vitorpamplona.amethyst.commons.resources.clink_offer_amount_range
import com.vitorpamplona.amethyst.commons.resources.clink_offer_amount_sats
import com.vitorpamplona.amethyst.commons.resources.clink_offer_invalid_amount
import com.vitorpamplona.amethyst.commons.resources.clink_offer_pay_to
import com.vitorpamplona.amethyst.commons.resources.clink_requesting_invoice
import com.vitorpamplona.amethyst.commons.resources.error_dialog_pay_invoice_error
import com.vitorpamplona.amethyst.commons.resources.pay
import com.vitorpamplona.amethyst.commons.resources.sats
import com.vitorpamplona.amethyst.commons.service.ClinkOfferPayer
import com.vitorpamplona.amethyst.commons.ui.components.PaymentCard
import com.vitorpamplona.amethyst.commons.ui.components.PaymentCardAmount
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.ClickableUserPicture
import com.vitorpamplona.amethyst.commons.ui.note.ErrorMessageDialog
import com.vitorpamplona.amethyst.commons.ui.note.LoadUser
import com.vitorpamplona.amethyst.commons.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.commons.ui.theme.Size18Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size25dp
import com.vitorpamplona.amethyst.commons.ui.theme.SmallBorder
import com.vitorpamplona.amethyst.commons.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.commons.util.formatGrouped
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.experimental.clink.common.SatRange
import com.vitorpamplona.quartz.experimental.clink.offers.OfferErrorCode
import com.vitorpamplona.quartz.experimental.clink.pointers.ClinkPointerParser
import com.vitorpamplona.quartz.experimental.clink.pointers.NOffer
import com.vitorpamplona.quartz.experimental.clink.pointers.OfferPriceType
import kotlinx.coroutines.launch

/**
 * Inline card for a CLINK Offers pointer (`noffer1…`) found in a note. Tapping "Pay"
 * runs the offer round-trip ([ClinkOfferPayer]) to fetch a fresh BOLT-11 over Nostr,
 * then pays it through the user's default payment source (confirmed for in-app wallets,
 * see [InvoicePaymentDispatcher]).
 */
@Composable
fun ClinkOfferPreview(
    offer: NOffer,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val scope = rememberCoroutineScope()

    var requesting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var payingInvoice by remember { mutableStateOf<String?>(null) }
    var amountInput by remember { mutableStateOf("") }
    var needsAmount by remember { mutableStateOf(offer.priceType == OfferPriceType.SPONTANEOUS) }
    var amountRange by remember { mutableStateOf<SatRange?>(null) }
    // The pointer actually paid: starts as the rendered offer, swapped if the service
    // replies "Expired or Moved" (code 3) with a replacement noffer.
    var activeOffer by remember(offer) { mutableStateOf(offer) }

    errorMessage?.let {
        ErrorMessageDialog(
            title = stringRes(Res.string.error_dialog_pay_invoice_error),
            textContent = it,
            onDismiss = { errorMessage = null },
        )
    }

    InvoicePaymentDispatcher(
        bolt11 = payingInvoice,
        accountViewModel = accountViewModel,
        onClear = { payingInvoice = null },
        onError = { errorMessage = it },
    )

    PaymentCard(
        title = stringRes(Res.string.clink_lightning_offer),
        icon = {
            Icon(
                imageVector = CustomHashTagIcons.Lightning,
                contentDescription = null,
                modifier = Size18Modifier,
                tint = Color.Unspecified,
            )
        },
        copyValue = activeOffer.encode(),
    ) {
        // Who gets paid: the offer pointer carries the recipient's pubkey.
        OfferRecipientRow(activeOffer.pubKey, accountViewModel, nav)

        // FIXED offers display their preset price; SPONTANEOUS offers (and the default
        // when the pointer omits a price type) require the payer to enter an amount.
        // Reflect the pointer actually being charged (which may have changed if the
        // service redirected us to a replacement noffer via "Expired or Moved").
        if (activeOffer.priceType == OfferPriceType.FIXED) {
            activeOffer.price?.let {
                PaymentCardAmount(
                    amount = formatGrouped(it),
                    unit = stringRes(Res.string.sats),
                )
            }
        }

        if (needsAmount) {
            OutlinedTextField(
                value = amountInput,
                onValueChange = { new -> amountInput = new.filter(Char::isDigit) },
                label = { Text(stringRes(Res.string.clink_offer_amount_sats)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText =
                    amountRange?.let { range ->
                        val min = range.min
                        val max = range.max
                        if (min != null && max != null) {
                            { Text(stringRes(Res.string.clink_offer_amount_range, min.toString(), max.toString())) }
                        } else {
                            null
                        }
                    },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
            )
        }

        val amountRequired = needsAmount

        suspend fun runOfferRequest(
            useOffer: NOffer,
            followMoved: Boolean,
        ) {
            val amount = if (amountRequired) amountInput.toLongOrNull() else useOffer.price

            val response = ClinkOfferPayer.requestInvoice(accountViewModel.account, accountViewModel.host.moneyOpRelays, useOffer, amountSats = amount)

            val bolt11 = response?.bolt11
            val movedTo =
                if (response?.code == OfferErrorCode.EXPIRED_OR_MOVED && followMoved) {
                    response.latest?.let { ClinkPointerParser.parse(it) as? NOffer }
                } else {
                    null
                }

            when {
                bolt11 != null -> {
                    requesting = false
                    payingInvoice = bolt11
                }
                // Follow a relocated offer once, paying the replacement pointer.
                movedTo != null -> {
                    activeOffer = movedTo
                    runOfferRequest(movedTo, followMoved = false)
                }
                response?.code == OfferErrorCode.INVALID_AMOUNT -> {
                    // Reveal the amount field (or refine it) with the service's range.
                    requesting = false
                    needsAmount = true
                    amountRange = response.range
                    errorMessage =
                        response.error?.takeIf { it.isNotBlank() }
                            ?: loadStringRes(Res.string.clink_offer_invalid_amount)
                }
                else -> {
                    requesting = false
                    errorMessage =
                        response?.error?.takeIf { it.isNotBlank() }
                            ?: loadStringRes(Res.string.error_dialog_pay_invoice_error)
                }
            }
        }

        Button(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            enabled = !requesting && (!amountRequired || (amountInput.toLongOrNull() ?: 0L) > 0L),
            onClick = {
                requesting = true
                scope.launch { runOfferRequest(activeOffer, followMoved = true) }
            },
            shape = ButtonBorder,
        ) {
            Text(text = stringRes(if (requesting) Res.string.clink_requesting_invoice else Res.string.pay))
        }
    }
}

/** Avatar + name of the offer's payee, tappable to open their profile. */
@Composable
private fun OfferRecipientRow(
    pubKey: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadUser(baseUserHex = pubKey) { user ->
        if (user != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clip(SmallBorder)
                        .clickable { nav.nav(routeFor(user)) }
                        .padding(4.dp),
            ) {
                Text(
                    text = stringRes(Res.string.clink_offer_pay_to),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp),
                )

                ClickableUserPicture(
                    baseUser = user,
                    size = Size25dp,
                    accountViewModel = accountViewModel,
                    onClick = { nav.nav(routeFor(it)) },
                )

                Spacer(modifier = StdHorzSpacer)

                UsernameDisplay(user, accountViewModel = accountViewModel)
            }
        }
    }
}
