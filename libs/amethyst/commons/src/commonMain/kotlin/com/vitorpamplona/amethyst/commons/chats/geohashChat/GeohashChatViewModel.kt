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
package com.vitorpamplona.amethyst.commons.chats.geohashChat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.service.georelay.GeohashRelays
import com.vitorpamplona.amethyst.commons.viewmodels.BaseAccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip09Deletions.DeletionRequestEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.utils.runCatchingCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The identity/reactions side of a Bitchat-interoperable geohash location chat. The message feed and
 * the composer are the shared chat components (`RefreshingChatroomFeedView` + `EditFieldRow` on a
 * geohash-aware `ChannelNewMessageViewModel`); this holds only what those can't express for an
 * anonymous per-cell identity:
 * - [myPubKeys] — the pubkeys the renderer treats as "me" (the per-cell key + the account, for
 *   "post as self") so own messages align/highlight/tick correctly.
 * - [react] — a kind-7 toggled under the per-cell key so reacting stays anonymous.
 */
class GeohashChatViewModel : ViewModel() {
    lateinit var geohash: String
        private set

    private lateinit var accountViewModel: BaseAccountViewModel

    private val _relays = MutableStateFlow<List<NormalizedRelayUrl>>(emptyList())
    val relays: StateFlow<List<NormalizedRelayUrl>> = _relays.asStateFlow()

    /** The pubkeys to right-align/highlight as "mine": the anonymous per-cell key AND the account. */
    private val _myPubKeys = MutableStateFlow<Set<String>>(emptySet())
    val myPubKeys: StateFlow<Set<String>> = _myPubKeys.asStateFlow()

    /** The anonymous per-cell pubkey alone (for de-duping our own anonymous reactions). */
    private var anonPubKeyHex: String? = null

    private var started = false

    fun init(
        geohash: String,
        accountViewModel: BaseAccountViewModel,
    ) {
        if (started) return
        started = true
        this.geohash = geohash
        this.accountViewModel = accountViewModel
        viewModelScope.launch { start() }
    }

    private suspend fun start() {
        val account = accountViewModel.account
        val anonPubKey =
            withContext(Dispatchers.IO) {
                account.geohashIdentity
                    .keyPair(geohash)
                    .pubKey
                    .toHexKey()
            }
        anonPubKeyHex = anonPubKey
        _myPubKeys.value = setOf(anonPubKey, account.signer.pubKey)
        _relays.value = resolveRelays()
    }

    /**
     * Toggle a reaction on [note] following the composer identity: the real account when [postAsSelf]
     * is on (full account react/undo), otherwise a kind-7 signed by the anonymous per-cell key so
     * reacting does not deanonymize the user. If our per-cell key already reacted with [reaction],
     * this retracts it (a NIP-09 deletion signed by the same key); otherwise it adds it.
     */
    fun react(
        note: Note,
        reaction: String,
        postAsSelf: Boolean,
    ) {
        if (postAsSelf) {
            accountViewModel.reactToOrDelete(note, reaction)
            return
        }

        val anon = anonPubKeyHex
        val mine =
            if (anon == null) {
                emptyList()
            } else {
                note.reactions[reaction]?.filter { it.author?.pubkeyHex == anon }?.mapNotNull { it.event } ?: emptyList()
            }

        viewModelScope.launch {
            val relays = _relays.value.toSet().ifEmpty { resolveRelays().toSet() }
            if (relays.isEmpty()) return@launch
            val account = accountViewModel.account
            val keyPair = withContext(Dispatchers.IO) { account.geohashIdentity.keyPair(geohash) }
            val signer = NostrSignerInternal(keyPair)
            val template =
                if (mine.isNotEmpty()) {
                    DeletionRequestEvent.build(mine)
                } else {
                    val hint = note.toEventHint<Event>() ?: return@launch
                    ReactionEvent.build(reaction, hint)
                }
            runCatchingCancellable { account.signWithAndSendPrivately(template, signer, relays) }
        }
    }

    private suspend fun resolveRelays(): List<NormalizedRelayUrl> {
        GeohashRelays.ensureLoaded()
        return GeohashRelays.closestRelays(geohash)
    }
}
