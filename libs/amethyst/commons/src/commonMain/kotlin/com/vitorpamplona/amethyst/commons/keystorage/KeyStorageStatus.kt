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

/** The OS keyrings the desktop can keep keys in. */
enum class OsKeyring {
    MAC_KEYCHAIN,
    SECRET_SERVICE,
    WINDOWS_CREDENTIALS,
    KWALLET,
}

/**
 * Where this device keeps private keys, and whether that works right now. Shown in Settings ›
 * Key storage and on the login screen, so a key that cannot be kept is never a silent failure.
 */
sealed interface KeyStorageStatus {
    /** Not opened yet: nothing has needed a key. */
    data object Unknown : KeyStorageStatus

    /** The OS keyring holds the keys and answered the last request; [keyring] is null when unknown. */
    data class Keyring(
        val keyring: OsKeyring?,
    ) : KeyStorageStatus

    /**
     * The OS keyring exists but refused or failed: a denied prompt, a locked keyring, a D-Bus or
     * Keychain error. Keys it holds are still there; asking again may work.
     */
    data class KeyringUnreachable(
        val keyring: OsKeyring?,
        val detail: String,
    ) : KeyStorageStatus

    /** No OS keyring: keys are kept in a password-protected (NIP-49) file on this device. */
    data class PasswordFile(
        val hasPassword: Boolean,
        val unlocked: Boolean,
    ) : KeyStorageStatus

    /** No OS keyring, and the user chose not to keep keys in a password file. */
    data object NotKept : KeyStorageStatus
}

/** Whether a private key given now would be kept on this device without asking anything. */
fun KeyStorageStatus.keepsKeys(): Boolean =
    when (this) {
        is KeyStorageStatus.Keyring -> true
        is KeyStorageStatus.PasswordFile -> true
        KeyStorageStatus.Unknown -> true
        is KeyStorageStatus.KeyringUnreachable -> false
        KeyStorageStatus.NotKept -> false
    }
