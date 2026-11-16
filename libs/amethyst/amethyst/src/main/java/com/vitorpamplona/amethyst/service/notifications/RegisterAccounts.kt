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

import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.BuildConfig
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.commons.account.AccountInfo
import com.vitorpamplona.amethyst.commons.model.AccountSettings
import com.vitorpamplona.amethyst.isDebug
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.isDialableFromOthers
import com.vitorpamplona.quartz.nip42RelayAuth.RelayAuthEvent
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.mapNotNullAsync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.coroutines.executeAsync
import java.io.IOException

class RegisterAccounts(
    private val accounts: List<AccountInfo>,
    private val client: (String) -> OkHttpClient,
) {
    @Suppress("SENSELESS_COMPARISON")
    val tag =
        if (BuildConfig.FLAVOR == "play") {
            "RegisterAccounts FirebaseMsgService"
        } else {
            "RegisterAccounts UnifiedPushService"
        }

    private suspend fun signAllAuths(
        notificationToken: String,
        remainingTos: List<Registration>,
    ): List<RelayAuthEvent> {
        if (remainingTos.isEmpty()) {
            return emptyList()
        }

        return mapNotNullAsync(remainingTos) { info ->
            val account = Amethyst.instance.accountsCache.loadAccount(info.accountSettings)
            RelayAuthEvent.create(info.relays, notificationToken, account.signer)
        }
    }

    class Registration(
        val accountSettings: AccountSettings,
        val relays: List<NormalizedRelayUrl>,
    )

    // creates proof that it controls all accounts
    private suspend fun signEventsToProveControlOfAccounts(
        accounts: List<AccountInfo>,
        notificationToken: String,
    ): List<RelayAuthEvent> {
        val readyToSend =
            accounts
                .mapNotNull { account ->
                    if (account.hasPrivKey || account.loggedInWithExternalSigner) {
                        Log.d(tag) { "Register Account ${account.npub}" }

                        val acc = LocalPreferences.loadAccountConfigFromEncryptedStorage(account.npub)
                        if (acc != null && acc.isWriteable()) {
                            // Our own lists, but the push server is the one that dials them: a
                            // local relay names our machine, which it cannot reach, and on its
                            // side would name its own network. So the same rule as a stranger's
                            // list applies.
                            val nip65Read = acc.backupNIP65RelayList?.allReadRelaysNorm()?.filter { it.isDialableFromOthers() } ?: emptyList()
                            val nip17Read = acc.backupDMRelayList?.allRelays()?.filter { it.isDialableFromOthers() } ?: emptyList()

                            if (isDebug) {
                                val readRelays = nip65Read.joinToString(", ") { it.url }
                                Log.d(tag) { "Register Account ${account.npub} NIP65 Reads $readRelays" }

                                val dmRelays = nip17Read.joinToString(", ") { it.url }
                                Log.d(tag) { "Register Account ${account.npub} NIP17 Reads $dmRelays" }
                            }

                            val relays = (nip65Read + nip17Read)

                            if (relays.isNotEmpty()) {
                                Registration(acc, relays)
                            } else {
                                null
                            }
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                }

        return signAllAuths(notificationToken, readyToSend)
    }

    suspend fun postRegistrationEvent(events: List<RelayAuthEvent>) {
        val jsonObject =
            """{
            "events": [ ${events.joinToString(", ") { it.toJson() }} ]
        }
        """

        val url = "https://push.amethyst.social/register"
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = jsonObject.toRequestBody(mediaType)

        val request =
            Request
                .Builder()
                .url(url)
                .post(body)
                .build()

        val client = client(url)

        client.newCall(request).executeAsync().use { response ->
            // Must throw on failure: retryIfException only re-attempts when the block
            // raises, and PushNotificationUtils only caches the token/account list
            // after a clean return. Returning normally on an error response would
            // mark this device as registered while push is silently dead until the
            // token or the account list changes.
            if (!response.isSuccessful) {
                val reason = response.peekBody(MAX_ERROR_BODY_BYTES).string().ifBlank { response.message }
                throw PushRegistrationException("Push registration failed at $url: HTTP ${response.code} $reason")
            }
            Log.i(tag) { "Server registration succeeded (HTTP ${response.code})" }
        }
    }

    suspend fun go(notificationToken: String) {
        if (notificationToken.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                postRegistrationEvent(signEventsToProveControlOfAccounts(accounts, notificationToken))
            }
        }
    }

    companion object {
        /** Caps how much of an error response is buffered into the exception message. */
        const val MAX_ERROR_BODY_BYTES = 512L
    }
}

/** The push server rejected the registration. Retried by the caller's retry loop. */
class PushRegistrationException(
    message: String,
) : IOException(message)
