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
package com.vitorpamplona.amethyst.commons.napplet.ui

import com.vitorpamplona.amethyst.commons.browser.BrowserIconRegistry
import com.vitorpamplona.amethyst.commons.browser.OmniboxInput
import com.vitorpamplona.amethyst.commons.connectedApps.consent.Nip46ConsentInfoBuilder
import com.vitorpamplona.amethyst.commons.connectedApps.consent.SignerConnectInfo
import com.vitorpamplona.amethyst.commons.connectedApps.consent.SignerConsentInfo
import com.vitorpamplona.amethyst.commons.connectedApps.consent.counterpartyLabel
import com.vitorpamplona.amethyst.commons.connectedApps.consent.sealContents
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerOp
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.napplet.NappletCapability
import com.vitorpamplona.amethyst.commons.napplet.NappletIdentity
import com.vitorpamplona.amethyst.commons.napplet.NappletRecentEncryptions
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletRequest
import com.vitorpamplona.amethyst.commons.napplet.protocol.counterpartyPubKey
import com.vitorpamplona.amethyst.commons.napplet.protocol.toNarrowSignerOp
import com.vitorpamplona.amethyst.commons.napplet.resolveNappletMeta
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.napplet_consent_seal_unknown
import com.vitorpamplona.amethyst.commons.resources.napplet_fallback_title
import com.vitorpamplona.amethyst.commons.resources.napplet_op_seal_message
import com.vitorpamplona.amethyst.commons.resources.napplet_op_seal_message_to
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_allow_always_for
import com.vitorpamplona.amethyst.commons.ui.connectedApps.consent.label
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.kite.KitePretty
import com.vitorpamplona.quartz.nip01Core.kite.KiteWriter
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** Builds the [SignerConsentInfo] needed by the per-op consent dialog. */
suspend fun buildSignerConsentInfo(
    browserIcons: BrowserIconRegistry,
    identity: NappletIdentity,
    op: NostrSignerOp,
    request: NappletRequest,
    account: Account? = null,
    recentEncryptions: NappletRecentEncryptions? = null,
): SignerConsentInfo {
    val untitled = loadStringRes(Res.string.napplet_fallback_title, identity.authorPubKey.take(8))
    val (title, iconUrl) =
        if (identity.authorPubKey == "browser") {
            val host = OmniboxInput.hostOf(identity.identifier) ?: identity.identifier
            host to browserIcons.iconModelFor(host)
        } else {
            resolveNappletMeta(identity.authorPubKey, identity.identifier, untitled)
        }
    // A decrypt grant can be scoped to one conversation: offer "always allow for Alice" next to the
    // broad "always allow", instead of only the all-conversations-forever choice. Mirrors the NIP-46
    // dialog, so the same decision reads the same way whichever surface asked.
    val narrowOp = request.toNarrowSignerOp()

    // The event a sign/publish request would sign, when there is one.
    val (signKind, signTags, signContent) =
        when (request) {
            is NappletRequest.Publish -> Triple(request.kind, request.tags, request.content)
            is NappletRequest.SignEvent -> Triple(request.kind, request.tags, request.content)
            // Encrypted on its way out; the user judges the event it becomes, in plain text.
            is NappletRequest.PublishEncrypted -> Triple(request.kind, request.tags, request.content)
            else -> Triple(null, null, null)
        }
    // A seal's content is ciphertext and it carries no tags; if this broker just encrypted it, show
    // the message and its recipient instead (see NappletRecentEncryptions).
    val seal = if (signKind != null && signContent != null) sealContents(signKind, signContent, recentEncryptions) else null
    val isUnreadableSeal = signKind == SealEvent.KIND && seal == null
    val change = if (signKind != null && signTags != null) listChange(account, signKind, signTags) ?: relayListChange(account, signKind, signTags) ?: deletionChange(signKind, signTags) ?: reportChange(signKind, signTags) ?: profileChange(account, signKind, signTags, signContent ?: "") else null

    val counterparty = seal?.recipient ?: request.counterpartyPubKey()
    // For decrypt this names the counterparty ("read your private messages with Alice").
    val summary =
        when {
            seal != null -> loadStringRes(Res.string.napplet_op_seal_message_to, counterpartyLabel(seal.recipient))
            isUnreadableSeal -> loadStringRes(Res.string.napplet_op_seal_message)
            else -> (signKind?.let { k -> signTags?.let { signRequestSummary(k, it) } }) ?: (narrowOp ?: op).label()
        }
    val preview =
        when {
            seal != null ->
                seal.rumor
                    ?.content
                    ?.take(160)
                    ?.trim() ?: ""
            isUnreadableSeal -> loadStringRes(Res.string.napplet_consent_seal_unknown)
            else -> plainPreview(request)
        }
    val rawData =
        if (seal != null) {
            seal.rumorJson
        } else {
            rawDataFor(request)
        }
    val previewTemplate =
        when {
            // Render the message itself, as it will read in the conversation.
            seal != null -> seal.rumor
            // Ciphertext rendered as a note says nothing: leave the explanation line instead.
            isUnreadableSeal -> null
            request is NappletRequest.Publish -> EventTemplate<Event>(TimeUtils.now(), request.kind, request.tags, request.content)
            request is NappletRequest.SignEvent -> EventTemplate<Event>(request.createdAt, request.kind, request.tags, request.content)
            else -> null
        }
    return SignerConsentInfo(
        appletTitle = title,
        coordinate = identity.coordinate,
        op = op,
        operationSummary = summary,
        contentPreview = preview,
        rawData = rawData,
        iconUrl = iconUrl,
        previewTemplate = previewTemplate,
        counterpartyName = counterparty?.let { counterpartyLabel(it) },
        counterpartyPicture = counterparty?.let { LocalCache.getUserIfExists(it)?.profilePicture() },
        counterpartyPubKey = counterparty,
        narrowOp = narrowOp,
        // Read the pubkey off the narrow op itself: the dialog drops the button unless BOTH halves
        // are present, so deriving them from one value keeps them from disagreeing.
        narrowOpLabel =
            (narrowOp as? NostrSignerOp.DecryptFrom)?.let {
                loadStringRes(Res.string.nip46_signer_allow_always_for, counterpartyLabel(it.counterparty))
            },
        rememberedOpLabel = op.label(),
        changeSummary = change?.text,
        changeIsWarning = change?.warning ?: false,
        // Which identity signs: the broker's account, not necessarily the one the app shows now.
        accountName = account?.userProfile()?.toBestDisplayName(),
        accountPicture = account?.userProfile()?.profilePicture(),
        accountPubKey = account?.pubKey,
    )
}

private fun plainPreview(request: NappletRequest): String =
    when (request) {
        is NappletRequest.Publish -> Nip46ConsentInfoBuilder.signPreview(request.kind, request.tags, request.content)
        is NappletRequest.SignEvent -> Nip46ConsentInfoBuilder.signPreview(request.kind, request.tags, request.content)
        is NappletRequest.PublishEncrypted -> request.content.take(160).trim()
        // Encryption shows the plaintext the page wants sealed; decryption has only ciphertext,
        // which tells the user nothing, so its preview stays empty and the counterparty in
        // rawData carries the meaning.
        is NappletRequest.Nip44Encrypt -> request.plaintext.take(160).trim()
        else -> ""
    }

private fun rawDataFor(request: NappletRequest): String =
    when (request) {
        is NappletRequest.Publish ->
            KiteWriter.toPrettyJson(EventTemplate<Nothing>(TimeUtils.now(), request.kind, request.tags, request.content))
        is NappletRequest.SignEvent ->
            KiteWriter.toPrettyJson(EventTemplate<Nothing>(request.createdAt, request.kind, request.tags, request.content))
        is NappletRequest.PublishEncrypted ->
            prettyJson {
                put("kind", request.kind)
                put("recipient", request.recipient)
                put("encryption", request.encryption)
                putJsonArray("tags") {
                    for (tag in request.tags) addJsonArray { for (item in tag) add(item) }
                }
                put("content", request.content)
            }
        is NappletRequest.Nip44Encrypt ->
            prettyJson {
                put("operation", "nip44.encrypt")
                put("peer", request.peer)
                put("plaintext", request.plaintext)
            }
        is NappletRequest.Nip44Decrypt ->
            prettyJson {
                put("operation", "nip44.decrypt")
                put("peer", request.peer)
                put("ciphertext", request.ciphertext)
            }
        else -> ""
    }

/** A JSON object built in order and laid out the way Quartz lays out events, for the raw-data pane. */
private inline fun prettyJson(build: JsonObjectBuilder.() -> Unit): String = KitePretty.format(buildJsonObject(build).toString())

/**
 * Creates a [SignerConnectInfo] for the first-connect dialog. [declared] is the capability set the
 * connection pre-grants as ALLOW_ALWAYS on accept, so it is surfaced as
 * [SignerConnectInfo.requestedPermissions] — otherwise the dialog would be asking the user to
 * approve a set it never showed them.
 */
suspend fun buildConnectInfo(
    browserIcons: BrowserIconRegistry,
    identity: NappletIdentity,
    declared: Set<NappletCapability> = emptySet(),
    // The account the app connects to, shown so the user knows which identity they hand over.
    account: Account? = null,
): SignerConnectInfo {
    val untitled = loadStringRes(Res.string.napplet_fallback_title, identity.authorPubKey.take(8))
    val (title, iconUrl) =
        if (identity.authorPubKey == "browser") {
            val host = OmniboxInput.hostOf(identity.identifier) ?: identity.identifier
            host to browserIcons.iconModelFor(host)
        } else {
            resolveNappletMeta(identity.authorPubKey, identity.identifier, untitled)
        }
    val domain =
        if (identity.authorPubKey == "browser") {
            OmniboxInput.hostOf(identity.identifier) ?: identity.identifier
        } else {
            identity.identifier.ifBlank { identity.authorPubKey.take(12) + "…" }
        }
    // Only the capabilities that actually get pre-granted are listed; SHELL/THEME never prompt and
    // VALUE is per-use, so listing them would overstate what accepting hands over.
    val preGranted =
        declared
            .filter { it.requiresConsent && !it.requiresPerUseConsent }
            .map { loadStringRes(it.labelRes()) }
            .sorted()
    return SignerConnectInfo(
        appletTitle = title,
        coordinate = identity.coordinate,
        domain = domain,
        iconUrl = iconUrl,
        accountName = account?.userProfile()?.toBestDisplayName(),
        accountPicture = account?.userProfile()?.profilePicture(),
        accountPubKey = account?.pubKey,
        requestedPermissions = preGranted,
    )
}
