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
package com.vitorpamplona.amethyst.service.priority

import android.content.Context
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import com.vitorpamplona.quartz.utils.Log
import java.io.File

/**
 * Demotes the app's network/ingest worker threads below the UI thread so a cold-start relay storm
 * cannot starve the main thread out of its frames.
 *
 * **Why this exists.** On a cold start the outbox model dials ~190 relays at once and the process
 * grows to ~650 threads (OkHttp's TaskRunner pool, OkHttp dispatchers, the kotlinx scheduler and
 * Arti's tokio workers). Every one of them is born at nice 0. The main thread is nice -10, but a
 * single -10 thread against dozens of simultaneously-runnable nice-0 threads still loses a large
 * share of its schedulable time to the runqueue — long enough that the first feed frame takes
 * seconds and the "Loading account" screen stays on-screen well after the account itself has
 * loaded.
 *
 * **Measured on a release-codegen build** (`:amethyst:installPlayBenchmark`, i.e. R8-minified +
 * baseline-profile AOT), SM-T220, 5-round round-robin, every run valid:
 *
 * | workers at | starvation | runqueue wait | time to first paint | spread |
 * |---|---|---|---|---|
 * | nice 0 (off) | 26.7% | 2300 ms | 11.1 s | 5.63 s |
 * | nice 5 | 22.0% | 1774 ms | 8.0 s | 4.05 s |
 * | nice 9 | 17.1% | 1280 ms | 8.4 s | 3.05 s |
 * | **nice 10** | **14.6%** | **1234 ms** | **6.2 s** | **1.55 s** |
 *
 * nice 10 beat the control in 5 of 5 paired rounds (median 5.0 s faster, ~45%) and collapsed the
 * run-to-run spread from 5.6 s to 1.6 s, so [DEFAULT_NICE] is 10.
 *
 * **This effect only exists in a release build, so never re-validate it on a debug one.** In a
 * debug build the same sweep changes nothing measurable: there the main thread is ~70% busy,
 * saturated with ART interpretation, so scheduling was never the constraint (starvation is 15% in
 * debug vs 27% in release). R8 collapses main's own work while leaving the relay storm untouched,
 * which is what promotes starvation to the binding constraint. An emulator is equally misleading
 * for the opposite reason — its shared cores manufacture contention real hardware does not have.
 *
 * **Why a /proc sweep instead of thread factories.** The largest pool by far is OkHttp's
 * `TaskRunner` backend, a process-wide singleton whose thread factory OkHttp does not expose per
 * client, so there is no injection point to set a priority at creation time. Sweeping
 * `/proc/self/task` catches every pool uniformly — including threads OkHttp renames after the host
 * they are currently serving. A nice value is per-OS-thread and survives renaming, so seeing a
 * thread once is enough; the sweep only has to be frequent enough to catch newly-spawned ones.
 *
 * Note that [Thread.setPriority] does NOT map to a Linux nice level on Android — only
 * [Process.setThreadPriority] does. (`AudioTrackPlayer` documents the same trap for audio.)
 *
 * **Scope.** [DENYLIST] holds the threads that must keep their scheduling: the main thread,
 * RenderThread (it draws the frames we are trying to protect), the ART daemons (demoting
 * HeapTaskDaemon would make the GC pressure *worse*, not better) and binder threads (IPC replies
 * the system waits on). Everything else is app work that should yield to the UI.
 *
 * **Cost.** Each thread is touched once, not once per sweep, and the interval backs off whenever a
 * sweep finds nothing new — the storm front-loads thread creation, so most sweeps after the first
 * few seconds are empty. Threads that exit are pruned so a recycled tid is re-evaluated.
 *
 * **Runtime override.** [SETTING_KEY] overrides [DEFAULT_NICE] without a rebuild, and is also the
 * off switch (any value <= 0 disables the governor entirely):
 * ```
 * adb shell settings put global amethyst_worker_nice 5    # demote to nice 5 instead
 * adb shell settings put global amethyst_worker_nice 0    # disable
 * adb shell settings delete global amethyst_worker_nice   # back to DEFAULT_NICE
 * ```
 * Despite AOSP's `androidSetThreadPriority` calling `set_sched_policy(SP_BACKGROUND)` at nice >= 10,
 * no cpuset/schedtune move was observed on real hardware (SM-T220 / Android 14): at nice 5, 9 and 10
 * every worker kept main's exact membership (`schedtune:/top-app`, `cpuset:/top-app`, `cpu:/`) and
 * only the nice value changed — so 10 carries no hidden cgroup penalty over 9.
 */
object WorkerThreadPriorityGovernor {
    /** `Settings.Global` key overriding [DEFAULT_NICE]; any value <= 0 disables the governor. */
    const val SETTING_KEY = "amethyst_worker_nice"

    /** Best measured value on a release-codegen build — see the table in the class doc. */
    const val DEFAULT_NICE = 10

    /** Sweep cadence while threads are still appearing. */
    private const val MIN_INTERVAL_MS = 250L

    /** Ceiling the interval backs off to while a sweep keeps finding nothing new. */
    private const val MAX_BURST_INTERVAL_MS = 2_000L

    /** How long to stay in the adaptive burst before settling at [IDLE_INTERVAL_MS]. */
    private const val BURST_DURATION_MS = 120_000L

    /** Steady-state cadence; relay reconnects still spawn threads long after boot. */
    private const val IDLE_INTERVAL_MS = 5_000L

    /**
     * Threads whose scheduling must not be touched. Matched as prefixes against the kernel `comm`
     * (which the kernel caps at 15 characters, so these are deliberately short).
     */
    private val DENYLIST =
        listOf(
            // Draws the frames this whole exercise is meant to protect.
            "RenderThread",
            "hwuiTask",
            "GPU completion",
            // ART daemons — demoting the GC would deepen the very stalls we are fixing.
            "HeapTaskDaemon",
            "ReferenceQueueD",
            "FinalizerDaemon",
            "FinalizerWatchd",
            "Signal Catcher",
            "Jit thread pool",
            "Runtime worker",
            "perfetto_hprof",
            // Debugger/profiler plumbing.
            "ADB-JDWP",
            "JDWP",
            // Synchronous IPC the system framework blocks on.
            "binder:",
        )

    @Volatile private var started = false

    fun start(context: Context) {
        if (started) return
        val targetNice = resolveTargetNice(context)
        if (targetNice == null) {
            Log.i("ThreadPriority") { "Worker thread governor disabled via $SETTING_KEY" }
            return
        }
        started = true
        Log.i("ThreadPriority") { "Worker thread governor on, target nice=$targetNice" }

        Thread({ sweepLoop(targetNice) }, "worker-nice-governor")
            .apply {
                isDaemon = true
                start()
            }
    }

    /** Returns the nice level to apply, or null when the governor should not run at all. */
    private fun resolveTargetNice(context: Context): Int? {
        val configured =
            runCatching {
                Settings.Global.getInt(context.contentResolver, SETTING_KEY, DEFAULT_NICE)
            }.getOrDefault(DEFAULT_NICE)

        // Only a demotion makes sense here; <= 0 is the documented off switch and anything above
        // the nice ceiling is a typo we should not act on.
        return configured.takeIf { it in 1..19 }
    }

    private fun sweepLoop(targetNice: Int) {
        // The governor must keep running while the pools it polices saturate the CPU, so it runs
        // slightly above default rather than as background work.
        runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND) }

        val startedAt = SystemClock.elapsedRealtime()
        val mainTid = Process.myPid()
        // Tids already dealt with — demoted, or skipped because they are on the denylist. Both are
        // permanent decisions, so keeping them here means a thread costs one `comm` read for its
        // whole life instead of one per sweep. Seeded with our own tid so the sweep can't demote
        // the governor itself.
        val handled = HashSet<Int>().apply { add(Process.myTid()) }
        var interval = MIN_INTERVAL_MS

        while (true) {
            val demoted = sweepOnce(mainTid, targetNice, handled)
            if (demoted > 0) {
                Log.d("ThreadPriority") { "Demoted $demoted thread(s) to nice $targetNice" }
            }

            // Thread creation is front-loaded into the connect storm, so once a sweep comes back
            // empty the next one almost certainly will too — back off instead of spinning.
            interval =
                when {
                    SystemClock.elapsedRealtime() - startedAt >= BURST_DURATION_MS -> IDLE_INTERVAL_MS
                    demoted > 0 -> MIN_INTERVAL_MS
                    else -> (interval * 2).coerceAtMost(MAX_BURST_INTERVAL_MS)
                }

            runCatching { Thread.sleep(interval) }.onFailure { return }
        }
    }

    private fun sweepOnce(
        mainTid: Int,
        targetNice: Int,
        handled: MutableSet<Int>,
    ): Int {
        // list() rather than listFiles(): this runs hundreds of times over a boot and the File
        // objects would be pure garbage on an already GC-pressured heap.
        val tidNames = File("/proc/self/task").list() ?: return 0
        val live = HashSet<Int>(tidNames.size * 2)
        var demoted = 0

        for (tidName in tidNames) {
            val tid = tidName.toIntOrNull() ?: continue
            live.add(tid)
            if (tid == mainTid || tid in handled) continue

            // A thread can exit between listing and reading; treat any failure as "skip" and let
            // the next sweep retry, since it is not yet recorded in `handled`.
            val name =
                runCatching {
                    File("/proc/self/task/$tidName/comm").readText().trim()
                }.getOrNull() ?: continue

            if (DENYLIST.any { name.startsWith(it) }) {
                handled.add(tid)
                continue
            }

            // A thread that raised itself chose its priority on purpose: ExoPlayer's playback
            // thread (-16), the nests AudioTrack writer and WebRTC's audio threads (audio
            // priority). Demoting them to [targetNice] is what makes audio underrun under load.
            val current = runCatching { Process.getThreadPriority(tid) }.getOrNull() ?: continue
            if (current < 0) {
                handled.add(tid)
                continue
            }

            if (runCatching { Process.setThreadPriority(tid, targetNice) }.isSuccess) {
                handled.add(tid)
                demoted++
            }
        }

        // Drop tids that have exited so the kernel recycling one into a new thread doesn't leave
        // that thread permanently un-demoted.
        handled.retainAll(live)
        return demoted
    }
}
