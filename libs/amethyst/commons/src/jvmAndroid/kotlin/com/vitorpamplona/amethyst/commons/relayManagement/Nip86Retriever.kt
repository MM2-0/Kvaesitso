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
package com.vitorpamplona.amethyst.commons.relayManagement

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip86RelayManagement.Nip86Client
import com.vitorpamplona.quartz.nip86RelayManagement.rpc.Nip86Request
import com.vitorpamplona.quartz.nip86RelayManagement.rpc.Nip86Response
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.coroutines.executeAsync

private const val CONTENT_TYPE_STRING = "application/nostr+json+rpc"
private val CONTENT_TYPE = CONTENT_TYPE_STRING.toMediaType()

class Nip86Retriever(
    val okHttpClient: (NormalizedRelayUrl) -> OkHttpClient,
) : Nip86Executor {
    override suspend fun execute(
        client: Nip86Client,
        request: Nip86Request,
    ): Nip86Response {
        val jsonBody = client.serializeRequest(request)
        val bodyBytes = jsonBody.encodeToByteArray()
        val authToken =
            try {
                client.buildAuthHeader(bodyBytes)
            } catch (e: SignerExceptions) {
                // Signing the NIP-98 header is up to the user's signer; a rejection is an answer.
                Log.w("Nip86Retriever", "Could not sign the NIP-98 header for ${client.httpUrl}", e)
                return Nip86Response(error = e.message ?: "Could not sign the request")
            }

        val httpRequest =
            Request
                .Builder()
                .url(client.httpUrl)
                .header("Content-Type", CONTENT_TYPE_STRING)
                .header("Accept", CONTENT_TYPE_STRING)
                .header("Authorization", authToken)
                .post(bodyBytes.toRequestBody(CONTENT_TYPE))
                .build()

        val httpClient = okHttpClient(client.relayUrl)

        return withContext(Dispatchers.IO) {
            try {
                httpClient.newCall(httpRequest).executeAsync().use { response ->
                    val body = response.body.string()
                    if (response.code == 401) {
                        Nip86Response(error = "Unauthorized: relay rejected authentication")
                    } else if (!response.isSuccessful) {
                        Nip86Response(error = "HTTP ${response.code}: $body")
                    } else {
                        try {
                            client.parseResponse(body)
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            Log.e("Nip86Retriever", "Failed to parse response ${client.httpUrl}: $body", e)
                            Nip86Response(error = "Failed to parse response: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("Nip86Retriever", "Failed to reach relay ${client.relayUrl.url}", e)
                Nip86Response(error = "Failed to reach relay: ${e.message}")
            }
        }
    }
}
