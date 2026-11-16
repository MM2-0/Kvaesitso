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

import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AndroidReleaseEventsTest {
    private val signer = NostrSignerSync()

    private val info =
        ApkInfo(
            packageName = "com.vitorpamplona.amethyst",
            versionName = "1.19.0",
            versionCode = 464,
            minSdk = 26,
            targetSdk = 37,
            nativeAbis = listOf("arm64-v8a"),
            certificateSha256 = "c2d0aa86bcb6b62090561a41bbe336e98b78c2d0210a498dc885f28e1348cf17",
        )

    @Test
    fun readsTheApkNamesTheReleaseWorkflowWrites() {
        assertEquals(
            AndroidRelease.ApkFile("amethyst-googleplay-arm64-v8a-v1.19.0.apk", AndroidRelease.Flavor.GOOGLE_PLAY, "arm64-v8a"),
            AndroidRelease.classify("amethyst-googleplay-arm64-v8a-v1.19.0.apk", "1.19.0"),
        )
        assertEquals(AndroidRelease.Flavor.FDROID, AndroidRelease.classify("amethyst-fdroid-universal-v1.19.0.apk", "1.19.0")?.flavor)
        assertEquals("x86_64", AndroidRelease.classify("amethyst-fdroid-x86_64-v1.19.0.apk", "1.19.0")?.abi)
        assertNull(AndroidRelease.classify("amethyst-fdroid-v1.19.0.apks", "1.19.0"))
        assertNull(AndroidRelease.classify("amethyst-googleplay-arm64-v8a-v1.18.0.apk", "1.19.0"))
        assertNull(AndroidRelease.classify("amethyst-desktop-1.19.0-linux-x64.deb", "1.19.0"))
    }

    /** Tag for tag the asset zsp published for v1.19.0 on relay.zapstore.dev (minus its cache-prefixed filename). */
    @Test
    fun theAssetCarriesWhatZspPublishes() {
        val file = AndroidRelease.classify("amethyst-googleplay-arm64-v8a-v1.19.0.apk", "1.19.0")!!
        val github = "https://github.com/vitorpamplona/amethyst/releases/download/v1.19.0/amethyst-googleplay-arm64-v8a-v1.19.0.apk"
        val sha = "158457da5adaad9f915bc2f5f933d12d17a26d325d70e8086ea4bf1d80bb4f2e"
        val apk = HashedApk(file, info, sha, 63380657, listOf(github, "https://cdn.zapstore.dev/$sha"))
        val event = signer.sign(AndroidReleaseEvents.asset(apk, "1.19.0", listOf("01", "5A")))

        val tags = event.tags.map { it.toList() }
        assertEquals(
            listOf(
                listOf("i", "com.vitorpamplona.amethyst"),
                listOf("m", "application/vnd.android.package-archive"),
                listOf("x", sha),
                listOf("version", "1.19.0"),
                listOf("size", "63380657"),
                listOf("url", github),
                listOf("url", "https://cdn.zapstore.dev/$sha"),
                listOf("f", "android-arm64-v8a"),
                listOf("min_platform_version", "26"),
                listOf("target_platform_version", "37"),
                listOf("filename", "amethyst-googleplay-arm64-v8a-v1.19.0.apk"),
                listOf("variant", "google"),
                listOf("supported_nip", "01"),
                listOf("supported_nip", "5A"),
                listOf("version_code", "464"),
                listOf("apk_certificate_hash", "c2d0aa86bcb6b62090561a41bbe336e98b78c2d0210a498dc885f28e1348cf17"),
            ).sortedBy { it.toString() },
            tags.sortedBy { it.toString() },
        )
    }

    @Test
    fun theReleaseNamesItsAssetsUnderThePackageName() {
        val file = AndroidRelease.classify("amethyst-googleplay-arm64-v8a-v1.19.0.apk", "1.19.0")!!
        val asset = signer.sign(AndroidReleaseEvents.asset(HashedApk(file, info, "a".repeat(64), 1, listOf("https://x")), "1.19.0", emptyList()))
        val release = signer.sign(Nip82ReleaseEvents.release(AndroidRelease.APP_ID, signer.pubKey, "1.19.0", "notes", listOf(asset), null))
        assertEquals("com.vitorpamplona.amethyst@1.19.0", release.dTag())
        assertEquals("com.vitorpamplona.amethyst", release.appId())
        assertEquals("main", release.channel())
        assertEquals(listOf(asset.id), release.assets().map { it.eventId })
        assertEquals(listOf("android-arm64-v8a"), release.platforms())
    }

    @Test
    fun theListingComesFromZapstoreYaml() {
        val config =
            ZapstoreConfig.fromMap(
                mapOf(
                    "name" to "Amethyst",
                    "summary" to "The all-in-one Nostr client",
                    "description" to "A privacy-focused Nostr client.\n",
                    "tags" to listOf("social-network", "nostr"),
                    "license" to "MIT",
                    "website" to "https://amethyst.social/",
                    "repository" to "https://github.com/vitorpamplona/amethyst",
                    "supported_nips" to listOf(1, "5A", "02"),
                ),
            )
        assertEquals(listOf("01", "5A", "02"), config.supportedNips)

        val listing = signer.sign(AndroidReleaseEvents.listing(config, "https://cdn/icon", listOf("https://cdn/a", "https://cdn/b"), listOf("android-arm64-v8a")))
        assertEquals("com.vitorpamplona.amethyst", listing.dTag())
        assertEquals("A privacy-focused Nostr client.\n", listing.content, "as zsp: the YAML block scalar, newline included")
        assertEquals("https://cdn/icon", listing.icon())
        assertEquals(listOf("https://cdn/a", "https://cdn/b"), listing.images())
        assertEquals(listOf("social-network", "nostr"), listing.topics())
        assertEquals(listOf("android-arm64-v8a"), listing.platforms())
        assertEquals(listOf(listOf("h", AndroidRelease.ZAPSTORE_COMMUNITY)), listing.tags.filter { it[0] == "h" }.map { it.toList() })
    }
}
