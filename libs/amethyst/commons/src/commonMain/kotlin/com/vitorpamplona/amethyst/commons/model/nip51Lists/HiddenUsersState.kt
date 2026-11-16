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
package com.vitorpamplona.amethyst.commons.model.nip51Lists

import com.vitorpamplona.amethyst.commons.model.AccountSettings
import com.vitorpamplona.amethyst.commons.model.LiveHiddenUsers
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.EventTag
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.HashtagTag
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.MuteTag
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.UserTag
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.WordTag
import com.vitorpamplona.quartz.utils.DualCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

class HiddenUsersState(
    val muteList: StateFlow<List<MuteTag>>,
    val blockList: StateFlow<List<MuteTag>>,
    val scope: CoroutineScope,
    val settings: AccountSettings,
) {
    var transientHiddenUsers: MutableStateFlow<Set<String>> = MutableStateFlow(setOf())

    fun assembleLiveHiddenUsers(
        blockList: List<MuteTag>,
        muteList: List<MuteTag>,
        transientHiddenUsers: Set<String>,
        showSensitiveContent: Boolean?,
        maxHashtagLimit: Int,
    ): LiveHiddenUsers {
        val hiddenUsers = blockList.mapNotNullTo(mutableSetOf()) { if (it is UserTag) it.pubKey else null } + muteList.mapNotNull { if (it is UserTag) it.pubKey else null }
        val hiddenWords = blockList.mapNotNullTo(mutableSetOf()) { if (it is WordTag) it.word else null } + muteList.mapNotNull { if (it is WordTag) it.word else null }
        val hiddenHashtags = blockList.mapNotNullTo(mutableSetOf()) { if (it is HashtagTag) it.hashtag.lowercase() else null } + muteList.mapNotNull { if (it is HashtagTag) it.hashtag.lowercase() else null }
        val mutedThreads = muteList.mapNotNullTo(mutableSetOf()) { if (it is EventTag) it.eventId else null }

        return LiveHiddenUsers(
            showSensitiveContent = showSensitiveContent,
            hiddenWordsCase = hiddenWords.map { DualCase(it.lowercase(), it.uppercase()) },
            hiddenUsersHashCodes = hiddenUsers.mapTo(HashSet()) { it.hashCode() },
            spammersHashCodes = transientHiddenUsers.mapTo(HashSet()) { it.hashCode() },
            hiddenUsers = hiddenUsers,
            spammers = transientHiddenUsers,
            hiddenWords = hiddenWords,
            hiddenHashtags = hiddenHashtags,
            maxHashtagLimit = maxHashtagLimit,
            mutedThreads = mutedThreads,
        )
    }

    val flow: StateFlow<LiveHiddenUsers> =
        combineTransform(
            blockList,
            muteList,
            transientHiddenUsers,
            settings.syncedSettings.security.showSensitiveContent,
            settings.syncedSettings.security.maxHashtagLimit,
        ) { blockList, muteList, transientHiddenUsers, showSensitiveContent, maxHashtagLimit ->
            emit(assembleLiveHiddenUsers(blockList, muteList, transientHiddenUsers, showSensitiveContent, maxHashtagLimit))
        }.onStart {
            emit(
                assembleLiveHiddenUsers(
                    blockList.value,
                    muteList.value,
                    transientHiddenUsers.value,
                    settings.syncedSettings.security.showSensitiveContent.value,
                    settings.syncedSettings.security.maxHashtagLimit.value,
                ),
            )
        }.flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                LiveHiddenUsers(
                    showSensitiveContent = null,
                    hiddenWordsCase = emptyList(),
                    hiddenUsersHashCodes = emptySet(),
                    spammersHashCodes = emptySet(),
                    hiddenUsers = emptySet(),
                    spammers = emptySet(),
                    hiddenWords = emptySet(),
                ),
            )

    fun resetTransientUsers() {
        transientHiddenUsers.update {
            emptySet()
        }
    }

    fun showUser(pubkeyHex: HexKey) {
        transientHiddenUsers.update { it - pubkeyHex }
    }

    fun hideUser(pubkeyHex: HexKey) {
        transientHiddenUsers.update { it + pubkeyHex }
    }

    fun isHidden(pubkeyHex: HexKey) = pubkeyHex in transientHiddenUsers.value
}
