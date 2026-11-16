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
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BuzzHeldAttestationsTest {
    private val agentKey = KeyPair()
    private val otherKey = KeyPair()
    private val ownerKey = KeyPair()

    private val agent = agentKey.pubKey.toHexKey()
    private val other = otherKey.pubKey.toHexKey()

    private val attestation = OwnerAttestation.sign(agent, CONDITIONS, ownerKey.privKey!!)

    // One store per account, holding the attestation issued to that account's key.
    private val held = BuzzHeldAttestations(agent)

    @Test
    fun emptyStoreYieldsNoTag() {
        assertNull(held.flow.value)
        assertNull(held.authTag())
    }

    @Test
    fun heldAttestationSurfacesAsItsAuthTag() {
        assertTrue(held.put(attestation))
        assertEquals(attestation, held.flow.value)
        // The tag attached to the agent's AUTH is exactly the attestation's ["auth", …] tag.
        assertContentEquals(attestation.toTag(), held.authTag())
    }

    @Test
    fun clearDropsTheHeldAttestation() {
        held.put(attestation)
        held.clear()
        assertNull(held.authTag())
        assertNull(held.flow.value)
    }

    @Test
    fun anAttestationIssuedToAnotherKeyIsRejected() {
        // The check that used to be the caller's job: this credential is real and verifies — for
        // somebody else's key. Storing it would attach an `auth` tag the relay rejects, and the
        // store's whole contract is that it never holds one.
        val theirs = BuzzHeldAttestations(other)

        assertFalse(theirs.put(attestation))
        assertNull(theirs.authTag())
    }

    @Test
    fun aTamperedAttestationIsRejectedAndLeavesTheHeldOneIntact() {
        held.put(attestation)

        val forged = attestation.copy(conditions = "kind=1")

        assertFalse(held.put(forged))
        assertEquals(attestation, held.flow.value)
    }

    companion object {
        private const val CONDITIONS = "kind=40002"
    }
}
