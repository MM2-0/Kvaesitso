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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.lists.display.packs

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.ui.note.creators.userSuggestions.UserSuggestionState
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip51Lists.starterPack.StarterPackEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest

@Stable
class FollowPackViewModel : ViewModel() {
    lateinit var account: Account
    lateinit var userSuggestions: UserSuggestionState

    var userSuggestionFocus by mutableStateOf<UserSuggestionState?>(null)

    val selectedDTag = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedList =
        selectedDTag
            .transformLatest {
                emitAll(
                    account.starterPacks.selectListFlow(it).flowOn(Dispatchers.IO),
                )
            }.flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun selectedAddress() = StarterPackEvent.createAddress(account.userProfile().pubkeyHex, selectedDTag.value)

    fun selectedNote() = account.cache.getOrCreateAddressableNote(selectedAddress())

    fun init(
        accountVM: AccountViewModel,
        selectedDTag: String,
    ) {
        if (!this::account.isInitialized || this.account != accountVM.account) {
            this.account = accountVM.account
            this.userSuggestions = UserSuggestionState(accountVM.account, accountVM.nip05ClientBuilder())
        }

        this.selectedDTag.tryEmit(selectedDTag)
    }

    suspend fun deleteFollowSet() {
        account.starterPacks.deleteFollowSet(selectedDTag.value, account)
    }

    fun loadNote(): AddressableNote? = account.starterPacks.getPeopleListNote(selectedDTag.value)

    suspend fun removeUserFromSet(user: User) {
        account.starterPacks.removeUserFromSet(user, selectedDTag.value, account)
    }

    suspend fun addUserToSet(user: User) {
        account.starterPacks.addUserToSet(user, selectedDTag.value, account)
    }

    fun hasUserFlow(user: User): Flow<Boolean> =
        selectedList.map {
            if (it == null) {
                false
            } else {
                user in it.publicMembers
            }
        }
}
