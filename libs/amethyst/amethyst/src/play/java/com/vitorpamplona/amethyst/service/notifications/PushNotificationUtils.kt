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
package com.vitorpamplona.amethyst.service.notifications

import com.google.firebase.messaging.FirebaseMessaging
import com.vitorpamplona.amethyst.commons.account.AccountInfo
import com.vitorpamplona.amethyst.commons.util.retryIfException
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import okhttp3.OkHttpClient
import kotlin.coroutines.cancellation.CancellationException

object PushNotificationUtils {
    var lastToken: String? = null
    var hasInit: List<AccountInfo>? = null

    @Suppress("DEPRECATION") // FirebaseMessaging.token is deprecated in the current SDK.
    suspend fun checkAndInit(
        accounts: List<AccountInfo>,
        okHttpClient: (String) -> OkHttpClient,
    ) = with(Dispatchers.IO) {
        try {
            val token = FirebaseMessaging.getInstance().token.await()
            if (hasInit?.equals(accounts) == true && lastToken == token) {
                return@with
            }

            registerToken(token, accounts, okHttpClient)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("PushNotificationUtils", "Failed to get Firebase token", e)
        }
    }

    suspend fun checkAndInit(
        token: String,
        accounts: List<AccountInfo>,
        okHttpClient: (String) -> OkHttpClient,
    ) = with(Dispatchers.IO) {
        // initializes if the accounts are different or if the token has changed
        if (hasInit?.equals(accounts) == true && lastToken == token) {
            return@with
        }
        registerToken(token, accounts, okHttpClient)
    }

    private suspend fun registerToken(
        token: String,
        accounts: List<AccountInfo>,
        okHttpClient: (String) -> OkHttpClient,
    ) = retryIfException("RegisterAccounts") {
        RegisterAccounts(accounts, okHttpClient).go(token)
        lastToken = token
        hasInit = accounts.toList()
    }
}
