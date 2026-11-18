package de.mm20.launcher2.ui.performance

import android.content.Context
import android.os.Process
import android.util.Log
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import de.mm20.launcher2.ktx.TelosShellEngine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

object TelosSelfOptimizer {

    private const val TAG = "TelosSelfOptimizer"
    private const val PREFS_NAME = "telos_optimizer_prefs"
    private const val KEY_LAST_AOT_VERSION = "last_aot_compiled_version_code"
    private val runMutex = Mutex()
    private val backgroundRunStarted = AtomicBoolean(false)

    /**
     * Call this from MainActivity.onCreate() on the main thread.
     * Sets the UI thread priority to the highest possible value for display rendering.
     */
    fun boostUiThreadPriority() {
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY)
            Log.i(TAG, "UI Thread Priority boosted to URGENT_DISPLAY")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to boost UI thread priority", e)
        }
    }

    /**
     * Start the automated background optimization process.
     * Designed to be called roughly 3 seconds after the first frame.
     */
    suspend fun runBackgroundOptimizations(context: Context) {
        if (backgroundRunStarted.getAndSet(true)) return
        withContext(Dispatchers.IO) {
            delay(3000)
            Log.i(TAG, "Starting TelosSelfOptimizer background tasks")
            val appContext = context.applicationContext
            TelosShellEngine.init(appContext)
            if (TelosShellEngine.isPrivilegedAvailable(appContext)) {
                applyShizukuOptimizations(appContext, force = false)
            }
        }
    }

    /**
     * Manually force a re-optimization (e.g. from a UI button).
     */
    suspend fun forceOptimizeNow(context: Context): Boolean {
        val appContext = context.applicationContext
        TelosShellEngine.init(appContext)
        if (!TelosShellEngine.isPrivilegedAvailable(appContext)) {
            if (rikka.shizuku.Shizuku.pingBinder() && rikka.shizuku.Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                rikka.shizuku.Shizuku.requestPermission(0)
            }
            Log.w(TAG, "Cannot force optimize: Privileged shell unavailable")
            return false
        }
        return applyShizukuOptimizations(appContext, force = true)
    }

    private suspend fun applyShizukuOptimizations(context: Context, force: Boolean): Boolean {
        val packageName = context.packageName
        val pid = Process.myPid()

        var success = true

        // 1. AOT Compilation
        try {
            val packageInfo = context.packageManager.getPackageInfo(packageName, 0)
            val currentVersionCode = if (isAtLeastApiLevel(28)) {
                packageInfo.longVersionCode
            } else {
                packageInfo.versionCode.toLong()
            }

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val lastCompiledVersion = prefs.getLong(KEY_LAST_AOT_VERSION, -1L)

            if (force || currentVersionCode != lastCompiledVersion) {
                Log.i(TAG, "AOT Compiling $packageName (version $currentVersionCode)...")
                val compileCmd = "cmd package compile -m speed -f $packageName && cmd package bg-dexopt-job $packageName"
                val result = executeShizukuShell(compileCmd)
                if (result.exitCode == 0) {
                    prefs.edit().putLong(KEY_LAST_AOT_VERSION, currentVersionCode).apply()
                    Log.i(TAG, "AOT Compilation successful.")
                } else {
                    Log.e(TAG, "AOT Compilation failed: ${result.stderr}")
                    success = false
                }
            } else {
                Log.i(TAG, "AOT Compilation skipped (already compiled for version $currentVersionCode)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking/running AOT compile", e)
            success = false
        }

        // 2. Anti-Kill & Whitelist
        try {
            Log.i(TAG, "Applying OS Whitelist and Anti-Kill protection...")
            val antiKillCmd = """
                dumpsys deviceidle whitelist +$packageName >/dev/null 2>&1
                am set-standby-bucket $packageName active >/dev/null 2>&1
                cmd appops set $packageName RUN_IN_BACKGROUND allow >/dev/null 2>&1
                cmd appops set $packageName RUN_ANY_IN_BACKGROUND allow >/dev/null 2>&1
                cmd appops set $packageName SYSTEM_ALERT_WINDOW allow >/dev/null 2>&1
                echo -800 > /proc/$pid/oom_score_adj 2>/dev/null || true
            """.trimIndent()

            val result = executeShizukuShell(antiKillCmd)
            if (result.exitCode == 0) {
                Log.i(TAG, "Anti-Kill protection applied successfully.")
            } else {
                Log.e(TAG, "Anti-Kill protection failed: ${result.stderr}")
                // It's common for some of these to fail (e.g. echo to /proc), so we don't strictly fail the whole process
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error applying anti-kill protection", e)
        }

        return success
    }

    data class ShellResult(val exitCode: Int, val stdout: String, val stderr: String)

    private fun executeShizukuShell(command: String): ShellResult {
        return try {
            val process = de.mm20.launcher2.ktx.ShizukuProcesses.start(arrayOf("sh", "-c", command))
            val stdout = process.inputStream.reader().use { it.readText() }
            val stderr = process.errorStream.reader().use { it.readText() }
            val exitCode = process.waitFor()
            ShellResult(exitCode, stdout, stderr)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute Shizuku shell: $command", e)
            ShellResult(-1, "", e.stackTraceToString())
        }
    }
}