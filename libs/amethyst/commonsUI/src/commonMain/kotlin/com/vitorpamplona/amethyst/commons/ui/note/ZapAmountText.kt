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
package com.vitorpamplona.amethyst.commons.ui.note

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.ZapraiserStatus
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteZaps
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.nwc.NWCFinderFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.sats_to_complete
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Font14SP
import com.vitorpamplona.amethyst.commons.ui.theme.Height24dpFilledModifier
import com.vitorpamplona.amethyst.commons.ui.theme.Height4dpFilledModifier
import com.vitorpamplona.amethyst.commons.ui.theme.NoSoTinyBorders
import com.vitorpamplona.amethyst.commons.ui.theme.TinyBorders
import com.vitorpamplona.amethyst.commons.ui.theme.fundraiserProgressColor
import com.vitorpamplona.amethyst.commons.util.showAmount
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip57Zaps.zapraiser.zapraiserAmount
import com.vitorpamplona.quartz.utils.BigDecimal
import com.vitorpamplona.quartz.utils.plus
import kotlin.math.roundToInt

@Composable
fun ObserveZapAmountText(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    inner: @Composable (String) -> Unit,
) {
    val zapsState by observeNoteZaps(baseNote, accountViewModel)

    if (zapsState?.note?.zapPayments?.isNotEmpty() == true) {
        zapsState?.note?.zapPayments?.forEach {
            if (it.value == null) {
                NWCFinderFilterAssemblerSubscription(it.key, accountViewModel)
            }
        }

        @Suppress("ProduceStateDoesNotAssignValue")
        val zapAmountTxt by
            produceState(initialValue = showAmount(baseNote.zapsAmount), key1 = zapsState) {
                zapsState?.note?.let {
                    val newZapAmount = accountViewModel.calculateZapAmount(it)
                    if (value != newZapAmount) {
                        value = newZapAmount
                    }
                }
            }

        inner(zapAmountTxt)
    } else {
        // Include the signed-in user's own pending onchain zaps so
        // the counter reflects the optimistic value the gallery shows.
        val ownPubKey = accountViewModel.account.userProfile().pubkeyHex
        val note = zapsState?.note
        val total =
            (note?.zapsAmount ?: BigDecimal(0)) +
                BigDecimal(note?.extraOwnPendingOnchainSats(ownPubKey) ?: 0L)
        inner(showAmount(total))
    }
}

@Composable
fun RenderZapRaiser(
    baseNote: Note,
    zapraiserAmount: Long,
    details: Boolean,
    accountViewModel: AccountViewModel,
) {
    val zapsState by observeNoteZaps(baseNote, accountViewModel)

    var zapraiserStatus by remember { mutableStateOf(ZapraiserStatus(0F, "$zapraiserAmount")) }

    LaunchedEffect(key1 = zapsState) {
        zapsState?.note?.let {
            val newStatus = accountViewModel.calculateZapraiser(baseNote)
            if (zapraiserStatus != newStatus) {
                zapraiserStatus = newStatus
            }
        }
    }

    LinearProgressIndicator(
        modifier = if (details) Height24dpFilledModifier else Height4dpFilledModifier,
        color = MaterialTheme.colorScheme.fundraiserProgressColor,
        progress = { zapraiserStatus.progress },
        gapSize = 0.dp,
        strokeCap = StrokeCap.Square,
        drawStopIndicator = {},
    )

    if (details) {
        Box(
            contentAlignment = Center,
            modifier = TinyBorders,
        ) {
            val totalPercentage by
                remember(zapraiserStatus) {
                    derivedStateOf { "${(zapraiserStatus.progress * 100).roundToInt()}%" }
                }

            Text(
                text =
                    stringRes(id = Res.string.sats_to_complete, totalPercentage, zapraiserStatus.left),
                modifier = NoSoTinyBorders,
                // color = MaterialTheme.colorScheme.placeholderText,
                fontSize = Font14SP,
                maxLines = 1,
            )
        }
    }
}
