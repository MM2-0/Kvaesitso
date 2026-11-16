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

import com.vitorpamplona.crypto.random.RandomInstance
import com.vitorpamplona.crypto.sha2.sha256
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.io.encoding.Base64

/**
 * OpenRouter's OAuth PKCE flow (openrouter.ai/docs/guides/overview/auth/oauth): the user
 * approves on openrouter.ai, which redirects to [OpenRouterAuthRequest.callbackUrl] with a
 * `code`; the code and the verifier buy an API key at [EXCHANGE_URL]. The pure parts,
 * shared by every platform; the platform part opens the browser and receives the redirect.
 */
object OpenRouterOAuth {
    const val AUTH_URL = "https://openrouter.ai/auth"
    const val EXCHANGE_URL = "https://openrouter.ai/api/v1/auth/keys"
    const val KEY_LABEL = "Amethyst"

    private val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

    fun newVerifier(): String = base64Url.encode(RandomInstance.bytes(32))

    /** S256: base64url(sha256(verifier)), no padding (RFC 7636). */
    fun challengeFor(verifier: String): String = base64Url.encode(sha256(verifier.encodeToByteArray()))

    fun newRequest(
        callbackUrl: String,
        verifier: String = newVerifier(),
        state: String = base64Url.encode(RandomInstance.bytes(16)),
    ) = OpenRouterAuthRequest(
        callbackUrl = callbackUrl,
        verifier = verifier,
        state = state,
        authUrl =
            AUTH_URL +
                "?callback_url=" + percentEncode(callbackUrl) +
                "&code_challenge=" + challengeFor(verifier) +
                "&code_challenge_method=S256" +
                "&state=" + percentEncode(state) +
                "&key_label=" + percentEncode(KEY_LABEL),
    )

    /** The JSON body of the exchange. */
    fun exchangeBody(
        code: String,
        verifier: String,
    ): String =
        JsonObject(
            mapOf(
                "code" to JsonPrimitive(code),
                "code_verifier" to JsonPrimitive(verifier),
                "code_challenge_method" to JsonPrimitive("S256"),
            ),
        ).toString()

    /** The `key` of the exchange's answer, or null for anything else. */
    fun parseKey(body: String): String? =
        try {
            ((Json.parseToJsonElement(body) as? JsonObject)?.get("key") as? JsonPrimitive)
                ?.takeIf { it.isString }
                ?.content
                ?.ifBlank { null }
        } catch (e: SerializationException) {
            null
        }

    /** What came back on the callback: the `code` when [request]'s state matches. */
    fun readCallback(
        request: OpenRouterAuthRequest,
        query: Map<String, String>,
    ): OpenRouterCallback {
        val code = query["code"]
        val state = query["state"]
        // Another flow's state is never ours, whatever it carries. OpenRouter sends no state
        // when the user denies, so a bare callback is a denial; a code needs our state.
        return when {
            state != null && state != request.state -> OpenRouterCallback.StateMismatch
            code.isNullOrBlank() -> OpenRouterCallback.Denied
            state != request.state -> OpenRouterCallback.StateMismatch
            else -> OpenRouterCallback.Code(code)
        }
    }

    /** `a=1&b=x%20y` → map, percent-decoded. */
    fun parseQuery(query: String): Map<String, String> =
        query
            .split('&')
            .filter { it.isNotEmpty() }
            .associate {
                val name = it.substringBefore('=')
                val value = it.substringAfter('=', "")
                percentDecode(name) to percentDecode(value)
            }

    /** RFC 3986: everything but the unreserved characters. */
    fun percentEncode(value: String): String =
        buildString {
            value.encodeToByteArray().forEach { byte ->
                val c = byte.toInt().toChar()
                if (c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '-' || c == '_' || c == '.' || c == '~') {
                    append(c)
                } else {
                    append('%')
                    append(HEX[(byte.toInt() shr 4) and 0x0F])
                    append(HEX[byte.toInt() and 0x0F])
                }
            }
        }

    /**
     * `%XX` escapes and `+` as space. Grows as it goes: a non-ASCII character is several bytes, so
     * a buffer of `value.length` bytes overflowed on one. Only two hex digits make an escape
     * (`toIntOrNull(16)` also took `%+5` and `%-1`).
     */
    fun percentDecode(value: String): String {
        val out = ArrayList<Byte>(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '%' && i + 2 <= value.lastIndex) {
                val high = hexDigit(value[i + 1])
                val low = hexDigit(value[i + 2])
                if (high >= 0 && low >= 0) {
                    out.add(((high shl 4) or low).toByte())
                    i += 3
                    continue
                }
            }
            (if (c == '+') " " else c.toString()).encodeToByteArray().forEach { out.add(it) }
            i++
        }
        return out.toByteArray().decodeToString()
    }

    private fun hexDigit(c: Char): Int =
        when (c) {
            in '0'..'9' -> c - '0'
            in 'a'..'f' -> c - 'a' + 10
            in 'A'..'F' -> c - 'A' + 10
            else -> -1
        }

    private const val HEX = "0123456789ABCDEF"
}

class OpenRouterAuthRequest(
    val callbackUrl: String,
    val verifier: String,
    val state: String,
    /** Where to send the user's browser. */
    val authUrl: String,
)

sealed interface OpenRouterCallback {
    data class Code(
        val code: String,
    ) : OpenRouterCallback

    /** No code: the user said no. */
    data object Denied : OpenRouterCallback

    /** A code that isn't an answer to our request. */
    data object StateMismatch : OpenRouterCallback
}

sealed interface OpenRouterConnectResult {
    data class Connected(
        val key: String,
    ) : OpenRouterConnectResult {
        override fun toString() = "Connected(key=<redacted>)"
    }

    data object Denied : OpenRouterConnectResult

    data object TimedOut : OpenRouterConnectResult

    /** The redirect didn't match, or the exchange failed. */
    data object Failed : OpenRouterConnectResult

    data object Unsupported : OpenRouterConnectResult
}
