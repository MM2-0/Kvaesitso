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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.interestSets.list.metadata

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.interest_set_creation_screen_title
import com.vitorpamplona.amethyst.commons.resources.interest_set_name_label
import com.vitorpamplona.amethyst.commons.resources.interest_set_name_placeholder
import com.vitorpamplona.amethyst.commons.resources.interest_set_rename
import com.vitorpamplona.amethyst.commons.resources.login_with_a_private_key_to_be_able_to_sign_events
import com.vitorpamplona.amethyst.commons.resources.read_only_user
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.CreatingTopBar
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.SavingTopBar
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions

@Composable
fun InterestSetMetadataScreen(
    identifier: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val viewModel: InterestSetMetadataViewModel = rememberViewModel { InterestSetMetadataViewModel() }
    viewModel.init(accountViewModel)

    if (identifier != null) {
        LaunchedEffect(viewModel) { viewModel.load(identifier) }
    } else {
        LaunchedEffect(viewModel) { viewModel.new() }
    }

    InterestSetMetadataScaffold(viewModel, accountViewModel, nav)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InterestSetMetadataScaffold(
    viewModel: InterestSetMetadataViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Scaffold(
        topBar = {
            InterestSetMetadataTopBar(viewModel, accountViewModel, nav)
        },
    ) { pad ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(
                    start = 10.dp,
                    end = 10.dp,
                    top = pad.calculateTopPadding(),
                    bottom = pad.calculateBottomPadding(),
                ).consumeWindowInsets(pad)
                .imePaddingSafe(),
        ) {
            item {
                ListName(viewModel)
            }
        }
    }
}

@Composable
fun InterestSetMetadataTopBar(
    viewModel: InterestSetMetadataViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (viewModel.isNewList) {
        CreatingTopBar(
            titleRes = Res.string.interest_set_creation_screen_title,
            isActive = viewModel::canPost,
            onCancel = {
                viewModel.clear()
                nav.popBack()
            },
            onPost = {
                try {
                    viewModel.createOrUpdate()
                    nav.popBack()
                } catch (_: SignerExceptions.ReadOnlyException) {
                    accountViewModel.toastManager.toast(
                        Res.string.read_only_user,
                        Res.string.login_with_a_private_key_to_be_able_to_sign_events,
                    )
                }
            },
        )
    } else {
        SavingTopBar(
            titleRes = Res.string.interest_set_rename,
            isActive = viewModel::canPost,
            onCancel = {
                viewModel.clear()
                nav.popBack()
            },
            onPost = {
                try {
                    viewModel.createOrUpdate()
                    nav.popBack()
                } catch (_: SignerExceptions.ReadOnlyException) {
                    accountViewModel.toastManager.toast(
                        Res.string.read_only_user,
                        Res.string.login_with_a_private_key_to_be_able_to_sign_events,
                    )
                }
            },
        )
    }
}

@Composable
private fun ListName(viewModel: InterestSetMetadataViewModel) {
    OutlinedTextField(
        label = { Text(text = stringRes(Res.string.interest_set_name_label)) },
        modifier = Modifier.fillMaxWidth(),
        value = viewModel.name.value,
        onValueChange = { viewModel.name.value = it },
        placeholder = {
            Text(
                text = stringRes(Res.string.interest_set_name_placeholder),
                color = MaterialTheme.colorScheme.placeholderText,
            )
        },
        keyboardOptions =
            KeyboardOptions.Default.copy(
                capitalization = KeyboardCapitalization.Sentences,
            ),
        textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
    )
}
