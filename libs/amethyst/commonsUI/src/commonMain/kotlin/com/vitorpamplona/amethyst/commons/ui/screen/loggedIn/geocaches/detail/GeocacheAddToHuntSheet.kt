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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.geocaches.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.feeds.FeedState
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.geocache_add_to_hunt
import com.vitorpamplona.amethyst.commons.resources.geocache_hunt_already_on
import com.vitorpamplona.amethyst.commons.resources.geocache_hunt_caches
import com.vitorpamplona.amethyst.commons.resources.geocache_hunt_new
import com.vitorpamplona.amethyst.commons.resources.geocache_hunt_none_yet
import com.vitorpamplona.amethyst.commons.ui.components.rememberModalSheetState
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nipCCGeocaching.curation.GeocacheCurationListEvent
import com.vitorpamplona.quartz.nipCCGeocaching.curation.GeocacheCurationRevision
import com.vitorpamplona.quartz.utils.runCatchingCancellable
import kotlinx.coroutines.launch

/**
 * "Add to a hunt", for hunts that already exist.
 *
 * Without this the action only ever created a *new* hunt, which made curation effectively
 * write-once: the moment a player wants a second cache on a route they already built, the app
 * had no answer. The sheet lists the hunts the signed-in user owns — someone else's list is
 * theirs to curate, and a relay would reject our signature on it anyway — and republishes the
 * chosen one with the cache appended.
 *
 * A hunt that already contains this cache is shown but not tappable, rather than hidden. Hiding
 * it would leave a player wondering where their list went; saying "already on this hunt" answers
 * the question they actually have.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeocacheAddToHuntSheet(
    cache: Address,
    accountViewModel: AccountViewModel,
    onNewHunt: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var working by remember { mutableStateOf(false) }

    val huntState by accountViewModel.feedStates.geocacheHuntsFeed.feedContent
        .collectAsStateWithLifecycle()

    val me = accountViewModel.userProfile().pubkeyHex

    val mine =
        when (val state = huntState) {
            is FeedState.Loaded -> rememberMyHunts(state, me)
            else -> emptyList()
        }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalSheetState()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text(
                text = stringRes(Res.string.geocache_add_to_hunt),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        onDismiss()
                        onNewHunt()
                    }.padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    symbol = MaterialSymbols.Add,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringRes(Res.string.geocache_hunt_new),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            if (mine.isEmpty()) {
                Text(
                    text = stringRes(Res.string.geocache_hunt_none_yet),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 14.dp),
                )
            }

            mine.forEach { hunt ->
                val already = remember(hunt, cache) { GeocacheCurationRevision.contains(hunt, cache) }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !already && !working) {
                            working = true
                            scope.launch {
                                runCatchingCancellable {
                                    accountViewModel.account.signAndComputeBroadcast(
                                        GeocacheCurationRevision.withCache(hunt, cache),
                                    )
                                }
                                working = false
                                onDismiss()
                            }
                        }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = hunt.title()?.trim().orEmpty(),
                            style = MaterialTheme.typography.bodyLarge,
                            color =
                                if (already) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                        )
                        Text(
                            text =
                                if (already) {
                                    stringRes(Res.string.geocache_hunt_already_on)
                                } else {
                                    val cacheCount = hunt.geocaches().size
                                    pluralStringRes(Res.plurals.geocache_hunt_caches, cacheCount, cacheCount)
                                },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (already) {
                        Icon(
                            symbol = MaterialSymbols.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // The sheet's last row has to clear the system navigation, and an empty Text was
            // standing in for that. Spacer + the real inset is both honest and correct on
            // gesture navigation, where the reserved band is much smaller.
            Spacer(Modifier.navigationBarsPadding().height(16.dp))
        }
    }
}

/**
 * The reader's own hunts, collected from the feed's own flow.
 *
 * [FeedState.Loaded] is handed out once and mutated through its inner flow, so keying a
 * `remember` on the outer state alone froze this list at whatever had arrived when the sheet
 * opened — a hunt created moments earlier was missing from the very sheet that offers to add a
 * cache to it.
 */
@Composable
private fun rememberMyHunts(
    state: FeedState.Loaded,
    me: HexKey,
): List<GeocacheCurationListEvent> {
    val loaded by state.feed.collectAsStateWithLifecycle()

    return remember(loaded, me) {
        loaded.list.mapNotNull { it.event as? GeocacheCurationListEvent }.filter { it.pubKey == me }
    }
}
