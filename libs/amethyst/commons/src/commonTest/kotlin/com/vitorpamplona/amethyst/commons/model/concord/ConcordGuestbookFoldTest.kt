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
package com.vitorpamplona.amethyst.commons.model.concord

import com.vitorpamplona.amethyst.commons.actions.ConcordActions
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityFactory
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEntry
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The guestbook re-folds on every arriving wrap, so opening the buffer each time is quadratic in
 * NIP-44 decrypts (plus two signature verifies apiece). A cold start measured 6,229 envelope opens
 * over 448 distinct wraps — ~13x redundant, and effectively all of the app's NIP-44 traffic.
 */
class ConcordGuestbookFoldTest {
    private val owner = NostrSignerInternal(KeyPair())

    @Test
    fun opensEachGuestbookWrapOnceAcrossSequentialArrivals() =
        runTest {
            val community = ConcordCommunityFactory.create(owner, "Nostrichs", createdAt = 1L, relays = listOf("wss://r.example"))
            val entry =
                ConcordCommunityListEntry(
                    id = community.communityIdHex,
                    owner = community.ownerPubKey,
                    ownerSalt = community.ownerSalt.toHexKey(),
                    root = community.communityRoot.toHexKey(),
                    rootEpoch = community.rootEpoch,
                    controlPk = community.controlPkHex,
                    controlRoot = community.controlRoot.toHexKey(),
                    relays = listOf("wss://r.example"),
                    name = "Nostrichs",
                )
            val session = ConcordCommunitySession(entry, owner.pubKey) { _, _, _, _ -> }
            community.genesisWraps.forEach { session.ingest(it) }

            val guestbook = ConcordActions.guestbookPlane(community.communityRoot, community.communityId, community.rootEpoch)
            val members = List(12) { NostrSignerInternal(KeyPair()) }
            val joins = members.mapIndexed { i, m -> ConcordActions.buildGuestbookJoin(m, guestbook, createdAt = 2L + i) }

            // Arrivals land one at a time, exactly as the relay delivers them.
            joins.forEach { session.ingest(it) }

            // Linear, not 12*13/2 = 78.
            assertEquals(joins.size, session.guestbookOpens, "guestbook wraps were re-decrypted on later folds")
            assertEquals(members.mapTo(HashSet()) { it.pubKey.lowercase() }, session.members.value)

            // A duplicate delivery re-folds nothing and opens nothing.
            session.ingest(joins.first())
            assertEquals(joins.size, session.guestbookOpens)
            assertEquals(members.mapTo(HashSet()) { it.pubKey.lowercase() }, session.members.value)
        }
}
