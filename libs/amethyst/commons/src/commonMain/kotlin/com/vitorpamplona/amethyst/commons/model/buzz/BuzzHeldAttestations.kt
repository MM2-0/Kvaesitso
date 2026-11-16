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
package com.vitorpamplona.amethyst.commons.model.buzz

import com.vitorpamplona.quartz.buzz.oaOwnerAttestation.OwnerAttestation
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The NIP-OA [OwnerAttestation] this account holds — an owner-signed authorization letting its key
 * publish in the owner's Buzz workspace without being enrolled as a relay member.
 *
 * The counterpart of issuance ([OwnerAttestation] is signed by an owner and handed to an agent
 * operator out-of-band): when this account authenticates (NIP-42) to a Buzz-dialect relay, the auth
 * coordinator attaches [authTag] to the AUTH event, and the relay grants virtual membership while
 * the owner stays a member.
 *
 * **One instance per account** (`Account.buzzAttestation`), holding at most one attestation — the
 * one issued to [agentPubKey]. It was a process-wide `Map<agentPubKey, OwnerAttestation>`, but every
 * caller only ever read or wrote the entry for the account doing the AUTH, so the map was a
 * single-entry map with a lookup that could not miss. Owning the agent key here also lets [put]
 * enforce the verification its callers used to be told to perform, which is the property that
 * matters: the store never carries a credential the relay would reject.
 */
class BuzzHeldAttestations(
    private val agentPubKey: HexKey,
) {
    private val held = MutableStateFlow<OwnerAttestation?>(null)

    /** The attestation held for this account, or null. UI collects this. */
    val flow: StateFlow<OwnerAttestation?> = held

    /**
     * The `auth` tag to attach to an AUTH event signed at [authCreatedAt], or null when no verified
     * attestation is held or its `created_at<` / `created_at>` bounds exclude that moment. Buzz
     * checks those bounds against the AUTH event's timestamp and refuses membership outside them,
     * so an expired credential stays held (the UI can say it expired) but is not presented.
     */
    fun authTag(authCreatedAt: Long = TimeUtils.now()): Array<String>? = held.value?.takeIf { it.isValidAt(authCreatedAt) }?.toTag()

    /**
     * Stores [attestation] as authorizing this account, if it verifies for [agentPubKey]. Returns
     * false — storing nothing — when it does not.
     *
     * The check lives here rather than in the caller so it cannot be skipped: this is the single
     * door into the store, used by the paste flow and by the on-disk restore alike, so a tampered
     * saved credential is dropped by the same gate that rejects a mistyped one.
     */
    fun put(attestation: OwnerAttestation): Boolean {
        if (!attestation.verify(agentPubKey)) return false
        held.value = attestation
        return true
    }

    /** Drops the held attestation. */
    fun clear() {
        held.value = null
    }
}
