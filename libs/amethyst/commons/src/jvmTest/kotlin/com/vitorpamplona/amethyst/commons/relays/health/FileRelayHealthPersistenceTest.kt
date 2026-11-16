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
package com.vitorpamplona.amethyst.commons.relays.health

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertSame

class FileRelayHealthPersistenceTest {
    private val dir = Files.createTempDirectory("relay-health").toFile()
    private val file = File(dir, "relay_health.json")
    private val relay = NormalizedRelayUrl("wss://nos.lol/")

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun aMissingFileLoadsEmpty() {
        assertEquals(RelayHealthSnapshot(), FileRelayHealthPersistence(file).load())
    }

    @Test
    fun aSavedSnapshotComesBack() {
        val saved =
            RelayHealthSnapshot(
                records = mapOf(relay to RelayHealthRecord(lastConnectAt = 10, lastIncomingAt = 20, snoozedUntil = 30)),
                firstScanAt = 5,
                lastSeenAny = 20,
                latencySamples = mapOf(relay to mapOf(LatencyMetric.EOSE to intArrayOf(120, 340, 90))),
            )
        FileRelayHealthPersistence(file).save(saved)

        val loaded = FileRelayHealthPersistence(file).load()
        assertEquals(saved.records, loaded.records)
        assertEquals(5, loaded.firstScanAt)
        assertEquals(20, loaded.lastSeenAny)
        assertContentEquals(intArrayOf(120, 340, 90), loaded.latencySamples[relay]?.get(LatencyMetric.EOSE))
    }

    @Test
    fun loadedRelaysAreTheSharedInstancesNotNewCopies() {
        val canonical = RelayUrlNormalizer.normalize("wss://health-identity.example.com/")
        FileRelayHealthPersistence(file).save(
            RelayHealthSnapshot(
                records = mapOf(canonical to RelayHealthRecord(lastConnectAt = 1)),
                latencySamples = mapOf(canonical to mapOf(LatencyMetric.EOSE to intArrayOf(10))),
            ),
        )

        val loaded = FileRelayHealthPersistence(file).load()

        assertSame(canonical, loaded.records.keys.single())
        assertSame(canonical, loaded.latencySamples.keys.single())
    }

    @Test
    fun aStoredKeyTheNormalizerRejectsIsDropped() {
        file.writeText("""{"records":{"not a relay":{"lastConnectAt":1}}}""")

        assertEquals(emptyMap(), FileRelayHealthPersistence(file).load().records)
    }

    @Test
    fun aCorruptFileLoadsEmpty() {
        file.writeText("{ not json")
        assertEquals(RelayHealthSnapshot(), FileRelayHealthPersistence(file).load())
    }
}
