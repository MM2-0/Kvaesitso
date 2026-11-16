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
package com.vitorpamplona.amethyst.model.preferences

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Stable
import androidx.core.content.getSystemService
import androidx.core.os.LocaleListCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.vitorpamplona.amethyst.commons.model.ThemeType
import com.vitorpamplona.amethyst.commons.model.UiSettings
import com.vitorpamplona.amethyst.commons.model.UiSettingsFlow
import com.vitorpamplona.amethyst.commons.model.preferences.UiSettingsStore
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * The Android half of the UI settings: the flows the app observes, and the two
 * platform side effects that a theme or language change has to perform.
 *
 * Persistence is [UiSettingsStore] in `commons`, which every front end shares.
 * What stays here is the part that has no desktop equivalent — the per-app night
 * mode override that the launch splash reads, and AppCompat's locale list.
 */
@Stable
class UiSharedPreferences(
    prefs: UiSettings,
    dataStore: DataStore<Preferences>,
    val context: Context,
    val scope: CoroutineScope,
) {
    private val store = UiSettingsStore(dataStore)

    // UI Preferences. Makes sure to wait for it to avoid blinking themes and language preferences
    val value = UiSettingsFlow.build(prefs)

    val languageUpdate =
        value.preferredLanguage
            .onEach { language -> applyLanguage(language) }
            .flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                value.toSettings(),
            )

    val nightModeUpdate =
        value.theme
            .onEach { theme -> applyNightMode(theme) }
            .flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                prefs.theme,
            )

    /**
     * Mirrors the in-app theme choice into the system's *per-application* night mode, so the
     * launch splash agrees with a theme that is pinned against the phone's own light/dark setting.
     *
     * The system composites the splash from the manifest theme before the process starts, resolving
     * it against this app's configuration -- so day/night resource qualifiers alone can only ever
     * follow the phone. [UiModeManager.setApplicationNightMode] commits a *persisted per-package
     * configuration override* (UiModeManagerService hands it to
     * ActivityTaskManagerInternal.PackageConfigurationUpdater), which the system then applies when
     * it launches the app. That is what carries a pinned LIGHT/DARK choice into the splash, from
     * the next cold start onwards -- the current launch is already painted.
     *
     * This is deliberately [UiModeManager.setApplicationNightMode] and not
     * [UiModeManager.setNightMode]: the latter changes the night mode for every app on the device
     * and is gated behind MODIFY_DAY_NIGHT_MODE, which this app does not hold -- that call was a
     * silent no-op and was removed. The per-application setter is the documented app-local
     * alternative and is not permission-checked; UiModeManagerService only validates the argument.
     *
     * MODE_NIGHT_AUTO is how [ThemeType.SYSTEM] is expressed: the service maps everything other
     * than YES/NO onto `Configuration.UI_MODE_NIGHT_UNDEFINED`, which clears the override and lets
     * the app fall back to the device configuration.
     *
     * Not deduplicated, deliberately. There is no public getter for the per-application override,
     * so the only way to skip a repeat call would be to shadow it in our own store -- a cache of
     * state we do not own, which goes stale silently and takes the splash with it. Re-sending the
     * value on every launch is self-healing instead, and the platform already no-ops the expensive
     * half: PackageConfigPersister.updateFromImpl returns early without writing when the mode is
     * unchanged, and ActivityRecord.applyAppSpecificConfig gates the activity reconfiguration on
     * having actually changed. What remains is one Binder round trip per launch, off the main
     * thread. (This is why the deduplication in applyLanguage below does not generalise here: it
     * compares against getApplicationLocales(), the authoritative value, not a private copy.)
     *
     * MainActivity declares `uiMode` in its `configChanges`, so any change that does result is
     * delivered to `onConfigurationChanged` rather than recreating the activity.
     */
    private suspend fun applyNightMode(theme: ThemeType) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

        val mode =
            when (theme) {
                ThemeType.DARK -> UiModeManager.MODE_NIGHT_YES
                ThemeType.LIGHT -> UiModeManager.MODE_NIGHT_NO
                ThemeType.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
            }

        try {
            context.getSystemService<UiModeManager>()?.setApplicationNightMode(mode)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("UiSharedPreferences", "Could not apply the per-application night mode", e)
        }
    }

    /**
     * Pushes the preferred language into AppCompat, skipping the call when the app already
     * runs in that locale.
     *
     * On API 33+, [AppCompatDelegate.setApplicationLocales] does not deduplicate: every call
     * is a blocking Binder round trip into the system's LocaleManagerService, which commits a
     * SharedPreferences file (and, on Samsung ROMs, appends to a log file) before returning.
     * That was measured at ~220ms on a Galaxy device, charged to the calling thread. Since
     * this flow starts eagerly, the app paid it on the main thread on every launch, even when
     * the locale had not changed since the previous run -- and StrictMode reported it as a
     * DiskReadViolation via the Binder call.
     *
     * [AppCompatDelegate.getApplicationLocales] is `@AnyThread` and only reads state, so the
     * comparison runs off the main thread. Actual changes still hop to the main thread:
     * below API 33 AppCompat applies them in process by reconfiguring (and possibly
     * recreating) the active activities.
     */
    private suspend fun applyLanguage(language: String?) {
        val newLocales = LocaleListCompat.forLanguageTags(language)
        if (newLocales == AppCompatDelegate.getApplicationLocales()) return

        withContext(Dispatchers.Main) {
            AppCompatDelegate.setApplicationLocales(newLocales)
        }
    }

    @OptIn(FlowPreview::class)
    val saving =
        value.propertyWatchFlow
            .debounce(1000)
            .distinctUntilChanged()
            .onEach {
                store.save(it)
            }.flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                value.toSettings(),
            )

    companion object {
        suspend fun uiPreferences(dataStore: DataStore<Preferences>): UiSettings? = UiSettingsStore(dataStore).load()
    }
}
