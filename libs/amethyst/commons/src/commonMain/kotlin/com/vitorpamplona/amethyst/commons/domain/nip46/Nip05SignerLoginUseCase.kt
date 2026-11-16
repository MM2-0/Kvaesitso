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

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip05DnsIdentifiers.Nip05RemoteSigner
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerClientMetadata
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectURI

/**
 * Signing login from a `name@domain` address, through the NIP-46 remote signer its domain
 * advertises (`Nip05Client.loadRemoteSigner`).
 *
 * With a `nostrconnect_url` the signer's page takes our `nostrconnect://` code: the page is handed
 * to [RemoteSignerAuthPrompts] (or [onAuthUrl]) like an `auth_url`, and the signer answers the code.
 * Without one, the request goes straight to the signer with no secret, and the signer asks the user
 * to approve through `auth_url`. Either way the account must be the one the address names.
 */
object Nip05SignerLoginUseCase {
    /** The signer signs for another account than the address names. */
    class SignerMismatchException(
        val expected: HexKey,
        val actual: HexKey,
    ) : Exception("The signer for this address signs for a different account")

    /** A finished login and the `bunker://` address (no secret) to save for it. */
    class Nip05Login(
        val result: BunkerLoginResult,
        val bunkerUri: String,
    )

    suspend fun execute(
        signer: Nip05RemoteSigner,
        transport: KeyPair,
        client: INostrClient,
        clientMetadata: BunkerClientMetadata? = null,
        authPrompts: RemoteSignerAuthPrompts? = null,
        /** Also told of every page to open, for a caller that shows it its own way (the CLI prints it). */
        onAuthUrl: ((String) -> Unit)? = null,
        /** How long the signer has to finish the login, on either path (its page or a direct connect). */
        connectTimeoutMs: Long = NostrConnectLoginUseCase.NOSTRCONNECT_TIMEOUT_MS,
    ): Nip05Login {
        val login =
            signer.nostrConnectUrl?.let { template ->
                viaSignerPage(signer, template, transport, client, clientMetadata, authPrompts, onAuthUrl, connectTimeoutMs)
            } ?: viaDirectConnect(signer, transport, client, clientMetadata, authPrompts, onAuthUrl, connectTimeoutMs)

        if (login.result.pubKeyHex.lowercase() != signer.userPubKey.lowercase()) {
            login.result.signer.closeSubscription()
            throw SignerMismatchException(signer.userPubKey, login.result.pubKeyHex)
        }
        return login
    }

    private suspend fun viaSignerPage(
        signer: Nip05RemoteSigner,
        template: String,
        transport: KeyPair,
        client: INostrClient,
        clientMetadata: BunkerClientMetadata?,
        authPrompts: RemoteSignerAuthPrompts?,
        onAuthUrl: ((String) -> Unit)?,
        connectTimeoutMs: Long,
    ): Nip05Login {
        val code = NostrConnectLoginUseCase.generateUri(transport, signer.relays.map { it.url }, clientMetadata)
        val page = NostrConnectURI.fillNostrConnectUrl(template, code.uri)
        authPrompts?.report(page)
        onAuthUrl?.invoke(page)
        try {
            val result = NostrConnectLoginUseCase.awaitAndLogin(code, client, authPrompts, connectTimeoutMs, onAuthUrl)
            return Nip05Login(result, NostrConnectURI.buildBunker(result.signer.remotePubkey, code.relays, secret = null))
        } finally {
            authPrompts?.settled(page)
        }
    }

    private suspend fun viaDirectConnect(
        signer: Nip05RemoteSigner,
        transport: KeyPair,
        client: INostrClient,
        clientMetadata: BunkerClientMetadata?,
        authPrompts: RemoteSignerAuthPrompts?,
        onAuthUrl: ((String) -> Unit)?,
        answerTimeoutMs: Long,
    ): Nip05Login {
        // No secret: the address is all the user gave, so the signer confirms it is them (auth_url).
        val bunkerUri = NostrConnectURI.buildBunker(signer.signerPubKey, signer.relays, secret = null)
        val result = BunkerLoginUseCase.execute(bunkerUri, NostrSignerInternal(transport), client, clientMetadata, authPrompts, onAuthUrl, answerTimeoutMs)
        return Nip05Login(result, bunkerUri)
    }
}
