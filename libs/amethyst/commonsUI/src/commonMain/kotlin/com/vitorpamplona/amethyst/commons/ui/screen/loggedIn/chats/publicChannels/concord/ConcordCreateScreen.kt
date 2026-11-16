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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.concord_create_action
import com.vitorpamplona.amethyst.commons.resources.concord_create_failed
import com.vitorpamplona.amethyst.commons.resources.concord_create_relays
import com.vitorpamplona.amethyst.commons.resources.concord_create_relays_desc
import com.vitorpamplona.amethyst.commons.resources.concord_create_title
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.ActionTopBar
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.concord.cord02Community.ImagePointer
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.launch

/**
 * Create a new Concord Channel (encrypted community) from Amethyst. Mints the
 * genesis (metadata + #general), publishes it to the chosen relays (or the
 * account's outbox by default), joins it, and opens the new community.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConcordCreateScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val name = remember { mutableStateOf("") }
    val about = remember { mutableStateOf("") }
    val icon = remember { mutableStateOf<ImagePointer?>(null) }
    val relays = remember { mutableListOf<NormalizedRelayUrl>().toMutableStateList() }
    var working by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val canCreate = name.value.isNotBlank() && concordNameFits(name.value) && !working
    val create: () -> Unit = create@{
        if (name.value.isBlank() || working) return@create
        working = true
        scope.launch {
            accountViewModel.reportSignerErrors {
                val communityId =
                    try {
                        accountViewModel.account.concord.createConcordCommunity(
                            name = name.value.trim(),
                            description = about.value.trim().ifBlank { null },
                            relays = relays.map { it.url },
                            icon = icon.value,
                        )
                    } finally {
                        // Always re-enable — a thrown create would otherwise strand the button.
                        working = false
                    }
                // Replace this form with the new community, as the Marmot and relay-group creators
                // do. newStack only popped up to the community route itself, which was not on the
                // stack, so Back from the new community reopened a filled-in create form.
                if (communityId != null) {
                    nav.popUpTo(Route.ConcordServer(communityId), Route.ConcordCreate::class)
                } else {
                    accountViewModel.toastManager.toast(Res.string.concord_create_title, Res.string.concord_create_failed)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            ActionTopBar(
                postRes = Res.string.concord_create_action,
                titleRes = Res.string.concord_create_title,
                isActive = { canCreate },
                onCancel = { nav.popBack() },
                onPost = create,
                isBusy = { working },
            )
        },
    ) { padding ->
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
                robotSeed = "concord-new",
                accountViewModel = accountViewModel,
            )

            ConcordSectionHeader(
                title = stringRes(Res.string.concord_create_relays),
                description = stringRes(Res.string.concord_create_relays_desc),
            )
            ConcordRelayListEditor(
                relays = relays,
                onRemove = { relays.remove(it) },
                onAdd = { if (it !in relays) relays.add(it) },
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

/** A section header (title + one-line description) matching the NIP-29 metadata form. */
@Composable
fun ConcordSectionHeader(
    title: String,
    description: String? = null,
) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        description?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
