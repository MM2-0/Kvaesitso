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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.nip53LiveActivities.header

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.CreationExtras
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.nip53LiveActivities.LiveActivitiesChannel
import com.vitorpamplona.amethyst.commons.nip53LiveActivities.TopZapperEntry
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.chat_zap_anonymous
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.note.ZapIcon
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.BitcoinOrange
import com.vitorpamplona.amethyst.commons.ui.theme.Size16Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size24dp
import com.vitorpamplona.amethyst.commons.util.showAmountInteger
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.LiveStreamTopZappersViewModel
import com.vitorpamplona.quartz.utils.BigDecimal
import kotlin.reflect.KClass

/**
 * Horizontally-scrollable leaderboard of the top zappers on a live stream. Matches
 * zap.stream's TopZappers look: pill chips with avatar + lightning + sats.
 *
 * State is owned by [LiveStreamTopZappersViewModel], which maintains the aggregation
 * incrementally off the UI thread and publishes a stable `List<TopZapperEntry>`.
 */
@Composable
fun LiveStreamTopZappers(
    channel: LiveActivitiesChannel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val topZappersVm: LiveStreamTopZappersViewModel =
        rememberViewModel(
            key = "TopZappers-${channel.address.toValue()}",
            factory =
                object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(
                        modelClass: KClass<T>,
                        extras: CreationExtras,
                    ): T = LiveStreamTopZappersViewModel(channel) as T
                },
        )

    // Track the goal note lifecycle — pass it to the VM as it resolves, clear on channel change.
    val goalId = channel.info?.goalEventId()
    if (goalId != null) {
        var goalNoteHolder by remember(goalId) {
            mutableStateOf(LocalCache.getNoteIfExists(goalId))
        }
        LaunchedEffect(goalId) {
            if (goalNoteHolder == null) {
                goalNoteHolder = LocalCache.checkGetOrCreateNote(goalId)
            }
            topZappersVm.setGoalNote(goalNoteHolder)
        }
    } else {
        LaunchedEffect(channel) { topZappersVm.setGoalNote(null) }
    }

    val entries by topZappersVm.topZappers.collectAsStateWithLifecycle()

    if (entries.isEmpty()) return

    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
    ) {
        items(entries, key = { it.bucketKey }) { entry ->
            ZapperPill(entry, accountViewModel, nav)
        }
    }
}

@Composable
private fun ZapperPill(
    entry: TopZapperEntry,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val clickModifier =
        if (entry.isAnonymous) {
            Modifier
        } else {
            Modifier.clickable { nav.nav(Route.Profile(entry.bucketKey)) }
        }

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = clickModifier,
    ) {
        Row(
            // Min height matches the avatar so text-only chips (Anonymous) stay aligned
            // with the avatar chips in the horizontal row.
            modifier =
                Modifier
                    .heightIn(min = Size24dp)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (entry.isAnonymous) {
                Text(
                    text = stringRes(Res.string.chat_zap_anonymous),
                    style = MaterialTheme.typography.labelMedium,
                )
            } else {
                UserPicture(
                    userHex = entry.bucketKey,
                    size = Size24dp,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }
            ZapIcon(Size16Modifier, BitcoinOrange)
            Text(
                text = showAmountInteger(BigDecimal(entry.totalSats)),
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.padding(start = 2.dp))
        }
    }
}
