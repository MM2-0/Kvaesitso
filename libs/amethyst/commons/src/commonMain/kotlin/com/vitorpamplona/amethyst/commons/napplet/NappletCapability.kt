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

/**
 * The capability classes a napplet shell can broker, aligned with the upstream NAP domains
 * (`napplet/naps`, `@napplet/web`). A napplet declares the domains it needs via `R` and `O` tags;
 * [fromNapDomain] maps each bare domain string to the capability the broker enforces.
 *
 * The mapping is **default-deny**: an unrecognized or only partially implemented NAP domain maps
 * to `null`. Keeping a broker implementation below does not advertise conformance; only domains
 * whose current NAP contract is implemented are injected into `window.napplet`.
 */
enum class NappletCapability {
    /** `identity` — read-only identity queries (`getPublicKey`, `onChanged`). */
    IDENTITY,

    /**
     * `keys` — keyboard / command action binding (`registerAction` with a key combo, `onAction`).
     * This is **not** signing: the upstream `@napplet/shim` deliberately has no `sign()` method, and
     * napplets never get direct key access — only a `keys.action` push for their own named action.
     * Signing happens only inside the shell via [RELAY] `publish`.
     */
    KEYS,

    /** `relay` — publish (shell-signed), query, and subscribe to the user's relays. */
    RELAY,

    /**
     * NIP-44 encrypt/decrypt with the user's key, returning the ciphertext/plaintext to the caller
     * instead of publishing it. Needed by any standard Nostr web app that builds NIP-59 seals
     * itself (NIP-17 DMs, gift-wrapped app protocols) — signing alone cannot produce a seal.
     *
     * Deliberately **not** in [fromNapDomain]: no NIP-5D domain maps here, so a locked napplet can
     * never declare it. It is granted only to the website posture (the NIP-07 `window.nostr`
     * surface), where the page is already trusted with `signEvent`. Every individual call still
     * passes the per-operation signer ledger
     * ([Encrypt][com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerOp.Encrypt] /
     * [Decrypt][com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerOp.Decrypt]),
     * which is what actually keeps decryption behind a prompt.
     */
    SIGNER,

    /** `storage` — a per-applet sandboxed key-value store, namespaced by applet identity. */
    STORAGE,

    /** `value` — shell-mediated value transfer / zaps / invoice payment. */
    VALUE,

    /** `resource` — sandboxed fetching of https/blossom/nostr/data resources. */
    RESOURCE,

    /** `upload` — NAP-UPLOAD: shell-mediated blob upload on the `blossom` rail, signed and sent by the shell. */
    UPLOAD,

    /** `theme` — read the host's current theme colors (`theme.get`). Cosmetic, read-only, no consent. */
    THEME,

    /** `notify` — create/list/dismiss user-facing notifications (`notify.*`). */
    NOTIFY,

    /**
     * `inc` — NAP-INC: topics (`inc.emit` / `inc.event`) and point-to-point channels between the
     * napplets running in this app, routed by the host's `NappletIncBus` under runtime-assigned
     * endpoint identifiers. Authorized on the declaration alone, like the upstream reference shell.
     */
    INC,

    /**
     * `outbox` — NIP-65 aware reads and writes: the shell finds each author's relays, queries them,
     * deduplicates, and publishes shell-signed events to the user's write relays and to the inboxes
     * the napplet names (NAP-OUTBOX).
     */
    OUTBOX,

    /**
     * `fs` — NAP-FS, the files the user picks for the napplet: it asks the shell to show the system
     * picker, and reads or writes only what the user chose there ([com.vitorpamplona.amethyst.commons.napplet.fs.NappletFsSession]).
     * Consent covers asking to pick; the pick itself is the user's choice of what to share.
     */
    FS,

    /**
     * `common` — NAP-COMMON: public NIP-19 helpers, profile and follow-list reads, and the common
     * social actions (follow, unfollow, react, report). The shell builds and signs every event; the
     * napplet only names the targets, and each action reaches the signer prompt as the event it is.
     */
    COMMON,

    /**
     * `intent` — NAP-INTENT: hand a convention (`napplet:<archetype>/<action>`) to another napplet the
     * user has installed, which the shell starts and delivers it to. The user picks which napplet
     * handles each archetype; a napplet can never choose or change that.
     */
    INTENT,
    ;

    /**
     * Whether using this capability requires user consent. The cosmetic, read-only theme read
     * ([THEME]) never prompts; everything else does (subject to the broker's
     * signer-self-gating and standing-grant rules). [INC] is authorized at the router edge on its
     * declaration alone, so it never reaches the consent path regardless of this flag.
     */
    val requiresConsent: Boolean
        get() = this != THEME

    /**
     * Whether the user must confirm **every single use** — no standing auto-approval. True for
     * [VALUE]: a payment always prompts with the amount shown, so a napplet can never silently
     * move money.
     */
    val requiresPerUseConsent: Boolean
        get() = this == VALUE

    /** Whether a persistent "always allow" grant may be offered for, and kept for, this capability. */
    val canGrantAlways: Boolean
        get() = !requiresPerUseConsent

    /** Whether a session-long allow may be kept for this capability. */
    val canGrantSession: Boolean
        get() = !requiresPerUseConsent

    companion object {
        /**
         * What a page in the **website** posture (the NIP-07 `window.nostr` surface) may ask the
         * broker for. There are two independent mints of this set — the nSite host derives it from
         * `HostProfile.WEBSITE`, while the in-app browser mints a fresh per-origin token — so it
         * lives here, once: when the two drifted, the browser silently denied every call to a
         * capability the injected shim was still advertising.
         *
         * Widening this widens what any visited site can request, so it is a security decision, not
         * a convenience list.
         */
        val WEBSITE_CAPABILITIES: Set<NappletCapability> = setOf(IDENTITY, RELAY, SIGNER)

        /**
         * Maps a bare, currently supported NAP domain to the capability the broker enforces.
         * Returns `null` for unknown and partial/legacy domains — callers MUST treat that as
         * "unavailable, do not inject or grant". NIP-5D domain names are exact lowercase strings.
         */
        fun fromNapDomain(domain: String): NappletCapability? =
            when (domain) {
                "identity" -> IDENTITY
                "relay" -> RELAY
                "storage" -> STORAGE
                "resource" -> RESOURCE
                "theme" -> THEME
                "outbox" -> OUTBOX
                "fs" -> FS
                "inc" -> INC
                "upload" -> UPLOAD
                "common" -> COMMON
                "intent" -> INTENT
                else -> null
            }

        /** Exact NIP-5D domain names Amethyst currently exposes through its injection prelude. */
        val supportedNapDomains: Set<String> = setOf("identity", "relay", "storage", "resource", "theme", "outbox", "fs", "inc", "upload", "common", "intent")
    }
}

/** A NAP domain the manifest declared that this shell does not recognize. */
data class UnknownNapDomain(
    val domain: String,
)

/** Outcome of resolving a manifest's required domain list against the capabilities this shell brokers. */
data class NappletRequiredCapabilities(
    val capabilities: Set<NappletCapability>,
    val unknown: List<UnknownNapDomain>,
) {
    val hasUnknown: Boolean get() = unknown.isNotEmpty()
}

/**
 * Resolves a manifest's `requires` domains into the [NappletCapability] set this shell can
 * broker plus the list of domains it does not recognize (which the user must be warned about).
 */
fun resolveRequiredCapabilities(napDomains: List<String>): NappletRequiredCapabilities {
    val caps = mutableSetOf<NappletCapability>()
    val unknown = mutableListOf<UnknownNapDomain>()
    for (domain in napDomains) {
        val cap = NappletCapability.fromNapDomain(domain)
        if (cap != null) caps.add(cap) else unknown.add(UnknownNapDomain(domain))
    }
    return NappletRequiredCapabilities(caps, unknown)
}
