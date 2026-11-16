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

import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.asset.SoftwareAssetEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SoftwareAssetDownloadsTest {
    private val hash = "1907f59c7fe728febd1d90d60f7e0b5f8d4bbfd66b9e12e6ec2c60e823be9f05"

    private fun asset(
        url: String?,
        filename: String?,
    ) = SoftwareAssetEvent(
        id = "0".repeat(64),
        pubKey = "1".repeat(64),
        createdAt = 0,
        tags =
            listOfNotNull(
                arrayOf("i", "buzz.armada.app"),
                arrayOf("m", "application/zip"),
                arrayOf("x", hash),
                url?.let { arrayOf("url", it) },
                filename?.let { arrayOf("filename", it) },
            ).toTypedArray(),
        content = "",
        sig = "",
    )

    @Test
    fun addsTheExtensionToABareBlossomHashLink() {
        val a = asset("https://blossom.ditto.pub/$hash", "Armada-v0.64.7-mac-arm64.zip")
        assertEquals("https://blossom.ditto.pub/$hash.zip", SoftwareAssetDownloads.url(a))
        assertEquals("zip", SoftwareAssetDownloads.extension(a))
    }

    @Test
    fun keepsQueryStrings() {
        val a = asset("https://cdn.example.com/$hash?download=1", "Armada.AppImage")
        assertEquals("https://cdn.example.com/$hash.AppImage?download=1", SoftwareAssetDownloads.url(a))
    }

    @Test
    fun leavesOtherUrlsAlone() {
        assertEquals("https://example.com/Armada.zip", SoftwareAssetDownloads.url(asset("https://example.com/Armada.zip", "Armada.zip")))
        assertEquals("https://blossom.ditto.pub/$hash.zip", SoftwareAssetDownloads.url(asset("https://blossom.ditto.pub/$hash.zip", "Armada.zip")))
    }

    @Test
    fun fallsBackToTheMimeTypeExtensionWithoutAFilename() {
        // The fixture's MIME type is application/zip.
        assertEquals("zip", SoftwareAssetDownloads.extension(asset(null, null)))
        assertEquals("https://blossom.ditto.pub/$hash.zip", SoftwareAssetDownloads.url(asset("https://blossom.ditto.pub/$hash", null)))
        assertEquals("apk", SoftwareAssetDownloads.extensionForMime("application/vnd.android.package-archive"))
        assertNull(SoftwareAssetDownloads.extensionForMime("application/octet-stream"))
    }

    @Test
    fun tarballsKeepTheirDoubleExtensionAndLabel() {
        val tarball =
            SoftwareAssetEvent(
                id = "0".repeat(64),
                pubKey = "1".repeat(64),
                createdAt = 0,
                tags =
                    arrayOf(
                        arrayOf("m", "application/gzip"),
                        arrayOf("x", hash),
                        arrayOf("url", "https://cdn.example.com/$hash"),
                        arrayOf("filename", "ngit-v3.0.3-x86_64-unknown-linux-musl.tar.gz"),
                        arrayOf("variant", "musl"),
                    ),
                content = "",
                sig = "",
            )
        assertEquals("tar.gz", SoftwareAssetDownloads.extension(tarball))
        // One suffix: blossom.napplet.soy 404s on `<hash>.tar.gz` but serves `<hash>.tgz`.
        assertEquals("https://cdn.example.com/$hash.tgz", SoftwareAssetDownloads.url(tarball))
        assertEquals("TAR.GZ", SoftwareAssetDownloads.formatLabel(tarball))
        assertEquals("TAR.GZ · musl", SoftwareAssetDownloads.describe(tarball, SoftwareOs.LINUX, null).title)
    }

    @Test
    fun unknownFormatsAreTitledByTheirFileName() {
        val text = SoftwareAssetDownloads.describe(asset("https://example.com/SHA256SUMS.txt", "SHA256SUMS.txt"), SoftwareOs.OTHER, null)
        // The fixture's MIME type is a known one (zip), so swap in an unknown asset.
        val sums =
            SoftwareAssetEvent(
                id = "0".repeat(64),
                pubKey = "1".repeat(64),
                createdAt = 0,
                tags = arrayOf(arrayOf("m", "text/plain"), arrayOf("x", hash), arrayOf("filename", "SHA256SUMS.txt")),
                content = "",
                sig = "",
            )
        val described = SoftwareAssetDownloads.describe(sums, SoftwareOs.OTHER, null)
        assertEquals("ZIP", text.title)
        assertEquals("SHA256SUMS.txt", described.title)
        assertEquals("TXT", described.fileKind)
    }

    @Test
    fun noUrlMeansFindItByHash() {
        assertNull(SoftwareAssetDownloads.url(asset(null, "Armada.zip")))
    }
}
