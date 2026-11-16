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

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Per-room configuration that orchestration needs to connect. Built from
 * the NIP-53 kind 30312 [MeetingSpaceEvent] by the caller (UI / VM)
 * before invoking `connectNestsListener` / `connectNestsSpeaker`:
 *
 *   - [authBaseUrl] — the event's `service` tag (e.g. `https://nostrnests.com/api/v1/nests`).
 *     Note: the real moq-auth API is rooted at this base; the client posts
 *     to `<authBaseUrl>/auth` to mint a JWT.
 *   - [endpoint] — the event's `endpoint` tag (e.g. `https://relay.nostrnests.com:4443/anon`).
 *     The MoQ relay's WebTransport URL.
 *   - [hostPubkey] — the event author's pubkey. Goes into the
 *     [NestsAuth.MOQ_NAMESPACE_PREFIX] to scope the JWT to this room.
 *   - [roomId] — the event's `d` tag.
 *   - [kind] — the NIP-53 event kind (30312 for meeting spaces).
 */
data class NestsRoomConfig(
    val authBaseUrl: String,
    val endpoint: String,
    val hostPubkey: String,
    val roomId: String,
    val kind: Int = MEETING_SPACE_KIND,
) {
    /**
     * MoQ namespace string for this room, in the format moq-auth expects:
     * `nests/<kind>:<host_pubkey_hex>:<roomId>`.
     */
    fun moqNamespace(): String = "nests/$kind:$hostPubkey:$roomId"

    companion object {
        const val MEETING_SPACE_KIND: Int = 30312
    }
}

/**
 * Response shape from `POST <authBase>/auth`. The reference server
 * (`nostrnests/nests/moq-auth/src/index.ts`) returns `{"token":"<jwt>"}`
 * — that JWT is then passed as the WebTransport bearer token to the
 * MoQ relay.
 */
@Serializable
data class NestsTokenResponse(
    val token: String,
) {
    companion object {
        private val json =
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            }

        fun parse(body: String): NestsTokenResponse = json.decodeFromString(serializer(), body)
    }
}

/**
 * Build the auth URL for a given service base. Trims trailing slashes
 * and appends `/auth`.
 *
 * Example: `https://nostrnests.com/api/v1/nests` →
 * `https://nostrnests.com/api/v1/nests/auth`.
 */
fun nestsAuthUrl(authBase: String): String = authBase.trimEnd('/') + "/auth"
