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
package com.vitorpamplona.amethyst.service.pow

import com.vitorpamplona.amethyst.commons.service.pow.PersistedPoWJob
import com.vitorpamplona.amethyst.commons.service.pow.PoWJobsFile
import com.vitorpamplona.amethyst.commons.service.resourceusage.ResourceUsageStore
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The PoW-queue and resource-usage files survived the move off Jackson.
 *
 * Both hold state an update must not lose: `pending_pow_jobs.json` is posts the user
 * already hit send on that are still mining, and the usage file backs the
 * high-consumption alert. Each literal below is the exact string the Jackson build
 * wrote for the object beside it, captured before the switch.
 */
class PowAndUsageFileFormatTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    private val powSample =
        PoWJobsFile(
            version = 1,
            jobs =
                listOf(
                    PersistedPoWJob(
                        id = "j1",
                        accountPubkey = "pk1",
                        kind = 1,
                        difficulty = 21,
                        templateJson = """{"t":1}""",
                        replayType = "broadcast",
                        relayUrls = listOf("wss://a"),
                        extraEventsJson = emptyList(),
                        publishAtSec = null,
                        recipientPubkeys = listOf("r1"),
                        wrapExpirationDelta = null,
                        createdAtSec = 999L,
                    ),
                ),
        )

    private val usageSample =
        ResourceUsageStore.UsageFile(
            version = 1,
            days = mapOf("20260919" to mapOf("relay.a" to 12L, "relay.b" to 34L)),
            lastAlertAtSec = 555L,
            alertsOptOut = true,
        )

    @Test
    fun powJobsWriteTheBytesJacksonWrote() {
        assertEquals(POW_JACKSON_OUTPUT, json.encodeToString(powSample))
    }

    @Test
    fun powJobsFromTheJacksonBuildStillLoad() {
        val loaded = json.decodeFromString<PoWJobsFile>(POW_JACKSON_OUTPUT)

        assertEquals(1, loaded.jobs.size)
        val job = loaded.jobs.first()
        assertEquals("j1", job.id)
        assertEquals(21, job.difficulty)
        assertEquals("broadcast", job.replayType)
        assertEquals(listOf("r1"), job.recipientPubkeys)
        assertEquals(999L, job.createdAtSec)
        assertEquals(null, job.publishAtSec)
    }

    @Test
    fun usageWritesTheBytesJacksonWrote() {
        assertEquals(USAGE_JACKSON_OUTPUT, json.encodeToString(usageSample))
    }

    @Test
    fun usageFromTheJacksonBuildStillLoads() {
        val loaded = json.decodeFromString<ResourceUsageStore.UsageFile>(USAGE_JACKSON_OUTPUT)

        assertEquals(mapOf("relay.a" to 12L, "relay.b" to 34L), loaded.days["20260919"])
        assertEquals(555L, loaded.lastAlertAtSec)
        assertTrue(loaded.alertsOptOut)
    }

    @Test
    fun bothReadersTolerateKeysFromANewerBuild() {
        val pow = POW_JACKSON_OUTPUT.replace("""{"version":1""", """{"version":1,"futureKey":[1]""")
        val usage = USAGE_JACKSON_OUTPUT.replace("""{"version":1""", """{"version":1,"futureKey":{"a":1}""")

        assertEquals(
            "j1",
            json
                .decodeFromString<PoWJobsFile>(pow)
                .jobs
                .first()
                .id,
        )
        assertEquals(555L, json.decodeFromString<ResourceUsageStore.UsageFile>(usage).lastAlertAtSec)
    }

    companion object {
        private const val POW_JACKSON_OUTPUT =
            """{"version":1,"jobs":[{"id":"j1","accountPubkey":"pk1","kind":1,"difficulty":21,""" +
                """"templateJson":"{\"t\":1}","replayType":"broadcast","relayUrls":["wss://a"],""" +
                """"extraEventsJson":[],"publishAtSec":null,"recipientPubkeys":["r1"],""" +
                """"wrapExpirationDelta":null,"createdAtSec":999}]}"""

        private const val USAGE_JACKSON_OUTPUT =
            """{"version":1,"days":{"20260919":{"relay.a":12,"relay.b":34}},""" +
                """"lastAlertAtSec":555,"alertsOptOut":true}"""
    }
}
