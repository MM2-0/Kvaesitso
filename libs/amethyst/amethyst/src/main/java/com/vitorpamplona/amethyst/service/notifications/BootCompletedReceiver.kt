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
import android.content.Intent
import com.vitorpamplona.quartz.utils.Log

/**
 * Restarts the NotificationRelayService after device reboot or app update.
 *
 * Handles:
 * - BOOT_COMPLETED / QUICKBOOT_POWERON: restart after device reboot
 * - MY_PACKAGE_REPLACED: restart after app update (without this, the service
 *   stays dead until the user manually opens the app or reboots)
 *
 * The specialUse foreground service type is allowed to start from BOOT_COMPLETED
 * on Android 15+, unlike dataSync which is restricted.
 */
class BootCompletedReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootCompletedReceiver"
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> {
                // isEnabled() reads plain SharedPreferences (a synchronous disk read, by
                // design — see LocalPreferences.globalSettingsPrefs) and scans the accounts
                // cache. onReceive runs on the main thread, so hop off it via goAsync() to
                // avoid a StrictMode DiskReadViolation while keeping the receiver alive until
                // the work finishes.
                val pending = goAsync()
                Thread {
                    try {
                        Log.d(TAG) { "Received ${intent.action}, checking if notification service should start" }
                        if (NotificationRelayService.isEnabled(context)) {
                            Log.d(TAG, "Starting notification relay service")
                            NotificationRelayService.start(context)
                        }
                    } finally {
                        pending.finish()
                    }
                }.start()
            }
        }
    }
}
