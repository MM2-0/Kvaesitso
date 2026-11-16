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
package com.vitorpamplona.amethyst.commons.ui.actions.nestsServers

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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.nests_servers_add_auth_field
import com.vitorpamplona.amethyst.commons.resources.nests_servers_add_pair_button
import com.vitorpamplona.amethyst.commons.resources.nests_servers_add_recommended
import com.vitorpamplona.amethyst.commons.resources.nests_servers_add_relay_field
import com.vitorpamplona.amethyst.commons.resources.nests_servers_auth_label
import com.vitorpamplona.amethyst.commons.resources.nests_servers_empty
import com.vitorpamplona.amethyst.commons.resources.nests_servers_explainer
import com.vitorpamplona.amethyst.commons.resources.nests_servers_my_explainer
import com.vitorpamplona.amethyst.commons.resources.nests_servers_my_section
import com.vitorpamplona.amethyst.commons.resources.nests_servers_recommended_explainer
import com.vitorpamplona.amethyst.commons.resources.nests_servers_recommended_section
import com.vitorpamplona.amethyst.commons.resources.nests_servers_relay_label
import com.vitorpamplona.amethyst.commons.resources.nests_servers_remove
import com.vitorpamplona.amethyst.commons.resources.nests_servers_title
import com.vitorpamplona.amethyst.commons.resources.nests_servers_use_defaults
import com.vitorpamplona.amethyst.commons.ui.actions.mediaServers.AllMediaServersScreen
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.SavingTopBar
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.SettingsCategory
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.SettingsCategoryWithButton
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.commons.ui.theme.DoubleVertPadding
import com.vitorpamplona.amethyst.commons.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.commons.ui.theme.SettingsCategoryFirstModifier
import com.vitorpamplona.amethyst.commons.ui.theme.SettingsCategorySpacingModifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size10dp
import com.vitorpamplona.amethyst.commons.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.tags.references.HttpUrlFormatter
import com.vitorpamplona.quartz.nip53LiveActivities.nestsServers.NestsServersEvent

/**
 * Settings screen for the user's preferred audio-room (NIP-53 / nests)
 * MoQ host servers — kind 10112 [NestsServersEvent].
 *
 * Mirrors [AllMediaServersScreen] — top bar with Cancel / Save, list
 * of saved servers with delete buttons, edit-fields to add a new
 * (relay, auth) pair, and a "Use defaults" row for one-tap inclusion
 * of `nostrnests.com`. Each server entry is two URLs because the
 * deployed nostrnests reference puts the moq-relay (WebTransport) and
 * the moq-auth (JWT mint) sidecar on different hosts; collapsing them
 * to a single URL breaks JWT minting.
 *
 * Reachable from `Settings → Audio-room servers`.
 */
@Composable
fun NestsServersScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val viewModel: NestsServersViewModel = rememberViewModel { NestsServersViewModel() }
    viewModel.init(accountViewModel)

    LaunchedEffect(accountViewModel) { viewModel.load() }

    NestsServersScaffold(viewModel) { nav.popBack() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NestsServersScaffold(
    viewModel: NestsServersViewModel,
    onClose: () -> Unit,
) {
    Scaffold(
        topBar = {
            SavingTopBar(
                titleRes = Res.string.nests_servers_title,
                onCancel = {
                    viewModel.refresh()
                    onClose()
                },
                onPost = {
                    viewModel.save()
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
                text = stringRes(id = Res.string.nests_servers_explainer),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.grayText,
            )
            NestsServersBody(viewModel)
        }
    }
}

@Composable
private fun NestsServersBody(viewModel: NestsServersViewModel) {
    val servers by viewModel.servers.collectAsStateWithLifecycle()

    LazyColumn(
        verticalArrangement = Arrangement.SpaceAround,
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = FeedPadding,
    ) {
        item {
            SettingsCategory(
                Res.string.nests_servers_my_section,
                Res.string.nests_servers_my_explainer,
                SettingsCategoryFirstModifier,
            )
        }

        if (servers.isEmpty()) {
            item {
                Text(
                    text = stringRes(id = Res.string.nests_servers_empty),
                    modifier = DoubleVertPadding,
                )
            }
        } else {
            itemsIndexed(servers, key = { _, s -> "saved-${s.relay}" }) { _, entry ->
                NestsServerRow(
                    server = entry,
                    isAmethystDefault = false,
                    onAction = { viewModel.removeServer(entry.relay) },
                )
            }
        }

        item {
            Spacer(modifier = StdVertSpacer)
            NestsServerPairEditField { relay, auth ->
                viewModel.addServer(relay, auth)
            }
        }

        item {
            SettingsCategoryWithButton(
                title = Res.string.nests_servers_recommended_section,
                description = Res.string.nests_servers_recommended_explainer,
                modifier = SettingsCategorySpacingModifier,
            ) {
                OutlinedButton(
                    onClick = { viewModel.addServerList(DEFAULT_NESTS_SERVERS) },
                ) {
                    Text(text = stringRes(id = Res.string.nests_servers_use_defaults))
                }
            }
        }

        itemsIndexed(DEFAULT_NESTS_SERVERS, key = { _, s -> "proposed-${s.relay}" }) { _, entry ->
            NestsServerRow(
                server = entry,
                isAmethystDefault = true,
                onAction = { viewModel.addServer(entry.relay, entry.auth) },
            )
        }
    }
}

@Composable
private fun NestsServerRow(
    server: NestsServerEntry,
    isAmethystDefault: Boolean,
    onAction: () -> Unit,
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
                text = server.name.replaceFirstChar(Char::titlecase),
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(modifier = StdVertSpacer)
            Text(
                text = "${stringRes(Res.string.nests_servers_relay_label)}: ${server.relay}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.grayText,
            )
            Text(
                text = "${stringRes(Res.string.nests_servers_auth_label)}: ${server.auth}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.grayText,
            )
        }
        Row(horizontalArrangement = Arrangement.End) {
            IconButton(onClick = onAction) {
                Icon(
                    symbol = if (isAmethystDefault) MaterialSymbols.Add else MaterialSymbols.Delete,
                    contentDescription =
                        if (isAmethystDefault) {
                            stringRes(id = Res.string.nests_servers_add_recommended)
                        } else {
                            stringRes(id = Res.string.nests_servers_remove)
                        },
                )
            }
        }
    }
}

/**
 * Two-field editor for a (relay, auth) pair plus an Add button. The
 * Add button is enabled only once both URLs parse — we don't try to
 * auto-derive the auth URL on input even though [NestsServersEvent.deriveAuthUrl]
 * exists; making the user paste both forces them to think about which
 * deployment they're on, which avoids the moq.* / moq-auth.* host
 * collision that produced the 4443-TCP connection bug.
 */
@Composable
private fun NestsServerPairEditField(onAdd: (relay: String, auth: String) -> Unit) {
    var relay by remember { mutableStateOf("") }
    var auth by remember { mutableStateOf("") }
    val canSubmit by
        remember {
            derivedStateOf {
                relay.isNotBlank() &&
                    auth.isNotBlank() &&
                    HttpUrlFormatter.isValidUrl(relay) &&
                    HttpUrlFormatter.isValidUrl(auth)
            }
        }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Size10dp),
    ) {
        OutlinedTextField(
            label = { Text(text = stringRes(Res.string.nests_servers_add_relay_field)) },
            modifier = Modifier.fillMaxWidth(),
            value = relay,
            onValueChange = { relay = it },
            placeholder = {
                Text(
                    text = "https://moq.example.com:4443",
                    color = MaterialTheme.colorScheme.placeholderText,
                    maxLines = 1,
                )
            },
            singleLine = true,
        )
        OutlinedTextField(
            label = { Text(text = stringRes(Res.string.nests_servers_add_auth_field)) },
            modifier = Modifier.fillMaxWidth(),
            value = auth,
            onValueChange = { auth = it },
            placeholder = {
                Text(
                    text = "https://moq-auth.example.com",
                    color = MaterialTheme.colorScheme.placeholderText,
                    maxLines = 1,
                )
            },
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    if (canSubmit) {
                        onAdd(
                            HttpUrlFormatter.normalize(relay),
                            HttpUrlFormatter.normalize(auth),
                        )
                        relay = ""
                        auth = ""
                    }
                },
                shape = ButtonBorder,
                enabled = canSubmit,
            ) {
                Text(stringRes(Res.string.nests_servers_add_pair_button))
            }
        }
    }
}

/**
 * Built-in suggestion list shown under "Recommended servers". Today
 * just the public `nostrnests.com` deployment; add new entries here
 * as community-run moq-rs / moq-auth instances come online.
 *
 * Each entry carries the relay and auth URLs side-by-side. They are
 * stored verbatim into the kind-10112 `["server", relay, auth]` tag
 * and pre-fill the kind-30312 `streaming` and `auth` tags when the
 * user starts a room.
 */
val DEFAULT_NESTS_SERVERS: List<NestsServerEntry> =
    listOf(
        NestsServerEntry(
            name = "nostrnests.com",
            relay = "https://moq.nostrnests.com:4443",
            auth = "https://moq-auth.nostrnests.com",
        ),
    )
