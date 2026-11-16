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

import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.asset.SoftwareAssetEvent
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.release.tags.ReleaseChannel
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip51Lists.releaseArtifactSet.ReleaseArtifactSetEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class Nip82UpdateFinderTest {
    private val publisher = NostrSignerSync()
    private val stranger = NostrSignerSync()
    private val finder = Nip82UpdateFinder(publisher = publisher.pubKey)

    private val linuxAppImage = UpdateTarget("linux-x86_64", listOf(DesktopPackage.APPIMAGE))

    private fun hashOf(seed: Char) = seed.toString().repeat(64)

    private fun asset(
        version: String,
        file: String,
        signer: NostrSignerSync = publisher,
        hash: String = hashOf('a'),
        url: String = DesktopRelease.downloadUrl(version, file),
    ): SoftwareAssetEvent {
        val classified = assertNotNull(DesktopAssetNames.classify(file, version), file)
        return signer.sign(DesktopReleaseEvents.asset(HashedDesktopAsset(classified, hash, 1234, url), version))
    }

    private fun release(
        version: String,
        assets: List<SoftwareAssetEvent>,
        signer: NostrSignerSync = publisher,
        channel: String = ReleaseChannel.MAIN,
    ): ReleaseArtifactSetEvent {
        val template = Nip82ReleaseEvents.release(DesktopRelease.APP_ID, signer.pubKey, version, "notes $version", assets, null)
        return if (channel == ReleaseChannel.MAIN) {
            signer.sign(template)
        } else {
            signer.signNormal(template.createdAt, template.kind, template.tags.map { if (it[0] == "c") arrayOf("c", channel) else it }.toTypedArray(), template.content)
        }
    }

    private fun find(
        current: String,
        target: UpdateTarget,
        vararg events: Event,
    ) = finder.find(current, target, events.filter { it.kind == ReleaseArtifactSetEvent.KIND }, events.filter { it.kind == SoftwareAssetEvent.KIND })

    @Test
    fun picksTheMatchingFileOfTheNewestRelease() {
        val old = asset("1.20.0", "amethyst-desktop-1.20.0-x86_64.AppImage")
        val newImage = asset("1.21.0", "amethyst-desktop-1.21.0-x86_64.AppImage", hash = hashOf('b'))
        val newDeb = asset("1.21.0", "amethyst-desktop-1.21.0-linux-x64.deb", hash = hashOf('c'))
        val update =
            assertNotNull(
                find("1.19.0", linuxAppImage, release("1.20.0", listOf(old)), old, release("1.21.0", listOf(newImage, newDeb)), newImage, newDeb),
            )

        assertEquals("1.21.0", update.version)
        assertEquals(hashOf('b'), update.asset?.sha256)
        assertEquals(DesktopPackage.APPIMAGE, update.asset?.packageType)
        assertEquals("notes 1.21.0", update.releaseNotes)
    }

    @Test
    fun nothingWhenAlreadyCurrentOrNewer() {
        val a = asset("1.20.0", "amethyst-desktop-1.20.0-x86_64.AppImage")
        val r = release("1.20.0", listOf(a))
        assertNull(find("1.20.0", linuxAppImage, r, a))
        assertNull(find("1.21.0", linuxAppImage, r, a))
    }

    @Test
    fun aPrereleaseUpgradesToItsStableRelease() {
        val a = asset("1.20.0", "amethyst-desktop-1.20.0-x86_64.AppImage")
        assertEquals("1.20.0", find("1.20.0-rc1", linuxAppImage, release("1.20.0", listOf(a)), a)?.version)
    }

    @Test
    fun ignoresReleasesSignedByAnyoneElse() {
        val a = asset("9.0.0", "amethyst-desktop-9.0.0-x86_64.AppImage", signer = stranger)
        assertNull(find("1.0.0", linuxAppImage, release("9.0.0", listOf(a), signer = stranger), a))
    }

    @Test
    fun ignoresAForeignAssetInsideATrustedRelease() {
        val foreign = asset("2.0.0", "amethyst-desktop-2.0.0-x86_64.AppImage", signer = stranger)
        val update = assertNotNull(find("1.0.0", linuxAppImage, release("2.0.0", listOf(foreign)), foreign))
        assertNull(update.asset, "a release still announces the version, but nothing is installed from a file it did not sign")
    }

    @Test
    fun ignoresAnAssetTheReleaseDoesNotName() {
        val named = asset("2.0.0", "amethyst-desktop-2.0.0-linux-x64.deb")
        val stray = asset("2.0.0", "amethyst-desktop-2.0.0-x86_64.AppImage")
        assertNull(find("1.0.0", linuxAppImage, release("2.0.0", listOf(named)), named, stray)?.asset)
    }

    @Test
    fun ignoresATamperedRelease() {
        val a = asset("2.0.0", "amethyst-desktop-2.0.0-x86_64.AppImage")
        val r = release("2.0.0", listOf(a))
        val forged = ReleaseArtifactSetEvent(r.id, r.pubKey, r.createdAt, r.tags, "different notes", r.sig)
        assertNull(find("1.0.0", linuxAppImage, forged, a))
    }

    @Test
    fun ignoresPrereleaseChannels() {
        val a = asset("2.0.0", "amethyst-desktop-2.0.0-x86_64.AppImage")
        assertNull(find("1.0.0", linuxAppImage, release("2.0.0", listOf(a), channel = ReleaseChannel.BETA), a))
        assertNotNull(find("1.0.0", linuxAppImage, release("2.0.0", listOf(a), channel = "stable"), a))
    }

    @Test
    fun aPrereleasePublishedByAmyIsNotOffered() {
        val a = asset("2.0.0-rc1", "amethyst-desktop-2.0.0-rc1-x86_64.AppImage")
        assertEquals(ReleaseChannel.BETA, Nip82ReleaseEvents.channelFor("2.0.0-rc1"))
        assertNull(find("1.0.0", linuxAppImage, release("2.0.0-rc1", listOf(a)), a))
    }

    @Test
    fun ignoresAReleaseWithoutThisPlatform() {
        val mac = asset("2.0.0", "amethyst-desktop-2.0.0-macos-arm64.dmg")
        assertNull(find("1.0.0", linuxAppImage, release("2.0.0", listOf(mac)), mac))
    }

    @Test
    fun rejectsPlainHttpAndBadHashes() {
        val http = asset("2.0.0", "amethyst-desktop-2.0.0-x86_64.AppImage", url = "http://example.com/a.AppImage")
        assertNull(find("1.0.0", linuxAppImage, release("2.0.0", listOf(http)), http)?.asset)

        val badHash = asset("2.0.0", "amethyst-desktop-2.0.0-x86_64.AppImage", hash = "not-a-hash")
        assertNull(find("1.0.0", linuxAppImage, release("2.0.0", listOf(badHash)), badHash)?.asset)
    }

    @Test
    fun followsThePackagePreference() {
        val msi = asset("2.0.0", "amethyst-desktop-2.0.0-windows-x64.msi", hash = hashOf('d'))
        val zip = asset("2.0.0", "amethyst-desktop-2.0.0-windows-x64.zip", hash = hashOf('e'))
        val r = release("2.0.0", listOf(msi, zip))

        assertEquals(hashOf('d'), find("1.0.0", UpdateTarget("windows-x86_64", listOf(DesktopPackage.MSI)), r, msi, zip)?.asset?.sha256)
        assertEquals(hashOf('e'), find("1.0.0", UpdateTarget("windows-x86_64", listOf(DesktopPackage.WINDOWS_ZIP)), r, msi, zip)?.asset?.sha256)
    }

    @Test
    fun aNotifyOnlyTargetGetsTheVersionButNoFile() {
        val dmg = asset("2.0.0", "amethyst-desktop-2.0.0-macos-arm64.dmg")
        val update = assertNotNull(find("1.0.0", UpdateTarget("darwin-arm64", emptyList()), release("2.0.0", listOf(dmg)), dmg))
        assertEquals("2.0.0", update.version)
        assertNull(update.asset)
    }
}
