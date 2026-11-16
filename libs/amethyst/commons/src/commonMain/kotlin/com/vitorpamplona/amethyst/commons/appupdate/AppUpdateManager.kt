/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.commons.appupdate

import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.shared.Nip82VersionComparator
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException

/**
 * The updater: checks [sources] in order, offers the newest version, and drives [installer] when
 * the user asks for it.
 *
 * [sources] go trusted first. A later source is asked only when the earlier ones found nothing
 * installable, and an update from it is offered only when it is newer than what they found. Only an
 * update with an asset (which only a trusted source gives) is ever installed.
 *
 * Checks run once [startupDelaySec] after [start] and then every [checkIntervalSec], while
 * [AppUpdateController.autoCheck] is on; a check that reached no source is retried after
 * [retryDelaySec] instead. [checkNow] runs one regardless.
 */
class AppUpdateManager(
    override val currentVersion: String,
    override val method: UpdateMethod,
    private val target: UpdateTarget,
    private val sources: List<UpdateSource>,
    private val installer: AppUpdateInstaller?,
    private val persistence: AppUpdateSettingsPersistence,
    private val scope: CoroutineScope,
    private val now: () -> Long = TimeUtils::now,
    private val startupDelaySec: Long = 60,
    private val checkIntervalSec: Long = 12 * 60 * 60,
    private val retryDelaySec: Long = 30 * 60,
) : AppUpdateController {
    private val _state = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    override val state: StateFlow<AppUpdateState> = _state

    private val _autoCheck = MutableStateFlow(true)
    override val autoCheck: StateFlow<Boolean> = _autoCheck

    private val _skippedVersion = MutableStateFlow<String?>(null)
    override val skippedVersion: StateFlow<String?> = _skippedVersion

    private val _lastCheckAt = MutableStateFlow(0L)

    /** When a check last reached a source, in this run or an earlier one; persisted. */
    override val lastCheckAt: StateFlow<Long> = _lastCheckAt

    /** When the periodic loop checks next: after a success, or sooner after a failure. */
    private val nextCheckAt = MutableStateFlow(0L)

    private val checking = Mutex()
    private val saving = Mutex()
    private var installJob: Job? = null

    // Settings the user changed before the stored ones finished loading: the load keeps them.
    @Volatile private var loadJob: Job? = null

    @Volatile private var autoCheckChanged = false

    @Volatile private var skippedVersionChanged = false

    /** Whether an update found here can be installed here; when not, any source's answer will do. */
    private val canInstallHere get() = installer != null && method.canInstall && target.packages.isNotEmpty()

    /** Loads the settings and starts the periodic check. */
    fun start(): Job {
        val load =
            scope.launch {
                val settings = persistence.load()
                if (!autoCheckChanged) _autoCheck.value = settings.autoCheck
                if (!skippedVersionChanged) _skippedVersion.value = settings.skippedVersion
                // Only shown: every start checks again, however recent the last check was.
                _lastCheckAt.update { maxOf(it, settings.lastCheckAt) }
            }
        loadJob = load
        return scope.launch {
            load.join()
            delay(startupDelaySec * 1000)
            while (isActive) {
                // Sleeps while checks are off, until they are turned back on.
                _autoCheck.first { it }
                if (now() >= nextCheckAt.value) check()
                // Then until the next check is due, or until a check (checkNow) reschedules it or
                // checks are turned off, whichever comes first.
                val due = nextCheckAt.value
                withTimeoutOrNull((due - now()).coerceIn(60, checkIntervalSec) * 1000) {
                    combine(nextCheckAt, _autoCheck) { next, on -> next != due || !on }.first { it }
                }
            }
        }
    }

    override fun setAutoCheck(enabled: Boolean) {
        autoCheckChanged = true
        _autoCheck.value = enabled
        persist()
    }

    override fun skip(version: String) {
        skippedVersionChanged = true
        _skippedVersion.value = version
        persist()
    }

    override fun checkNow() {
        scope.launch { check() }
    }

    /** One check, unless one is running or an update is already on its way in. */
    suspend fun check() {
        if (!checking.tryLock()) return
        try {
            while (true) {
                val current = _state.value
                if (current is AppUpdateState.Downloading || current is AppUpdateState.ReadyToRestart) {
                    // Nothing to look for until the user restarts into it, or it fails.
                    nextCheckAt.value = now() + checkIntervalSec
                    return
                }
                if (_state.compareAndSet(current, AppUpdateState.Checking)) break
            }

            val result =
                when (val outcome = findUpdate()) {
                    is Outcome.Found -> AppUpdateState.Available(outcome.update)
                    Outcome.None -> AppUpdateState.UpToDate(now())
                    Outcome.Unreachable -> AppUpdateState.Failed(UpdateFailure.CHECK_FAILED, null)
                }
            // An install the user started meanwhile owns the state now.
            _state.compareAndSet(AppUpdateState.Checking, result)

            if (result is AppUpdateState.Failed) {
                nextCheckAt.value = now() + retryDelaySec
            } else {
                _lastCheckAt.value = now()
                nextCheckAt.value = _lastCheckAt.value + checkIntervalSec
                persist()
            }
        } finally {
            checking.unlock()
        }
    }

    private sealed interface Outcome {
        data class Found(
            val update: AvailableUpdate,
        ) : Outcome

        data object None : Outcome

        data object Unreachable : Outcome
    }

    private suspend fun findUpdate(): Outcome {
        var best: AvailableUpdate? = null
        var reached = false
        for (source in sources) {
            // A later source is only a fallback: stop once the answer is as good as this install
            // can use (one that only tells about the update needs no installable file).
            if (best != null && (best.canInstall || !canInstallHere)) break
            val found =
                try {
                    source.findUpdate(currentVersion, target).also { reached = true }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Update source ${source::class.simpleName} failed", e)
                    null
                }
            if (found != null && (best == null || Nip82VersionComparator.compare(found.version, best.version) > 0)) {
                best = found
            }
        }
        return when {
            best != null -> Outcome.Found(best)
            reached -> Outcome.None
            else -> Outcome.Unreachable
        }
    }

    override fun install() {
        val installer = installer ?: return
        val update = (_state.value as? AppUpdateState.Available)?.update ?: (_state.value as? AppUpdateState.Failed)?.update ?: return
        if (!update.canInstall || !method.canInstall) return
        if (installJob?.isActive == true) return

        installJob =
            scope.launch {
                _state.value = AppUpdateState.Downloading(update, 0, update.asset?.sizeInBytes)
                _state.value =
                    try {
                        installer.prepare(update) { read, total -> _state.value = AppUpdateState.Downloading(update, read, total) }
                        AppUpdateState.ReadyToRestart(update)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: UpdateInstallException) {
                        Log.w(TAG, "Update ${update.version} not prepared: ${e.failure}", e)
                        AppUpdateState.Failed(e.failure, update)
                    } catch (e: Exception) {
                        Log.w(TAG, "Update ${update.version} not prepared", e)
                        AppUpdateState.Failed(UpdateFailure.INSTALL_FAILED, update)
                    }
            }
    }

    override fun restartToUpdate() {
        val ready = _state.value as? AppUpdateState.ReadyToRestart ?: return
        try {
            installer?.applyAndRestart(ready.update)
        } catch (e: UpdateInstallException) {
            Log.w(TAG, "Could not hand over to update ${ready.update.version}: ${e.failure}", e)
            _state.value = AppUpdateState.Failed(e.failure, ready.update)
        } catch (e: Exception) {
            Log.w(TAG, "Could not hand over to update ${ready.update.version}", e)
            _state.value = AppUpdateState.Failed(UpdateFailure.INSTALL_FAILED, ready.update)
        }
    }

    /**
     * Saves the settings as they are when the save runs, one save at a time, so the last one always
     * holds the newest values; and never before the stored ones are loaded, which it would erase.
     */
    private fun persist() {
        scope.launch {
            try {
                loadJob?.join()
                saving.withLock { persistence.save(AppUpdateSettings(_autoCheck.value, _skippedVersion.value, _lastCheckAt.value)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not save the update settings", e)
            }
        }
    }

    companion object {
        private const val TAG = "AppUpdateManager"
    }
}
