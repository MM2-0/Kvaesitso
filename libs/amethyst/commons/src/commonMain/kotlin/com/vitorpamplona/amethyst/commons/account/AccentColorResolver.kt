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
package com.vitorpamplona.amethyst.commons.account

import com.vitorpamplona.amethyst.commons.model.AccentColorType
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest

/**
 * The accent color the app should draw with: the account's own choice, or [appDefault] (the
 * app-wide setting) when it has none or there is no account.
 *
 * A null [accountPubKey] means the logged-in account. A screen acting for one specific account
 * (a signer request, a call) passes its pubkey, so it keeps that account's color even when the
 * logged-in account is another one; an account that is not loaded gets [appDefault].
 */
fun AccountSessionManager.currentAccentColor(
    appDefault: StateFlow<AccentColorType>,
    accountPubKey: HexKey? = null,
): AccentColorType = accountFor(accountPubKey)?.settings?.accentColor?.value ?: appDefault.value

/** [currentAccentColor] as it changes: on login, logout, account switch, or either setting changing. */
@OptIn(ExperimentalCoroutinesApi::class)
fun AccountSessionManager.accentColorFlow(
    appDefault: StateFlow<AccentColorType>,
    accountPubKey: HexKey? = null,
): Flow<AccentColorType> =
    if (accountPubKey != null) {
        accentColorFlow(accountsCache.loadedAccount(accountPubKey), appDefault)
    } else {
        accountContent.flatMapLatest { state ->
            accentColorFlow((state as? AccountState.LoggedIn)?.account, appDefault)
        }
    }.distinctUntilChanged()

private fun AccountSessionManager.accountFor(accountPubKey: HexKey?): Account? = if (accountPubKey != null) accountsCache.loadedAccount(accountPubKey) else loggedInAccount()

private fun accentColorFlow(
    account: Account?,
    appDefault: StateFlow<AccentColorType>,
): Flow<AccentColorType> {
    val accountAccent = account?.settings?.accentColor ?: return appDefault
    return combine(accountAccent, appDefault) { mine, app -> mine ?: app }
}
