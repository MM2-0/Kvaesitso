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

import com.vitorpamplona.crypto.random.SecureRandom
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.StaticSubscription
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerClientMetadata
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerMessage
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestConnect
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponseAck
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectEvent
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectURI
import com.vitorpamplona.quartz.nip46RemoteSigner.signer.NostrSignerRemote
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException

object NostrConnectLoginUseCase {
    private const val TAG = "NostrConnectLogin"

    /**
     * How long the code waits for a signer, from the moment it shows. Five minutes: the user may
     * first have to clear the signer's queue of other apps' requests and answer whether to
     * replace an existing connection, and two minutes ran out on that path. Cancel ends it sooner.
     */
    const val NOSTRCONNECT_TIMEOUT_MS = 300_000L

    data class NostrConnectUri(
        val uri: String,
        val ephemeralKeyPair: KeyPair,
        val ephemeralSigner: NostrSignerInternal,
        val relays: Set<NormalizedRelayUrl>,
        val secret: String,
    )

    data class ConnectRequestData(
        val requestId: String?,
        val signerPubkey: HexKey,
        val userPubkey: HexKey,
    )

    fun generateUri(
        ephemeralKeyPair: KeyPair,
        relays: List<String>,
        clientMetadata: BunkerClientMetadata?,
        /** NIP-46 `perms`: the operations to ask the signer to pre-approve, comma-separated. */
        perms: String? = null,
    ): NostrConnectUri {
        val ephemeralSigner = NostrSignerInternal(ephemeralKeyPair)
        val ephemeralPubKey = ephemeralKeyPair.pubKey.toHexKey()
        val secret = generateSecret()

        // Normalized, so the pool keys these relays the way it keys them everywhere else, and
        // percent-encoded, as NIP-46 writes them.
        val normalizedRelays = relays.mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }.toSet()
        // The signer's approval screen and its list of connected apps show this name and icon.
        val uri =
            NostrConnectURI.buildNostrConnect(
                clientPubKey = ephemeralPubKey,
                relays = normalizedRelays,
                secret = secret,
                perms = perms,
                name = clientMetadata?.name,
                url = clientMetadata?.url,
                image = clientMetadata?.image,
            )

        return NostrConnectUri(
            uri = uri,
            ephemeralKeyPair = ephemeralKeyPair,
            ephemeralSigner = ephemeralSigner,
            relays = normalizedRelays,
            secret = secret,
        )
    }

    suspend fun awaitAndLogin(
        uriData: NostrConnectUri,
        client: INostrClient,
        authPrompts: RemoteSignerAuthPrompts? = null,
        /** How long to wait for a signer to answer the code. */
        connectTimeoutMs: Long = NOSTRCONNECT_TIMEOUT_MS,
        /** Also told of each `auth_url` page, for a caller that shows it its own way (the CLI prints it). */
        onAuthUrl: ((String) -> Unit)? = null,
    ): BunkerLoginResult {
        val connectData =
            waitForConnectRequest(
                ephemeralSigner = uriData.ephemeralSigner,
                ephemeralPubKey = uriData.ephemeralKeyPair.pubKey.toHexKey(),
                relays = uriData.relays,
                expectedSecret = uriData.secret,
                client = client,
                timeoutMs = connectTimeoutMs,
            )

        if (connectData.requestId != null) {
            sendAckResponse(uriData.ephemeralSigner, connectData, uriData.relays, client)
        }

        val remoteSigner =
            NostrSignerRemote(
                signer = uriData.ephemeralSigner,
                remotePubkey = connectData.signerPubkey,
                relays = uriData.relays,
                client = client,
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
        remoteSigner.openSubscription()
        Log.d(TAG) { "Signer ${connectData.signerPubkey.take(8)} answered the code; asking it for the account's key" }

        try {
            return BunkerLoginResult(remoteSigner, remoteSigner.getPublicKey())
        } catch (e: Throwable) {
            // Cancellation included: nobody else holds this signer to close it.
            remoteSigner.closeSubscription()
            throw e
        }
    }

    private suspend fun waitForConnectRequest(
        ephemeralSigner: NostrSignerInternal,
        ephemeralPubKey: HexKey,
        relays: Set<NormalizedRelayUrl>,
        expectedSecret: String,
        client: INostrClient,
        timeoutMs: Long,
    ): ConnectRequestData {
        // Every event for the code's key, in order: the first one is not necessarily the signer's.
        val events = Channel<NostrConnectEvent>(Channel.UNLIMITED)

        val subscription =
            StaticSubscription(
                client,
                relays.associateWith {
                    listOf(
                        Filter(
                            kinds = listOf(NostrConnectEvent.KIND),
                            tags = mapOf("p" to listOf(ephemeralPubKey)),
                            since = TimeUtils.now() - 60,
                        ),
                    )
                },
            ) { event ->
                if (event is NostrConnectEvent) events.trySend(event)
            }

        try {
            // Not withTimeout: its TimeoutCancellationException reads as a cancellation to
            // callers, so the login screen would never show that the wait ended.
            return withTimeoutOrNull(timeoutMs) {
                for (event in events) {
                    readConnect(event, ephemeralSigner, expectedSecret)?.let { return@withTimeoutOrNull it }
                }
                null
            } ?: throw Exception("No signer answered the connection request in time")
        } finally {
            subscription.close()
            events.close()
        }
    }

    /**
     * The signer's answer to the code, or null for an event that is not one. Anyone who sees the
     * code, or the relay's subscription to its key, can write to that key: only an answer that
     * carries the code's secret (NIP-46) is the signer the user approved in. Anything else is
     * ignored rather than ending the login, so a stray event can neither pose as the signer
     * (and receive the account's encryption requests) nor fail the wait for the real one.
     */
    private suspend fun readConnect(
        event: NostrConnectEvent,
        ephemeralSigner: NostrSignerInternal,
        expectedSecret: String,
    ): ConnectRequestData? {
        val signerPubkey = event.talkingWith(ephemeralSigner.pubKey)
        val message =
            try {
                OptimizedJsonMapper.fromJsonTo<BunkerMessage>(ephemeralSigner.decrypt(event.content, signerPubkey))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Not encrypted to this code, or not NIP-46.
                Log.d(TAG) { "Ignored an event for the code that is not a NIP-46 message: ${e::class.simpleName}" }
                return null
            }

        return when (message) {
            is BunkerRequest -> {
                if (message.method != BunkerRequestConnect.METHOD_NAME) return null
                if (message.params.getOrNull(1) != expectedSecret) {
                    Log.d(TAG) { "Ignored a connect request for the code without its secret" }
                    return null
                }
                val userPubkey = message.params.getOrNull(0) ?: return null
                ConnectRequestData(
                    requestId = message.id,
                    signerPubkey = signerPubkey,
                    userPubkey = userPubkey,
                )
            }

            is BunkerResponse -> {
                // A refusal carries no secret to check; it only ends this attempt.
                if (message.error != null) throw Exception("Signer rejected connection: ${message.error}")
                if (message.result != expectedSecret) {
                    Log.d(TAG) { "Ignored a response for the code without its secret" }
                    return null
                }
                // The account's key comes from get_public_key once connected.
                ConnectRequestData(
                    requestId = null,
                    signerPubkey = signerPubkey,
                    userPubkey = signerPubkey,
                )
            }

            else -> {
                null
            }
        }
    }

    private suspend fun sendAckResponse(
        ephemeralSigner: NostrSignerInternal,
        connectData: ConnectRequestData,
        relays: Set<NormalizedRelayUrl>,
        client: INostrClient,
    ) {
        val ackResponse = BunkerResponseAck(id = connectData.requestId!!)
        val ackEvent =
            NostrConnectEvent.create(
                message = ackResponse,
                remoteKey = connectData.signerPubkey,
                signer = ephemeralSigner,
            )
        client.publish(ackEvent, relays)
    }

    private fun generateSecret(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return bytes.toHexKey()
    }
}
