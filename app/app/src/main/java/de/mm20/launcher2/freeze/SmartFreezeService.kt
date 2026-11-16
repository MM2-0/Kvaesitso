// === TELOS_PENDING_REVIEW_START: smart_freeze_service ===
package de.mm20.launcher2.freeze

import android.app.ActivityManager
import de.mm20.launcher2.base.R as BaseR
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.os.Build
import android.os.IBinder
import android.os.Process
import android.util.Log
import androidx.core.app.NotificationCompat
import rikka.shizuku.Shizuku
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import kotlin.time.Duration.Companion.seconds

class SmartFreezeService : Service() {

    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private var isScreenOff = false
    
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOff = true
                    triggerFreezeCycle()
                }
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOff = false
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, "smart_freeze_channel")
            .setContentTitle("Smart Freeze Manager")
            .setContentText("Monitoring for idle apps to freeze")
            .setSmallIcon(BaseR.mipmap.ic_launcher)
            .build()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenReceiver, filter)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(screenReceiver)
        job.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun triggerFreezeCycle() {
        scope.launch {
            delay(5.seconds)
            if (!isScreenOff) return@launch
            if (isMediaActive() || isVpnRunning() || isHighDataTransfer()) return@launch

            val packagesToFreeze = getPackagesToFreeze()
            for (pkg in packagesToFreeze) {
                if (canFreezePackage(pkg)) {
                    freezePackage(pkg, Process.myUserHandle().hashCode())
                }
            }
        }
    }

    private suspend fun canFreezePackage(packageName: String): Boolean {
        // Foreground / ongoing process check per package. Media, VPN and radio
        // traffic are sampled once per screen-off cycle, not once per app.
        // Note: Full notification check requires NotificationListenerService.
        // We use a best-effort approach or assume integration with a companion listener.
        if (hasForegroundOrOngoingNotifications(packageName)) {
            Log.d("SmartFreeze", "Skipping freeze for $packageName: Has ongoing notifications")
            return false
        }

        return true
    }

    private fun isMediaActive(): Boolean {
        try {
            val mediaSessionManager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
            // Requires MEDIA_CONTENT_CONTROL or active NotificationListenerService component
            val activeSessions = mediaSessionManager.getActiveSessions(null)
            for (session in activeSessions) {
                if (session.playbackState?.state == PlaybackState.STATE_PLAYING) {
                    return true
                }
            }
        } catch (e: SecurityException) {
            Log.w("SmartFreeze", "Missing permission to check active media sessions", e)
        }
        return false
    }

    private fun isVpnRunning(): Boolean {
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }

    private suspend fun isHighDataTransfer(): Boolean {
        val rxStart = TrafficStats.getTotalRxBytes()
        val txStart = TrafficStats.getTotalTxBytes()
        
        delay(1.seconds) // Evaluation interval
        
        val rxEnd = TrafficStats.getTotalRxBytes()
        val txEnd = TrafficStats.getTotalTxBytes()
        
        val deltaBytes = (rxEnd - rxStart) + (txEnd - txStart)
        val deltaKb = deltaBytes / 1024
        
        return deltaKb > 500
    }

    private fun hasForegroundOrOngoingNotifications(packageName: String): Boolean {
        try {
            val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
            val processes = am.runningAppProcesses ?: return false
            for (process in processes) {
                if (process.processName == packageName) {
                    if (process.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("SmartFreeze", "Failed to check process importance for $packageName", e)
        }
        return false
    }

    // === TELOS_PENDING_REVIEW_START: multi_user_freeze ===
    private fun freezePackage(packageName: String, userId: Int) {
        val isShizukuAvailable = (Shizuku.pingBinder() && 
            (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED))

        if (isShizukuAvailable) {
            Log.d("SmartFreeze", "Freezing $packageName for user $userId via Shizuku")
            executeShellCommandViaShizuku("cmd package suspend --user $userId $packageName")
        } else {
            Log.d("SmartFreeze", "Shizuku not available, falling back to root for $packageName user $userId")
            executeShellCommandViaRoot("pm suspend --user $userId $packageName")
        }
    }

    private fun unfreezePackage(packageName: String, userId: Int) {
        val isShizukuAvailable = (Shizuku.pingBinder() && 
            (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED))

        if (isShizukuAvailable) {
            Log.d("SmartFreeze", "Unfreezing $packageName for user $userId via Shizuku")
            executeShellCommandViaShizuku("cmd package unsuspend --user $userId $packageName")
        } else {
            Log.d("SmartFreeze", "Shizuku not available, falling back to root for $packageName user $userId")
            executeShellCommandViaRoot("pm unsuspend --user $userId $packageName")
        }
    }
    // === TELOS_PENDING_REVIEW_END: multi_user_freeze ===

    private fun executeShellCommandViaShizuku(command: String) {
        try {

            val process = de.mm20.launcher2.ktx.ShizukuProcesses.start(arrayOf("sh", "-c", command))
            try { process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS) }
            finally { process.destroy() }
        } catch (e: Exception) {
            Log.e("SmartFreeze", "Shizuku command failed", e)
        }
    }

    private fun executeShellCommandViaRoot(command: String) {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            process.waitFor()
        } catch (e: Exception) {
            Log.e("SmartFreeze", "Root command failed", e)
        }
    }

    private fun getPackagesToFreeze(): List<String> {
        // This should interface with Telos Settings to get user-selected apps to freeze.
        return emptyList()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "smart_freeze_channel",
            "Smart Freeze Service",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Runs the Smart Freeze monitoring service in the background"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}
// === TELOS_PENDING_REVIEW_END: smart_freeze_service ===
