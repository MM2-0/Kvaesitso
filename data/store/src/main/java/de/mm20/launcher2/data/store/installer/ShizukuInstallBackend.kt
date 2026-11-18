package de.mm20.launcher2.data.store.installer

import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.File
import kotlin.coroutines.resume

/** Uses Android install sessions through the authorized Shizuku process. */
internal class ShizukuInstallBackend(private val context: android.content.Context) : InstallBackend {

    companion object {
        private const val REQUEST_CODE = 9317
        private const val TAG = "ShizukuInstallBackend"
    }

    override suspend fun isAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Throwable) {
            false
        }
    }

    override suspend fun hasPermission(): Boolean {
        if (!isAvailable()) return false
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Throwable) {
            false
        }
    }

    override suspend fun requestPermission(): Boolean {
        if (!isAvailable()) return false
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) return true

        return suspendCancellableCoroutine { cont ->
            val listener = object : Shizuku.OnRequestPermissionResultListener {
                override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                    if (requestCode != REQUEST_CODE) return
                    Shizuku.removeRequestPermissionResultListener(this)
                    if (cont.isActive) {
                        cont.resume(grantResult == PackageManager.PERMISSION_GRANTED)
                    }
                }
            }
            Shizuku.addRequestPermissionResultListener(listener)
            cont.invokeOnCancellation {
                Shizuku.removeRequestPermissionResultListener(listener)
            }
            Shizuku.requestPermission(REQUEST_CODE)
        }
    }

    override suspend fun install(apk: File, packageName: String, allowDowngrade: Boolean): BackendInstallResult =
        withContext(Dispatchers.IO) {
            if (!hasPermission()) {
                return@withContext BackendInstallResult.Failed("Shizuku is not authorized")
            }
            try {
                de.mm20.launcher2.ktx.ShizukuProcesses.init(context.applicationContext)

                installPrivilegedSession(
                    apk = apk,
                    userId = android.os.Process.myUid() / 100000,
                    preserveEnabled = android.os.Build.VERSION.SDK_INT >= 34,
                    allowDowngrade = allowDowngrade,
                ) { command, input ->
                    runInstallCommand(input, de.mm20.launcher2.ktx.ShizukuProcesses.start(command.toTypedArray()))
                }
            } catch (e: Exception) {
                Log.w(TAG, "Shizuku install failed for $packageName", e)
                BackendInstallResult.Failed("Shizuku install failed: ${e.message}", e)
            }
        }
}
