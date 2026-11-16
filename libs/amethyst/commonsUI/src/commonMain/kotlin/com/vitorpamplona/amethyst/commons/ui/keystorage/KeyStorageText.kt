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
package com.vitorpamplona.amethyst.commons.ui.keystorage

import androidx.compose.runtime.Composable
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.keystorage.KeyStorageStatus
import com.vitorpamplona.amethyst.commons.keystorage.OsKeyring
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.key_storage_keyring_kwallet
import com.vitorpamplona.amethyst.commons.resources.key_storage_keyring_mac
import com.vitorpamplona.amethyst.commons.resources.key_storage_keyring_secret_service
import com.vitorpamplona.amethyst.commons.resources.key_storage_keyring_windows
import com.vitorpamplona.amethyst.commons.resources.key_storage_status_keyring
import com.vitorpamplona.amethyst.commons.resources.key_storage_status_not_kept
import com.vitorpamplona.amethyst.commons.resources.key_storage_status_password_locked
import com.vitorpamplona.amethyst.commons.resources.key_storage_status_password_new
import com.vitorpamplona.amethyst.commons.resources.key_storage_status_password_unlocked
import com.vitorpamplona.amethyst.commons.resources.key_storage_status_unknown
import com.vitorpamplona.amethyst.commons.resources.key_storage_status_unreachable
import com.vitorpamplona.amethyst.commons.ui.stringRes

/** The keyring's name as it reads inside a sentence ("kept in the macOS Keychain"). */
@Composable
fun keyringName(keyring: OsKeyring?): String =
    stringRes(
        when (keyring) {
            OsKeyring.MAC_KEYCHAIN -> Res.string.key_storage_keyring_mac
            OsKeyring.WINDOWS_CREDENTIALS -> Res.string.key_storage_keyring_windows
            OsKeyring.KWALLET -> Res.string.key_storage_keyring_kwallet
            OsKeyring.SECRET_SERVICE, null -> Res.string.key_storage_keyring_secret_service
        },
    )

/** The icon of a state: a shield when keys are kept and readable, a lock or warning when not. */
fun keyStorageSymbol(status: KeyStorageStatus): MaterialSymbol =
    when (status) {
        is KeyStorageStatus.KeyringUnreachable -> MaterialSymbols.Warning
        is KeyStorageStatus.PasswordFile -> if (status.hasPassword && !status.unlocked) MaterialSymbols.Lock else MaterialSymbols.Shield
        KeyStorageStatus.NotKept -> MaterialSymbols.Info
        else -> MaterialSymbols.Shield
    }

/** Where keys are kept and whether that works, as a short title. */
@Composable
fun keyStorageDescription(status: KeyStorageStatus): String =
    when (status) {
        KeyStorageStatus.Unknown -> stringRes(Res.string.key_storage_status_unknown)
        is KeyStorageStatus.Keyring -> stringRes(Res.string.key_storage_status_keyring, keyringName(status.keyring))
        // Short, with no keyring name: anything longer wraps beside Try again on a phone.
        is KeyStorageStatus.KeyringUnreachable -> stringRes(Res.string.key_storage_status_unreachable)
        is KeyStorageStatus.PasswordFile ->
            when {
                !status.hasPassword -> stringRes(Res.string.key_storage_status_password_new)
                status.unlocked -> stringRes(Res.string.key_storage_status_password_unlocked)
                else -> stringRes(Res.string.key_storage_status_password_locked)
            }
        KeyStorageStatus.NotKept -> stringRes(Res.string.key_storage_status_not_kept)
    }
