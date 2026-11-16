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

import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerOp
import com.vitorpamplona.amethyst.commons.connectedApps.signers.SignerOpGrant
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.utils.TimeUtils

/** Everything the per-operation consent dialog needs to render. */
data class SignerConsentInfo(
    val appletTitle: String,
    val coordinate: String,
    val op: NostrSignerOp,
    val operationSummary: String,
    /** Short excerpt shown in the dialog body (≤ 160 chars). */
    val contentPreview: String,
    /**
     * Full raw content for the "See more" toggle — event JSON for sign/encrypt operations,
     * decrypted plaintext for decrypt (Amethyst decrypts first, then asks permission to expose).
     */
    val rawData: String = "",
    val iconUrl: String? = null,
    /**
     * The account that would sign/encrypt/decrypt, shown as an avatar + name so it's clear which
     * logged-in identity is acting. Null on paths that don't resolve it; [accountPubKey] seeds the
     * robohash avatar fallback when there's no picture.
     */
    val accountName: String? = null,
    val accountPicture: String? = null,
    val accountPubKey: String? = null,
    /**
     * The unsigned event a `sign_event`/publish request would sign, so the dialog can render it as a
     * note preview (what it will look like) in addition to the raw JSON. Null for encrypt/decrypt and
     * non-event ops.
     */
    val previewTemplate: EventTemplate<Event>? = null,
    /**
     * The OTHER party of a decrypt request — whose conversation the app is asking to read — shown as
     * an avatar + name. "X wants to read your messages with Alice" is a categorically different
     * decision from "X wants to read your private messages", so this must reach the dialog.
     * Null for every op that has no counterparty (signing, and the napplet/browser paths).
     */
    val counterpartyName: String? = null,
    val counterpartyPicture: String? = null,
    val counterpartyPubKey: String? = null,
    /**
     * A NARROWER op the dialog may offer to remember instead of [op] — today only
     * [com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerOp.DecryptFrom], i.e.
     * "always allow, but only for this counterparty". Offered ALONGSIDE the broad "Always allow" so
     * the user gets granularity without a prompt per conversation. [narrowOpLabel] is its button text.
     */
    val narrowOp: NostrSignerOp? = null,
    val narrowOpLabel: String? = null,
    /**
     * What an "always" decision on [op] covers, when that is broader than [operationSummary]. The
     * headline can name this request ("send a private message to Vitor"), but the remembered grant
     * is the whole op (every seal), and a deny button must not promise less than it does.
     */
    val rememberedOpLabel: String? = null,
    /**
     * For a people-list update (follow / mute list), what it changes against the list Amethyst
     * already has ("Adds Nind · keeps the other 15"). [changeIsWarning] marks the alarming cases: a
     * wipe, several removals at once, or a current list that hasn't loaded to compare against.
     */
    val changeSummary: String? = null,
    val changeIsWarning: Boolean = false,
    /**
     * When the app asked, so a prompt left open a long time can say the app may have stopped
     * waiting: most web apps give up on a signer after a minute or two, and a signature granted
     * after that is never used.
     */
    val requestedAtMillis: Long = TimeUtils.nowMillis(),
)

/** One per-operation consent request waiting on the user, as the batched sheet renders it. */
typealias PendingConsent = ConsentQueue.Request<SignerConsentInfo, SignerOpGrant>

/**
 * The requests to show together: those for the account of the oldest one. A sheet signs for one
 * account, so requests for another wait for the next sheet. Requests that name no account group
 * together; empty only when nothing is waiting.
 */
fun List<PendingConsent>.oldestAccountGroup(): List<PendingConsent> {
    val oldest = firstOrNull() ?: return emptyList()
    return filter { it.info.accountPubKey == oldest.info.accountPubKey }
}

/** Everything the "Connect to Nostr" dialog needs to render. */
data class SignerConnectInfo(
    val appletTitle: String,
    val coordinate: String,
    val domain: String,
    val iconUrl: String? = null,
    /**
     * The account the app is connecting to, shown as an avatar + name instead of a raw pubkey. When
     * [accountName] is null (e.g. napplet/browser paths that don't resolve it) the dialog falls back
     * to [domain]. [accountPubKey] seeds the robohash avatar fallback when there's no picture.
     */
    val accountName: String? = null,
    val accountPicture: String? = null,
    val accountPubKey: String? = null,
    /**
     * Human-readable permissions the app declared it needs (from a `nostrconnect://…?perms=` offer),
     * shown so the user gives INFORMED consent before those ops are pre-granted. Empty for flows that
     * carry no declaration (bunker connect), which just show the trust picker.
     */
    val requestedPermissions: List<String> = emptyList(),
)
