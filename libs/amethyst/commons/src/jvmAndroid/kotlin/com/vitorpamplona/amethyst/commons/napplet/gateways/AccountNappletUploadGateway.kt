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
package com.vitorpamplona.amethyst.commons.napplet.gateways

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.napplet.NappletUploadGateway
import com.vitorpamplona.amethyst.commons.napplet.NappletUploadResult
import com.vitorpamplona.amethyst.commons.service.upload.BlossomClient
import com.vitorpamplona.crypto.sha2.sha256
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nipB7Blossom.BlossomServerUrl
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient

/**
 * NAP-UPLOAD's `blossom` rail for a logged-in [account]: the bytes go to the account's Blossom servers
 * (kind 10063), in its order, authorized by a kind 24242 upload event the account signs. The napplet
 * never sees the servers, the credentials or the signer.
 *
 * [client] gives the HTTP client for an upload to a URL, so the app's Tor choice for uploads applies.
 */
class AccountNappletUploadGateway(
    private val account: Account,
    client: (url: String) -> OkHttpClient,
    override val maxBytes: Long = NappletUploadGateway.DEFAULT_MAX_BYTES,
) : NappletUploadGateway {
    private val uploader =
        NappletBlossomUploader(
            servers = {
                account.blossomServers
                    .getBlossomServersList()
                    ?.servers()
                    .orEmpty()
            },
            auth = { hash, size, alt -> account.createBlossomUploadAuth(hash, size, alt).toAuthorizationHeader() },
            blossom = { server -> BlossomClient(client(server)) },
        )

    override suspend fun hasServer(): Boolean = uploader.hasServer()

    override suspend fun upload(
        bytes: ByteArray,
        contentType: String,
        filename: String?,
    ): NappletUploadResult? = uploader.upload(bytes, contentType, filename)
}

/**
 * Puts a blob on the first of [servers] that takes it, trying at most [MAX_SERVERS]. A success is
 * reported only for a blob stored under the hash of the bytes sent: a server that reports another
 * hash (it transformed the file) is passed over. A server that refuses the upload but already holds
 * that exact hash counts as storing it; some servers answer a duplicate with an error, and the bytes
 * there are provably these.
 */
class NappletBlossomUploader(
    private val servers: () -> List<String>,
    private val auth: suspend (hash: String, size: Long, alt: String) -> String,
    private val blossom: (server: String) -> BlossomClient,
) {
    fun hasServer(): Boolean = servers().isNotEmpty()

    suspend fun upload(
        bytes: ByteArray,
        contentType: String,
        filename: String?,
    ): NappletUploadResult? {
        val hash = sha256(bytes).toHexKey()
        for (server in servers().take(MAX_SERVERS)) {
            val client = blossom(server)
            try {
                val stored = client.upload(bytes, contentType, server, auth(hash, bytes.size.toLong(), filename ?: "Upload $hash"))
                val url = stored.url
                if (url != null && (stored.sha256 == null || stored.sha256 == hash)) {
                    return NappletUploadResult(url, hash, stored.size ?: bytes.size.toLong(), stored.type ?: contentType)
                }
                Log.w(TAG) { "$server stored ${stored.sha256} for an upload of $hash" }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Upload to $server failed", e)
                if (client.has(hash, server)) return NappletUploadResult(BlossomServerUrl.blob(server, hash), hash, bytes.size.toLong(), contentType)
            }
        }
        return null
    }

    companion object {
        private const val TAG = "NappletUpload"

        /** Servers one upload tries before it fails. */
        const val MAX_SERVERS = 3
    }
}
