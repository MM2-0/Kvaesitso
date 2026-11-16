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
package com.vitorpamplona.amethyst.commons.account.ui

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.domain.nip46.RemoteSignerAuthPrompts
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.remote_signer_auth_body
import com.vitorpamplona.amethyst.commons.resources.remote_signer_auth_cannot_open
import com.vitorpamplona.amethyst.commons.resources.remote_signer_auth_for_account
import com.vitorpamplona.amethyst.commons.resources.remote_signer_auth_later
import com.vitorpamplona.amethyst.commons.resources.remote_signer_auth_open
import com.vitorpamplona.amethyst.commons.resources.remote_signer_auth_title
import com.vitorpamplona.amethyst.commons.ui.components.PromptDialog
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.quartz.utils.Log
import kotlin.coroutines.cancellation.CancellationException

/**
 * Shows a NIP-46 `auth_url` challenge, one at a time: what the signer asks, the page's address
 * so the user can check it is their signer's, and a button that opens it. The request that drew
 * the challenge keeps waiting meanwhile, so closing this does not cancel it. Every loaded account's
 * signer reports here, so the account it is for is named.
 */
@Composable
fun RemoteSignerAuthPrompt(prompts: RemoteSignerAuthPrompts) {
    val pending by prompts.pending.collectAsStateWithLifecycle()
    val page = pending.firstOrNull() ?: return
    val url = page.url

    val uriHandler = LocalUriHandler.current
    var cannotOpen by remember(page) { mutableStateOf(false) }
    // The account's own profile is cached once it is loaded; a login has no account yet.
    val accountName =
        remember(page.account) {
            page.account?.let { LocalCache.getUserIfExists(it)?.toBestDisplayName() ?: it.take(8) }
        }

    PromptDialog(
        symbol = MaterialSymbols.Key,
        title = stringRes(Res.string.remote_signer_auth_title),
        description = stringRes(Res.string.remote_signer_auth_body),
        confirmText = stringRes(Res.string.remote_signer_auth_open),
        onConfirm = {
            try {
                uriHandler.openUri(url)
                prompts.dismiss(page)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Both platforms' handlers throw when no app takes the link; the address stays up to copy.
                Log.w("RemoteSignerAuthPrompt", "Could not open the signer's auth page", e)
                cannotOpen = true
            }
        },
        dismissText = stringRes(Res.string.remote_signer_auth_later),
        onDismiss = { prompts.dismiss(page) },
        dismissible = true,
    ) {
        if (accountName != null) {
            Text(
                text = stringRes(Res.string.remote_signer_auth_for_account, accountName),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
        SelectionContainer {
            Text(
                text = url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (cannotOpen) {
            Text(
                text = stringRes(Res.string.remote_signer_auth_cannot_open),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
    }
}
