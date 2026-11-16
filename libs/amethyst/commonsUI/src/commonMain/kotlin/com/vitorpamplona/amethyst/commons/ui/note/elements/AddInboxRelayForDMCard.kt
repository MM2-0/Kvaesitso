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
package com.vitorpamplona.amethyst.commons.ui.note.elements

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.dm_relays_not_found
import com.vitorpamplona.amethyst.commons.resources.dm_relays_not_found_create_now
import com.vitorpamplona.amethyst.commons.resources.dm_relays_not_found_description
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.dm.AddDMRelayListDialog
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.BigPadding
import com.vitorpamplona.amethyst.commons.ui.theme.StdPadding
import com.vitorpamplona.amethyst.commons.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.commons.ui.theme.imageModifier
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.mockAccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip17Dm.settings.DmRelayListEvent

@Preview
@Composable
fun AddInboxRelayForDMCardPreview() {
    ThemeComparisonColumn {
        AddInboxRelayForDMCard(
            accountViewModel = mockAccountViewModel(),
            nav = EmptyNav(),
        )
    }
}

@Composable
fun ObserveRelayListForDMsAndDisplayIfNotFound(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    ObserveRelayListForDMs(
        accountViewModel = accountViewModel,
    ) { relayListEvent ->
        if (relayListEvent == null || relayListEvent.allRelays().isEmpty()) {
            AddInboxRelayForDMCard(
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

@Composable
fun ObserveRelayListForDMs(
    accountViewModel: AccountViewModel,
    inner: @Composable (relayListEvent: DmRelayListEvent?) -> Unit,
) {
    ObserveRelayListForDMs(
        pubkey = accountViewModel.account.userProfile().pubkeyHex,
        accountViewModel = accountViewModel,
    ) { relayListEvent ->
        inner(relayListEvent)
    }
}

@Composable
fun ObserveRelayListForDMs(
    pubkey: HexKey,
    accountViewModel: AccountViewModel,
    inner: @Composable (relayListEvent: DmRelayListEvent?) -> Unit,
) {
    LoadAddressableNote(
        DmRelayListEvent.createAddress(pubkey),
    ) { relayList ->
        if (relayList != null) {
            val relayListEvent by observeNoteEvent<DmRelayListEvent>(relayList, accountViewModel)

            inner(relayListEvent)
        }
    }
}

@Composable
fun AddInboxRelayForDMCard(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Column(modifier = StdPadding) {
        Card(
            modifier = MaterialTheme.colorScheme.imageModifier,
        ) {
            Column(
                modifier = BigPadding,
            ) {
                // Title
                Text(
                    text = stringRes(id = Res.string.dm_relays_not_found),
                    style =
                        TextStyle(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                )

                Spacer(modifier = StdVertSpacer)

                Text(
                    text = stringRes(id = Res.string.dm_relays_not_found_description),
                )

                Spacer(modifier = StdVertSpacer)

                var wantsToEditRelays by remember { mutableStateOf(false) }
                if (wantsToEditRelays) {
                    AddDMRelayListDialog({ wantsToEditRelays = false }, accountViewModel, nav = nav)
                }

                Button(
                    onClick = {
                        wantsToEditRelays = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringRes(id = Res.string.dm_relays_not_found_create_now))
                }
            }
        }
    }
}
