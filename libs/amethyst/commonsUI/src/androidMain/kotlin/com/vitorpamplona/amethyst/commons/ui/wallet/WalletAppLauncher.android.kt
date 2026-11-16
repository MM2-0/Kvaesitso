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
package com.vitorpamplona.amethyst.commons.ui.wallet

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import kotlin.coroutines.cancellation.CancellationException

@Composable
actual fun rememberWalletAppLauncher(): WalletAppLauncher {
    val context = LocalContext.current
    return remember(context) {
        WalletAppLauncher { uri, noWalletFound, onPaid, onError ->
            openWalletUri(context, uri, noWalletFound, onPaid, onError)
        }
    }
}

/**
 * Hands a payment URI (`lightning:…`, `bitcoin:?lno=…`) to whichever wallet is installed. Also used
 * by the app's non-composable callers, so the intent and its flags live in one place.
 */
fun openWalletUri(
    context: Context,
    uri: String,
    noWalletFound: String,
    onPaid: () -> Unit,
    onError: (String) -> Unit,
) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, uri.toUri())
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

        context.startActivity(intent)
        onPaid()
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        onError(noWalletFound)
    }
}
