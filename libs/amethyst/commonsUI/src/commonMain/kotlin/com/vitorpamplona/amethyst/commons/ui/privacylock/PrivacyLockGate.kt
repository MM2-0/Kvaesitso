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
package com.vitorpamplona.amethyst.commons.ui.privacylock

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.privacylock.LocalPrivacyLockState
import com.vitorpamplona.amethyst.commons.privacylock.LockScope
import com.vitorpamplona.amethyst.commons.privacylock.LockState
import com.vitorpamplona.amethyst.commons.privacylock.PrivacyLockSettings
import com.vitorpamplona.amethyst.commons.privacylock.PrivacyLockState

/** The privacy lock's settings, for the settings screen; null where no lock is installed. */
val LocalPrivacyLockSettings = compositionLocalOf<PrivacyLockSettings?> { null }

/**
 * Restarts the privacy lock's idle timers. The host already sees taps, clicks and hardware keys;
 * text fields call this as they change, because the soft keyboard types from its own window.
 */
val LocalUserActivity = staticCompositionLocalOf<() -> Unit> { {} }

/** Whether the wallet blurs while the window is not focused (desktop) once its lock is on. */
private val LocalBlurWalletWhenUnfocused = staticCompositionLocalOf { false }

/**
 * Installs the privacy lock around [content]: one [PrivacyLockState] per scope and the platform's
 * way to unlock. Any touch or key press restarts the idle timers. The app-wide gate is placed by
 * the app inside the account's ViewModel store, so locking does not tear the account down. Without
 * [settings] the app runs unlocked.
 */
@Composable
fun PrivacyLockHost(
    settings: PrivacyLockSettings?,
    blurWalletWhenUnfocused: Boolean = false,
    content: @Composable () -> Unit,
) {
    if (settings == null) {
        content()
        return
    }

    val coroutineScope = rememberCoroutineScope()
    val states = remember(settings) { LockScope.entries.associateWith { PrivacyLockState(it, settings, coroutineScope) } }
    val prompter = rememberPrivacyLockPrompter(settings)
    val onActivity = remember(states) { { states.values.forEach { it.onUserInteraction() } } }

    CompositionLocalProvider(
        LocalPrivacyLockState provides states,
        LocalPrivacyLockSettings provides settings,
        LocalCredentialPrompter provides prompter,
        LocalBlurWalletWhenUnfocused provides blurWalletWhenUnfocused,
        LocalUserActivity provides onActivity,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(states) {
                    awaitPointerEventScope {
                        while (true) {
                            // Observed on the Initial pass and never consumed: taps and scrolls behave as before.
                            awaitPointerEvent(PointerEventPass.Initial)
                            onActivity()
                        }
                    }
                }.onPreviewKeyEvent {
                    onActivity()
                    false
                },
        ) {
            content()
        }
    }
}

/**
 * Shows [content] unless the lock for [scope] is on and engaged, in which case the lock screen
 * takes its place: [content] is not composed at all, so nothing behind the lock is loaded. A null
 * [scope], or no lock installed, shows [content].
 */
@Composable
fun PrivacyLockGate(
    scope: LockScope?,
    content: @Composable () -> Unit,
) {
    val lockState = scope?.let { LocalPrivacyLockState.current[it] }
    if (scope == null || lockState == null) {
        content()
        return
    }

    val current by lockState.state.collectAsState()
    when (current) {
        is LockState.Locked -> {
            LockScreen(scope, lockState)
        }

        is LockState.Unlocked -> {
            if (scope == LockScope.Wallet && LocalBlurWalletWhenUnfocused.current && !LocalWindowInfo.current.isWindowFocused) {
                Box(Modifier.blur(16.dp)) { content() }
            } else {
                content()
            }
        }

        is LockState.Disabled -> {
            content()
        }
    }
}
