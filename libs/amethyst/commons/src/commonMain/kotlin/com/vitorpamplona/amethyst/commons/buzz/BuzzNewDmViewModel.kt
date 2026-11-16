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
package com.vitorpamplona.amethyst.commons.buzz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.quartz.buzz.dm.DmOpenEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.isValid
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException

/**
 * Backing ViewModel for [BuzzNewDmScreen]. It gathers 1-8 other participants and a Buzz
 * workspace relay, publishes a kind-41010 open command, and reads the relay's synchronous OK
 * (`response:{"channel_id":"…"}`) for the assigned [GroupId] — we never mint the DM's UUID
 * ourselves, and the deployed relay does not emit a queryable kind-41001 to poll for.
 */
class BuzzNewDmViewModel : ViewModel() {
    @Volatile private var account: Account? = null

    private val _relay = MutableStateFlow<NormalizedRelayUrl?>(null)
    val relay: StateFlow<NormalizedRelayUrl?> = _relay.asStateFlow()

    private val _relays = MutableStateFlow<List<NormalizedRelayUrl>>(emptyList())
    val relays: StateFlow<List<NormalizedRelayUrl>> = _relays.asStateFlow()

    private val _participants = MutableStateFlow<List<HexKey>>(emptyList())
    val participants: StateFlow<List<HexKey>> = _participants.asStateFlow()

    /** The current typeahead query and its resolved candidate pubkeys (members of this workspace first). */
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _suggestions = MutableStateFlow<List<HexKey>>(emptyList())
    val suggestions: StateFlow<List<HexKey>> = _suggestions.asStateFlow()

    private var searchJob: Job? = null

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()

    sealed interface Status {
        data object Idle : Status

        data object Sending : Status

        data class Error(
            val message: String,
        ) : Status
    }

    /** Binds scoped to a single community [relayUrl] — a new DM is created on that community. */
    fun bind(
        account: Account,
        relayUrl: String,
    ) {
        if (this.account != null) return
        this.account = account
        val relay = RelayUrlNormalizer.normalizeOrNull(relayUrl)
        _relays.value = listOfNotNull(relay)
        _relay.value = relay
    }

    fun selectRelay(relay: NormalizedRelayUrl) {
        _relay.value = relay
    }

    /**
     * Updates the typeahead [text] and refreshes [suggestions] off the main thread. Members of
     * this workspace's channels are surfaced first (they're the people you'd DM here), then the
     * rest of the general user search. Already-added recipients and yourself are filtered out.
     */
    fun updateQuery(text: String) {
        _query.value = text
        val account = account ?: return
        searchJob?.cancel()
        if (text.isBlank()) {
            _suggestions.value = emptyList()
            return
        }
        searchJob =
            viewModelScope.launch(Dispatchers.IO) {
                delay(150) // debounce keystrokes before touching the cache
                val members = workspaceMemberKeys()
                val me = account.userProfile().pubkeyHex
                val already = _participants.value.toSet()
                val ranked =
                    LocalCache.search
                        .findUsersStartingWith(text.trim(), account)
                        .asSequence()
                        .map { it.pubkeyHex }
                        .filter { it != me && it !in already }
                        // Stable sort keeps findUsersStartingWith's own relevance order within each bucket.
                        .sortedByDescending { it in members }
                        .take(12)
                        .toList()
                _suggestions.value = ranked
            }
    }

    /** The union of member + admin pubkeys across every channel this workspace relay hosts locally. */
    private fun workspaceMemberKeys(): Set<HexKey> {
        val relay = _relay.value ?: return emptySet()
        val keys = HashSet<HexKey>()
        LocalCache.getRelayGroupChannelsOnRelay(relay).forEach { channel ->
            keys.addAll(channel.members)
            channel.admins.forEach { keys.add(it.pubKey) }
        }
        return keys
    }

    /**
     * Adds an already-resolved [hex] pubkey (a tapped search result). Returns an error string to
     * surface, or null on success. Rejects me, duplicates, invalid keys and the 8-participant ceiling.
     * Also clears the query so the suggestion list collapses after a pick.
     */
    fun addParticipant(hex: HexKey): String? {
        val account = account ?: return "Not ready"
        if (!hex.isValid()) return "Not a valid key"
        if (hex == account.userProfile().pubkeyHex) return "That's you"
        val current = _participants.value
        if (hex in current) return "Already added"
        if (current.size >= DmOpenEvent.MAX_PARTICIPANTS) return "At most ${DmOpenEvent.MAX_PARTICIPANTS} others"
        _participants.value = current + hex
        _query.value = ""
        _suggestions.value = emptyList()
        return null
    }

    /**
     * Resolves a pasted npub/hex [input] and adds it — the escape hatch for someone not yet in the
     * local cache (so they never surface in [updateQuery]'s search). Returns an error or null.
     */
    fun addRawKey(input: String): String? {
        val hex = decodePublicKeyAsHexOrNull(input.trim())?.takeIf { it.isValid() } ?: return "Not a valid npub or hex key"
        return addParticipant(hex)
    }

    fun removeParticipant(hex: HexKey) {
        _participants.update { it - hex }
    }

    /**
     * Publishes the 41010 and reads the relay's synchronous OK confirmation for the assigned
     * channel id, then invokes [onOpened] with the [GroupId]. On a null id (the relay didn't
     * confirm in the ack) it calls [onOpened] with null so the screen falls back to the inbox.
     */
    fun start(onOpened: (GroupId?) -> Unit) {
        val account = account ?: return
        val relay =
            _relay.value ?: run {
                _status.value = Status.Error("Pick a workspace relay")
                return
            }
        val others = _participants.value
        if (others.isEmpty()) {
            _status.value = Status.Error("Add at least one person")
            return
        }

        _status.value = Status.Sending
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val channelId = account.relayGroups.openBuzzDm(relay, others)
                val groupId = channelId?.let { GroupId(it, relay) }
                withContext(Dispatchers.Main) { onOpened(groupId) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // openBuzzDm can throw signer / IO / timeout errors (not just IllegalArgumentException);
                // catch them all so a failure surfaces as an error instead of crashing and leaving the
                // Start button stuck disabled on `Sending`.
                _status.value = Status.Error(e.message ?: "Could not open the DM")
            }
        }
    }
}
