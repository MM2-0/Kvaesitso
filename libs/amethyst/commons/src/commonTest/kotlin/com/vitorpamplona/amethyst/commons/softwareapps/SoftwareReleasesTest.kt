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
package com.vitorpamplona.amethyst.commons.softwareapps

import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.application.SoftwareApplicationEvent
import com.vitorpamplona.quartz.nip51Lists.releaseArtifactSet.ReleaseArtifactSetEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SoftwareReleasesTest {
    private val publisher = "1".repeat(64)
    private val maintainer = "2".repeat(64)
    private val stranger = "3".repeat(64)

    private val app =
        SoftwareApplicationEvent(
            id = "a".repeat(64),
            pubKey = publisher,
            createdAt = 0,
            tags = arrayOf(arrayOf("d", "com.example.app"), arrayOf("name", "Example"), arrayOf("p", maintainer)),
            content = "",
            sig = "",
        )

    private fun release(
        signer: String,
        version: String,
        channel: String = "main",
        createdAt: Long = 0,
        withATag: Boolean = true,
    ) = ReleaseArtifactSetEvent(
        id = (signer.take(1) + version + channel + createdAt).padEnd(64, '0').take(64),
        pubKey = signer,
        createdAt = createdAt,
        tags =
            listOfNotNull(
                if (withATag) arrayOf("a", "32267:$publisher:com.example.app") else null,
                arrayOf("i", "com.example.app"),
                arrayOf("version", version),
                arrayOf("d", "com.example.app@$version"),
                arrayOf("c", channel),
            ).toTypedArray(),
        content = "",
        sig = "",
    )

    @Test
    fun publisherAndCreditedMaintainersAreTrusted() {
        assertEquals(listOf(publisher, maintainer), SoftwareReleases.trustedSigners(app))
        assertTrue(SoftwareReleases.isReleaseOf(release(publisher, "1.0"), app))
        assertTrue(SoftwareReleases.isReleaseOf(release(maintainer, "1.0"), app))
    }

    @Test
    fun strangersPointingAtTheAppAreNotItsReleases() {
        assertFalse(SoftwareReleases.isReleaseOf(release(stranger, "99.0"), app))
    }

    @Test
    fun releasesWithoutATagFallBackToThePublisher() {
        assertTrue(SoftwareReleases.isReleaseOf(release(publisher, "1.0", withATag = false), app))
        // Without `a` the release names the signer's own app, which is not this one.
        assertFalse(SoftwareReleases.isReleaseOf(release(maintainer, "1.0", withATag = false), app))
    }

    @Test
    fun withoutTheAppOnlySelfPublishedReleasesShow() {
        val own = release(publisher, "1.0", withATag = false)
        assertTrue(SoftwareReleases.isSelfPublished(own))
        assertTrue(SoftwareReleases.canShow(own, null))

        // Points at someone else's app: unverifiable until that app loads.
        val foreign = release(stranger, "99.0")
        assertFalse(SoftwareReleases.isSelfPublished(foreign))
        assertFalse(SoftwareReleases.canShow(foreign, null))
        assertFalse(SoftwareReleases.canShow(foreign, app))
    }

    @Test
    fun releasesOfAnotherAppDoNotMatch() {
        val other =
            ReleaseArtifactSetEvent(
                id = "b".repeat(64),
                pubKey = publisher,
                createdAt = 0,
                tags =
                    arrayOf(
                        arrayOf("a", "32267:$publisher:com.example.other"),
                        arrayOf("i", "com.example.app"),
                        arrayOf("version", "1.0"),
                    ),
                content = "",
                sig = "",
            )
        assertFalse(SoftwareReleases.isReleaseOf(other, app))
    }

    @Test
    fun latestIsTheHighestMainVersionNotTheNewestEvent() {
        val v1 = release(publisher, "1.9.0", createdAt = 100)
        val v2 = release(publisher, "1.10.0", createdAt = 50)
        val backport = release(publisher, "1.9.1", createdAt = 200)
        val nightly = release(publisher, "2.0.0-dev", channel = "nightly", createdAt = 300)

        assertEquals(v2, SoftwareReleases.latest(listOf(v1, v2, backport, nightly)))
        assertEquals(listOf(nightly, v2, backport, v1), SoftwareReleases.sorted(listOf(v1, v2, backport, nightly)))
    }

    @Test
    fun arrangeSplitsNewerPreReleasesFromOlderReleases() {
        val v1 = release(publisher, "1.0.0")
        val v11 = release(publisher, "1.1.0")
        val beta = release(publisher, "1.2.0-beta", channel = "beta")
        val oldBeta = release(publisher, "1.1.0-beta", channel = "beta")

        val arranged = SoftwareReleases.arrange(SoftwareReleases.sorted(listOf(v1, v11, beta, oldBeta)))
        assertEquals(v11, arranged.latest)
        assertEquals(listOf(beta), arranged.preReleases)
        assertEquals(listOf(oldBeta, v1), arranged.older)

        val empty = SoftwareReleases.arrange(emptyList())
        assertEquals(null, empty.latest)
        assertTrue(empty.preReleases.isEmpty() && empty.older.isEmpty())
    }

    @Test
    fun latestFallsBackToOtherChannelsWithoutAMainRelease() {
        val beta = release(publisher, "1.0-beta", channel = "beta")
        val nightly = release(publisher, "1.1-dev", channel = "nightly")
        assertEquals(nightly, SoftwareReleases.latest(listOf(beta, nightly)))
    }
}
