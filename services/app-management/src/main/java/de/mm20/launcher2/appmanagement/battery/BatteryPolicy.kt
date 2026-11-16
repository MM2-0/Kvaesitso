package de.mm20.launcher2.appmanagement.battery

import android.app.NotificationManager
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.provider.Settings
import android.telecom.TelecomManager
import de.mm20.launcher2.ktx.PrivilegedShell
import de.mm20.launcher2.preferences.BatteryGroup
import de.mm20.launcher2.preferences.battery.BatterySettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Opt-in screen-off power policy. Requires Shizuku or root. Never runs until the user
 * enables it in System → Battery. Incoming calls and user-chosen GPS/comms apps skip
 * the matching disable. The dialer blocklist is not touched.
 */
class BatteryPolicy(
    private val context: Context,
    private val settings: BatterySettings,
) {
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val idle = AtomicBoolean(false)
    private var snapshot: Snapshot? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> scope.launch {
                    delay(20_000)
                    val power = ctx?.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
                    if (power?.isInteractive == true) return@launch
                    applyIdle()
                }
                Intent.ACTION_SCREEN_ON -> scope.launch { restore() }
            }
        }
    }

    fun start() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
    }

    private suspend fun policy(): BatteryGroup = settings.data.first()

    private suspend fun applyIdle() {
        val p = policy()
        if (!p.batteryGuardEnabled) return
        if (!PrivilegedShell.available()) return
        if (idle.get()) return
        val snap = Snapshot.capture(context)
        snapshot = snap
        val gpsHold = holdSet(p.batteryGpsHoldPackages, RECOMMENDED_GPS)
        val callHold = holdSet(p.batteryCallHoldPackages, RECOMMENDED_CALL)
        if (p.batteryScreenOffGps && !foregroundIn(gpsHold)) {
            PrivilegedShell.run("settings put secure location_mode 0")
        }
        if (p.batteryScreenOffData && !foregroundIn(gpsHold + callHold)) {
            PrivilegedShell.run("settings put global mobile_data 0")
        }
        if (p.batteryScreenOffRadio.isNotBlank() && !foregroundIn(gpsHold + callHold)) {
            val mode = when (p.batteryScreenOffRadio) {
                "lte" -> "11"
                "wcdma" -> "2"
                "gsm" -> "1"
                else -> null
            }
            if (mode != null) PrivilegedShell.run("settings put global preferred_network_mode $mode")
        }
        if (p.batteryScreenOffMic && !shouldHoldMic(callHold)) {
            PrivilegedShell.run("cmd sensor_privacy enable microphone")
        }
        if (p.batteryScreenOffCamera && !foregroundIn(callHold)) {
            PrivilegedShell.run("cmd sensor_privacy enable camera")
        }
        if (p.batteryScreenOffSync) {
            runCatching { ContentResolver.setMasterSyncAutomatically(false) }
        }
        idle.set(true)
        scheduleWakes(p)
    }

    private suspend fun restore() {
        if (!idle.getAndSet(false)) return
        val snap = snapshot ?: return
        snap.restore()
        if (policy().batteryScreenOffMic) PrivilegedShell.run("cmd sensor_privacy disable microphone")
        if (policy().batteryScreenOffCamera) PrivilegedShell.run("cmd sensor_privacy disable camera")
    }

    private suspend fun scheduleWakes(p: BatteryGroup) {
        val weather = p.batteryWeatherWakeMinutes
        if (weather > 0) {
            scope.launch {
                while (idle.get()) {
                    delay(weather.coerceIn(15, 24 * 60) * 60_000L)
                    if (!idle.get()) return@launch
                    PrivilegedShell.run("settings put secure location_mode 3")
                    PrivilegedShell.run("settings put global mobile_data 1")
                    delay(45_000)
                    if (idle.get() && policy().batteryGuardEnabled) {
                        if (p.batteryScreenOffGps) PrivilegedShell.run("settings put secure location_mode 0")
                        if (p.batteryScreenOffData) PrivilegedShell.run("settings put global mobile_data 0")
                    }
                }
            }
        }
        val sync = p.batterySyncWakeMinutes
        if (sync > 0) {
            scope.launch {
                while (idle.get()) {
                    delay(sync.coerceIn(15, 24 * 60) * 60_000L)
                    if (!idle.get()) return@launch
                    PrivilegedShell.run("settings put global mobile_data 1")
                    runCatching { ContentResolver.setMasterSyncAutomatically(true) }
                    runCatching { ContentResolver.requestSync(null, null, android.os.Bundle()) }
                    delay(60_000)
                    if (idle.get() && policy().batteryScreenOffData) {
                        PrivilegedShell.run("settings put global mobile_data 0")
                    }
                    if (idle.get() && policy().batteryScreenOffSync) {
                        runCatching { ContentResolver.setMasterSyncAutomatically(false) }
                    }
                }
            }
        }
    }

    private fun shouldHoldMic(callHold: Set<String>): Boolean {
        val telecom = context.getSystemService(TelecomManager::class.java)
        if (telecom?.isInCall == true) return true
        if (Build.VERSION.SDK_INT >= 31 && telecom?.isInManagedCall == true) return true
        if (foregroundIn(callHold)) return true
        val nm = context.getSystemService(NotificationManager::class.java)
        val active = runCatching { nm?.activeNotifications }.getOrNull().orEmpty()
        return active.any { it.packageName in callHold }
    }

    private fun foregroundIn(packages: Set<String>): Boolean {
        if (packages.isEmpty()) return false
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return false
        val now = System.currentTimeMillis()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 60_000, now) ?: return false
        val top = stats.maxByOrNull { it.lastTimeUsed } ?: return false
        return top.packageName in packages && now - top.lastTimeUsed < 120_000
    }

    private data class Snapshot(
        val locationMode: String?,
        val mobileData: String?,
        val radio: String?,
        val masterSync: Boolean,
    ) {
        suspend fun restore() {
            locationMode?.let { PrivilegedShell.run("settings put secure location_mode $it") }
            mobileData?.let { PrivilegedShell.run("settings put global mobile_data $it") }
            radio?.let { PrivilegedShell.run("settings put global preferred_network_mode $it") }
            runCatching { ContentResolver.setMasterSyncAutomatically(masterSync) }
        }

        companion object {
            suspend fun capture(context: Context): Snapshot = withContext(Dispatchers.IO) {
                Snapshot(
                    locationMode = PrivilegedShell.run("settings get secure location_mode")?.output?.trim(),
                    mobileData = PrivilegedShell.run("settings get global mobile_data")?.output?.trim(),
                    radio = PrivilegedShell.run("settings get global preferred_network_mode")?.output?.trim(),
                    masterSync = ContentResolver.getMasterSyncAutomatically(),
                )
            }
        }
    }

    companion object {
        val RECOMMENDED_GPS = listOf(
            "com.google.android.apps.maps",
            "com.waze",
            "app.organicmaps",
            "com.mapswithme.maps.pro",
            "com.here.app.maps",
        )
        val RECOMMENDED_CALL = listOf(
            "com.whatsapp",
            "org.telegram.messenger",
            "org.thunderdog.challegram",
            "org.thoughtcrime.securesms",
            "com.viber.voip",
            "com.google.android.apps.tachyon",
            "us.zoom.videomeetings",
            "com.discord",
            "com.skype.raider",
            "org.jitsi.meet",
            "com.facebook.orca",
        )

        fun holdSet(stored: String, recommended: List<String>): Set<String> {
            val extra = stored.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            return (recommended + extra).toSet()
        }

        fun installedRecommended(context: Context, packages: List<String>): List<String> {
            val pm = context.packageManager
            return packages.filter { pkg ->
                runCatching { pm.getPackageInfo(pkg, 0) }.isSuccess
            }
        }
    }
}
