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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.types

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.ZapAmountCommentNotification
import com.vitorpamplona.amethyst.commons.model.navigation.routeFor
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.nip53LiveActivities.ui.StreamSystemCard
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.chat_zap_amount_suffix
import com.vitorpamplona.amethyst.commons.resources.chat_zap_anonymous
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.CrossfadeToDisplayComment
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.note.ZapIcon
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.BitcoinOrange
import com.vitorpamplona.amethyst.commons.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size20dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size24Modifier
import com.vitorpamplona.amethyst.commons.util.showAmountInteger
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip57Zaps.ZapReceiptEvent
import com.vitorpamplona.quartz.utils.toLongValue

private const val BIG_ZAP_THRESHOLD_SATS = 50_000L

@Composable
fun RenderChatZap(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val zapEvent = baseNote.event as? ZapReceiptEvent ?: return

    val card by produceState(
        ZapAmountCommentNotification(user = null, comment = null, amount = null),
        baseNote,
    ) {
        value = accountViewModel.innerDecryptAmountMessage(baseNote) ?: value
    }

    val isBigZap =
        remember(zapEvent) {
            (zapEvent.amount?.toLongValue() ?: 0L) >= BIG_ZAP_THRESHOLD_SATS
        }

    val accentAlpha = if (isBigZap) 0.18f else 0.08f
    val amountText = card.amount ?: showAmountInteger(zapEvent.amount)

    // Content-hugging centered pill, matching the system-message design the rest
    // of the chat feed moved to; the bitcoin accent keeps zaps celebratory.
    // Tapping opens the zap receipt in its own thread view.
    StreamSystemCard(
        accent = BitcoinOrange,
        accentAlpha = accentAlpha,
        fillWidth = false,
        onClick = {
            routeFor(baseNote, accountViewModel.account)?.let { nav.nav(it) }
        },
    ) {
        val backgroundColor = remember(accentAlpha) { mutableStateOf(BitcoinOrange.copy(alpha = accentAlpha)) }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ZapIcon(
                    if (isBigZap) Size24Modifier else Size20Modifier,
                    BitcoinOrange,
                )

                val sender = card.user
                if (sender != null) {
                    UserPicture(sender, Size20dp, Modifier, accountViewModel, nav)
                    // weight(fill = false) hugs short names but yields to the
                    // amount when the name is long — the amount is the point of
                    // the card and must always stay visible.
                    Row(modifier = Modifier.weight(1f, fill = false)) {
                        UsernameDisplay(
                            baseUser = sender,
                            fontWeight = FontWeight.Bold,
                            accountViewModel = accountViewModel,
                        )
                    }
                } else {
                    Text(
                        text = stringRes(Res.string.chat_zap_anonymous),
                        fontWeight = FontWeight.Bold,
                    )
                }

                Text(
                    text = stringRes(Res.string.chat_zap_amount_suffix, amountText),
                    color = BitcoinOrange,
                    fontWeight = if (isBigZap) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                )
            }

            card.comment?.takeIf { it.isNotBlank() }?.let { comment ->
                Spacer(Modifier.padding(top = 2.dp))
                CrossfadeToDisplayComment(
                    comment = comment,
                    backgroundColor = backgroundColor,
                    nav = nav,
                    accountViewModel = accountViewModel,
                )
            }
        }
    }
}
