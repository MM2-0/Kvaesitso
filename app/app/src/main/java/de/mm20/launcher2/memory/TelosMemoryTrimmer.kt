package de.mm20.launcher2.memory

import android.app.Application
import android.content.BroadcastReceiver
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.util.Log
import de.mm20.launcher2.appmanagement.art.StartupOptimizationAudit
import de.mm20.launcher2.packages.TelosIconCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * System-wide memory pressure watcher and screen-off cache purger.
 *
 * Implements [ComponentCallbacks2] and listens for [Intent.ACTION_SCREEN_OFF] / [Intent.ACTION_SCREEN_ON]:
 * - On [ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW] or [ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN]:
 *   Clears non-essential caches (ART Optimizer audit reports, search buffers) and trims [TelosIconCache] by 50%.
 * - When the screen turns off for more than 60 seconds: automatically purges temporary caches and evicts
 *   all unpinned icons from RAM, keeping only the Home Screen & Dock icons pinned in memory.
 */
class TelosMemoryTrimmer private constructor(
    private val app: Application,
) : ComponentCallbacks2 {

    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private var screenOffTimerJob: Job? = null
    private val isRegistered = AtomicBoolean(false)

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> onScreenTurnedOff()
                Intent.ACTION_SCREEN_ON -> onScreenTurnedOn()
            }
        }
    }

    fun register() {
        if (!isRegistered.compareAndSet(false, true)) return
        app.registerComponentCallbacks(this)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        app.registerReceiver(screenReceiver, filter)
        Log.i(TAG, "TelosMemoryTrimmer initialized")
    }

    private fun onScreenTurnedOff() {
        screenOffTimerJob?.cancel()
        screenOffTimerJob = scope.launch {
            // Wait 60 seconds before aggressive screen-off purge
            delay(60_000L)
            if (isActive) {
                Log.d(TAG, "Screen off > 60s: Purging non-essential caches & evicting unpinned icons")
                purgeNonEssentialCaches(critical = false)
            }
        }
    }

    private fun onScreenTurnedOn() {
        screenOffTimerJob?.cancel()
        screenOffTimerJob = null
    }

    override fun onTrimMemory(level: Int) {
        when {
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                Log.w(TAG, "onTrimMemory: TRIM_MEMORY_RUNNING_CRITICAL ($level) - Evicting all unpinned caches")
                purgeNonEssentialCaches(critical = true)
            }
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                Log.d(TAG, "onTrimMemory: Moderate trim level ($level) - Trimming icon cache by 50%")
                purgeNonEssentialCaches(critical = false)
            }
            level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                TelosIconCache.trimHalf()
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {}

    override fun onLowMemory() {
        Log.w(TAG, "onLowMemory: System under extreme memory pressure!")
        purgeNonEssentialCaches(critical = true)
    }

    private fun purgeNonEssentialCaches(critical: Boolean) {
        // 1. Evict or trim shared Icon Cache
        if (critical) {
            TelosIconCache.evictUnpinned()
        } else {
            TelosIconCache.trimHalf()
        }

        // 2. Drop heavy ART Optimizer dump/audit reports from memory
        StartupOptimizationAudit.clearCachedAudit()

        if (critical) {
            runCatching { coil.Coil.imageLoader(app).memoryCache?.clear() }
        }
    }

    companion object {
        private const val TAG = "TelosMemoryTrimmer"

        @Volatile
        private var instance: TelosMemoryTrimmer? = null

        fun init(app: Application): TelosMemoryTrimmer {
            return instance ?: synchronized(this) {
                instance ?: TelosMemoryTrimmer(app).also {
                    instance = it
                    it.register()
                }
            }
        }
    }
}
