package de.mm20.launcher2.appmanagement.stealth

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.IBinder
import android.os.Process
import android.util.Log
import de.mm20.launcher2.coroutines.TelosDispatchers
import de.mm20.launcher2.ktx.ShizukuProcesses
import de.mm20.launcher2.ktx.TelosShellEngine
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/**
 * Privileged execution bridge owned by Telos.
 *
 * Mode A: Shizuku UserService already runs as [ShizukuCommandService] under Telos's
 * package name with shell UID 2000. Once that process is bound, the external
 * `moe.shizuku.privileged.api` UI can be hidden; commands stay inside Telos.
 * Mode B: live Shizuku/Sui binder, then root (`su`).
 *
 * The exported surface is this in-process Binder only. External apps cannot bind
 * ([android:exported]=false) and [assertCaller] rejects any other UID.
 */
object TelosPrivilegedDaemon {
    private const val TAG = "TelosPrivDaemon"
    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    const val WRITE_SECURE_SETTINGS = "android.permission.WRITE_SECURE_SETTINGS"

    enum class Mode { InternalUserService, ShizukuBinder, Root, None }

    fun assertCaller() {
        val calling = Binder.getCallingUid()
        val mine = Process.myUid()
        if (calling != mine && calling != 0) {
            throw SecurityException("TelosPrivilegedDaemon rejects uid=$calling (mine=$mine)")
        }
    }

    fun currentMode(context: Context): Mode {
        ShizukuProcesses.init(context.applicationContext)
        if (ShizukuProcesses.available()) return Mode.InternalUserService
        val binder = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (binder) return Mode.ShizukuBinder
        if (TelosShellEngine.isPrivilegedAvailable()) return Mode.Root
        return Mode.None
    }

    suspend fun resolveMode(context: Context): Mode {
        val fast = currentMode(context)
        if (fast != Mode.None) return fast
        return if (TelosShellEngine.isRootAvailable()) Mode.Root else Mode.None
    }

    fun isExternalShizukuInstalled(context: Context): Boolean {
        return runCatching {
            context.packageManager.getApplicationInfo(SHIZUKU_PACKAGE, 0)
            true
        }.getOrDefault(false)
    }

    fun hasWriteSecureSettings(context: Context): Boolean {
        return context.checkSelfPermission(WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun exec(command: String, timeoutMs: Long = 15_000L): TelosShellEngine.ShellResult {
        assertCaller()
        return TelosShellEngine.execResult(command, timeoutMs = timeoutMs)
    }

    suspend fun grantWriteSecureSettings(context: Context): Boolean = withContext(TelosDispatchers.Disk) {
        assertCaller()
        if (hasWriteSecureSettings(context)) return@withContext true
        val pkg = context.packageName
        val result = TelosShellEngine.execArgsResult(
            arrayOf("pm", "grant", pkg, WRITE_SECURE_SETTINGS),
            timeoutMs = 15_000L,
        )
        val granted = hasWriteSecureSettings(context)
        if (!granted) {
            Log.w(TAG, "pm grant WRITE_SECURE_SETTINGS failed: ${result.stderr}")
        }
        granted
    }

    suspend fun hideExternalShizuku(context: Context): Boolean = withContext(TelosDispatchers.Disk) {
        assertCaller()
        if (!isExternalShizukuInstalled(context)) return@withContext true
        val hide = TelosShellEngine.execArgsResult(
            arrayOf("pm", "hide", SHIZUKU_PACKAGE),
            timeoutMs = 10_000L,
        )
        if (hide.isSuccess) return@withContext true
        TelosShellEngine.execArgsResult(
            arrayOf("pm", "disable-user", "--user", "0", SHIZUKU_PACKAGE),
            timeoutMs = 10_000L,
        ).isSuccess
    }

    fun warmup(context: Context) {
        ShizukuProcesses.init(context.applicationContext)
        TelosShellEngine.init(context)
    }
}

/** Local-only Binder. Never exported. */
class TelosPrivilegedDaemonService : Service() {
    private val stub = object : ITelosPrivilegedDaemon.Stub() {
        override fun getUid(): Int {
            TelosPrivilegedDaemon.assertCaller()
            return Process.myUid()
        }

        override fun exec(command: String?): String {
            TelosPrivilegedDaemon.assertCaller()
            val cmd = command.orEmpty()
            if (cmd.isBlank() || cmd.length > 8_192) return "invalid"
            return runBlocking(TelosDispatchers.Disk) {
                val r = TelosPrivilegedDaemon.exec(cmd)
                if (r.isSuccess) r.stdout else r.stderr.ifBlank { "exit ${r.exitCode}" }
            }
        }

        override fun grantWriteSecureSettings(): Boolean {
            TelosPrivilegedDaemon.assertCaller()
            return runBlocking(TelosDispatchers.Disk) {
                TelosPrivilegedDaemon.grantWriteSecureSettings(this@TelosPrivilegedDaemonService)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        TelosPrivilegedDaemon.assertCaller()
        return stub
    }
}
