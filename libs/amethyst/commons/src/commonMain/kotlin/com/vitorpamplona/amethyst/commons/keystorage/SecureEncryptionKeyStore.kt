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
package com.vitorpamplona.amethyst.commons.keystorage

import com.vitorpamplona.quartz.experimental.decoupling.store.EncryptionKeyStore
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArrayOrNull
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.Nip01Crypto

/**
 * The persistent NIP-4E [EncryptionKeyStore]: each encryption private key is kept in the platform's
 * secure storage ([SecureKeyStorage] — Android Keystore, the OS keychain on desktop, the iOS
 * Keychain) next to the account keys, under its own alias `nip4e:<owner>:<encryption pubkey>`.
 *
 * These keys deserve the account key's protection: a transferred encryption key cannot be
 * re-derived, so losing it means another device has to send it again, and leaking it opens every
 * message encrypted to it.
 *
 * Talks to the storage through [SecretSlots] so tests can run against memory instead of the user's
 * real keychain; production code uses [over].
 */
class SecureEncryptionKeyStore(
    private val slots: SecretSlots,
) : EncryptionKeyStore {
    /** The three operations this store needs from a secret storage, by opaque alias. */
    interface SecretSlots {
        suspend fun save(
            alias: String,
            secretHex: String,
        )

        suspend fun load(alias: String): String?

        suspend fun delete(alias: String): Boolean
    }

    /**
     * A stored value that is not a valid key for its own alias (corrupted, or written by something
     * else) reads as absent rather than as a wrong key: encrypting with it would produce messages
     * nobody announced a key for.
     */
    override suspend fun get(
        owner: HexKey,
        encryptionPubKey: HexKey,
    ): ByteArray? {
        val secret = slots.load(alias(owner, encryptionPubKey))?.hexToByteArrayOrNull() ?: return null
        if (secret.size != 32) return null
        val derived =
            try {
                Nip01Crypto.pubKeyCreate(secret).toHexKey()
            } catch (e: Exception) {
                return null
            }
        return if (derived == encryptionPubKey.lowercase()) secret else null
    }

    override suspend fun put(
        owner: HexKey,
        encryptionPrivKey: ByteArray,
    ): HexKey {
        val pubKey = Nip01Crypto.pubKeyCreate(encryptionPrivKey).toHexKey()
        slots.save(alias(owner, pubKey), encryptionPrivKey.toHexKey())
        return pubKey
    }

    override suspend fun delete(
        owner: HexKey,
        encryptionPubKey: HexKey,
    ): Boolean = slots.delete(alias(owner, encryptionPubKey))

    companion object {
        const val ALIAS_PREFIX = "nip4e:"

        /** The storage alias of one key: never an npub, so it cannot collide with an account key. */
        fun alias(
            owner: HexKey,
            encryptionPubKey: HexKey,
        ) = "$ALIAS_PREFIX${owner.lowercase()}:${encryptionPubKey.lowercase()}"

        /** The production store, over the platform's [SecureKeyStorage]. */
        fun over(storage: SecureKeyStorage) =
            SecureEncryptionKeyStore(
                object : SecretSlots {
                    override suspend fun save(
                        alias: String,
                        secretHex: String,
                    ) = storage.savePrivateKey(alias, secretHex)

                    override suspend fun load(alias: String) = storage.getPrivateKey(alias)

                    override suspend fun delete(alias: String) = storage.deletePrivateKey(alias)
                },
            )
    }
}
