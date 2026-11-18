/*
 * Copyright (c) 2026 Telos contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vitorpamplona.amethyst.ui

import android.content.ComponentCallbacks2
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import com.vitorpamplona.amethyst.commons.ui.theme.HostChrome
import com.vitorpamplona.amethyst.commons.ui.theme.LocalHostChrome
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.ui.app.AmethystApp
import com.vitorpamplona.amethyst.commons.ui.note.elements.NowProvider
import com.vitorpamplona.amethyst.connectedApps.consent.SignerConnectCoordinator
import com.vitorpamplona.amethyst.connectedApps.consent.SignerConsentCoordinator
import com.vitorpamplona.amethyst.napplet.NappletConsentCoordinator
import com.vitorpamplona.amethyst.service.lang.LanguageTranslatorService
import com.vitorpamplona.amethyst.service.notifications.NotificationRelayService
import com.vitorpamplona.amethyst.service.playback.composable.DEFAULT_MUTED_SETTING
import com.vitorpamplona.amethyst.service.playback.pip.BackgroundMedia
import com.vitorpamplona.amethyst.ui.theme.AmethystTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal val LocalAetherHostSize = compositionLocalOf<DpSize?> { null }
private var resumedHosts = 0

/** Full upstream client with deferred startup and Telos appearance defaults. */
@Composable
fun EmbeddedAether(
    card: (Modifier, Shape, Color) -> Modifier = { modifier, _, _ -> modifier },
    heading: @Composable (@Composable () -> Unit) -> Unit = { it() },
    label: @Composable (String, Modifier) -> Unit = { text, modifier -> androidx.compose.material3.Text(text, modifier) },
    brandIcon: (@Composable (Modifier) -> Unit)? = null,
) {
    val chrome = remember(card, heading, label, brandIcon) { HostChrome(card, heading, label, brandIcon) }
    val hostColors = MaterialTheme.colorScheme
    // Client dialogs use Surface's default role. Keep it opaque; only the client's
    // page background inherits the host's single transparent wallpaper backing.
    val colors = remember(hostColors) { hostColors.copy(surface = hostColors.surfaceContainer) }
    val typography = MaterialTheme.typography
    val shapes = MaterialTheme.shapes
    AmethystTheme(hostColors = colors, hostTypography = typography, updateWindow = false) {
        // Keep upstream composition locals while inheriting the Telos user's appearance.
        CompositionLocalProvider(LocalHostChrome provides chrome, LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            MaterialTheme(shapes = shapes) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val size = remember(maxWidth, maxHeight) { DpSize(maxWidth, maxHeight) }
                    CompositionLocalProvider(LocalAetherHostSize provides size) {
                        EmbeddedClientLifecycle()
                        NowProvider {
                            AmethystApp(Amethyst.instance.sessionManager, AndroidAppRoot)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmbeddedClientLifecycle() {
    val activity = LocalActivity.current ?: return
    val application = activity.application as Amethyst
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    DisposableEffect(owner, activity, application) {
        var started = false
        var resumed = false
        fun update() {
            val nextStarted = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            val nextResumed = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if (nextStarted != started) {
                if (nextStarted) {
                    Amethyst.instance.foregroundTracker.embeddedHostStarted()
                    application.foregroundRecycleHook.embeddedHostStarted()
                } else {
                    Amethyst.instance.foregroundTracker.embeddedHostStopped()
                    application.foregroundRecycleHook.embeddedHostStopped()
                    if (!Amethyst.instance.foregroundTracker.isForeground.value) Amethyst.instance.trim(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
                }
                started = nextStarted
            }
            if (nextResumed != resumed) {
                resumedHosts = (resumedHosts + if (nextResumed) 1 else -1).coerceAtLeast(0)
                MainActivity.isResumed = resumedHosts > 0
                resumed = nextResumed
                if (nextResumed) {
                    DEFAULT_MUTED_SETTING.value = true
                    SignerConsentCoordinator.resurfacePending(activity)
                    SignerConnectCoordinator.resurfacePending(activity)
                    NappletConsentCoordinator.resurfacePending(activity)
                    scope.launch(Dispatchers.IO) {
                        if (NotificationRelayService.isEnabled(application)) NotificationRelayService.start(application)
                    }
                } else {
                    Amethyst.instance.applicationIOScope.launch { LanguageTranslatorService.clear() }
                }
            }
        }
        val observer = LifecycleEventObserver { _, _ -> update() }
        owner.lifecycle.addObserver(observer)
        update()
        onDispose {
            owner.lifecycle.removeObserver(observer)
            if (started) {
                Amethyst.instance.foregroundTracker.embeddedHostStopped()
                application.foregroundRecycleHook.embeddedHostStopped()
            }
            if (resumed) {
                resumedHosts = (resumedHosts - 1).coerceAtLeast(0)
                MainActivity.isResumed = resumedHosts > 0
            }
            if (resumedHosts == 0) BackgroundMedia.removeBackgroundControllerAndReleaseIt()
        }
    }
}
