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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.keystorage.KeyPrompt
import com.vitorpamplona.amethyst.commons.keystorage.KeyStorageController
import com.vitorpamplona.amethyst.commons.keystorage.KeyStorageStatus
import kotlinx.coroutines.launch

/** The platform's key storage, when it has one the user deals with (the desktop); null otherwise. */
val LocalKeyStorage = staticCompositionLocalOf<KeyStorageController?> { null }

/**
 * Provides [controller] to the app and answers what it asks: the key-file password, a key this
 * device does not keep. Also says, once per failure, when the OS keyring could not be opened.
 * With no [controller] it only renders [content].
 */
@Composable
fun KeyStorageHost(
    controller: KeyStorageController?,
    content: @Composable () -> Unit,
) {
    if (controller == null) {
        content()
        return
    }
    CompositionLocalProvider(LocalKeyStorage provides controller) {
        content()
        KeyPromptDialogs(controller)
        KeyringUnreachableAlert(controller)
    }
}

@Composable
private fun KeyPromptDialogs(controller: KeyStorageController) {
    val pending by controller.prompts.pending.collectAsStateWithLifecycle()
    when (val prompt = pending) {
        is KeyPrompt.CreatePassword -> CreateKeyPasswordDialog(prompt)
        is KeyPrompt.Unlock -> UnlockKeysDialog(prompt)
        is KeyPrompt.EnterKey -> EnterKeyDialog(prompt)
        null -> Unit
    }
}

@Composable
private fun KeyringUnreachableAlert(controller: KeyStorageController) {
    val status by controller.status.collectAsStateWithLifecycle()
    val unreachable = status as? KeyStorageStatus.KeyringUnreachable ?: return
    // Once per failure: a dismissed alert stays away until the keyring fails again differently.
    var dismissed by remember { mutableStateOf<KeyStorageStatus?>(null) }
    if (dismissed == unreachable) return
    val scope = rememberCoroutineScope()
    KeyringUnreachableDialog(
        status = unreachable,
        onRetry = {
            dismissed = unreachable
            scope.launch { controller.retry() }
        },
        onDismiss = { dismissed = unreachable },
    )
}
