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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.buzz

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.buzz.BuzzNewDmViewModel
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserName
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.buzz_dm_add_hint
import com.vitorpamplona.amethyst.commons.resources.buzz_dm_new
import com.vitorpamplona.amethyst.commons.resources.buzz_dm_opening
import com.vitorpamplona.amethyst.commons.resources.buzz_dm_recipients
import com.vitorpamplona.amethyst.commons.resources.buzz_dm_remove
import com.vitorpamplona.amethyst.commons.resources.buzz_dm_start
import com.vitorpamplona.amethyst.commons.resources.buzz_dm_workspace
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl

/**
 * Start a new Buzz DM: pick a workspace relay, add 1-8 people (search by name — this workspace's
 * members rank first — or paste an npub/hex), and open. On the relay's confirmation the screen jumps
 * straight into the shared [Route.RelayGroup] chat for the new conversation; on timeout it falls
 * back to the DM inbox.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BuzzNewDmScreen(
    relayUrl: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val viewModel: BuzzNewDmViewModel = rememberViewModel(key = "BuzzNewDm-$relayUrl") { BuzzNewDmViewModel() }
    viewModel.bind(accountViewModel.account, relayUrl)

    val relays by viewModel.relays.collectAsStateWithLifecycle()
    val selectedRelay by viewModel.relay.collectAsStateWithLifecycle()
    val participants by viewModel.participants.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()

    var inputError by remember { mutableStateOf<String?>(null) }

    val sending = status is BuzzNewDmViewModel.Status.Sending

    Scaffold(
        topBar = { TopBarWithBackButton(stringRes(Res.string.buzz_dm_new), nav) },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Workspace relay — a DM lives on exactly one Buzz relay (its tenant).
            if (relays.isNotEmpty()) {
                Text(
                    text = stringRes(Res.string.buzz_dm_workspace),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    relays.forEach { relay ->
                        FilterChip(
                            selected = relay == selectedRelay,
                            onClick = { viewModel.selectRelay(relay) },
                            label = { Text(relay.displayUrl(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
            }

            // Recipients
            Text(
                text = stringRes(Res.string.buzz_dm_recipients),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            if (participants.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    participants.forEach { hex ->
                        ParticipantChip(hex, accountViewModel, nav) { viewModel.removeParticipant(hex) }
                    }
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = {
                    viewModel.updateQuery(it)
                    inputError = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringRes(Res.string.buzz_dm_add_hint)) },
                leadingIcon = { Icon(symbol = MaterialSymbols.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                singleLine = true,
                isError = inputError != null,
                supportingText = inputError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions =
                    KeyboardActions(
                        // Enter accepts a pasted npub/hex directly — the escape hatch for someone
                        // not yet in the local cache, so they'd never surface in the search list.
                        onDone = {
                            if (query.isNotBlank()) {
                                inputError = viewModel.addRawKey(query)
                            }
                        },
                    ),
            )

            (status as? BuzzNewDmViewModel.Status.Error)?.let {
                Text(it.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            // Search results — workspace members first — fill the space above the pinned Start button.
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                items(suggestions, key = { it }) { hex ->
                    // Surface the same errors the Enter/paste path shows (e.g. the participant ceiling),
                    // instead of a tap that silently does nothing.
                    SuggestionRow(hex, accountViewModel, nav) { inputError = viewModel.addParticipant(hex) }
                }
            }

            Button(
                onClick = {
                    viewModel.start { groupId ->
                        if (groupId != null) {
                            nav.newStack(Route.RelayGroup(groupId.id, groupId.relayUrl.url))
                        } else {
                            nav.newStack(Route.BuzzDmList(relayUrl))
                        }
                    }
                },
                enabled = !sending && participants.isNotEmpty() && selectedRelay != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (sending) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(stringRes(Res.string.buzz_dm_opening))
                } else {
                    Icon(symbol = MaterialSymbols.AutoMirrored.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(stringRes(Res.string.buzz_dm_start))
                }
            }
        }
    }
}

/** One tappable search result — avatar + resolved name — that adds the user as a recipient. */
@Composable
private fun SuggestionRow(
    hex: HexKey,
    accountViewModel: AccountViewModel,
    nav: INav,
    onClick: () -> Unit,
) {
    val user: User = remember(hex) { LocalCache.getOrCreateUser(hex) }
    val name by observeUserName(user, accountViewModel)

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        UserPicture(hex, 34.dp, accountViewModel = accountViewModel, nav = nav)
        Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
    }
}

/** A removable chip for one added recipient — avatar + resolved name + a clear affordance. */
@Composable
private fun ParticipantChip(
    hex: HexKey,
    accountViewModel: AccountViewModel,
    nav: INav,
    onRemove: () -> Unit,
) {
    val user: User = remember(hex) { LocalCache.getOrCreateUser(hex) }
    val name by observeUserName(user, accountViewModel)

    InputChip(
        selected = false,
        onClick = onRemove,
        label = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        avatar = { UserPicture(hex, 22.dp, accountViewModel = accountViewModel, nav = nav) },
        trailingIcon = {
            Icon(symbol = MaterialSymbols.Close, contentDescription = stringRes(Res.string.buzz_dm_remove), modifier = Modifier.size(16.dp))
        },
    )
}
