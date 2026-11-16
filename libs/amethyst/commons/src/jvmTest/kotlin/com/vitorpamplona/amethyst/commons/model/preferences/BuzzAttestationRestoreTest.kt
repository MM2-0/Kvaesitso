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
package com.vitorpamplona.amethyst.commons.model.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The migration precedence in [BuzzAttestationStore.restoreFrom]: which of the two on-disk
 * shapes wins when the held attestation moved from one device-global list to a per-account key.
 *
 * The store itself needs a DataStore, so the decision is pulled out as a pure function — this is
 * the part with the sharp edge, and it is the part the DataStore round-trip cannot express.
 */
class BuzzAttestationRestoreTest {
    private val me = "a".repeat(64)
    private val someoneElse = "b".repeat(64)
    private val owner = "c".repeat(64)
    private val sig = "d".repeat(128)

    private fun saved(
        owner: String = this.owner,
        conditions: String = "kind=40002",
    ) = """{"owner":"$owner","conditions":"$conditions","sig":"$sig"}"""

    private fun legacyList(vararg agents: String) = agents.joinToString(",", "[", "]") { """{"agent":"$it","owner":"$owner","conditions":"kind=40002","sig":"$sig"}""" }

    @Test
    fun nothingSavedAnywhereRestoresNothing() {
        assertNull(BuzzAttestationStore.restoreFrom(null, null, me))
    }

    @Test
    fun thisAccountsOwnKeyWins() {
        val restored = BuzzAttestationStore.restoreFrom(saved(), legacyList(me), me)
        assertEquals(owner, restored?.ownerPubKey)
    }

    @Test
    fun aRemovedAttestationIsNotResurrectedFromTheLegacyList() {
        // The regression this test exists for. Removing the held credential used to delete the
        // per-account key, which is indistinguishable from "never migrated" — so the next launch
        // seeded it straight back out of the legacy list, which nothing ever clears. An explicit
        // tombstone is the only thing that can say "migrated, and holding nothing".
        assertNull(BuzzAttestationStore.restoreFrom("", legacyList(me), me))
    }

    @Test
    fun aNeverMigratedAccountTakesItsOwnEntryFromTheLegacyList() {
        val restored = BuzzAttestationStore.restoreFrom(null, legacyList(someoneElse, me), me)
        assertEquals(owner, restored?.ownerPubKey)
    }

    @Test
    fun anotherAgentsLegacyEntryIsNeverPickedUp() {
        // The legacy list was already agent-keyed, so the migration is exact rather than
        // best-effort: there is no shared blob to accidentally inherit.
        assertNull(BuzzAttestationStore.restoreFrom(null, legacyList(someoneElse), me))
    }
}
