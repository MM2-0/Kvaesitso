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

import com.vitorpamplona.amethyst.commons.util.appDataDir
import com.vitorpamplona.amethyst.commons.util.restrictToOwner
import com.vitorpamplona.quartz.utils.Log
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Desktop [SecretEncryption]: the same AES-256-GCM as Android, but with the key
 * held in a file this OS user owns instead of in hardware.
 *
 * That difference is real and worth stating plainly. On Android the key lives
 * in the AndroidKeyStore and never enters app memory, so a copy of the data
 * files is useless without the device. Here the key sits next to the data,
 * readable by anything running as this user — encryption at rest that survives
 * a stolen disk or a careless backup, not a compromised account. The JVM has no
 * portable hardware-backed keystore to do better with; a per-OS keyring binding
 * (as `cli`'s SecretStore does for credentials) is the upgrade path.
 */
actual class SecretEncryption internal constructor(
    private val keyFile: File,
) {
    /** Production entry point: the key file this OS user owns. */
    actual constructor() : this(defaultKeyFile())

    companion object {
        private const val TAG = "SecretEncryption"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val ALGORITHM = "AES"
        private const val KEY_SIZE_BYTES = 32
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val KEY_FILE_NAME = "secret.key"

        internal fun defaultKeyFile(): File = File(appDataDir, KEY_FILE_NAME)
    }

    // A Cipher holds the state of the operation in progress, so two coroutines
    // encrypting through one instance would corrupt each other's output. One
    // per thread, matching the Android actual.
    private val ciphers = ThreadLocal.withInitial { Cipher.getInstance(TRANSFORMATION) }

    @Volatile
    private var cachedKey: SecretKey? = null

    private fun getKey(): SecretKey =
        cachedKey ?: synchronized(this) {
            cachedKey ?: loadOrCreateKey().also { cachedKey = it }
        }

    private fun loadOrCreateKey(): SecretKey {
        if (keyFile.exists()) {
            val bytes = keyFile.readBytes()
            if (bytes.size == KEY_SIZE_BYTES) return SecretKeySpec(bytes, ALGORITHM)
            // A truncated or padded key file cannot decrypt anything already
            // written; replacing it silently would strand that data under a key
            // nobody holds. Fail loudly instead.
            throw IllegalStateException(
                "Key file ${keyFile.absolutePath} is ${bytes.size} bytes, expected $KEY_SIZE_BYTES. " +
                    "Refusing to overwrite it — move it aside to start fresh.",
            )
        }
        return createKey()
    }

    private fun createKey(): SecretKey {
        Log.d(TAG) { "Creating a new AES key at ${keyFile.absolutePath}" }
        val bytes = ByteArray(KEY_SIZE_BYTES).also { SecureRandom().nextBytes(it) }

        keyFile.parentFile?.let { parent ->
            parent.mkdirs()
            parent.restrictToOwner(TAG)
        }
        // Narrow the file before the key goes in: created at the default umask
        // and chmodded afterwards, the key would be world-readable in between.
        keyFile.createNewFile()
        keyFile.restrictToOwner(TAG)
        keyFile.writeBytes(bytes)

        return SecretKeySpec(bytes, ALGORITHM)
    }

    actual fun encrypt(bytes: ByteArray): ByteArray {
        try {
            val cipher = ciphers.get()
            cipher.init(Cipher.ENCRYPT_MODE, getKey())
            return cipher.iv + cipher.doFinal(bytes)
        } catch (e: Exception) {
            cachedKey = null
            Log.e(TAG, "encrypt() failed: ${e.message}", e)
            throw e
        }
    }

    actual fun decrypt(bytes: ByteArray): ByteArray? {
        try {
            val iv = bytes.copyOfRange(0, GCM_IV_LENGTH)
            val data = bytes.copyOfRange(GCM_IV_LENGTH, bytes.size)
            val cipher = ciphers.get()
            cipher.init(Cipher.DECRYPT_MODE, getKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            return cipher.doFinal(data)
        } catch (e: Exception) {
            cachedKey = null
            Log.e(TAG, "decrypt() failed (input ${bytes.size} bytes): ${e.message}", e)
            throw e
        }
    }
}
