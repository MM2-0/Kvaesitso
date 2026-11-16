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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.geocaches.hunt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.geocaches.hunt.NewGeocacheHuntViewModel
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.geocache_hunt_banner
import com.vitorpamplona.amethyst.commons.resources.geocache_hunt_description
import com.vitorpamplona.amethyst.commons.resources.geocache_hunt_move_down
import com.vitorpamplona.amethyst.commons.resources.geocache_hunt_move_up
import com.vitorpamplona.amethyst.commons.resources.geocache_hunt_needs_caches
import com.vitorpamplona.amethyst.commons.resources.geocache_hunt_pick_caches
import com.vitorpamplona.amethyst.commons.resources.geocache_hunt_remove
import com.vitorpamplona.amethyst.commons.resources.geocache_hunt_title
import com.vitorpamplona.amethyst.commons.resources.geocache_new_publish
import com.vitorpamplona.amethyst.commons.resources.geocache_unnamed
import com.vitorpamplona.amethyst.commons.resources.route_edit_geocache_hunt
import com.vitorpamplona.amethyst.commons.resources.route_new_geocache_hunt
import com.vitorpamplona.amethyst.commons.resources.save
import com.vitorpamplona.amethyst.commons.ui.components.SpellCheckedOutlinedTextField
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.ActionTopBar
import com.vitorpamplona.amethyst.commons.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nipCCGeocaching.listing.GeocacheListingEvent
import kotlinx.coroutines.launch

/**
 * The hunt composer.
 *
 * Caches are reordered with explicit up/down buttons rather than drag handles: the order is the
 * hunt's content, so it needs to be adjustable precisely and reversibly, and a drag that
 * half-lands is worse than two taps.
 *
 * Caches are added from the hub — "Add to a hunt" on a cache's detail screen — rather than
 * through a picker here, because the moment someone wants a cache in a hunt is the moment they
 * are looking at the cache.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewGeocacheHuntScreen(
    nav: INav,
    accountViewModel: AccountViewModel,
    seedCache: String? = null,
    editKind: Int? = null,
    editPubKeyHex: String? = null,
    editDTag: String? = null,
) {
    val model: NewGeocacheHuntViewModel = rememberViewModel { NewGeocacheHuntViewModel() }
    val scope = rememberCoroutineScope()

    remember(editKind, editPubKeyHex, editDTag, seedCache) {
        model.init(accountViewModel.account)
        if (editKind != null && editPubKeyHex != null && editDTag != null) {
            model.loadForEdit(editKind, editPubKeyHex, editDTag)
        }
        // Arrived from a cache's "Add to a hunt": start the itinerary with it.
        seedCache?.let { Address.parse(it) }?.let { model.addCache(it) }
        true
    }

    Scaffold(
        topBar = {
            ActionTopBar(
                postRes = if (model.isEditing) Res.string.save else Res.string.geocache_new_publish,
                titleRes = if (model.isEditing) Res.string.route_edit_geocache_hunt else Res.string.route_new_geocache_hunt,
                isActive = { model.isValid() && !model.isPublishing.value },
                onCancel = { nav.popBack() },
                onPost = { scope.launch { if (model.publish()) nav.popBack() } },
            )
        },
    ) { pad ->
        Column(
            Modifier
                .padding(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding())
                .consumeWindowInsets(pad)
                .imePaddingSafe()
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SpellCheckedOutlinedTextField(
                value = model.title.value,
                onValueChange = { model.title.value = it },
                label = { Text(stringRes(Res.string.geocache_hunt_title)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            SpellCheckedOutlinedTextField(
                value = model.description.value,
                onValueChange = { model.description.value = it },
                label = { Text(stringRes(Res.string.geocache_hunt_description)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )

            OutlinedTextField(
                value = model.bannerUrl.value,
                onValueChange = { model.bannerUrl.value = it },
                label = { Text(stringRes(Res.string.geocache_hunt_banner)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Text(
                text = stringRes(Res.string.geocache_hunt_pick_caches),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )

            if (model.caches.isEmpty()) {
                Text(
                    text = stringRes(Res.string.geocache_hunt_needs_caches),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            model.caches.forEachIndexed { index, address ->
                LoadAddressableNote(address) { cacheNote ->
                    // Same reason as the hunt screen's stops: a cache added by naddr has no event
                    // in the cache yet, so this has to ask the relays and watch rather than read.
                    val listing = cacheNote?.let { observeNoteEvent<GeocacheListingEvent>(it, accountViewModel).value }

                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Text(
                            text =
                                listing?.cacheName()?.trim()?.ifBlank { null }
                                    ?: stringRes(Res.string.geocache_unnamed),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )

                        IconButton(onClick = { model.move(index, index - 1) }, enabled = index > 0) {
                            Icon(
                                symbol = MaterialSymbols.ArrowUpward,
                                contentDescription = stringRes(Res.string.geocache_hunt_move_up),
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        IconButton(
                            onClick = { model.move(index, index + 1) },
                            enabled = index < model.caches.size - 1,
                        ) {
                            Icon(
                                symbol = MaterialSymbols.ArrowDownward,
                                contentDescription = stringRes(Res.string.geocache_hunt_move_down),
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        IconButton(onClick = { model.removeCache(address) }) {
                            Icon(
                                symbol = MaterialSymbols.Close,
                                contentDescription = stringRes(Res.string.geocache_hunt_remove),
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
