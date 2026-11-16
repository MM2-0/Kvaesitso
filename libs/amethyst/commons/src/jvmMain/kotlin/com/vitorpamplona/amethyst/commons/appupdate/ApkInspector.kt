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

import com.android.apksig.ApkVerifier
import com.android.apksig.apk.ApkUtils
import com.android.apksig.util.DataSources
import com.vitorpamplona.crypto.sha2.Sha256Hasher
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * Reads what a release needs from an APK, with Google's apksig: verifies its signature (v1 to v3,
 * as Android would) and takes the signing certificate's SHA-256, the package name, version and SDK
 * range from the binary manifest, and the native ABIs from `lib/`. zsp does the same with Go
 * libraries; an APK that does not verify is refused, as there.
 */
object ApkInspector {
    /** `android:versionName`, which apksig's public API does not read. */
    private const val ATTR_VERSION_NAME = 0x0101021c

    class InvalidApkException(
        message: String,
    ) : Exception(message)

    fun inspect(apk: File): ApkInfo {
        val result = ApkVerifier.Builder(apk).build().verify()
        if (!result.isVerified) {
            throw InvalidApkException("${apk.name} does not verify: ${result.errors.joinToString("; ")}")
        }
        // The highest scheme's signer; with key rotation (v3) that is the current key, as zsp picks.
        val certificate = result.signerCertificates.firstOrNull() ?: throw InvalidApkException("${apk.name} has no signer certificate")

        val manifest =
            RandomAccessFile(apk, "r").use { file ->
                ApkUtils.getAndroidManifest(DataSources.asDataSource(file))
            }

        return ApkInfo(
            packageName = ApkUtils.getPackageNameFromBinaryAndroidManifest(manifest.duplicate()),
            versionName = versionName(manifest.duplicate()),
            versionCode = ApkUtils.getLongVersionCodeFromBinaryAndroidManifest(manifest.duplicate()),
            minSdk = ApkUtils.getMinSdkVersionFromBinaryAndroidManifest(manifest.duplicate()),
            targetSdk = ApkUtils.getTargetSdkVersionFromBinaryAndroidManifest(manifest.duplicate()),
            nativeAbis = nativeAbis(apk),
            certificateSha256 = MessageDigest.getInstance("SHA-256").digest(certificate.encoded).toHexKey(),
        )
    }

    /**
     * The `android:versionName` of the root `<manifest>` element, when it is a literal string. Read
     * from the binary XML here: apksig's public API has no reader for it, only an internal class.
     * The format is a run of chunks (`u16 type, u16 headerSize, u32 size`, little-endian): a string
     * pool, the attribute resource-id map, then one chunk per element start and end.
     */
    internal fun versionName(manifest: ByteBuffer): String? =
        try {
            BinaryXml(manifest.duplicate().order(ByteOrder.LITTLE_ENDIAN)).rootAttributeString(ATTR_VERSION_NAME)
        } catch (e: IndexOutOfBoundsException) {
            null
        }

    private class BinaryXml(
        private val buf: ByteBuffer,
    ) {
        private val base = buf.position()
        private var poolAt = -1
        private var resourceIds = IntArray(0)

        private fun u16(at: Int) = buf.getShort(at).toInt() and 0xffff

        private fun u8(at: Int) = buf.get(at).toInt() and 0xff

        fun rootAttributeString(resourceId: Int): String? {
            if (u16(base) != XML_TYPE) return null
            val end = base + buf.getInt(base + 4)
            var at = base + u16(base + 2)
            while (at + 8 <= end) {
                val headerSize = u16(at + 2)
                val size = buf.getInt(at + 4)
                if (size < 8 || at + size > end) return null
                when (u16(at)) {
                    STRING_POOL_TYPE -> poolAt = at
                    RESOURCE_MAP_TYPE -> resourceIds = IntArray((size - headerSize) / 4) { buf.getInt(at + headerSize + it * 4) }
                    // The first element is the root: <manifest>.
                    START_ELEMENT_TYPE -> return manifestAttribute(at + headerSize, resourceId)
                }
                at += size
            }
            return null
        }

        // Element: u32 ns, u32 name, u16 attributeStart, u16 attributeSize, u16 attributeCount, ...
        // Attribute: u32 ns, u32 name, u32 rawValue, u16 size, u8 res0, u8 dataType, u32 data.
        private fun manifestAttribute(
            element: Int,
            resourceId: Int,
        ): String? {
            if (string(buf.getInt(element + 4)) != "manifest") return null
            val start = u16(element + 8)
            val size = u16(element + 10)
            for (i in 0 until u16(element + 12)) {
                val attribute = element + start + i * size
                if (resourceIds.getOrNull(buf.getInt(attribute + 4)) != resourceId) continue
                return if (u8(attribute + 15) == TYPE_STRING) string(buf.getInt(attribute + 16)) else null
            }
            return null
        }

        // Pool: header (u32 count, u32 styles, u32 flags, u32 stringsStart, u32 stylesStart), then
        // u32 offsets from stringsStart. UTF-8: char and byte lengths (1 or 2 bytes each), bytes.
        // UTF-16: length (1 or 2 u16s), chars.
        private fun string(index: Int): String? {
            if (poolAt < 0 || index < 0 || index >= buf.getInt(poolAt + 8)) return null
            val utf8 = buf.getInt(poolAt + 16) and UTF8_FLAG != 0
            var at = poolAt + buf.getInt(poolAt + 20) + buf.getInt(poolAt + u16(poolAt + 2) + index * 4)
            return if (utf8) {
                at += if (u8(at) and 0x80 != 0) 2 else 1
                val length = if (u8(at) and 0x80 != 0) ((u8(at) and 0x7f) shl 8) or u8(at + 1) else u8(at)
                at += if (u8(at) and 0x80 != 0) 2 else 1
                String(ByteArray(length) { buf.get(at + it) }, Charsets.UTF_8)
            } else {
                val length = if (u16(at) and 0x8000 != 0) ((u16(at) and 0x7fff) shl 16) or u16(at + 2) else u16(at)
                at += if (u16(at) and 0x8000 != 0) 4 else 2
                String(CharArray(length) { buf.getShort(at + it * 2).toInt().toChar() })
            }
        }
    }

    /** The ABI folders under `lib/`, sorted. */
    private fun nativeAbis(apk: File): List<String> =
        ZipFile(apk).use { zip ->
            zip
                .entries()
                .asSequence()
                .map { it.name }
                .filter { it.startsWith("lib/") && !it.contains("..") }
                .mapNotNull { it.split('/').getOrNull(1)?.takeIf(String::isNotEmpty) }
                .toSortedSet()
                .toList()
        }

    fun sha256(file: File): String = file.inputStream().use { Sha256Hasher().hashStream(it, 1 shl 16) }.toHexKey()

    private const val XML_TYPE = 0x0003
    private const val STRING_POOL_TYPE = 0x0001
    private const val RESOURCE_MAP_TYPE = 0x0180
    private const val START_ELEMENT_TYPE = 0x0102
    private const val TYPE_STRING = 0x03
    private const val UTF8_FLAG = 0x100
}
