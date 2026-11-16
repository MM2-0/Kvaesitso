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
package com.vitorpamplona.amethyst.connectedApps.consent

import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.connectedApps.consent.Nip46ConsentInfoBuilder
import com.vitorpamplona.amethyst.commons.connectedApps.consent.Nip46ConsentStrings
import com.vitorpamplona.amethyst.commons.connectedApps.consent.SignerConnectInfo
import com.vitorpamplona.amethyst.commons.connectedApps.consent.SignerFace
import com.vitorpamplona.amethyst.commons.connectedApps.nip46.Nip46PermissionAuthorizer
import com.vitorpamplona.amethyst.commons.connectedApps.signers.AppConnectResult
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerOp
import com.vitorpamplona.amethyst.commons.connectedApps.signers.SignerOpGrant
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.nip46Signer.Nip46ConsentPrompter
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_allow_always_for
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_decrypt_failed
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_remote_app
import com.vitorpamplona.amethyst.commons.ui.connectedApps.consent.label
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestConnect
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestNip04Decrypt
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestNip44Decrypt
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Bridges the (KMP, headless) [com.vitorpamplona.amethyst.commons.connectedApps.nip46.Nip46PermissionAuthorizer]
 * to the interactive consent UI. It reuses the SAME dialogs the napplet/browser signer path uses —
 * [SignerConnectCoordinator] (first-connect trust picker) and [SignerConsentCoordinator]
 * (per-operation allow/deny) — so a NIP-46 remote app prompts through one consistent surface, and
 * the user's "remember" choices land in the same [com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerPermissionLedger].
 *
 * Runs only in the main process (the signer never runs in `:napplet`), so [Amethyst.instance] is set;
 * the coordinators launch their Activity from the application context.
 */
object Nip46ConsentBridge : Nip46ConsentPrompter {
    /**
     * Upper bound on how long a consent prompt may block the signer's single-consumer loop. A user who
     * ignores the dialog eventually fails the request closed (deny / declined) instead of wedging the
     * signer for every other client whose requests queue behind that one blocked prompt.
     */
    private const val CONSENT_TIMEOUT_MS = 120_000L

    /** First-connect consent: show the app's self-declared identity and let the user pick a trust level. */
    override suspend fun requestConnect(
        coordinate: String,
        clientPubKey: HexKey,
        request: BunkerRequestConnect,
    ): AppConnectResult {
        val context = Amethyst.instance.appContext
        val meta = request.clientMetadata
        val title = meta?.name?.ifBlank { null } ?: loadStringRes(Res.string.nip46_signer_remote_app)
        val domain = meta?.url?.ifBlank { null } ?: (clientPubKey.take(12) + "…")
        // The identity being connected to lives in the coordinate; show it as an avatar + name.
        val face = accountFace(coordinate)
        val info =
            SignerConnectInfo(
                appletTitle = title,
                coordinate = coordinate,
                domain = domain,
                iconUrl = meta?.image,
                accountName = face.name,
                accountPicture = face.picture,
                accountPubKey = face.pubKey,
            )
        // Fail closed (declined) if the prompt is never answered, so a stuck first-connect dialog can't
        // hold the single-consumer loop hostage against every other client.
        return withTimeoutOrNull(CONSENT_TIMEOUT_MS) {
            SignerConnectCoordinator.requestConnect(context, info)
        } ?: AppConnectResult.Cancelled
    }

    /**
     * First-connect consent for the client-initiated (`nostrconnect://`) flow: like [requestConnect]
     * but built from the pasted/scanned offer, and — crucially — it surfaces the app's declared
     * [requestedOps] so the user gives informed consent before those ops are pre-granted. Returns the
     * user's [AppConnectResult] (or [AppConnectResult.Cancelled] if the prompt is never answered).
     */
    override suspend fun requestNostrConnectConsent(
        coordinate: String,
        name: String?,
        url: String?,
        image: String?,
        requestedOps: List<NostrSignerOp>,
    ): AppConnectResult {
        val context = Amethyst.instance.appContext
        val title = name?.ifBlank { null } ?: loadStringRes(Res.string.nip46_signer_remote_app)
        val domain = url?.ifBlank { null } ?: (Nip46PermissionAuthorizer.clientPubKeyOf(coordinate)?.take(12)?.plus("…") ?: "")
        val face = accountFace(coordinate)
        val info =
            SignerConnectInfo(
                appletTitle = title,
                coordinate = coordinate,
                domain = domain,
                iconUrl = image,
                accountName = face.name,
                accountPicture = face.picture,
                accountPubKey = face.pubKey,
                requestedPermissions = requestedOps.map { it.label() },
            )
        return withTimeoutOrNull(CONSENT_TIMEOUT_MS) {
            SignerConnectCoordinator.requestConnect(context, info)
        } ?: AppConnectResult.Cancelled
    }

    /**
     * Per-operation consent: describe the request and await the user's grant.
     *
     * For a decrypt request this DECRYPTS FIRST and shows the resulting plaintext, together with the
     * counterparty the conversation is with. That is what makes the decision reviewable: without it
     * the dialog said only "wants to read your private messages" with no way to tell one request from
     * another. Decryption is local — [signer] runs on this device and nothing leaves it unless the
     * user approves — and it is bounded by [Nip46ConsentInfoBuilder.DECRYPT_PREVIEW_TIMEOUT_MS] so a slow or failing signer
     * degrades to an explanatory message instead of hanging or blanking the prompt.
     */
    override suspend fun requestOp(
        coordinate: String,
        clientPubKey: HexKey,
        op: NostrSignerOp,
        request: BunkerRequest,
        signer: NostrSigner,
    ): SignerOpGrant {
        val context = Amethyst.instance.appContext
        val info = runCatching { Amethyst.instance.nip46ClientStore.load(coordinate) }.getOrNull()
        val title = info?.name?.ifBlank { null } ?: loadStringRes(Res.string.nip46_signer_remote_app)

        val consentInfo =
            Nip46ConsentInfoBuilder.build(
                coordinate = coordinate,
                title = title,
                iconUrl = info?.image,
                op = op,
                request = request,
                account = accountFace(coordinate),
                faceOf = ::userFace,
                strings =
                    Nip46ConsentStrings(
                        opLabel = { it.label() },
                        allowAlwaysFor = { loadStringRes(Res.string.nip46_signer_allow_always_for, it) },
                        decryptFailed = loadStringRes(Res.string.nip46_signer_decrypt_failed),
                    ),
                decrypt = { decryptWithAccountSigner(signer, it) },
            )
        // Fail closed if the prompt is never answered so a stuck dialog can't hold the signer hostage.
        return withTimeoutOrNull(CONSENT_TIMEOUT_MS) {
            SignerConsentCoordinator.requestConsent(context, consentInfo)
        } ?: SignerOpGrant.DenyOnce
    }

    /**
     * Performs the local decryption behind the decrypt preview with the account's own signer. Errors
     * and timeouts are handled by [Nip46ConsentInfoBuilder]; this only maps the request to a call.
     */
    private suspend fun decryptWithAccountSigner(
        signer: NostrSigner,
        request: BunkerRequest,
    ): String? =
        when (request) {
            is BunkerRequestNip04Decrypt -> signer.nip04Decrypt(request.ciphertext, request.pubKey)
            is BunkerRequestNip44Decrypt -> signer.nip44Decrypt(request.ciphertext, request.pubKey)
            else -> null
        }

    /** The account being signed for (avatar + name), resolved from the coordinate's signer pubkey. */
    private fun accountFace(coordinate: String): SignerFace {
        val pubKey = Nip46PermissionAuthorizer.signerPubKeyOf(coordinate)
        val user = pubKey?.let { LocalCache.getUserIfExists(it) }
        return SignerFace(name = user?.toBestDisplayName(), picture = user?.profilePicture(), pubKey = pubKey)
    }

    /** Cached profile for a counterparty; the builder supplies the shortened-npub fallback. */
    private fun userFace(pubKey: HexKey): SignerFace {
        val user = LocalCache.getUserIfExists(pubKey)
        return SignerFace(name = user?.toBestDisplayName(), picture = user?.profilePicture(), pubKey = pubKey)
    }
}
