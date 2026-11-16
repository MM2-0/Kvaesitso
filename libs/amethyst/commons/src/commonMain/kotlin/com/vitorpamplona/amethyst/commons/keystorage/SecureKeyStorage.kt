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

/**
 * Secure storage for private keys using platform-specific secure storage mechanisms.
 *
 * ## Platform Implementations
 *
 * **Android:** Android Keystore + EncryptedSharedPreferences (AES-256-GCM, StrongBox when available)
 *
 * **Desktop:** OS native credential managers (macOS Keychain, Windows Credential Manager, Linux Secret Service)
 * with encrypted file fallback.
 *
 * ## Security Considerations
 *
 * **String Memory Limitation:** Private keys are returned as String (hexadecimal format). Unlike char arrays,
 * Kotlin/JVM Strings are immutable and cannot be securely zeroed from memory after use. The private key
 * will remain in memory until garbage collected, which may be delayed. This is an inherent limitation of
 * the JVM platform.
 *
 * **Mitigation:** Private keys are only exposed through this API when needed for signing operations.
 * They are encrypted at rest on all platforms. For maximum security, minimize the time private keys
 * are held in memory and ensure they are dereferenced promptly after use.
 *
 * **Production Recommendation:** For high-security applications, consider using hardware security modules
 * (HSM) or secure enclaves (Android StrongBox, iOS Secure Enclave) that never expose private keys to
 * application memory.
 *
 * Use [SecureKeyStorage.create] factory method to create instances.
 */
expect class SecureKeyStorage private constructor() {
    companion object {
        /**
         * Creates a SecureKeyStorage instance.
         *
         * @param context Platform-specific context (required on Android, ignored on Desktop)
         * @return SecureKeyStorage instance
         */
        fun create(context: Any? = null): SecureKeyStorage
    }

    /**
     * Saves a private key securely for the given npub.
     *
     * @param npub The public key in npub (Bech32) format
     * @param privKeyHex The private key in hexadecimal format
     * @throws SecureStorageException if storage operation fails
     */
    suspend fun savePrivateKey(
        npub: String,
        privKeyHex: String,
    )

    /**
     * Retrieves a private key for the given npub.
     *
     * **Security Warning:** The returned String cannot be securely zeroed from memory (JVM limitation).
     * Dereference the returned value immediately after use to minimize exposure time.
     *
     * Callers that MUST distinguish "key does not exist" from "backend refused/locked/failed"
     * (for example, before generating a replacement key on disk) should use
     * [getPrivateKeyOrThrow] instead. This method returns null on any error and cannot
     * safely be used as an "is this the first launch?" probe.
     *
     * @param npub The public key in npub (Bech32) format
     * @return The private key in hexadecimal format, or null if not found
     * @throws SecureStorageException if retrieval operation fails
     */
    suspend fun getPrivateKey(npub: String): String?

    /**
     * Retrieves a private key for the given npub, distinguishing "definitively absent"
     * from any other failure mode.
     *
     * On success, returns the key. When the backend confirms the item does not exist,
     * returns null. Any other outcome (backend unavailable, user denied the OS prompt,
     * keychain locked, I/O error) throws [SecureStorageException]. This is the safe
     * primitive for compare-and-swap style flows where a null must not be interpreted
     * as permission to generate and persist a replacement.
     *
     * @param npub The public key in npub (Bech32) format
     * @return The private key in hexadecimal format, or null only when the backend
     *   confirms the item does not exist
     * @throws SecureStorageException on any ambiguous or transient failure
     */
    suspend fun getPrivateKeyOrThrow(npub: String): String?

    /**
     * Deletes a private key for the given npub.
     *
     * @param npub The public key in npub (Bech32) format
     * @return true if the key was deleted, false if it didn't exist
     * @throws SecureStorageException if deletion operation fails
     */
    suspend fun deletePrivateKey(npub: String): Boolean

    /**
     * Checks if a private key exists for the given npub.
     *
     * @param npub The public key in npub (Bech32) format
     * @return true if a private key exists, false otherwise
     */
    suspend fun hasPrivateKey(npub: String): Boolean
}

/**
 * Exception thrown when secure storage operations fail.
 */
open class SecureStorageException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * The key was not kept because the user chose so: they declined to keep keys on this device. Not a
 * failure to report; the key is asked for again next launch.
 */
class KeyNotKeptException(
    message: String,
) : SecureStorageException(message)

/**
 * The key was not kept because the key file is locked: the user skipped unlocking it this run. A
 * failure, not a choice: the account still keeps its key, and the one already stored stays.
 */
class KeyFileLockedException(
    message: String,
) : SecureStorageException(message)
