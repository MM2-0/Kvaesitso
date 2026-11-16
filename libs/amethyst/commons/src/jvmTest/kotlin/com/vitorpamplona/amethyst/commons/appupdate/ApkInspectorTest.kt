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

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * `fixture-signed.apk` is a 25 KB APK built with aapt2 and signed with apksigner (v2 + v3) by a
 * throwaway EC key: package com.vitorpamplona.amethyst, version 1.19.0 (464), SDK 26 to 37, one
 * `lib/arm64-v8a/` entry. `fixture-unsigned.apk` is the same before signing.
 */
class ApkInspectorTest {
    private fun fixture(name: String) = File(requireNotNull(javaClass.getResource("/appupdate/$name")) { name }.toURI())

    @Test
    fun readsTheManifestAndTheSigner() {
        val info = ApkInspector.inspect(fixture("fixture-signed.apk"))
        assertEquals("com.vitorpamplona.amethyst", info.packageName)
        assertEquals("1.19.0", info.versionName)
        assertEquals(464L, info.versionCode)
        assertEquals(26, info.minSdk)
        assertEquals(37, info.targetSdk)
        assertEquals(listOf("arm64-v8a"), info.nativeAbis)
        assertEquals(listOf("android-arm64-v8a"), info.platforms)
        // apksigner verify --print-certs: "certificate SHA-256 digest"
        assertEquals("8d1cefa22c2246ae3ef1fe600546626d166a7a32ef625d21aea6f96cb35a629f", info.certificateSha256)
    }

    @Test
    fun refusesAnUnsignedApk() {
        assertFailsWith<ApkInspector.InvalidApkException> { ApkInspector.inspect(fixture("fixture-unsigned.apk")) }
    }

    /**
     * Against a real release APK, when one is given (`AMETHYST_REAL_APK=/path/to.apk`): the values must be
     * the ones zsp published for it. For amethyst-googleplay-arm64-v8a-v1.19.0.apk, Zapstore's
     * asset event says version_code 464, SDK 26/37, android-arm64-v8a and this certificate.
     */
    @Test
    fun matchesWhatZspPublishedForARealApk() {
        val path = System.getenv("AMETHYST_REAL_APK") ?: return
        println("Checking $path against zsp's published asset event")
        val info = ApkInspector.inspect(File(path))
        assertEquals("com.vitorpamplona.amethyst", info.packageName)
        assertEquals("1.19.0", info.versionName)
        assertEquals(464L, info.versionCode)
        assertEquals(26, info.minSdk)
        assertEquals(37, info.targetSdk)
        assertEquals(listOf("android-arm64-v8a"), info.platforms)
        assertEquals("c2d0aa86bcb6b62090561a41bbe336e98b78c2d0210a498dc885f28e1348cf17", info.certificateSha256)
        assertEquals("158457da5adaad9f915bc2f5f933d12d17a26d325d70e8086ea4bf1d80bb4f2e", ApkInspector.sha256(File(path)))
    }

    /**
     * A minimal binary manifest, `<manifest android:versionName="…">`, as aapt writes one: the XML
     * header, a string pool (UTF-16 or UTF-8), the attribute resource map, the root element.
     */
    private fun binaryManifest(
        utf8: Boolean,
        versionName: String,
        rootName: String = "manifest",
        valueType: Int = 0x03,
    ): ByteBuffer {
        val strings = listOf(rootName, "versionName", versionName)
        val data = ByteArrayOutputStream()
        val offsets =
            strings.map { value ->
                val offset = data.size()
                if (utf8) {
                    val bytes = value.toByteArray(Charsets.UTF_8)
                    data.write(value.length)
                    data.write(bytes.size)
                    data.write(bytes)
                    data.write(0)
                } else {
                    data.write(value.length and 0xff)
                    data.write(value.length shr 8)
                    value.forEach { c ->
                        data.write(c.code and 0xff)
                        data.write(c.code shr 8)
                    }
                    data.write(0)
                    data.write(0)
                }
                offset
            }
        while (data.size() % 4 != 0) data.write(0)

        val poolHeader = 28
        val poolSize = poolHeader + 4 * strings.size + data.size()
        val mapSize = 8 + 4 * 2
        val elementSize = 16 + 20 + 20
        val total = 8 + poolSize + mapSize + elementSize
        val out = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN)
        out.putShort(0x0003).putShort(8).putInt(total)
        out.putShort(0x0001).putShort(poolHeader.toShort()).putInt(poolSize)
        out
            .putInt(strings.size)
            .putInt(0)
            .putInt(if (utf8) 0x100 else 0)
            .putInt(poolHeader + 4 * strings.size)
            .putInt(0)
        offsets.forEach { out.putInt(it) }
        out.put(data.toByteArray())
        out
            .putShort(0x0180)
            .putShort(8)
            .putInt(mapSize)
            .putInt(0)
            .putInt(0x0101021c)
        out
            .putShort(0x0102)
            .putShort(16)
            .putInt(elementSize)
            .putInt(1)
            .putInt(-1)
        out
            .putInt(-1)
            .putInt(0)
            .putShort(20)
            .putShort(20)
            .putShort(1)
            .putShort(0)
            .putShort(0)
            .putShort(0)
        out
            .putInt(-1)
            .putInt(1)
            .putInt(2)
            .putShort(8)
            .put(0)
            .put(valueType.toByte())
            .putInt(2)
        return out.flip()
    }

    @Test
    fun readsTheVersionNameInEitherStringEncoding() {
        assertEquals("2.3.4-beta", ApkInspector.versionName(binaryManifest(utf8 = false, versionName = "2.3.4-beta")))
        assertEquals("2.3.4-beta", ApkInspector.versionName(binaryManifest(utf8 = true, versionName = "2.3.4-beta")))
        assertEquals("1.0-ñ", ApkInspector.versionName(binaryManifest(utf8 = true, versionName = "1.0-ñ")))
    }

    @Test
    fun noVersionNameWhenItIsNotALiteralOrNotTheManifest() {
        // A resource reference (@string/version) is not a literal.
        assertNull(ApkInspector.versionName(binaryManifest(utf8 = false, versionName = "x", valueType = 0x01)))
        assertNull(ApkInspector.versionName(binaryManifest(utf8 = false, versionName = "x", rootName = "application")))
        // Cut short: no crash, no answer.
        val cut = binaryManifest(utf8 = false, versionName = "2.3.4")
        assertNull(ApkInspector.versionName(cut.limit(cut.limit() - 30)))
        assertNull(ApkInspector.versionName(ByteBuffer.wrap(ByteArray(0))))
    }
}
