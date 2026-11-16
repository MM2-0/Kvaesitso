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

import com.vitorpamplona.cordn.appMultiDevice.CordnMigrationGroup
import com.vitorpamplona.cordn.appMultiDevice.CordnMigrationSnapshot
import com.vitorpamplona.cordn.client.CoordinatorConfig
import com.vitorpamplona.cordn.client.CordnBlobCipher
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toOkioPath
import java.util.Base64
import kotlin.io.path.createTempDirectory
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A handoff snapshot's base64 must mean the same thing on both sides of an
 * upgrade: a phone on an older build (which used `java.util.Base64`) hands off
 * to one on this build, and the other way round.
 *
 * Every length mod 3, so every padding case, and each input both padded and
 * unpadded, because `java.util.Base64.getDecoder()` accepted either.
 */
class CordnMigrationStoresBase64Test {
    private object Identity : CordnBlobCipher {
        override fun encrypt(bytes: ByteArray) = bytes

        override fun decrypt(bytes: ByteArray) = bytes
    }

    private val rootFile = createTempDirectory("cordn-b64").toFile()
    private val root = rootFile.toOkioPath()
    private val account = "e".repeat(64)
    private val coordinator = "b".repeat(64)

    @AfterTest
    fun cleanUp() {
        rootFile.deleteRecursively()
    }

    @Test
    fun `snapshot base64 is java util Base64 in both directions`() =
        runTest {
            val random = Random(7)
            val blobs = (1..13).map { random.nextBytes(it) }
            val javaEncoder = Base64.getEncoder()
            val unpadded = Base64.getEncoder().withoutPadding()

            val snapshot =
                CordnMigrationSnapshot(
                    accountPubKey = account,
                    groups =
                        blobs.mapIndexed { i, blob ->
                            CordnMigrationGroup(
                                coordinatorPubKey = coordinator,
                                coordinatorRelays = listOf("wss://coord.example"),
                                gid = "g" + i.toString().padStart(2, '0'),
                                clientStateBase64 = if (i % 2 == 0) javaEncoder.encodeToString(blob) else unpadded.encodeToString(blob),
                                cursor = 0,
                            )
                        },
                )

            CordnMigrationStores.write(root, account, Identity, snapshot)
            val read =
                CordnMigrationStores.read(
                    root,
                    account,
                    Identity,
                    listOf(CoordinatorConfig(coordinator, listOf(RelayUrlNormalizer.normalizeOrNull("wss://coord.example")!!))),
                )

            assertEquals(
                blobs.map { javaEncoder.encodeToString(it) },
                read.groups.sortedBy { it.gid }.map { it.clientStateBase64 },
            )
        }
}
