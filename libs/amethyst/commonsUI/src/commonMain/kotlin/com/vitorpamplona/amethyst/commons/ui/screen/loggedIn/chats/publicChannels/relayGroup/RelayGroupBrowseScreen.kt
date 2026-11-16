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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.relayGroup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults.cardElevation
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.relay_group_browse_description
import com.vitorpamplona.amethyst.commons.resources.relay_group_browse_go
import com.vitorpamplona.amethyst.commons.resources.relay_group_browse_invalid_url
import com.vitorpamplona.amethyst.commons.resources.relay_group_browse_popular
import com.vitorpamplona.amethyst.commons.resources.relay_group_browse_relay_label
import com.vitorpamplona.amethyst.commons.resources.relay_group_browse_title
import com.vitorpamplona.amethyst.commons.resources.relay_group_browse_your_relays
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarExtensibleWithBackButton
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.common.RelaySuggestionState
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.common.ShowRelaySuggestionList
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.HalfHorzPadding
import com.vitorpamplona.amethyst.commons.ui.theme.PopupUpEffect
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer

/**
 * A handful of widely-used public NIP-29 relays to seed discovery for a user who
 * hasn't joined any group yet. Not exhaustive and not an endorsement — just a
 * starting point; anyone can paste any relay URL above.
 */
private val POPULAR_RELAYS =
    listOf(
        "wss://groups.0xchat.com",
        "wss://communities.nos.social",
    )

/**
 * Discovery entry point for NIP-29 groups: paste any relay URL to browse the
 * channels it hosts (opening [Route.RelayGroupServer]), pick from relays you're
 * already on, or try a popular public relay. This is the "find new groups" flow
 * that the joined-only Messages list can't offer.
 */
@Composable
fun RelayGroupBrowseScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    var relayUrl by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    // Same known-relay autocomplete every other relay field uses: filters the relays the app has
    // already seen (LocalCache.relayHints) by the substring being typed.
    val relaySuggestions = remember { RelaySuggestionState() }
    val suggestionResults by relaySuggestions.results.collectAsStateWithLifecycle(emptyList())

    val joined by accountViewModel.account.relayGroupList.liveRelayGroupServers
        .collectAsStateWithLifecycle()

    fun open(url: String) {
        val normalized = RelayUrlNormalizer.normalizeOrNull(url.trim())
        if (normalized == null) {
            showError = true
            return
        }
        relaySuggestions.reset()
        nav.nav(Route.RelayGroupServer(normalized.url))
    }

    Scaffold(
        topBar = {
            TopBarExtensibleWithBackButton(
                title = {
                    Text(
                        text = stringRes(Res.string.relay_group_browse_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                nav = nav,
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringRes(Res.string.relay_group_browse_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )

            OutlinedTextField(
                value = relayUrl,
                onValueChange = {
                    relayUrl = it
                    showError = false
                    relaySuggestions.processInput(it)
                },
                singleLine = true,
                isError = showError,
                supportingText =
                    if (showError) {
                        { Text(stringRes(Res.string.relay_group_browse_invalid_url)) }
                    } else {
                        null
                    },
                label = { Text(stringRes(Res.string.relay_group_browse_relay_label)) },
                placeholder = { Text("wss://…") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { open(relayUrl) }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )

            // Autocomplete popup: tapping a match opens that relay's group directory directly.
            if (suggestionResults.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    elevation = cardElevation(5.dp),
                    shape = PopupUpEffect,
                ) {
                    ShowRelaySuggestionList(
                        relaySuggestions = relaySuggestions,
                        onSelect = { relay -> open(relay.url) },
                        modifier = HalfHorzPadding,
                        nip11CachedRetriever = LocalCache.appHost.nip11Cache,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }
            }

            Button(
                onClick = { open(relayUrl) },
                enabled = relayUrl.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(stringRes(Res.string.relay_group_browse_go))
            }

            if (joined.isNotEmpty()) {
                SectionHeader(stringRes(Res.string.relay_group_browse_your_relays))
                joined.sorted().forEach { server ->
                    RelayGroupServerRow(server, accountViewModel) { open(server) }
                    HorizontalDivider(thickness = 0.25.dp, color = MaterialTheme.colorScheme.outlineVariant)
                }
            }

            // `joined` holds normalized URLs (trailing-slash form); normalize the
            // literals before comparing so an already-joined popular relay is hidden.
            val joinedNormalized = joined.mapNotNullTo(mutableSetOf()) { RelayUrlNormalizer.normalizeOrNull(it)?.url }
            val suggestions = POPULAR_RELAYS.filter { RelayUrlNormalizer.normalizeOrNull(it)?.url !in joinedNormalized }
            if (suggestions.isNotEmpty()) {
                SectionHeader(stringRes(Res.string.relay_group_browse_popular))
                suggestions.forEach { server ->
                    RelayGroupServerRow(server, accountViewModel) { open(server) }
                    HorizontalDivider(thickness = 0.25.dp, color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}
