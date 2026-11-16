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
package com.vitorpamplona.amethyst.commons.ui.note.types

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.softwareapps.SoftwareAssetDownloads
import com.vitorpamplona.amethyst.commons.softwareapps.SoftwareOs
import com.vitorpamplona.amethyst.commons.softwareapps.SoftwarePlatforms
import com.vitorpamplona.amethyst.commons.softwareapps.SoftwareReleases
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonRow
import com.vitorpamplona.amethyst.commons.ui.theme.subtleBorder
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.application.SoftwareApplicationEvent
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.asset.SoftwareAssetEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip51Lists.releaseArtifactSet.ReleaseArtifactSetEvent
import io.mockk.mockk
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Renders real NIP-82 releases (fetched with amy from relay.zapstore.dev, relay.ngit.dev and
 * others; `resources/nip82/releases.jsonl`) offscreen through the shared release composables, the
 * header, the OS chips and the grouped downloads, into `commonsUI/build/nip82-release/`. The release
 * notes are left out: they go through the rich-text viewer, which needs an account.
 *
 * The device is a 64-bit ARM Android phone.
 */
class SoftwareReleaseRenderTest {
    private val outDir = File("build/nip82-release").apply { mkdirs() }
    private val phone = listOf("android-arm64-v8a", "android-armeabi-v7a")

    private val events =
        javaClass
            .getResource("/nip82/releases.jsonl")!!
            .readText()
            .lines()
            .filter { it.isNotBlank() }
            .map { Event.fromJson(it) }
    private val releases = events.filterIsInstance<ReleaseArtifactSetEvent>()
    private val assets = events.filterIsInstance<SoftwareAssetEvent>().associateBy { it.id }
    private val apps = events.filterIsInstance<SoftwareApplicationEvent>().associateBy { it.addressTag() }

    private fun release(appId: String) = releases.first { it.appId() == appId }

    private fun appOf(release: ReleaseArtifactSetEvent) = release.appAddress()?.toValue()?.let { apps[it] }

    private fun assetsOf(release: ReleaseArtifactSetEvent) = release.assets().mapNotNull { assets[it.eventId] }

    @Test
    fun armadaGroupsEveryOsAndPicksTheApkForThePhone() {
        val armada = release("buzz.armada.app")
        val groups = SoftwareAssetDownloads.group(assetsOf(armada), phone)

        // The APK is the phone's pick, shown on top, so the Android group it would be alone in is dropped.
        assertEquals("application/vnd.android.package-archive", groups.forThisDevice?.mimeType())
        assertEquals(listOf(SoftwareOs.MACOS, SoftwareOs.WINDOWS, SoftwareOs.LINUX), groups.byOs.map { it.first })
        val titles = groups.byOs.flatMap { (os, list) -> list.map { SoftwareAssetDownloads.describe(it, os, armada.version()).title } }
        assertEquals(listOf("ZIP", "ZIP", "EXE · installer", "EXE · portable", "AppImage", "DEB", "Flatpak"), titles)
        assertTrue(SoftwareReleases.canShow(armada, appOf(armada)))
    }

    @Test
    fun checksumsAndReadmesAreTitledByFileName() {
        val provider = release("xyz.privateprovider")
        val titles = assetsOf(provider).map { asset -> SoftwareAssetDownloads.describe(asset, SoftwarePlatforms.osesOf(asset).first(), provider.version()).title }
        assertTrue("APK" in titles)
        assertTrue(titles.none { it.startsWith("application/") || it.startsWith("text/") }, "raw MIME types as titles: $titles")
        assertEquals(titles.size, titles.toSet().size, "two rows read the same: $titles")
    }

    @Test
    fun truncatedFileNamesKeepTheirTypeInTheDetails() {
        val archipelago = release("archipelago")
        val kinds =
            assetsOf(archipelago)
                .map { SoftwareAssetDownloads.describe(it, SoftwarePlatforms.osesOf(it).first(), archipelago.version()) }
                .filter { it.titleIsFileName }
                .map { it.fileKind }
        // archipelago (no extension), then the .iso, .iso.sha256 and .iso.sha256.json siblings.
        assertEquals(listOf(null, "ISO", "SHA256", "JSON"), kinds)
    }

    @Test
    fun aReleaseWithoutFTagsBorrowsTheAppsPlatforms() {
        val pyblockwatch = release("com.kilombino.pyblockwatch")
        assertTrue(pyblockwatch.platforms().isEmpty())
        assertEquals(listOf(SoftwareOs.ANDROID), SoftwareReleases.oses(pyblockwatch, appOf(pyblockwatch)))
    }

    @Test
    fun ngitTarballsAreLabelledTarGz() {
        val ngit = release("ngit")
        val titles = assetsOf(ngit).map { SoftwareAssetDownloads.formatLabel(it) }
        assertTrue(titles.all { it == "TAR.GZ" || it == "ZIP" }, "$titles")
    }

    /** Grace Launcher (kind 32267) as Zapstore published it: a name, an icon, and nothing to describe it. */
    private val graceLauncher =
        Event.fromJson(
            """{"id":"64a6c4b2f36c83841be158df382769c7cbebdcd9d42e495294cf039d8f945508","pubkey":"78ce6faa72264387284e647ba6938995735ec8c7d5c5a65737e55130f026307d","created_at":1791290975,"kind":32267,"tags":[["d","com.galaxyrio.gracelauncher"],["name","Grace Launcher"],["icon","https://cdn.zapstore.dev/fd81778b32119e7f4469a2009c1365ce3a00dfab06e2b8dbb6f13c2554cc2b0c"],["repository","https://github.com/Galaxy-rio/GraceLauncher"],["f","android-x86_64"],["f","android-arm64-v8a"],["f","android-armeabi-v7a"],["f","android-x86"],["h","acfeaea6e51420e8068fac446ca9d17d7a9ef6a5d20d93894e50fee3d4902a84"]],"content":"","sig":"cb24823cb32a8c5e3e18c74913f318b12d632c417b25a384ca34bc7efcfc25908b8ee92a1b90b0be003629279ab750993ba593f2a788b388212da2882706a04b"}""",
        ) as SoftwareApplicationEvent

    /** More apps Zapstore published with only a name and an icon. */
    private val otherBareApps =
        listOf(
            """{"id":"4db4b27e0ed3a78cb0cffd1a9551ccc2f4d969ed14ed70a0546f0b2f4c8aff3e","pubkey":"78ce6faa72264387284e647ba6938995735ec8c7d5c5a65737e55130f026307d","created_at":1791376878,"kind":32267,"tags":[["d","app.cleartray"],["name","Trayzero"],["icon","https://cdn.zapstore.dev/fd805e4b334d8c848f07367ce8f256221961be337b803dc7fba5ff9b4c20dcbc.png"],["f","android-arm64-v8a"]],"content":"","sig":"9f9e62568075fb338dd7f40163a4cec7630e4002cd25051852607894e35baf0c0995c67b504c7292bc99ce377634aef221f4c150a688977364b823e0922ef53b"}""",
            """{"id":"adde043f8adfe3ea8c9ca382ba41be90d287ebeda5a450ec9d214f8644b5845c","pubkey":"78ce6faa72264387284e647ba6938995735ec8c7d5c5a65737e55130f026307d","created_at":1791376881,"kind":32267,"tags":[["d","app.comaps"],["name","CoMaps"],["icon","https://cdn.zapstore.dev/f32e824aa7d7926bf7eebe6bd70f7f5a4db46b9a8ee330a78d4fce0955731149.png"],["repository","https://codeberg.org/comaps/comaps"],["f","android-arm64-v8a"],["f","android-armeabi-v7a"]],"content":"","sig":"2a211ed10de8802342df868578ea1a499e9f77d5816209b9ec2e4fd57b1e4fced1f81e430e55433f0e68416cf29508e2fd79df3c5e051597fb846353fbfcbdfb"}""",
            """{"id":"45ba4908b61282a1c0eca5fe506188c170d3214c22c95b5525265a59e73d08b7","pubkey":"78ce6faa72264387284e647ba6938995735ec8c7d5c5a65737e55130f026307d","created_at":1791377040,"kind":32267,"tags":[["d","app.comaps.fdroid"],["name","CoMaps"],["icon","https://cdn.zapstore.dev/f32e824aa7d7926bf7eebe6bd70f7f5a4db46b9a8ee330a78d4fce0955731149.png"],["repository","https://codeberg.org/comaps/comaps"],["f","android-arm64-v8a"],["f","android-armeabi-v7a"],["f","android-x86_64"]],"content":"","sig":"cf7d31e5e051edc2cf5cff6ca9481e49523ef0e6565a5884575be5d41a5861d322cbc20ee886ece01177fd207201e1daeb5adc6d5dc4a2f48657c57a25757a60"}""",
        ).map { Event.fromJson(it) as SoftwareApplicationEvent }

    @Test
    fun aBareAppCardShowsItsPlatformsLinkAndId() {
        assertEquals("github.com/Galaxy-rio/GraceLauncher", displayLink(graceLauncher.repository()!!))
        assertEquals(listOf(SoftwareOs.ANDROID), SoftwarePlatforms.osesOfPlatforms(graceLauncher.platforms()))

        // The card's header (icon + name) plus the details it now falls back to; the author line,
        // options menu and reactions need an account and are left out.
        // The two CoMaps listings differ only by their ids, which the card now shows.
        (listOf(graceLauncher) + otherBareApps).forEach { app ->
            renderToPng("bare-app-${app.appId()}", heightDp = 170) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(icon = app.icon(), name = app.name()!!)
                    Spacer(Modifier.width(12.dp))
                    Text(app.name()!!, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                AppCardDetails(app)
            }
        }
    }

    @Test
    fun render() {
        val nav = mockk<INav>(relaxed = true)
        releases.forEach { release ->
            val app = appOf(release)
            val shownApp = app.takeIf { SoftwareReleases.canShow(release, it) }
            val groups = SoftwareAssetDownloads.group(assetsOf(release), phone)
            val rows = groups.byOs.sumOf { it.second.size } + if (groups.forThisDevice != null) 1 else 0

            val density = 2f
            val widthDp = 840
            val heightDp = 220 + rows * 58 + groups.byOs.size * 30
            val scene =
                ImageComposeScene(
                    width = (widthDp * density).toInt(),
                    height = (heightDp * density).toInt(),
                    density = Density(density),
                ) {
                    ThemeComparisonRow {
                        Column(
                            Modifier
                                .padding(12.dp)
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.subtleBorder, QuoteBorder)
                                .padding(12.dp),
                        ) {
                            ReleaseAppHeader(release, shownApp, release.appAddress().takeIf { shownApp != null }, nav)
                            ReleaseSummaryFooter(release, shownApp)
                            Spacer(Modifier.height(12.dp))
                            Text("— release notes (rich text, not rendered here) —", style = MaterialTheme.typography.labelSmall)
                            Spacer(Modifier.height(12.dp))
                            DownloadsSection(groups, release.version()) { DownloadAction(onClick = {}) }
                        }
                    }
                }
            try {
                var image = scene.render(0)
                repeat(SETTLE_FRAMES) { frame ->
                    Thread.sleep(FRAME_MILLIS)
                    image = scene.render((frame + 1) * FRAME_MILLIS * 1_000_000L)
                }
                val png = image.encodeToData(EncodedImageFormat.PNG) ?: error("could not encode")
                File(outDir, "${release.appId()}.png").writeBytes(png.bytes)

                val pixels = image.peekPixels() ?: error("no pixels")
                val distinct = HashSet<Int>()
                for (y in 0 until image.height step 7) for (x in 0 until image.width step 7) distinct += pixels.getColor(x, y)
                assertTrue(distinct.size > 20, "${release.appId()} rendered almost nothing (${distinct.size} colours)")
            } finally {
                scene.close()
            }
        }
    }

    private companion object {
        const val SETTLE_FRAMES = 6
        const val FRAME_MILLIS = 60L
    }

    private fun renderToPng(
        name: String,
        heightDp: Int,
        content: @Composable () -> Unit,
    ) {
        val density = 2f
        val scene =
            ImageComposeScene(width = (840 * density).toInt(), height = (heightDp * density).toInt(), density = Density(density)) {
                ThemeComparisonRow {
                    Column(
                        Modifier
                            .padding(12.dp)
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.subtleBorder, QuoteBorder)
                            .padding(12.dp),
                    ) { content() }
                }
            }
        try {
            var image = scene.render(0)
            repeat(SETTLE_FRAMES) { frame ->
                Thread.sleep(FRAME_MILLIS)
                image = scene.render((frame + 1) * FRAME_MILLIS * 1_000_000L)
            }
            File(outDir, "$name.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        } finally {
            scene.close()
        }
    }
}
