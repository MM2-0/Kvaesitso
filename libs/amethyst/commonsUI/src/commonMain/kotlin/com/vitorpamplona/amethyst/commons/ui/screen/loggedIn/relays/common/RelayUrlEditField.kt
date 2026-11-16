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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults.cardElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.relays.nip11RelayInfo.Nip11CachedRetriever
import com.vitorpamplona.amethyst.commons.relays.nip11RelayInfo.Nip11Fetcher
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.add
import com.vitorpamplona.amethyst.commons.resources.add_a_relay
import com.vitorpamplona.amethyst.commons.resources.relay_url_not_valid
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.common.BasicRelaySetupInfo
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.common.IRelaySuggestionState
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.common.RelaySuggestionState
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.common.ShowRelaySuggestionList
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.commons.ui.theme.HalfHorzPadding
import com.vitorpamplona.amethyst.commons.ui.theme.PopupUpEffect
import com.vitorpamplona.amethyst.commons.ui.theme.StdEndPadding
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.mockAccountViewModel
import com.vitorpamplona.quartz.nip01Core.relay.client.stats.RelayStat
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

@Preview
@Composable
fun RelayUrlEditFieldPreview() {
    val suggestions =
        listOf(
            BasicRelaySetupInfo(
                relay = "wss://nos.lol".normalizeRelayUrl(),
                relayStat =
                    RelayStat(
                        receivedBytes = 1000,
                        sentBytes = 1000,
                        spamCounter = 12,
                        errorCounter = 0,
                    ),
            ),
            BasicRelaySetupInfo(
                relay = "wss://nostr.mom".normalizeRelayUrl(),
                relayStat =
                    RelayStat(
                        receivedBytes = 1000,
                        sentBytes = 1000,
                        spamCounter = 12,
                        errorCounter = 0,
                    ),
            ),
        )

    val relaySuggestions =
        remember {
            object : IRelaySuggestionState {
                override val results: Flow<List<BasicRelaySetupInfo>> =
                    MutableStateFlow(suggestions)

                override fun processInput(input: String) {
                    // No-op: this anonymous IRelaySuggestionState is preview-only and
                    // serves the static suggestions flow above. Live input filtering
                    // would only matter in an interactive run, which a @Preview never
                    // reaches.
                }

                override fun reset() {
                    // No-op: see processInput above.
                }
            }
        }

    val accountViewModel = mockAccountViewModel()

    ThemeComparisonColumn {
        RelayUrlEditField(
            onNewRelay = {},
            relaySuggestions = relaySuggestions,
            nip11CachedRetriever = Nip11CachedRetriever(Nip11Fetcher.Offline),
            modifier = Modifier,
            accountViewModel = accountViewModel,
            nav = EmptyNav(),
        )
    }
}

@Composable
fun RelayUrlEditField(
    onNewRelay: (NormalizedRelayUrl) -> Unit,
    modifier: Modifier,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    RelayUrlEditField(
        onNewRelay = onNewRelay,
        nip11CachedRetriever = LocalCache.appHost.nip11Cache,
        modifier = modifier,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

@Composable
fun RelayUrlEditField(
    onNewRelay: (NormalizedRelayUrl) -> Unit,
    nip11CachedRetriever: Nip11CachedRetriever,
    modifier: Modifier,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val relaySuggestions = remember { RelaySuggestionState() }
    RelayUrlEditField(
        onNewRelay = onNewRelay,
        relaySuggestions = relaySuggestions,
        nip11CachedRetriever = nip11CachedRetriever,
        modifier = modifier,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

@Composable
fun RelayUrlEditField(
    onNewRelay: (NormalizedRelayUrl) -> Unit,
    relaySuggestions: IRelaySuggestionState,
    nip11CachedRetriever: Nip11CachedRetriever,
    modifier: Modifier,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    var url by remember { mutableStateOf("") }
    var isInvalid by remember { mutableStateOf(false) }

    fun submitRelay() {
        if (url.isNotBlank()) {
            val relay = RelayUrlNormalizer.normalizeOrNull(url)
            if (relay != null) {
                onNewRelay(relay)
                url = ""
                isInvalid = false
                relaySuggestions.reset()
            } else {
                // Without this the Add button is a silent no-op, which reads as a broken button.
                // Bare IPv6 literals are the common way to land here: an overlay-mesh address
                // pasted straight out of `yggdrasilctl getSelf` needs brackets to carry a port.
                isInvalid = true
            }
        }
    }

    Column(modifier) {
        OutlinedTextField(
            label = { Text(text = stringRes(Res.string.add_a_relay)) },
            modifier = Modifier.fillMaxWidth(),
            value = url,
            onValueChange = {
                url = it
                isInvalid = false
                relaySuggestions.processInput(it)
            },
            isError = isInvalid,
            // Null, not an empty lambda: a non-null slot reserves its line height even when it
            // draws nothing, which would pad the field permanently for every user.
            supportingText =
                if (isInvalid) {
                    {
                        Text(
                            text = stringRes(Res.string.relay_url_not_valid),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                } else {
                    null
                },
            placeholder = {
                Text(
                    text = "server.com",
                    color = MaterialTheme.colorScheme.placeholderText,
                    maxLines = 1,
                )
            },
            singleLine = true,
            keyboardOptions =
                KeyboardOptions.Default.copy(
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Go,
                    capitalization = KeyboardCapitalization.None,
                    keyboardType = KeyboardType.Text,
                ),
            keyboardActions =
                KeyboardActions(
                    onGo = { submitRelay() },
                ),
            trailingIcon = {
                Button(
                    onClick = { submitRelay() },
                    shape = ButtonBorder,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor =
                                if (url.isNotBlank()) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.placeholderText
                                },
                        ),
                    modifier = StdEndPadding,
                ) {
                    Text(text = stringRes(id = Res.string.add), color = Color.White)
                }
            },
        )

        Card(
            modifier = Modifier.padding(horizontal = 1.dp),
            elevation = cardElevation(5.dp),
            shape = PopupUpEffect,
        ) {
            ShowRelaySuggestionList(
                relaySuggestions = relaySuggestions,
                onSelect = { relay ->
                    url = relay.url
                    relaySuggestions.reset()
                },
                modifier = HalfHorzPadding,
                nip11CachedRetriever = nip11CachedRetriever,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}
