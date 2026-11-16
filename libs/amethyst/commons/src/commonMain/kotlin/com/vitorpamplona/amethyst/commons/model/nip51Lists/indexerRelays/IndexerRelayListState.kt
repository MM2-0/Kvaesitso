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
package com.vitorpamplona.amethyst.commons.model.nip51Lists.indexerRelays

import com.vitorpamplona.amethyst.commons.defaults.DefaultIndexerRelayList
import com.vitorpamplona.amethyst.commons.model.AccountSettings
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.NoteState
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.nip51Lists.indexerRelays.IndexerRelayListDecryptionCache
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip51Lists.relayLists.IndexerRelayListEvent
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class IndexerRelayListState(
    val signer: NostrSigner,
    val cache: LocalCache,
    val decryptionCache: IndexerRelayListDecryptionCache,
    val scope: CoroutineScope,
    val settings: AccountSettings,
) {
    // Creates a long-term reference for this note so that the GC doesn't collect the note it self
    val indexerListNote = cache.getOrCreateAddressableNote(getIndexerRelayListAddress())

    fun getIndexerRelayListAddress() = IndexerRelayListEvent.createAddress(signer.pubKey)

    fun getIndexerRelayListFlow(): StateFlow<NoteState> = indexerListNote.flow().metadata.stateFlow

    fun getIndexerRelayList(): IndexerRelayListEvent? = indexerListNote.event as? IndexerRelayListEvent

    fun indexListEvent(note: Note) = note.event as? IndexerRelayListEvent ?: settings.backupIndexRelayList

    suspend fun normalizeIndexerRelayListWithBackup(note: Note): Set<NormalizedRelayUrl> {
        val event = indexListEvent(note) ?: return DefaultIndexerRelayList
        // Fully decrypted here, so empty means the user listed nothing — not "not decrypted yet".
        return decryptionCache.relays(event)
    }

    suspend fun normalizeIndexerRelayListWithBackupNoDefaults(note: Note): Set<NormalizedRelayUrl> = indexListEvent(note)?.let { decryptionCache.relays(it) } ?: emptySet()

    /**
     * Same resolution as [normalizeIndexerRelayListWithBackup] but non-suspending, for use as the
     * [flow] seed. Reads the event's public tags plus any *already decrypted* private tags; it
     * never asks the signer, so it cannot block or hit a NIP-46 round trip.
     *
     * At login `indexerListNote.event` is usually still null and this resolves through
     * `settings.backupIndexRelayList`, restored from LocalPreferences — so an account with public
     * indexer relays gets its own relays immediately instead of the defaults.
     */
    fun normalizeIndexerRelayListPrecached(note: Note): Set<NormalizedRelayUrl> = indexListEvent(note)?.let { decryptionCache.cachedRelays(it) }?.ifEmpty { null } ?: DefaultIndexerRelayList

    /** See `Nip65RelayListState.assumedDefaults`. Empty as soon as any kind:10086 exists. */
    fun assumedDefaults(note: Note): Set<NormalizedRelayUrl> = if (indexListEvent(note) == null) DefaultIndexerRelayList else emptySet()

    val assumedDefaultsFlow =
        getIndexerRelayListFlow()
            .map { assumedDefaults(it.note) }
            .onStart { emit(assumedDefaults(indexerListNote)) }
            .flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                assumedDefaults(indexerListNote),
            )

    /**
     * The account's indexer relays. [normalizeIndexerRelayListWithBackup] substitutes
     * [DefaultIndexerRelayList] when there is no kind:10086 at all — but **not** when the one we
     * have decodes to zero relays, which is the user saying "no indexers" and is honored. Callers
     * assembling metadata / relay-list REQs must therefore tolerate an empty set; use
     * [flowNoDefaults] to show or diff what the user actually configured.
     *
     * Seeded via [normalizeIndexerRelayListPrecached] rather than `emptySet()`, for the same
     * reason as the search list: `flowOn(IO)` makes the first real emission asynchronous, so an
     * `emptySet()` seed left a window where `.value` contradicted the contract above.
     */
    val flow =
        getIndexerRelayListFlow()
            .map { normalizeIndexerRelayListWithBackup(it.note) }
            .onStart { emit(normalizeIndexerRelayListWithBackup(indexerListNote)) }
            .flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                normalizeIndexerRelayListPrecached(indexerListNote),
            )

    val flowNoDefaults =
        getIndexerRelayListFlow()
            .map { normalizeIndexerRelayListWithBackupNoDefaults(it.note) }
            .onStart { emit(normalizeIndexerRelayListWithBackupNoDefaults(indexerListNote)) }
            .flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptySet(),
            )

    /**
     * The relays to save to add [relay], or null when it is already there. Starts from the
     * defaults when the user has no list, so adding one keeps the indexers the user had.
     */
    suspend fun withRelay(relay: NormalizedRelayUrl): List<NormalizedRelayUrl>? {
        val current = normalizeIndexerRelayListWithBackup(indexerListNote)
        return if (relay in current) null else current.toList() + relay
    }

    suspend fun saveRelayList(indexerRelays: List<NormalizedRelayUrl>): IndexerRelayListEvent {
        val relayListForIndexer = getIndexerRelayList()

        return if (relayListForIndexer != null && relayListForIndexer.tags.isNotEmpty()) {
            IndexerRelayListEvent.updateRelayList(
                earlierVersion = relayListForIndexer,
                relays = indexerRelays,
                signer = signer,
            )
        } else {
            IndexerRelayListEvent.create(
                relays = indexerRelays,
                signer = signer,
            )
        }
    }

    init {
        settings.backupIndexRelayList?.let {
            Log.d("AccountRegisterObservers") { "Loading saved index relay list ${it.toJson()}" }
            @OptIn(DelicateCoroutinesApi::class)
            scope.launch(Dispatchers.IO) { LocalCache.justConsumeMyOwnEvent(it) }
        }

        scope.launch(Dispatchers.IO) {
            Log.d("AccountRegisterObservers", "Index Relay List Collector Start")
            getIndexerRelayListFlow().collect {
                Log.d("AccountRegisterObservers") { "Updating Index Relay List for ${signer.pubKey}" }
                (it.note.event as? IndexerRelayListEvent)?.let {
                    settings.updateIndexRelayList(it)
                }
            }
        }
    }
}
