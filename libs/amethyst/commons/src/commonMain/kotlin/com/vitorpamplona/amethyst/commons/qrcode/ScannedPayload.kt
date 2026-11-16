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
package com.vitorpamplona.amethyst.commons.qrcode

import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.Entity
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip19Bech32.entities.NSec

/**
 * What a decoded QR string *is*, independent of whether any particular screen can act on it.
 *
 * The scanner needs this separately from routing: `uriToRoute` answers "where does this
 * navigate", and collapses everything it doesn't recognise into `null` — which is how a
 * perfectly good scan of an unsupported payload became indistinguishable from the camera never
 * reading anything (issue #417). Classifying first lets the UI say *which* of those happened.
 *
 * Pure Kotlin on purpose: no Android, no `LocalCache`, no navigation. It is the one piece of the
 * scanner that can be exhaustively unit-tested on the JVM.
 */
sealed interface ScannedPayload {
    /** Exactly what the decoder read, trimmed. */
    val raw: String

    /**
     * True when [raw] carries key material or a pairing secret — an `nsec`, an `ncryptsec`, a
     * wallet-connect URI (its `secret=` is spendable), or a NIP-46 URI (its `secret=` authorises
     * a signer). Any UI that echoes a payload back to the screen, copies it, or logs it MUST
     * check this first. A QR code is scanned in public, over someone's shoulder, by definition.
     */
    val containsSecret: Boolean
        get() = false

    /** A NIP-19 entity, with or without the `nostr:` prefix. */
    data class Nostr(
        override val raw: String,
        val entity: Entity,
    ) : ScannedPayload {
        override val containsSecret get() = entity is NSec
    }

    /** `nostrconnect://` — an app asking our signer to connect. Carries a `secret`. */
    data class NostrConnect(
        override val raw: String,
    ) : ScannedPayload {
        override val containsSecret get() = true
    }

    /** `bunker://` — a remote signer offering itself. Carries a `secret`. */
    data class Bunker(
        override val raw: String,
    ) : ScannedPayload {
        override val containsSecret get() = true
    }

    /** `nostr+walletconnect://` and friends. Carries a spendable `secret`. */
    data class WalletConnect(
        override val raw: String,
    ) : ScannedPayload {
        override val containsSecret get() = true
    }

    /** A BOLT-11 invoice, an LNURL, or a `lightning:` URI. */
    data class Lightning(
        override val raw: String,
    ) : ScannedPayload

    /** A Cashu token or payment request. Bearer money — never echo it. */
    data class Cashu(
        override val raw: String,
    ) : ScannedPayload {
        override val containsSecret get() = true
    }

    /**
     * An `http(s)://` link. [url] is [raw] with the scheme and host lowercased: a QR code's
     * alphanumeric mode can only carry uppercase, so generators that use it hand out
     * `HTTPS://EXAMPLE.COM/...`, and Android matches intent-filter schemes case-sensitively — an
     * uppercase scheme reaches no browser at all.
     */
    data class Web(
        override val raw: String,
        val url: String,
    ) : ScannedPayload

    /**
     * A bare 64-character hex key with no bech32 wrapper — what issue #417 hit in 2023 and what
     * every "copy the pubkey" web tool still produces. [npub] is the same 32 bytes encoded as a
     * pubkey, so a caller that knows it is one can hand it to the normal NIP-19 routing path.
     *
     * Marked secret because nothing in the string says which kind of key it is: a raw private
     * key is the same 64 hex characters, and key generators and backups export exactly that.
     * Opening one as a profile sends it to relays as an author filter, so it is never routed
     * without the user saying it is a public key, and never echoed onto the screen.
     */
    data class HexKey(
        override val raw: String,
        val npub: String,
    ) : ScannedPayload {
        override val containsSecret get() = true
    }

    /**
     * Key material we can recognise but not decode. Two things land here:
     *
     * - an `ncryptsec`, which `Nip19Parser` lists in its regex but has no branch to parse;
     * - an `nsec` the parser rejected — truncated by a half-finished copy, or transcribed with a
     *   typo into whatever generated the code. It is still a private key, and a damaged one
     *   still shows all but a few of its characters.
     *
     * Both are classified by *prefix*, not by parse success: whether a payload is dangerous to
     * display cannot depend on whether we happen to be able to read it.
     */
    data class PrivateKey(
        override val raw: String,
    ) : ScannedPayload {
        override val containsSecret get() = true
    }

    /** Decoded fine, but it is not anything Amethyst knows how to act on. */
    data class Unknown(
        override val raw: String,
    ) : ScannedPayload
}

private val HEX_64 = Regex("^[0-9a-fA-F]{64}$")

private val LIGHTNING_PREFIXES = listOf("lightning:", "lnbc", "lntb", "lnbcrt", "lnurl")

private val WALLET_CONNECT_PREFIXES =
    listOf(
        "nostr+walletconnect:",
        "nostrwalletconnect:",
        "nostr+walletconnect://",
        "amethyst+walletconnect:",
    )

/**
 * Classify a decoded QR string.
 *
 * Scheme checks run before the NIP-19 scan on purpose: a `bunker://` or `nostrconnect://` URI
 * embeds a hex pubkey and relay URLs, and letting the (deliberately permissive) NIP-19 regex
 * look at those first risks matching a bech32-shaped fragment out of a relay path and routing
 * somewhere absurd.
 */
fun classifyScannedPayload(text: String): ScannedPayload {
    val raw = text.trim()
    if (raw.isEmpty()) return ScannedPayload.Unknown(raw)

    val lower = raw.lowercase()

    if (lower.startsWith("bunker:")) return ScannedPayload.Bunker(raw)
    if (lower.startsWith("nostrconnect:")) return ScannedPayload.NostrConnect(raw)
    if (WALLET_CONNECT_PREFIXES.any { lower.startsWith(it) }) return ScannedPayload.WalletConnect(raw)
    if (lower.startsWith("cashu") || lower.startsWith("creq")) return ScannedPayload.Cashu(raw)

    // Before the NIP-19 scan, and by prefix rather than by parse: an ncryptsec cannot be decoded
    // here, so waiting to find out what it is would mean deciding it is harmless.
    if (lower.startsWith("ncryptsec1") || lower.startsWith("nostr:ncryptsec1")) {
        return ScannedPayload.PrivateKey(raw)
    }
    if (LIGHTNING_PREFIXES.any { lower.startsWith(it) }) return ScannedPayload.Lightning(raw)

    Nip19Parser.uriToRoute(raw)?.let { return ScannedPayload.Nostr(raw, it.entity) }

    // An nsec the parser would not take. The parse is tried first so a well-formed one still
    // becomes a [ScannedPayload.Nostr] and keeps the routing that logging in by scanning one
    // depends on — but a damaged one must not fall through to [ScannedPayload.Unknown], where
    // the sheet prints the payload on screen with a Copy button next to it.
    if (lower.startsWith("nsec1") || lower.startsWith("nostr:nsec1")) return ScannedPayload.PrivateKey(raw)

    if (HEX_64.matches(raw)) {
        val npub = runCatching { NPub.create(raw.lowercase()) }.getOrNull()
        if (npub != null) return ScannedPayload.HexKey(raw, npub)
    }

    if (lower.startsWith("http://") || lower.startsWith("https://")) {
        return ScannedPayload.Web(raw, normalizeSchemeAndHost(raw))
    }

    return ScannedPayload.Unknown(raw)
}

/**
 * Lowercases a URL's scheme and host, the two parts that are case-insensitive by spec, and leaves
 * the path, query and fragment alone — those are the server's business and often case-sensitive.
 * An authority carrying userinfo (`user:pass@host`) keeps its case: userinfo is case-sensitive,
 * and splitting it out reliably is more than a scanned link needs.
 */
private fun normalizeSchemeAndHost(url: String): String {
    val schemeEnd = url.indexOf("://")
    if (schemeEnd < 0) return url
    val authorityStart = schemeEnd + 3
    val authorityEnd =
        url
            .indexOfAny(charArrayOf('/', '?', '#'), authorityStart)
            .let { if (it < 0) url.length else it }
    val authority = url.substring(authorityStart, authorityEnd)
    val host = if ('@' in authority) authority else authority.lowercase()
    return url.substring(0, schemeEnd).lowercase() + "://" + host + url.substring(authorityEnd)
}
