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

import com.vitorpamplona.crypto.sha2.sha256
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * The single source of truth for the napplet **web contract** — the trusted shell page, the injected
 * `window.napplet.*` shim, the internal origin/URLs, and the two Content-Security-Policies that keep
 * the applet boxed in. Every host (the Android `:napplet` WebView today, a desktop CEF host
 * tomorrow) serves these exact bytes and headers, so an applet's sandbox can't differ by platform.
 *
 * The HTML/JS themselves are compose resources in commonsUI, read through [NappletWebResources]
 * there; everything here is headless, so any host builds its pages from the same rules.
 */
object NappletWebContract {
    /** Internal host the trusted **shell** is served from (never the user's keys). */
    const val HOST = "napplet.local"
    const val ORIGIN = "https://napplet.local"

    /** The trusted shell document (top frame, on the shell [ORIGIN], where the native bridge lives). */
    const val SHELL_URL = "$ORIGIN/__shell__"

    /**
     * Per-site origin retained for Amethyst's NIP-5A WEBSITE profile. NIP-5D napplets never navigate
     * here: their verified, self-contained `/index.html` is assigned through `srcdoc` and therefore
     * executes with an opaque origin in an `allow-scripts`-only sandbox.
     */
    fun appOrigin(
        appId: String,
        profile: HostProfile = HostProfile.WEBSITE,
    ): String = if (profile == HostProfile.WEBXDC) "https://$appId.$WEBXDC_HOST" else "https://$appId.$HOST"

    /**
     * Where webxdc apps live, one subdomain each: a name of their own, so a host can take WebRTC from
     * every frame of every webxdc app (a browser extension's match pattern can name a domain, not a
     * kind of app) and leave it to nSites, whose network is theirs.
     */
    const val WEBXDC_HOST = "xdc.$HOST"

    /**
     * Stable, unique, DNS-label-safe id for an applet's sandbox origin: a sha256 of
     * `author:identifier`, hex, truncated to 31 chars and letter-prefixed (`n`). The leading letter
     * avoids any numeric-host parsing quirk and keeps it ≤63 chars (a valid DNS label). The same
     * applet always derives the same id, so its origin, and therefore its localStorage/IndexedDB,
     * persists across launches; different applets derive different subdomains, so their storage is
     * isolated from one another. A host whose browser keeps one storage jar for every account passes
     * that account as [partition], so each account gets its own origin.
     *
     * A webxdc app's seed starts with a name no site's can: a site's starts with a hex key, so a site
     * whose `d` tag spells a webxdc instance (`webxdc:<id>`) still can't land on the app's origin and
     * read what was typed into it, with the network the app never had.
     */
    fun appId(
        author: String,
        identifier: String,
        partition: String? = null,
        profile: HostProfile = HostProfile.NAPPLET,
    ): String {
        val base = if (partition == null) "$author:$identifier" else "$partition:$author:$identifier"
        val seed = if (profile == HostProfile.WEBXDC) "$WEBXDC_SEED_PREFIX$base" else base
        return "n" + sha256(seed.encodeToByteArray()).toHexKey().take(31)
    }

    private const val WEBXDC_SEED_PREFIX = "webxdc/"

    /** A filled-in shell page and the Content-Security-Policy header it must be served with. */
    class ShellPage(
        val html: ByteArray,
        val csp: String,
    )

    /**
     * The trusted shell for one launch: [shellHtml] with its placeholders filled for [profile].
     *
     * A NIP-5D napplet's identity is bound to the exact verified bytes that execute, so the host
     * resolves its sole `/index.html` and passes the prelude and those bytes as [artifactParts];
     * they reach the opaque-origin child through `srcdoc`, never a navigated `src`, base64-encoded
     * straight into the page with no copy in between. A NIP-5A website (null parts) keeps a
     * dedicated real origin ([appOrigin]) with normal storage and network, and NIP-07 behind the
     * consent broker. [head] goes first in the shell's head, before anything there runs (the
     * desktop's bridge).
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun shell(
        shellHtml: ByteArray,
        profile: HostProfile,
        appOrigin: String,
        artifactParts: List<ByteArray>?,
        head: String = "",
    ): ShellPage {
        val napplet = profile == HostProfile.NAPPLET
        val sandbox =
            when (profile) {
                HostProfile.NAPPLET -> "allow-scripts"
                HostProfile.WEBSITE -> "allow-scripts allow-same-origin"
                // Forms post to the app's own origin (its CSP's form-action); nothing else is added.
                HostProfile.WEBXDC -> "allow-scripts allow-same-origin allow-forms"
                // No scripts and an opaque origin: only its links may leave, to the system browser.
                HostProfile.PAPER -> "allow-popups allow-popups-to-escape-sandbox"
            }
        val frameSource = if (napplet) "'self'" else appOrigin
        val template =
            shellHtml
                .decodeToString()
                .replace(APP_ORIGIN_PLACEHOLDER, appOrigin)
                .replace(APP_SANDBOX_PLACEHOLDER, sandbox)
                .let { if (head.isEmpty()) it else it.replaceFirst("<head>", "<head>$head") }
        val before = template.substringBefore(APP_BOOTSTRAP_PLACEHOLDER).encodeToByteArray()
        val after = template.substringAfter(APP_BOOTSTRAP_PLACEHOLDER, "").encodeToByteArray()

        if (!napplet) {
            // A webxdc app opens at its root with index.html appended, as the spec requires.
            val entry = if (profile == HostProfile.WEBXDC || profile == HostProfile.PAPER) "/index.html" else "/"
            return ShellPage(before + "iframe.src = '$appOrigin$entry';".encodeToByteArray() + after, shellCsp(frameSource))
        }

        // NIP-5D identity is bound to the exact verified bytes that execute: they reach the
        // opaque-origin child as a srcdoc, never a navigated src. The shell binds that identity to
        // the one document the srcdoc makes, and to nothing that replaces it in the frame.
        val parts = requireNotNull(artifactParts) { "A napplet's shell needs its verified artifact" }
        val open = "bindOneDocument();var b=atob('".encodeToByteArray()
        val close =
            (
                "'),u=new Uint8Array(b.length);" +
                    "for(var j=0;j<b.length;j++)u[j]=b.charCodeAt(j);" +
                    "iframe.srcdoc=new TextDecoder('utf-8').decode(u);"
            ).encodeToByteArray()
        val length = parts.sumOf { it.size }
        val encodedLength = (length + 2) / 3 * 4
        val page = ByteArray(before.size + open.size + encodedLength + close.size + after.size)
        var at = 0
        before.copyInto(page, at)
        at += before.size
        open.copyInto(page, at)
        at += open.size
        at += base64Into(parts, page, at)
        close.copyInto(page, at)
        at += close.size
        after.copyInto(page, at)
        return ShellPage(page, shellCsp(frameSource))
    }

    /**
     * Base64-encodes the concatenation of [parts] into [destination] at [offset] without
     * concatenating them first: each part is encoded in whole three-byte groups, and the 1-2 bytes
     * left over at a part's end are carried into the next one. Returns the bytes written.
     */
    @OptIn(ExperimentalEncodingApi::class)
    private fun base64Into(
        parts: List<ByteArray>,
        destination: ByteArray,
        offset: Int,
    ): Int {
        var at = offset
        val carry = ByteArray(3)
        var carried = 0
        for (part in parts) {
            var from = 0
            if (carried > 0) {
                val take = minOf(3 - carried, part.size)
                part.copyInto(carry, carried, 0, take)
                carried += take
                from = take
                if (carried < 3) continue
                at += Base64.encodeIntoByteArray(carry, destination, at)
                carried = 0
            }
            val whole = from + (part.size - from) / 3 * 3
            if (whole > from) at += Base64.encodeIntoByteArray(part, destination, at, from, whole)
            part.copyInto(carry, 0, whole, part.size)
            carried = part.size - whole
        }
        if (carried > 0) at += Base64.encodeIntoByteArray(carry, destination, at, 0, carried)
        return at - offset
    }

    /** True for the shell host and any per-applet subdomain — i.e. everything we serve internally. */
    fun isInternalHost(host: String?): Boolean = host == HOST || (host != null && host.endsWith(".$HOST"))

    /** Placeholders in the shell page (`files/napplet/shell.html`) replaced by [shell]. */
    const val APP_ORIGIN_PLACEHOLDER = "__APP_ORIGIN__"
    const val APP_SANDBOX_PLACEHOLDER = "__APP_SANDBOX__"
    const val APP_BOOTSTRAP_PLACEHOLDER = "__APP_BOOTSTRAP__"

    /** Name of the origin-restricted native bridge the shell (and only the shell) can reach. */
    const val BRIDGE_NAME = "__nappletBridge"

    /**
     * CSP for the shell document: it may inline its own bridge script/style and frame **only this
     * applet's** origin, but has no network and cannot navigate or submit anywhere.
     *
     * A napplet's `srcdoc` frame inherits this policy on top of [APP_CSP], so the inline images and
     * fonts a one-file napplet carries (`data:`, `blob:`, which never reach the network) must be
     * allowed here as well, or they are blocked however [APP_CSP] reads.
     */
    fun shellCsp(frameSource: String): String =
        "default-src 'none'; script-src 'unsafe-inline' 'wasm-unsafe-eval'; style-src 'unsafe-inline'; " +
            "img-src data: blob:; font-src data:; " +
            "frame-src $frameSource; base-uri 'none'; form-action 'none'; frame-ancestors 'none'"

    /**
     * Conservative NIP-5D CSP injected as the first element of the verified napplet's `head` before
     * the runtime prelude. A `srcdoc` napplet has an opaque origin, so self-hosted subresources are
     * intentionally unavailable; a conforming napplet is one self-contained `/index.html`.
     */
    const val APP_CSP: String =
        "default-src 'none'; script-src 'unsafe-inline' 'wasm-unsafe-eval'; style-src 'unsafe-inline'; " +
            "img-src data: blob:; font-src data:; connect-src 'none'; worker-src 'none'; " +
            "child-src 'none'; frame-src 'none'; media-src 'none'; object-src 'none'; " +
            "manifest-src 'none'; base-uri 'none'; form-action 'none'"

    /**
     * The front page's CSP: no script source at all, its inline house stylesheet, and pictures from
     * the open web over https only (its art is hotlinked where its authors published it). Nothing
     * else loads, nothing posts, nothing frames.
     */
    const val PAPER_CSP: String =
        "default-src 'none'; style-src 'unsafe-inline'; img-src https:; " +
            "frame-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'"

    /**
     * A webxdc app's CSP: everything from its own origin (the archive, served locally) and inline
     * `data:`/`blob:`, nothing from anywhere else. Webxdc requires that an app has no Internet access.
     */
    const val WEBXDC_CSP: String =
        "default-src 'self' data: blob:; script-src 'self' 'unsafe-inline' 'unsafe-eval' 'wasm-unsafe-eval' data: blob:; " +
            "style-src 'self' 'unsafe-inline' data: blob:; img-src 'self' data: blob:; font-src 'self' data: blob:; " +
            "media-src 'self' data: blob:; connect-src 'self' data: blob:; worker-src 'self' data: blob:; " +
            "frame-src 'self' data: blob:; child-src 'self' data: blob:; object-src 'none'; " +
            "manifest-src 'self'; base-uri 'self'; form-action 'self'"

    /**
     * What goes before every page of a webxdc app: WebRTC taken away before any of its scripts run,
     * and nothing of the napplet runtime. The app loads its `webxdc.js` itself.
     *
     * A messenger runs a webxdc app as its top document, so apps treat `window.parent` as their own
     * (Quake III keeps its realtime channel there, across reloads). Here it runs in the shell's
     * frame, on another origin, and touching that parent throws: the app gets itself instead. Only
     * there: a frame the app makes of its own pages keeps its real, same-origin parent. `parent` is
     * replaceable; `top` is not, and stays the shell.
     */
    fun webxdcPrelude(): ByteArray = "<!doctype html><html><head><script>$WEBRTC_GUARD_JS$COOKIE_GUARD_JS$WEBXDC_PARENT_JS</script>".encodeToByteArray()

    private const val WEBXDC_PARENT_JS: String =
        "(function(){try{void window.parent.document;}catch(e){" +
            "try{Object.defineProperty(window,'parent',{value:window,writable:true,configurable:true});}catch(_){}}})();"

    /**
     * Takes WebRTC away from the frame it runs in, before any page script: a napplet has no network,
     * and WebRTC reaches one anyway (its TURN connections go through the browser's proxy, which an
     * open nSite may have routed out; Android's WebView sends its UDP around the proxy, and Tor, too).
     *
     * The constructors that open a connection become `undefined`, gone from the frame for good. A page
     * may still put its own in their place: webxdc games (Quake III) carry a stand-in that sends their
     * packets over `webxdc.joinRealtimeChannel`, and a read-only global made assigning it throw. The
     * description and candidate classes stay: they only hold an SDP or a candidate line, and such a
     * stand-in builds its offers with them.
     */
    const val WEBRTC_GUARD_JS: String =
        "(function(){var n=['RTCPeerConnection','webkitRTCPeerConnection','RTCDataChannel'," +
            "'RTCRtpSender','RTCRtpReceiver','RTCRtpTransceiver','RTCCertificate'];" +
            "for(var i=0;i<n.length;i++){try{Object.defineProperty(window,n[i],{value:undefined,writable:true,configurable:true});}catch(e){}}})();"

    /**
     * Takes cookies away from the frame it runs in, before any page script. Every app here lives under
     * one site, `napplet.local` (`.local` is not a public suffix), so a cookie an app sets on that
     * domain would reach every other app, account and route on the device: a tracking id one nSite
     * plants over Tor and another reads on the open web, or what a sealed webxdc app hands an nSite to
     * carry out. The host never sends `Set-Cookie`, and requests to other sites are third-party (their
     * cookies blocked), so scripts are the only writer: `document.cookie` reads empty and drops writes,
     * and `cookieStore` is gone. localStorage and IndexedDB are per origin and stay.
     */
    const val COOKIE_GUARD_JS: String =
        "(function(){try{Object.defineProperty(Document.prototype,'cookie',{get:function(){return '';},set:function(){},configurable:false});}catch(e){}" +
            "try{Object.defineProperty(window,'cookieStore',{value:undefined,writable:false,configurable:false});}catch(e){}" +
            "try{if(window.CookieStore){Object.defineProperty(window,'CookieStore',{value:undefined,writable:false,configurable:false});}}catch(e){}})();"

    /**
     * What a paper gets before its page: markup only, no script. It names the charset the server's
     * header already promises, because JCEF drops the header's charset and a page that names none
     * was read as Latin-1. A repeated doctype and head in the page are ignored by the parser.
     */
    const val PAPER_PRELUDE = "<!doctype html><html><head><meta charset=\"utf-8\">"

    /**
     * Injects host-owned policy and the runtime prelude before any authored element in `head`.
     * [locked] is the NIP-5D posture; WEBSITE callers deliberately retain Amethyst's NIP-07 and
     * normal-origin behavior. Only syntactically valid, explicitly authorized NAP domains are
     * projected onto `window.napplet` by the trusted [shimJs].
     */
    fun injectPrelude(
        html: ByteArray,
        shimJs: String,
        declaredDomains: List<String>,
        locked: Boolean,
        injectNip07: Boolean = false,
        imeProxy: Boolean = false,
    ): ByteArray = preludePrefix(shimJs, declaredDomains, locked, injectNip07, imeProxy) + html

    /**
     * The bytes [injectPrelude] puts before the page. It is a pure prefix, so a host that serves
     * many pages for one launch builds it once and prepends it, never decoding the page itself.
     */
    fun preludePrefix(
        shimJs: String,
        declaredDomains: List<String>,
        locked: Boolean,
        injectNip07: Boolean = false,
        imeProxy: Boolean = false,
    ): ByteArray {
        // A locked napplet also loses WebRTC in its own frame; the hosts cover the frames it makes. Every
        // page here is under napplet.local, so none keeps a cookie the other apps would see.
        val policy =
            if (locked) {
                "<meta http-equiv=\"Content-Security-Policy\" content=\"$APP_CSP\"><script>$WEBRTC_GUARD_JS$COOKIE_GUARD_JS</script>"
            } else {
                "<script>$COOKIE_GUARD_JS</script>"
            }
        val style = "<style>html,body{overscroll-behavior:none !important}</style>"
        val flags =
            "<script>window.__nappletNip07=$injectNip07;" +
                (if (imeProxy) "window.__nappletImeProxy=true;" else "") +
                "</script>"
        val safeDomains =
            declaredDomains
                .filter { it.matches(NAP_DOMAIN) && it in NappletCapability.supportedNapDomains }
                .distinct()
        val domainsJson = safeDomains.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
        val prelude = "$policy$style$flags<script>window.__nappletDomains=$domainsJson;$shimJs</script>"
        // Establish the parser's head before *any* authored input. Searching for "<head" is unsafe:
        // it may be in a comment, quoted attribute, or after an already executable script. HTML's
        // parser merges a subsequent html element's attributes and ignores a second head start tag;
        // the authored head end/body start still closes this head normally. A repeated doctype is
        // ignored. The original artifact bytes used for verification remain untouched by this step.
        return "<!doctype html><html><head>$prelude".encodeToByteArray()
    }

    private val NAP_DOMAIN = Regex("^[a-z][a-z0-9-]*$")
}
