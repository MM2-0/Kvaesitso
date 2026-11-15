package de.mm20.launcher2.comms.telephony

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.telecom.Call
import android.telecom.CallScreeningService
import de.mm20.launcher2.base.containedScope
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Event-driven screening: no polling, wake lock or permanent foreground service. */
class TelosCallScreeningService : CallScreeningService() {
    private val deadlineHandler = Handler(Looper.getMainLooper())
    private val pending = ConcurrentHashMap.newKeySet<ScreeningDecision>()
    private val dispatcher =
        ThreadPoolExecutor(
                2,
                2,
                30,
                TimeUnit.SECONDS,
                LinkedBlockingQueue<Runnable>(),
                { task ->
                    Thread(
                            {
                                // Priority applies only to these short-lived screening workers,
                                // never the UI,
                                // networking pools, compilation jobs or the rest of the device.
                                runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND) }
                                task.run()
                            },
                            "Telos-call-screening",
                        )
                        .apply { isDaemon = true }
                },
            )
            .apply { allowCoreThreadTimeOut(true) }
            .asCoroutineDispatcher()
    private val scope = containedScope(dispatcher)

    override fun onScreenCall(callDetails: Call.Details) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                callDetails.callDirection != Call.Details.DIRECTION_INCOMING
        ) {
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }
        lateinit var decision: ScreeningDecision
        val fallback = Runnable { decision.complete(false) }
        decision = ScreeningDecision { block ->
            deadlineHandler.removeCallbacks(fallback)
            pending.remove(decision)
            respondToCall(
                callDetails,
                CallResponse.Builder()
                    .setDisallowCall(block)
                    .setRejectCall(block)
                    .setSkipCallLog(block)
                    .setSkipNotification(block)
                    .build(),
            )
        }
        pending.add(decision)
        // Independent of the worker queue and blocking provider/SQLCipher calls. Telecom has
        // a five-second response limit; retain headroom and allow calls on failure.
        deadlineHandler.postDelayed(fallback, 3500)
        scope.launch {
            val block =
                try {
                    withTimeoutOrNull(3500) {
                        CallBlockEngine.shouldBlock(
                            callDetails.handle?.schemeSpecificPart.orEmpty(),
                            callDetails.handlePresentation,
                            incoming = true,
                        )
                    } ?: false
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    false
                }
            decision.complete(block)
        }
    }

    override fun onDestroy() {
        pending.toList().forEach { it.complete(false) }
        scope.cancel()
        dispatcher.close()
        super.onDestroy()
    }
}
