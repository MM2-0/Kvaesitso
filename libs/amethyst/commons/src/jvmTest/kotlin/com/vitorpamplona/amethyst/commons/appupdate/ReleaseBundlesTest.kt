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

import com.sun.net.httpserver.HttpServer
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.test.runTest
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ReleaseBundlesTest {
    private val dir = Files.createTempDirectory("release-bundles").toFile()
    private val signer = NostrSignerInternal(KeyPair())
    private val fixture = File(requireNotNull(javaClass.getResource("/appupdate/fixture-signed.apk")).toURI())

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun apk(name: String) = fixture.copyTo(File(dir, name))

    @Test
    fun findsTheApksOfOneFlavorAndAbi() {
        apk("amethyst-googleplay-arm64-v8a-v1.19.0.apk")
        apk("amethyst-fdroid-arm64-v8a-v1.19.0.apk")
        apk("amethyst-googleplay-x86-v1.19.0.apk")

        val found = ReleaseBundles.androidApks(dir, "1.19.0", AndroidRelease.Flavor.GOOGLE_PLAY, listOf("arm64-v8a"), null)
        assertEquals(listOf("amethyst-googleplay-arm64-v8a-v1.19.0.apk"), found.map { it.second.file.filename })
        val apk = found.single().second
        assertEquals(464L, apk.info.versionCode)
        assertEquals(listOf("https://github.com/vitorpamplona/amethyst/releases/download/v1.19.0/amethyst-googleplay-arm64-v8a-v1.19.0.apk"), apk.urls)
    }

    @Test
    fun refusesAnApkOfAnotherVersion() {
        // The fixture's manifest says 1.19.0: an APK named for 1.20.0 is the wrong file.
        apk("amethyst-googleplay-arm64-v8a-v1.20.0.apk")
        val e = assertFailsWith<ReleaseFilesException> { ReleaseBundles.androidApks(dir, "1.20.0", AndroidRelease.Flavor.GOOGLE_PLAY, listOf("arm64-v8a"), null) }
        assertTrue(e.message!!.contains("version 1.19.0"))
    }

    @Test
    fun theArm64AssetIsTheNewest() =
        runTest {
            apk("amethyst-googleplay-arm64-v8a-v1.19.0.apk")
            apk("amethyst-googleplay-x86-v1.19.0.apk")
            val apks = ReleaseBundles.androidApks(dir, "1.19.0", AndroidRelease.Flavor.GOOGLE_PLAY, listOf("arm64-v8a", "x86"), null).map { it.second }

            val signed = ReleaseBundles.signAndroid(signer, "1.19.0", "", apks, listOf("01"), AndroidRelease.RELAYS, null, ZapstoreConfig())
            val newest = signed.assets.maxBy { it.createdAt }
            assertEquals("amethyst-googleplay-arm64-v8a-v1.19.0.apk", newest.filename())
            assertEquals(
                signed.assets.map { it.id }.toSet(),
                signed.release
                    .assets()
                    .map { it.eventId }
                    .toSet(),
            )
        }

    @Test
    fun mirrorsToBlossomAndChecksTheHash() =
        runTest {
            val file = File(dir, "blob.bin").apply { writeBytes(ByteArray(1000) { it.toByte() }) }
            val sha256 = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
            var authorization: String? = null
            var contentDigest: String? = null
            var xSha256: String? = null
            var returnedHash = sha256
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            server.createContext("/upload") { exchange ->
                authorization = exchange.requestHeaders.getFirst("Authorization")
                contentDigest = exchange.requestHeaders.getFirst("Content-Digest")
                xSha256 = exchange.requestHeaders.getFirst("X-SHA-256")
                exchange.requestBody.readBytes()
                val body = """{"url":"http://127.0.0.1:${server.address.port}/$returnedHash","sha256":"$returnedHash","size":1000,"uploaded":1}""".toByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            server.start()
            try {
                val base = "http://127.0.0.1:${server.address.port}"
                assertEquals("$base/$sha256", ReleaseBundles.mirrorToBlossom(file, "application/octet-stream", base, signer))
                assertTrue(authorization!!.startsWith("Nostr "), "BUD-02 upload auth")
                assertEquals(sha256, contentDigest, "cdn.zapstore.dev refuses an upload without Content-Digest")
                assertEquals(sha256, xSha256)

                returnedHash = "0".repeat(64)
                assertFailsWith<ReleaseFilesException> { ReleaseBundles.mirrorToBlossom(file, "application/octet-stream", base, signer) }

                assertEquals("https://cdn.example/$sha256", ReleaseBundles.mirrorToBlossom(file, "application/octet-stream", "https://cdn.example/", signer, dryRun = true))
            } finally {
                server.stop(0)
            }
        }

    @Test
    fun listingImagesKeepUrlsAndNeedAServerForFiles() =
        runTest {
            File(dir, "icon.png").writeBytes(byteArrayOf(1, 2, 3))
            val config = ZapstoreConfig(icon = "./icon.png", images = listOf("https://example.com/shot.png"))
            assertFailsWith<ReleaseFilesException> { ReleaseBundles.listingMedia(config, dir, null, signer) }

            val media = ReleaseBundles.listingMedia(config, dir, "https://cdn.example", signer, dryRun = true)
            assertTrue(media.iconUrl!!.startsWith("https://cdn.example/"))
            assertEquals(listOf("https://example.com/shot.png"), media.imageUrls)
        }
}
