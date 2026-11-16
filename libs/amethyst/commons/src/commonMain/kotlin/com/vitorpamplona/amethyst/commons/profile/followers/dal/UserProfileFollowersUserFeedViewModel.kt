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
package com.vitorpamplona.amethyst.commons.profile.followers.dal

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.wot.sortedByFollowsThenTrust
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import kotlin.reflect.KClass

@Stable
class UserProfileFollowersUserFeedViewModel(
    val user: User,
    val account: Account,
) : ViewModel() {
    val followerFilter =
        Filter(
            kinds = listOf(ContactListEvent.KIND),
            tags = mapOf("p" to listOf(user.pubkeyHex)),
        )

    fun List<Event>.toNonHiddenOwners(): Set<User> =
        mapNotNullTo(mutableSetOf()) { event ->
            if (!account.isHidden(event.pubKey)) {
                account.cache.getOrCreateUser(event.pubKey)
            } else {
                null
            }
        }

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    val followersFlow: StateFlow<List<User>> =
        account.cache
            .observeEvents<Event>(followerFilter)
            .sample(500)
            .map { followerContactLists ->
                followerContactLists.toNonHiddenOwners().toList().sortedByFollowsThenTrust(account, then = compareBy { it.pubkeyHex })
            }.flowOn(Dispatchers.IO)
            .stateIn(
                viewModelScope,
                initialValue = emptyList(),
                started = SharingStarted.Lazily,
            )

    val followerCount =
        followersFlow
            .map { it.size }
            .flowOn(Dispatchers.IO)
            .stateIn(
                viewModelScope,
                initialValue = 0,
                started = SharingStarted.Lazily,
            )

    class Factory(
        val user: User,
        val account: Account,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(
            modelClass: KClass<T>,
            extras: CreationExtras,
        ): T = UserProfileFollowersUserFeedViewModel(user, account) as T
    }
}
