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
package com.vitorpamplona.nestsclient

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip98HttpAuth.HTTPAuthorizationEvent
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalEncodingApi::class)
class NestsAuthTest {
    @Test
    fun header_has_nostr_prefix_and_decodes_to_signed_27235_event() =
        runTest {
            val signer = NostrSignerInternal(KeyPair())
            val header = NestsAuth.header(signer, "https://nostrnests.com/api/v1/nests/abc", "GET")

            assertTrue(header.startsWith("Nostr "), "header must start with `Nostr `, got: $header")

            val token = header.removePrefix("Nostr ")
            val json = Base64.decode(token).decodeToString()
            val event = Event.fromJson(json) as HTTPAuthorizationEvent

            assertEquals(HTTPAuthorizationEvent.KIND, event.kind)
            assertEquals("https://nostrnests.com/api/v1/nests/abc", event.url())
            assertEquals("GET", event.method())
            assertTrue(event.verify(), "signature must verify")
        }

    @Test
    fun header_binds_to_the_exact_url_and_method() =
        runTest {
            val signer = NostrSignerInternal(KeyPair())
            val a = NestsAuth.header(signer, "https://a.example.com/foo", "GET")
            val b = NestsAuth.header(signer, "https://a.example.com/foo", "POST")
            val c = NestsAuth.header(signer, "https://a.example.com/bar", "GET")

            fun tagsOf(header: String) =
                (Event.fromJson(Base64.decode(header.removePrefix("Nostr ")).decodeToString()) as HTTPAuthorizationEvent).let {
                    Triple(it.url(), it.method(), it.id)
                }

            val (urlA, methodA, idA) = tagsOf(a)
            val (urlB, methodB, idB) = tagsOf(b)
            val (urlC, methodC, idC) = tagsOf(c)

            assertEquals("https://a.example.com/foo", urlA)
            assertEquals("GET", methodA)
            assertEquals("POST", methodB)
            assertEquals("https://a.example.com/bar", urlC)
            // Same url+method with different signer instance still produces same binding fields but new id.
            assertTrue(idA != idB, "different method must produce a distinct event id")
            assertTrue(idA != idC, "different url must produce a distinct event id")
        }
}
