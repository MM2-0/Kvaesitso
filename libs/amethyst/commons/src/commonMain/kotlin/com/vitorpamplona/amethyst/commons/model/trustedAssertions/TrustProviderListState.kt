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
package com.vitorpamplona.amethyst.commons.model.trustedAssertions

import com.vitorpamplona.amethyst.commons.model.AccountSettings
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.NoteState
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.nip85TrustedAssertions.TrustProviderListDecryptionCache
import com.vitorpamplona.amethyst.commons.wot.network.ResolvedProvider
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.TrustProviderListEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.serviceProviderSet
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ProviderTypes
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ServiceProviderTag
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import com.vitorpamplona.amethyst.commons.model.nip85TrustedAssertions.TrustProviderListState as ITrustProviderListState

class TrustProviderListState(
    val signer: NostrSigner,
    val cache: LocalCache,
    val decryptionCache: TrustProviderListDecryptionCache,
    val scope: CoroutineScope,
    val settings: AccountSettings,
) : ITrustProviderListState {
    // Creates a long-term reference for this note so that the GC doesn't collect the note it self
    val trustProviderListNote = cache.getOrCreateAddressableNote(getTrustProviderListAddress())

    fun getTrustProviderListAddress() = TrustProviderListEvent.createAddress(signer.pubKey)

    fun getTrustProviderListFlow(): StateFlow<NoteState> = trustProviderListNote.flow().metadata.stateFlow

    fun getTrustProviderList(): TrustProviderListEvent? = trustProviderListNote.event as? TrustProviderListEvent

    /**
     * A new kind 10040 in which the user-score entries name [providerKey] on [relay]. See
     * [withScoreProvider] (the free function), which this applies to the current list.
     */
    suspend fun withScoreProvider(
        providerKey: HexKey,
        relay: NormalizedRelayUrl,
        isPrivate: Boolean,
    ): TrustProviderListEvent = withScoreProvider(getTrustProviderList() ?: settings.backupTrustProviderList, providerKey, relay, isPrivate, signer)

    /** A new kind 10040 with [rows] in it. See [withProviderRows] (the free function). */
    suspend fun withProviderRows(
        rows: List<TrustProviderRow>,
        isPrivate: Boolean,
    ): TrustProviderListEvent = withProviderRows(getTrustProviderList() ?: settings.backupTrustProviderList, rows, isPrivate, signer)

    /** A new kind 10040 without the score provider's rows, or null when there is nothing to remove. */
    suspend fun withoutScoreProvider(): TrustProviderListEvent? = withoutScoreProvider(getTrustProviderList() ?: settings.backupTrustProviderList, signer)

    suspend fun trustProviderListWithBackup(note: Note): Set<ServiceProviderTag> {
        val event = note.event as? TrustProviderListEvent ?: settings.backupTrustProviderList
        return event?.let { decryptionCache.serviceProviderSet(it) } ?: emptySet()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val liveTrustProviderList: StateFlow<Set<ServiceProviderTag>> =
        getTrustProviderListFlow()
            .transformLatest { noteState ->
                emit(trustProviderListWithBackup(noteState.note))
            }.onStart {
                emit(trustProviderListWithBackup(trustProviderListNote))
            }.flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptySet(),
            )

    /**
     * The relays the user's providers publish on. The user named them in their own list, so they
     * count among the user's relays: with default privacy settings that means clearnet, not Tor
     * (a provider's first download moves tens of MB through it).
     */
    val liveRelays: StateFlow<Set<NormalizedRelayUrl>> =
        liveTrustProviderList
            .map { providers -> providers.mapTo(HashSet()) { it.relayUrl } }
            .stateIn(scope, SharingStarted.Eagerly, liveTrustProviderList.value.mapTo(HashSet()) { it.relayUrl })

    /**
     * The list once it is known for sure: read (or its backup), with its private part decrypted.
     * Null until then, and while the private part cannot be decrypted (a signer that is not
     * available), so "not known yet" never reads as "no providers".
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val resolvedTrustProviderList: StateFlow<Set<ServiceProviderTag>?> =
        getTrustProviderListFlow()
            // The note flow is a StateFlow, so its current note arrives first; no onStart, whose
            // retries would hold off a newer 10040 until the next pause ended.
            .transformLatest { noteState -> emitResolved(noteState.note) }
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, null)

    /**
     * Emits [note]'s providers once known. When the private part cannot be decrypted yet (an
     * external signer that refuses in the background), tries again with a growing pause, so a
     * process woken by a push still resolves once the user opens the app.
     */
    private suspend fun FlowCollector<Set<ServiceProviderTag>?>.emitResolved(note: Note) {
        var pauseMs = 15_000L
        while (true) {
            val resolved = resolvedProviders(note)
            emit(resolved)
            if (resolved != null) return
            delay(pauseMs)
            pauseMs = (pauseMs * 2).coerceAtMost(10 * 60_000L)
        }
    }

    private suspend fun resolvedProviders(note: Note): Set<ServiceProviderTag>? {
        val event = note.event as? TrustProviderListEvent ?: settings.backupTrustProviderList ?: return emptySet()
        return event.knownProviders { decryptionCache.cachedPrivateLists.privateTags(it) }
    }

    /** The rank provider once the list is known for sure (see [resolvedTrustProviderList]). */
    val resolvedRankProvider: StateFlow<ResolvedProvider?> =
        resolvedTrustProviderList
            .map { providers -> providers?.rankChoice() }
            .stateIn(scope, SharingStarted.Eagerly, null)

    @OptIn(ExperimentalCoroutinesApi::class)
    override val liveUserRankProvider: StateFlow<ServiceProviderTag?> =
        liveTrustProviderList
            .map {
                it.firstOrNull { it.service == ProviderTypes.rank }
            }.onStart {
                emit(
                    liveTrustProviderList.value.firstOrNull {
                        it.service == ProviderTypes.rank
                    },
                )
            }.flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                null,
            )

    @OptIn(ExperimentalCoroutinesApi::class)
    override val liveUserFollowerCount: StateFlow<ServiceProviderTag?> =
        liveTrustProviderList
            .map { tagList ->
                tagList.firstOrNull { it.service == ProviderTypes.followerCount }
            }.onStart {
                emit(
                    liveTrustProviderList.value.firstOrNull { it.service == ProviderTypes.followerCount },
                )
            }.flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                null,
            )

    init {
        settings.backupTrustProviderList?.let { event ->
            Log.d("AccountRegisterObservers", "Loading saved ephemeral chat list")
            @OptIn(DelicateCoroutinesApi::class)
            scope.launch(Dispatchers.IO) {
                LocalCache.justConsumeMyOwnEvent(event)
            }
        }

        scope.launch(Dispatchers.IO) {
            Log.d("AccountRegisterObservers", "TrustProviderList Collector Start")
            getTrustProviderListFlow().collect { noteState ->
                Log.d("AccountRegisterObservers") { "TrustProviderList List for ${signer.pubKey}" }
                (noteState.note.event as? TrustProviderListEvent)?.let {
                    settings.updateTrustProviderListTo(it)
                }
            }
        }
    }
}
