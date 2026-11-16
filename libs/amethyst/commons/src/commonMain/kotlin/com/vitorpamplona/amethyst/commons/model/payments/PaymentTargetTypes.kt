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
package com.vitorpamplona.amethyst.commons.model.payments

/**
 * The NIP-A3 (`payto`) target-type vocabulary: how a free-text type string is
 * normalized, which types the app's own wallets already cover, and the URI a
 * type hands off to.
 *
 * Type strings come from user input ([PaymentTargetsViewModel.addTarget] only
 * trims and lowercases), so the space is unbounded — `iban`, `upi`, `pix`,
 * `swish` and whatever comes next all land here as themselves. The tables below
 * only collapse the aliases we know about; everything else passes through and
 * falls back to `payto://<type>/<authority>`.
 *
 * Single source of truth for three callers that would otherwise each keep their
 * own copy: the profile chips (which pay a target directly), the zap picker's
 * hand-off chip (which must exclude the types the wallet rails already own),
 * and the installed-app probe (which needs the URI before it has an authority).
 */
object PaymentTargetTypes {
    private const val CASHME = "cashme"
    private const val DOLLAR = "\$"

    /** Lightning-family types Amethyst can pay in-app through the Send Payment screen. */
    val LIGHTNING_TYPES = setOf("lightning", "ln", "lnurl")

    /** Bitcoin-family types the in-app on-chain wallet can pay directly. */
    val BITCOIN_TYPES = setOf("bitcoin", "btc", "onchain")

    /**
     * Alias -> family. Only collapses spellings of the *same* rail, so matching a
     * sender's `btc` against a recipient's `bitcoin` succeeds while `monero` and
     * `bitcoin` stay apart.
     */
    private val ALIASES =
        mapOf(
            "btc" to "bitcoin",
            "onchain" to "bitcoin",
            "ln" to "lightning",
            "lnurl" to "lightning",
            "eth" to "ethereum",
            "xmr" to "monero",
            "zec" to "zcash",
            "bch" to "bitcoincash",
            "ltc" to "litecoin",
            "doge" to "dogecoin",
            "sol" to "solana",
            "trx" to "tron",
            "xno" to "nano",
            // NIP-A3 names the Cash App cashtag type `cashme`; `cashapp` is what
            // Amethyst (and others) wrote before the spec settled on it.
            "cashapp" to "cashme",
        )

    /** Types whose hand-off is a web page rather than a registered URI scheme. */
    private val WEB_TYPES = setOf("cashme", "venmo", "paypal", "revolut")

    /** Types with a dedicated URI scheme, keyed by canonical name. */
    private val SCHEMES =
        mapOf(
            "bitcoin" to "bitcoin",
            "lightning" to "lightning",
            "liquid" to "liquidnetwork",
            "ethereum" to "ethereum",
            "monero" to "monero",
            "dash" to "dash",
            "zcash" to "zcash",
            "bitcoincash" to "bitcoincash",
            "litecoin" to "litecoin",
            "dogecoin" to "dogecoin",
            "solana" to "solana",
            "tron" to "tron",
            "nano" to "nano",
        )

    /**
     * Types whose scheme carries the address in a query parameter instead of the path:
     * a BIP-352 silent payment address goes in a BIP-321 `bitcoin:` URI's `sp` field.
     */
    private val QUERY_SCHEMES =
        mapOf(
            "bip352" to "bitcoin:?sp=",
        )

    private val WEB_HOSTS =
        mapOf(
            "cashme" to "https://cash.app/",
            "venmo" to "https://venmo.com/",
            "paypal" to "https://paypal.me/",
            "revolut" to "https://revolut.me/",
        )

    /**
     * Trims, lowercases and collapses known aliases onto one family name. The result is the
     * NIP-A3 spelling where the spec lists one (`cashapp` -> `cashme`), so it is also what a
     * new `payto` tag should carry.
     */
    fun canonical(rawType: String): String {
        val trimmed = rawType.trim().lowercase()
        return ALIASES[trimmed] ?: trimmed
    }

    /**
     * True when a wallet rail already on the zap picker owns this type. Lightning
     * and bitcoin targets ARE the Lightning and on-chain rails, so offering them
     * again as a hand-off would just draw a second bolt beside the first.
     */
    fun isWalletCovered(rawType: String): Boolean {
        val type = canonical(rawType)
        return type in LIGHTNING_TYPES || type in BITCOIN_TYPES
    }

    /**
     * True when the hand-off is an `https://` page. Any browser resolves those, so
     * they are never gated on an installed app — but for the same reason
     * `resolveActivity` would hand back the browser, so they cannot take an app
     * icon without the control probe.
     */
    fun isWebTarget(rawType: String): Boolean = canonical(rawType) in WEB_TYPES

    /**
     * The URI this target hands off to. Unknown types fall back to RFC 8905
     * `payto://<type>/<authority>`, which is why a single `payto` entry in the
     * manifest's `<queries>` covers the whole open-ended tail of the vocabulary.
     *
     * No `amount=` is ever emitted: zap presets are sats and there is no rate to
     * convert them with, so the amount is named in the receiving app.
     */
    fun uriFor(
        rawType: String,
        authority: String,
    ): String {
        val type = canonical(rawType)
        val value = authority.trim()
        SCHEMES[type]?.let { return "$it:$value" }
        QUERY_SCHEMES[type]?.let { return "$it$value" }
        if (type == CASHME) {
            // cash.app/$cashtag: NIP-A3 describes the `$`-prefixed tag, but accept a bare one.
            val cashtag = if (value.isEmpty() || value.startsWith(DOLLAR)) value else DOLLAR + value
            return WEB_HOSTS.getValue(CASHME) + cashtag
        }
        WEB_HOSTS[type]?.let { return "$it$value" }
        return "payto://$type/$value"
    }

    /**
     * Cache key for "can any installed app open this type?" — the URI with the
     * authority stripped, i.e. scheme plus host.
     *
     * Scheme alone would be too coarse: an app may declare
     * `android:scheme="payto" android:host="iban"`, and a scheme-only hit would
     * then wrongly claim `payto://upi/...` is handled too.
     */
    fun probeKeyFor(rawType: String): String = uriFor(rawType, "")
}
