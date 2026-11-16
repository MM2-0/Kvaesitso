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
package com.vitorpamplona.amethyst.commons.agents

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpenRouterOAuthTest {
    @Test
    fun challengeMatchesRfc7636AppendixB() {
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            OpenRouterOAuth.challengeFor("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"),
        )
    }

    @Test
    fun verifiersAreUrlSafeAndFresh() {
        val a = OpenRouterOAuth.newVerifier()
        assertTrue(a.length >= 43)
        assertTrue(a.all { it.isLetterOrDigit() || it == '-' || it == '_' })
        assertNotEquals(a, OpenRouterOAuth.newVerifier())
    }

    @Test
    fun authUrlCarriesEveryParameterEncoded() {
        val request = OpenRouterOAuth.newRequest("http://localhost:51423/callback", verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk", state = "s+1")
        assertEquals(
            "https://openrouter.ai/auth?callback_url=http%3A%2F%2Flocalhost%3A51423%2Fcallback" +
                "&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM&code_challenge_method=S256" +
                "&state=s%2B1&key_label=Amethyst",
            request.authUrl,
        )
    }

    @Test
    fun callbackNeedsACodeAndOurState() {
        val request = OpenRouterOAuth.newRequest("http://localhost:1/callback", state = "abc")
        assertEquals(OpenRouterCallback.Code("c1"), OpenRouterOAuth.readCallback(request, OpenRouterOAuth.parseQuery("code=c1&state=abc")))
        assertEquals(OpenRouterCallback.StateMismatch, OpenRouterOAuth.readCallback(request, OpenRouterOAuth.parseQuery("code=c1&state=other")))
        // OpenRouter sends no state when the user denies.
        assertEquals(OpenRouterCallback.Denied, OpenRouterOAuth.readCallback(request, OpenRouterOAuth.parseQuery("")))
        // Another flow's answer, code or not, is never this one's: the wait goes on.
        assertEquals(OpenRouterCallback.StateMismatch, OpenRouterOAuth.readCallback(request, OpenRouterOAuth.parseQuery("state=other")))
        assertEquals(OpenRouterCallback.StateMismatch, OpenRouterOAuth.readCallback(request, OpenRouterOAuth.parseQuery("code=c1")))
    }

    @Test
    fun percentDecodingNeverOverflowsOrTakesSigns() {
        // Each "é" is two bytes: a buffer sized by characters overflowed here.
        assertEquals("ééé", OpenRouterOAuth.percentDecode("ééé"))
        assertEquals("% 5%-1", OpenRouterOAuth.percentDecode("%+5%-1"))
        assertEquals("é", OpenRouterOAuth.percentDecode("%C3%A9"))
    }

    @Test
    fun queryAndPercentCodingRoundTrip() {
        val raw = "a b+c/é&=?"
        assertEquals(raw, OpenRouterOAuth.percentDecode(OpenRouterOAuth.percentEncode(raw)))
        assertEquals(mapOf("x" to "1 2", "y" to ""), OpenRouterOAuth.parseQuery("x=1+2&y="))
    }

    @Test
    fun exchangeBodyAndAnswer() {
        assertEquals(
            """{"code":"c","code_verifier":"v","code_challenge_method":"S256"}""",
            OpenRouterOAuth.exchangeBody("c", "v"),
        )
        assertEquals("sk-or-v1-x", OpenRouterOAuth.parseKey("""{"key":"sk-or-v1-x","user_id":"u"}"""))
        assertNull(OpenRouterOAuth.parseKey("""{"error":{"code":400}}"""))
        assertNull(OpenRouterOAuth.parseKey("<html>"))
        assertIs<OpenRouterConnectResult.Connected>(OpenRouterConnectResult.Connected("k"))
        assertTrue("sk-secret" !in OpenRouterConnectResult.Connected("sk-secret").toString())
    }
}
