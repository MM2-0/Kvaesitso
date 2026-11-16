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

import android.app.NotificationManager
import android.util.LruCache
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PushNotificationReceiverService : FirebaseMessagingService() {
    // Exists to avoid exceptions stopping the coroutine
    val exceptionHandler =
        CoroutineExceptionHandler { _, throwable ->
            Log.e("AmethystCoroutine", "Caught exception: ${throwable.message}", throwable)
        }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob() + exceptionHandler)
    private val eventCache = LruCache<String, String>(100)

    // this is called when a message is received
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d("PushNotificationService") { "Notification received $remoteMessage" }
        scope.launch(Dispatchers.IO) {
            parseMessage(remoteMessage.data)?.let { receiveIfNew(it) }
        }
    }

    private fun parseMessage(params: Map<String, String>): GiftWrapEvent? {
        params["encryptedEvent"]?.let { eventStr ->
            (Event.fromJson(eventStr) as? GiftWrapEvent)?.let {
                return it
            }
        }
        return null
    }

    private suspend fun receiveIfNew(event: GiftWrapEvent) {
        if (eventCache.get(event.id) == null) {
            eventCache.put(event.id, event.id)
            // The push server re-wraps the real GiftWrap and strips the p tag;
            // unwrap the outer layer, feed the inner into LocalCache, and let
            // the usual Account → EventProcessor chain take over.
            PushWrapDecryptor.unwrapAndFeed(event)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("PushNotificationService", "PushNotificationReceiverService.onCreate")
    }

    override fun onDestroy() {
        Log.d("PushNotificationService", "PushNotificationReceiverService.onDestroy")

        scope.cancel()
        super.onDestroy()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onNewToken(token: String) {
        scope.launch(Dispatchers.IO) {
            Log.d("PushNotificationService", "PushNotificationReceiverService.onNewToken")
            // if the app is running, try to get tor. if not, goes open web.
            PushNotificationUtils.checkAndInit(token, LocalPreferences.allSavedAccounts()) {
                Amethyst.instance.okHttpClients.getHttpClient(Amethyst.instance.torManager.isSocksReady())
            }
            NotificationCategory.ZAP.ensureChannel(applicationContext)
            NotificationCategory.DIRECT_MESSAGE.ensureChannel(applicationContext)
        }
    }

    fun notificationManager(): NotificationManager =
        ContextCompat.getSystemService(applicationContext, NotificationManager::class.java)
            as NotificationManager
}
