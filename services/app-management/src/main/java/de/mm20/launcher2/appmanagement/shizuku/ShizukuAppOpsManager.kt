package de.mm20.launcher2.appmanagement.shizuku

import android.util.Log
import de.mm20.launcher2.ktx.TelosShellEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ShizukuAppOpsManager {
    private const val TAG = "ShizukuAppOpsManager"
    private val OP_NAME_REGEX = Regex("^[A-Z0-9_]+$")

    enum class OpMode(val modeName: String) {
        Allow("allow"),
        Ignore("ignore"),
        Deny("deny"),
        Default("default")
    }

    suspend fun setAppOpMode(packageName: String, opName: String, mode: OpMode): Boolean = withContext(Dispatchers.IO) {
        if (!TelosShellEngine.isValidPackageName(packageName) || !OP_NAME_REGEX.matches(opName)) {
            Log.w(TAG, "Invalid packageName or opName: $packageName / $opName")
            return@withContext false
        }
        try {
            val command = "cmd appops set $packageName $opName ${mode.modeName}"
            TelosShellEngine.execResult(command, timeoutMs = 10_000L).isSuccess
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set AppOp $opName for $packageName", e)
            false
        }
    }

    suspend fun restrictBackgroundExecution(packageName: String, restrict: Boolean): Boolean {
        val mode = if (restrict) OpMode.Ignore else OpMode.Allow
        return setAppOpMode(packageName, "RUN_IN_BACKGROUND", mode) &&
               setAppOpMode(packageName, "RUN_ANY_IN_BACKGROUND", mode)
    }
}
