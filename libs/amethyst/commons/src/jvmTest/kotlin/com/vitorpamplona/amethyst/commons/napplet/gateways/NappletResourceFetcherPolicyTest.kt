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
package com.vitorpamplona.amethyst.commons.napplet.gateways

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class NappletResourceFetcherPolicyTest {
    @Test
    fun blocksPrivateSpecialAndLocalAddresses() {
        val blocked =
            listOf(
                "0.0.0.0",
                "10.0.0.1",
                "100.64.0.1",
                "127.0.0.1",
                "169.254.169.254",
                "172.16.0.1",
                "192.0.0.1",
                "192.0.2.1",
                "192.168.1.1",
                "198.18.0.1",
                "198.51.100.1",
                "203.0.113.1",
                "224.0.0.1",
                "::1",
                "2001:db8::1",
                "fc00::1",
                "fe80::1",
            )

        blocked.forEach {
            assertFalse(it, NappletResourceFetcher.isPublicAddress(InetAddress.getByName(it)))
        }
    }

    @Test
    fun permitsPublicAddresses() {
        assertTrue(NappletResourceFetcher.isPublicAddress(InetAddress.getByName("1.1.1.1")))
        assertTrue(NappletResourceFetcher.isPublicAddress(InetAddress.getByName("2606:4700:4700::1111")))
    }

    @Test
    fun acceptsOnlyCredentialFreeHttpsUrls() {
        assertTrue(NappletResourceFetcher.isSafeHttpsResourceUrl("https://example.com/a"))
        assertFalse(NappletResourceFetcher.isSafeHttpsResourceUrl("http://example.com/a"))
        assertFalse(NappletResourceFetcher.isSafeHttpsResourceUrl("https://user:secret@example.com/a"))
        assertFalse(NappletResourceFetcher.isSafeHttpsResourceUrl("file:///etc/passwd"))
    }

    @Test
    fun bud10ReferencesUseArtifactHashAndUntrustedServerHints() {
        val hash = "ab".repeat(32)
        val parsed = NappletResourceFetcher.parseBlossomResource("blossom:$hash.png?xs=https%3A%2F%2Fexample.com&sz=123")!!
        assertEquals(hash, parsed.sha256)
        assertEquals(listOf("https://example.com"), parsed.servers)
        assertNull(NappletResourceFetcher.parseBlossomResource("blossom:sha256:$hash"))
        assertNull(NappletResourceFetcher.parseBlossomResource("blossom:invalid.png"))
        assertNull(NappletResourceFetcher.parseBlossomResource("blossom:$hash.png#fragment"))
    }

    @Test
    fun bud10ReferencesMayOmitTheExtension() {
        // The SeedHammer 3D viewer asks for its models as `blossom:<sha256>?xs=<host>`.
        val hash = "cd".repeat(32)
        val parsed = NappletResourceFetcher.parseBlossomResource("blossom:$hash?xs=bls.unrug.tech&xs=nostr.download")!!
        assertEquals(hash, parsed.sha256)
        assertEquals(listOf("bls.unrug.tech", "nostr.download"), parsed.servers)
        assertEquals(hash, NappletResourceFetcher.parseBlossomResource("blossom:$hash")!!.sha256)
        assertNull(NappletResourceFetcher.parseBlossomResource("blossom:$hash."))
        assertNull(NappletResourceFetcher.parseBlossomResource("blossom:$hash#fragment"))
        assertNull(NappletResourceFetcher.parseBlossomResource("blossom:${hash}ab"))
    }

    @Test
    fun binaryDataTheRuntimeDoesNotKnowIsDeliveredAsOctetStream() {
        val glb = "glTF".encodeToByteArray() + byteArrayOf(2, 0, 0, 0, 0x20, 0x7A, 0x0B, 0)
        assertEquals("application/octet-stream", NappletResourceFetcher.resourceType(glb))
        assertEquals("application/octet-stream", NappletResourceFetcher.resourceType(byteArrayOf(0, 1, 2, -1)))
        assertEquals("application/octet-stream", NappletResourceFetcher.resourceType(ByteArray(0)))
    }

    @Test
    fun knownMagicBytesKeepTheirType() {
        assertEquals("application/gzip", NappletResourceFetcher.resourceType(byteArrayOf(0x1F, -0x75, 8, 0)))
        assertEquals("application/wasm", NappletResourceFetcher.resourceType(byteArrayOf(0, 0x61, 0x73, 0x6D, 1, 0, 0, 0)))
        assertEquals("image/png", NappletResourceFetcher.resourceType(byteArrayOf(-0x77, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)))
    }

    @Test
    fun jsonAndTextNeedADeclarationTheBytesBearOut() {
        val json = """{"a":[1,2]}""".encodeToByteArray()
        assertEquals("application/json", NappletResourceFetcher.resourceType(json, "application/json"))
        assertEquals("application/octet-stream", NappletResourceFetcher.resourceType(json))
        assertEquals("application/octet-stream", NappletResourceFetcher.resourceType("{not json".encodeToByteArray(), "application/json"))
        assertEquals("text/plain", NappletResourceFetcher.resourceType("hello".encodeToByteArray(), "text/plain"))
        assertEquals("application/octet-stream", NappletResourceFetcher.resourceType(byteArrayOf(0x68, 0, 0x69), "text/plain"))
    }

    @Test
    fun svgHtmlAndXmlAreRefused() {
        val refused =
            listOf(
                """<svg xmlns="http://www.w3.org/2000/svg" onload="x()"/>""",
                """<!-- logo --><svg/>""",
                "<!DOCTYPE html><p>hi",
                "<html><body>",
                "  \n\t<script>alert(1)</script>",
                "\uFEFF<html>",
                "<!-- comment -->",
                "<a href=x>link</a>",
                "<?xml version=\"1.0\"?><html xmlns=\"http://www.w3.org/1999/xhtml\"/>",
                "<IFRAME src=x>",
            )
        refused.forEach { assertNull(it, NappletResourceFetcher.resourceType(it.encodeToByteArray())) }
    }

    @Test
    fun declaredMarkupTypesAreRefusedWhateverTheBytes() {
        val bytes = "plain".encodeToByteArray()
        listOf("image/svg+xml", "text/html", "application/xhtml+xml", "text/xml", "application/xml").forEach {
            assertNull(it, NappletResourceFetcher.resourceType(bytes, it))
        }
    }

    @Test
    fun textThatOnlyMentionsMarkupIsNotRefused() {
        // Openers only count at the start, followed by a space or `>`, as browsers sniff them.
        assertEquals("text/plain", NappletResourceFetcher.resourceType("<abc> is not a tag we sniff".encodeToByteArray(), "text/plain"))
        assertEquals("text/plain", NappletResourceFetcher.resourceType("<br/> stays text".encodeToByteArray(), "text/plain"))
        assertEquals("application/json", NappletResourceFetcher.resourceType("""{"html":"<b>bold</b>"}""".encodeToByteArray(), "application/json"))
    }

    @Test
    fun dataUrlEscapesAreBytesAndPlusIsLiteral() {
        assertArrayEquals(byteArrayOf(0, 1, 2, -1), NappletResourceFetcher.decodeDataPayload("%00%01%02%FF"))
        assertEquals("a+b c", NappletResourceFetcher.decodeDataPayload("a+b%20c").decodeToString())
        assertTrue(runCatching { NappletResourceFetcher.decodeDataPayload("%GG") }.isFailure)
        assertTrue(runCatching { NappletResourceFetcher.decodeDataPayload("%1") }.isFailure)
    }
}
