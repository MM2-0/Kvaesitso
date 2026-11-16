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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.dm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vitorpamplona.amethyst.commons.defaults.DefaultDMRelayList
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.default_relays_longer
import com.vitorpamplona.amethyst.commons.resources.dm_relays_not_found_editing
import com.vitorpamplona.amethyst.commons.resources.dm_relays_not_found_examples2
import com.vitorpamplona.amethyst.commons.resources.dm_relays_title
import com.vitorpamplona.amethyst.commons.ui.components.SetDialogToEdgeToEdge
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.SavingTopBar
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.common.relaySetupInfoBuilder
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.dm.DMRelayList
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.dm.DMRelayListViewModel
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.imageModifier
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDMRelayListDialog(
    onClose: () -> Unit,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val postViewModel: DMRelayListViewModel = rememberViewModel { DMRelayListViewModel() }

    postViewModel.init(accountViewModel)

    LaunchedEffect(postViewModel, accountViewModel) {
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
                    titleRes = Res.string.dm_relays_title,
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

                DMRelayList(postViewModel, accountViewModel, onClose, nav)
            }
        }
    }
}

@Composable
private fun Explanation(postViewModel: DMRelayListViewModel) {
    Card(modifier = MaterialTheme.colorScheme.imageModifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringRes(id = Res.string.dm_relays_not_found_editing),
            )

            Spacer(modifier = StdVertSpacer)

            Text(
                text = stringRes(id = Res.string.dm_relays_not_found_examples2),
            )

            Spacer(modifier = StdVertSpacer)

            ResetDMRelaysLonger(postViewModel)
        }
    }
}

@Composable
fun ResetDMRelaysLonger(postViewModel: DMRelayListViewModel) {
    OutlinedButton(
        onClick = {
            postViewModel.deleteAll()
            DefaultDMRelayList.forEach {
                postViewModel.addRelay(relaySetupInfoBuilder(it))
            }
            postViewModel.loadRelayDocuments()
        },
    ) {
        Text(stringRes(Res.string.default_relays_longer))
    }
}
