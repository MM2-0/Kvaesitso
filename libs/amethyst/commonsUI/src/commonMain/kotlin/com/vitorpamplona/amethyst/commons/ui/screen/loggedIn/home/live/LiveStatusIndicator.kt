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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.Channel
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.emphChat.EphemeralChatChannel
import com.vitorpamplona.amethyst.commons.model.nip53LiveActivities.LiveActivitiesChannel
import com.vitorpamplona.amethyst.commons.service.isVideoOnline
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.tags.StatusTag as MeetingSpaceStatusTag

@Composable
fun LiveStatusIndicator(
    isOnline: Boolean,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier.size(8.dp),
    ) {
        drawCircle(
            color = if (isOnline) Color.Red else Color.Black,
            radius = size.minDimension / 2,
        )
    }
}

@Composable
fun LiveStatusIndicatorForChannel(
    channel: Channel,
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier,
) {
    val isOnline by produceState(initialValue = false, key1 = channel) {
        while (true) {
            value = checkChannelIsOnline(channel, accountViewModel)
            delay(30_000) // Poll every 30 seconds
        }
    }

    LiveStatusIndicator(
        isOnline = isOnline,
        modifier = modifier,
    )
}

private suspend fun checkChannelIsOnline(
    channel: Channel,
    accountViewModel: AccountViewModel,
): Boolean =
    withContext(Dispatchers.IO) {
        try {
            when (channel) {
                is LiveActivitiesChannel -> {
                    // Audio rooms (kind-30312) ride the same channel
                    // model but channel.info is null (typed to
                    // LiveActivitiesEvent). Their "is live" signal is
                    // the addressable kind-30312's status — surface
                    // the red dot whenever the room is OPEN/PRIVATE.
                    if (channel.address.kind == MeetingSpaceEvent.KIND) {
                        val room = LocalCache.getAddressableNoteIfExists(channel.address)?.event as? MeetingSpaceEvent
                        room?.status() == MeetingSpaceStatusTag.STATUS.LIVE ||
                            room?.status() == MeetingSpaceStatusTag.STATUS.PRIVATE
                    } else {
                        // Check if streaming URL is online, fall back to relay check
                        val streamingUrl = channel.info?.streaming()
                        if (!streamingUrl.isNullOrBlank()) {
                            accountViewModel.httpClientBuilder.isVideoOnline(streamingUrl)
                        } else {
                            // Check relay connection
                            val relayUrl = channel.relayHintUrl()
                            if (relayUrl != null) {
                                accountViewModel.httpClientBuilder.isVideoOnline(relayUrl.url)
                            } else {
                                false
                            }
                        }
                    }
                }

                is EphemeralChatChannel -> {
                    // Check relay connection for ephemeral chat
                    val relayUrl = channel.roomId.relayUrl
                    accountViewModel.httpClientBuilder.isVideoOnline(relayUrl.url)
                }

                else -> {
                    false
                }
            }
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            Log.d("LiveStatusIndicator") { "Network error checking channel ${channel.toBestDisplayName()}: ${e.message}" }
            // Return false if any network error occurs
            false
        }
    }
