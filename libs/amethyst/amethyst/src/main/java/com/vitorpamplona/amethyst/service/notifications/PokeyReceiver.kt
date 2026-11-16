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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.RECEIVER_EXPORTED
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PokeyReceiver : BroadcastReceiver() {
    companion object {
        const val POKEY_ACTION = "com.shared.NOSTR"
        const val TAG = "PokeyReceiver"
        val INTENT = IntentFilter(POKEY_ACTION)
    }

    val exceptionHandler =
        CoroutineExceptionHandler { _, throwable ->
            Log.e("AmethystCoroutine", "Caught exception: ${throwable.message}", throwable)
        }

    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob() + exceptionHandler)

    fun register(app: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            app.registerReceiver(this, INTENT, RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            app.registerReceiver(this, INTENT)
        }
    }

    fun unregister(app: Context) {
        app.unregisterReceiver(this)
        scope.cancel()
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action == POKEY_ACTION) { // it's best practice to verify intent action before performing any operation
            val eventStr = intent.getStringExtra("EVENT")
            Log.d(TAG) { "New Pokey Notification Arrived $eventStr" }

            if (eventStr == null) return

            scope.launch {
                try {
                    // Feeds the shared cache; NotificationDispatcher observes and dispatches.
                    LocalCache.justConsume(Event.fromJson(eventStr), null, false)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse Pokey Event", e)
                }
            }
        }
    }
}
