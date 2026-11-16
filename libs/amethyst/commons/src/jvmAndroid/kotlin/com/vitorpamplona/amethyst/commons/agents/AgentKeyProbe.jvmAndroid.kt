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

import com.vitorpamplona.amethyst.commons.service.http.IRoleBasedHttpClientBuilder
import com.vitorpamplona.quartz.nipFAAgentKeys.tags.AgentKeyTag
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.Request
import okhttp3.coroutines.executeAsync
import java.io.IOException

// The key will pay for the user's AI calls, so it travels the way money calls do (Tor
// included when the user asked for it there).
actual suspend fun IRoleBasedHttpClientBuilder.probeAgentKey(key: AgentKeyTag): AgentKeyProbeResult =
    withContext(Dispatchers.IO) {
        // NIP-FA: never send a key in cleartext to another machine, not even to test it.
        if (!key.isUrlAllowed()) return@withContext AgentKeyProbeResult.Unreachable
        val preset = AgentProviderPresets.forUrl(key.url)
        val keyChecked = preset == null || preset.keyCheckPath != null
        val url = key.apiRoot() + (preset?.keyCheckPath ?: "/models")
        val request =
            try {
                Request
                    .Builder()
                    .url(url)
                    .get()
                    .apply {
                        if (key.hasKey()) header("Authorization", "Bearer ${key.key}")
                        // Anthropic's OpenAI-compatible models list still wants its version header.
                        if (AgentProviderPresets.hostOf(key.url) == "api.anthropic.com") {
                            if (key.hasKey()) header("x-api-key", key.key)
                            header("anthropic-version", "2023-06-01")
                        }
                    }.build()
            } catch (e: IllegalArgumentException) {
                Log.w("AgentKeyProbe", "Invalid URL", e)
                return@withContext AgentKeyProbeResult.Unreachable
            }

        try {
            okHttpClientForMoney(url).newCall(request).executeAsync().use { response ->
                val body = response.body.string()
                when {
                    response.isSuccessful -> AgentKeyProbeResult.Ok(countModels(body), keyChecked)
                    isKeyRejection(response.code, body) -> AgentKeyProbeResult.Rejected(response.code)
                    else -> AgentKeyProbeResult.Failed(response.code)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Log.w("AgentKeyProbe", "No answer from the provider", e)
            AgentKeyProbeResult.Unreachable
        }
    }

/** 401/403, or a 400 whose message is about the key: xAI and Gemini answer a bad key with 400. */
internal fun isKeyRejection(
    code: Int,
    body: String,
): Boolean = code == 401 || code == 403 || (code == 400 && body.contains("api key", ignoreCase = true))

/** The length of `data` in `{"data":[…]}`, the OpenAI models list; null for any other shape. */
internal fun countModels(body: String): Int? =
    try {
        ((Json.parseToJsonElement(body) as? JsonObject)?.get("data") as? JsonArray)?.size
    } catch (e: SerializationException) {
        Log.w("AgentKeyProbe", "Unexpected models list", e)
        null
    }
