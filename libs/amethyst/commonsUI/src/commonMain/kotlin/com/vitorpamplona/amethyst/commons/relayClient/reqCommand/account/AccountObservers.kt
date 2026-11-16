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
package com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.User
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Composable
fun observeAccountIsHiddenWord(
    account: Account,
    word: String,
): State<Boolean> {
    // Subscribe in the LocalCache for changes that arrive in the device
    val flow =
        remember(account, word) {
            account.hiddenUsers.flow
                .map { word in it.hiddenWords }
                .distinctUntilChanged()
        }

    return flow.collectAsStateWithLifecycle(false)
}

@Composable
fun observeAccountIsHiddenUser(
    account: Account,
    user: User,
): State<Boolean> {
    // Subscribe in the LocalCache for changes that arrive in the device
    val flow =
        remember(account, user) {
            account.hiddenUsers.flow
                .map { it.hiddenUsers.contains(user.pubkeyHex) || it.spammers.contains(user.pubkeyHex) }
                .distinctUntilChanged()
        }

    return flow.collectAsStateWithLifecycle(account.isHidden(user))
}
