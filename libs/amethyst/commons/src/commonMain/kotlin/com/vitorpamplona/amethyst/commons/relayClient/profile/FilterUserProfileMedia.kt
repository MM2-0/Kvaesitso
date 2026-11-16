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
package com.vitorpamplona.amethyst.commons.relayClient.profile

import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.experimental.profileGallery.ProfileGalleryEntryEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip53LiveActivities.clip.LiveActivitiesClipEvent
import com.vitorpamplona.quartz.nip68Picture.PictureEvent
import com.vitorpamplona.quartz.nip71Video.AddressableNormalVideoEvent
import com.vitorpamplona.quartz.nip71Video.AddressableShortVideoEvent
import com.vitorpamplona.quartz.nip71Video.VideoNormalEvent
import com.vitorpamplona.quartz.nip71Video.VideoShortEvent

val UserProfileMediaKinds =
    listOf(
        PictureEvent.KIND,
        ProfileGalleryEntryEvent.KIND,
        AddressableShortVideoEvent.KIND,
        AddressableNormalVideoEvent.KIND,
        VideoNormalEvent.KIND,
        VideoShortEvent.KIND,
    )

fun filterUserProfileMedia(
    cache: ICacheProvider,
    user: User,
    since: SincePerRelayMap?,
): List<RelayBasedFilter> {
    val relays =
        user.outboxRelays()?.ifEmpty { null }
            ?: user.allUsedRelaysOrNull()
            ?: cache.relayHints.hintsForKey(user.pubkeyHex)

    return relays.flatMap { relay ->
        listOf(
            RelayBasedFilter(
                relay = relay,
                filter =
                    ExplainedFilter(
                        purpose = SubPurpose.USER_PROFILE,
                        kinds = UserProfileMediaKinds,
                        authors = listOf(user.pubkeyHex),
                        limit = 200,
                        since = since?.get(relay)?.time,
                    ),
            ),
            // Clips are authored by viewers but carry a `p` tag identifying the stream host.
            // Fetch clips OF this profile's streams so they surface in their gallery.
            RelayBasedFilter(
                relay = relay,
                filter =
                    ExplainedFilter(
                        purpose = SubPurpose.USER_PROFILE,
                        kinds = listOf(LiveActivitiesClipEvent.KIND),
                        tags = mapOf("p" to listOf(user.pubkeyHex)),
                        limit = 100,
                        since = since?.get(relay)?.time,
                    ),
            ),
        )
    }
}
