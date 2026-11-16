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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.concord

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.back
import com.vitorpamplona.amethyst.commons.resources.concord_create_relays
import com.vitorpamplona.amethyst.commons.resources.concord_edit_relays_desc
import com.vitorpamplona.amethyst.commons.resources.concord_edit_save
import com.vitorpamplona.amethyst.commons.resources.concord_edit_title
import com.vitorpamplona.amethyst.commons.resources.concord_timer_desc
import com.vitorpamplona.amethyst.commons.resources.concord_timer_title
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.concord.ConcordTimerPicker
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.concord.datasource.ConcordChannelSubscription
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.concord.cord02Community.ImagePointer
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon as SymbolIcon

/**
 * Edit a Concord community's metadata (name / description / icon). Reuses the shared
 * [ConcordMetadataFields] hero + fields, prefilled from the folded Control Plane, and
 * saves a new metadata edition via [com.vitorpamplona.amethyst.commons.model.Account.editConcordMetadata]
 * — honored on fold only when this account holds MANAGE_METADATA (or is the owner).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConcordEditScreen(
    communityId: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val account = accountViewModel.account

    // Self-sufficient: mount the plane subscription so a deep link folds the community, and
    // re-resolve the session on each revision so it resolves once the session exists.
    ConcordChannelSubscription(accountViewModel.dataSources().concordChannels, accountViewModel)

    val revision by account.concordSessions.revision.collectAsStateWithLifecycle()
    val session = remember(account, communityId, revision) { account.concordSessions.sessionFor(communityId) }
    val state by (session?.state ?: remember { MutableStateFlow(null) }).collectAsStateWithLifecycle()

    val name = remember { mutableStateOf("") }
    val about = remember { mutableStateOf("") }
    val icon = remember { mutableStateOf<ImagePointer?>(null) }
    val banner = remember { mutableStateOf<ImagePointer?>(null) }
    val relays = remember { mutableStateListOf<NormalizedRelayUrl>() }
    // CORD-08 timer, seconds (0 = off): the folded value, and the one picked here.
    var foldedTimer by remember { mutableStateOf(0L) }
    var timer by remember { mutableStateOf(0L) }
    var prefilled by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Seed the fields once, the first time the folded metadata is available. Relays come from the
    // folded metadata when present, else from this account's list entry (the bootstrap set).
    LaunchedEffect(state?.metadata) {
        val md = state?.metadata
        if (!prefilled && md != null) {
            name.value = md.name
            about.value = md.description.orEmpty()
            icon.value = md.icon
            banner.value = md.banner
            val seededRelays = (md.relays.takeIf { it.isNotEmpty() } ?: session?.entry?.relays.orEmpty())
            relays.clear()
            relays.addAll(seededRelays.mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) })
            foldedTimer = md.messageExpirationSecs() ?: 0L
            timer = foldedTimer
            prefilled = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringRes(Res.string.concord_edit_title), maxLines = 1) },
                navigationIcon = {
                    if (nav.canPop()) {
                        IconButton(onClick = { nav.popBack() }) {
                            SymbolIcon(symbol = MaterialSymbols.AutoMirrored.ArrowBack, contentDescription = stringRes(Res.string.back))
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (session == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ConcordMetadataFields(
                name = name,
                about = about,
                icon = icon,
                robotSeed = communityId,
                accountViewModel = accountViewModel,
                banner = banner,
            )

            ConcordSectionHeader(
                title = stringRes(Res.string.concord_create_relays),
                description = stringRes(Res.string.concord_edit_relays_desc),
            )
            ConcordRelayListEditor(
                relays = relays,
                onRemove = { relays.remove(it) },
                onAdd = { if (it !in relays) relays.add(it) },
                accountViewModel = accountViewModel,
                nav = nav,
            )

            // CORD-08: this screen is only reachable with MANAGE_METADATA and the Control write key,
            // the same predicate as every other field here.
            ConcordSectionHeader(
                title = stringRes(Res.string.concord_timer_title),
                description = stringRes(Res.string.concord_timer_desc),
            )
            ConcordTimerPicker(selected = timer, onSelect = { timer = it }, enabled = !working)

            Button(
                onClick = {
                    if (name.value.isBlank() || working) return@Button
                    working = true
                    scope.launch {
                        accountViewModel.reportSignerErrors {
                            val ok =
                                try {
                                    account.concord.editConcordMetadata(
                                        communityId = communityId,
                                        name = name.value.trim(),
                                        description = about.value.trim().ifBlank { null },
                                        icon = icon.value,
                                        banner = banner.value,
                                        relays = relays.map { it.url },
                                    ) &&
                                        // A timer change is its own edition, chained on the one above, and
                                        // posts the CORD-08 §4 notice into each channel.
                                        (timer == foldedTimer || account.concord.setConcordMessageExpiration(communityId, timer.takeIf { it > 0 }))
                                } finally {
                                    // Always re-enable — a thrown save would otherwise strand the button.
                                    working = false
                                }
                            if (ok) nav.popBack()
                        }
                    }
                },
                enabled = name.value.isNotBlank() && concordNameFits(name.value) && !working,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text(stringRes(Res.string.concord_edit_save))
            }
        }
    }
}
