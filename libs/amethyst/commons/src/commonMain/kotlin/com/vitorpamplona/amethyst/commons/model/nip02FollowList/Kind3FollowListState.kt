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
package com.vitorpamplona.amethyst.commons.model.nip02FollowList

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.actions.FollowActions
import com.vitorpamplona.amethyst.commons.model.NoteState
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class Kind3FollowListState(
    val signer: NostrSigner,
    val cache: ICacheProvider,
    val scope: CoroutineScope,
    val settings: Kind3FollowListRepository,
) {
    // Creates a long-term reference for this note so that the GC doesn't collect the note itself
    val user = cache.getOrCreateUser(signer.pubKey)

    // Creates a long-term reference for this note so that the GC doesn't collect the note itself
    val note = cache.getOrCreateAddressableNote(getFollowListAddress())

    fun getFollowListAddress() = ContactListEvent.createAddress(signer.pubKey)

    fun getFollowListFlow(): StateFlow<NoteState> = note.flow().metadata.stateFlow

    fun getFollowListEvent(): ContactListEvent? = note.event as? ContactListEvent

    @OptIn(ExperimentalCoroutinesApi::class)
    private val innerFlow: Flow<Kind3Follows> =
        getFollowListFlow().transformLatest {
            emit(buildKind3Follows(it.note.event as? ContactListEvent ?: settings.backupContactList))
        }

    val flow =
        innerFlow
            .flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                // this has priority.
                buildKind3Follows(getFollowListEvent() ?: settings.backupContactList),
            )

    // Creates a long-term reference for all follows of a user
    val userList =
        flow
            .map { kind3Follows ->
                kind3Follows.authors.mapNotNull {
                    runCatching { cache.getOrCreateUser(it) }.getOrNull()
                }
            }.flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                // this has priority.
                flow.value.authors.mapNotNull {
                    runCatching { cache.getOrCreateUser(it) }.getOrNull()
                },
            )

    /**
     This contains a big OR of everything the user wants to see in the a single feed.
     */
    @Immutable
    class Kind3Follows(
        val authors: Set<HexKey> = emptySet(),
        val authorsPlusMe: Set<HexKey>,
    )

    fun buildKind3Follows(latestContactList: ContactListEvent?): Kind3Follows {
        // makes sure the output include only valid p tags
        val verifiedFollowingUsers = latestContactList?.verifiedFollowKeySet() ?: emptySet()

        return Kind3Follows(
            authors = verifiedFollowingUsers,
            authorsPlusMe = verifiedFollowingUsers + signer.pubKey,
        )
    }

    // Serializes all kind:3 mutations so concurrent follow/unfollow/follow-all
    // calls cannot lose writes. The read of getFollowListEvent() inside each
    // call happens under the lock, so the dedupe baseline is always fresh.
    private val followMutex = Mutex()

    suspend fun follow(users: List<User>): ContactListEvent =
        followMutex.withLock {
            FollowActions.buildFollowBatch(
                signer = signer,
                pubkeysWithHints = users.map { it.pubkeyHex to it.bestRelayHint() },
                currentContactList = getFollowListEvent(),
            )
        }

    suspend fun follow(user: User): ContactListEvent =
        followMutex.withLock {
            FollowActions.buildFollow(
                signer = signer,
                pubkeyToFollow = user.pubkeyHex,
                currentContactList = getFollowListEvent(),
                relayHint = user.bestRelayHint(),
            )
        }

    suspend fun unfollow(user: User): ContactListEvent? =
        followMutex.withLock {
            FollowActions.buildUnfollow(
                signer = signer,
                pubkeyToUnfollow = user.pubkeyHex,
                currentContactList = getFollowListEvent(),
            )
        }

    suspend fun unfollow(users: List<User>): ContactListEvent? =
        followMutex.withLock {
            FollowActions.buildUnfollowBatch(
                signer = signer,
                pubkeysToRemove = users.mapTo(mutableSetOf()) { it.pubkeyHex },
                currentContactList = getFollowListEvent(),
            )
        }

    init {
        settings.backupContactList?.let {
            Log.d("AccountRegisterObservers") { "Loading saved ${it.tags.size} contacts" }

            @OptIn(DelicateCoroutinesApi::class)
            scope.launch(Dispatchers.IO) { cache.justConsumeMyOwnEvent(it) }
        }

        // saves contact list for the next time.
        scope.launch(Dispatchers.IO) {
            Log.d("AccountRegisterObservers", "Kind 3 Collector Start")
            getFollowListFlow().collect {
                Log.d("AccountRegisterObservers") { "Updating Kind 3 ${signer.pubKey}" }
                (it.note.event as? ContactListEvent)?.let {
                    settings.updateContactListTo(it)
                }
            }
        }
    }
}
