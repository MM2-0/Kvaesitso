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

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LooksLikeOpaquePayloadTest {
    @Test
    fun proseIsShown() {
        assertFalse(looksLikeOpaquePayload("Spamming the channel with links"))
        assertFalse(looksLikeOpaquePayload("Caddy is a web server that automatically obtains HTTPS certificates; a minimal two-line Caddyfile can reverse proxy an app."))
    }

    @Test
    fun longLinksAreShown() {
        val url = "https://image.example.com/generated/" + "a".repeat(140) + ".png?width=1024&height=1024"
        assertFalse(looksLikeOpaquePayload(url))
        assertFalse(looksLikeOpaquePayload("Here you go: nostr:nevent1" + "q".repeat(150)))
    }

    @Test
    fun jsonIsHidden() {
        assertTrue(looksLikeOpaquePayload("""{"timestamp":1792289067,"source":"T470"}"""))
        assertTrue(looksLikeOpaquePayload(""" [1, 2, 3] """))
    }

    @Test
    fun shortNip04CiphertextIsHidden() {
        assertTrue(looksLikeOpaquePayload("bKPs+dk3fOOiTfXddY6uE94BnolP1PUTF9qxskn52XZAIFBEWv2FLU9gcIeo8dz9fIn/jUblzVTlHNIvj5Vrdw==?iv=lq5WC8grY5Y9xBYjjKSaUw=="))
    }

    @Test
    fun base64BlobsAreHidden() {
        assertTrue(looksLikeOpaquePayload("/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDABQODxIPDRQSEBIXFRQYHjIhHhwcHj0sLiQySUBMS0dARkVQWnNiUFVtVkVGZIhlbXd7gYKBTmCNl4x9lnN+gXz/"))
        assertTrue(looksLikeOpaquePayload("AjL2Kc8m" + "x".repeat(200)))
    }

    @Test
    fun proseWithOneLongTokenIsShown() {
        val invoice = "lnbc10u1p" + "q".repeat(200)
        assertFalse(looksLikeOpaquePayload("Pay here: $invoice"))
        assertFalse(looksLikeOpaquePayload("summarize naddr1" + "q".repeat(180) + " please"))
        assertFalse(looksLikeOpaquePayload("Your change: cashuA" + "e".repeat(300)))
    }

    @Test
    fun aLoneInvoiceOrEntityIsShown() {
        assertFalse(looksLikeOpaquePayload("lnbc10u1p" + "q".repeat(200)))
        assertFalse(looksLikeOpaquePayload("naddr1" + "q".repeat(180)))
    }

    @Test
    fun bracketedProseIsNotJson() {
        assertFalse(looksLikeOpaquePayload("[spam] posted links [x]"))
        assertTrue(looksLikeOpaquePayload("""["a", "b"]"""))
    }

    @Test
    fun lineWrappedBase64IsHidden() {
        val wrapped = (1..4).joinToString("\n") { "QUJDREVGR0hJSktMTU5PUFFSU1RVVldYWVphYmNkZWZnaGlqa2xtbm9wcXJzdHV2d3h5ejAxMjM0NTY3ODk" }
        assertTrue(looksLikeOpaquePayload(wrapped))
    }
}
