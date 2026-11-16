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

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class KeyStoreEncryption {
    companion object {
        private const val ALGORITHM = KeyProperties.KEY_ALGORITHM_AES
        private const val BLOCK_MODE = KeyProperties.BLOCK_MODE_GCM

        /**
         * GCM is a stream mode: it pads nothing, and `AES/GCM/NoPadding` is the
         * only transformation JCA defines for it. [LEGACY_PADDING] is what this
         * class has always asked for, and some providers accept it, so the keys
         * and ciphertext already on those devices were made under that name.
         */
        private const val LEGACY_PADDING = KeyProperties.ENCRYPTION_PADDING_PKCS7
        private const val GCM_PADDING = KeyProperties.ENCRYPTION_PADDING_NONE
        private const val PURPOSE = KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        private const val KEY_ALIAS = "AMETHYST_AES_KEY"

        /** GCM's authentication tag, in bits. 128 is the default and the maximum. */
        private const val TAG_BITS = 128

        /** GCM's nonce, in bytes — what [encrypt] prefixes to its output. */
        private const val IV_BYTES = 12

        private fun transformationFor(padding: String) = "$ALGORITHM/$BLOCK_MODE/$padding"

        /**
         * Which padding name this device's providers actually answer to.
         *
         * Asking for the legacy name where it works keeps every existing key
         * and every encrypted file readable — switching unconditionally would
         * orphan them, and one of them holds account secrets. Where no provider
         * offers it (seen on a Samsung API 34 tablet, where `Cipher.getInstance`
         * throws `NoSuchAlgorithmException`), the correct GCM name is used
         * instead. Before this, that throw propagated out of the constructor:
         * callers that guarded it silently lost persistence, and the one that
         * did not — cordn, from `Account`'s constructor — hung the app on
         * "Loading account" forever.
         *
         * Resolved once per process: the answer cannot change under us, and
         * every instance would otherwise repeat the failing lookup.
         */
        private val resolvedPadding: String by lazy {
            try {
                Cipher.getInstance(transformationFor(LEGACY_PADDING))
                LEGACY_PADDING
            } catch (_: GeneralSecurityException) {
                GCM_PADDING
            }
        }
    }

    private val padding = resolvedPadding

    // One Cipher per thread rather than one shared instance: a Cipher holds the
    // state of the operation in progress, so two callers on different threads
    // through the same object would corrupt each other's output. The
    // transformation is resolved once, off [resolvedPadding], so every thread's
    // instance is built with the spelling this device actually offers.
    // withInitial never yields null, but Android's SDK annotates ThreadLocal.get() as nullable,
    // hence the `!!` where it is read.
    private val ciphers = ThreadLocal.withInitial { Cipher.getInstance(transformationFor(padding)) }

    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    // The handle never changes for the life of the alias, but fetching it is a
    // round trip to the keystore daemon — not something to pay per operation.
    @Volatile
    private var cachedKey: SecretKey? = null

    private fun getKey(): SecretKey =
        cachedKey ?: synchronized(this) {
            cachedKey ?: loadOrCreateKey().also { cachedKey = it }
        }

    private fun loadOrCreateKey(): SecretKey {
        val existingKey = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        return existingKey?.secretKey ?: createKey()
    }

    private fun createKeyStrongBoxIfAvailable(): SecretKey? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val keyParams =
                    KeyGenParameterSpec
                        .Builder(KEY_ALIAS, PURPOSE)
                        .setBlockModes(BLOCK_MODE)
                        .setEncryptionPaddings(padding)
                        .setIsStrongBoxBacked(true)
                        .build()

                val generator = KeyGenerator.getInstance(ALGORITHM, "AndroidKeyStore")
                generator.init(keyParams)
                generator.generateKey()
            } catch (_: StrongBoxUnavailableException) {
                null
            }
        } else {
            null
        }

    private fun createKeyRegular(): SecretKey {
        val keyParams =
            KeyGenParameterSpec
                .Builder(KEY_ALIAS, PURPOSE)
                .setBlockModes(BLOCK_MODE)
                .setEncryptionPaddings(padding)
                .build()

        val generator = KeyGenerator.getInstance(ALGORITHM, "AndroidKeyStore")
        generator.init(keyParams)
        return generator.generateKey()
    }

    private fun createKey(): SecretKey = createKeyStrongBoxIfAvailable() ?: createKeyRegular()

    fun encrypt(bytes: ByteArray): ByteArray {
        try {
            // Initializes the cipher in encrypt mode and encrypts data
            val cipher = ciphers.get()!!
            cipher.init(Cipher.ENCRYPT_MODE, getKey())
            val iv = cipher.iv
            val encrypted = cipher.doFinal(bytes)
            return iv + encrypted
        } catch (e: Exception) {
            // A key the system has retired (a wipe, a credential reset) keeps
            // failing until it is re-read, so the cached handle goes with it.
            cachedKey = null
            throw e
        }
    }

    fun decrypt(bytes: ByteArray): ByteArray {
        try {
            val iv = bytes.copyOfRange(0, IV_BYTES)
            val data = bytes.copyOfRange(IV_BYTES, bytes.size)
            val cipher = ciphers.get()!!
            // A GCMParameterSpec, not an IvParameterSpec. [BLOCK_MODE] is GCM,
            // and AndroidKeyStore's GCM implementation rejects anything else
            // outright — "Only GCMParameterSpec supported". Encrypting never
            // noticed, because that direction lets the cipher choose its own
            // nonce and passes no spec at all, so every store built on this
            // class could write bytes it was then unable to read back: cordn
            // lost its coordinators on each launch, and Marmot and the
            // encrypted DataStore read nothing they had saved.
            cipher.init(Cipher.DECRYPT_MODE, getKey(), GCMParameterSpec(TAG_BITS, iv))
            return cipher.doFinal(data)
        } catch (e: Exception) {
            cachedKey = null
            throw e
        }
    }
}
