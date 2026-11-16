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
package com.vitorpamplona.amethyst.commons.relayClient.user

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.NoteState
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.emphChat.EphemeralChatChannel
import com.vitorpamplona.amethyst.commons.model.nip01Core.UserInfo
import com.vitorpamplona.amethyst.commons.model.nip28PublicChats.PublicChatChannel
import com.vitorpamplona.amethyst.commons.model.nip85TrustedAssertions.Nickname
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip51Lists.PinListEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.BookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.OldBookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.interestList.InterestListEvent
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.sample
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserAboutMe as sharedObserveUserAboutMe
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserBanner as sharedObserveUserBanner
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserInfo as sharedObserveUserInfo
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserPicture as sharedObserveUserPicture

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun observeUserName(
    user: User,
    accountViewModel: AccountViewModel,
): State<String> {
    // Subscribe in the relay for changes in the metadata of this user.
    UserFinderFilterAssemblerSubscription(user, accountViewModel)

    val userAssertions = accountViewModel.account.userAssertions
    val flow = remember(user) { userAssertions.displayNameFlow(user) }

    // Subscribe in the LocalCache for changes that arrive in the device
    return flow.collectAsStateWithLifecycle(remember(user) { userAssertions.cachedDisplayName(user) })
}

/**
 * The nickname (NIP-85 petname + private summary) the logged-in account gave
 * this user through its own contact card, decrypted from the card's content,
 * with the card's tags so `:shortcode:` custom emojis resolve. Null when the
 * account never nicknamed this user. Per the spec, the petname should be
 * rendered instead of the user's display name.
 */
@Composable
fun observeUserNickname(
    user: User,
    accountViewModel: AccountViewModel,
): State<Nickname?> {
    val userAssertions = accountViewModel.account.userAssertions
    val flow = remember(user) { userAssertions.nicknameFlow(user) }

    return flow.collectAsStateWithLifecycle(remember(user) { userAssertions.cachedNickname(user) })
}

/**
 * The name the account knows [user] by, for display: the NIP-85 nickname, else the NIP-02 petname
 * from the account's own follow list. Editing UIs should keep using [observeUserNickname].
 */
@Composable
fun observeUserDisplayNickname(
    user: User,
    accountViewModel: AccountViewModel,
): State<Nickname?> {
    val userAssertions = accountViewModel.account.userAssertions
    val flow = remember(user) { userAssertions.displayNicknameFlow(user) }

    return flow.collectAsStateWithLifecycle(remember(user) { userAssertions.cachedDisplayNickname(user) })
}

@Composable
fun observeUserAboutMe(
    user: User,
    accountViewModel: AccountViewModel,
): State<String> = sharedObserveUserAboutMe(user, accountViewModel.dataSources().userFinder, accountViewModel.account)

@Composable
fun observeUserInfo(
    user: User,
    accountViewModel: AccountViewModel,
): State<UserInfo?> = sharedObserveUserInfo(user, accountViewModel.dataSources().userFinder, accountViewModel.account)

@Composable
fun observeUserBanner(
    user: User,
    accountViewModel: AccountViewModel,
): State<String?> = sharedObserveUserBanner(user, accountViewModel.dataSources().userFinder, accountViewModel.account)

@Composable
fun observeUserPicture(
    user: User,
    accountViewModel: AccountViewModel,
    subscribe: Boolean = true,
): State<String?> = sharedObserveUserPicture(user, accountViewModel.dataSources().userFinder, accountViewModel.account, subscribe)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Composable
fun observeUserTagFollowCount(
    user: User,
    accountViewModel: AccountViewModel,
): State<Int> {
    // Subscribe in the relay for changes in the metadata of this user.
    UserFinderFilterAssemblerSubscription(user, accountViewModel)

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow =
        remember(user) {
            accountViewModel
                .hashtagFollows(user)
                .flow()
                .metadata.stateFlow
                .sample(1000)
                .mapLatest { noteState ->
                    (noteState.note.event as? InterestListEvent)?.let { accountViewModel.account.interestListDecryptionCache.hashtags(it) }?.size ?: 0
                }.onStart {
                    emit((accountViewModel.hashtagFollows(user).event as? InterestListEvent)?.let { accountViewModel.account.interestListDecryptionCache.hashtags(it) }?.size ?: 0)
                }.distinctUntilChanged()
                .flowOn(Dispatchers.IO)
        }

    return flow.collectAsStateWithLifecycle(0)
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Composable
fun observeUserTagFollows(
    user: User,
    accountViewModel: AccountViewModel,
): State<List<String>> {
    // Subscribe in the relay for changes in the metadata of this user.
    UserFinderFilterAssemblerSubscription(user, accountViewModel)

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow =
        remember(user) {
            accountViewModel
                .hashtagFollows(user)
                .flow()
                .metadata.stateFlow
                .sample(200)
                .mapLatest { noteState ->
                    (noteState.note.event as? InterestListEvent)?.let { accountViewModel.account.interestListDecryptionCache.hashtags(it) }?.sorted() ?: emptyList()
                }.onStart {
                    emit((accountViewModel.hashtagFollows(user).event as? InterestListEvent)?.let { accountViewModel.account.interestListDecryptionCache.hashtags(it) }?.sorted() ?: emptyList())
                }.distinctUntilChanged()
                .flowOn(Dispatchers.IO)
        }

    return flow.collectAsStateWithLifecycle(emptyList())
}

@Composable
fun observeUserBookmarks(
    user: User,
    accountViewModel: AccountViewModel,
): State<NoteState> {
    // Subscribe in the relay for changes in the metadata of this user.
    UserFinderFilterAssemblerSubscription(user, accountViewModel)

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow =
        remember(user) {
            accountViewModel
                .bookmarks(user)
                .flow()
                .metadata.stateFlow
        }

    // Subscribe in the LocalCache for changes that arrive in the device
    return flow.collectAsStateWithLifecycle()
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Composable
fun observeUserBookmarkCount(
    user: User,
    accountViewModel: AccountViewModel,
): State<Int> {
    // Subscribe in the relay for changes in the metadata of this user.
    UserFinderFilterAssemblerSubscription(user, accountViewModel)

    // Subscribe in the LocalCache for changes that arrive in the device
    val newFlow =
        remember(user) {
            accountViewModel
                .bookmarks(user)
                .flow()
                .metadata.stateFlow
                .sample(200)
                .mapLatest { noteState ->
                    (noteState.note.event as? BookmarkListEvent)?.countBookmarks() ?: 0
                }.distinctUntilChanged()
                .flowOn(Dispatchers.IO)
        }

    val oldFlow =
        remember(user) {
            accountViewModel
                .oldBookmarks(user)
                .flow()
                .metadata.stateFlow
                .sample(200)
                .mapLatest { noteState ->
                    (noteState.note.event as? OldBookmarkListEvent)?.countBookmarks() ?: 0
                }.distinctUntilChanged()
                .flowOn(Dispatchers.IO)
        }

    val combined =
        remember(user) {
            combine(newFlow, oldFlow) { newCount, oldCount ->
                newCount + oldCount
            }
        }

    return combined.collectAsStateWithLifecycle(0)
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Composable
fun observeUserPinnedNotesCount(
    user: User,
    accountViewModel: AccountViewModel,
): State<Int> {
    UserFinderFilterAssemblerSubscription(user, accountViewModel)

    val flow =
        remember(user) {
            accountViewModel
                .pinnedNotes(user)
                .flow()
                .metadata.stateFlow
                .sample(200)
                .mapLatest { noteState ->
                    (noteState.note.event as? PinListEvent)?.countPins() ?: 0
                }.distinctUntilChanged()
                .flowOn(Dispatchers.IO)
        }

    return flow.collectAsStateWithLifecycle(0)
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Composable
fun observeUserIsFollowing(
    user1: User,
    user2: User,
    accountViewModel: AccountViewModel,
): State<Boolean> {
    // Subscribe in the relay for changes in the metadata of this user.
    UserFinderFilterAssemblerSubscription(user1, accountViewModel)

    val note =
        remember(user1) {
            accountViewModel.follows(user1)
        }

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow =
        remember(note, user2) {
            note
                .flow()
                .metadata.stateFlow
                .mapLatest { userState ->
                    val event = userState.note.event as? ContactListEvent
                    event?.isFollowing(user2.pubkeyHex) ?: false
                }.distinctUntilChanged()
                .flowOn(Dispatchers.IO)
        }

    return flow.collectAsStateWithLifecycle(
        (note.event as? ContactListEvent)?.isFollowing(user2.pubkeyHex) ?: false,
    )
}

@Suppress("StateFlowValueCalledInComposition")
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Composable
fun observeUserIsFollowingHashtag(
    hashtag: String,
    accountViewModel: AccountViewModel,
): State<Boolean> {
    // Subscribe in the LocalCache for changes that arrive in the device
    val flow =
        remember(accountViewModel, hashtag) {
            accountViewModel.account.interestList.flow
                .mapLatest { hashtags ->
                    hashtag in hashtags
                }.onStart {
                    emit(hashtag in accountViewModel.account.interestList.flow.value)
                }.distinctUntilChanged()
                .flowOn(Dispatchers.IO)
        }

    return flow.collectAsStateWithLifecycle(hashtag in accountViewModel.account.interestList.flow.value)
}

@Suppress("StateFlowValueCalledInComposition")
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Composable
fun observeUserIsMutingHashtag(
    hashtag: String,
    accountViewModel: AccountViewModel,
): State<Boolean> {
    // Subscribe in the LocalCache for changes that arrive in the device
    val flow =
        remember(accountViewModel, hashtag) {
            accountViewModel.account.hiddenUsers.flow
                .mapLatest { it.isHashtagHidden(hashtag) }
                .onStart {
                    emit(
                        accountViewModel.account.hiddenUsers.flow.value
                            .isHashtagHidden(hashtag),
                    )
                }.distinctUntilChanged()
                .flowOn(Dispatchers.IO)
        }

    return flow.collectAsStateWithLifecycle(
        accountViewModel.account.hiddenUsers.flow.value
            .isHashtagHidden(hashtag),
    )
}

@Suppress("StateFlowValueCalledInComposition")
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Composable
fun observeUserIsFollowingGeohash(
    geohash: String,
    accountViewModel: AccountViewModel,
): State<Boolean> {
    val flow =
        remember(accountViewModel, geohash) {
            accountViewModel.account.geohashList.flow
                .mapLatest { geohashes ->
                    geohash in geohashes
                }.onStart {
                    emit(geohash in accountViewModel.account.geohashList.flow.value)
                }.distinctUntilChanged()
                .flowOn(Dispatchers.IO)
        }

    return flow.collectAsStateWithLifecycle(geohash in accountViewModel.account.geohashList.flow.value)
}

@Suppress("StateFlowValueCalledInComposition")
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Composable
fun observeUserIsFollowingRelay(
    relayUrl: NormalizedRelayUrl,
    accountViewModel: AccountViewModel,
): State<Boolean> {
    val flow =
        remember(accountViewModel, relayUrl) {
            accountViewModel.account.favoriteRelayList.flowNoDefaults
                .mapLatest { relays ->
                    relayUrl in relays
                }.onStart {
                    emit(relayUrl in accountViewModel.account.favoriteRelayList.flowNoDefaults.value)
                }.distinctUntilChanged()
                .flowOn(Dispatchers.IO)
        }

    return flow.collectAsStateWithLifecycle(relayUrl in accountViewModel.account.favoriteRelayList.flowNoDefaults.value)
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Composable
fun observeUserIsFollowingChannel(
    account: Account,
    channel: PublicChatChannel,
    accountViewModel: AccountViewModel,
): State<Boolean> {
    // Subscribe in the relay for changes in the metadata of this user.
    UserFinderFilterAssemblerSubscription(account.userProfile(), accountViewModel)

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow =
        remember(account, channel) {
            account
                .publicChatList
                .flowSet
                .mapLatest { followingChannels ->
                    channel.idHex in followingChannels
                }.distinctUntilChanged()
                .flowOn(Dispatchers.IO)
        }

    @Suppress("StateFlowValueCalledInComposition")
    return flow.collectAsStateWithLifecycle(channel.idHex in account.publicChatList.flowSet.value)
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Composable
fun observeUserIsFollowingChannel(
    account: Account,
    channel: EphemeralChatChannel,
    accountViewModel: AccountViewModel,
): State<Boolean> {
    // Subscribe in the relay for changes in the metadata of this user.
    UserFinderFilterAssemblerSubscription(account.userProfile(), accountViewModel)

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow =
        remember(account, channel) {
            account
                .ephemeralChatList
                .liveEphemeralChatList
                .mapLatest { followingChannels ->
                    channel.roomId in followingChannels
                }.distinctUntilChanged()
                .flowOn(Dispatchers.IO)
        }

    @Suppress("StateFlowValueCalledInComposition")
    return flow.collectAsStateWithLifecycle(channel.roomId in account.ephemeralChatList.liveEphemeralChatList.value)
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Composable
fun observeUserAssertionsScore(
    user: User,
    accountViewModel: AccountViewModel,
    subscribe: Boolean = true,
): State<Int?> {
    // Subscribe in the relay for changes in the metadata of this user.
    // See observeUserPicture: pass subscribe = false when a shared subscription
    // for the same user already exists.
    if (subscribe) {
        UserFinderFilterAssemblerSubscription(user, accountViewModel)
    }

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow =
        remember(user) {
            val account = accountViewModel.account
            user.cards().rankFlow(account.trustProviderList, account.trustNetwork, user.pubkeyHex)
        }

    return flow.collectAsStateWithLifecycle(null)
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Composable
fun observeUserAssertionsFollowerCount(
    user: User,
    accountViewModel: AccountViewModel,
): State<String> {
    // Subscribe in the relay for changes in the metadata of this user.
    UserFinderFilterAssemblerSubscription(user, accountViewModel)

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow =
        remember(user) {
            val account = accountViewModel.account
            user.cards().followerCountStrFlow(account.trustProviderList, account.trustNetwork, user.pubkeyHex)
        }

    return flow.collectAsStateWithLifecycle("--")
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Composable
fun observeUserStatuses(
    user: User,
    accountViewModel: AccountViewModel,
): State<ImmutableList<AddressableNote>> {
    // Subscribe in the relay for changes in the metadata of this user.
    UserFinderFilterAssemblerSubscription(user, accountViewModel)

    val flow =
        remember(user) {
            user
                .statusState()
                .statuses
                .onStart {
                    user.statusState().removeExpired()
                }.flowOn(Dispatchers.IO)
        }

    @Suppress("StateFlowValueCalledInComposition")
    return flow.collectAsStateWithLifecycle(user.statusState().statuses.value)
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Composable
fun observeUserRelayIntoList(
    relayUrl: NormalizedRelayUrl,
    accountViewModel: AccountViewModel,
): State<Boolean> {
    // Subscribe in the LocalCache for changes that arrive in the device
    val flow =
        remember(accountViewModel, relayUrl) {
            accountViewModel.account.trustedRelays.flow
                .mapLatest { relays ->
                    relayUrl in relays
                }.distinctUntilChanged()
                .flowOn(Dispatchers.IO)
        }

    @Suppress("StateFlowValueCalledInComposition")
    return flow.collectAsStateWithLifecycle(relayUrl in accountViewModel.account.trustedRelays.flow.value)
}
