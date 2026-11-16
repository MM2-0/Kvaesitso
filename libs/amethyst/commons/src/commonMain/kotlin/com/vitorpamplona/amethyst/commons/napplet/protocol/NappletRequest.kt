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
package com.vitorpamplona.amethyst.commons.napplet.protocol

import com.vitorpamplona.amethyst.commons.napplet.NappletCapability
import com.vitorpamplona.amethyst.commons.napplet.fs.NappletFsPickOptions
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter

enum class NappletStorageScope {
    SHARED,
    INSTANCE,
}

/**
 * A capability request from a napplet, after it has crossed the postMessage + IPC edge
 * and been deserialized into a typed object. Each variant declares the [capability] the
 * broker must check before running it; the broker never trusts a value the applet sends
 * for that — it is derived here from the request type.
 *
 * Wire (JSON over postMessage, then a string across Binder) is marshalled at the Android
 * edge so this layer stays KMP-pure and unit-testable. Identity-bearing fields the applet
 * could try to spoof (e.g. "sign as someone else") are constrained by the broker, not here.
 */
sealed interface NappletRequest {
    val capability: NappletCapability

    /**
     * Whether executing this request **signs an event as the user**. The shell — never the napplet
     * — holds the key and does the signing (matching `@napplet/shim`, which has no `sign()`). This
     * flag lets the broker defer the per-signature prompt to a remote/external signer that runs its
     * own consent UI, instead of double-prompting.
     */
    val signsAsUser: Boolean get() = false

    /**
     * Whether the user's consent to the [capability] is asked before this runs. False only for a
     * request that grants nothing by itself: a keyboard action binding (a UI affordance, not key
     * access), and an `fs` request other than a pick, which reaches only what a pick handed over.
     */
    val asksConsent: Boolean get() = true

    /** Read the active user's public key. */
    data object GetPublicKey : NappletRequest {
        override val capability get() = NappletCapability.IDENTITY
    }

    /**
     * Read a non-key identity datum (`identity.getProfile`/`getRelays`/`getFollows`/`getMutes`/…).
     * [method] is the bare method name and [argument] carries an optional parameter (e.g. the list
     * type for `getList`). The shell answers from the active account; a method this shell does not
     * implement resolves to Unsupported. No private key material is ever returned.
     */
    data class IdentityRead(
        val method: String,
        val argument: String? = null,
    ) : NappletRequest {
        override val capability get() = NappletCapability.IDENTITY
    }

    /** `theme.get` — read the host's current theme colors. Cosmetic, read-only, never prompts. */
    data object ThemeGet : NappletRequest {
        override val capability get() = NappletCapability.THEME
    }

    /**
     * Publish an event built from an **unsigned template**. The napplet supplies only `kind`,
     * `tags`, and `content`; the shell sets `pubkey` from the real signer, stamps `created_at`,
     * signs, and broadcasts — so a napplet can never sign as another identity, backdate, nor
     * obtain a raw signature. This is the *only* signing path exposed to napplets.
     */
    data class Publish(
        val kind: Int,
        val tags: Array<Array<String>>,
        val content: String,
        /**
         * The domain the napplet used. A NAP-COMMON action ([CommonAction]) becomes the event it
         * publishes, built by the shell, and stays gated as `common`, not `relay`.
         */
        val via: NappletCapability = NappletCapability.RELAY,
    ) : NappletRequest {
        override val capability get() = via
        override val signsAsUser get() = true

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Publish) return false
            if (via != other.via) return false
            if (kind != other.kind) return false
            if (content != other.content) return false
            if (tags.size != other.tags.size) return false
            for (i in tags.indices) if (!tags[i].contentEquals(other.tags[i])) return false
            return true
        }

        override fun hashCode(): Int {
            var result = kind
            result = 31 * result + content.hashCode()
            result = 31 * result + tags.sumOf { it.contentHashCode() }
            return result
        }
    }

    /**
     * NIP-07 `window.nostr.signEvent`: sign a full event template **as the user** and return it,
     * **without publishing** (the web app sends it to relays itself). Unlike [Publish] this honors the
     * app-supplied `created_at` (NIP-07 apps rely on it); the shell still fixes `pubkey` to the real
     * signer, so the app can never sign as another identity. Exposed only to nSites (website mode).
     *
     * Gated on [NappletCapability.SIGNER], which only the website posture holds and no NIP-5D manifest
     * can declare: a locked napplet that could reach this would hold raw signatures dated whenever it
     * likes (a future-dated replaceable event takes the user's slot for good), to publish after the
     * user revoked it.
     */
    data class SignEvent(
        val kind: Int,
        val tags: Array<Array<String>>,
        val content: String,
        val createdAt: Long,
    ) : NappletRequest {
        override val capability get() = NappletCapability.SIGNER
        override val signsAsUser get() = true

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is SignEvent) return false
            if (kind != other.kind || createdAt != other.createdAt || content != other.content) return false
            if (tags.size != other.tags.size) return false
            for (i in tags.indices) if (!tags[i].contentEquals(other.tags[i])) return false
            return true
        }

        override fun hashCode(): Int {
            var result = kind
            result = 31 * result + createdAt.hashCode()
            result = 31 * result + content.hashCode()
            result = 31 * result + tags.sumOf { it.contentHashCode() }
            return result
        }
    }

    /**
     * Encrypt [content] to [recipient] with [encryption] (`"nip44"`, default, or `"nip04"`), then
     * build, sign, and publish the event. The shell holds the key and performs both the encryption
     * and the signing; the napplet supplies only plaintext.
     */
    data class PublishEncrypted(
        val kind: Int,
        val tags: Array<Array<String>>,
        val content: String,
        val recipient: HexKey,
        val encryption: String,
    ) : NappletRequest {
        override val capability get() = NappletCapability.RELAY
        override val signsAsUser get() = true

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is PublishEncrypted) return false
            if (kind != other.kind) return false
            if (content != other.content) return false
            if (recipient != other.recipient) return false
            if (encryption != other.encryption) return false
            if (tags.size != other.tags.size) return false
            for (i in tags.indices) if (!tags[i].contentEquals(other.tags[i])) return false
            return true
        }

        override fun hashCode(): Int {
            var result = kind
            result = 31 * result + content.hashCode()
            result = 31 * result + recipient.hashCode()
            result = 31 * result + encryption.hashCode()
            result = 31 * result + tags.sumOf { it.contentHashCode() }
            return result
        }
    }

    /**
     * NIP-07 `window.nostr.nip44.encrypt`: NIP-44 encrypt [plaintext] to [peer] with the user's key
     * and return the ciphertext **without publishing it**. This is what lets a web app build its own
     * NIP-59 seals (a kind:13 seal is NIP-44 ciphertext authored by the real key, so `signEvent`
     * alone cannot produce one) — NIP-17 DMs and every gift-wrapped app protocol need it.
     *
     * The page never touches the key: the shell encrypts and hands back only the result.
     *
     * [signsAsUser] stays false — this produces no event and no signature; the gate that matters is
     * the [NostrSignerOp][com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerOp]
     * mapping in `toSignerOp`.
     */
    data class Nip44Encrypt(
        val peer: HexKey,
        val plaintext: String,
    ) : NappletRequest {
        override val capability get() = NappletCapability.SIGNER
    }

    /**
     * NIP-07 `window.nostr.nip44.decrypt`: NIP-44 decrypt [ciphertext] from [peer] with the user's
     * key and return the plaintext. The counterpart of [Nip44Encrypt] — a NIP-17 client needs it to
     * open incoming seals.
     *
     * Strictly more dangerous than encryption (it reads, rather than writes, private content), so it
     * maps to [NostrSignerOp.Decrypt][com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerOp.Decrypt],
     * which always asks under the REASONABLE policy instead of auto-approving.
     */
    data class Nip44Decrypt(
        val peer: HexKey,
        val ciphertext: String,
    ) : NappletRequest {
        override val capability get() = NappletCapability.SIGNER
    }

    /** Read events matching [filters] (from the cache and/or a bounded relay fetch). */
    data class QueryEvents(
        val filters: List<Filter>,
    ) : NappletRequest {
        override val capability get() = NappletCapability.RELAY
    }

    /**
     * Subscribe to events matching [filters]. The shell opens a live relay subscription and pushes
     * `relay.event`/`relay.eose`/`relay.closed` keyed by the applet's `subId`.
     */
    data class Subscribe(
        val filters: List<Filter>,
    ) : NappletRequest {
        override val capability get() = NappletCapability.RELAY
    }

    /**
     * `outbox.getEvent` — one event by [eventId], read from [author]'s write relays when given, or
     * from the cache and the user's relays otherwise. [relays] are hints, checked by the shell.
     */
    data class OutboxGetEvent(
        val eventId: HexKey,
        val author: HexKey? = null,
        val relays: List<String> = emptyList(),
        val timeoutMs: Long? = null,
    ) : NappletRequest {
        override val capability get() = NappletCapability.OUTBOX
    }

    /**
     * `outbox.query` — [filters] read from the write relays of their authors (the filters' own and
     * [authors]), deduplicated. [relays] are hints, checked by the shell.
     */
    data class OutboxQuery(
        val filters: List<Filter>,
        val authors: List<HexKey> = emptyList(),
        val relays: List<String> = emptyList(),
        val limit: Int? = null,
        val timeoutMs: Long? = null,
    ) : NappletRequest {
        override val capability get() = NappletCapability.OUTBOX
    }

    /** `outbox.subscribe` — a live [OutboxQuery]: the host streams `outbox.event` until `outbox.close`. */
    data class OutboxSubscribe(
        val filters: List<Filter>,
        val authors: List<HexKey> = emptyList(),
        val relays: List<String> = emptyList(),
    ) : NappletRequest {
        override val capability get() = NappletCapability.OUTBOX
    }

    /**
     * `outbox.publish` — sign [template] once and send it to the user's write relays (unless
     * [toOutbox] is false), the read relays of every author in [toInboxes], and the checked
     * [relays]. The template is a [Publish], so it is shown and gated exactly as `relay.publish` is.
     */
    data class OutboxPublish(
        val template: Publish,
        val toOutbox: Boolean = true,
        val toInboxes: List<HexKey> = emptyList(),
        val relays: List<String> = emptyList(),
    ) : NappletRequest {
        override val capability get() = NappletCapability.OUTBOX
        override val signsAsUser get() = true
    }

    /** `outbox.resolveRelays` — the relays the shell would use to read from or write to [authors]. */
    data class OutboxResolveRelays(
        val authors: List<HexKey>,
        val write: Boolean,
    ) : NappletRequest {
        override val capability get() = NappletCapability.OUTBOX
    }

    /** Read a value from this napplet's sandboxed key-value store (`storage.getItem`). */
    data class StorageGet(
        val key: String,
        val scope: NappletStorageScope = NappletStorageScope.SHARED,
    ) : NappletRequest {
        override val capability get() = NappletCapability.STORAGE
    }

    /** Write a value to this napplet's sandboxed key-value store (`storage.setItem`). */
    data class StorageSet(
        val key: String,
        val value: String,
        val scope: NappletStorageScope = NappletStorageScope.SHARED,
    ) : NappletRequest {
        override val capability get() = NappletCapability.STORAGE
    }

    /** Remove a value from this napplet's sandboxed key-value store (`storage.removeItem`). */
    data class StorageRemove(
        val key: String,
        val scope: NappletStorageScope = NappletStorageScope.SHARED,
    ) : NappletRequest {
        override val capability get() = NappletCapability.STORAGE
    }

    /** List the keys this napplet has stored (`storage.keys`). */
    data class StorageKeys(
        val scope: NappletStorageScope = NappletStorageScope.SHARED,
    ) : NappletRequest {
        override val capability get() = NappletCapability.STORAGE
    }

    /** `notify.create` — post a user-facing notification; replies `notify.created { id }`. */
    data class NotifyCreate(
        val title: String,
        val body: String,
    ) : NappletRequest {
        override val capability get() = NappletCapability.NOTIFY
    }

    /** `notify.list` — enumerate this napplet's active notifications; replies `notify.listed { notifications }`. */
    data object NotifyList : NappletRequest {
        override val capability get() = NappletCapability.NOTIFY
    }

    /** `notify.dismiss` — dismiss a notification by id (fire-and-forget on the client). */
    data class NotifyDismiss(
        val id: String,
    ) : NappletRequest {
        override val capability get() = NappletCapability.NOTIFY
    }

    /**
     * `keys.registerAction` — register a named keyboard/command action. This is a shell-mediated UI
     * affordance, **not** signing (the `keys` domain has no key access). [actionId] is the napplet's
     * action id (echoed back); [label] is its display name; [binding] is the requested key combo
     * (the SDK's `defaultKey`, e.g. `"Ctrl+S"`), which the shell honors and echoes back.
     */
    data class RegisterAction(
        val actionId: String,
        val label: String,
        val binding: String? = null,
    ) : NappletRequest {
        override val capability get() = NappletCapability.KEYS
        override val asksConsent get() = false
    }

    /** `keys.unregisterAction` — drop a previously registered action (fire-and-forget). */
    data class UnregisterAction(
        val actionId: String,
    ) : NappletRequest {
        override val capability get() = NappletCapability.KEYS
        override val asksConsent get() = false
    }

    /**
     * Pay a BOLT-11 invoice from the user's wallet (`value.payInvoice`). This is an
     * **Amethyst-specific extension** — the upstream `value` domain models zaps/value-transfer
     * differently — kept because a real napplet built against `@napplet/shim` never calls it, so
     * it cannot conflict.
     */
    data class PayInvoice(
        val invoice: String,
    ) : NappletRequest {
        override val capability get() = NappletCapability.VALUE
    }

    /** Fetch the bytes of an https/blossom/nostr/data resource (`resource.bytes`). */
    data class ResourceBytes(
        val url: String,
    ) : NappletRequest {
        override val capability get() = NappletCapability.RESOURCE
    }

    /** Describe the bounded schemes and limits of this shell's existing resource broker. */
    data object ResourceInfo : NappletRequest {
        override val capability get() = NappletCapability.RESOURCE
    }

    /** Fetch several resources in input order, returning a per-URL success/error record. */
    data class ResourceBytesMany(
        val urls: List<String>,
    ) : NappletRequest {
        override val capability get() = NappletCapability.RESOURCE
    }

    /**
     * Upload a blob to the user's Blossom server (`upload.upload`). [rail] is the NAP-UPLOAD rail the
     * napplet asked for, null to let the shell pick; only `blossom` is offered.
     */
    data class UploadBlob(
        val bytes: ByteArray,
        val contentType: String,
        val filename: String? = null,
        val rail: String? = null,
    ) : NappletRequest {
        override val capability get() = NappletCapability.UPLOAD

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is UploadBlob) return false
            return contentType == other.contentType && filename == other.filename && rail == other.rail && bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int = 31 * (31 * (31 * contentType.hashCode() + filename.hashCode()) + rail.hashCode()) + bytes.contentHashCode()
    }

    /** `upload.info`: the rails and limits the shell offers. Advisory introspection, so it never prompts. */
    data object UploadInfo : NappletRequest {
        override val capability get() = NappletCapability.UPLOAD
        override val asksConsent get() = false
    }

    /** `upload.status`: the latest status of this napplet's own upload [uploadId]. Never prompts. */
    data class UploadStatus(
        val uploadId: String,
    ) : NappletRequest {
        override val capability get() = NappletCapability.UPLOAD
        override val asksConsent get() = false
    }

    /** A NAP-COMMON request (`common.*`): NIP-19 helpers, profile and follow reads, social actions. */
    sealed interface Common : NappletRequest {
        override val capability get() = NappletCapability.COMMON
    }

    /**
     * `common.encodeNip19`: a public NIP-19 identifier of [type] (`npub`, `note`, `nprofile`, `nevent`,
     * `naddr`, `nrelay`) from the fields that type takes. Pure computation, so it never prompts.
     */
    data class CommonEncodeNip19(
        val type: String,
        val hex: String? = null,
        val pubkey: String? = null,
        val eventId: String? = null,
        val identifier: String? = null,
        val kind: Int? = null,
        val author: String? = null,
        val relay: String? = null,
        val relays: List<String> = emptyList(),
    ) : Common {
        override val asksConsent get() = false
    }

    /** `common.decodeNip19`: the fields of a public NIP-19 [value]. Pure computation, never prompts. */
    data class CommonDecodeNip19(
        val value: String,
    ) : Common {
        override val asksConsent get() = false
    }

    /** `common.getProfile`: the latest kind 0 of [target], a hex key, `npub` or `nprofile`. */
    data class CommonGetProfile(
        val target: String,
    ) : Common

    /** `common.follows`: the hex keys on the user's kind 3 follow list. */
    data object CommonFollows : Common

    /**
     * A NAP-COMMON action that publishes an event the shell builds: the broker turns it into a
     * [Publish] (via `common`) before any gate, so the consent and signer prompts show that event.
     */
    sealed interface CommonAction : Common

    /** `common.follow` / `common.unfollow`: add or remove [pubkeys] (`npub`s) on the user's kind 3. */
    data class CommonFollow(
        val pubkeys: List<String>,
        val follow: Boolean,
    ) : CommonAction

    /** `common.react`: a NIP-25 kind 7 [reaction] to the event [targetEventId]. */
    data class CommonReact(
        val targetEventId: String,
        val reaction: String,
        val customEmojiHref: String? = null,
    ) : CommonAction

    /**
     * `common.report`: a NIP-56 kind 1984 for the event [eventId] (with its author) or the user
     * [pubkey] (a hex key or `npub`), for [reason], with the report [text].
     */
    data class CommonReport(
        val eventId: String?,
        val pubkey: String?,
        val relay: String?,
        val reason: String,
        val text: String,
    ) : CommonAction

    /**
     * A NAP-FS request (`fs.*`). Its paths, options and bytes come from the napplet and are checked by
     * [NappletFsSession][com.vitorpamplona.amethyst.commons.napplet.fs.NappletFsSession], never trusted here.
     */
    sealed interface Fs : NappletRequest {
        override val capability get() = NappletCapability.FS

        /**
         * Whether it asks the user to pick something. Picks are the only `fs` requests that grant
         * access, so they are the ones consent gates; the rest act on what a pick already granted.
         */
        val picks: Boolean get() = false

        override val asksConsent get() = picks
    }

    /** Which NAP-FS picker a [FsPick] opens. [wire] is its operation name. */
    enum class FsPickKind(
        val wire: String,
    ) {
        FILE("pickFile"),
        FILES("pickFiles"),
        DIRECTORY("pickDirectory"),
        SAVE_FILE("pickSaveFile"),
    }

    /** `fs.info` — visible roots and limits. */
    data object FsInfo : Fs

    /** `fs.pickFile` / `pickFiles` / `pickDirectory` / `pickSaveFile`. */
    data class FsPick(
        val kind: FsPickKind,
        val options: NappletFsPickOptions = NappletFsPickOptions(),
    ) : Fs {
        override val picks get() = true
    }

    /** `fs.stat`. */
    data class FsStat(
        val path: String,
    ) : Fs

    /** `fs.list`. */
    data class FsList(
        val path: String,
    ) : Fs

    /** `fs.read`: [length] bytes (or the runtime's chunk) from [offset] (or 0). */
    data class FsRead(
        val path: String,
        val offset: Long? = null,
        val length: Long? = null,
    ) : Fs

    /**
     * `fs.write`. [data] is the wire's base64 text, decoded (and rejected when not canonical) by the
     * runtime; [mode] is the raw `FsWriteMode` (absent means `replace`).
     */
    data class FsWrite(
        val path: String,
        val data: String,
        val mode: String? = null,
        val offset: Long? = null,
        val ifRevision: String? = null,
        val ifAbsent: Boolean? = null,
    ) : Fs

    /** `fs.mkdir`. */
    data class FsMkdir(
        val path: String,
    ) : Fs

    /** `fs.remove`. */
    data class FsRemove(
        val path: String,
    ) : Fs

    /** `fs.move`. */
    data class FsMove(
        val fromPath: String,
        val toPath: String,
    ) : Fs

    /** `fs.watch`. */
    data class FsWatch(
        val path: String,
    ) : Fs

    /** `fs.unwatch`. */
    data class FsUnwatch(
        val watchId: String,
    ) : Fs
}
