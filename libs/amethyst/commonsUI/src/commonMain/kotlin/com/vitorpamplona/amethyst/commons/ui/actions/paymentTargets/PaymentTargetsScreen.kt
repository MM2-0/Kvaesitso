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
package com.vitorpamplona.amethyst.commons.ui.actions.paymentTargets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.paymentTargets.PaymentTargetsViewModel
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.add
import com.vitorpamplona.amethyst.commons.resources.delete_payment_target
import com.vitorpamplona.amethyst.commons.resources.no_payment_targets_message
import com.vitorpamplona.amethyst.commons.resources.payment_target_authority
import com.vitorpamplona.amethyst.commons.resources.payment_target_type
import com.vitorpamplona.amethyst.commons.resources.payment_targets
import com.vitorpamplona.amethyst.commons.resources.payment_targets_explainer
import com.vitorpamplona.amethyst.commons.resources.payment_targets_section_explainer
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.SavingTopBar
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.SettingsCategory
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.commons.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.commons.ui.theme.SettingsCategoryFirstModifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size10dp
import com.vitorpamplona.amethyst.commons.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nipA3PaymentTargets.PaymentTarget

@Composable
fun PaymentTargetsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val viewModel: PaymentTargetsViewModel = rememberViewModel { PaymentTargetsViewModel() }
    viewModel.init(accountViewModel)

    LaunchedEffect(key1 = accountViewModel) {
        viewModel.load()
    }

    PaymentTargetsScaffold(viewModel) {
        nav.popBack()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentTargetsScaffold(
    viewModel: PaymentTargetsViewModel,
    onClose: () -> Unit,
) {
    Scaffold(
        topBar = {
            SavingTopBar(
                titleRes = Res.string.payment_targets,
                onCancel = {
                    viewModel.refresh()
                    onClose()
                },
                onPost = {
                    viewModel.savePaymentTargets()
                    onClose()
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        start = 16.dp,
                        top = padding.calculateTopPadding(),
                        end = 16.dp,
                        bottom = padding.calculateBottomPadding(),
                    ).consumeWindowInsets(padding)
                    .imePaddingSafe(),
            verticalArrangement = Arrangement.spacedBy(10.dp, alignment = Alignment.Top),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringRes(id = Res.string.payment_targets_explainer),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.grayText,
            )

            PaymentTargetsBody(viewModel)
        }
    }
}

@Composable
fun PaymentTargetsBody(viewModel: PaymentTargetsViewModel) {
    val targets by viewModel.paymentTargets.collectAsStateWithLifecycle()

    LazyColumn(
        verticalArrangement = Arrangement.SpaceAround,
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = FeedPadding,
    ) {
        item {
            SettingsCategory(
                Res.string.payment_targets,
                Res.string.payment_targets_section_explainer,
                SettingsCategoryFirstModifier,
            )
        }

        if (targets.isEmpty()) {
            item {
                Text(
                    text = stringRes(id = Res.string.no_payment_targets_message),
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
        } else {
            itemsIndexed(
                targets,
                key = { _: Int, target: PaymentTarget -> target.type + ":" + target.authority },
            ) { _, target ->
                PaymentTargetEntry(target = target, onDelete = { viewModel.removeTarget(target) })
            }
        }

        item {
            Spacer(modifier = StdVertSpacer)
            PaymentTargetAddField { type, authority ->
                viewModel.addTarget(type, authority)
            }
        }
    }
}

@Composable
fun PaymentTargetEntry(
    target: PaymentTarget,
    onDelete: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = target.type.replaceFirstChar(Char::titlecase),
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(modifier = StdVertSpacer)
            Text(
                text = target.authority,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.grayText,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                symbol = MaterialSymbols.Delete,
                contentDescription = stringRes(id = Res.string.delete_payment_target),
            )
        }
    }
}

@Composable
fun PaymentTargetAddField(onAdd: (type: String, authority: String) -> Unit) {
    var type by remember { mutableStateOf("") }
    var authority by remember { mutableStateOf("") }
    val isValid = type.trim().isNotEmpty() && authority.trim().isNotEmpty()

    Column(verticalArrangement = Arrangement.spacedBy(Size10dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Size10dp),
        ) {
            OutlinedTextField(
                label = { Text(text = stringRes(Res.string.payment_target_type)) },
                modifier = Modifier.weight(1f),
                value = type,
                onValueChange = { type = it },
                placeholder = {
                    Text(
                        text = "bitcoin",
                        color = MaterialTheme.colorScheme.placeholderText,
                        maxLines = 1,
                    )
                },
                singleLine = true,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Size10dp),
        ) {
            OutlinedTextField(
                label = { Text(text = stringRes(Res.string.payment_target_authority)) },
                modifier = Modifier.weight(1f),
                value = authority,
                onValueChange = { authority = it },
                placeholder = {
                    Text(
                        text = "bc1q...",
                        color = MaterialTheme.colorScheme.placeholderText,
                        maxLines = 1,
                    )
                },
                singleLine = true,
            )
            Button(
                onClick = {
                    if (isValid) {
                        onAdd(type, authority)
                        type = ""
                        authority = ""
                    }
                },
                shape = ButtonBorder,
                enabled = isValid,
            ) {
                Text(text = stringRes(id = Res.string.add), color = Color.White)
            }
        }
    }
}
