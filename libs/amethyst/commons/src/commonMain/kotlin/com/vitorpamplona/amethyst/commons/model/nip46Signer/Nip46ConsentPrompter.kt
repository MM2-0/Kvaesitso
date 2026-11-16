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
package com.vitorpamplona.amethyst.commons.model.nip46Signer

import com.vitorpamplona.amethyst.commons.connectedApps.signers.AppConnectResult
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerOp
import com.vitorpamplona.amethyst.commons.connectedApps.signers.SignerOpGrant
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestConnect

/**
 * Asks the user whether a NIP-46 client may connect or perform an operation. [Nip46SignerState]
 * calls it whenever the permission ledger says ASK; each platform shows its own dialog. Every
 * method suspends until the user answers, and must fail closed ([AppConnectResult.Cancelled],
 * [SignerOpGrant.DenyOnce]) if they never do.
 */
interface Nip46ConsentPrompter {
    /** First connect over `bunker://`: the client's self-declared identity, and a trust level to pick. */
    suspend fun requestConnect(
        coordinate: String,
        clientPubKey: HexKey,
        request: BunkerRequestConnect,
    ): AppConnectResult

    /** First connect over `nostrconnect://`: like [requestConnect], showing the [requestedOps] it pre-grants. */
    suspend fun requestNostrConnectConsent(
        coordinate: String,
        name: String?,
        url: String?,
        image: String?,
        requestedOps: List<NostrSignerOp>,
    ): AppConnectResult

    /** One operation. [signer] is the account's own, so a decrypt can be previewed locally before the user decides. */
    suspend fun requestOp(
        coordinate: String,
        clientPubKey: HexKey,
        op: NostrSignerOp,
        request: BunkerRequest,
        signer: NostrSigner,
    ): SignerOpGrant

    /** Declines everything, as an unanswered prompt does (previews, tests). */
    object Unanswered : Nip46ConsentPrompter {
        override suspend fun requestConnect(
            coordinate: String,
            clientPubKey: HexKey,
            request: BunkerRequestConnect,
        ): AppConnectResult = AppConnectResult.Cancelled

        override suspend fun requestNostrConnectConsent(
            coordinate: String,
            name: String?,
            url: String?,
            image: String?,
            requestedOps: List<NostrSignerOp>,
        ): AppConnectResult = AppConnectResult.Cancelled

        override suspend fun requestOp(
            coordinate: String,
            clientPubKey: HexKey,
            op: NostrSignerOp,
            request: BunkerRequest,
            signer: NostrSigner,
        ): SignerOpGrant = SignerOpGrant.DenyOnce
    }
}
