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
import com.vitorpamplona.amethyst.commons.connectedApps.consent.counterpartyLabel
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.napplet.ConsentSubject
import com.vitorpamplona.amethyst.commons.napplet.NappletCapability
import com.vitorpamplona.amethyst.commons.napplet.NappletConsentInfo
import com.vitorpamplona.amethyst.commons.napplet.NappletIdentity
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletRequest
import com.vitorpamplona.amethyst.commons.napplet.resolveNappletMeta
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.napplet_consent_common
import com.vitorpamplona.amethyst.commons.resources.napplet_consent_diff_follow_added
import com.vitorpamplona.amethyst.commons.resources.napplet_consent_diff_follow_one
import com.vitorpamplona.amethyst.commons.resources.napplet_consent_diff_follow_removed
import com.vitorpamplona.amethyst.commons.resources.napplet_consent_diff_follows
import com.vitorpamplona.amethyst.commons.resources.napplet_consent_diff_joiner
import com.vitorpamplona.amethyst.commons.resources.napplet_consent_diff_mute_added
import com.vitorpamplona.amethyst.commons.resources.napplet_consent_diff_mute_one
import com.vitorpamplona.amethyst.commons.resources.napplet_consent_diff_mute_removed
import com.vitorpamplona.amethyst.commons.resources.napplet_consent_diff_mutes
import com.vitorpamplona.amethyst.commons.resources.napplet_consent_diff_no_baseline
import com.vitorpamplona.amethyst.commons.resources.napplet_consent_diff_none
import com.vitorpamplona.amethyst.commons.resources.napplet_consent_diff_relay_added
import com.vitorpamplona.amethyst.commons.resources.napplet_consent_diff_relay_removed
import com.vitorpamplona.amethyst.commons.resources.napplet_consent_diff_relays
import com.vitorpamplona.amethyst.commons.resources.napplet_consent_diff_unfollow_one
import com.vitorpamplona.amethyst.commons.resources.napplet_consent_diff_unmute_one
import com.vitorpamplona.amethyst.commons.resources.napplet_consent_effect_deletes
import com.vitorpamplona.amethyst.commons.resources.napplet_consent_fs_open
import com.vitorpamplona.amethyst.commons.resources.napplet_consent_fs_save
import com.vitorpamplona.amethyst.commons.resources.napplet_consent_get_pubkey
import com.vitorpamplona.amethyst.commons.resources.napplet_consent_identity_read
import com.vitorpamplona.amethyst.commons.resources.napplet_consent_nip44_decrypt
import com.vitorpamplona.amethyst.commons.resources.napplet_consent_nip44_encrypt
import com.vitorpamplona.amethyst.commons.resources.napplet_consent_notify
import com.vitorpamplona.amethyst.commons.resources.napplet_consent_outbox_read
import com.vitorpamplona.amethyst.commons.resources.napplet_consent_pay_amount
import com.vitorpamplona.amethyst.commons.resources.napplet_consent_pay_no_amount
import com.vitorpamplona.amethyst.commons.resources.napplet_consent_publish
import com.vitorpamplona.amethyst.commons.resources.napplet_consent_publish_encrypted
import com.vitorpamplona.amethyst.commons.resources.napplet_consent_publish_preview
import com.vitorpamplona.amethyst.commons.resources.napplet_consent_query
import com.vitorpamplona.amethyst.commons.resources.napplet_consent_resource
import com.vitorpamplona.amethyst.commons.resources.napplet_consent_sign
import com.vitorpamplona.amethyst.commons.resources.napplet_consent_storage
import com.vitorpamplona.amethyst.commons.resources.napplet_consent_upload
import com.vitorpamplona.amethyst.commons.resources.napplet_consent_wants_described
import com.vitorpamplona.amethyst.commons.resources.napplet_fallback_title
import com.vitorpamplona.amethyst.commons.ui.loadPluralStringRes
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.quartz.lightning.LnInvoiceUtil
import com.vitorpamplona.quartz.nip01Core.core.fastForEach
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip09Deletions.DeletionRequestEvent
import com.vitorpamplona.quartz.nip51Lists.muteList.MuteListEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.utils.toLongValue
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource

/**
 * Turns a pending [NappletRequest] into the human-readable [NappletConsentInfo] the consent dialog
 * shows — the applet's title, the capability label, and a per-operation summary (e.g. a note preview
 * or a sat amount). Localized via app resources.
 *
 * Reads [account] only to diff a proposed replaceable list (follows, relays, mutes) against the copy
 * already cached there, so the dialog can say what a signature would actually change. It never signs,
 * mutates, or exposes account state — the values it reads are the user's own public lists.
 */
class NappletConsentSummary(
    private val browserIcons: BrowserIconRegistry,
    private val account: Account,
) {
    suspend fun info(
        identity: NappletIdentity,
        capability: NappletCapability,
        request: NappletRequest,
    ): NappletConsentInfo {
        val untitled = loadStringRes(Res.string.napplet_fallback_title, identity.authorPubKey.take(8))
        val (title, iconUrl) =
            if (identity.authorPubKey == "browser") {
                val host = OmniboxInput.hostOf(identity.identifier) ?: identity.identifier
                host to browserIcons.iconModelFor(host)
            } else {
                resolveNappletMeta(identity.authorPubKey, identity.identifier, untitled)
            }
        val consequence = consequenceFor(request)
        return NappletConsentInfo(
            appletTitle = title,
            coordinate = identity.coordinate,
            // A signing request is described by what it does ("sign you in", "sign a POST request
            // to …"); the capability behind it ("Relays") is plumbing and reads as wrong there.
            capabilityLabel = if (request is NappletRequest.SignEvent || request is NappletRequest.Publish) "" else loadStringRes(capability.labelRes()),
            operationSummary = listOfNotNull(summaryFor(request).ifBlank { null }, consequence?.text).joinToString("\n\n"),
            allowAlways = capability.canGrantAlways,
            iconUrl = iconUrl,
            rawData = rawEventFor(request),
            subject = consequence?.subject,
        )
    }

    /**
     * Pretty-prints the unsigned event behind the consent dialog's "Show Event" toggle. Only the
     * signing requests carry one; everything else has nothing to disclose.
     */
    private fun rawEventFor(request: NappletRequest): String =
        when (request) {
            is NappletRequest.Publish -> rawEvent(request.kind, request.tags, request.content, null)
            is NappletRequest.SignEvent -> rawEvent(request.kind, request.tags, request.content, request.createdAt)
            else -> ""
        }

    private fun rawEvent(
        kind: Int,
        tags: Array<Array<String>>,
        content: String,
        createdAt: Long?,
    ): String =
        buildString {
            append("kind: ").append(kind).append('\n')
            createdAt?.let { append("created_at: ").append(it).append('\n') }
            append("tags:")
            if (tags.isEmpty()) {
                append(" []\n")
            } else {
                append('\n')
                tags.fastForEach { tag -> append("  ").append(tag.joinToString(", ", "[", "]")).append('\n') }
            }
            append("content: ").append(content.ifEmpty { "(empty)" })
        }

    /** A consequence line, plus the single account it is about when the change names exactly one. */
    private data class Consequence(
        val text: String,
        val subject: ConsentSubject? = null,
    )

    /**
     * A plain-language warning for the kinds whose payload lives entirely in the tags. Without this
     * the dialog reads "publish a kind 3 event" while the user is actually about to replace their
     * whole social graph — the summary would be technically true and practically useless.
     */
    private suspend fun consequenceFor(request: NappletRequest): Consequence? =
        when (request) {
            is NappletRequest.Publish -> consequenceFor(request.kind, request.tags)
            is NappletRequest.SignEvent -> consequenceFor(request.kind, request.tags)
            else -> null
        }

    private suspend fun consequenceFor(
        kind: Int,
        tags: Array<Array<String>>,
    ): Consequence? =
        when (kind) {
            ContactListEvent.KIND ->
                diffOf(
                    current = account.kind3FollowList.getFollowListEvent()?.tags,
                    proposed = tags,
                    tagName = "p",
                    template = Res.string.napplet_consent_diff_follows,
                    added = Res.plurals.napplet_consent_diff_follow_added,
                    removed = Res.plurals.napplet_consent_diff_follow_removed,
                    oneAdded = Res.string.napplet_consent_diff_follow_one,
                    oneRemoved = Res.string.napplet_consent_diff_unfollow_one,
                )
            AdvertisedRelayListEvent.KIND ->
                diffOf(
                    current = account.nip65RelayList.getNIP65RelayList()?.tags,
                    proposed = tags,
                    tagName = "r",
                    template = Res.string.napplet_consent_diff_relays,
                    added = Res.plurals.napplet_consent_diff_relay_added,
                    removed = Res.plurals.napplet_consent_diff_relay_removed,
                )
            // Public entries only: a mute list also carries encrypted ones, which are not in `tags`
            // and so cannot be diffed here.
            MuteListEvent.KIND ->
                diffOf(
                    current = account.muteList.getMuteList()?.tags,
                    proposed = tags,
                    tagName = "p",
                    template = Res.string.napplet_consent_diff_mutes,
                    added = Res.plurals.napplet_consent_diff_mute_added,
                    removed = Res.plurals.napplet_consent_diff_mute_removed,
                    oneAdded = Res.string.napplet_consent_diff_mute_one,
                    oneRemoved = Res.string.napplet_consent_diff_unmute_one,
                )
            // Deletions have no prior version to compare against — the tags are the whole request.
            DeletionRequestEvent.KIND ->
                pluralFor(Res.plurals.napplet_consent_effect_deletes, countTag(tags, "e") + countTag(tags, "a"))
                    ?.let { Consequence(it) }
            // Any other kind: nothing to add. "Carries N tags, check the JSON" asked users to read
            // JSON; Show Event is still there for those who want it.
            else -> null
        }

    /**
     * Describes what a proposed replaceable list changes relative to the copy already on the account.
     * A bare total ("a list of 12 accounts") hides the dangerous case: the alarming edit is a list
     * that silently drops 130 follows, and only a diff surfaces that. Falls back to the total when
     * nothing is cached to compare against.
     */
    private suspend fun diffOf(
        current: Array<Array<String>>?,
        proposed: Array<Array<String>>,
        tagName: String,
        template: StringResource,
        added: PluralStringResource,
        removed: PluralStringResource,
        oneAdded: StringResource? = null,
        oneRemoved: StringResource? = null,
    ): Consequence {
        val next = valuesOf(proposed, tagName)
        val previous =
            current?.let { valuesOf(it, tagName) }
                ?: return Consequence(loadPluralStringRes(Res.plurals.napplet_consent_diff_no_baseline, next.size, next.size))

        val addedKeys = next.filter { it !in previous }
        val removedKeys = previous.filter { it !in next }
        if (addedKeys.isEmpty() && removedKeys.isEmpty()) {
            return Consequence(loadStringRes(Res.string.napplet_consent_diff_none))
        }

        // The overwhelmingly common edit is a single follow/unfollow. Naming and picturing that one
        // account is far more use than "follows 1 new account" — the user can tell at a glance
        // whether it is who they expected.
        if (oneAdded != null && addedKeys.size == 1 && removedKeys.isEmpty()) {
            subjectOf(addedKeys.first())?.let { return Consequence(loadStringRes(oneAdded, it.name), it) }
        }
        if (oneRemoved != null && removedKeys.size == 1 && addedKeys.isEmpty()) {
            subjectOf(removedKeys.first())?.let { return Consequence(loadStringRes(oneRemoved, it.name), it) }
        }

        val parts = listOfNotNull(pluralFor(added, addedKeys.size), pluralFor(removed, removedKeys.size))
        val summary =
            if (parts.size == 2) {
                loadStringRes(Res.string.napplet_consent_diff_joiner, parts[0], parts[1])
            } else {
                parts.first()
            }
        return Consequence(loadStringRes(template, summary))
    }

    /** Resolves a pubkey to a name + picture for the dialog, or null when the user isn't cached. */
    private fun subjectOf(pubKey: String): ConsentSubject? {
        val user = account.cache.getUserIfExists(pubKey) ?: return null
        return ConsentSubject(
            pubKey = pubKey,
            name = user.toBestDisplayName(),
            pictureUrl = user.profilePicture(),
        )
    }

    /** The distinct values of every `[tagName, value, …]` tag. */
    private fun valuesOf(
        tags: Array<Array<String>>,
        tagName: String,
    ): Set<String> {
        val out = mutableSetOf<String>()
        tags.fastForEach { if (it.size > 1 && it[0] == tagName) out.add(it[1]) }
        return out
    }

    private suspend fun pluralFor(
        resId: PluralStringResource,
        count: Int,
    ): String? = if (count <= 0) null else loadPluralStringRes(resId, count, count)

    private fun countTag(
        tags: Array<Array<String>>,
        name: String,
    ): Int = tags.count { it.isNotEmpty() && it[0] == name }

    private suspend fun summaryFor(request: NappletRequest): String =
        when (request) {
            is NappletRequest.GetPublicKey -> loadStringRes(Res.string.napplet_consent_get_pubkey)
            is NappletRequest.IdentityRead -> loadStringRes(Res.string.napplet_consent_identity_read)
            is NappletRequest.Publish -> {
                val preview = request.content.take(160).trim()
                if (preview.isEmpty()) {
                    loadStringRes(Res.string.napplet_consent_publish, request.kind)
                } else {
                    loadStringRes(Res.string.napplet_consent_publish_preview, request.kind) + "\n“$preview”"
                }
            }
            is NappletRequest.SignEvent -> {
                val preview = request.content.take(160).trim()
                val described = signRequestSummary(request.kind, request.tags)
                if (described != null) {
                    loadStringRes(Res.string.napplet_consent_wants_described, described)
                } else if (preview.isEmpty()) {
                    loadStringRes(Res.string.napplet_consent_sign, request.kind)
                } else {
                    loadStringRes(Res.string.napplet_consent_sign, request.kind) + "\n“$preview”"
                }
            }
            is NappletRequest.PublishEncrypted -> loadStringRes(Res.string.napplet_consent_publish_encrypted)
            is NappletRequest.Nip44Encrypt -> {
                val preview = request.plaintext.take(160).trim()
                val summary = loadStringRes(Res.string.napplet_consent_nip44_encrypt, counterpartyLabel(request.peer))
                if (preview.isEmpty()) summary else summary + "\n\u201C$preview\u201D"
            }
            // The ciphertext is meaningless to show, so name the counterparty instead — that is
            // the decision the user is actually making ("let this page read messages from X").
            is NappletRequest.Nip44Decrypt -> loadStringRes(Res.string.napplet_consent_nip44_decrypt, counterpartyLabel(request.peer))
            is NappletRequest.QueryEvents, is NappletRequest.Subscribe -> loadStringRes(Res.string.napplet_consent_query)
            is NappletRequest.OutboxGetEvent, is NappletRequest.OutboxQuery, is NappletRequest.OutboxSubscribe, is NappletRequest.OutboxResolveRelays ->
                loadStringRes(Res.string.napplet_consent_outbox_read)
            // The broker shows the template it signs; this is only reached if a host asks with the wrapper.
            is NappletRequest.OutboxPublish -> summaryFor(request.template)
            is NappletRequest.StorageGet, is NappletRequest.StorageSet, is NappletRequest.StorageRemove, is NappletRequest.StorageKeys ->
                loadStringRes(Res.string.napplet_consent_storage)
            is NappletRequest.NotifyCreate -> {
                val preview = request.title.take(80).trim()
                if (preview.isEmpty()) loadStringRes(Res.string.napplet_consent_notify) else loadStringRes(Res.string.napplet_consent_notify) + "\n“$preview”"
            }
            is NappletRequest.NotifyList, is NappletRequest.NotifyDismiss -> loadStringRes(Res.string.napplet_consent_notify)
            is NappletRequest.PayInvoice -> {
                // getAmountInSats returns ZERO (not null, not a throw) for an amountless BOLT11, so a
                // naive read renders "pay 0 sats" — telling the user a payment is free when the amount
                // is in fact unspecified and decided by the payee. Treat non-positive as "no amount".
                val sats = runCatching { LnInvoiceUtil.getAmountInSats(request.invoice).toLongValue() }.getOrNull()
                if (sats != null && sats > 0) {
                    loadPluralStringRes(Res.plurals.napplet_consent_pay_amount, sats.toInt(), sats)
                } else {
                    loadStringRes(Res.string.napplet_consent_pay_no_amount)
                }
            }
            NappletRequest.ResourceInfo, is NappletRequest.ResourceBytes, is NappletRequest.ResourceBytesMany ->
                loadStringRes(Res.string.napplet_consent_resource)
            // info and status never prompt; worded like an upload should one ever be shown.
            is NappletRequest.UploadBlob, NappletRequest.UploadInfo, is NappletRequest.UploadStatus -> loadStringRes(Res.string.napplet_consent_upload)
            // Its actions reach the prompt as the event they publish; only its reads are asked as `common`.
            is NappletRequest.Common -> loadStringRes(Res.string.napplet_consent_common)
            is NappletRequest.FsPick ->
                loadStringRes(if (request.kind == NappletRequest.FsPickKind.SAVE_FILE) Res.string.napplet_consent_fs_save else Res.string.napplet_consent_fs_open)
            // Only picks ask: the rest act on what a pick already handed over.
            is NappletRequest.Fs -> ""
            // Resolved in the broker before consent (negotiation / shell-mediated / cosmetic); never shown.
            is NappletRequest.RegisterAction, is NappletRequest.UnregisterAction, is NappletRequest.ThemeGet -> ""
        }
}
