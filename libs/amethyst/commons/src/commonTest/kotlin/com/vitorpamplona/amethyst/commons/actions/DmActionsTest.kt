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
package com.vitorpamplona.amethyst.commons.actions

import com.vitorpamplona.marmot.RecipientRelayFetcher
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nip65RelayList.tags.AdvertisedRelayInfo
import com.vitorpamplona.quartz.nip65RelayList.tags.AdvertisedRelayType
import com.vitorpamplona.quartz.utils.Secp256k1Instance
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DmActionsTest {
    private val senderPriv = "0000000000000000000000000000000000000000000000000000000000000007"
    private val recipientPriv = "0000000000000000000000000000000000000000000000000000000000000019"
    private val signer = NostrSignerInternal(KeyPair(senderPriv.hexToByteArray()))
    private val recipientPub =
        Secp256k1Instance
            .compressedPubKeyFor(recipientPriv.hexToByteArray())
            .copyOfRange(1, 33)
            .toHexKey()

    private val dmInbox = relay("wss://dm-inbox.example")
    private val nip65ReadRelay = relay("wss://nip65-read.example")
    private val nip65WriteRelay = relay("wss://nip65-write.example")
    private val bootstrap = setOf(relay("wss://bootstrap.example"))

    private fun relay(url: String) = RelayUrlNormalizer.normalizeOrNull(url)!!

    /** Build a kind:10002 with one read + one write relay so [nip65Read] returns
     *  the expected single URL. */
    private suspend fun nip65WithReadAndWrite(): AdvertisedRelayListEvent =
        signer.sign(
            createdAt = 1_700_000_000L,
            kind = AdvertisedRelayListEvent.KIND,
            tags =
                arrayOf(
                    AdvertisedRelayInfo.assemble(nip65ReadRelay, AdvertisedRelayType.READ),
                    AdvertisedRelayInfo.assemble(nip65WriteRelay, AdvertisedRelayType.WRITE),
                ),
            content = "",
        )

    // ------------------------------------------------------------------
    // resolveDmRelays — strict (default) mode
    // ------------------------------------------------------------------

    @Test
    fun resolveDmRelays_strictReturnsKind10050WhenPresent() {
        val lists =
            RecipientRelayFetcher.Lists(
                dmInbox = listOf(dmInbox),
                keyPackage = emptyList(),
                nip65 = null,
            )
        val result = DmActions.resolveDmRelays(lists, bootstrap = bootstrap, allowFallback = false)

        assertEquals(setOf(dmInbox), result.relays)
        assertEquals(DmActions.RelaySource.KIND_10050, result.source)
    }

    @Test
    fun resolveDmRelays_strictReturnsNoneWhenKind10050Empty() {
        val lists =
            RecipientRelayFetcher.Lists(
                dmInbox = emptyList(),
                keyPackage = emptyList(),
                nip65 = null,
            )
        val result = DmActions.resolveDmRelays(lists, bootstrap = bootstrap, allowFallback = false)

        // NIP-17 strict mode: no kind:10050 → refuse to deliver. Caller
        // surfaces a no_dm_relays error rather than guessing.
        assertTrue(result.relays.isEmpty())
        assertEquals(DmActions.RelaySource.NONE, result.source)
    }

    @Test
    fun resolveDmRelays_strictReturnsNoneEvenWhenNip65Present() =
        runTest {
            val lists =
                RecipientRelayFetcher.Lists(
                    dmInbox = emptyList(),
                    keyPackage = emptyList(),
                    nip65 = nip65WithReadAndWrite(),
                )
            val result = DmActions.resolveDmRelays(lists, bootstrap = bootstrap, allowFallback = false)

            // Strict mode does not fall through to NIP-65 even if it's present.
            assertTrue(result.relays.isEmpty())
            assertEquals(DmActions.RelaySource.NONE, result.source)
        }

    // ------------------------------------------------------------------
    // resolveDmRelays — permissive (allowFallback=true) mode
    // ------------------------------------------------------------------

    @Test
    fun resolveDmRelays_fallbackPrefersKind10050OverNip65() =
        runTest {
            val lists =
                RecipientRelayFetcher.Lists(
                    dmInbox = listOf(dmInbox),
                    keyPackage = emptyList(),
                    nip65 = nip65WithReadAndWrite(),
                )
            val result = DmActions.resolveDmRelays(lists, bootstrap = bootstrap, allowFallback = true)

            // kind:10050 wins even with fallback enabled — it's still the strict path.
            assertEquals(setOf(dmInbox), result.relays)
            assertEquals(DmActions.RelaySource.KIND_10050, result.source)
        }

    @Test
    fun resolveDmRelays_fallbackUsesNip65ReadWhenKind10050Empty() =
        runTest {
            val lists =
                RecipientRelayFetcher.Lists(
                    dmInbox = emptyList(),
                    keyPackage = emptyList(),
                    nip65 = nip65WithReadAndWrite(),
                )
            val result = DmActions.resolveDmRelays(lists, bootstrap = bootstrap, allowFallback = true)

            // Falls through to NIP-65 read relays — not write — matching User.inboxRelays().
            assertEquals(setOf(nip65ReadRelay), result.relays)
            assertEquals(DmActions.RelaySource.NIP65_READ, result.source)
        }

    @Test
    fun resolveDmRelays_fallbackReachesBootstrapWhenNothingElsePresent() {
        val lists =
            RecipientRelayFetcher.Lists(
                dmInbox = emptyList(),
                keyPackage = emptyList(),
                nip65 = null,
            )
        val result = DmActions.resolveDmRelays(lists, bootstrap = bootstrap, allowFallback = true)

        assertEquals(bootstrap, result.relays)
        assertEquals(DmActions.RelaySource.BOOTSTRAP, result.source)
    }

    @Test
    fun resolveDmRelays_nullListsTreatedAsEmpty() {
        val resultStrict = DmActions.resolveDmRelays(null, bootstrap = bootstrap, allowFallback = false)
        assertEquals(DmActions.RelaySource.NONE, resultStrict.source)

        val resultPermissive = DmActions.resolveDmRelays(null, bootstrap = bootstrap, allowFallback = true)
        // Null Lists → no kind:10050, no NIP-65 → bootstrap.
        assertEquals(DmActions.RelaySource.BOOTSTRAP, resultPermissive.source)
        assertEquals(bootstrap, resultPermissive.relays)
    }

    // ------------------------------------------------------------------
    // buildTextDm — smoke test that we get back a kind:14 and the right
    // wrap count. NIP17Factory internals are exercised more deeply in
    // quartz's own tests.
    // ------------------------------------------------------------------

    @Test
    fun buildTextDm_producesKind14InnerAndOneWrapPerSide() =
        runTest {
            val result = DmActions.buildTextDm(signer, recipientPub, "hi from a test")

            assertEquals(ChatMessageEvent.KIND, result.msg.kind)
            assertEquals(signer.pubKey, result.msg.pubKey)
            assertEquals("hi from a test", result.msg.content)
            // NIP17Factory wraps once per recipient — and the sender keeps
            // their own copy, so a 1-recipient DM produces 2 wraps.
            assertEquals(2, result.wraps.size)
            val recipientsCovered = result.wraps.mapNotNull { it.recipientPubKey() }.toSet()
            assertTrue(signer.pubKey in recipientsCovered, "sender's own copy missing")
            assertTrue(recipientPub in recipientsCovered, "recipient's wrap missing")
        }

    @Test
    fun relaySourceEnumNamesAreStable() {
        // amy emits these as lowercase strings in JSON output; if these
        // names change, the public CLI contract breaks.
        assertEquals(
            setOf("KIND_10050", "NIP65_READ", "BOOTSTRAP", "NONE"),
            DmActions.RelaySource.entries
                .map { it.name }
                .toSet(),
        )
    }
}
