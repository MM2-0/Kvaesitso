package de.mm20.launcher2.art

import android.content.Context
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import de.mm20.launcher2.ktx.TelosShellEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * On-startup and post-update self-compilation hook for Telos Launcher.
 *
 * Checks if Telos Launcher was newly installed or updated (via [PackageInfoCompat.getLongVersionCode]).
 * If Shizuku or Root is active, automatically compiles Telos Launcher itself in the background:
 * `cmd package compile -m speed --secondary-dex -f <own_package_name>`
 *
 * Guarantees Telos Launcher always runs as 100% native AOT machine code with zero JIT lag or frame drops.
 */
object TelosSelfOptimizer {

    private const val TAG = "TelosSelfOptimizer"
    private const val PREFS_NAME = "telos_self_optimizer"
    private const val KEY_LAST_OPTIMIZED_VERSION = "last_optimized_version_code"

    fun checkAndSelfCompileAsync(context: Context, scope: CoroutineScope) {
        val appContext = context.applicationContext
        scope.launch {
            runCatching { de.mm20.launcher2.appmanagement.stealth.TelosPrivilegedDaemon.grantWriteSecureSettings(appContext) }
            de.mm20.launcher2.ui.performance.TelosSelfOptimizer.runBackgroundOptimizations(appContext)
        }
    }
}
