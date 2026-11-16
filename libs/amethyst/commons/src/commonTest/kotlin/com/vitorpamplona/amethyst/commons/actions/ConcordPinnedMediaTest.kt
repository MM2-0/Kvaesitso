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

import com.vitorpamplona.amethyst.commons.model.concord.ConcordCommunitySession
import com.vitorpamplona.amethyst.commons.model.nip92IMeta.appendMissingImetaUrls
import com.vitorpamplona.crypto.aes.AESGCM
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityFactory
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEntry
import com.vitorpamplona.quartz.concord.cord03Channels.ChannelChat
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * A pinned message carrying an encrypted attachment renders like the feed message it pins (CORD-04 §7
 * SHOULD): the rumor rebuilt from the verified proof keeps the `imeta` tags — so the attachment's
 * decryption key reaches the media pipeline even when this account never held the message — and an
 * attachment-only message still gets its URL as text, as the feed gives it.
 */
class ConcordPinnedMediaTest {
    private val owner = NostrSignerInternal(KeyPair())
    private val alice = NostrSignerInternal(KeyPair())

    @Test
    fun aPinnedImageKeepsItsEncryptedAttachment() =
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
            val rumors = mutableListOf<Event>()
            val session = ConcordCommunitySession(entry, owner.pubKey) { _, _, rumor, _ -> rumors += rumor }
            community.genesisWraps.forEach { session.ingest(it) }
            // The genesis wraps are the whole plane; pin writes wait for a drained fold (CORD-04 §7).
            session.markControlDrained()
            val general = community.generalChannelIdHex
            val plane = assertNotNull(session.currentChannelPlane(general))

            val url = "https://blossom.example/ciphertext.bin"
            val cipher = AESGCM(ByteArray(32) { 0x11 }, ByteArray(16) { 0x22 })
            val imeta = ChannelChat.encryptedImageImeta(url, "image/jpeg", "800x600", null, cipher, "aa".repeat(32))
            // An attachment-only message: empty words, the image only in its imeta (Armada allows it).
            session.ingest(ConcordActions.buildChannelImageMessage(alice, plane.key, general, plane.epoch, "", listOf(imeta), 10L))
            val message = rumors.last()

            fun ctx(pins: ConcordChannelPins) =
                ConcordPinContext(
                    actor = owner,
                    controlPlane = session.controlPlaneKeys(),
                    communityId = community.communityId,
                    owner = community.ownerPubKey,
                    current = session.controlEditions(),
                    channelIdHex = general,
                    channelIsPrivate = false,
                    currentPlane = plane,
                    pins = pins,
                    authorized = true,
                )

            val evidence = ConcordPinEvidence(rumors)
            val before = assertNotNull(session.readPins(general, evidence::isKilled, evidence::newestEdit))
            val write = ConcordPinning.pin(ctx(before), assertNotNull(session.pinSource(general, message.id)), 11L)
            session.ingest(assertNotNull(write.wrap))

            val pinned = assertNotNull(session.readPins(general, evidence::isKilled, evidence::newestEdit)).pins.single()
            val rumor = pinned.toRumor()
            assertEquals(message.id, rumor.id)
            assertEquals(alice.pubKey, rumor.pubKey)

            val attachment = ChannelChat.encryptedImagesOf(rumor).single()
            assertEquals(url, attachment.url)
            assertContentEquals(cipher.keyBytes, attachment.key)
            assertContentEquals(cipher.nonce, attachment.nonce)
            // The words carry the ciphertext URL (Armada's assembly), so the shared renderer shows it.
            assertEquals(url, rumor.content)
            // A client that sent only the imeta (empty words) still renders it: the feed's fallback.
            assertEquals(url, appendMissingImetaUrls("", rumor))
        }
}
