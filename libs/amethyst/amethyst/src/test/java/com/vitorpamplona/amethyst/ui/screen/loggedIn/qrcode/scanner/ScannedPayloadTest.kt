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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.qrcode.scanner

import com.vitorpamplona.amethyst.commons.qrcode.ScannedPayload
import com.vitorpamplona.amethyst.commons.qrcode.classifyScannedPayload
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip19Bech32.entities.NSec
import com.vitorpamplona.quartz.nip19Bech32.toNsec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannedPayloadTest {
    private val pubkeyHex = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val npub = NPub.create(pubkeyHex)

    @Test
    fun `npub with and without the nostr prefix both classify as nostr`() {
        assertTrue(classifyScannedPayload(npub) is ScannedPayload.Nostr)
        assertTrue(classifyScannedPayload("nostr:$npub") is ScannedPayload.Nostr)
        assertTrue(classifyScannedPayload("  $npub  ") is ScannedPayload.Nostr)
    }

    @Test
    fun `a bare hex key is re-encoded rather than rejected`() {
        val payload = classifyScannedPayload(pubkeyHex)

        assertTrue(payload is ScannedPayload.HexKey)
        assertEquals(npub, (payload as ScannedPayload.HexKey).npub)
    }

    @Test
    fun `a bare hex key is treated as possibly secret`() {
        // A raw private key is the same 64 hex characters as a raw public key. Echoing it, or
        // routing it to a profile (which sends it to relays as an author), must wait for the
        // user to say which one it is.
        assertTrue(classifyScannedPayload(pubkeyHex).containsSecret)
    }

    @Test
    fun `uppercase hex is accepted and normalises to the same npub`() {
        val payload = classifyScannedPayload(pubkeyHex.uppercase())

        assertTrue(payload is ScannedPayload.HexKey)
        assertEquals(npub, (payload as ScannedPayload.HexKey).npub)
    }

    @Test
    fun `hex of the wrong length is not a pubkey`() {
        assertTrue(classifyScannedPayload(pubkeyHex.drop(1)) is ScannedPayload.Unknown)
        assertTrue(classifyScannedPayload(pubkeyHex + "ab") is ScannedPayload.Unknown)
    }

    @Test
    fun `signer and wallet schemes win over the nip19 scan`() {
        assertTrue(classifyScannedPayload("bunker://$pubkeyHex?relay=wss%3A%2F%2Frelay.example") is ScannedPayload.Bunker)
        assertTrue(classifyScannedPayload("nostrconnect://$pubkeyHex?relay=wss://r.example&secret=abc") is ScannedPayload.NostrConnect)
        assertTrue(classifyScannedPayload("nostr+walletconnect://$pubkeyHex?relay=wss://r.example&secret=abc") is ScannedPayload.WalletConnect)
        assertTrue(classifyScannedPayload("nostrwalletconnect://$pubkeyHex?secret=abc") is ScannedPayload.WalletConnect)
    }

    @Test
    fun `scheme matching ignores case`() {
        assertTrue(classifyScannedPayload("BUNKER://$pubkeyHex") is ScannedPayload.Bunker)
        assertTrue(classifyScannedPayload("Nostr+WalletConnect://$pubkeyHex") is ScannedPayload.WalletConnect)
    }

    @Test
    fun `lightning invoices and lnurls are recognised`() {
        assertTrue(classifyScannedPayload("lnbc1u1pjxyz") is ScannedPayload.Lightning)
        assertTrue(classifyScannedPayload("lightning:lnbc1u1pjxyz") is ScannedPayload.Lightning)
        assertTrue(classifyScannedPayload("LNURL1DP68GURN8GHJ7") is ScannedPayload.Lightning)
    }

    @Test
    fun `web links are recognised and carry their url`() {
        val payload = classifyScannedPayload("https://example.com/some/page")

        assertTrue(payload is ScannedPayload.Web)
        assertEquals("https://example.com/some/page", (payload as ScannedPayload.Web).url)
    }

    @Test
    fun `an uppercase link from alphanumeric mode gets a lowercase scheme and host`() {
        val payload = classifyScannedPayload("HTTPS://EXAMPLE.COM/ABC?Q=1#Frag")

        assertTrue(payload is ScannedPayload.Web)
        // Only the case-insensitive parts change: the path, query and fragment are the server's.
        assertEquals("https://example.com/ABC?Q=1#Frag", (payload as ScannedPayload.Web).url)
        assertEquals("HTTPS://EXAMPLE.COM/ABC?Q=1#Frag", payload.raw)
    }

    @Test
    fun `userinfo keeps its case`() {
        val payload = classifyScannedPayload("HTTPS://User:Pass@Example.com") as ScannedPayload.Web

        assertEquals("https://User:Pass@Example.com", payload.url)
    }

    @Test
    fun `an njump link resolves as the entity in its path, not as a web page`() {
        // The NIP-19 scan runs before the http check, and matches anywhere in the string. That is
        // deliberate: a scanned njump/nostr.at link should open the profile in the app rather
        // than a web page whose only job is to redirect back into one.
        assertTrue(classifyScannedPayload("https://njump.to/$npub") is ScannedPayload.Nostr)
    }

    @Test
    fun `plain text and blanks fall through to unknown`() {
        assertTrue(classifyScannedPayload("WIFI:S:cafe;T:WPA;P:hunter2;;") is ScannedPayload.Unknown)
        assertTrue(classifyScannedPayload("") is ScannedPayload.Unknown)
        assertTrue(classifyScannedPayload("   ") is ScannedPayload.Unknown)
    }

    @Test
    fun `everything carrying key material or a pairing secret is marked secret`() {
        val nsec = classifyScannedPayload(NSEC_FIXTURE)
        assertTrue(nsec is ScannedPayload.Nostr)
        assertTrue((nsec as ScannedPayload.Nostr).entity is NSec)
        assertTrue(nsec.containsSecret)

        assertTrue(classifyScannedPayload("bunker://$pubkeyHex?secret=abc").containsSecret)
        assertTrue(classifyScannedPayload("nostrconnect://$pubkeyHex?secret=abc").containsSecret)
        assertTrue(classifyScannedPayload("nostr+walletconnect://$pubkeyHex?secret=abc").containsSecret)
        assertTrue(classifyScannedPayload("cashuBo2Ftd").containsSecret)
    }

    @Test
    fun `an ncryptsec is secret even though nothing can parse it`() {
        // Nip19Parser's regex lists ncryptsec1 but parseComponents has no branch for it, so it
        // returns null and the payload lands in Unknown. Unknown is not secret, which put an
        // encrypted private key on screen with a Copy button -- the exact thing containsSecret
        // exists to prevent. Classification must not depend on whether we can decode the thing.
        val payload = classifyScannedPayload(NCRYPTSEC_FIXTURE)

        assertTrue("an encrypted private key must never be echoed", payload.containsSecret)
    }

    @Test
    fun `key material is secret whatever case it arrives in`() {
        assertTrue(classifyScannedPayload(NCRYPTSEC_FIXTURE.uppercase()).containsSecret)
        assertTrue(classifyScannedPayload("nostr:$NCRYPTSEC_FIXTURE").containsSecret)
    }

    @Test
    fun `public payloads are not marked secret`() {
        assertFalse(classifyScannedPayload(npub).containsSecret)
        assertFalse(classifyScannedPayload("https://example.com").containsSecret)
        assertFalse(classifyScannedPayload("lnbc1u1pjxyz").containsSecret)
        assertFalse(classifyScannedPayload("just some text").containsSecret)
    }

    @Test
    fun `an nsec is secret in every shape a QR code can carry it`() {
        // Bech32 is case-insensitive, and a QR encoder that wants alphanumeric mode -- half the
        // bytes of byte mode, so a noticeably smaller and easier-to-scan code -- must uppercase
        // its payload. An uppercased nsec is therefore not a corner case; it is what a
        // size-conscious generator produces.
        assertTrue("an uppercase nsec must never be echoed", classifyScannedPayload(NSEC_FIXTURE.uppercase()).containsSecret)
        assertTrue(classifyScannedPayload("nostr:$NSEC_FIXTURE").containsSecret)
        assertTrue(classifyScannedPayload("NOSTR:${NSEC_FIXTURE.uppercase()}").containsSecret)
    }

    @Test
    fun `an nsec too damaged to parse is still secret`() {
        // Same principle the ncryptsec branch already follows: whether a payload is dangerous to
        // put on screen cannot depend on whether we happen to be able to read it. A half-copied
        // key pasted from the clipboard, or one transcribed with a typo into a QR generator,
        // still shows most of its characters.
        assertTrue(classifyScannedPayload(NSEC_FIXTURE.dropLast(4)).containsSecret)
        assertTrue(classifyScannedPayload(NSEC_FIXTURE.dropLast(1) + "q").containsSecret)
    }

    @Test
    fun `raw is always the trimmed input`() {
        assertEquals(npub, classifyScannedPayload("\n $npub \t").raw)
    }

    companion object {
        /** A throwaway key, generated here so no real secret ever reaches a source file. */
        private val NSEC_FIXTURE = ByteArray(32) { (it + 1).toByte() }.toNsec()

        /** Shape only — nothing here can decrypt it, and classification must not need to. */
        private const val NCRYPTSEC_FIXTURE =
            "ncryptsec1qgg9947rlpvqu76pj5ecreduf9jxhselq2nae2kghhvd5g7dgjtcxfqtd67p9m0w57lspw8gsq6yphnm8623nsl8xn9j4jdzz84zm3frztj3z7s35vpzmqf6ksu8r89qk5z2zxfmu5gv8th8wclt0h4p"
    }
}
