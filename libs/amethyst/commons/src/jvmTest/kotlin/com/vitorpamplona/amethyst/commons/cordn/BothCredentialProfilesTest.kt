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
package com.vitorpamplona.amethyst.commons.cordn

import com.vitorpamplona.cordn.groups.CordnCredential
import com.vitorpamplona.cordn.groups.CordnGroupPolicy
import com.vitorpamplona.cordn.spec01GroupMetadata.CordnGroupMetadata
import com.vitorpamplona.marmot.groups.MarmotGroupPolicy
import com.vitorpamplona.mls.group.MlsGroup
import com.vitorpamplona.mls.tree.Credential
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * One engine, both credential encodings, side by side.
 *
 * §4.1 of `cordn/plans/2026-09-17-cordn-interop.md` in vitorpamplona/cordn-kmp records a hard
 * incompatibility: Marmot writes a Nostr pubkey into a BasicCredential as the
 * **raw 32 bytes**, cordn as **64 ASCII bytes of lowercase hex**. The decision
 * was to implement both rather than wait for either ecosystem to move, which
 * only works if the two profiles cannot be confused for one another — a leaf
 * read under the wrong binding must come back as "not mine", never as a
 * plausible-looking wrong pubkey.
 *
 * That is what this pins. It is cheap to get wrong in a way no single-binding
 * test would notice: `MlsGroup.memberIdentityHex` hex-encodes credential bytes,
 * so run over a cordn leaf it returns 128 characters of hex-of-hex — a string
 * that looks like a pubkey, compares unequal to every real one, and would
 * quietly drop a member from a UI list.
 */
class BothCredentialProfilesTest {
    private val aliceHex = "aa".repeat(32)
    private val bobHex = "bb".repeat(32)

    private fun marmotGroup() = MlsGroup.create(aliceHex.hexToByteArray(), policy = MarmotGroupPolicy)

    private fun cordnGroup() =
        MlsGroup.create(
            identity = CordnCredential.of(aliceHex).identity,
            policy = CordnGroupPolicy,
            initialExtensions = listOf(CordnGroupMetadata(name = "both profiles").toExtension()),
        )

    @Test
    fun `the two encodings cannot collide, by length alone`() {
        val marmot = Credential.Basic(aliceHex.hexToByteArray())
        val cordn = CordnCredential.of(aliceHex)

        assertEquals(32, marmot.identity.size)
        assertEquals(64, cordn.identity.size)
        assertNotEquals(marmot.identity.toHexKey(), cordn.identity.toHexKey())

        // The disambiguation is structural, not heuristic: cordn's reader wants
        // exactly 64 bytes, Marmot's wants exactly 32, and no byte string is
        // both. Neither binding needs to guess which profile a leaf belongs to.
        assertNull(CordnCredential.identityOrNull(marmot), "a Marmot credential must not read as a cordn identity")
    }

    @Test
    fun `a cordn credential round-trips through its own reader`() {
        assertEquals(aliceHex, CordnCredential.identityOrNull(CordnCredential.of(aliceHex)))
        assertContentEquals(aliceHex.encodeToByteArray(), CordnCredential.of(aliceHex).identity)
    }

    @Test
    fun `both groups run on the same engine at once`() {
        // Not a formality: since Stage 1 the profile arrives as a constructor
        // argument, so a leaked default or a shared mutable would show up as
        // one group adopting the other's rules.
        val marmot = marmotGroup()
        val cordn = cordnGroup()

        assertEquals(0L, marmot.epoch)
        assertEquals(0L, cordn.epoch)

        // Each group reports its own creator under its own encoding.
        assertEquals(setOf(aliceHex), CordnCredential.memberIdentities(cordn))
        assertEquals(aliceHex, marmot.memberIdentityHex(0))

        // And a message in each still opens.
        listOf(marmot, cordn).forEach { group ->
            val sealed = group.encrypt("hello".encodeToByteArray())
            assertEquals("hello", group.decrypt(sealed).content.decodeToString())
        }
    }

    @Test
    fun `reading a cordn leaf with the raw-bytes helper gives hex-of-hex, not a pubkey`() {
        // The trap, made explicit so nobody "fixes" CordnCredential.membersOf
        // back into memberIdentityHex. 64 ASCII bytes hex-encode to 128 chars.
        val cordn = cordnGroup()

        assertEquals(128, cordn.memberIdentityHex(0)?.length)
        assertNotEquals(aliceHex, cordn.memberIdentityHex(0))
        assertEquals(aliceHex, CordnCredential.identityOrNull(cordn.members().first { it.first == 0 }.second))
    }

    @Test
    fun `each profile admits a joiner carrying its own encoding`() {
        val cordn = cordnGroup()
        val bobsKeyPackage = cordnGroup().createKeyPackage(CordnCredential.of(bobHex).identity, ByteArray(0))

        cordn.addMember(bobsKeyPackage.keyPackage.toTlsBytes())

        assertEquals(setOf(aliceHex, bobHex), CordnCredential.memberIdentities(cordn))

        val marmot = marmotGroup()
        val bobsMarmotKeyPackage = marmotGroup().createKeyPackage(bobHex.hexToByteArray(), ByteArray(0))

        marmot.addMember(bobsMarmotKeyPackage.keyPackage.toTlsBytes())

        assertEquals(bobHex, marmot.memberIdentityHex(1))
        // ...and the joiner each admitted is invisible to the other binding.
        assertNull(CordnCredential.identityOrNull(marmot.members().first { it.first == 1 }.second))
    }
}
