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
package com.vitorpamplona.amethyst.commons.ui.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreOwner
import com.vitorpamplona.amethyst.commons.ui.components.LocalAccountViewModelStoreOwner

/**
 * The owner is keyed by the account. While the same account stays logged in, the call site is
 * stable, so the owner (and its view models) survives recompositions and configuration changes: it
 * is parented to the window's [LocalViewModelStoreOwner]. When the account changes, the previous
 * owner leaves the composition and Lifecycle 2.11's [rememberViewModelStoreOwner] clears its store
 * immediately.
 *
 * The same owner is the account's shared store ([LocalAccountViewModelStoreOwner]) for view models
 * that several destinations share, so they are cleared with the account too.
 */
@Composable
actual fun AccountScopedViewModelStore(
    accountKey: String,
    content: @Composable () -> Unit,
) {
    key(accountKey) {
        val owner = rememberViewModelStoreOwner()
        CompositionLocalProvider(
            LocalViewModelStoreOwner provides owner,
            LocalAccountViewModelStoreOwner provides owner,
            content = content,
        )
    }
}
