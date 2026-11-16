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
package com.vitorpamplona.amethyst.commons.relayClient.reqCommand.channel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.Channel
import com.vitorpamplona.amethyst.commons.model.ChannelState
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.nip28PublicChats.PublicChatChannel
import com.vitorpamplona.amethyst.commons.model.nip53LiveActivities.LiveActivitiesChannel
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart

@Composable
fun observeChannel(
    baseChannel: Channel,
    accountViewModel: AccountViewModel,
): State<ChannelState?> {
    ChannelFinderFilterAssemblerSubscription(baseChannel, accountViewModel)

    return baseChannel
        .flow()
        .metadata.stateFlow
        .collectAsStateWithLifecycle()
}

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun observeChannelNoteAuthors(
    baseChannel: Channel,
    accountViewModel: AccountViewModel,
): State<ImmutableList<User>> {
    ChannelFinderFilterAssemblerSubscription(baseChannel, accountViewModel)

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow =
        remember(baseChannel) {
            baseChannel
                .flow()
                .notes.stateFlow
                .mapLatest {
                    channelToParticipatingUsers(it.channel, accountViewModel)
                }.onStart {
                    emit(
                        channelToParticipatingUsers(baseChannel, accountViewModel),
                    )
                }.distinctUntilChanged()
                .flowOn(Dispatchers.IO)
        }

    return flow.collectAsStateWithLifecycle(persistentListOf())
}

private fun channelToParticipatingUsers(
    channel: Channel,
    accountViewModel: AccountViewModel,
    maxTimeLimit: Long = TimeUtils.fifteenMinutesAgo(),
): ImmutableList<User> {
    val users = mutableSetOf<User>()

    channel.participatingAuthors(maxTimeLimit).forEach {
        users.add(it)
    }

    if (channel is LiveActivitiesChannel) {
        val noteAuthor = channel.infoNote?.author
        if (noteAuthor != null) {
            users.add(noteAuthor)
        }

        val pKeys = channel.info?.participantKeys() ?: emptyList()

        pKeys.forEach {
            val u = LocalCache.checkGetOrCreateUser(it)
            if (u != null) {
                users.add(u)
            }
        }
    }

    return users
        .sortedWith(
            compareBy(
                { !accountViewModel.isFollowing(it) },
                { it.pubkeyHex },
            ),
        ).toImmutableList()
}

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun observeChannelPicture(
    baseChannel: PublicChatChannel,
    accountViewModel: AccountViewModel,
): State<String?> {
    // Subscribe in the relay for changes in the metadata of this user.
    ChannelFinderFilterAssemblerSubscription(baseChannel, accountViewModel)

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow =
        remember(baseChannel) {
            baseChannel
                .flow()
                .metadata.stateFlow
                .mapLatest { (it.channel as? PublicChatChannel)?.profilePicture() }
                .distinctUntilChanged()
        }

    return flow.collectAsStateWithLifecycle(baseChannel.profilePicture())
}

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun observeChannelInfo(
    baseChannel: LiveActivitiesChannel,
    accountViewModel: AccountViewModel,
): State<LiveActivitiesEvent?> {
    // Subscribe in the relay for changes in the metadata of this user.
    ChannelFinderFilterAssemblerSubscription(baseChannel, accountViewModel)

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow =
        remember(baseChannel) {
            baseChannel
                .flow()
                .metadata.stateFlow
                .mapLatest { (it.channel as? LiveActivitiesChannel)?.info }
                .distinctUntilChanged()
        }

    return flow.collectAsStateWithLifecycle(baseChannel.info)
}
