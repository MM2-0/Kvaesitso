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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.keystorage.KeyPrompt
import com.vitorpamplona.amethyst.commons.keystorage.KeyStorageStatus
import com.vitorpamplona.amethyst.commons.keystorage.NewPasswordAnswer
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.key_storage_confirm_password
import com.vitorpamplona.amethyst.commons.resources.key_storage_create_password_description
import com.vitorpamplona.amethyst.commons.resources.key_storage_create_password_title
import com.vitorpamplona.amethyst.commons.resources.key_storage_details
import com.vitorpamplona.amethyst.commons.resources.key_storage_dont_remember
import com.vitorpamplona.amethyst.commons.resources.key_storage_enter_key_description
import com.vitorpamplona.amethyst.commons.resources.key_storage_enter_key_title
import com.vitorpamplona.amethyst.commons.resources.key_storage_keep_key
import com.vitorpamplona.amethyst.commons.resources.key_storage_not_now
import com.vitorpamplona.amethyst.commons.resources.key_storage_nsec
import com.vitorpamplona.amethyst.commons.resources.key_storage_password
import com.vitorpamplona.amethyst.commons.resources.key_storage_skip
import com.vitorpamplona.amethyst.commons.resources.key_storage_try_again
import com.vitorpamplona.amethyst.commons.resources.key_storage_unlock
import com.vitorpamplona.amethyst.commons.resources.key_storage_unlock_title
import com.vitorpamplona.amethyst.commons.resources.key_storage_unreachable_description
import com.vitorpamplona.amethyst.commons.resources.key_storage_unreachable_title
import com.vitorpamplona.amethyst.commons.resources.key_storage_use_key
import com.vitorpamplona.amethyst.commons.resources.key_storage_wrong_key
import com.vitorpamplona.amethyst.commons.resources.privacy_lock_password_mismatch
import com.vitorpamplona.amethyst.commons.resources.privacy_lock_password_too_short
import com.vitorpamplona.amethyst.commons.resources.privacy_lock_password_wrong
import com.vitorpamplona.amethyst.commons.ui.components.PromptDialog
import com.vitorpamplona.amethyst.commons.ui.components.PromptSecretField
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.quartz.nip49PrivKeyEnc.Nip49

// The prompts stay open until answered (PromptDialog's default): a dismissal by clicking outside
// would leave the storage waiting on an answer, so each dialog answers through its own buttons only.

/** Asks for a new password for the key file, or for not keeping keys on this device. */
@Composable
internal fun CreateKeyPasswordDialog(prompt: KeyPrompt.CreatePassword) {
    var password by remember(prompt) { mutableStateOf("") }
    var confirm by remember(prompt) { mutableStateOf("") }
    var error by remember(prompt) { mutableStateOf<String?>(null) }

    val tooShort = pluralStringRes(Res.plurals.privacy_lock_password_too_short, Nip49.MIN_PASSWORD_LENGTH, Nip49.MIN_PASSWORD_LENGTH)
    val mismatch = stringRes(Res.string.privacy_lock_password_mismatch)

    val keep = {
        error =
            when {
                !Nip49.isLongEnough(password) -> tooShort
                password != confirm -> mismatch
                else -> null
            }
        if (error == null) prompt.answer(NewPasswordAnswer.Password(password))
    }

    PromptDialog(
        symbol = MaterialSymbols.Lock,
        title = stringRes(Res.string.key_storage_create_password_title),
        description = stringRes(Res.string.key_storage_create_password_description),
        confirmText = stringRes(Res.string.key_storage_keep_key),
        onConfirm = keep,
        dismissText = stringRes(Res.string.key_storage_dont_remember),
        onDismiss = { prompt.answer(NewPasswordAnswer.DontRemember) },
    ) {
        // A change to either answers the last check: the red goes until Keep checks again.
        PromptSecretField(
            password,
            stringRes(Res.string.key_storage_password),
            {
                password = it
                error = null
            },
            isError = error != null,
            autoFocus = true,
        )
        // The mismatch is about both; its words go under the second, where the user just typed.
        PromptSecretField(
            confirm,
            stringRes(Res.string.key_storage_confirm_password),
            {
                confirm = it
                error = null
            },
            error = error,
            onDone = keep,
        )
    }
}

/** Asks for the key file's password; Skip leaves it locked for this run. */
@Composable
internal fun UnlockKeysDialog(prompt: KeyPrompt.Unlock) {
    var password by remember(prompt) { mutableStateOf("") }

    PromptDialog(
        symbol = MaterialSymbols.LockOpen,
        title = stringRes(Res.string.key_storage_unlock_title),
        // The title and the Password field say it all.
        description = null,
        confirmText = stringRes(Res.string.key_storage_unlock),
        onConfirm = { prompt.answer(password) },
        confirmEnabled = password.isNotEmpty(),
        dismissText = stringRes(Res.string.key_storage_skip),
        onDismiss = { prompt.answer(null) },
    ) {
        PromptSecretField(
            value = password,
            label = stringRes(Res.string.key_storage_password),
            onValueChange = { password = it },
            error = if (prompt.wrongPassword) stringRes(Res.string.privacy_lock_password_wrong) else null,
            onDone = { if (password.isNotEmpty()) prompt.answer(password) },
            autoFocus = true,
        )
    }
}

/** Asks for the key of an account this device does not keep; Skip loads it without signing. */
@Composable
internal fun EnterKeyDialog(prompt: KeyPrompt.EnterKey) {
    var key by remember(prompt) { mutableStateOf("") }

    PromptDialog(
        symbol = MaterialSymbols.Key,
        title = stringRes(Res.string.key_storage_enter_key_title),
        description = stringRes(Res.string.key_storage_enter_key_description, shortNpub(prompt.npub)),
        confirmText = stringRes(Res.string.key_storage_use_key),
        onConfirm = { prompt.answer(key) },
        confirmEnabled = key.isNotBlank(),
        dismissText = stringRes(Res.string.key_storage_skip),
        onDismiss = { prompt.answer(null) },
    ) {
        PromptSecretField(
            value = key,
            label = stringRes(Res.string.key_storage_nsec),
            onValueChange = { key = it },
            error = if (prompt.wrongKey) stringRes(Res.string.key_storage_wrong_key) else null,
            onDone = { if (key.isNotBlank()) prompt.answer(key) },
            autoFocus = true,
        )
    }
}

/** The OS keyring is there but did not answer: say so, and offer to ask it again. */
@Composable
internal fun KeyringUnreachableDialog(
    status: KeyStorageStatus.KeyringUnreachable,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    PromptDialog(
        symbol = MaterialSymbols.Warning,
        title = stringRes(Res.string.key_storage_unreachable_title),
        description = stringRes(Res.string.key_storage_unreachable_description),
        confirmText = stringRes(Res.string.key_storage_try_again),
        onConfirm = onRetry,
        dismissText = stringRes(Res.string.key_storage_not_now),
        onDismiss = onDismiss,
        isError = true,
        // Nothing waits on this one: closing it is the same as Not now.
        dismissible = true,
    ) {
        KeyStorageErrorDetails(status.detail)
    }
}

/** The system's own error, folded away: it is for a bug report, not for reading first. */
@Composable
internal fun KeyStorageErrorDetails(detail: String) {
    var open by remember(detail) { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = { open = !open }) {
            Text(stringRes(Res.string.key_storage_details))
            Icon(if (open) MaterialSymbols.ExpandLess else MaterialSymbols.ExpandMore, contentDescription = null)
        }
        AnimatedVisibility(open) {
            SelectionContainer {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** npub1qe3e5wr…zefe: enough of both ends to tell accounts apart. */
private fun shortNpub(npub: String) = if (npub.length > 20) npub.take(12) + "…" + npub.takeLast(4) else npub
