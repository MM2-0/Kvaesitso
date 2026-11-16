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
package com.vitorpamplona.amethyst.commons.service.spellcheck

/**
 * Words people write on Nostr that a general dictionary does not know. Matched without regard to
 * case, so "Nostr", "nostr" and "NOSTR" all pass.
 */
object NostrVocabulary {
    val words: Set<String> =
        setOf(
            "amethyst",
            "bech32",
            "bitcoin",
            "bitcoiner",
            "bitcoiners",
            "blossom",
            "cashu",
            "damus",
            "dm",
            "dms",
            "ecash",
            "footstr",
            "geode",
            "gm",
            "gn",
            "hodl",
            "lnurl",
            "marmot",
            "mls",
            "naddr",
            "napplet",
            "napplets",
            "nevent",
            "nip",
            "nips",
            "nostr",
            "nostrich",
            "nostriches",
            "nprofile",
            "npub",
            "npubs",
            "nsec",
            "nsecs",
            "nsite",
            "nsites",
            "nutzap",
            "nutzaps",
            "onchain",
            "plebchain",
            "pleb",
            "plebs",
            "primal",
            "pv",
            "relay",
            "relays",
            "repost",
            "reposted",
            "reposts",
            "sat",
            "sats",
            "satoshi",
            "satoshis",
            "schnorr",
            "zap",
            "zapped",
            "zapper",
            "zappers",
            "zapping",
            "zaps",
        )

    fun contains(word: String): Boolean = word.lowercase() in words
}
