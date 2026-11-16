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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.account.ui.LoggedOffBanner
import com.vitorpamplona.amethyst.commons.account.ui.LoggedOffSwitchRow
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.keystorage.KeyStorageController
import com.vitorpamplona.amethyst.commons.keystorage.KeyStorageStatus
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.key_storage_login_notice_locked
import com.vitorpamplona.amethyst.commons.resources.key_storage_login_notice_not_kept
import com.vitorpamplona.amethyst.commons.resources.key_storage_login_notice_unreachable
import com.vitorpamplona.amethyst.commons.resources.key_storage_remember_key
import com.vitorpamplona.amethyst.commons.resources.key_storage_save_keys
import com.vitorpamplona.amethyst.commons.resources.key_storage_try_again
import com.vitorpamplona.amethyst.commons.resources.key_storage_unlock
import com.vitorpamplona.amethyst.commons.ui.stringRes
import kotlinx.coroutines.launch

/**
 * The login screen's part: *Remember my key on this device* where [offerRememberKey] (the field
 * may hold a private key), and a banner when the keys can't be kept as the user expects, with the
 * action that fixes it. Renders nothing where the platform has no key storage the user deals with.
 */
@Composable
fun KeyStorageLoginOptions(
    rememberKey: Boolean,
    onRememberKeyChange: (Boolean) -> Unit,
    offerRememberKey: Boolean = true,
) {
    val controller = LocalKeyStorage.current ?: return
    val status by controller.status.collectAsStateWithLifecycle()
    // This device keeps no keys this run: a switch saying it would is a promise nothing keeps.
    if (offerRememberKey && status != KeyStorageStatus.NotKept) {
        LoggedOffSwitchRow(MaterialSymbols.Lock, stringRes(Res.string.key_storage_remember_key), rememberKey, onRememberKeyChange)
    }
    KeyStorageLoginBanner(controller, status, typingKey = offerRememberKey)
}

/**
 * One line on how the keys stand, with the action that fixes it. Try again (the keyring refused)
 * and Unlock (the key file is locked) are here as in Key Backup, which a logged-off
 * user cannot reach, and show whatever the switch says: either one logs the saved account in once
 * the keys open. Not kept is about the key being typed ([typingKey]). A first key file needs no
 * line: its password dialog says what it is when it opens.
 */
@Composable
private fun KeyStorageLoginBanner(
    controller: KeyStorageController,
    status: KeyStorageStatus,
    typingKey: Boolean,
) {
    val scope = rememberCoroutineScope()

    when (val current = status) {
        is KeyStorageStatus.KeyringUnreachable ->
            LoggedOffBanner(
                symbol = MaterialSymbols.Warning,
                text = stringRes(Res.string.key_storage_login_notice_unreachable),
                isError = true,
                actionText = stringRes(Res.string.key_storage_try_again),
                onAction = { scope.launch { controller.retry() } },
            )
        // The user's own "Don't save" from earlier in this run: say so, and let them take it back.
        KeyStorageStatus.NotKept ->
            if (typingKey) {
                LoggedOffBanner(
                    symbol = MaterialSymbols.Info,
                    text = stringRes(Res.string.key_storage_login_notice_not_kept),
                    actionText = stringRes(Res.string.key_storage_save_keys),
                    onAction = { scope.launch { controller.saveKeys() } },
                )
            }
        is KeyStorageStatus.PasswordFile ->
            when {
                current.hasPassword && !current.unlocked ->
                    LoggedOffBanner(
                        symbol = MaterialSymbols.Lock,
                        text = stringRes(Res.string.key_storage_login_notice_locked),
                        actionText = stringRes(Res.string.key_storage_unlock),
                        onAction = { scope.launch { controller.unlock() } },
                    )
                else -> Unit
            }
        else -> Unit
    }
}
