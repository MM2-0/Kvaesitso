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
package com.vitorpamplona.amethyst.commons.model.nip38UserStatuses.nowPlaying

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * What this account shares as its NIP-38 `music` status. Everything is off by default: a listening
 * status is public, so the user opts in.
 *
 * - [shareInApp]: tracks and podcast episodes played in Amethyst.
 * - [shareOtherApps]: whatever another app on this device reports through the OS media controls
 *   (needs a platform permission on Android), except the apps in [blockedApps].
 * - [knownApps]: the other apps seen playing so far (id to label), listed on the settings screen
 *   so the user can block one.
 */
@Immutable
data class NowPlayingSettings(
    val shareInApp: Boolean = false,
    val shareOtherApps: Boolean = false,
    val blockedApps: Set<String> = emptySet(),
    val knownApps: Map<String, String> = emptyMap(),
) {
    fun isEnabled() = shareInApp || shareOtherApps

    fun isAllowed(source: NowPlayingSource): Boolean =
        when (source) {
            NowPlayingSource.InApp -> shareInApp
            is NowPlayingSource.OtherApp -> shareOtherApps && source.id !in blockedApps
        }
}

/**
 * Per-account holder for [NowPlayingSettings] (`Account.nowPlayingSettings`). The platform
 * restores and persists it (Android: `NowPlayingSettingsStore`).
 */
@Stable
class NowPlayingSettingsState {
    private val state = MutableStateFlow(NowPlayingSettings())
    private val restored = MutableStateFlow(false)

    val flow: StateFlow<NowPlayingSettings> = state

    fun restore(settings: NowPlayingSettings) {
        state.update { settings }
        restored.value = true
    }

    /**
     * [NowPlayingSettings.shareOtherApps], silent until the saved settings are restored, so the
     * all-off defaults an account starts with never read as the user turning sharing off.
     */
    fun sharesOtherApps(): Flow<Boolean> =
        flow {
            restored.first { it }
            emitAll(state)
        }.map { it.shareOtherApps }.distinctUntilChanged()

    fun setShareInApp(enabled: Boolean) = state.update { it.copy(shareInApp = enabled) }

    fun setShareOtherApps(enabled: Boolean) = state.update { it.copy(shareOtherApps = enabled) }

    fun setAppBlocked(
        appId: String,
        blocked: Boolean,
    ) = state.update { it.copy(blockedApps = if (blocked) it.blockedApps + appId else it.blockedApps - appId) }

    /** Records an app seen playing, so the settings screen can offer to block it. */
    fun rememberApp(
        appId: String,
        label: String,
    ) {
        if (state.value.knownApps[appId] == label) return
        state.update { it.copy(knownApps = it.knownApps + (appId to label)) }
    }

    fun forgetApp(appId: String) =
        state.update {
            it.copy(knownApps = it.knownApps - appId, blockedApps = it.blockedApps - appId)
        }
}
