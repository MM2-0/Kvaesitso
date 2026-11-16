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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.home.live

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.routeFor
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.model.nip53LiveActivities.LiveActivitiesChannel
import com.vitorpamplona.amethyst.commons.nests.room.activity.NestBridge
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.channel.observeChannelNoteAuthors
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.Gallery
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppPlatform
import com.vitorpamplona.amethyst.commons.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent

@Composable
fun RenderLiveActivityBubble(
    channel: LiveActivitiesChannel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // Audio rooms (kind-30312) share the kind-1311 chat infra with
    // streams (kind-30311), so they ride the same `LiveActivitiesChannel`
    // pump. But channel.info is null for audio rooms (typed to
    // LiveActivitiesEvent), so toBestDisplayName() would fall back
    // to the truncated bech32. Read the kind-30312 addressable
    // directly when the channel's address points to one and use the
    // room name + a launch path that goes straight to NestActivity.
    val meetingEvent =
        remember(channel.address) {
            if (channel.address.kind == MeetingSpaceEvent.KIND) {
                LocalCache.getAddressableNoteIfExists(channel.address)?.event as? MeetingSpaceEvent
            } else {
                null
            }
        }
    val appPlatform = LocalAppPlatform.current
    FilledTonalButton(
        contentPadding = PaddingValues(start = 8.dp, end = 10.dp, bottom = 0.dp, top = 0.dp),
        onClick = {
            if (meetingEvent != null) {
                val service = meetingEvent.service()
                val endpoint = meetingEvent.endpoint()
                val dTag = meetingEvent.address().dTag
                if (!service.isNullOrBlank() && !endpoint.isNullOrBlank() && dTag.isNotBlank()) {
                    NestBridge.set(accountViewModel)
                    appPlatform.openNestRoom(meetingEvent.address().toValue())
                } else {
                    // Fall back to the channel route so the user
                    // still lands somewhere — same as a malformed
                    // streaming kind-30311.
                    nav.nav { routeFor(channel) }
                }
            } else {
                nav.nav { routeFor(channel) }
            }
        },
    ) {
        LiveStatusIndicatorForChannel(
            channel = channel,
            accountViewModel = accountViewModel,
            modifier = Modifier.align(Alignment.CenterVertically),
        )
        Spacer(StdHorzSpacer)
        RenderUsers(channel, accountViewModel, nav)
        Spacer(StdHorzSpacer)
        Text(
            // Audio rooms have a real `room()` name on the addressable;
            // pick that up so the bubble reads "Lounge" instead of
            // "naddr1abc…".
            meetingEvent?.room() ?: channel.toBestDisplayName(),
        )
    }
}

@Composable
fun RenderUsers(
    channel: LiveActivitiesChannel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val authors by observeChannelNoteAuthors(channel, accountViewModel)

    Gallery(authors, Modifier, accountViewModel, nav, 3)
}
