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
package com.vitorpamplona.amethyst.ui

import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.ui.app.AmethystApp
import com.vitorpamplona.amethyst.commons.ui.note.elements.NowProvider
import com.vitorpamplona.amethyst.connectedApps.consent.SignerConnectCoordinator
import com.vitorpamplona.amethyst.connectedApps.consent.SignerConsentCoordinator
import com.vitorpamplona.amethyst.debugState
import com.vitorpamplona.amethyst.napplet.NappletConsentCoordinator
import com.vitorpamplona.amethyst.service.lang.LanguageTranslatorService
import com.vitorpamplona.amethyst.service.notifications.NotificationRelayService
import com.vitorpamplona.amethyst.service.playback.composable.DEFAULT_MUTED_SETTING
import com.vitorpamplona.amethyst.service.playback.pip.BackgroundMedia
import com.vitorpamplona.amethyst.ui.theme.AmethystTheme
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    companion object {
        // True only while MainActivity is resumed. Used by the notification
        // pipeline to suppress in-app notifications — PiP/Call activities
        // have their own lifecycle, so MainActivity is paused while they're up.
        @Volatile
        var isResumed: Boolean = false
            private set
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        Log.d("ActivityLifecycle") { "MainActivity.onCreate $this" }

        setContent {
            AmethystTheme {
                NowProvider {
                    AmethystApp(Amethyst.instance.sessionManager, AndroidAppRoot)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isResumed = true

        Log.d("ActivityLifecycle") { "MainActivity.onResume $this" }

        // starts muted every time
        DEFAULT_MUTED_SETTING.value = true

        // A signer prompt left open when the user went to the launcher sits in its own task, which is
        // excluded from Recents: coming back through the launcher would otherwise leave it out of reach
        // while the app that asked waits on it.
        SignerConsentCoordinator.resurfacePending(this)
        SignerConnectCoordinator.resurfacePending(this)
        NappletConsentCoordinator.resurfacePending(this)

        // If always-on notifications are enabled but the foreground service couldn't be
        // started from the background during cold-start (Android 12+ restriction), retry
        // now that the activity is in the foreground.
        if (NotificationRelayService.isEnabled(this)) {
            NotificationRelayService.start(this)
        }
    }

    override fun onPause() {
        isResumed = false
        Log.d("ActivityLifecycle") { "MainActivity.onPause $this" }

        Amethyst.instance.applicationIOScope.launch {
            LanguageTranslatorService.clear()
        }

        Amethyst.instance.applicationIOScope.launch {
            debugState(applicationContext)
            Amethyst.instance.relayReqStats?.printStats()
        }

        super.onPause()
    }

    override fun onStop() {
        super.onStop()

        Log.d("ActivityLifecycle") { "MainActivity.onStop $this" }
    }

    override fun onDestroy() {
        Log.d("ActivityLifecycle") { "MainActivity.onDestroy $this" }

        BackgroundMedia.removeBackgroundControllerAndReleaseIt()

        super.onDestroy()
    }
}
