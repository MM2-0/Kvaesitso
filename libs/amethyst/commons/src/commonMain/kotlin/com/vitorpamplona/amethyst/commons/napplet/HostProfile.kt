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
 * The security posture a sandbox host renders under — the single decision that "locked nApplet vs
 * open nSite" actually *is*. Replaces the old scattered `websiteMode` boolean: resolved once in the
 * trusted main process, carried over the Intent/Messenger boundary as [name], and every coupled
 * consequence (capabilities, CSP, NIP-07, off-origin, network UI) reads from here — so they can't
 * drift out of sync, and a third posture is one new constant instead of several edited call sites.
 */
enum class HostProfile {
    /** NIP-5D napplet: locked sandbox — declared-only capabilities, strict app CSP, no off-origin, pinned to Tor. */
    NAPPLET,

    /** NIP-5A nSite ("website mode"): a normal web app — NIP-07 provider, no app CSP, off-origin + per-site Tor toggle. */
    WEBSITE,

    /**
     * A webxdc app: its own origin and storage, as a site has, but no network at all and nothing of
     * the user's — no NIP-07, no broker capabilities. Its `window.webxdc` is the messenger's own script.
     */
    WEBXDC,

    /**
     * The reader's front page (commons `frontpage`): one HTML document the app itself wrote and
     * checked, run with NO scripts — a sandboxed frame without `allow-scripts` under a CSP with no
     * script source — nothing of the user's, its art hotlinked from the open web the way a site's is,
     * and links out to the system browser.
     */
    PAPER,
    ;

    /**
     * What this posture is allowed to ask the broker for — THE security decision, minted into the
     * launch token in the trusted main process. A website gets the IDENTITY + RELAY + SIGNER set
     * NIP-07 needs (consent-gated); a locked napplet gets only what its manifest `requires` declares.
     *
     * SIGNER (NIP-44 encrypt/decrypt) is website-only by construction: no NIP-5D domain maps to it,
     * so [resolveRequiredCapabilities] can never produce it for a napplet however its manifest is
     * written. Without it a page can sign but not seal, which locks it out of NIP-17 and every other
     * gift-wrapped protocol.
     */
    fun declaredCapabilities(requires: List<String>): Set<NappletCapability> =
        when (this) {
            WEBSITE -> NappletCapability.WEBSITE_CAPABILITIES
            NAPPLET -> resolveRequiredCapabilities(requires).capabilities.toSet()
            WEBXDC, PAPER -> emptySet()
        }

    /** Strict app CSP for a locked applet (connect-src 'none', etc.) or a webxdc app (its own origin only); null for a website, which sets its own. */
    val appCsp: String?
        get() =
            when (this) {
                NAPPLET -> NappletWebContract.APP_CSP
                WEBXDC -> NappletWebContract.WEBXDC_CSP
                PAPER -> NappletWebContract.PAPER_CSP
                WEBSITE -> null
            }

    /** Runs at a real origin of its own ([NappletWebContract.appOrigin]), so its storage persists and stays its own. */
    val ownsOrigin: Boolean get() = this != NAPPLET

    /** Install the NIP-07 `window.nostr` provider before the shim runs. */
    val injectsNip07: Boolean get() = this == WEBSITE

    /** Let off-origin requests reach the network (a locked applet 404s them — its CSP is connect-src 'none'). */
    val allowsOffOrigin: Boolean get() = this == WEBSITE || this == PAPER

    /**
     * Apply the WebView SOCKS proxy and expose the Tor/network toggle. A website can re-route over Tor
     * or the open web; a locked napplet is pinned to Tor with no toggle.
     */
    val exposesNetwork: Boolean get() = this == WEBSITE || this == PAPER

    /** Whether anything the launch serves may run a script. A paper is a document, nothing more. */
    val runsScripts: Boolean get() = this != PAPER

    /**
     * Hand a link the user clicks to the system browser. A webxdc app never leaves the device: a link
     * carrying what it knows (its `selfAddr`, what was typed) would be its way out.
     */
    val opensLinks: Boolean get() = this != WEBXDC

    companion object {
        /** Reconstruct from the wire [name], defaulting to the locked [NAPPLET] posture on anything unknown. */
        fun fromName(name: String?): HostProfile = entries.firstOrNull { it.name == name } ?: NAPPLET
    }
}
