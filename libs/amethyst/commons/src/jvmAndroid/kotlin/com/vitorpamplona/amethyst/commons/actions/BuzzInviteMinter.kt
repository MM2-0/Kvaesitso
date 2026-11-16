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
package com.vitorpamplona.amethyst.commons.actions

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip98HttpAuth.HTTPAuthorizationEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.coroutines.executeAsync

/**
 * Mints a `block/buzz` workspace invite link by POSTing to the relay's **Buzz-specific**
 * `/api/invites` HTTP endpoint (NIP-98 signed; owner/admin only). Not a Nostr event and not a NIP —
 * only the NIP-98 auth is standard — so this is Buzz-only and lives outside quartz.
 *
 * The endpoint host is the relay's own host (the invite link is `https://<host>/invite/<code>`), so
 * we never need to own a domain — the relay does, and it checks the signer is an owner/admin. See
 * `crates/buzz-relay/src/api/invites.rs`.
 */
object BuzzInviteMinter {
    /** Shortest lifetime the relay accepts (`MIN_INVITE_TTL_SECS` in `buzz-core/src/invite.rs`). */
    const val MIN_TTL_SECS = 60L

    /** Longest lifetime the relay accepts: 30 days (`MAX_INVITE_TTL_SECS`). */
    const val MAX_TTL_SECS = 30L * 24 * 60 * 60

    /** Largest `max_uses` the relay accepts (`MAX_INVITE_USES`, the database constraint). */
    const val MAX_USES = 10_000

    /**
     * A freshly minted invite: the opaque [code], the shareable [url], and its [expiresAt] (secs).
     * [maxUses] / [usesRemaining] are null for an unlimited invite (the default).
     */
    data class MintedInvite(
        val code: String,
        val url: String,
        val expiresAt: Long,
        val maxUses: Int? = null,
        val usesRemaining: Int? = null,
    )

    /**
     * POST `/api/invites` on [relay]'s host with an optional [ttlSecs] (default 72 h) and an
     * optional [maxUses] cap (default unlimited). The relay does **not** clamp: a [ttlSecs] outside
     * [[MIN_TTL_SECS], [MAX_TTL_SECS]] or a [maxUses] outside [1, [MAX_USES]] is refused with a 400,
     * so both are checked here first. [httpAuth] signs the NIP-98 event over the exact URL + body;
     * [okHttpClient] supplies the transport (use a trusted-relay-posture client so a
     * Cloudflare-fronted relay is reached over clearnet). Throws [IllegalStateException] with the
     * relay's error message on failure.
     */
    suspend fun mint(
        relay: NormalizedRelayUrl,
        ttlSecs: Long?,
        okHttpClient: (String) -> OkHttpClient,
        httpAuth: suspend (url: String, method: String, body: ByteArray?) -> HTTPAuthorizationEvent,
        maxUses: Int? = null,
    ): MintedInvite =
        withContext(Dispatchers.IO) {
            require(ttlSecs == null || ttlSecs in MIN_TTL_SECS..MAX_TTL_SECS) { "ttl_secs must be between $MIN_TTL_SECS and $MAX_TTL_SECS" }
            require(maxUses == null || maxUses in 1..MAX_USES) { "max_uses must be between 1 and $MAX_USES" }

            // wss://host[/..] -> https://host ; ws://host -> http://host. The endpoint is host-root.
            val wsUrl = relay.url
            val scheme = if (wsUrl.startsWith("wss", ignoreCase = true)) "https" else "http"
            val host = wsUrl.substringAfter("://").substringBefore("/")
            // Parse once into an HttpUrl and sign over ITS canonical string, so the NIP-98 `u` tag is
            // byte-for-byte what OkHttp transmits (host casing / encoding can't drift the two apart).
            val httpUrl = "$scheme://$host/api/invites".toHttpUrl()
            val url = httpUrl.toString()

            // Exact bytes the NIP-98 payload hash is computed over — must equal what we send.
            val bodyStr = requestBody(ttlSecs, maxUses)
            val bodyBytes = bodyStr.toByteArray(Charsets.UTF_8)

            val auth = httpAuth(url, "POST", bodyBytes)

            val request =
                Request
                    .Builder()
                    .url(httpUrl)
                    .addHeader("Authorization", auth.toAuthToken())
                    .post(bodyBytes.toRequestBody("application/json".toMediaType()))
                    .build()

            okHttpClient(url).newCall(request).executeAsync().use { response ->
                val payload = response.body.string()
                val tree = runCatching { Json.parseToJsonElement(payload).jsonObject }.getOrNull()

                if (!response.isSuccessful) {
                    val slug = tree?.get("error")?.stringOrNull() ?: "HTTP ${response.code}"
                    throw IllegalStateException(slug)
                }

                MintedInvite(
                    code = tree?.get("code")?.stringOrNull().orEmpty(),
                    url = tree?.get("url")?.stringOrNull().orEmpty(),
                    expiresAt = tree?.get("expires_at")?.longOrNull() ?: 0L,
                    maxUses = tree?.get("max_uses")?.longOrNull()?.toInt(),
                    usesRemaining = tree?.get("uses_remaining")?.longOrNull()?.toInt(),
                )
            }
        }

    /** The exact JSON body of a mint request; omitted fields take the relay's defaults (72 h, unlimited). */
    fun requestBody(
        ttlSecs: Long?,
        maxUses: Int?,
    ): String =
        buildList {
            ttlSecs?.let { add("\"ttl_secs\":$it") }
            maxUses?.let { add("\"max_uses\":$it") }
        }.joinToString(",", prefix = "{", postfix = "}")
}

private fun JsonElement.stringOrNull(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonElement.longOrNull(): Long? = (this as? JsonPrimitive)?.content?.toLongOrNull()
