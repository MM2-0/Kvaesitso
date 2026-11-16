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
package com.vitorpamplona.amethyst.commons.model.nip51Lists.searchRelays

import com.vitorpamplona.amethyst.commons.defaults.DefaultSearchRelayList
import com.vitorpamplona.amethyst.commons.model.AccountSettings
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.NoteState
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.nip51Lists.searchRelays.SearchRelayListDecryptionCache
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip50Search.SearchRelayListEvent
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

class SearchRelayListState(
    val signer: NostrSigner,
    val cache: LocalCache,
    val decryptionCache: SearchRelayListDecryptionCache,
    val scope: CoroutineScope,
    val settings: AccountSettings,
) {
    // Creates a long-term reference for this note so that the GC doesn't collect the note it self
    val searchListNote = cache.getOrCreateAddressableNote(getSearchRelayListAddress())

    fun getSearchRelayListAddress() = SearchRelayListEvent.createAddress(signer.pubKey)

    fun getSearchRelayListFlow(): StateFlow<NoteState> = searchListNote.flow().metadata.stateFlow

    fun getSearchRelayList(): SearchRelayListEvent? = searchListNote.event as? SearchRelayListEvent

    fun searchListEvent(note: Note) = note.event as? SearchRelayListEvent ?: settings.backupSearchRelayList

    suspend fun normalizeSearchRelayListWithBackup(note: Note): Set<NormalizedRelayUrl> {
        val event = searchListEvent(note) ?: return DefaultSearchRelayList
        // Fully decrypted here, so empty means the user listed nothing — not "not decrypted yet".
        return decryptionCache.relays(event)
    }

    suspend fun normalizeSearchRelayListWithBackupNoDefaults(note: Note): Set<NormalizedRelayUrl> = searchListEvent(note)?.let { decryptionCache.relays(it) } ?: emptySet()

    /**
     * Same resolution as [normalizeSearchRelayListWithBackup] but non-suspending, for use as the
     * [flow] seed. Reads the event's public tags plus any *already decrypted* private tags; it
     * never asks the signer, so it cannot block or hit a NIP-46 round trip.
     *
     * At login `searchListNote.event` is usually still null and this resolves through
     * `settings.backupSearchRelayList`, restored from LocalPreferences — so an account with public
     * search relays gets its own relays immediately instead of the defaults. Accounts whose relays
     * are exclusively private fall back to [DefaultSearchRelayList] until the first decrypt lands.
     */
    fun normalizeSearchRelayListPrecached(note: Note): Set<NormalizedRelayUrl> = searchListEvent(note)?.let { decryptionCache.cachedRelays(it) }?.ifEmpty { null } ?: DefaultSearchRelayList

    /** See `Nip65RelayListState.assumedDefaults`. Empty as soon as any kind:10007 exists. */
    fun assumedDefaults(note: Note): Set<NormalizedRelayUrl> = if (searchListEvent(note) == null) DefaultSearchRelayList else emptySet()

    val assumedDefaultsFlow =
        getSearchRelayListFlow()
            .map { assumedDefaults(it.note) }
            .onStart { emit(assumedDefaults(searchListNote)) }
            .flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                assumedDefaults(searchListNote),
            )

    /**
     * The account's search relays. [normalizeSearchRelayListWithBackup] substitutes
     * [DefaultSearchRelayList] when there is no kind:10007 at all — but **not** when the one we
     * have decodes to zero relays, which is the user saying "no search relays" and is honored.
     * Callers assembling NIP-50 REQs must tolerate an empty set, and can
     * rely on getting a usable set; use [flowNoDefaults] instead to show or diff what the
     * user actually configured.
     *
     * Seeded via [normalizeSearchRelayListPrecached] rather than `emptySet()`: `flowOn(IO)` means
     * the first real emission can never be synchronous with `stateIn`, so an `emptySet()` seed
     * left a window where `.value` reported nothing before the event had been read at all, so search
     * silently queried nothing. That window is unbounded for a NIP-46 signer whose list has
     * private entries, since the first emission waits on a remote decrypt.
     */
    val flow =
        getSearchRelayListFlow()
            .map { normalizeSearchRelayListWithBackup(it.note) }
            .onStart { emit(normalizeSearchRelayListWithBackup(searchListNote)) }
            .flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                normalizeSearchRelayListPrecached(searchListNote),
            )

    val flowNoDefaults =
        getSearchRelayListFlow()
            .map { normalizeSearchRelayListWithBackupNoDefaults(it.note) }
            .onStart { emit(normalizeSearchRelayListWithBackupNoDefaults(searchListNote)) }
            .flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptySet(),
            )

    /**
     * The relays to save to add [relay], or null when it is already there. Starts from the
     * defaults when the user has no list, so adding one keeps the search the user had.
     */
    suspend fun withRelay(relay: NormalizedRelayUrl): List<NormalizedRelayUrl>? {
        val current = normalizeSearchRelayListWithBackup(searchListNote)
        return if (relay in current) null else current.toList() + relay
    }

    suspend fun saveRelayList(searchRelays: List<NormalizedRelayUrl>): SearchRelayListEvent {
        val relayListForSearch = getSearchRelayList()

        return if (relayListForSearch != null && relayListForSearch.tags.isNotEmpty()) {
            SearchRelayListEvent.updateRelayList(
                earlierVersion = relayListForSearch,
                relays = searchRelays,
                signer = signer,
            )
        } else {
            SearchRelayListEvent.create(
                relays = searchRelays,
                signer = signer,
            )
        }
    }

    init {
        settings.backupSearchRelayList?.let {
            Log.d("AccountRegisterObservers") { "Loading saved search relay list ${it.toJson()}" }
            @OptIn(DelicateCoroutinesApi::class)
            scope.launch(Dispatchers.IO) { LocalCache.justConsumeMyOwnEvent(it) }
        }

        scope.launch(Dispatchers.IO) {
            Log.d("AccountRegisterObservers", "Search Relay List Collector Start")
            getSearchRelayListFlow().collect {
                Log.d("AccountRegisterObservers") { "Updating Search Relay List for ${signer.pubKey}" }
                (it.note.event as? SearchRelayListEvent)?.let {
                    settings.updateSearchRelayList(it)
                }
            }
        }
    }
}
