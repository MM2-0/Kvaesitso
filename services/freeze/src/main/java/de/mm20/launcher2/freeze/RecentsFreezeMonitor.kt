package de.mm20.launcher2.freeze

import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import android.os.Build
import de.mm20.launcher2.preferences.freeze.FreezeSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import rikka.shizuku.Shizuku

private data class RecentsSignal(val id: Int, val task: RecentsTask? = null)

internal data class RecentsTask(val id: Int, val packageName: String, val userId: Int)
internal fun removedLastTask(removed: RecentsTask, remaining: List<RecentsTask>, userId: Int): Boolean =
    removed.userId == userId && remaining.none { it.packageName == removed.packageName && it.userId == userId }

/** Privileged removal callbacks, never inferred from app switches or a truncated task-list diff. */
class RecentsFreezeMonitor internal constructor(
    private val context: Context,
    private val settings: FreezeSettings,
    private val autoFreeze: AutoFreezeController,
    private val freezeManager: FreezeManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _connected = MutableStateFlow(false)
    val connected = _connected.asStateFlow()
    private val events = Channel<RecentsSignal>(256)
    private val refreshQueued = java.util.concurrent.atomic.AtomicBoolean(false)
    private val overflowed = java.util.concurrent.atomic.AtomicBoolean(false)
    private fun offer(signal: RecentsSignal) {
        if (signal.id == REFRESH && signal.task == null && !refreshQueued.compareAndSet(false, true)) return
        if (events.trySend(signal).isFailure) overflowed.set(true)
    }
    private val known = linkedMapOf<Int, RecentsTask>()
    private val removalScheduler by lazy {
        RecentsRemovalScheduler(scope, userId,
            enabled = { _connected.value && settings.freezeOnRecentsRemoval.first() },
            snapshot = ::snapshot,
            onRemoval = { pkg, proof -> autoFreeze.freezeAfterRecentsRemoval(pkg, proof) },
            observationLost = { overflowed.set(true) },
        )
    }
    private val session = java.util.concurrent.atomic.AtomicLong(0)
    private var root: RootRecentsConnection? = null
    private var shizukuConnection = false
    private val userId = Process.myUid() / 100000

    init {
        scope.launch {
            settings.freezeOnRecentsRemoval.collectLatest { enabled ->
                if (!enabled || Build.VERSION.SDK_INT < 29) { disconnect(); return@collectLatest }
                try {
                    freezeManager.refreshBackendState()
                    while (isActive) {
                        val shizukuReady = runCatching { Shizuku.pingBinder() &&
                            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)
                        if (_connected.value && root?.alive == false) disconnect()
                        if (_connected.value && shizukuConnection && !shizukuReady) disconnect()
                        if (!_connected.value) {
                            if (shizukuReady) connect()
                            if (!_connected.value && freezeManager.activeBackend.value == FreezeBackendType.Root) connectRoot()
                        }
                        if (overflowed.getAndSet(false)) disconnect()
                        if (_connected.value) {
                            val event = withTimeoutOrNull(5000) { events.receive() }
                            if (event != null) try { processEvent(event) }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (error: Exception) {
                                android.util.Log.w("RecentsFreeze", "Removal handling failed; reconnecting", error)
                                disconnect()
                            }
                        } else delay(5000)
                    }
                } finally { disconnect() }
            }
        }
    }

    private fun connect() = connectRoot(useShizuku = true)

    private fun connectRoot(useShizuku: Boolean = false) {
        try {
            val observedSession = session.get()
            val connection = RootRecentsConnection(context, userId, useShizuku) { id, task ->
                if (session.get() == observedSession) offer(RecentsSignal(id, task))
            }
            root = connection
            shizukuConnection = useShizuku
            check(connection.awaitReady()) { "Privileged task listener unavailable" }
            val initial = connection.snapshot() ?: error("Privileged task snapshot unavailable")
            known.clear(); initial.forEach { known[it.id] = it }
            _connected.value = true
        } catch (error: Exception) {
            android.util.Log.w("RecentsFreeze", "Task removal detection unavailable", error)
            disconnect()
        }
    }

    private fun disconnect() {
        session.incrementAndGet()
        removalScheduler.reset()
        root?.close(); root = null
        _connected.value = false
        shizukuConnection = false
        known.clear()
        refreshQueued.set(false)
        overflowed.set(false)
        while (events.tryReceive().isSuccess) { /* discard old-session callbacks */ }
    }

    private fun snapshot(): List<RecentsTask>? = root?.snapshot()

    private suspend fun processEvent(signal: RecentsSignal) {
        val id = signal.id
        signal.task?.let { if (it.userId == userId && it.id >= 0) known[it.id] = it }
        if (id == REFRESH) {
            refreshQueued.set(false)
            val tasks = snapshot() ?: run { disconnect(); return }
            tasks.forEach { known[it.id] = it }
            while (known.size > 1024) known.remove(known.keys.first())
            return
        }
        val removed = known[id] ?: return
        if (!removalScheduler.submit(removed)) overflowed.set(true)
    }

    companion object {
        private const val REFRESH = -1
        private const val SYSTEM_UID = 1000
    }
}
