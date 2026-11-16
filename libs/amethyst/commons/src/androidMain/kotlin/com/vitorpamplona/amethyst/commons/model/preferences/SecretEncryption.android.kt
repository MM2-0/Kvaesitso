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
package com.vitorpamplona.amethyst.commons.model.preferences

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import com.vitorpamplona.quartz.utils.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec

actual class SecretEncryption {
    companion object {
        private const val TAG = "SecretEncryption"
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val ALGORITHM = KeyProperties.KEY_ALGORITHM_AES
        private const val BLOCK_MODE = KeyProperties.BLOCK_MODE_GCM
        private const val PADDING = KeyProperties.ENCRYPTION_PADDING_NONE
        private const val TRANSFORMATION = "$ALGORITHM/$BLOCK_MODE/$PADDING"
        private const val PURPOSE = KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        private const val KEY_ALIAS = "AMETHYST_AES_KEY"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH_BITS = 128
    }

    // One Cipher per thread rather than one shared instance. A Cipher carries
    // the state of the operation in progress, so two coroutines encrypting on
    // different Dispatchers.IO threads through the same object would corrupt
    // each other's output.
    // withInitial never yields null, but Android's SDK annotates ThreadLocal.get() as nullable,
    // hence the `!!` where it is read.
    private val ciphers = ThreadLocal.withInitial { Cipher.getInstance(TRANSFORMATION) }

    private val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

    // The key handle never changes for the life of the alias, but fetching it
    // is a round trip to the keystore daemon. Every encrypted store here reads
    // and writes through this class, so doing that per operation put an IPC in
    // front of every Marmot group-state write and every message appended.
    @Volatile
    private var cachedKey: SecretKey? = null

    private fun getKey(): SecretKey =
        cachedKey ?: synchronized(this) {
            cachedKey ?: loadOrCreateKey().also { cachedKey = it }
        }

    private fun loadOrCreateKey(): SecretKey {
        val existingKey = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        return (existingKey?.secretKey ?: createKey()).also { logSecurityLevel(it) }
    }

    /**
     * Say once, per process, where this key actually lives.
     *
     * It decides the cost of everything encrypted at rest here, and it is not
     * knowable from the code: [createKeyStrongBoxIfAvailable] is tried first, so
     * a device with a secure element gets one, and `getEntry` then returns
     * whatever that device created — possibly years ago, under different code.
     *
     * The difference is not small. A secure element runs AES-GCM at roughly
     * 68 KB/s (1 MiB in ~15s on a Pixel 8), against tens of MB/s for the TEE.
     * Anything bulk that shows up slow on one device and fine on another is
     * explained by this line.
     */
    private fun logSecurityLevel(key: SecretKey) {
        try {
            val factory = SecretKeyFactory.getInstance(key.algorithm, ANDROID_KEY_STORE)
            val info = factory.getKeySpec(key, KeyInfo::class.java) as KeyInfo
            val level =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    when (info.securityLevel) {
                        KeyProperties.SECURITY_LEVEL_STRONGBOX -> "STRONGBOX (bulk crypto here is ~68 KB/s)"
                        KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> "TEE"
                        KeyProperties.SECURITY_LEVEL_SOFTWARE -> "SOFTWARE"
                        else -> "UNKNOWN(${info.securityLevel})"
                    }
                } else {
                    @Suppress("DEPRECATION")
                    if (info.isInsideSecureHardware) "SECURE_HARDWARE (TEE or StrongBox)" else "SOFTWARE"
                }
            Log.i(TAG) { "$KEY_ALIAS security level: $level" }
        } catch (e: Exception) {
            // Purely diagnostic — never let it interfere with having a key.
            Log.d(TAG) { "Could not determine the security level of $KEY_ALIAS: ${e.message}" }
        }
    }

    private fun createKeyStrongBoxIfAvailable(): SecretKey? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val keyParams =
                    KeyGenParameterSpec
                        .Builder(KEY_ALIAS, PURPOSE)
                        .setBlockModes(BLOCK_MODE)
                        .setEncryptionPaddings(PADDING)
                        .setIsStrongBoxBacked(true)
                        .build()

                val generator = KeyGenerator.getInstance(ALGORITHM, ANDROID_KEY_STORE)
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
                .setEncryptionPaddings(PADDING)
                .build()

        val generator = KeyGenerator.getInstance(ALGORITHM, ANDROID_KEY_STORE)
        generator.init(keyParams)
        return generator.generateKey()
    }

    private fun createKey(): SecretKey {
        Log.d(TAG) { "Creating new AES key in AndroidKeyStore (alias=$KEY_ALIAS)" }
        return createKeyStrongBoxIfAvailable() ?: createKeyRegular()
    }

    actual fun encrypt(bytes: ByteArray): ByteArray {
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
            Log.e(TAG, "encrypt() failed: ${e.message}", e)
            throw e
        }
    }

    actual fun decrypt(bytes: ByteArray): ByteArray? {
        try {
            // Extract the 12-byte GCM IV prefix and decrypt the remainder. The
            // AndroidKeyStore cipher only accepts GCMParameterSpec (not a plain
            // IvParameterSpec), so we must pass the 128-bit auth tag length.
            val iv = bytes.copyOfRange(0, GCM_IV_LENGTH)
            val data = bytes.copyOfRange(GCM_IV_LENGTH, bytes.size)
            val cipher = ciphers.get()!!
            cipher.init(Cipher.DECRYPT_MODE, getKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            return cipher.doFinal(data)
        } catch (e: Exception) {
            cachedKey = null
            Log.e(TAG, "decrypt() failed (input ${bytes.size} bytes): ${e.message}", e)
            throw e
        }
    }
}
