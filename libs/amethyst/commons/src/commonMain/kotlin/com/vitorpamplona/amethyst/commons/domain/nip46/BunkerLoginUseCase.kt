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
package com.vitorpamplona.amethyst.commons.domain.nip46

import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerClientMetadata
import com.vitorpamplona.quartz.nip46RemoteSigner.signer.NostrSignerRemote
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

object BunkerLoginUseCase {
    const val RELAY_CONNECT_TIMEOUT_MS = 15_000L

    suspend fun execute(
        bunkerUri: String,
        ephemeralSigner: NostrSignerInternal,
        client: INostrClient,
        clientMetadata: BunkerClientMetadata? = null,
        authPrompts: RemoteSignerAuthPrompts? = null,
        /** Also told of each `auth_url` page, for a caller that shows it its own way (the CLI prints it). */
        onAuthUrl: ((String) -> Unit)? = null,
        /**
         * The most the whole login may take: reaching the signer's relays, then its answers to
         * `connect` and `get_public_key`. Null leaves the relays [RELAY_CONNECT_TIMEOUT_MS] and the
         * answers to [NostrSignerRemote], which waits 65 s per request and retries once.
         */
        answerTimeoutMs: Long? = null,
    ): BunkerLoginResult {
        val remoteSigner =
            NostrSignerRemote.fromBunkerUri(
                bunkerUri,
                ephemeralSigner,
                client,
                clientMetadata = clientMetadata,
                onAuthUrl =
                    if (authPrompts == null && onAuthUrl == null) {
                        null
                    } else {
                        { url ->
                            authPrompts?.report(url)
                            onAuthUrl?.invoke(url)
                        }
                    },
                onAuthUrlSettled = authPrompts?.let { prompts -> { url -> prompts.settled(url) } },
            )
        if (remoteSigner.relays.isEmpty()) throw Exception("The bunker address names no relay")
        remoteSigner.openSubscription()

        val started = TimeSource.Monotonic.markNow()

        // What is left of answerTimeoutMs, but never more than cap. Long.MAX_VALUE is no limit.
        fun budgetMs(cap: Long) = answerTimeoutMs?.let { minOf(cap, it - started.elapsedNow().inWholeMilliseconds) } ?: cap

        try {
            // Wait for websocket to be ready before sending connect request. Not withTimeout: its
            // TimeoutCancellationException reads as a cancellation to callers, who rethrow it, so
            // the login screen never learns the attempt is over and keeps spinning.
            withTimeoutOrNull(budgetMs(RELAY_CONNECT_TIMEOUT_MS)) {
                client.connectedRelaysFlow().first { connected ->
                    remoteSigner.relays.any { it in connected }
                }
            } ?: throw Exception("Could not reach the signer's relays: ${remoteSigner.relays.joinToString { it.url }}")

            val pubKey =
                withTimeoutOrNull(budgetMs(Long.MAX_VALUE)) {
                    remoteSigner.connect()
                    remoteSigner.getPublicKey()
                } ?: throw Exception("Timed out after ${answerTimeoutMs?.milliseconds}")
            return BunkerLoginResult(remoteSigner, pubKey)
        } catch (e: Throwable) {
            // Cancellation included: nobody else holds this signer to close it.
            remoteSigner.closeSubscription()
            throw e
        }
    }
}
