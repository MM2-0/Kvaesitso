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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.embed

import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.privacysandbox.ui.client.view.SandboxedSdkView
import com.vitorpamplona.amethyst.commons.napplet.NappletInlineDownloads
import com.vitorpamplona.amethyst.commons.ui.platform.InlineNappletStatus
import com.vitorpamplona.amethyst.napplet.NappletLauncher
import com.vitorpamplona.amethyst.napplethost.NappletHostContract
import com.vitorpamplona.amethyst.ui.screen.loggedIn.favorites.EmbeddedNostrAppController
import com.vitorpamplona.quartz.nip5dNapplets.NappletManifest

/**
 * A napplet running inside a note: the same cross-process surface the favorite tabs use (the page
 * stays in the keyless `:napplet` process), owned by the note instead of the tab host, so it ends
 * when the note leaves the composition.
 *
 * Unlike a tab, the surface doesn't claim scroll gestures: the feed keeps vertical drags and the
 * page gets taps. It is z-ordered below the window, so the note draws its bar and overlays over it.
 */
@RequiresApi(Build.VERSION_CODES.R)
@Composable
fun InlineNappletView(
    manifest: NappletManifest,
    instanceId: String,
    onStatus: (InlineNappletStatus) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    InlineNappletView(manifest, instanceId, onStatus, modifier) { NappletLauncher.buildInlineLaunchParams(context, manifest, instanceId) }
}

/**
 * [InlineNappletView] for any app a note runs: [launchParams] builds its sandbox launch (null when
 * refused), once per [key].
 */
@RequiresApi(Build.VERSION_CODES.R)
@Composable
fun InlineNappletView(
    key: Any,
    instanceId: String,
    onStatus: (InlineNappletStatus) -> Unit,
    modifier: Modifier,
    launchParams: () -> Bundle?,
) {
    val context = LocalContext.current
    val background = MaterialTheme.colorScheme.background.toArgb()
    val status = rememberUpdatedState(onStatus)

    val controller =
        remember(key, instanceId) {
            launchParams()?.let { params ->
                params.putInt(NappletHostContract.EXTRA_BG_COLOR, background)
                EmbeddedNostrAppController(context.applicationContext, params).apply { refusesFileChooser = true }
            }
        }

    if (controller == null) {
        LaunchedEffect(key, instanceId) { status.value(InlineNappletStatus.FAILED) }
        return
    }

    DisposableEffect(controller) {
        controller.bind()
        controller.onLoadStatusChanged = { status.value(it.toInline()) }
        // The note's stage draws the bar; the run is known to it by its instance id.
        controller.onDownloadChanged = { NappletInlineDownloads.shared.report(instanceId, it) }
        status.value(controller.loadStatus.toInline())
        onDispose {
            controller.onLoadStatusChanged = null
            controller.onDownloadChanged = null
            controller.teardown()
        }
    }

    // Nobody is looking while the app is in the background: the page stops acting for the user and
    // pauses until it comes back.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, controller) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> {
                        controller.onAppVisibility(true)
                        controller.onShown()
                    }
                    Lifecycle.Event.ON_STOP -> {
                        controller.onAppVisibility(false)
                        controller.onHidden()
                    }
                    else -> {}
                }
            }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    AndroidView(
        factory = { ctx ->
            // androidx.privacysandbox.ui is deprecated with no successor; the napplet sandbox
            // still renders through it until it is replaced.
            @Suppress("DEPRECATION")
            SandboxedSdkView(ctx).also {
                controller.attachView(it)
                controller.onShown()
            }
        },
        onRelease = { controller.detachView(it) },
        modifier = modifier,
    )
}

private fun EmbeddedLoadStatus.toInline(): InlineNappletStatus =
    when {
        failed -> InlineNappletStatus.FAILED
        hasLoadedReal -> InlineNappletStatus.SHOWING
        else -> InlineNappletStatus.LOADING
    }
