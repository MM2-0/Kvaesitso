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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.nip53LiveActivities.LiveActivitiesChannel
import com.vitorpamplona.amethyst.commons.nip53LiveActivities.ui.LiveFlag
import com.vitorpamplona.amethyst.commons.nip53LiveActivities.ui.OfflineFlag
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.channel.observeChannel
import com.vitorpamplona.amethyst.commons.ui.components.CheckIfVideoIsOnline
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.LikeReaction
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.note.ZapReaction
import com.vitorpamplona.amethyst.commons.ui.theme.RowColSpacing
import com.vitorpamplona.amethyst.commons.ui.theme.Size34dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size35dp
import com.vitorpamplona.amethyst.commons.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent

@Composable
fun ShortLiveActivityChannelHeader(
    baseChannel: LiveActivitiesChannel,
    showFlag: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val channelState by observeChannel(baseChannel, accountViewModel)
    val channel = channelState?.channel as? LiveActivitiesChannel ?: return

    ShortLiveActivityChannelHeader(
        name = channel.toBestDisplayName(),
        creator = channel.creator,
        liveActivitiesEvent = channel.info,
        addressableNote = LocalCache.getAddressableNoteIfExists(channel.address),
        showFlag = showFlag,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

@Composable
fun ShortLiveActivityChannelHeader(
    name: String,
    creator: User?,
    liveActivitiesEvent: LiveActivitiesEvent?,
    addressableNote: com.vitorpamplona.amethyst.commons.model.Note?,
    showFlag: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        creator?.let {
            UserPicture(
                user = it,
                size = Size34dp,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }

        Column(
            modifier =
                Modifier
                    .padding(start = 10.dp)
                    .height(35.dp)
                    .weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Live flag is stream-specific (kind:30311) — needs the
        // streaming URL to ping. Only render it when we actually
        // have a [LiveActivitiesEvent].
        if (showFlag && liveActivitiesEvent != null) {
            Row(
                modifier =
                    Modifier
                        .height(Size35dp)
                        .padding(start = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LiveChannelLiveFlag(liveActivitiesEvent, accountViewModel)
            }
        }

        // Likes + zaps are addressable-event reactions (kind:7 +
        // kind:9735) — they work for any kind that registers an
        // addressable note, not just kind:30311. Pulling them out
        // of the `liveActivitiesEvent?.let` gate above lets Nests
        // (kind:30312) and Meeting Rooms (kind:30313) show the same
        // reaction row that streams already have.
        addressableNote?.let {
            Row(
                modifier =
                    Modifier
                        .height(Size35dp)
                        .padding(start = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = RowColSpacing,
            ) {
                LikeReaction(
                    baseNote = it,
                    grayTint = MaterialTheme.colorScheme.onSurface,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
                Spacer(modifier = StdHorzSpacer)
                ZapReaction(
                    baseNote = it,
                    grayTint = MaterialTheme.colorScheme.onSurface,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }
        }
    }
}

/**
 * Just the LIVE / OFFLINE flag for a stream; no reaction icons.
 * Pulled out of [LiveChannelActionOptions] so the reaction row in
 * [ShortLiveActivityChannelHeader] can render independently of
 * whether we hold a [LiveActivitiesEvent].
 */
@Composable
private fun LiveChannelLiveFlag(
    activity: LiveActivitiesEvent,
    accountViewModel: AccountViewModel,
) {
    val isLive by remember(activity) { derivedStateOf { activity.isLive() } }
    val url = activity.streaming()
    if (isLive && url != null) {
        CheckIfVideoIsOnline(url, accountViewModel) { isOnline ->
            if (isOnline) LiveFlag() else OfflineFlag()
        }
    }
}

@Composable
fun LiveChannelActionOptions(
    activity: LiveActivitiesEvent,
    showFlag: Boolean = true,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (showFlag) {
        LiveChannelLiveFlag(activity, accountViewModel)
        Spacer(modifier = StdHorzSpacer)
    }

    val note = remember(activity) { LocalCache.getAddressableNoteIfExists(activity.address()) }
    note?.let {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = RowColSpacing,
        ) {
            LikeReaction(
                baseNote = it,
                grayTint = MaterialTheme.colorScheme.onSurface,
                accountViewModel = accountViewModel,
                nav,
            )
        }
        Spacer(modifier = StdHorzSpacer)
        ZapReaction(
            baseNote = it,
            grayTint = MaterialTheme.colorScheme.onSurface,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}
