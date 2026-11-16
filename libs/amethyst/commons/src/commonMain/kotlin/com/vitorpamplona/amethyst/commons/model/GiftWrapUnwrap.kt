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
package com.vitorpamplona.amethyst.commons.model

import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.utils.Log

/**
 * Opens [event] if it is a gift wrap or seal, indexes what is inside, and returns the note of the
 * first event that is neither; for any other event, its note if the cache holds one.
 *
 * For opening one on demand (a DM that replies to a wrapped message), not for the feed, which
 * the indexer opens as it arrives. A wrap or seal that opens to something that is not an event
 * (empty content, a sender's broken JSON) has nothing to show, so it returns null like the
 * indexer does, instead of throwing.
 */
suspend fun unwrapToNote(
    event: Event,
    signer: NostrSigner,
): Note? =
    try {
        unwrapIfNeeded(event, signer)
    } catch (e: IllegalArgumentException) {
        Log.w("GiftWrapUnwrap", "Could not parse the content of ${event.kind}:${event.id}", e)
        null
    }

private suspend fun unwrapGiftWrap(
    event: GiftWrapEvent,
    signer: NostrSigner,
): Note? {
    val cacheInnerEventId = event.innerEventId
    return if (cacheInnerEventId != null) {
        val existingNoteEvent = LocalCache.getNoteIfExists(cacheInnerEventId)?.event
        if (existingNoteEvent != null) {
            unwrapIfNeeded(existingNoteEvent, signer)
        } else {
            val newEvent = event.unwrapOrNull(signer) ?: return null

            // clear the encrypted payload to save memory
            LocalCache.getOrCreateNote(event.id).event = event.copyNoContent()

            LocalCache.justConsume(newEvent, null, false)

            unwrapIfNeeded(newEvent, signer)
        }
    } else {
        val newEvent = event.unwrapThrowing(signer)
        // clear the encrypted payload to save memory
        LocalCache.getOrCreateNote(event.id).event = event.copyNoContent()

        val existingNoteEvent = LocalCache.getNoteIfExists(newEvent.id)?.event

        if (existingNoteEvent != null) {
            unwrapIfNeeded(existingNoteEvent, signer)
        } else {
            LocalCache.justConsume(newEvent, null, false)
            unwrapIfNeeded(newEvent, signer)
        }
    }
}

private suspend fun unwrapSeal(
    event: SealEvent,
    signer: NostrSigner,
): Note? {
    val cacheInnerEventId = event.innerEventId
    return if (cacheInnerEventId != null) {
        val existingNoteEvent = LocalCache.getNoteIfExists(cacheInnerEventId)?.event
        if (existingNoteEvent != null) {
            unwrapIfNeeded(existingNoteEvent, signer)
        } else {
            val newEvent = event.unsealThrowing(signer)
            // clear the encrypted payload to save memory
            LocalCache.getOrCreateNote(event.id).event = event.copyNoContent()

            // this is not verifiable
            LocalCache.justConsume(newEvent, null, true)
            unwrapIfNeeded(newEvent, signer)
        }
    } else {
        val newEvent = event.unsealThrowing(signer)
        // clear the encrypted payload to save memory
        LocalCache.getOrCreateNote(event.id).event = event.copyNoContent()

        val existingNoteEvent = LocalCache.getNoteIfExists(newEvent.id)?.event
        if (existingNoteEvent != null) {
            unwrapIfNeeded(existingNoteEvent, signer)
        } else {
            // this is not verifiable
            LocalCache.justConsume(newEvent, null, true)
            unwrapIfNeeded(newEvent, signer)
        }
    }
}

private suspend fun unwrapIfNeeded(
    event: Event,
    signer: NostrSigner,
): Note? =
    when (event) {
        is GiftWrapEvent -> unwrapGiftWrap(event, signer)
        is SealEvent -> unwrapSeal(event, signer)
        else -> LocalCache.getNoteIfExists(event.id)
    }
