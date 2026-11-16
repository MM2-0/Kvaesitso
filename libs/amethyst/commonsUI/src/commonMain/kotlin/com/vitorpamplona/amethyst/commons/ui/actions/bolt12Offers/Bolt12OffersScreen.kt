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
package com.vitorpamplona.amethyst.commons.ui.actions.bolt12Offers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.bolt12Offers.Bolt12OffersViewModel
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.add
import com.vitorpamplona.amethyst.commons.resources.bolt12_offer
import com.vitorpamplona.amethyst.commons.resources.bolt12_offers
import com.vitorpamplona.amethyst.commons.resources.bolt12_offers_explainer
import com.vitorpamplona.amethyst.commons.resources.bolt12_offers_section_explainer
import com.vitorpamplona.amethyst.commons.resources.delete_bolt12_offer
import com.vitorpamplona.amethyst.commons.resources.invalid_bolt12_offer
import com.vitorpamplona.amethyst.commons.resources.no_bolt12_offers_message
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.SavingTopBar
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.header.abbreviateBolt12Offer
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

@Composable
fun Bolt12OffersScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val viewModel: Bolt12OffersViewModel = rememberViewModel { Bolt12OffersViewModel() }
    viewModel.init(accountViewModel)

    LaunchedEffect(key1 = accountViewModel) {
        viewModel.load()
    }

    Bolt12OffersScaffold(viewModel) {
        nav.popBack()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Bolt12OffersScaffold(
    viewModel: Bolt12OffersViewModel,
    onClose: () -> Unit,
) {
    Scaffold(
        topBar = {
            SavingTopBar(
                titleRes = Res.string.bolt12_offers,
                onCancel = {
                    viewModel.refresh()
                    onClose()
                },
                onPost = {
                    viewModel.saveOffers()
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
                text = stringRes(id = Res.string.bolt12_offers_explainer),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.grayText,
            )

            Bolt12OffersBody(viewModel)
        }
    }
}

@Composable
fun Bolt12OffersBody(viewModel: Bolt12OffersViewModel) {
    val offers by viewModel.offers.collectAsStateWithLifecycle()

    LazyColumn(
        verticalArrangement = Arrangement.SpaceAround,
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = FeedPadding,
    ) {
        item {
            SettingsCategory(
                Res.string.bolt12_offers,
                Res.string.bolt12_offers_section_explainer,
                SettingsCategoryFirstModifier,
            )
        }

        if (offers.isEmpty()) {
            item {
                Text(
                    text = stringRes(id = Res.string.no_bolt12_offers_message),
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
        } else {
            items(offers, key = { it }) { offer ->
                Bolt12OfferEntry(offer = offer, onDelete = { viewModel.removeOffer(offer) })
            }
        }

        item {
            Spacer(modifier = StdVertSpacer)
            Bolt12OfferAddField { raw -> viewModel.addOffer(raw) }
        }
    }
}

@Composable
fun Bolt12OfferEntry(
    offer: String,
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
        Text(
            text = abbreviateBolt12Offer(offer),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDelete) {
            Icon(
                symbol = MaterialSymbols.Delete,
                contentDescription = stringRes(id = Res.string.delete_bolt12_offer),
            )
        }
    }
}

@Composable
fun Bolt12OfferAddField(onAdd: (raw: String) -> Boolean) {
    var offer by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(Size10dp)) {
        OutlinedTextField(
            label = { Text(text = stringRes(Res.string.bolt12_offer)) },
            modifier = Modifier.fillMaxWidth(),
            value = offer,
            onValueChange = {
                offer = it
                isError = false
            },
            isError = isError,
            supportingText =
                if (isError) {
                    { Text(text = stringRes(Res.string.invalid_bolt12_offer)) }
                } else {
                    null
                },
            placeholder = {
                Text(
                    text = "lno1…",
                    color = MaterialTheme.colorScheme.placeholderText,
                    maxLines = 1,
                )
            },
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    if (onAdd(offer)) {
                        offer = ""
                        isError = false
                    } else {
                        isError = true
                    }
                },
                shape = ButtonBorder,
                enabled = offer.isNotBlank(),
            ) {
                Text(text = stringRes(id = Res.string.add), color = Color.White)
            }
        }
    }
}
