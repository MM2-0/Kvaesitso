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
package com.vitorpamplona.amethyst.commons.model.bolt12Offers

import com.vitorpamplona.amethyst.commons.model.AccountSettings
import com.vitorpamplona.amethyst.commons.model.NoteState
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.offer.Bolt12OfferListEvent
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The logged-in user's NIP-B1 BOLT12 offer list (kind 10058) as live account state,
 * mirroring [com.vitorpamplona.amethyst.commons.model.nipA3PaymentTargets.NipA3PaymentTargetsState].
 * Exposes the current offers as a [flow], persists them across restarts (via
 * [AccountSettings]), and publishes updates with [saveOffers].
 */
class Bolt12OfferListState(
    val signer: NostrSigner,
    val cache: LocalCache,
    val scope: CoroutineScope,
    val settings: AccountSettings,
) {
    val bolt12OfferListNote = cache.getOrCreateAddressableNote(getBolt12OfferListAddress())

    fun getBolt12OfferListFlow(): StateFlow<NoteState> = bolt12OfferListNote.flow().metadata.stateFlow

    fun getBolt12OfferListAddress() = Bolt12OfferListEvent.createAddress(signer.pubKey)

    fun getBolt12OfferListEvent(): Bolt12OfferListEvent? = bolt12OfferListNote.event as? Bolt12OfferListEvent

    /** The user's currently-published canonical raw BOLT12 offers. */
    val flow: StateFlow<List<String>> =
        getBolt12OfferListFlow()
            .map { (it.note.event as? Bolt12OfferListEvent)?.offers() ?: emptyList() }
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    suspend fun saveOffers(offers: List<String>): Bolt12OfferListEvent {
        val existing = getBolt12OfferListEvent()
        return if (existing != null && existing.tags.isNotEmpty()) {
            Bolt12OfferListEvent.updateOffers(existing, offers, signer)
        } else {
            Bolt12OfferListEvent.create(offers, signer)
        }
    }

    init {
        settings.backupBolt12Offers?.let {
            Log.d("AccountRegisterObservers") { "Loading saved BOLT12 offer list ${it.toJson()}" }
            @OptIn(DelicateCoroutinesApi::class)
            scope.launch(Dispatchers.IO) { cache.justConsumeMyOwnEvent(it) }
        }

        scope.launch(Dispatchers.IO) {
            getBolt12OfferListFlow().collect {
                (it.note.event as? Bolt12OfferListEvent)?.let { offerListEvent ->
                    settings.updateBolt12Offers(offerListEvent)
                }
            }
        }
    }
}
