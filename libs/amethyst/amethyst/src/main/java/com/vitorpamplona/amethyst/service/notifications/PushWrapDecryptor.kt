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
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException

/**
 * Handles GiftWraps that arrive via FCM / UnifiedPush. The push server
 * re-wraps the real NIP-59 GiftWrap inside its own outer GiftWrap and strips
 * the recipient `p` tag, so neither the relay's [CacheClientConnector] nor
 * the account's [GiftWrapEventHandler] can identify which account owns it.
 * We have to probe every saved account's signer until one decrypts.
 *
 * Once decrypted, the inner event is fed into [LocalCache]. From there the
 * normal `Account.newNotesPreProcessor` → `GiftWrapEventHandler` →
 * `SealEventHandler` chain unwraps any remaining layers, and
 * [NotificationDispatcher] picks up the final payload and notifies.
 */
object PushWrapDecryptor {
    private const val TAG = "PushWrapDecryptor"

    suspend fun unwrapAndFeed(outerWrap: GiftWrapEvent) {
        // A remote signer answers over a relay, and a signer that is offline holds the probe for
        // its whole timeout: try every account that decrypts on the device first, so its
        // notifications never wait on a bunker that isn't theirs.
        LocalPreferences.allSavedAccounts().sortedBy { it.loggedInWithRemoteSigner }.forEach { savedAccount ->
            if (!savedAccount.hasPrivKey && !savedAccount.loggedInWithExternalSigner) return@forEach

            val accountSettings = LocalPreferences.loadAccountConfigFromEncryptedStorage(savedAccount.npub) ?: return@forEach
            try {
                val account = Amethyst.instance.accountsCache.loadAccount(accountSettings)
                val inner = outerWrap.unwrapThrowing(account.signer)
                LocalCache.justConsume(inner, null, false)
                return
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.d(TAG) { "Push wrap not for ${savedAccount.npub}: ${e.message}" }
            }
        }
        Log.w(TAG) { "Push wrap ${outerWrap.id} did not decrypt for any saved account" }
    }
}
