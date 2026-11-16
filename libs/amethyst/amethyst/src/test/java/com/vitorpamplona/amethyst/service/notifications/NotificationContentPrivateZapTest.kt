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
package com.vitorpamplona.amethyst.service.notifications

import com.vitorpamplona.amethyst.commons.notifications.NotificationContent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip57Zaps.ZapReceiptEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapRequestEvent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * A private zap is signed by a one-time key and carries the real sender and message encrypted
 * for the zapped author. The notification must decrypt it with the recipient's signer so the
 * shade names the real zapper, and fall back to the request itself when it can't.
 */
class NotificationContentPrivateZapTest {
    private val relays = setOf(RelayUrlNormalizer.normalizeOrNull("wss://relay.example/")!!)
    private val recipient = NostrSignerInternal(KeyPair())
    private val sender = NostrSignerInternal(KeyPair())

    private val zappedPost =
        Event(
            id = "a".repeat(64),
            pubKey = recipient.pubKey,
            createdAt = 1000L,
            kind = 1,
            tags = emptyArray(),
            content = "Hello world",
            sig = "b".repeat(128),
        )

    private fun zapPost(zapType: ZapReceiptEvent.ZapType) =
        runBlocking {
            ZapRequestEvent.create(
                zappedEvent = zappedPost,
                relays = relays,
                signer = sender,
                pollOption = null,
                message = "great post",
                zapType = zapType,
                toUserPubHex = null,
            )
        }

    @Test
    fun privateZapOnAPostIsDecryptedForTheRecipient() =
        runBlocking {
            val decrypted = NotificationContent.decryptZapContentAuthor(zapPost(ZapReceiptEvent.ZapType.PRIVATE), recipient)!!

            assertEquals(sender.pubKey, decrypted.pubKey)
            assertEquals("great post", decrypted.content)
        }

    @Test
    fun privateProfileZapIsDecryptedForTheRecipient() =
        runBlocking {
            val zap =
                ZapRequestEvent.create(
                    userHex = recipient.pubKey,
                    relays = relays,
                    signer = sender,
                    message = "thanks",
                    zapType = ZapReceiptEvent.ZapType.PRIVATE,
                )

            val decrypted = NotificationContent.decryptZapContentAuthor(zap, recipient)!!

            assertEquals(sender.pubKey, decrypted.pubKey)
            assertEquals("thanks", decrypted.content)
        }

    @Test
    fun privateZapTheSignerCannotOpenFallsBackToTheRequest() =
        runBlocking {
            val zap = zapPost(ZapReceiptEvent.ZapType.PRIVATE)

            assertSame(zap, NotificationContent.decryptZapContentAuthor(zap, NostrSignerInternal(KeyPair())))
        }

    @Test
    fun publicZapIsReturnedAsIs() =
        runBlocking {
            val zap = zapPost(ZapReceiptEvent.ZapType.PUBLIC)

            assertSame(zap, NotificationContent.decryptZapContentAuthor(zap, recipient))
        }
}
