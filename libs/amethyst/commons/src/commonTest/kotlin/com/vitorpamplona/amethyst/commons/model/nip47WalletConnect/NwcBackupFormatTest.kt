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
package com.vitorpamplona.amethyst.commons.model.nip47WalletConnect

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip47WalletConnect.Nip47WalletConnect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NwcBackupFormatTest {
    private val uri1String =
        "nostr+walletconnect://b889ff5b1513b641e2a139f661a661364979c5beee91842f8f0ef42ab558e9d4" +
            "?relay=wss%3A%2F%2Frelay.damus.io&secret=71a8c14c1407c113601079c4302dab36460f0ccd0ad506f1f2dc73b5100571c5&lud16=user%40example.com"

    private val uri1 = Nip47WalletConnect.parse(uri1String)

    private val uri2 =
        Nip47WalletConnect.parse(
            "nostr+walletconnect://69effe7b49a6dd5cf525bd0905917a5005ffe480b58eeb8e861418cf3ae760d9?relay=wss%3A%2F%2Frelay.getalby.com%2Fv1&secret=e839faf78693765b3833027fefa5a305c78f6965d0a5d2e47a3fcb25aa7cc45b&lud16=hello%40getalby.com",
        )

    private fun wallet(
        name: String,
        uri: Nip47WalletConnect.Nip47URINorm,
        id: String = "id-$name",
    ) = NwcWalletEntryNorm(id = id, name = name, uri = uri)

    @Test
    fun multiWalletPayloadRoundTrips() {
        val wallets = listOf(wallet("Main", uri1), wallet("Spending", uri2))
        val encoded = NwcBackupFormat.encode(wallets, defaultId = "id-Spending")

        val decoded = NwcBackupFormat.decodePlaintext(encoded)

        assertEquals(2, decoded?.wallets?.size)
        assertEquals("id-Spending", decoded?.defaultId)
        assertEquals("Main", decoded?.wallets?.first()?.name)
        assertEquals(
            uri1,
            decoded
                ?.wallets
                ?.first()
                ?.normalize()
                ?.uri,
        )
        assertEquals(
            uri2,
            decoded
                ?.wallets
                ?.get(1)
                ?.normalize()
                ?.uri,
        )
    }

    @Test
    fun nullDefaultIdRoundTrips() {
        val encoded = NwcBackupFormat.encode(listOf(wallet("Main", uri1)), defaultId = null)
        assertNull(NwcBackupFormat.decodePlaintext(encoded)?.defaultId)
    }

    @Test
    fun decodesBareConnectionUriFromWispFormat() {
        val decoded = NwcBackupFormat.decodePlaintext(uri1String)

        assertEquals(1, decoded?.wallets?.size)
        assertEquals(
            uri1,
            decoded
                ?.wallets
                ?.first()
                ?.normalize()
                ?.uri,
        )
        assertNull(decoded?.defaultId)
    }

    @Test
    fun decodeIgnoresWispSparkSeedPlaintext() {
        assertNull(NwcBackupFormat.decodePlaintext("abandon ability able about above absent absorb abstract absurd abuse access accident"))
        assertNull(NwcBackupFormat.decodePlaintext("{\"walletId\":\"abc\",\"mnemonic\":\"abandon ability\"}"))
    }

    @Test
    fun decodeRejectsForeignText() {
        assertNull(NwcBackupFormat.decodePlaintext(""))
        assertNull(NwcBackupFormat.decodePlaintext("   "))
        assertNull(NwcBackupFormat.decodePlaintext("not a backup at all"))
    }

    @Test
    fun decodeRejectsEmptyWalletPayload() {
        val encoded = NwcBackupFormat.encode(emptyList(), defaultId = null)
        assertNull(NwcBackupFormat.decodePlaintext(encoded))
    }

    @Test
    fun tombstoneDetection() {
        assertTrue(NwcBackupFormat.isTombstone(tags = emptyArray(), content = ""))
        assertTrue(
            NwcBackupFormat.isTombstone(
                tags = arrayOf(arrayOf("d", NwcBackupFormat.D_TAG), arrayOf("deleted", "true")),
                content = "anything",
            ),
        )
        assertTrue(
            !NwcBackupFormat.isTombstone(
                tags = arrayOf(arrayOf("d", NwcBackupFormat.D_TAG)),
                content = "A?QBq0t6",
            ),
        )
    }

    @Test
    fun dTagExtraction() {
        val tags = arrayOf(arrayOf("encryption", "nip44"), arrayOf("d", NwcBackupFormat.D_TAG))
        assertEquals(NwcBackupFormat.D_TAG, NwcBackupFormat.dTagOf(tags))
        assertNull(NwcBackupFormat.dTagOf(emptyArray()))
    }

    @Test
    fun connectionStringRoundTripsThroughParser() {
        val connectionString = uri2.toConnectionString()

        assertEquals(
            uri2,
            Nip47WalletConnect.parse(connectionString),
            "parse(toConnectionString()) must return the original normalized URI",
        )
    }

    @Test
    fun connectionStringEncodesQueryParameters() {
        val connectionString = uri1.toConnectionString()

        assertTrue(connectionString.startsWith("nostr+walletconnect://b889ff5b"))
        assertTrue(connectionString.contains("relay=wss%3A%2F%2Frelay.damus.io"))
        assertTrue(connectionString.contains("lud16=user%40example.com"))
    }

    @Test
    fun connectionStringOmitsAbsentOptionalParameters() {
        val withoutOptional = uri2.copy(secret = null, lud16 = null).toConnectionString()

        assertTrue(withoutOptional.endsWith("?relay=wss%3A%2F%2Frelay.getalby.com%2Fv1"))
    }

    private fun backupEvent(
        dTag: String,
        createdAt: Long,
        content: String = "ciphertext",
        deleted: Boolean = false,
    ): Event {
        val tags = if (deleted) arrayOf(arrayOf("d", dTag), arrayOf("deleted", "true")) else arrayOf(arrayOf("d", dTag))
        return Event("id-$dTag-$createdAt-$deleted", "pub", createdAt, 30078, tags, content, "sig")
    }

    @Test
    fun liveBackupsPutsTheNewestFirstAcrossApps() {
        val wisp = backupEvent(NwcBackupFormat.WISP_D_TAG, createdAt = 100)
        val sidecar = backupEvent(NwcBackupFormat.SIDECAR_D_TAG, createdAt = 200)

        assertEquals(listOf(sidecar, wisp), NwcBackupFormat.liveBackups(listOf(wisp, sidecar)))
    }

    @Test
    fun liveBackupsLetsOnlyANewerOrSameSecondTombstoneHideABackup() {
        val live = backupEvent(NwcBackupFormat.WISP_D_TAG, createdAt = 200)

        val olderTombstone = backupEvent(NwcBackupFormat.WISP_D_TAG, createdAt = 100, content = "", deleted = true)
        assertEquals(listOf(live), NwcBackupFormat.liveBackups(listOf(olderTombstone, live)))

        val sameSecondTombstone = backupEvent(NwcBackupFormat.WISP_D_TAG, createdAt = 200, content = "", deleted = true)
        assertEquals(emptyList(), NwcBackupFormat.liveBackups(listOf(live, sameSecondTombstone)))
        assertEquals(emptyList(), NwcBackupFormat.liveBackups(listOf(sameSecondTombstone, live)))

        val newerTombstone = backupEvent(NwcBackupFormat.WISP_D_TAG, createdAt = 300, content = "")
        assertEquals(emptyList(), NwcBackupFormat.liveBackups(listOf(live, newerTombstone)))
    }

    @Test
    fun liveBackupsIgnoresOtherDTags() {
        val spark = backupEvent("spark-wallet-backup:abc", createdAt = 500)

        assertEquals(emptyList(), NwcBackupFormat.liveBackups(listOf(spark)))
    }
}
