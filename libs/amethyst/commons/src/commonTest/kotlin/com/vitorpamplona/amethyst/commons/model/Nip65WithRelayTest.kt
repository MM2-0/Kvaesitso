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

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip65RelayList.tags.AdvertisedRelayInfo
import com.vitorpamplona.quartz.nip65RelayList.tags.AdvertisedRelayType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Nip65WithRelayTest {
    private val damus = RelayUrlNormalizer.normalizeOrNull("wss://relay.damus.io")!!
    private val nos = RelayUrlNormalizer.normalizeOrNull("wss://nos.lol")!!
    private val local = RelayUrlNormalizer.normalizeOrNull("ws://127.0.0.1:4869")!!

    private fun List<AdvertisedRelayInfo>?.summary() = this?.map { it.relayUrl.url to it.type }

    @Test
    fun appendsANewRelayWithTheRequestedDirection() {
        val result = nip65WithRelay(listOf(AdvertisedRelayInfo(damus, AdvertisedRelayType.BOTH)), nos, AdvertisedRelayType.WRITE)

        assertEquals(listOf(damus.url to AdvertisedRelayType.BOTH, nos.url to AdvertisedRelayType.WRITE), result.summary())
    }

    @Test
    fun returnsNullWhenTheRelayIsAlreadyAdvertisedThatWay() {
        assertNull(nip65WithRelay(listOf(AdvertisedRelayInfo(damus, AdvertisedRelayType.WRITE)), damus, AdvertisedRelayType.WRITE))
        assertNull(nip65WithRelay(listOf(AdvertisedRelayInfo(damus, AdvertisedRelayType.BOTH)), damus, AdvertisedRelayType.READ))
    }

    @Test
    fun upgradesTheOtherDirectionToBothInPlace() {
        val current =
            listOf(
                AdvertisedRelayInfo(damus, AdvertisedRelayType.READ),
                AdvertisedRelayInfo(nos, AdvertisedRelayType.BOTH),
            )

        val result = nip65WithRelay(current, damus, AdvertisedRelayType.WRITE)

        assertEquals(listOf(damus.url to AdvertisedRelayType.BOTH, nos.url to AdvertisedRelayType.BOTH), result.summary())
    }

    @Test
    fun collapsesDuplicateTagsForTheRelay() {
        val current =
            listOf(
                AdvertisedRelayInfo(damus, AdvertisedRelayType.READ),
                AdvertisedRelayInfo(nos, AdvertisedRelayType.BOTH),
                AdvertisedRelayInfo(damus, AdvertisedRelayType.READ),
            )

        val result = nip65WithRelay(current, damus, AdvertisedRelayType.WRITE)

        assertEquals(listOf(damus.url to AdvertisedRelayType.BOTH, nos.url to AdvertisedRelayType.BOTH), result.summary())
    }

    // Open question for the maintainer: may the user's own NIP-65 outbox/inbox hold a local relay?
    // Until decided, the one-tap add keeps refusing one; entries already there are kept (below).
    @Test
    fun oneTapAddRefusesALocalRelayForTheNip65OutboxAndInbox() {
        assertNull(nip65WithRelay(listOf(AdvertisedRelayInfo(damus, AdvertisedRelayType.BOTH)), local, AdvertisedRelayType.WRITE))
        assertFalse(MyRelayList.OUTBOX.accepts(local))
        assertFalse(MyRelayList.INBOX.accepts(local))
        assertTrue(MyRelayList.PRIVATE_HOME.accepts(local))
    }

    @Test
    fun keepsLocalRelaysAlreadyInTheUsersOwnList() {
        // Republishing the user's own list must never silently lose an entry they configured.
        val result = nip65WithRelay(listOf(AdvertisedRelayInfo(local, AdvertisedRelayType.BOTH)), nos, AdvertisedRelayType.READ)

        assertEquals(listOf(local.url to AdvertisedRelayType.BOTH, nos.url to AdvertisedRelayType.READ), result.summary())
    }

    @Test
    fun theSettingsSaveRebuildsTheListItLoaded() {
        val loaded =
            listOf(
                AdvertisedRelayInfo(damus, AdvertisedRelayType.BOTH),
                AdvertisedRelayInfo(local, AdvertisedRelayType.WRITE),
                AdvertisedRelayInfo(nos, AdvertisedRelayType.READ),
            )
        val writes = loaded.filter { it.type.isWrite() }.map { it.relayUrl }.toSet()
        val reads = loaded.filter { it.type.isRead() }.map { it.relayUrl }.toSet()

        assertEquals(loaded.summary()?.toSet(), nip65ListOf(writes, reads).summary()?.toSet())
    }
}
