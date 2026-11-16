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
package com.vitorpamplona.amethyst.commons.browser

import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class BrowserDownloadRulesTest {
    @Test
    fun flagsInstallableAndScriptExtensions() {
        assertTrue(BrowserDownloadRules.isRisky("invoice.apk"))
        assertTrue(BrowserDownloadRules.isRisky("invoice.pdf.APK"))
        assertTrue(BrowserDownloadRules.isRisky("page.html"))
        assertFalse(BrowserDownloadRules.isRisky("photo.jpg"))
        assertFalse(BrowserDownloadRules.isRisky("apk"))
        assertFalse(BrowserDownloadRules.isRisky("download"))
    }

    @Test
    fun decodesBase64DataUrl() {
        val payload = "hello world".encodeToByteArray()
        val url = "data:text/plain;charset=utf-8;base64," + Base64.encode(payload)
        val decoded = BrowserDownloadRules.decodeDataUrl(url)!!
        assertEquals("text/plain", decoded.mimeType)
        assertContentEquals(payload, decoded.bytes)
    }

    @Test
    fun decodesUnpaddedAndWrappedBase64() {
        assertContentEquals("hi".encodeToByteArray(), BrowserDownloadRules.decodeDataUrl("data:;base64,aGk")!!.bytes)
        assertContentEquals("hello world".encodeToByteArray(), BrowserDownloadRules.decodeDataUrl("data:;base64,aGVsbG8g\r\nd29ybGQ=")!!.bytes)
    }

    @Test
    fun decodesPercentEncodedDataUrl() {
        val decoded = BrowserDownloadRules.decodeDataUrl("DATA:,a%20b+c%C3%A9")!!
        assertNull(decoded.mimeType)
        assertEquals("a b+cé", decoded.bytes.decodeToString())
    }

    @Test
    fun refusesMalformedDataUrls() {
        assertNull(BrowserDownloadRules.decodeDataUrl("https://example.com/a.apk"))
        assertNull(BrowserDownloadRules.decodeDataUrl("data:text/plain;base64"))
        assertNull(BrowserDownloadRules.decodeDataUrl("data:,broken%2"))
        assertNull(BrowserDownloadRules.decodeDataUrl("data:,broken%zz"))
    }

    @Test
    fun refusesOversizedPayloadsInsteadOfTruncating() {
        val bytes = ByteArray(100) { it.toByte() }
        val url = "data:application/octet-stream;base64," + Base64.encode(bytes)
        assertEquals(100, BrowserDownloadRules.decodeDataUrl(url, maxBytes = 100)!!.bytes.size)
        assertNull(BrowserDownloadRules.decodeDataUrl(url, maxBytes = 99))
        assertNull(BrowserDownloadRules.decodeDataUrl("data:," + "a".repeat(11), maxBytes = 10))
    }

    @Test
    fun base64MarkerMustBeTheLastParameter() {
        assertEquals("aGk", BrowserDownloadRules.decodeDataUrl("data:text/plain;base64;x=y,aGk")!!.bytes.decodeToString())
    }

    @Test
    fun percentDecodingCountsBytesBeforeAllocating() {
        assertEquals(3, BrowserDownloadRules.decodeDataUrl("data:,%E4%B8%AD", maxBytes = 3)!!.bytes.size)
        assertEquals("中", BrowserDownloadRules.decodeDataUrl("data:,中", maxBytes = 3)!!.bytes.decodeToString())
        assertNull(BrowserDownloadRules.decodeDataUrl("data:,中中", maxBytes = 5))
        assertEquals(4, BrowserDownloadRules.decodeDataUrl("data:,\uD83D\uDE00", maxBytes = 4)!!.bytes.size)
        // A lone surrogate must not overflow the pre-counted buffer.
        assertTrue(BrowserDownloadRules.decodeDataUrl("data:,a\uDE00b%41")!!.bytes.isNotEmpty())
    }

    @Test
    fun safeFileNameStripsPathsAndInvisibleCharacters() {
        assertEquals("evil.apk", BrowserDownloadRules.safeFileName("../../evil.apk"))
        assertEquals("evil.apk", BrowserDownloadRules.safeFileName("C:\\x\\evil.apk"))
        assertEquals("a_b_.pdf", BrowserDownloadRules.safeFileName("a:b?.pdf"))
        assertEquals("invoicegpj.apk", BrowserDownloadRules.safeFileName("invoice\u202Egpj.apk"))
        assertEquals("ab.txt", BrowserDownloadRules.safeFileName("a\u200Bb\u0085.txt"))
        assertNull(BrowserDownloadRules.safeFileName(".."))
        assertNull(BrowserDownloadRules.safeFileName("  \u200F "))
        assertNull(BrowserDownloadRules.safeFileName(null))
    }

    @Test
    fun safeFileNameKeepsTheExtensionWhenShortening() {
        val name = BrowserDownloadRules.safeFileName("photo.jpg" + "x".repeat(300) + ".apk")!!
        assertEquals(BrowserDownloadRules.MAX_NAME_LENGTH, name.length)
        assertTrue(name.endsWith(".apk"))
        assertTrue(BrowserDownloadRules.isRisky(name))
    }

    @Test
    fun cooldownHoldsOffOnlyTheAnsweredOrigin() {
        val clock = TestTimeSource()
        val cooldown = DownloadCooldown(1.seconds, 1.minutes, clock)
        assertTrue(cooldown.allows("https://a.example"))

        cooldown.answered("https://a.example", allowed = true)
        assertFalse(cooldown.allows("https://a.example"))
        assertTrue(cooldown.allows("https://b.example"))

        clock += 999.milliseconds
        assertFalse(cooldown.allows("https://a.example"))
        clock += 1.milliseconds
        assertTrue(cooldown.allows("https://a.example"))
    }

    @Test
    fun cooldownDoublesOnEachRefusalAndResetsOnSave() {
        val clock = TestTimeSource()
        val cooldown = DownloadCooldown(1.seconds, 4.seconds, clock)
        val site = "https://a.example"

        cooldown.answered(site, allowed = false) // 1s
        clock += 1.seconds
        assertTrue(cooldown.allows(site))
        cooldown.answered(site, allowed = false) // 2s
        clock += 1.seconds
        assertFalse(cooldown.allows(site))
        clock += 1.seconds
        assertTrue(cooldown.allows(site))
        cooldown.answered(site, allowed = false) // 4s
        cooldown.answered(site, allowed = false) // capped at 4s
        clock += 3.seconds
        assertFalse(cooldown.allows(site))
        clock += 1.seconds
        assertTrue(cooldown.allows(site))

        cooldown.answered(site, allowed = true) // back to 1s
        clock += 1.seconds
        assertTrue(cooldown.allows(site))
    }
}
