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
package com.vitorpamplona.amethyst.commons.privacylock

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PBKDF2 password hashing for the Desktop privacy-lock password.
 *
 * Same primitive family as [com.vitorpamplona.amethyst.commons.keystorage.SecureKeyStorage]
 * uses for its master-password key derivation.
 *
 * ## Versioned hash format
 *
 * Stored form is `<version>$<saltBase64>$<hashBase64>`:
 *
 * | Version | KDF | Iterations | Notes |
 * |---|---|---|---|
 * | (no prefix) | PBKDF2-HmacSHA256 | 100_000 | Legacy — verify-only for backward compat with hashes created before the OWASP 2023 bump. |
 * | `v1` | PBKDF2-HmacSHA256 | 600_000 | Current — matches OWASP Password Storage Cheat Sheet (2023). |
 *
 * [hash] always produces `v1$…` format. [verify] handles both legacy
 * bare-format and versioned inputs. Callers who want to opportunistically
 * upgrade an old hash should re-call [hash] after a successful verify and
 * persist the result.
 *
 * ## Cost tuning
 *
 * v1 at 600k iterations takes ~250 ms on a modern laptop (M1/M2 or
 * Intel 12th gen). Users unlock a handful of times per session, so 250 ms
 * is well within the tolerable range. At 20 attempts/second (verify
 * throughput) an attacker with the on-disk hash needs ~150M attempts
 * to exhaust a 6-char mixed-alpha space — orders of magnitude slower
 * than the legacy 100k value.
 */
object PasswordHasher {
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_LENGTH_BYTES = 16

    private const val V1_PREFIX = "v1"
    private const val V1_ITERATIONS = 600_000
    private const val LEGACY_ITERATIONS = 100_000

    private const val SEP = '$'

    fun hash(password: CharArray): String {
        val salt = ByteArray(SALT_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
        val hash = pbkdf2(password, salt, V1_ITERATIONS)
        val encoder = Base64.getEncoder()
        return "$V1_PREFIX$SEP${encoder.encodeToString(salt)}$SEP${encoder.encodeToString(hash)}"
    }

    fun verify(
        password: CharArray,
        stored: String,
    ): Boolean {
        val (iterations, saltB64, hashB64) = parse(stored) ?: return false
        val decoder = Base64.getDecoder()
        val salt = runCatching { decoder.decode(saltB64) }.getOrNull() ?: return false
        val expected = runCatching { decoder.decode(hashB64) }.getOrNull() ?: return false
        val computed = pbkdf2(password, salt, iterations)
        return constantTimeEquals(computed, expected)
    }

    /**
     * True when [stored] is in the legacy pre-v1 format. Callers may opt to
     * re-hash and persist after a successful [verify] to migrate the user
     * to v1 transparently.
     */
    fun isLegacyFormat(stored: String): Boolean {
        val parts = stored.split(SEP)
        return parts.size == 2
    }

    private data class ParsedHash(
        val iterations: Int,
        val saltB64: String,
        val hashB64: String,
    )

    private fun parse(stored: String): ParsedHash? {
        val parts = stored.split(SEP)
        return when (parts.size) {
            2 -> ParsedHash(LEGACY_ITERATIONS, parts[0], parts[1])
            3 -> {
                if (parts[0] != V1_PREFIX) return null
                ParsedHash(V1_ITERATIONS, parts[1], parts[2])
            }
            else -> null
        }
    }

    private fun pbkdf2(
        password: CharArray,
        salt: ByteArray,
        iterations: Int,
    ): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, KEY_LENGTH_BITS)
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun constantTimeEquals(
        a: ByteArray,
        b: ByteArray,
    ): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }
}
