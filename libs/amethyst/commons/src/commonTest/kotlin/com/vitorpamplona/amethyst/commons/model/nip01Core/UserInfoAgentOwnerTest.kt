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
package com.vitorpamplona.amethyst.commons.model.nip01Core

import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.quartz.buzz.oaOwnerAttestation.OwnerAttestation
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.metadata.UserMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UserInfoAgentOwnerTest {
    private val owner = KeyPair()
    private val agent = KeyPair().pubKey.toHexKey()

    private fun info(
        vararg tags: Array<String>,
        pubKey: String? = agent,
    ) = UserInfo(UserMetadata(), arrayOf(*tags).toImmutableListOfLists(), emptyList(), createdAt = 1_000, pubKey = pubKey)

    @Test
    fun aProfileWithAValidAttestationNamesItsOwner() {
        val tag = OwnerAttestation.sign(agent, "", owner.privKey!!).toTag()
        assertEquals(owner.pubKey.toHexKey(), info(tag).nipOaOwner)
    }

    @Test
    fun noAttestationOrOneForAnotherKeyMeansNoOwner() {
        assertNull(info().nipOaOwner)
        val forSomeoneElse = OwnerAttestation.sign(KeyPair().pubKey.toHexKey(), "", owner.privKey!!).toTag()
        assertNull(info(forSomeoneElse).nipOaOwner)
        // Without the profile's own pubkey there is nothing to verify against.
        assertNull(info(OwnerAttestation.sign(agent, "", owner.privKey!!).toTag(), pubKey = null).nipOaOwner)
    }
}
