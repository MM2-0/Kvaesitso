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
package com.vitorpamplona.amethyst.commons.ui.note.types

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.hashtags.Cashu
import com.vitorpamplona.amethyst.commons.hashtags.CustomHashTagIcons
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.ActivityAmountRow
import com.vitorpamplona.amethyst.commons.ui.note.ActivityBadge
import com.vitorpamplona.amethyst.commons.ui.note.ActivityCardFrame
import com.vitorpamplona.amethyst.commons.ui.note.ActivityHeaderRow
import com.vitorpamplona.amethyst.commons.ui.note.CrossfadeToDisplayComment
import com.vitorpamplona.amethyst.commons.ui.note.DisplayBlankAuthor
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.theme.Size25dp
import com.vitorpamplona.amethyst.commons.ui.theme.bitcoinColor
import com.vitorpamplona.amethyst.commons.util.showAmount
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip61Nutzaps.nutzap.NutzapEvent
import com.vitorpamplona.quartz.nip61Nutzaps.nutzap.claimedSatsTotal
import com.vitorpamplona.quartz.utils.BigDecimal

/**
 * Renders a NIP-61 nutzap (kind 9321) as an activity card, like lightning zaps.
 * Unlike kind 9735 receipts, the nutzap is signed by the sender, so [Note.author]
 * is already the right person to attribute.
 */
@Composable
fun RenderNutzap(
    note: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val nutzapEvent = note.event as? NutzapEvent ?: return

    val recipientKey = nutzapEvent.linkedPubKeys().firstOrNull()
    val orange = MaterialTheme.colorScheme.bitcoinColor

    ActivityCardFrame(orange) { cardBackground ->
        ActivityHeaderRow(
            tint = orange,
            pillLabel = "CASHU",
            badge = {
                ActivityBadge(orange) {
                    Icon(
                        imageVector = CustomHashTagIcons.Cashu,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            },
            senderAvatar = {
                val sender = note.author
                if (sender != null) {
                    UserPicture(sender, Size25dp, Modifier, accountViewModel, nav)
                } else {
                    DisplayBlankAuthor(Size25dp, accountViewModel = accountViewModel)
                }
            },
            recipientAvatar =
                recipientKey?.let {
                    { UserPicture(it, Size25dp, Modifier, accountViewModel, nav) }
                },
        )

        RenderTargetNote(note, quotesLeft, cardBackground, accountViewModel, nav)

        ActivityAmountRow(showAmount(BigDecimal(nutzapEvent.claimedSatsTotal())), orange)

        nutzapEvent.content.ifBlank { null }?.let {
            CrossfadeToDisplayComment(it, cardBackground, nav, accountViewModel)
        }
    }
}
