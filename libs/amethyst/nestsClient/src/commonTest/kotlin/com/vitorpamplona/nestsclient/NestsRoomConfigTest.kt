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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NestsRoomConfigTest {
    @Test
    fun moq_namespace_uses_kind_host_pubkey_room_id_format() {
        val cfg =
            NestsRoomConfig(
                authBaseUrl = "https://nostrnests.com/api/v1/nests",
                endpoint = "https://relay.nostrnests.com:4443/anon",
                hostPubkey = "0".repeat(64),
                roomId = "room-abc",
            )
        // Matches moq-auth's NAMESPACE_REGEX: nests/<kind>:<hex64>:<room>
        assertEquals(
            "nests/30312:${"0".repeat(64)}:room-abc",
            cfg.moqNamespace(),
        )
    }

    @Test
    fun parses_token_response_from_moq_auth() {
        val r = NestsTokenResponse.parse("""{"token":"abc.def.ghi"}""")
        assertEquals("abc.def.ghi", r.token)
    }

    @Test
    fun ignores_unknown_response_fields() {
        val r = NestsTokenResponse.parse("""{"token":"t","exp":1234567,"future_knob":"x"}""")
        assertEquals("t", r.token)
    }

    @Test
    fun rejects_missing_token_field() {
        assertFailsWith<Exception> { NestsTokenResponse.parse("""{"endpoint":"x"}""") }
    }

    @Test
    fun builds_auth_url_from_service_base() {
        assertEquals(
            "https://nostrnests.com/api/v1/nests/auth",
            nestsAuthUrl("https://nostrnests.com/api/v1/nests"),
        )
    }

    @Test
    fun trims_trailing_slash_from_service_base() {
        assertEquals(
            "https://nostrnests.com/api/v1/nests/auth",
            nestsAuthUrl("https://nostrnests.com/api/v1/nests/"),
        )
    }
}
