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
package com.vitorpamplona.amethyst.commons.connectedApps.consent

import com.vitorpamplona.amethyst.commons.connectedApps.nip46.Nip46PermissionAuthorizer.Companion.decryptCounterparty
import com.vitorpamplona.amethyst.commons.connectedApps.nip46.Nip46PermissionAuthorizer.Companion.toNarrowSignerOp
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerOp
import com.vitorpamplona.amethyst.commons.relayClient.auth.relayAuthTargets
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.kite.KiteWriter
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip42RelayAuth.RelayAuthEvent
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestSign
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/** Avatar + display name for one pubkey, as the consent dialogs render it. */
data class SignerFace(
    val name: String?,
    val picture: String?,
    val pubKey: String?,
)

/**
 * The user-visible strings the builder needs, injected rather than read from `R.string` so the
 * builder itself carries no Android dependency and can be unit-tested.
 */
class Nip46ConsentStrings(
    /** Human-readable label for an op, e.g. "read your private messages with Alice". */
    val opLabel: suspend (NostrSignerOp) -> String,
    /** Button text for the counterparty-scoped grant; the argument is the counterparty's name. */
    val allowAlwaysFor: suspend (String) -> String,
    /** Shown as the preview when Amethyst itself could not decrypt the message. */
    val decryptFailed: String,
)

/**
 * Builds the [SignerConsentInfo] for one NIP-46 per-operation prompt.
 *
 * Split out of [Nip46ConsentBridge] (which owns the Android `Context`/`LocalCache` lookups) so the
 * decisions that matter for safety are testable without an emulator:
 *  - a decrypt request is DECRYPTED FIRST and the plaintext becomes the preview, honouring the
 *    contract the dialog documented but never implemented;
 *  - a decrypt that cannot be decrypted still produces a populated dialog, never a blank one;
 *  - the counterparty label is never empty — it degrades to a shortened npub, never to nothing.
 */
object Nip46ConsentInfoBuilder {
    /** Characters of plaintext/content shown inline before the "show more" toggle takes over. */
    const val PREVIEW_MAX_CHARS = 160

    /**
     * Upper bound on the pre-consent decryption. Short on purpose: the preview is a nicety, the
     * prompt is not, so a signer that stalls (e.g. an external NIP-55 app that is not responding)
     * must not delay the dialog.
     */
    const val DECRYPT_PREVIEW_TIMEOUT_MS = 8_000L

    suspend fun build(
        coordinate: String,
        title: String,
        iconUrl: String?,
        op: NostrSignerOp,
        request: BunkerRequest,
        account: SignerFace,
        /** Resolves a pubkey to a cached profile; the builder supplies its own npub fallback. */
        faceOf: (HexKey) -> SignerFace,
        strings: Nip46ConsentStrings,
        /** Performs the local decryption. May fail, return null, or hang — all are handled. */
        decrypt: suspend (BunkerRequest) -> String?,
    ): SignerConsentInfo {
        val counterparty = request.decryptCounterparty()
        val plaintext = if (counterparty != null) decryptPreview(request, decrypt, strings.decryptFailed) else null

        val preview =
            when {
                request is BunkerRequestSign -> signPreview(request.event.kind, request.event.tags, request.event.content)
                plaintext != null -> plaintext.take(PREVIEW_MAX_CHARS).trim()
                else -> ""
            }
        val rawData =
            when {
                request is BunkerRequestSign -> KiteWriter.toPrettyJson(request.event)
                // Only worth a "show more" toggle when the preview actually truncated it.
                plaintext != null && plaintext.length > PREVIEW_MAX_CHARS -> plaintext
                else -> ""
            }

        // A decrypt grant can be scoped to one conversation: offer "always allow for Alice" next to
        // the broad "always allow", instead of only the all-conversations-forever choice.
        val narrowOp = request.toNarrowSignerOp()
        val counterpartyFace = counterparty?.let { face(it, faceOf) }

        return SignerConsentInfo(
            appletTitle = title,
            coordinate = coordinate,
            op = op,
            // For decrypt this names the counterparty ("read your private messages with Alice").
            operationSummary = strings.opLabel(narrowOp ?: op),
            contentPreview = preview,
            rawData = rawData,
            iconUrl = iconUrl,
            accountName = account.name,
            accountPicture = account.picture,
            accountPubKey = account.pubKey,
            previewTemplate = (request as? BunkerRequestSign)?.event,
            counterpartyName = counterpartyFace?.name,
            counterpartyPicture = counterpartyFace?.picture,
            counterpartyPubKey = counterparty,
            narrowOp = narrowOp,
            narrowOpLabel = counterpartyFace?.name?.let { strings.allowAlwaysFor(it) },
        )
    }

    /**
     * The one-line preview of an event an app asks to sign. Usually its content; a NIP-42 relay
     * login has none, so it names the relay(s) instead — otherwise a batched row reads as a blank
     * "sign Relay Auth" with nothing to say where the user is logging in.
     */
    fun signPreview(
        kind: Int,
        tags: TagArray,
        content: String,
    ): String =
        if (kind == RelayAuthEvent.KIND) {
            tags.relayAuthTargets().joinToString(", ") { it.display }
        } else {
            content.take(PREVIEW_MAX_CHARS).trim()
        }

    /**
     * Decrypts the message the app asked to read. Never throws and never hangs: a signer that fails,
     * refuses, returns nothing, or takes too long yields [failureText], because a request whose
     * ciphertext we cannot even read is itself worth showing — a blank dialog is not.
     */
    private suspend fun decryptPreview(
        request: BunkerRequest,
        decrypt: suspend (BunkerRequest) -> String?,
        failureText: String,
    ): String =
        withTimeoutOrNull(DECRYPT_PREVIEW_TIMEOUT_MS) {
            try {
                decrypt(request)?.ifBlank { null }
            } catch (e: CancellationException) {
                // Includes this block's own timeout — must propagate so withTimeoutOrNull sees it.
                throw e
            } catch (e: Exception) {
                Log.w("NIP46Signer") { "decrypt preview failed: ${e.message}" }
                null
            }
        } ?: failureText

    /** [faceOf], but with a guaranteed non-blank name (shortened npub when the user isn't cached). */
    private fun face(
        pubKey: HexKey,
        faceOf: (HexKey) -> SignerFace,
    ): SignerFace {
        val resolved = runCatching { faceOf(pubKey) }.getOrNull()
        return SignerFace(
            name = resolved?.name?.ifBlank { null } ?: shortIdentifier(pubKey),
            picture = resolved?.picture,
            pubKey = pubKey,
        )
    }

    /** A shortened npub for an uncached pubkey; falls back to the hex prefix if it isn't valid hex. */
    fun shortIdentifier(pubKey: HexKey): String {
        val npub = runCatching { NPub.create(pubKey) }.getOrNull()
        return if (!npub.isNullOrBlank()) npub.take(12) + "…" else pubKey.take(12) + "…"
    }
}
