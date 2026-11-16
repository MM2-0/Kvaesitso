package de.mm20.launcher2.ktx

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Looper
import android.os.Handler
import android.os.ParcelFileDescriptor
import de.mm20.launcher2.ktx.shizuku.ICommandService
import de.mm20.launcher2.ktx.shizuku.ShizukuCommandService
import moe.shizuku.server.IRemoteProcess
import rikka.shizuku.Shizuku
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Supported Shizuku UserService binding; no reflection into its private/deprecated process factory. */
object ShizukuProcesses {
    @Volatile private var appContext: Context? = null
    private val monitor = Any()
    private class UnsupportedNewProcessTransaction : UnsupportedOperationException("newProcess transact rejected")
    private class Binding {
        val ready = CountDownLatch(1)
        @Volatile var service: ICommandService? = null
    }
    private var binding: Binding? = null
    fun init(context: Context) {
        if (appContext != null) return
        synchronized(monitor) {
            if (appContext != null) return
            appContext = context.applicationContext
            Shizuku.addBinderDeadListener {
                synchronized(monitor) { binding?.ready?.countDown(); binding = null }
            }
        }
    }
    fun available(): Boolean = runCatching {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    fun start(args: Array<String>, environment: Array<String>? = null, directory: String? = null, retryOnSpawnFailure: Boolean = true): Process {
        check(Looper.myLooper() != Looper.getMainLooper()) { "Privileged processes must start off Main" }
        check(args.isNotEmpty() && available())
        runCatching { startViaNewProcess(args, environment, directory) }.onFailure {
            // Only a rejected transaction proves no process was spawned. An ambiguous reply
            // must not cause a state-changing command or startup script to be replayed.
            if (!retryOnSpawnFailure && it !is UnsupportedNewProcessTransaction) throw it
        }.getOrNull()?.let { return it }
        val context = requireNotNull(appContext) { "ShizukuProcesses is not initialized" }
        val current = synchronized(monitor) {
            binding?.takeIf { it.ready.count > 0 || it.service?.asBinder()?.pingBinder() == true } ?: Binding().also { state ->
                binding = state
                val serviceArgs = Shizuku.UserServiceArgs(ComponentName(context.packageName, ShizukuCommandService::class.java.name))
                    .daemon(false).processNameSuffix("telos-command").version(userServiceVersion(context))
                    .debuggable(context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0)
                val connection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName, binder: IBinder?) {
                        state.service = runCatching { ICommandService.Stub.asInterface(binder)?.takeIf { it.uid == 0 || it.uid == 2000 } }.getOrNull()
                        state.ready.countDown()
                    }
                    override fun onServiceDisconnected(name: ComponentName) { state.service = null; state.ready.countDown() }
                }
                Handler(Looper.getMainLooper()).post {
                    runCatching { Shizuku.bindUserService(serviceArgs, connection) }.onFailure { state.ready.countDown() }
                }
            }
        }
        check(current.ready.await(8, TimeUnit.SECONDS)) { "Shizuku UserService connection timed out" }
        val service = checkNotNull(current.service) { "Shizuku UserService unavailable" }
        val remote = runCatching {
            IRemoteProcess.Stub.asInterface(service.start(args, environment, directory))
        }.getOrElse {
            synchronized(monitor) { binding = null }
            // A failed Binder reply can mean the child already started. Scripts must not replay it.
            if (!retryOnSpawnFailure) throw it
            val retriedState = synchronized(monitor) {
                Binding().also { state ->
                    binding = state
                    val serviceArgs = Shizuku.UserServiceArgs(ComponentName(context.packageName, ShizukuCommandService::class.java.name))
                        .daemon(false).processNameSuffix("telos-command").version(userServiceVersion(context))
                        .debuggable(context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0)
                    val connection = object : ServiceConnection {
                        override fun onServiceConnected(name: ComponentName, binder: IBinder?) {
                            state.service = runCatching { ICommandService.Stub.asInterface(binder)?.takeIf { it.uid == 0 || it.uid == 2000 } }.getOrNull()
                            state.ready.countDown()
                        }
                        override fun onServiceDisconnected(name: ComponentName) { state.service = null; state.ready.countDown() }
                    }
                    Handler(Looper.getMainLooper()).post {
                        runCatching { Shizuku.bindUserService(serviceArgs, connection) }.onFailure { state.ready.countDown() }
                    }
                }
            }
            retriedState.ready.await(8, TimeUnit.SECONDS)
            val retryService = checkNotNull(retriedState.service) { "Shizuku UserService unavailable on retry" }
            IRemoteProcess.Stub.asInterface(retryService.start(args, environment, directory))
        }
        return fromRemoteProcess(requireNotNull(remote))
    }

    /**
     * Direct IShizukuService.newProcess (transaction 7). Built-in Telos Shizuku
     * always exposes this; UserService spawning can still fail when the manager
     * provider races or the APK id has a flavor suffix.
     */
    private fun startViaNewProcess(
        args: Array<String>,
        environment: Array<String>?,
        directory: String?,
    ): Process {
        val binder = requireNotNull(Shizuku.getBinder()) { "Shizuku binder missing" }
        val data = android.os.Parcel.obtain()
        val reply = android.os.Parcel.obtain()
        try {
            data.writeInterfaceToken("moe.shizuku.server.IShizukuService")
            data.writeStringArray(args)
            data.writeStringArray(environment)
            data.writeString(directory)
            if (!binder.transact(7, data, reply, 0)) {
                throw UnsupportedNewProcessTransaction()
            }
            reply.readException()
            val remote = IRemoteProcess.Stub.asInterface(reply.readStrongBinder())
            return fromRemoteProcess(requireNotNull(remote) { "newProcess returned an empty binder" })
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    fun getSystemProperty(name: String, defaultValue: String = ""): String? {
        if (!available()) return null
        val binder = Shizuku.getBinder() ?: return null
        val data = android.os.Parcel.obtain()
        val reply = android.os.Parcel.obtain()
        return try {
            data.writeInterfaceToken("moe.shizuku.server.IShizukuService")
            data.writeString(name)
            data.writeString(defaultValue)
            if (!binder.transact(9, data, reply, 0)) return null
            reply.readException()
            reply.readString()
        } catch (_: Throwable) {
            null
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    fun setSystemProperty(name: String, value: String): Boolean {
        if (!available()) return false
        val binder = Shizuku.getBinder() ?: return false
        val data = android.os.Parcel.obtain()
        val reply = android.os.Parcel.obtain()
        return try {
            data.writeInterfaceToken("moe.shizuku.server.IShizukuService")
            data.writeString(name)
            data.writeString(value)
            if (!binder.transact(10, data, reply, 0)) return false
            reply.readException()
            true
        } catch (_: Throwable) {
            false
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    /** Descriptor ownership boundary, also exercised with a fake binder by Android regression tests. */
    private fun userServiceVersion(context: Context): Int {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val code = if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        return (code and 0x7fffffffL).toInt().coerceAtLeast(1)
    }

    fun fromRemoteProcess(remote: IRemoteProcess): Process {
        return object : Process() {
            private val stdout by lazy { ParcelFileDescriptor.AutoCloseInputStream(remote.inputStream) }
            private val stderr by lazy { ParcelFileDescriptor.AutoCloseInputStream(remote.errorStream) }
            private val stdin by lazy { ParcelFileDescriptor.AutoCloseOutputStream(remote.outputStream) }
            override fun getInputStream() = stdout
            override fun getErrorStream() = stderr
            override fun getOutputStream() = stdin
            override fun waitFor(): Int = remote.waitFor()
            override fun waitFor(timeout: Long, unit: TimeUnit): Boolean = remote.waitForTimeout(timeout, unit.name)
            override fun exitValue(): Int = remote.exitValue()
            override fun isAlive(): Boolean = remote.alive()
            override fun destroy() { runCatching { remote.destroy() }; runCatching { stdin.close() }; runCatching { stdout.close() }; runCatching { stderr.close() } }
        }
    }
}
