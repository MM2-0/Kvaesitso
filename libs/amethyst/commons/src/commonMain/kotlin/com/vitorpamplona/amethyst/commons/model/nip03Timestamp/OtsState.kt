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
package com.vitorpamplona.amethyst.commons.model.nip03Timestamp

import com.vitorpamplona.amethyst.commons.model.AccountSettings
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip03Timestamp.OtsEvent
import com.vitorpamplona.quartz.nip03Timestamp.OtsResolver
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.io.encoding.Base64

class OtsState(
    val signer: NostrSigner,
    val cache: LocalCache,
    val otsResolver: () -> OtsResolver,
    val scope: CoroutineScope,
    val settings: AccountSettings,
) {
    var lastTimeItTriedToUpdateAttestations: Long = 0

    fun upgradeAttestationsIfNeeded(onReady: (List<OtsEvent>) -> Unit) {
        // only tries to upgrade every hour
        val now = TimeUtils.now()
        if (now - lastTimeItTriedToUpdateAttestations > TimeUtils.ONE_HOUR) {
            lastTimeItTriedToUpdateAttestations = now
            scope.launch {
                onReady(updateAttestations())
            }
        }
    }

    suspend fun updateAttestations(): List<OtsEvent> {
        Log.d("Pending Attestations") { "Updating ${settings.pendingAttestations.value.size} pending attestations" }

        return settings.pendingAttestations.value.toList().mapNotNull { (key, value) ->
            val otsState = OtsEvent.upgrade(lenientBase64.decode(value), key, otsResolver())

            if (otsState != null) {
                val hint = cache.getNoteIfExists(key)?.toEventHint<Event>()
                val template =
                    if (hint != null) {
                        OtsEvent.build(hint, otsState)
                    } else {
                        OtsEvent.build(key, otsState)
                    }

                val otsEvent = signer.sign(template)

                settings.pendingAttestations.update { it - key }

                otsEvent
            } else {
                null
            }
        }
    }

    fun hasPendingAttestations(note: Note): Boolean {
        val id = note.event?.id ?: note.idHex
        return settings.pendingAttestations.value[id] != null
    }

    suspend fun timestamp(note: Note) {
        if (note.isDraft()) return

        val id = note.event?.id ?: note.idHex

        settings.addPendingAttestation(
            id = id,
            stamp =
                Base64.encode(
                    OtsEvent.stamp(
                        id,
                        otsResolver(),
                    ),
                ),
        )
    }
}

/**
 * Decodes with or without padding, as `java.util.Base64.getDecoder()` did before this moved to
 * commonMain; kotlin's `Base64.Default` requires it. Encoding still pads.
 */
private val lenientBase64 = Base64.Default.withPadding(Base64.PaddingOption.PRESENT_OPTIONAL)
