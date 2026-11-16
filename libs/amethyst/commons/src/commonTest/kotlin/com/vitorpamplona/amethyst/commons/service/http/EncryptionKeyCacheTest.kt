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
package com.vitorpamplona.amethyst.commons.service.http

import com.vitorpamplona.crypto.aes.AESGCM
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * An encrypted attachment whose link is a `blossom:` URI is never downloaded by that URI: it is
 * resolved to a server URL first, and the key has to be found under that URL.
 */
class EncryptionKeyCacheTest {
    private val sha = "b1674191a88ec5cdd733e4240a81803105dc412d6c6708d53ab94fc248f4f553"
    private val keys = EncryptionKeyCache()
    private val info = DecryptInformation(AESGCM(), "image/png")

    @Test
    fun blossomKeyFollowsTheBlobToAnyServer() {
        keys.add("blossom:$sha.png?xs=cdn.example.com&sz=1234", info)

        assertSame(info, keys.get("blossom:$sha.png?xs=cdn.example.com&sz=1234"))
        assertSame(info, keys.get("https://cdn.example.com/$sha.png"))
        assertSame(info, keys.get("https://other.example.com/$sha"))
        assertSame(info, keys.get("https://cdn.nostr.build/i/${sha.uppercase()}.bin?token=1"))
    }

    @Test
    fun otherBlobsAndLookalikeNamesStayLocked() {
        keys.add("blossom:$sha.png", info)

        assertNull(keys.get("https://cdn.example.com/${sha.replaceFirst('b', 'c')}.png"))
        // Embeds the hash but isn't a Blossom blob name (BUD-01).
        assertNull(keys.get("https://image.nostr.build/nostr.build_$sha.png"))
        assertNull(keys.get("https://$sha.example.com"))
    }

    @Test
    fun aKeyForAnHttpsUrlStaysWithThatUrl() {
        keys.add("https://cdn.example.com/$sha.png", info)

        assertSame(info, keys.get("https://cdn.example.com/$sha.png"))
        assertNull(keys.get("https://mirror.example.com/$sha.png"))
    }

    @Test
    fun removingTheUriForgetsTheBlob() {
        keys.add("blossom:$sha.png", info)
        keys.remove("blossom:$sha.png")

        assertNull(keys.get("https://cdn.example.com/$sha.png"))
    }
}
