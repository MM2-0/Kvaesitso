package de.mm20.launcher2.desktopmode

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.Process
import android.provider.Settings
import android.util.Log
import de.mm20.launcher2.ktx.TelosShellEngine
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import kotlin.coroutines.resume

/**
 * Reads/writes the device-wide `Settings.Global.enable_freeform_support` flag that makes the OS
 * treat launched activities as freeform windows instead of fullscreen. Reading it never needs a
 * privilege (global settings are world-readable); writing it requires WRITE_SECURE_SETTINGS,
 * which we self-grant once via Shizuku through the same direct-IPackageManager-binder mechanism
 * [de.mm20.launcher2.freeze.providers.ShizukuProvider] already uses for suspend/enable calls -
 * WRITE_SECURE_SETTINGS carries the "development" protection flag specifically so shell (and
 * therefore Shizuku, which runs as shell or root) is allowed to grant it, exactly like the
 * well-known `adb shell pm grant <pkg> android.permission.WRITE_SECURE_SETTINGS` workaround.
 * Relies on the `rikka.shizuku.ShizukuProvider` manifest entry declared by services:freeze - the
 * Shizuku binder connection is a process-wide singleton, so no second provider is needed here.
 * Every operation is best-effort and degrades silently to "unavailable" rather than throwing,
 * matching the pattern already established for Freeze Manager's privileged operations.
 */
internal class FreeformAccessProvider(private val context: Context) {

    companion object {
        private const val REQUEST_CODE = 9317
        private const val TAG = "FreeformAccessProvider"
        private const val SETTING_KEY = "enable_freeform_support"
        private const val PERMISSION = "android.permission.WRITE_SECURE_SETTINGS"
    }

    private val isRoot: Boolean
        get() = runCatching { Shizuku.getUid() == 0 }.getOrDefault(false)

    private val callerPackage: String
        get() = if (isRoot) context.packageName else "com.android.shell"

    private val userId: Int
        get() = Process.myUid() / 100000

    /** True if the current OS build/branding is expected to honor this flag at all. */
    val isPotentiallySupported: Boolean =
        isAtLeastApiLevel(Build.VERSION_CODES.N) &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS)

    /** Current value of the OS-level setting. No privilege required to read it. */
    fun isEnabledInSystem(): Boolean {
        return runCatching {
            Settings.Global.getInt(context.contentResolver, SETTING_KEY, 0) == 1
        }.getOrDefault(false)
    }

    /** Whether our own process already holds WRITE_SECURE_SETTINGS (from a previous grant). */
    fun canWriteDirectly(): Boolean {
        return context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun isShizukuAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            Shizuku.pingBinder()
        } catch (e: Throwable) {
            false
        }
    }

    suspend fun hasShizukuPermission(): Boolean {
        return isShizukuAvailable() && try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Throwable) {
            false
        }
    }

    suspend fun requestShizukuPermission(): Boolean {
        if (!isShizukuAvailable()) return false
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

    /**
     * Turns freeform support on/off. Grants ourselves WRITE_SECURE_SETTINGS via Shizuku first if
     * we don't already hold it. @return true if the setting ends up in the requested state.
     */
    suspend fun setEnabled(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (!canWriteDirectly()) {
            if (!grantSelfWriteSecureSettings()) return@withContext false
        }
        runCatching {
            Settings.Global.putInt(context.contentResolver, SETTING_KEY, if (enabled) 1 else 0)
        }.onFailure {
            Log.e(TAG, "Failed to write $SETTING_KEY", it)
        }.isSuccess && isEnabledInSystem() == enabled
    }

    private suspend fun grantSelfWriteSecureSettings(): Boolean {
        val pkg = context.packageName
        if (hasShizukuPermission()) {
            val grantedViaShizuku = runCatching {
                val pm = packageManager() ?: return@runCatching false
                if (isAtLeastApiLevel(Build.VERSION_CODES.P)) {
                    HiddenApiBypass.invoke(
                        pm.javaClass, pm, "grantRuntimePermission",
                        pkg, PERMISSION, userId,
                    )
                } else {
                    pm.javaClass.getMethod(
                        "grantRuntimePermission",
                        String::class.java, String::class.java, Int::class.javaPrimitiveType,
                    ).invoke(pm, pkg, PERMISSION, userId)
                }
                true
            }.onFailure {
                Log.e(TAG, "Failed to grant $PERMISSION via Shizuku", it)
            }.getOrDefault(false)
            if (grantedViaShizuku && canWriteDirectly()) return true
        }
        TelosShellEngine.execRootResult("pm grant $pkg $PERMISSION", 10_000L)
        return canWriteDirectly()
    }

    private fun packageManager(): Any? {
        return runCatching {
            val binder = ShizukuBinderWrapper(SystemServiceHelper.getSystemService("package"))
            val stub = Class.forName("android.content.pm.IPackageManager\$Stub")
            if (isAtLeastApiLevel(Build.VERSION_CODES.P)) {
                HiddenApiBypass.invoke(stub, null, "asInterface", binder)
            } else {
                stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
            }
        }.onFailure { Log.e(TAG, "Could not obtain IPackageManager", it) }.getOrNull()
    }
}
