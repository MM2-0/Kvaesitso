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
package com.vitorpamplona.amethyst.commons.napplet

import com.vitorpamplona.amethyst.commons.connectedApps.signers.AppConnectResult
import com.vitorpamplona.amethyst.commons.connectedApps.signers.AppSignerPolicy
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrConnectPrompt
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrOpDecision
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerConsentPrompt
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerOp
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerPermissionLedger
import com.vitorpamplona.amethyst.commons.connectedApps.signers.SignerOpGrant
import com.vitorpamplona.amethyst.commons.napplet.fs.NappletFsError
import com.vitorpamplona.amethyst.commons.napplet.fs.NappletFsSession
import com.vitorpamplona.amethyst.commons.napplet.permissions.GrantState
import com.vitorpamplona.amethyst.commons.napplet.permissions.NappletPermissionLedger
import com.vitorpamplona.amethyst.commons.napplet.permissions.PermissionDecision
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletRequest
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletResponse
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletStorageScope
import com.vitorpamplona.amethyst.commons.napplet.protocol.toNarrowSignerOp
import com.vitorpamplona.amethyst.commons.napplet.protocol.toSignerOp
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip02FollowList.tags.ContactTag
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrlTag
import com.vitorpamplona.quartz.nip56Reports.ReportEvent
import com.vitorpamplona.quartz.nip56Reports.ReportType
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * The trust boundary, expressed as code. The broker is the **only** component that holds a
 * [NostrSigner] and turns an untrusted napplet's [NappletRequest] into a [NappletResponse],
 * gating every dangerous operation through three checks, in order:
 *
 * 1. **Declaration** — the request's capability must appear in the manifest's `requires`
 *    (`declared`). An applet cannot use a capability it never asked for up front.
 * 2. **Ledger** — a standing grant/denial decides without prompting.
 * 3. **Consent** — when no standing decision exists, the user is asked.
 *
 * Two capability-specific policies refine step 2/3:
 * - **Per-use capabilities** ([NappletCapability.requiresPerUseConsent], i.e. [NappletCapability.VALUE])
 *   never auto-approve from a prior grant — every payment is confirmed afresh, with the amount shown.
 * - All signer types — internal, NIP-46 remote, and NIP-55 external — are gated through Amethyst's
 *   consent UI before the operation reaches the signer. External signers add their own per-request
 *   prompt on top (double-prompting), ensuring the user can differentiate requests from different
 *   apps inside the external signer.
 *
 * Security invariants enforced here (never trusted from the applet): the signing identity is
 * always the host's signer; the napplet only ever supplies an unsigned template — the shell signs
 * it and stamps `created_at` from the host clock; a response never contains private key bytes;
 * storage is namespaced per applet coordinate.
 */
class NappletBroker(
    private val signer: NostrSigner,
    private val ledger: NappletPermissionLedger,
    private val consentPrompt: NappletConsentPrompt,
    private val relay: NappletRelayGateway? = null,
    private val storage: NappletStorage? = null,
    private val wallet: NappletWalletGateway? = null,
    private val resource: NappletResourceGateway? = null,
    private val upload: NappletUploadGateway? = null,
    private val identityReads: NappletIdentityGateway? = null,
    private val theme: NappletThemeGateway? = null,
    private val notify: NappletNotifyGateway? = null,
    private val signerLedger: NostrSignerPermissionLedger? = null,
    private val nostrConnectPrompt: NostrConnectPrompt? = null,
    private val signerConsentPrompt: NostrSignerConsentPrompt? = null,
    private val outbox: NappletOutboxGateway? = null,
    // Plaintext of recent nip44.encrypt calls, read back by the consent prompt for the seal that
    // carries the ciphertext (see [NappletRecentEncryptions]).
    private val recentEncryptions: NappletRecentEncryptions? = null,
    // Wall-clock source (ms) for the post-cancel re-prompt cooldown; injectable for tests.
    private val nowMillis: () -> Long = { TimeUtils.nowMillis() },
    // Where a request that needed the user, from an app running inside a note, is recorded instead.
    private val inlineAsks: NappletInlineAsks = NappletInlineAsks.shared,
    // NAP-COMMON: the account's follow list. Reads and events go through [outbox].
    private val common: NappletCommonGateway? = null,
) {
    // Serializes the consent-prompt path so concurrent requests queue into one dialog at a time
    // (see [authorizeWithConsent]). Only the prompt is held here; non-prompting paths and execute()
    // run unserialized.
    private val consentLock = Mutex()

    // Serializes first-connect and per-op signer consent dialogs so concurrent signing requests
    // queue into one dialog at a time rather than launching several dialogs simultaneously.
    private val signerConsentLock = Mutex()

    // In-memory session grants (AllowForSession): cleared when this broker instance is destroyed.
    // Keyed by "<coordinate>|<op.key>" via [sessionKey] — this broker instance is shared by every
    // applet and browser origin under the account, so an unkeyed op would let one app's session
    // grant silently authorize the same op for all the others. Guarded by [sessionLock], not the prompt
    // lock: relay reads ask it for every encrypted event ([mayDecrypt]) and must not wait while a
    // consent dialog is open.
    private val sessionAllows = mutableSetOf<String>()
    private val sessionLock = Mutex()

    // NAP-UPLOAD: each napplet's recent uploads, by "<coordinate>|<uploadId>", for `upload.status`.
    // An upload completes before its result is sent, so this only ever holds finished ones; the
    // oldest go past [MAX_REMEMBERED_UPLOADS].
    private val uploads = LinkedHashMap<String, NappletResponse.Uploaded>()
    private val uploadsLock = Mutex()

    private suspend fun rememberUpload(
        coordinate: String,
        upload: NappletResponse.Uploaded,
    ) = uploadsLock.withLock {
        val key = "$coordinate|${upload.uploadId}"
        uploads.remove(key)
        uploads[key] = upload
        while (uploads.size > MAX_REMEMBERED_UPLOADS) uploads.remove(uploads.keys.first())
    }

    private suspend fun hasSessionGrant(key: String): Boolean = sessionLock.withLock { key in sessionAllows }

    // Apps whose first-connect dialog the user just dismissed with Cancel, mapped to the wall-clock
    // instant (ms) their re-prompt suppression expires. Cancelling means "not now": for a brief
    // cooldown we drop re-prompts so the burst of requests a page/napplet fires on load (relays +
    // storage + identity, all racing) doesn't relaunch the dialog once per request. Once the cooldown
    // lapses the entry is stale, so a fresh request — e.g. the user deliberately re-triggering login —
    // prompts again. (A plain "suppress forever" set used to lock the user out: a single Cancel killed
    // every future prompt for the broker's whole lifetime, and because Cancel persists nothing there
    // was no Connected-Apps entry to clear to recover.)
    // Only accessed under signerConsentLock.
    private val cancelledUntil = mutableMapOf<String, Long>()

    // Per-operation prompts the user just turned down (dismissed or denied once), keyed by
    // "<coordinate>|<op or capability>", with how many times in a row and until when (ms) a new ask is
    // dropped without prompting. Without it a napplet looping `relay.publish` of a follow list would
    // bring the dialog straight back after every dismissal until the user gave in. Guarded by
    // [declinedLock]; the wait doubles with each decline, up to [MAX_DECLINE_BACKOFF_MS].
    private class Declined(
        val count: Int,
        val until: Long,
    )

    private val declined = mutableMapOf<String, Declined>()
    private val declinedLock = Mutex()

    // Requests per app waiting on a consent dialog: past [MAX_WAITING_PROMPTS] a new one is refused at
    // once instead of suspending, so an app can't pile up coroutines behind the dialog.
    private val waiting = mutableMapOf<String, Int>()
    private val waitingLock = Mutex()

    /**
     * Authorizes and runs [request] on behalf of [identity]. [declared] is the capability set the
     * manifest's `requires` resolved to; a request outside it is refused before any prompt.
     * Re-throws [CancellationException]; converts any other failure into [NappletResponse.Failed]
     * so host internals never cross back to the applet process.
     *
     * [files] is the calling surface's NAP-FS session: the picks it holds belong to that one running
     * surface, not to the app, so the host passes its own and this shared broker keeps none.
     */
    suspend fun handle(
        identity: NappletIdentity,
        request: NappletRequest,
        declared: Set<NappletCapability>,
        files: NappletFsSession? = null,
    ): NappletResponse {
        val capability = request.capability

        if (capability !in declared) {
            return NappletResponse.Denied(capability, "This napplet did not declare the '${capability.name.lowercase()}' capability.")
        }

        // A NAP-COMMON action is the event the shell builds for it: from here on it is that publish,
        // gated as `common`, so the consent and signer prompts show the actual event.
        if (request is NappletRequest.CommonAction) {
            return when (val plan = planCommonAction(request)) {
                is CommonPlan.Publish -> handle(identity, plan.request, declared, files)
                is CommonPlan.Answer -> plan.response
            }
        }

        // A standing denial always wins, even for signer-self-gated capabilities.
        if (ledger.decide(identity, capability) == PermissionDecision.DENY) {
            return NappletResponse.Denied(capability, "Blocked by a standing denial.")
        }

        // Show the first-connect dialog if the app has no signer policy yet.
        if (signerLedger != null && !signerLedger.hasPolicy(signerCoordinateFor(identity))) {
            if (!ensureConnected(identity, declared)) {
                return NappletResponse.Denied(capability, "Connection not authorized.")
            }
        }

        // The per-operation signer gate below shows the event itself; for requests it covers, a second,
        // generic "this site wants to use Relays" prompt first only made the user answer twice.
        val signerOp = if (signerLedger != null) request.toSignerOp() else null
        val signerPromptDecides = signerOp != null && signerConsentPrompt != null

        val authorized =
            when {
                // Grants nothing by itself (a keyboard action binding, an fs read or write of what a
                // pick handed over): declared is enough; it never prompts.
                !request.asksConsent -> true
                // Cosmetic capabilities (theme) never prompt.
                !capability.requiresConsent -> true
                // A standing allow short-circuits, except for per-use capabilities (e.g. payments).
                ledger.decide(identity, capability) == PermissionDecision.ALLOW && !capability.requiresPerUseConsent -> true
                // Signing / encryption: one prompt, the signer's. A standing capability DENY was already
                // honored above; per-use capabilities keep their own prompt.
                signerPromptDecides && !capability.requiresPerUseConsent -> true
                else -> authorizeWithConsent(identity, capability, request)
            }

        if (!authorized) return NappletResponse.Denied(capability, "The user declined.")

        // Additional per-operation gate for signing/encryption.
        if (signerLedger != null) {
            val op = signerOp
            if (op != null && !authorizeSignerOp(identity, op, request)) {
                return NappletResponse.Denied(capability, "Signing operation declined.")
            }
        }

        return try {
            execute(identity, request, files)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            NappletResponse.Failed(e.message ?: e::class.simpleName ?: "Unknown error")
        }
    }

    /**
     * Prompts for consent under [consentLock] so several requests arriving at once — the common case,
     * a napplet reading relays + storage + identity on load — queue into one dialog at a time instead
     * of racing. Without this each would launch a consent prompt concurrently; the host can only show
     * one, so the rest are dropped and their calls hang forever.
     *
     * After taking the lock we re-read the standing decision: a sibling request for the same capability
     * may have recorded an Allow/Deny while we waited, so we honor that instead of prompting again.
     * Per-use capabilities (payments) always re-prompt, so they skip the re-check.
     */
    private suspend fun authorizeWithConsent(
        identity: NappletIdentity,
        capability: NappletCapability,
        request: NappletRequest,
    ): Boolean =
        waitingForPrompt(identity.coordinate) {
            consentLock.withLock {
                if (!capability.requiresPerUseConsent) {
                    when (ledger.decide(identity, capability)) {
                        PermissionDecision.DENY -> return@withLock false
                        PermissionDecision.ALLOW -> return@withLock true
                        PermissionDecision.ASK -> {}
                    }
                }
                if (identity.inline) return@withLock refuseInline(identity)
                val declineKey = "${identity.coordinate}|cap:${capability.name}"
                if (isBackingOff(declineKey)) return@withLock false
                val grant = consentPrompt.request(identity, capability, request.consentView())
                ledger.record(identity, capability, effectiveGrant(capability, grant))
                noteAnswer(declineKey, grant.allowsExecution)
                grant.allowsExecution
            }
        }

    /**
     * Runs a NAP-FS request on the surface's [files]. An app running inside a note never opens a
     * picker, even with the app's standing grant: a picker over the feed would come from something the
     * user only scrolled past. The note offers to open the app instead, where it can ask again.
     */
    private suspend fun executeFiles(
        identity: NappletIdentity,
        request: NappletRequest.Fs,
        files: NappletFsSession?,
    ): NappletResponse {
        val session = files ?: return NappletResponse.FsFailure(NappletFsError.UNSUPPORTED)
        if (request.picks && identity.inline) {
            inlineAsks.record(identity)
            return NappletResponse.FsFailure(NappletFsError.POLICY_DENIED)
        }
        return session.execute(request)
    }

    /**
     * What a prompt shows for [this]: an outbox publish is shown as the event it signs, so the user
     * sees and decides it exactly as a relay publish.
     */
    private fun NappletRequest.consentView(): NappletRequest = if (this is NappletRequest.OutboxPublish) template else this

    /**
     * An app running inside a note asked for something only the user can give. Nothing is asked
     * and nothing is recorded against the app: the note offers to open it, where it can ask again.
     */
    private fun refuseInline(identity: NappletIdentity): Boolean {
        inlineAsks.record(identity)
        return false
    }

    /** Downgrades a grant to one-shot when the capability forbids persisting that scope (e.g. payments). */
    private fun effectiveGrant(
        capability: NappletCapability,
        grant: GrantState,
    ): GrantState =
        when {
            grant == GrantState.ALLOW_ALWAYS && !capability.canGrantAlways -> GrantState.ALLOW_ONCE
            grant == GrantState.ALLOW_SESSION && !capability.canGrantSession -> GrantState.ALLOW_ONCE
            else -> grant
        }

    private suspend fun execute(
        identity: NappletIdentity,
        request: NappletRequest,
        files: NappletFsSession?,
    ): NappletResponse =
        when (request) {
            is NappletRequest.GetPublicKey -> NappletResponse.PublicKey(signer.pubKey)

            is NappletRequest.ThemeGet -> {
                val gateway = theme ?: return NappletResponse.Unsupported("theme.get")
                val colors = gateway.current()
                NappletResponse.Theme(colors.background, colors.text, colors.primary)
            }

            is NappletRequest.IdentityRead -> {
                val gateway = identityReads ?: return NappletResponse.Unsupported("identity.${request.method}")
                val raw = gateway.read(request.method, request.argument) ?: return NappletResponse.Unsupported("identity.${request.method}")
                NappletResponse.Json(raw)
            }

            // The napplet supplies an unsigned template; the shell signs and publishes it.
            // created_at comes from the host, never the applet, so it cannot backdate.
            is NappletRequest.Publish -> signAndPublish(request.kind, request.tags, request.content)

            // NIP-07 signEvent: sign as the user (honoring the app's created_at) and return it WITHOUT
            // publishing — the web app sends it to relays itself. pubkey is still fixed by the signer.
            is NappletRequest.SignEvent -> NappletResponse.Published(signer.sign(request.createdAt, request.kind, request.tags, request.content), emptyList())

            is NappletRequest.PublishEncrypted -> {
                val ciphertext =
                    when (request.encryption.trim().lowercase()) {
                        "nip04" -> signer.nip04Encrypt(request.content, request.recipient)
                        else -> signer.nip44Encrypt(request.content, request.recipient)
                    }
                signAndPublish(request.kind, withRecipientTag(request.tags, request.recipient), ciphertext)
            }

            // NIP-07 nip44.encrypt/decrypt: the shell runs the crypto with the real key and hands back
            // only the result, so the page can build its own NIP-59 seals without ever seeing the key.
            is NappletRequest.Nip44Encrypt -> {
                val ciphertext = signer.nip44Encrypt(request.plaintext, request.peer)
                recentEncryptions?.record(ciphertext, request.peer, request.plaintext)
                NappletResponse.Text(ciphertext)
            }

            is NappletRequest.Nip44Decrypt -> NappletResponse.Text(signer.nip44Decrypt(request.ciphertext, request.peer))

            is NappletRequest.QueryEvents -> {
                val gateway = relay ?: return NappletResponse.Unsupported("relay.query")
                if (request.filters.size > MAX_RELAY_FILTERS) return NappletResponse.Failed("too many filters")
                // The gateway hands back events as relays and the cache hold them; what the app may read
                // of their encrypted content is decided here, per counterparty, never by a prompt.
                val mayDecrypt = decryptGate(identity)
                val readable = gateway.query(request.filters).mapNotNull { NappletRelayCleartext.forDelivery(it, signer, mayDecrypt) }
                NappletResponse.Events(readable.take(MAX_QUERY_RESULTS))
            }

            // The broker only authorizes the subscription (consent + declaration); the host opens the
            // live relay subscription and streams relay.event/relay.eose/relay.closed by subId.
            is NappletRequest.OutboxGetEvent -> {
                val gateway = outbox ?: return NappletResponse.Unsupported("outbox.getEvent")
                val read = gateway.getEvent(request.eventId, request.author, request.relays, request.timeoutMs)
                // NAP-OUTBOX: the shell MUST check the event it returns is the one asked for.
                val found = read.events.firstOrNull { it.event.id == request.eventId }
                NappletResponse.OutboxFound(found, read.incomplete, if (found == null) "not found" else null)
            }

            is NappletRequest.OutboxQuery -> {
                val gateway = outbox ?: return NappletResponse.Unsupported("outbox.query")
                if (request.filters.size > NappletOutboxGateway.MAX_FILTERS) return NappletResponse.Failed("too many filters")
                val read = gateway.query(request.filters, request.authors, request.relays, request.timeoutMs)
                // Never more than a read returns, and never a negative count.
                val limit = (request.limit ?: request.filters.mapNotNull { it.limit }.maxOrNull() ?: NappletOutboxGateway.MAX_RESULTS).coerceIn(0, NappletOutboxGateway.MAX_RESULTS)
                NappletResponse.OutboxEvents(read.events.take(limit), read.incomplete)
            }

            // Like relay.subscribe, the broker only authorizes and picks the relays; the host streams.
            is NappletRequest.OutboxSubscribe -> {
                val gateway = outbox ?: return NappletResponse.Unsupported("outbox.subscribe")
                if (request.filters.size > NappletOutboxGateway.MAX_FILTERS) return NappletResponse.Failed("too many filters")
                NappletResponse.OutboxSubscribed(gateway.subscriptionRelays(request.filters, request.authors, request.relays))
            }

            is NappletRequest.OutboxPublish -> {
                val gateway = outbox ?: return NappletResponse.Unsupported("outbox.publish")
                val template = request.template
                // Signed once, whatever the fan-out: NAP-OUTBOX allows one event per publish.
                val signed: Event = signer.sign(TimeUtils.now(), template.kind, template.tags, template.content)
                val delivery = gateway.publish(signed, request.toOutbox, request.toInboxes, request.relays)
                val relays = delivery.relays.mapKeys { it.key.url }
                val error =
                    when {
                        delivery.unresolvedInboxes.isNotEmpty() -> "relay list unavailable"
                        relays.values.none { it } -> "publish failed"
                        else -> null
                    }
                NappletResponse.OutboxPublished(error == null, signed, relays, error)
            }

            is NappletRequest.OutboxResolveRelays -> {
                val gateway = outbox ?: return NappletResponse.Unsupported("outbox.resolveRelays")
                NappletResponse.OutboxPlan(gateway.plan(request.authors, request.write))
            }

            is NappletRequest.Subscribe -> {
                relay ?: return NappletResponse.Unsupported("relay.subscribe")
                if (request.filters.size > MAX_RELAY_FILTERS) return NappletResponse.Failed("too many filters")
                NappletResponse.Subscribed
            }

            is NappletRequest.StorageGet -> {
                val store = storage ?: return NappletResponse.Unsupported("storage.getItem")
                NappletResponse.StorageValue(store.get(storageCoordinate(identity, request.scope), request.key))
            }

            is NappletRequest.StorageSet -> {
                val store = storage ?: return NappletResponse.Unsupported("storage.setItem")
                store.set(storageCoordinate(identity, request.scope), request.key, request.value, quotaCoordinate = identity.storageCoordinate)
                NappletResponse.Done
            }

            is NappletRequest.StorageRemove -> {
                val store = storage ?: return NappletResponse.Unsupported("storage.removeItem")
                store.remove(storageCoordinate(identity, request.scope), request.key)
                NappletResponse.Done
            }

            is NappletRequest.StorageKeys -> {
                val store = storage ?: return NappletResponse.Unsupported("storage.keys")
                NappletResponse.Strings(store.keys(storageCoordinate(identity, request.scope)))
            }

            is NappletRequest.NotifyCreate -> {
                val gateway = notify ?: return NappletResponse.Unsupported("notify.create")
                NappletResponse.NotifyCreated(gateway.create(identity.coordinate, request.title, request.body))
            }

            is NappletRequest.NotifyList -> {
                val gateway = notify ?: return NappletResponse.Unsupported("notify.list")
                NappletResponse.NotifyListed(gateway.list(identity.coordinate))
            }

            is NappletRequest.NotifyDismiss -> {
                val gateway = notify ?: return NappletResponse.Unsupported("notify.dismiss")
                gateway.dismiss(identity.coordinate, request.id)
                NappletResponse.Done
            }

            // Keyboard actions are acknowledged (with the honored binding) so SDK napplets'
            // registerAction() resolves; the host binds the key and pushes keys.action when triggered.
            is NappletRequest.RegisterAction -> NappletResponse.ActionRegistered(request.actionId, request.binding)

            is NappletRequest.UnregisterAction -> NappletResponse.Done

            is NappletRequest.PayInvoice -> {
                val gateway = wallet ?: return NappletResponse.Unsupported("value.payInvoice")
                NappletResponse.Paid(gateway.payInvoice(request.invoice))
            }

            is NappletRequest.ResourceBytes -> {
                val gateway = resource ?: return NappletResponse.Unsupported("resource.bytes")
                when (val fetched = gateway.fetch(request.url, identity.coordinate)) {
                    is NappletResourceResult.Success -> NappletResponse.Bytes(fetched.resource.bytes, fetched.resource.contentType)
                    is NappletResourceResult.Failure -> NappletResponse.ResourceFailure(fetched.error, fetched.message)
                }
            }

            is NappletRequest.ResourceInfo -> {
                resource ?: return NappletResponse.Unsupported("resource.info")
                NappletResponse.ResourceInfo(
                    schemes = listOf("data", "https", "blossom", "nostr"),
                    maxBytes = RESOURCE_MAX_BYTES,
                    maxUrls = RESOURCE_MAX_URLS,
                )
            }

            is NappletRequest.ResourceBytesMany -> {
                val gateway = resource ?: return NappletResponse.Unsupported("resource.bytesMany")
                if (request.urls.isEmpty()) return NappletResponse.ResourceFailure("invalid-request", "Resource URL list is empty.")
                if (request.urls.size > RESOURCE_MAX_URLS) return NappletResponse.ResourceFailure("too-large", "Resource URL limit exceeded.")
                NappletResponse.ResourceItems(
                    request.urls.map { url ->
                        when (val fetched = gateway.fetch(url, identity.coordinate)) {
                            is NappletResourceResult.Success ->
                                NappletResponse.ResourceItem(
                                    url = url,
                                    resource = NappletResponse.Bytes(fetched.resource.bytes, fetched.resource.contentType),
                                )
                            is NappletResourceResult.Failure ->
                                NappletResponse.ResourceItem(url = url, error = fetched.error, message = fetched.message)
                        }
                    },
                )
            }

            is NappletRequest.Fs -> executeFiles(identity, request, files)

            is NappletRequest.UploadBlob -> {
                val gateway = upload ?: return NappletResponse.Unsupported("upload.upload")
                // NAP-UPLOAD's error strings: no upload was created, so the napplet gets one of these.
                if (request.rail != null && request.rail != UPLOAD_RAIL) return NappletResponse.Failed("unsupported rail")
                if (request.bytes.size > gateway.maxBytes) return NappletResponse.Failed("file too large")
                if (!gateway.hasServer()) return NappletResponse.Failed("no server configured")
                val res = gateway.upload(request.bytes, request.contentType, request.filename) ?: return NappletResponse.Failed("upload failed")
                val done = NappletResponse.Uploaded(res.url, res.sha256, res.size, res.mimeType, updatedAt = TimeUtils.now())
                rememberUpload(identity.coordinate, done)
                done
            }

            is NappletRequest.CommonEncodeNip19 -> NappletCommonNip19.encode(request)

            is NappletRequest.CommonDecodeNip19 -> NappletCommonNip19.decode(request.value)

            is NappletRequest.CommonGetProfile -> {
                val reader = outbox ?: return NappletResponse.Unsupported("common.getProfile")
                val (pubkey, hints) = NappletCommonNip19.pubkeyOf(request.target) ?: return NappletResponse.CommonFailure("invalid-profile-target")
                val found = reader.query(listOf(Filter(kinds = listOf(MetadataEvent.KIND), authors = listOf(pubkey), limit = 1)), listOf(pubkey), hints.map { it.url }, COMMON_READ_TIMEOUT_MS)
                NappletResponse.CommonProfile(
                    pubkey,
                    found.events
                        .map { it.event }
                        .filter { it.kind == MetadataEvent.KIND && it.pubKey == pubkey }
                        .maxByOrNull { it.createdAt },
                )
            }

            is NappletRequest.CommonFollows -> {
                val gateway = common ?: return NappletResponse.Unsupported("common.follows")
                NappletResponse.CommonFollowList(gateway.followList()?.verifiedFollowKeySet()?.toList() ?: emptyList())
            }

            // Turned into a Publish in handle() before it gets here.
            is NappletRequest.CommonAction -> NappletResponse.Unsupported("common")

            is NappletRequest.UploadInfo -> {
                val gateway = upload ?: return NappletResponse.Unsupported("upload.info")
                NappletResponse.UploadInfo(enabled = gateway.hasServer(), maxBytes = gateway.maxBytes)
            }

            is NappletRequest.UploadStatus -> {
                upload ?: return NappletResponse.Unsupported("upload.status")
                uploadsLock.withLock { uploads["${identity.coordinate}|${request.uploadId}"] } ?: NappletResponse.Failed("unknown upload")
            }
        }

    /**
     * Whether [identity] may read content encrypted between the user and [peer] — what a relay read
     * ([NappletRelayCleartext]) asks before it decrypts an event for the app. Never prompts: a relay
     * read is not a decrypt request the user can see, so only a grant the user already gave counts —
     * the broad decrypt grant (standing, for this session, or a FULL_TRUST policy) or the narrow one
     * for [peer]. A standing denial of the broad grant, or of the app's relay access, wins. Under
     * REASONABLE and PARANOID, where decryption always asks, encrypted events are withheld.
     */
    suspend fun mayDecrypt(
        identity: NappletIdentity,
        peer: HexKey,
    ): Boolean {
        val sl = signerLedger ?: return false
        if (ledger.decide(identity, NappletCapability.RELAY) == PermissionDecision.DENY) return false
        val coordinate = signerCoordinateFor(identity)
        if (hasSessionGrant(sessionKey(coordinate, NostrSignerOp.Decrypt))) return true
        return when (sl.decide(coordinate, NostrSignerOp.Decrypt)) {
            NostrOpDecision.ALLOW -> true
            NostrOpDecision.DENY -> false
            NostrOpDecision.ASK -> isNarrowAllowed(sl, coordinate, NostrSignerOp.DecryptFrom(peer))
        }
    }

    /**
     * [mayDecrypt] for one reader of [identity]'s events (a query, a live subscription), remembering
     * each counterparty's answer for [DECRYPT_GATE_TTL_MS]: a page of 500 encrypted events from a
     * handful of peers then reads the permission store a handful of times, not 500, and a revoked
     * grant still stops decryption within seconds. One reader at a time: it is not thread-safe.
     */
    fun decryptGate(identity: NappletIdentity): suspend (HexKey) -> Boolean {
        val answers = HashMap<HexKey, Pair<Boolean, Long>>()
        return { peer ->
            val now = nowMillis()
            val cached = answers[peer]?.takeIf { now - it.second < DECRYPT_GATE_TTL_MS }
            cached?.first ?: mayDecrypt(identity, peer).also { answers[peer] = it to now }
        }
    }

    /**
     * Signs a napplet-supplied template **as the active user** and publishes it. The applet never
     * sees a key, can never sign as another identity (the signer fixes `pubkey`), and cannot
     * backdate (`created_at` is the host clock).
     */
    private sealed interface CommonPlan {
        class Publish(
            val request: NappletRequest.Publish,
        ) : CommonPlan

        class Answer(
            val response: NappletResponse,
        ) : CommonPlan
    }

    private fun publishing(template: EventTemplate<*>) = CommonPlan.Publish(NappletRequest.Publish(template.kind, template.tags, template.content, NappletCapability.COMMON))

    private fun refusing(error: String) = CommonPlan.Answer(NappletResponse.CommonFailure(error))

    /**
     * The event a NAP-COMMON action publishes, or the answer when there is nothing to publish: a
     * target that isn't valid, or a follow-list change that changes nothing. Reads only public data
     * (the follow list, the reacted or reported event), before any prompt.
     */
    private suspend fun planCommonAction(request: NappletRequest.CommonAction): CommonPlan =
        when (request) {
            is NappletRequest.CommonFollow -> {
                val gateway = common ?: return CommonPlan.Answer(NappletResponse.Unsupported("common.follow"))
                val keys = request.pubkeys.map { NappletCommonNip19.pubkeyOf(it)?.first ?: return refusing("invalid-pubkey") }.distinct()
                if (keys.isEmpty()) return refusing("invalid-pubkey")
                val current = gateway.followList()
                val tags = current?.tags ?: emptyArray()
                val followed = tags.mapNotNullTo(HashSet()) { ContactTag.parseKey(it) }
                val newTags =
                    if (request.follow) {
                        tags + keys.filter { it !in followed }.map { arrayOf("p", it) }
                    } else {
                        tags.filterNot { tag -> ContactTag.parseKey(tag)?.let { it in keys } == true }.toTypedArray()
                    }
                // Idempotent: nothing to add or remove publishes nothing.
                if (newTags.size == tags.size) {
                    CommonPlan.Answer(NappletResponse.CommonUnchanged(current))
                } else {
                    CommonPlan.Publish(NappletRequest.Publish(ContactListEvent.KIND, newTags, current?.content ?: "", NappletCapability.COMMON))
                }
            }

            is NappletRequest.CommonReact -> {
                if (!NappletCommonNip19.isHexKey(request.targetEventId)) return refusing("invalid-target")
                val shortcode = SHORTCODE.matchEntire(request.reaction)?.groupValues?.get(1)
                val emojiUrl = request.customEmojiHref
                when {
                    shortcode != null -> if (emojiUrl == null || !emojiUrl.startsWith("https://")) return refusing("invalid-reaction")
                    emojiUrl != null -> return refusing("invalid-reaction")
                    !isReactionText(request.reaction) -> return refusing("invalid-reaction")
                }
                val target = commonEvent(request.targetEventId) ?: return refusing("invalid-target")
                if (shortcode != null && emojiUrl != null) {
                    publishing(ReactionEvent.build(EmojiUrlTag(shortcode, emojiUrl), EventHintBundle(target)))
                } else {
                    publishing(ReactionEvent.build(request.reaction, EventHintBundle(target)))
                }
            }

            is NappletRequest.CommonReport -> {
                val type = REPORT_REASONS[request.reason] ?: return refusing("invalid-report-reason")
                if (request.eventId != null) {
                    if (!NappletCommonNip19.isHexKey(request.eventId)) return refusing("invalid-target")
                    // NIP-56 names the author of a reported event; one that can't be found is not guessed.
                    val reported = commonEvent(request.eventId) ?: return refusing("author-unresolved")
                    if (request.pubkey != null && NappletCommonNip19.pubkeyOf(request.pubkey)?.first != reported.pubKey) return refusing("invalid-target")
                    publishing(ReportEvent.build(reported, type, request.text))
                } else {
                    val user = request.pubkey?.let { NappletCommonNip19.pubkeyOf(it)?.first } ?: return refusing("invalid-pubkey")
                    publishing(ReportEvent.build(user, type, request.text))
                }
            }
        }

    // A native event by id, from the cache or the relays, verified.
    private suspend fun commonEvent(id: HexKey): Event? =
        outbox
            ?.getEvent(id, null, emptyList(), COMMON_READ_TIMEOUT_MS)
            ?.events
            ?.firstOrNull { it.event.id == id }
            ?.event

    // NIP-25 content: "+", "-", or one emoji. An emoji is not a word: nothing ASCII, no spaces, short.
    private fun isReactionText(reaction: String): Boolean = reaction == "+" || reaction == "-" || (reaction.isNotEmpty() && reaction.length <= MAX_EMOJI_CHARS && reaction.none { it.code < 0x80 || it.isWhitespace() })

    private suspend fun signAndPublish(
        kind: Int,
        tags: Array<Array<String>>,
        content: String,
    ): NappletResponse {
        val gateway = relay ?: return NappletResponse.Unsupported("relay.publish")
        val signed: Event = signer.sign(TimeUtils.now(), kind, tags, content)
        return NappletResponse.Published(signed, gateway.publish(signed))
    }

    /** Ensures the encrypted event addresses [recipient] with a `p` tag, without duplicating one. */
    private fun withRecipientTag(
        tags: Array<Array<String>>,
        recipient: String,
    ): Array<Array<String>> =
        if (tags.any { it.size >= 2 && it[0] == "p" && it[1] == recipient }) {
            tags
        } else {
            tags + arrayOf(arrayOf("p", recipient))
        }

    private fun storageCoordinate(
        identity: NappletIdentity,
        scope: NappletStorageScope,
    ): String =
        when (scope) {
            NappletStorageScope.SHARED -> identity.storageCoordinate
            NappletStorageScope.INSTANCE -> identity.instanceStorageCoordinate
        }

    /**
     * Shows the first-connect "Connect to Nostr" dialog if no signer policy exists yet.
     * On success, stores the chosen policy and bulk-grants all declared non-payment capabilities.
     * Returns false if the user cancelled or blocked the app.
     */
    private suspend fun ensureConnected(
        identity: NappletIdentity,
        declared: Set<NappletCapability>,
    ): Boolean =
        signerConsentLock.withLock {
            val sl = signerLedger ?: return@withLock true
            // Re-check after acquiring lock: a sibling request may have set the policy while we waited.
            if (sl.hasPolicy(signerCoordinateFor(identity))) return@withLock true

            // Within the post-cancel cooldown, suppress re-prompting so a load-time burst doesn't
            // relaunch the dialog per request. Once it lapses, drop the stale entry and fall through to
            // prompt again — this is what lets the user retry after dismissing the dialog. The cooldown
            // self-clears, so there is nothing to hunt down and clear in Connected Apps.
            cancelledUntil[identity.coordinate]?.let { until ->
                if (nowMillis() < until) return@withLock false
                cancelledUntil.remove(identity.coordinate)
            }

            val prompt = nostrConnectPrompt ?: return@withLock true
            if (identity.inline) return@withLock refuseInline(identity)
            when (val result = prompt.request(identity, declared)) {
                is AppConnectResult.Connected -> {
                    cancelledUntil.remove(identity.coordinate)
                    sl.setPolicy(signerCoordinateFor(identity), result.policy)
                    // Bulk-grant non-payment capabilities only for non-paranoid policies.
                    // PARANOID users chose "ask me for everything" — leave the capability ledger
                    // empty so each capability prompts on first use.
                    if (result.policy != AppSignerPolicy.PARANOID) {
                        for (cap in declared) {
                            if (!cap.requiresPerUseConsent) {
                                ledger.record(identity, cap, GrantState.ALLOW_ALWAYS)
                            }
                        }
                    }
                    true
                }
                AppConnectResult.Blocked -> {
                    sl.setPolicy(signerCoordinateFor(identity), AppSignerPolicy.PARANOID)
                    // Every capability, not only those declared today: "Block" is about the app, and a
                    // later version that asks for something new must not get a fresh prompt for it.
                    for (cap in NappletCapability.entries) {
                        ledger.record(identity, cap, GrantState.DENY)
                    }
                    false
                }
                AppConnectResult.Cancelled -> {
                    cancelledUntil[identity.coordinate] = nowMillis() + CANCEL_REPROMPT_COOLDOWN_MS
                    false
                }
            }
        }

    /**
     * Gates a specific signing/encryption operation through the signer permission ledger.
     * If the ledger says ASK, prompts the user and records their choice.
     */
    private suspend fun authorizeSignerOp(
        identity: NappletIdentity,
        op: NostrSignerOp,
        request: NappletRequest,
    ): Boolean = waitingForPrompt(identity.coordinate) { authorizeSignerOpQueued(identity, op, request) }

    private suspend fun authorizeSignerOpQueued(
        identity: NappletIdentity,
        op: NostrSignerOp,
        request: NappletRequest,
    ): Boolean =
        signerConsentLock.withLock {
            val sl = signerLedger ?: return@withLock true
            val coordinate = signerCoordinateFor(identity)
            // A decrypt request also carries a narrower op ("decrypt messages from THIS
            // counterparty"). A standing narrow grant satisfies it without widening the broad one.
            val narrowOp = request.toNarrowSignerOp()

            // Session grants win immediately without touching storage. Scoped to this applet: a
            // grant made for one app never authorizes another.
            if (hasSessionGrant(sessionKey(coordinate, op))) {
                sl.updateLastUsed(coordinate)
                return@withLock true
            }
            when (sl.decide(coordinate, op)) {
                NostrOpDecision.ALLOW -> {
                    sl.updateLastUsed(coordinate)
                    true
                }
                // An explicit DENY on the broad op is final — a narrow grant never overrides it.
                NostrOpDecision.DENY -> false
                NostrOpDecision.ASK -> {
                    if (narrowOp != null && isNarrowAllowed(sl, coordinate, narrowOp)) {
                        sl.updateLastUsed(coordinate)
                        return@withLock true
                    }
                    val prompt = signerConsentPrompt ?: return@withLock true
                    if (identity.inline) return@withLock refuseInline(identity)
                    val declineKey = "$coordinate|${op.key}"
                    if (isBackingOff(declineKey)) return@withLock false
                    val grant = prompt.request(identity, op, request.consentView())
                    noteAnswer(declineKey, grant.isAllowed)
                    // Record the GRANT's own op, never the requested one: the dialog may hand back a
                    // narrower op ("only from this counterparty"), and a stored grant must never be
                    // wider than what the user actually tapped.
                    sl.record(coordinate, grant)
                    if (grant is SignerOpGrant.AllowForSession) {
                        sessionLock.withLock { sessionAllows.add(sessionKey(coordinate, grant.op)) }
                    }
                    if (grant.isAllowed) sl.updateLastUsed(coordinate)
                    grant.isAllowed
                }
            }
        }

    /**
     * Runs [block] (which may wait on a consent dialog) unless [coordinate] already has
     * [MAX_WAITING_PROMPTS] requests waiting, in which case the request is refused at once.
     */
    private suspend fun waitingForPrompt(
        coordinate: String,
        block: suspend () -> Boolean,
    ): Boolean {
        val admitted =
            waitingLock.withLock {
                val n = waiting[coordinate] ?: 0
                if (n >= MAX_WAITING_PROMPTS) {
                    false
                } else {
                    waiting[coordinate] = n + 1
                    true
                }
            }
        if (!admitted) return false
        try {
            return block()
        } finally {
            withContext(NonCancellable) {
                waitingLock.withLock {
                    val n = (waiting[coordinate] ?: 1) - 1
                    if (n <= 0) waiting.remove(coordinate) else waiting[coordinate] = n
                }
            }
        }
    }

    /** True while a prompt for [key] the user just turned down must not be shown again. */
    private suspend fun isBackingOff(key: String): Boolean =
        declinedLock.withLock {
            val entry = declined[key] ?: return@withLock false
            nowMillis() < entry.until
        }

    /** Records the user's answer to the prompt for [key]: a decline starts (or doubles) its backoff, an allow clears it. */
    private suspend fun noteAnswer(
        key: String,
        allowed: Boolean,
    ) {
        declinedLock.withLock {
            if (allowed) {
                declined.remove(key)
            } else {
                val count = (declined[key]?.count ?: 0) + 1
                // One dismissal may be a slip the user retries at once: that retry still gets its
                // dialog. From the second decline in a row the app is asking again unwanted, and waits.
                val wait = if (count < 2) 0L else (DECLINE_BACKOFF_MS shl (count - 2).coerceAtMost(8)).coerceAtMost(MAX_DECLINE_BACKOFF_MS)
                declined[key] = Declined(count, nowMillis() + wait)
                if (declined.size > MAX_DECLINED_REMEMBERED) declined.keys.firstOrNull()?.let { declined.remove(it) }
            }
        }
    }

    /**
     * True when a standing or session grant exists for the narrower [narrowOp] (e.g. decrypt-from-X).
     * Only an explicit per-op override counts: [NostrSignerPermissionLedger.decide] would otherwise
     * fall through to the app's policy, and FULL_TRUST/REASONABLE would answer for an op nobody ever
     * granted. Mirrors the NIP-46 authorizer so both surfaces honour a narrow grant identically.
     */
    private suspend fun isNarrowAllowed(
        sl: NostrSignerPermissionLedger,
        coordinate: String,
        narrowOp: NostrSignerOp,
    ): Boolean =
        hasSessionGrant(sessionKey(coordinate, narrowOp)) ||
            sl.store.loadOpDecision(coordinate, narrowOp)?.let { sl.decide(coordinate, narrowOp) == NostrOpDecision.ALLOW } ?: false

    /**
     * The signer-ledger coordinate for [identity] under the current account. The signer permission
     * store is shared with NIP-46, which already namespaces by account
     * (`nip46:<signer>:<client>` — see Nip46PermissionAuthorizer.coordinateFor); this mirrors that so
     * an "always allow" granted by one npub can never authorize signing under another. The bare
     * [NappletIdentity.coordinate] stays the app's display/UI identity and is unchanged.
     */
    private fun signerCoordinateFor(identity: NappletIdentity): String = signerCoordinateFor(identity.coordinate)

    /** [signerCoordinateFor] for a bare app coordinate — what the Connected Apps UI holds. */
    private fun signerCoordinateFor(coordinate: String): String = NappletSignerKey.of(signer.pubKey, coordinate)

    /**
     * Namespaces an in-memory session grant to the applet that was actually prompted for. Mirrors
     * `Nip46PermissionAuthorizer.sessionKey`; both authorizers share one process-wide instance, so
     * the coordinate is what keeps one app's "allow for this session" from leaking to the rest.
     */
    private fun sessionKey(
        coordinate: String,
        op: NostrSignerOp,
    ): String = "$coordinate|${op.key}"

    /**
     * Drops every live session grant held for [coordinate], so revoking an app takes effect
     * immediately instead of lingering until this broker instance dies.
     *
     * [coordinate] is the **bare** app coordinate (`<author>:<identifier>`, or `browser:<origin>`)
     * — the identity the Connected Apps UI holds. Session keys are stored under the
     * account-namespaced [signerCoordinateFor] form, so this namespaces before matching; comparing
     * the bare coordinate against those keys would silently match nothing and revoke nothing.
     *
     * Also clears any post-Cancel re-prompt suppression for the app: after an explicit revoke the
     * user's next interaction should prompt, not be dropped by a cooldown from before.
     */
    suspend fun revokeSessionGrants(coordinate: String) {
        signerConsentLock.withLock {
            val prefix = "${signerCoordinateFor(coordinate)}|"
            sessionLock.withLock { sessionAllows.removeAll { it.startsWith(prefix) } }
            cancelledUntil.remove(coordinate)
        }
        declinedLock.withLock { declined.keys.removeAll { it.startsWith("$coordinate|") || it.startsWith("${signerCoordinateFor(coordinate)}|") } }
    }

    companion object {
        /**
         * How long (ms) a Cancel on the first-connect dialog suppresses re-prompting for the same app.
         * Long enough to absorb the burst of requests a page/napplet fires on load without relaunching
         * the dialog per request; short enough that a deliberate user retry seconds later prompts again.
         */
        private const val CANCEL_REPROMPT_COOLDOWN_MS = 3_000L

        /** How long a NAP-COMMON read (a profile, a reacted or reported event) waits on relays. */
        private const val COMMON_READ_TIMEOUT_MS = 5_000L

        // A NIP-30 shortcode reaction, `:name:`.
        private val SHORTCODE = Regex("^:([A-Za-z0-9_-]+):$")

        // The longest emoji kept as one reaction: a family or a flag with modifiers, not a sentence.
        private const val MAX_EMOJI_CHARS = 16

        // NAP-COMMON's report reasons, which are NIP-56's.
        private val REPORT_REASONS =
            mapOf(
                "nudity" to ReportType.NUDITY,
                "malware" to ReportType.MALWARE,
                "profanity" to ReportType.PROFANITY,
                "illegal" to ReportType.ILLEGAL,
                "spam" to ReportType.SPAM,
                "impersonation" to ReportType.IMPERSONATION,
                "other" to ReportType.OTHER,
            )

        /** The one NAP-UPLOAD rail offered: Blossom. */
        const val UPLOAD_RAIL = "blossom"

        /** Uploads kept for `upload.status`, across every napplet. */
        private const val MAX_REMEMBERED_UPLOADS = 64

        /**
         * How long a prompt stays quiet after it was declined twice in a row; each further decline
         * doubles it. A single decline never silences the next ask.
         */
        const val DECLINE_BACKOFF_MS = 5_000L
        const val MAX_DECLINE_BACKOFF_MS = 5L * 60_000L

        /**
         * Requests one app may have waiting on a consent dialog at once. An app's first load fires bursts
         * (a hundred storage reads behind the one first prompt is ordinary), so this only bounds a flood.
         */
        const val MAX_WAITING_PROMPTS = 1_000

        /** How long a [decryptGate] keeps an answer before asking the permission store again. */
        const val DECRYPT_GATE_TTL_MS = 5_000L
        private const val MAX_DECLINED_REMEMBERED = 1_024
        private const val RESOURCE_MAX_BYTES = 10L * 1024L * 1024L
        private const val RESOURCE_MAX_URLS = 16

        /** The most filters one `relay.query` or `relay.subscribe` may carry, as for outbox reads. */
        const val MAX_RELAY_FILTERS = NappletOutboxGateway.MAX_FILTERS

        /** The most events one `relay.query` returns. */
        const val MAX_QUERY_RESULTS = NappletOutboxGateway.MAX_RESULTS
    }
}
