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

import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapReceiptEvent
import com.vitorpamplona.quartz.utils.Secp256k1Instance
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ZapActionsTest {
    private val senderPriv = "0000000000000000000000000000000000000000000000000000000000000007"
    private val authorPriv = "000000000000000000000000000000000000000000000000000000000000000d"
    private val recipientPriv = "0000000000000000000000000000000000000000000000000000000000000011"
    private val signer = NostrSignerInternal(KeyPair(senderPriv.hexToByteArray()))
    private val authorSigner = NostrSignerInternal(KeyPair(authorPriv.hexToByteArray()))

    // Use a real curve-point pubkey — PRIVATE / ANONYMOUS zaps internally do
    // NIP-04-style ECDH with the recipient, which rejects garbage pubkeys.
    private val recipientPubkey = xOnly(recipientPriv)
    private val relay = RelayUrlNormalizer.normalizeOrNull("wss://inbox.example")!!

    private fun xOnly(privHex: String) =
        Secp256k1Instance
            .compressedPubKeyFor(privHex.hexToByteArray())
            .copyOfRange(1, 33)
            .toHexKey()

    @Test
    fun satsToMillisats_multipliesByThousand() {
        assertEquals(0L, ZapActions.satsToMillisats(0))
        assertEquals(1_000L, ZapActions.satsToMillisats(1))
        assertEquals(21_000_000L, ZapActions.satsToMillisats(21_000))
    }

    @Test
    fun extractLnAddress_prefersLud16OverLud06() =
        runTest {
            val metadata =
                signer.sign(
                    MetadataEvent.createNew(
                        name = "alice",
                        lnAddress = "alice@walletofsatoshi.com",
                        lnURL = "lnurl1somelongstring",
                    ),
                )
            assertEquals("alice@walletofsatoshi.com", ZapActions.extractLnAddress(metadata))
        }

    @Test
    fun extractLnAddress_fallsBackToLud06WhenNoLud16() =
        runTest {
            val metadata =
                signer.sign(
                    MetadataEvent.createNew(
                        name = "bob",
                        lnURL = "lnurl1bobsLightning",
                    ),
                )
            assertEquals("lnurl1bobsLightning", ZapActions.extractLnAddress(metadata))
        }

    @Test
    fun extractLnAddress_returnsNullWhenNoLnDetails() =
        runTest {
            val metadata =
                signer.sign(
                    MetadataEvent.createNew(name = "noln"),
                )
            assertNull(ZapActions.extractLnAddress(metadata))
        }

    @Test
    fun buildUserZapRequest_publicTypeStampsAllFields() =
        runTest {
            val request =
                ZapActions.buildUserZapRequest(
                    signer = signer,
                    recipientPubkey = recipientPubkey,
                    amountMillisats = 21_000L,
                    inboxRelays = setOf(relay),
                    comment = "thanks!",
                    zapType = ZapReceiptEvent.ZapType.PUBLIC,
                    lnurl = "lnurl1example",
                )

            assertEquals(9734, request.kind)
            assertEquals(signer.pubKey, request.pubKey, "PUBLIC zap is signed by the sender")
            assertEquals("thanks!", request.content)

            val tagMap = request.tags.groupBy { it[0] }
            assertEquals(recipientPubkey, tagMap["p"]?.first()?.get(1))
            assertEquals("21000", tagMap["amount"]?.first()?.get(1))
            assertEquals("lnurl1example", tagMap["lnurl"]?.first()?.get(1))
            assertTrue(tagMap["relays"]?.first()?.contains(relay.url) == true)
            assertNull(tagMap["anon"], "PUBLIC zap must not carry an anon tag")
        }

    @Test
    fun buildUserZapRequest_anonymousTypeUsesEphemeralKeyAndAnonTag() =
        runTest {
            val request =
                ZapActions.buildUserZapRequest(
                    signer = signer,
                    recipientPubkey = recipientPubkey,
                    amountMillisats = 1_000L,
                    inboxRelays = setOf(relay),
                    zapType = ZapReceiptEvent.ZapType.ANONYMOUS,
                )

            assertTrue(
                request.pubKey != signer.pubKey,
                "ANONYMOUS zaps are signed with a freshly-generated keypair, not the sender's",
            )
            assertNotNull(request.tags.firstOrNull { it[0] == "anon" })
        }

    @Test
    fun buildUserZapRequest_privateTypeCarriesAnonTagWithEncryptedPayload() =
        runTest {
            val request =
                ZapActions.buildUserZapRequest(
                    signer = signer,
                    recipientPubkey = recipientPubkey,
                    amountMillisats = 1_000L,
                    inboxRelays = setOf(relay),
                    zapType = ZapReceiptEvent.ZapType.PRIVATE,
                )

            // NIP-57 PRIVATE zaps use an ephemeral key derived from
            // (sender, recipient, zappedEvent) so the recipient can re-derive
            // and verify origin via NIP-04 decryption of the anon tag value.
            // The outer event is therefore NOT signed by the sender.
            val anon = request.tags.firstOrNull { it[0] == "anon" }
            assertNotNull(anon, "PRIVATE zap must carry an anon tag")
            assertTrue(
                (anon.getOrNull(1) ?: "").isNotEmpty(),
                "PRIVATE zap's anon tag carries the NIP-04-encrypted private payload",
            )
        }

    @Test
    fun buildEventZapRequest_carriesEventTagAndAuthorPTag() =
        runTest {
            val note = authorSigner.sign(TextNoteEvent.build("hello world"))

            val request =
                ZapActions.buildEventZapRequest(
                    signer = signer,
                    zappedEvent = note,
                    amountMillisats = 5_000L,
                    inboxRelays = setOf(relay),
                    comment = "great post",
                )

            val tagMap = request.tags.groupBy { it[0] }
            assertEquals(note.id, tagMap["e"]?.first()?.get(1))
            assertEquals(note.pubKey, tagMap["p"]?.first()?.get(1))
            assertEquals("5000", tagMap["amount"]?.first()?.get(1))
            assertEquals("great post", request.content)
        }

    @Test
    fun buildEventZapRequest_toUserPubkeyOverridesAuthorTag() =
        runTest {
            val note = authorSigner.sign(TextNoteEvent.build("split me"))
            val splitTo = "2222222222222222222222222222222222222222222222222222222222222222"

            val request =
                ZapActions.buildEventZapRequest(
                    signer = signer,
                    zappedEvent = note,
                    amountMillisats = 5_000L,
                    inboxRelays = setOf(relay),
                    toUserPubkey = splitTo,
                )

            val pTag = request.tags.firstOrNull { it[0] == "p" }
            assertEquals(splitTo, pTag?.getOrNull(1), "explicit toUserPubkey wins over event.pubKey")
        }

    // ------------------------------------------------------------------
    // buildEventZapRequestsForSplits — covers the correctness bug the
    // single-recipient buildEventZapRequest has for split notes.
    // ------------------------------------------------------------------

    @Test
    fun buildEventZapRequestsForSplits_lnAddressSplitTagsProduceOneRequestPerRecipient() =
        runTest {
            val note =
                authorSigner.sign<com.vitorpamplona.quartz.nip10Notes.TextNoteEvent>(
                    createdAt = 1_700_000_000L,
                    kind = com.vitorpamplona.quartz.nip10Notes.TextNoteEvent.KIND,
                    tags =
                        arrayOf(
                            arrayOf("zap", "alice@wallet.example"),
                            arrayOf("zap", "bob@wallet.example"),
                        ),
                    content = "split me 50/50",
                )

            val requests =
                ZapActions.buildEventZapRequestsForSplits(
                    signer = signer,
                    zappedEvent = note,
                    totalAmountMillisats = 10_000L,
                    senderInboxRelays = setOf(relay),
                    lookupLnAddress = { null },
                )

            assertEquals(2, requests.size)
            assertEquals(setOf("alice@wallet.example", "bob@wallet.example"), requests.map { it.recipient.lnAddress }.toSet())
            // LnAddress-style splits are always weight 1.0 (per quartz parser),
            // so 10000 msats / 2 = 5000 msats each.
            assertEquals(setOf(5_000L), requests.map { it.amountMillisats }.toSet())
        }

    @Test
    fun buildEventZapRequestsForSplits_pubkeySplitsRespectWeights() =
        runTest {
            val splitAPriv = "000000000000000000000000000000000000000000000000000000000000000d"
            val splitAPub =
                com.vitorpamplona.quartz.utils.Secp256k1Instance
                    .compressedPubKeyFor(splitAPriv.hexToByteArray())
                    .copyOfRange(1, 33)
                    .toHexKey()
            val splitBPriv = "0000000000000000000000000000000000000000000000000000000000000011"
            val splitBPub =
                com.vitorpamplona.quartz.utils.Secp256k1Instance
                    .compressedPubKeyFor(splitBPriv.hexToByteArray())
                    .copyOfRange(1, 33)
                    .toHexKey()

            val note =
                authorSigner.sign<com.vitorpamplona.quartz.nip10Notes.TextNoteEvent>(
                    createdAt = 1_700_000_000L,
                    kind = com.vitorpamplona.quartz.nip10Notes.TextNoteEvent.KIND,
                    tags =
                        arrayOf(
                            arrayOf("zap", splitAPub, "", "1.0"),
                            arrayOf("zap", splitBPub, "", "4.0"),
                        ),
                    content = "20/80 split",
                )

            val requests =
                ZapActions.buildEventZapRequestsForSplits(
                    signer = signer,
                    zappedEvent = note,
                    totalAmountMillisats = 100_000L, // 100 sats
                    senderInboxRelays = setOf(relay),
                    lookupLnAddress = { pk ->
                        when (pk) {
                            splitAPub -> "a@wallet"
                            splitBPub -> "b@wallet"
                            else -> null
                        }
                    },
                )

            val byPub = requests.associateBy { it.recipient.pubkey }
            assertEquals(20_000L, byPub[splitAPub]?.amountMillisats, "1/5 of 100 sats")
            assertEquals(80_000L, byPub[splitBPub]?.amountMillisats, "4/5 of 100 sats")
            // Sum matches input within rounding.
            assertEquals(100_000L, requests.sumOf { it.amountMillisats })
        }

    @Test
    fun buildEventZapRequestsForSplits_unionsAuthorAndRecipientInboxRelays() =
        runTest {
            // Use a key distinct from authorPriv/senderPriv so the split
            // recipient and the note author are different pubkeys — otherwise
            // their inbox-relay lookups collide and we can't tell which one
            // ended up in the relays tag.
            val splitPriv = "0000000000000000000000000000000000000000000000000000000000000019"
            val splitPub =
                com.vitorpamplona.quartz.utils.Secp256k1Instance
                    .compressedPubKeyFor(splitPriv.hexToByteArray())
                    .copyOfRange(1, 33)
                    .toHexKey()
            val note =
                authorSigner.sign<com.vitorpamplona.quartz.nip10Notes.TextNoteEvent>(
                    createdAt = 1_700_000_000L,
                    kind = com.vitorpamplona.quartz.nip10Notes.TextNoteEvent.KIND,
                    tags = arrayOf(arrayOf("zap", splitPub, "", "1.0")),
                    content = "test inbox unioning",
                )

            val senderRelay = relay
            val authorRelay =
                com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
                    .normalizeOrNull("wss://author-inbox.example")!!
            val recipientRelay =
                com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
                    .normalizeOrNull("wss://recipient-inbox.example")!!

            val requests =
                ZapActions.buildEventZapRequestsForSplits(
                    signer = signer,
                    zappedEvent = note,
                    totalAmountMillisats = 1_000L,
                    senderInboxRelays = setOf(senderRelay),
                    lookupLnAddress = { _ -> "x@wallet" },
                    lookupInboxRelays = { pk ->
                        when (pk) {
                            authorSigner.pubKey -> setOf(authorRelay)
                            splitPub -> setOf(recipientRelay)
                            else -> emptySet()
                        }
                    },
                )

            assertEquals(1, requests.size)
            val relaysTag = requests[0].request.tags.firstOrNull { it[0] == "relays" }
            assertNotNull(relaysTag)
            val relayUrls = relaysTag.drop(1).toSet()
            // All three sources end up in the kind:9734 `relays` tag.
            assertTrue(senderRelay.url in relayUrls, "sender inbox missing")
            assertTrue(authorRelay.url in relayUrls, "author inbox missing")
            assertTrue(recipientRelay.url in relayUrls, "recipient inbox missing")
        }

    @Test
    fun buildEventZapRequestsForSplits_emptyWhenNoRecipientHasLnAddress() =
        runTest {
            val splitPriv = "000000000000000000000000000000000000000000000000000000000000000d"
            val splitPub =
                com.vitorpamplona.quartz.utils.Secp256k1Instance
                    .compressedPubKeyFor(splitPriv.hexToByteArray())
                    .copyOfRange(1, 33)
                    .toHexKey()
            val note =
                authorSigner.sign<com.vitorpamplona.quartz.nip10Notes.TextNoteEvent>(
                    createdAt = 1_700_000_000L,
                    kind = com.vitorpamplona.quartz.nip10Notes.TextNoteEvent.KIND,
                    tags = arrayOf(arrayOf("zap", splitPub, "", "1.0")),
                    content = "no recipient ln",
                )

            val requests =
                ZapActions.buildEventZapRequestsForSplits(
                    signer = signer,
                    zappedEvent = note,
                    totalAmountMillisats = 10_000L,
                    senderInboxRelays = setOf(relay),
                    lookupLnAddress = { null },
                )

            assertTrue(requests.isEmpty())
        }
}
