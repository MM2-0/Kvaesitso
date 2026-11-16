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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.nip53LiveActivities

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import com.vitorpamplona.amethyst.commons.model.nip53LiveActivities.LiveActivitiesChannel
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.channel.observeChannelInfo
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlVideo
import com.vitorpamplona.amethyst.commons.ui.components.CrossfadeCheckIfVideoIsOnline
import com.vitorpamplona.amethyst.commons.ui.components.SensitivityWarning
import com.vitorpamplona.amethyst.commons.ui.note.platform.ZoomableContentView
import com.vitorpamplona.amethyst.commons.ui.theme.StreamingHeaderModifier
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

@Composable
fun ShowVideoStreaming(
    baseChannel: LiveActivitiesChannel,
    accountViewModel: AccountViewModel,
) {
    baseChannel.info?.let {
        val streamingInfoEvent by observeChannelInfo(baseChannel, accountViewModel)
        streamingInfoEvent?.let { event ->
            event.streaming()?.let { url ->
                val zoomableUrlVideo =
                    remember(streamingInfoEvent) {
                        MediaUrlVideo(
                            url = url,
                            description = event.title() ?: baseChannel.toBestDisplayName(),
                            artworkUri = event.image(),
                            authorName = baseChannel.creatorName(),
                            uri = baseChannel.toNAddr(),
                            isLiveStream = true,
                        )
                    }

                SensitivityWarning(
                    event = event,
                    accountViewModel = accountViewModel,
                ) {
                    RenderStreaming(zoomableUrlVideo, accountViewModel)
                }
            }
        }
    }
}

@Composable
private fun RenderStreaming(
    media: MediaUrlVideo,
    accountViewModel: AccountViewModel,
) {
    CrossfadeCheckIfVideoIsOnline(media.url, accountViewModel) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = StreamingHeaderModifier,
        ) {
            ZoomableContentView(
                content = media,
                roundedCorner = false,
                contentScale = ContentScale.FillWidth,
                accountViewModel = accountViewModel,
            )
        }
    }
}
