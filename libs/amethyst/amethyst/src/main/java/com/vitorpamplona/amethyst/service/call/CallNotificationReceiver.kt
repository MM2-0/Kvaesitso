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
package com.vitorpamplona.amethyst.service.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.vitorpamplona.amethyst.commons.service.call.CallSessionBridge
import com.vitorpamplona.amethyst.service.call.notification.CallNotifier
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private const val TAG = "CallNotificationReceiver"

/**
 * Handles the Reject action from the incoming call notification.
 *
 * The Accept action launches [com.vitorpamplona.amethyst.ui.call.CallActivity]
 * directly via PendingIntent.getActivity to comply with Android 12+
 * notification trampoline restrictions.
 */
class CallNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        Log.d(TAG) { "onReceive action=${intent.action} thread=${Thread.currentThread().name}" }
        val callManager = CallSessionBridge.callManager
        if (callManager == null) {
            Log.e(TAG) { "onReceive: callManager is null — cannot process ${intent.action}" }
            return
        }
        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate)
        scope.launch {
            try {
                when (intent.action) {
                    ACTION_REJECT_CALL -> {
                        Log.d(TAG) { "REJECT_CALL: state=${callManager.state.value::class.simpleName}" }
                        CallNotifier.cancelIncomingCall(context)
                        callManager.rejectCall()
                        Log.d(TAG) { "REJECT_CALL: after rejectCall(), state=${callManager.state.value::class.simpleName}" }
                    }

                    ACTION_HANGUP_CALL -> {
                        Log.d(TAG) { "HANGUP_CALL: state=${callManager.state.value::class.simpleName}" }
                        callManager.hangup()
                        Log.d(TAG) { "HANGUP_CALL: after hangup(), state=${callManager.state.value::class.simpleName}" }
                    }
                }
            } finally {
                pendingResult.finish()
                scope.cancel()
            }
        }
    }

    companion object {
        const val ACTION_REJECT_CALL = "com.vitorpamplona.amethyst.REJECT_CALL"
        const val ACTION_HANGUP_CALL = "com.vitorpamplona.amethyst.HANGUP_CALL"
    }
}
