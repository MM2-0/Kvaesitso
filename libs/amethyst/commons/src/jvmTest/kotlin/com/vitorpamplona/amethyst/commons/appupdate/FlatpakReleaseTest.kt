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
package com.vitorpamplona.amethyst.commons.appupdate

import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Orchard's side of `amy releases publish`. The fixture is the first 4 KiB of the 1.19.0
 * `amethyst-desktop-1.19.0-linux-x64.flatpak` release asset: the header Orchard reads the ref and
 * the metadata from.
 */
class FlatpakReleaseTest {
    private val header = requireNotNull(javaClass.getResource("/appupdate/flatpak-header-1.19.0-x86_64.bin")).readBytes()
    private val dir = Files.createTempDirectory("flatpak-bundles").toFile()
    private val signer = NostrSignerInternal(KeyPair())

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun readsTheRefOfARealBundle() {
        assertEquals("app/com.vitorpamplona.amethyst.Amethyst/x86_64/master", FlatpakRelease.bundleRef(header))
        assertNull(FlatpakRelease.bundleRef("not a bundle at all".encodeToByteArray()))
        assertNull(FlatpakRelease.bundleRef(ByteArray(0)))
    }

    @Test
    fun readsBundleNames() {
        assertEquals("linux-x86_64", FlatpakRelease.platformOfBundle("amethyst-desktop-1.20.0-linux-x64.flatpak", "1.20.0"))
        assertEquals("linux-aarch64", FlatpakRelease.platformOfBundle("amethyst-desktop-1.20.0-linux-arm64.flatpak", "1.20.0"))
        assertNull(FlatpakRelease.platformOfBundle("amethyst-desktop-1.20.0-linux-x64.deb", "1.20.0"))
        assertNull(FlatpakRelease.platformOfBundle("amethyst-desktop-1.19.0-linux-x64.flatpak", "1.20.0"))
    }

    @Test
    fun hashesTheBundlesAndSkipsTheRest() {
        File(dir, "amethyst-desktop-1.19.0-linux-x64.flatpak").writeBytes(header)
        File(dir, "amethyst-desktop-1.19.0-linux-x64.deb").writeText("deb")
        File(dir, "amethyst-desktop-1.19.0-x86_64.AppImage").writeText("appimage")

        val bundle = ReleaseBundles.flatpakBundles(dir, "1.19.0", null).single()
        assertEquals("linux-x86_64", bundle.platform)
        assertEquals(sha256(header), bundle.sha256)
        assertEquals(header.size.toLong(), bundle.sizeInBytes)
        assertEquals("https://github.com/vitorpamplona/amethyst/releases/download/v1.19.0/amethyst-desktop-1.19.0-linux-x64.flatpak", bundle.url)
    }

    /** A bundle named for one arch but built for another, or not a bundle, stops the run. */
    @Test
    fun refusesAMislabelledBundle() {
        File(dir, "amethyst-desktop-1.19.0-linux-arm64.flatpak").writeBytes(header)
        val e = assertFailsWith<ReleaseFilesException> { ReleaseBundles.flatpakBundles(dir, "1.19.0", null) }
        assertTrue(e.message!!.contains("x86_64/master"), e.message)

        File(dir, "amethyst-desktop-1.19.0-linux-arm64.flatpak").writeText("<html>Not Found</html>")
        assertFailsWith<ReleaseFilesException> { ReleaseBundles.flatpakBundles(dir, "1.19.0", null) }
    }

    /**
     * The shape Orchard 0.2.0 installs a bundle from (its `zapstore_url_assets_project_as_installable`
     * test): an asset with `i`, `x`, `version`, `f`, `m` = application/vnd.flatpak, `filename` and an
     * https `url`, named by a release whose `d` is `<id>@<version>`, with a matching `i`, on `main`.
     */
    @Test
    fun theEventsAreWhatOrchardInstalls() =
        runTest {
            File(dir, "amethyst-desktop-1.20.0-linux-x64.flatpak").writeBytes(header)
            File(dir, "amethyst-desktop-1.20.0-linux-arm64.flatpak").writeBytes(String(header, Charsets.ISO_8859_1).replace("/x86_64/", "/aarch64/").toByteArray(Charsets.ISO_8859_1))
            val bundles = ReleaseBundles.flatpakBundles(dir, "1.20.0", null)
            val signed = ReleaseBundles.signFlatpak(signer, "1.20.0", "notes", bundles, FlatpakRelease.RELAYS, withListing = true)

            val byPlatform = signed.assets.associateBy { asset -> asset.tags.first { it[0] == "f" }[1] }
            assertEquals(setOf("linux-x86_64", "linux-aarch64"), byPlatform.keys)
            val x64 = byPlatform.getValue("linux-x86_64")
            val tags = x64.tags.associate { it[0] to it[1] }
            assertEquals(FlatpakRelease.APP_ID, tags["i"])
            assertEquals(bundles.first { it.platform == "linux-x86_64" }.sha256, tags["x"])
            assertEquals("1.20.0", tags["version"])
            assertEquals("application/vnd.flatpak", tags["m"])
            assertEquals("amethyst-desktop-1.20.0-linux-x64.flatpak", tags["filename"])
            assertEquals("https://github.com/vitorpamplona/amethyst/releases/download/v1.20.0/amethyst-desktop-1.20.0-linux-x64.flatpak", tags["url"])

            val release = signed.release.tags.groupBy({ it[0] }, { it[1] })
            assertEquals(listOf("${FlatpakRelease.APP_ID}@1.20.0"), release["d"])
            assertEquals(listOf(FlatpakRelease.APP_ID), release["i"])
            assertEquals(listOf("main"), release["c"])
            assertEquals(signed.assets.map { it.id }.toSet(), release["e"]!!.toSet())
            assertEquals("notes", signed.release.content)
            assertEquals(signed.assets + signed.release + signed.listing!!, signed.events)
        }

    /** Orchard 0.1.x and later read `stable` or `main`; a prerelease goes out on `beta`, which it skips. */
    @Test
    fun aPrereleaseIsOnBeta() =
        runTest {
            File(dir, "amethyst-desktop-1.20.0-rc1-linux-x64.flatpak").writeBytes(header)
            val bundles = ReleaseBundles.flatpakBundles(dir, "1.20.0-rc1", null)
            val signed = ReleaseBundles.signFlatpak(signer, "1.20.0-rc1", "", bundles, FlatpakRelease.RELAYS, withListing = false)
            assertEquals("beta", signed.release.tags.first { it[0] == "c" }[1])
        }

    /** Tag for tag, in order, the listing orchard-publish signed for 1.19.0 on relay.orchard.rocks. */
    @Test
    fun theListingIsTheLiveOne() {
        val listing = FlatpakReleaseEvents.listing()
        assertTrue(listing.content.startsWith("Amethyst is a client for Nostr, an open protocol"))
        assertTrue(listing.content.endsWith("or with a public key in read-only mode."))
        assertEquals(
            listOf(
                listOf("d", "com.vitorpamplona.amethyst.Amethyst"),
                listOf("name", "Amethyst"),
                listOf("summary", "Join the social network you control"),
                listOf("icon", "https://raw.githubusercontent.com/vitorpamplona/amethyst/main/desktopApp/packaging/flatpak/icons/512/com.vitorpamplona.amethyst.Amethyst.png"),
                listOf("license", "MIT AND LGPL-2.1-or-later AND BSD-2-Clause AND Apache-2.0 AND GPL-2.0-only WITH Classpath-exception-2.0"),
                listOf("repository", "https://github.com/vitorpamplona/amethyst"),
                listOf("h", "https://amethyst.social"),
                listOf("t", "Network"),
                listOf("t", "InstantMessaging"),
                listOf("f", "linux-x86_64"),
                listOf("f", "linux-aarch64"),
            ),
            listing.tags.map { it.toList() },
        )
    }
}
