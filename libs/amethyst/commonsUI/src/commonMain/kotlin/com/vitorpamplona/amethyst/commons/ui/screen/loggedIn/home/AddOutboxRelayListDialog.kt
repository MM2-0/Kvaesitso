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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.outbox_relays_not_found_editing
import com.vitorpamplona.amethyst.commons.resources.outbox_relays_not_found_examples
import com.vitorpamplona.amethyst.commons.resources.outbox_relays_title
import com.vitorpamplona.amethyst.commons.ui.components.SetDialogToEdgeToEdge
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.SavingTopBar
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.nip65.Nip65OutboxRelayList
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.nip65.Nip65RelayListViewModel
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.imageModifier
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddOutboxRelayListDialog(
    onClose: () -> Unit,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val postViewModel: Nip65RelayListViewModel = rememberViewModel { Nip65RelayListViewModel() }

    postViewModel.init(accountViewModel)

    LaunchedEffect(postViewModel, accountViewModel.account) {
        postViewModel.load()
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        SetDialogToEdgeToEdge()
        Scaffold(
            topBar = {
                SavingTopBar(
                    titleRes = Res.string.outbox_relays_title,
                    onCancel = {
                        postViewModel.clear()
                        onClose()
                    },
                    onPost = {
                        postViewModel.create()
                        onClose()
                    },
                )
            },
        ) { pad ->
            Column(
                modifier =
                    Modifier.padding(
                        16.dp,
                        pad.calculateTopPadding(),
                        16.dp,
                        pad.calculateBottomPadding(),
                    ),
                verticalArrangement = Arrangement.SpaceAround,
            ) {
                Explanation(postViewModel)

                Nip65OutboxRelayList(postViewModel, accountViewModel, onClose, nav)
            }
        }
    }
}

@Composable
private fun Explanation(postViewModel: Nip65RelayListViewModel) {
    Card(modifier = MaterialTheme.colorScheme.imageModifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringRes(id = Res.string.outbox_relays_not_found_editing),
            )

            Spacer(modifier = StdVertSpacer)

            Text(
                text = stringRes(id = Res.string.outbox_relays_not_found_examples),
            )
        }
    }
}
