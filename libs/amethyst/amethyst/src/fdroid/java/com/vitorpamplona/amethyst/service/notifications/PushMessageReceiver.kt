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
import android.content.Context
import android.util.LruCache
import androidx.core.content.ContextCompat
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.MessagingReceiver
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage

class PushMessageReceiver : MessagingReceiver() {
    companion object {
        private val TAG = "Amethyst-OSSPushReceiver"
    }

    private val appContext = Amethyst.instance.appContext
    private val scope = Amethyst.instance.applicationIOScope
    private val eventCache = LruCache<String, String>(100)
    private val pushHandler = PushDistributorHandler

    override fun onMessage(
        context: Context,
        message: PushMessage,
        instance: String,
    ) {
        val messageStr = message.content.decodeToString()
        Log.d(TAG) { "New message $messageStr for Instance: $instance" }
        scope.launch {
            try {
                parseMessage(messageStr)?.let {
                    receiveIfNew(it)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.d(TAG) { "Message could not be parsed: ${e.message}" }
            }
        }
    }

    private suspend fun parseMessage(message: String): GiftWrapEvent? {
        (Event.fromJson(message) as? GiftWrapEvent)?.let {
            return it
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

    override fun onNewEndpoint(
        context: Context,
        endpoint: PushEndpoint,
        instance: String,
    ) {
        val sanitizedEndpoint = if (endpoint.url.endsWith("?up=1")) endpoint.url.dropLast(5) else endpoint.url
        // if (sanitizedEndpoint != pushHandler.getSavedEndpoint()) {
        Log.d(TAG) { "New endpoint provided:- ${endpoint.url} for Instance: $instance ${pushHandler.getSavedEndpoint()} $sanitizedEndpoint" }
        pushHandler.setEndpoint(sanitizedEndpoint)
        scope.launch(Dispatchers.IO) {
            PushNotificationUtils.checkAndInit(sanitizedEndpoint, LocalPreferences.allSavedAccounts()) {
                Log.d(TAG) { "Building okHttpClient, useTor: ${Amethyst.instance.torManager.isSocksReady()}" }
                Amethyst.instance.okHttpClients.getHttpClient(Amethyst.instance.torManager.isSocksReady())
            }
            NotificationCategory.ZAP.ensureChannel(appContext)
            NotificationCategory.DIRECT_MESSAGE.ensureChannel(appContext)
        }
        // } else {
        Log.d(TAG) { "Same endpoint provided:- ${endpoint.url} for Instance: $instance $sanitizedEndpoint" }
        // }
    }

    override fun onRegistrationFailed(
        context: Context,
        reason: FailedReason,
        instance: String,
    ) {
        Log.d(TAG) { "Registration failed for Instance: $instance" }
        pushHandler.forceRemoveDistributor(context)
    }

    override fun onUnregistered(
        context: Context,
        instance: String,
    ) {
        val removedEndpoint = pushHandler.getSavedEndpoint()
        Log.d(TAG) { "Endpoint: $removedEndpoint removed for Instance: $instance" }
        Log.d(TAG, "App is unregistered. ")
        pushHandler.forceRemoveDistributor(context)
        pushHandler.removeEndpoint()
    }

    fun notificationManager(): NotificationManager =
        ContextCompat.getSystemService(appContext, NotificationManager::class.java)
            as NotificationManager
}
