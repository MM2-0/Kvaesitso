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
package com.vitorpamplona.amethyst.commons.ui.note.creators.zapsplits

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import com.vitorpamplona.amethyst.commons.ui.components.ClickableTextPrimary
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.note.creators.zapsplits.ZapSplitIcon
import com.vitorpamplona.amethyst.commons.ui.theme.BitcoinOrange
import com.vitorpamplona.amethyst.commons.ui.theme.Size25dp
import com.vitorpamplona.amethyst.commons.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip57Zaps.splits.ZapSplitSetup
import com.vitorpamplona.quartz.nip57Zaps.splits.ZapSplitSetupLnAddress
import com.vitorpamplona.quartz.nip57Zaps.splits.zapSplitSetup

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DisplayZapSplits(
    noteEvent: Event,
    useAuthorIfEmpty: Boolean = false,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val list =
        remember(noteEvent) {
            val list = noteEvent.zapSplitSetup()
            if (list.isEmpty() && useAuthorIfEmpty) {
                listOf(
                    ZapSplitSetup(
                        pubKeyHex = noteEvent.pubKey,
                        relay = null,
                        weight = 1.0,
                    ),
                )
            } else {
                list
            }
        }
    if (list.isEmpty()) return

    Row(verticalAlignment = Alignment.CenterVertically) {
        ZapSplitIcon(tint = BitcoinOrange)

        Spacer(modifier = StdHorzSpacer)

        FlowRow {
            list.forEach {
                when (it) {
                    is ZapSplitSetupLnAddress -> {
                        ClickableTextPrimary(it.lnAddress) { }
                    }

                    is ZapSplitSetup -> {
                        UserPicture(
                            userHex = it.pubKeyHex,
                            size = Size25dp,
                            accountViewModel = accountViewModel,
                            nav = nav,
                        )
                    }
                }
            }
        }
    }
}
