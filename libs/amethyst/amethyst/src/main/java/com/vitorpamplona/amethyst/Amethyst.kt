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
package com.vitorpamplona.amethyst

import android.app.Application
import android.content.ComponentCallbacks2
import android.os.Build
import com.vitorpamplona.amethyst.commons.favorites.FavoriteApp
import com.vitorpamplona.amethyst.commons.service.QuicLogBridge
import com.vitorpamplona.amethyst.commons.service.http.HttpClientEnvironment
import com.vitorpamplona.amethyst.commons.service.http.MediaCallEventListener
import com.vitorpamplona.amethyst.commons.util.AndroidDebugFlag
import com.vitorpamplona.amethyst.favorites.WebShortcuts
import com.vitorpamplona.amethyst.napplet.NsiteNetworkRegistry
import com.vitorpamplona.amethyst.napplet.WebAppNetworkRegistry
import com.vitorpamplona.amethyst.napplet.WebSitePermissionRegistry
import com.vitorpamplona.amethyst.service.logging.Logging
import com.vitorpamplona.amethyst.service.nests.AppForegroundRecycleHook
import com.vitorpamplona.amethyst.service.okhttp.isEmulator
import com.vitorpamplona.amethyst.service.priority.WorkerThreadPriorityGovernor
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.EmbeddedTabHost
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.LogLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The Android [Application]. **Heads-up: this app runs in TWO OS processes**, and Android
 * instantiates this same class in each of them:
 *
 * - **main** — the normal app (UI, account, signer, `LocalCache`, relay client). [instance]
 *   ([AppModules]) is built here in [onCreate].
 * - **`:napplet`** — the sandboxed WebView host for NIP-5D napplets / NIP-5A nSites
 *   (`NappletHostActivity`, declared `android:process=":napplet"`). It holds **no** account or keys.
 *   [onCreate] early-returns here, so [instance] is **left unset** and any access throws.
 *
 * There is no per-process Application in Android — `android:name` is one class for the whole package —
 * so the process-name guard ([isNappletSandbox]) is how the two are kept apart.
 *
 * **Processes don't share memory:** every `object`/companion/`static` (e.g. `LocalCache`,
 * `NappletLaunchRegistry`) is a *separate copy per process*. Don't assume [instance] exists off the
 * main process, and don't try to share state via a singleton across the boundary — use Messenger IPC.
 */
open class Amethyst : Application() {
    init {
        // Keep logging configuration lightweight in every process, including the keyless sandbox.
        Log.minLevel = DEFAULT_LOG_LEVEL
        Log.d("AmethystApp") { "Creating App $this" }
    }

    companion object {
        /**
         * Restores the full firehose in debug builds: per-socket relay lifecycle
         * (`Connecting…`/`Disconnected`/`OnOpen`), per-second event throughput, per-stream Arti
         * SOCKS errors and per-relay census detail.
         *
         * Off by default. A cold start on the outbox model dials ~400 relays, and those sources
         * alone emit ~3.7k of the ~6.2k lines an 80s boot produces — which buries the boot
         * narrative and the relay-protocol warnings (`auth-required`, `rate-limited`,
         * `unsupported: too many filters`) that are the actionable ones. Flip this, or set
         * [Log.minLevel] at runtime, when you need the per-socket detail back.
         */
        const val VERBOSE_LOGS = false

        /**
         * Debug defaults to INFO so a boot reads as a narrative plus warnings; release defaults to
         * WARN rather than ERROR so relay-protocol refusals stay visible in the field — they are
         * the highest-value-per-line diagnostics we emit and were previously dropped entirely.
         */
        val DEFAULT_LOG_LEVEL: LogLevel =
            when {
                // `isDebug` also covers the `benchmark` build type — a release build (R8 + AOT)
                // that exists purely to be measured and is never shipped. Treating it as a release
                // build left it at WARN, which drops every INFO milestone the boot narrative is
                // made of (account load timings, Tor status transitions, the relay census), so the
                // one variant whose numbers are trustworthy was also the one we could not read.
                VERBOSE_LOGS && isDebug -> LogLevel.DEBUG
                isDebug -> LogLevel.INFO
                else -> LogLevel.WARN
            }

        lateinit var instance: AppModules
            private set

        @Volatile
        private var clientReady = false

        val isInitialized: Boolean
            get() = clientReady
    }

    /**
     * Android instantiates this single Application class in EVERY process (there's no per-process
     * Application in the manifest). The sandboxed napplet host runs in `:napplet`, where we must not
     * build [AppModules] — so this is computed once and gates every lifecycle callback that would
     * otherwise touch the unset [instance].
     */
    private val isNappletSandbox by lazy { isNappletSandboxProcess() }

    private val initializationMutex = Mutex()

    protected open val initializeOnCreate: Boolean = true

    override fun onCreate() {
        super.onCreate()
        AndroidDebugFlag.enabled = isDebug
        if (initializeOnCreate && !isNappletSandbox) initializeClient()
    }

    suspend fun initializeEmbedded() = withContext(Dispatchers.IO) {
        check(!isNappletSandbox) { "The browser sandbox cannot initialize an account" }
        initializationMutex.withLock {
            if (!isInitialized) initializeClient()
        }
    }

    private fun initializeClient() {
        QuicLogBridge.install()
        // Before anything logs: shared code's debug-only logging (commons `logTime`) reads this
        // instead of the app's BuildConfig, in both processes.
        AndroidDebugFlag.enabled = isDebug
        Log.d("AmethystApp") { "onCreate $this" }

        // Application.onCreate runs in every process. The sandboxed napplet host
        // (`:napplet`) must NOT build AppModules — that would load the account and
        // construct the signer in the very process we keep secret-free. The host
        // is self-contained (WebView + IPC), so we skip all app init there and
        // leave `instance` unset; any accidental use fails fast.
        if (isNappletSandbox) {
            // Milestone, not chatter: this is the one line that explains why `instance` is unset
            // and `LocalCache` is empty in this process. Without it a napplet-process log looks
            // like a broken app rather than a deliberately secret-free sandbox.
            Log.i("AmethystApp") { "Napplet sandbox process starting — no account, no AppModules" }
            return
        }

        Log.i("AmethystApp") { "Amethyst ${BuildConfig.VERSION_NAME} starting in main process (log level ${Log.minLevel})" }

        // Both flags MUST be set before AppModules: its constructor eagerly builds the
        // OkHttp factories, whose dispatchers read isEmulator at construction time —
        // set afterwards, the emulator-safe limits are never applied.
        MediaCallEventListener.verboseLogging = isDebug
        HttpClientEnvironment.isEmulator = isEmulator()

        instance = AppModules(this)

        // Keeps the ~650 relay/ingest worker threads a cold start spawns from starving the UI
        // thread out of its frames — worth ~45% off time-to-first-paint on a release build.
        // Override or disable with the `amethyst_worker_nice` global setting.
        WorkerThreadPriorityGovernor.start(this)

        // Hydrate the device-local favorite-apps list (main process only; the sandbox never reads it).
        instance.favoriteApps.init()

        // Mirror web-app favorites into the launcher's long-press shortcuts (open in Amethyst's browser).
        // IO: publishing goes through ShortcutManager, a binder call into system_server.
        CoroutineScope(Dispatchers.IO).launch {
            instance.favoriteApps.favorites
                .map { apps -> apps.filterIsInstance<FavoriteApp.WebApp>().map { it.url to it.label } }
                .distinctUntilChanged()
                .collect { WebShortcuts.publishFavorites(this@Amethyst, instance.favoriteApps.favorites.value) }
        }

        // Hydrate the per-site camera/microphone/location answers the browser asks about.
        WebSitePermissionRegistry.init(this)

        // Hydrate the device-local browser visit history (main process only; feeds the omnibox suggestions).
        instance.browserHistory.init()

        // Index device-local captured favicons (main process only; decorates favorites + suggestions).
        instance.browserIcons.init()

        // Warm the global-settings prefs off-main so the first (deliberately synchronous) read of
        // them does not hit disk on the main thread. See LocalPreferences.warmGlobalSettings.
        CoroutineScope(Dispatchers.IO).launch { LocalPreferences.warmGlobalSettings() }

        // Hydrate the per-web-client Tor routing preferences so a site opted out of Tor (some reject Tor
        // exits) starts on the open web without first flashing a failed Tor load.
        WebAppNetworkRegistry.init(this)
        // The same for nSites: a launch reads the choice synchronously, and one read before these have
        // loaded falls back to Tor, whatever "Applets and Websites" says.
        NsiteNetworkRegistry.preload()

        // After-background foreground recycle: when the app returns to
        // the foreground after spending more than ~5 s in the
        // background, publish a network-change event so every active
        // NestViewModel recycles its underlying QUIC session. Covers
        // the case where Android reclaims our UDP socket FD while
        // backgrounded — the connectivity callback in
        // `NestForegroundService` doesn't fire there because the
        // network itself is still up. See `AppForegroundRecycleHook`'s
        // kdoc for the threshold rationale.
        registerActivityLifecycleCallbacks(AppForegroundRecycleHook())

        // Foreground signal for the resource-usage ledger (fg/bg attribution
        // of bytes and connection-time). Main process only.
        registerActivityLifecycleCallbacks(instance.foregroundTracker)

        if (isDebug) {
            Logging.setup()
            // Auto-enable the Nests session-trace recorder in debug
            // builds so two-phone repros can be captured via
            //   adb logcat -s NestsTraceJsonl:D -v raw > nest-trace.jsonl
            // without rebuilding to flip a flag. Off in release.
            com.vitorpamplona.nestsclient.trace.NestsTrace
                .setRecording(true)
        }

        instance.initiate(this)
        clientReady = true
    }

    /** True when this process is the isolated `:napplet` WebView host (see AndroidManifest). */
    private fun isNappletSandboxProcess(): Boolean {
        val name =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                getProcessName()
            } else {
                runCatching { File("/proc/self/cmdline").readText().substringBefore('\u0000').trim() }.getOrNull()
            }
        return name?.endsWith(":napplet") == true
    }

    override fun onTerminate() {
        super.onTerminate()
        Log.d("AmethystApp") { "onTerminate $this" }
        // The sandbox process never built AppModules; `instance` is unset there.
        if (!isInitialized || isNappletSandbox) return
        instance.terminate(this)
    }

    /**
     * Release memory when the UI becomes hidden or when system resources become low.
     *
     * @param level the memory-related event that was raised.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Log.d("AmethystApp") { "onTrimMemory $level" }
        // onTrimMemory IS delivered to the `:napplet` process on real devices, where `instance`
        // was never initialized — guard so a trim callback can't crash the sandbox.
        if (!isInitialized || isNappletSandbox) return
        instance.trim(level)
        // Drop warm embedded tab sessions under genuine memory pressure (decision: keep warm until the
        // user or Android reclaims them). Since API 34 the OS only delivers UI_HIDDEN and BACKGROUND:
        // BACKGROUND means the process is on the system LRU list (real reclaim pressure), while UI_HIDDEN
        // fires on every app switch — so evict only at BACKGROUND and above, letting a pinned tab survive
        // a plain backgrounding. R+ only.
        //
        // rebuildAll, not a plain eviction: the tab screen the user left stays composed, holding its
        // controller, and it only re-acquires when the rebuild epoch moves. Tearing the sessions down without
        // bumping it left that screen with a dead controller and no active tab — blank on return. The epoch
        // bump is picked up by the next recomposition, which doesn't run while the app is backgrounded, so the
        // memory stays freed until the user comes back.
        val pressure = level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND
        if (pressure && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            EmbeddedTabHost.rebuildAll(keepPages = true)
        }
    }
}
