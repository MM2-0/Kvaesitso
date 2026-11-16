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
package com.vitorpamplona.amethyst.service.logging

import android.os.Build
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.os.StrictMode.VmPolicy

class Logging {
    companion object {
        fun setup() {
            StrictMode.setThreadPolicy(
                ThreadPolicy
                    .Builder()
                    .detectAll()
                    .penaltyLog()
                    .build(),
            )
            StrictMode.setVmPolicy(
                VmPolicy
                    .Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectActivityLeaks()
                    .detectLeakedClosableObjects()
                    .detectLeakedRegistrationObjects()
                    .detectFileUriExposure()
                    // detectCleartextNetwork() is intentionally NOT enabled. netd enforces it
                    // per-UID at the packet level (it flags a socket whose first bytes aren't a
                    // TLS handshake) and there is no per-host exemption. Every relay WebSocket
                    // tunneled over the embedded Arti Tor SOCKS proxy on 127.0.0.1:17392 opens
                    // with a cleartext SOCKS5 greeting, so this detector fires constantly on
                    // legitimate loopback traffic. The Network-Security-Config localhost
                    // allowlist does NOT silence it — that flag only governs the voluntary
                    // NetworkSecurityPolicy.isCleartextTrafficPermitted() check, not netd.
                    .detectContentUriWithoutPermission()
                    .apply {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            detectCredentialProtectedWhileLocked()
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            detectIncorrectContextUse()
                            detectUnsafeIntentLaunch()
                        }
                    }.penaltyLog()
                    .build(),
            )
            // Looper.getMainLooper().setMessageLogging(LogMonitor())
            // ChoreographerHelper.start()
        }
    }
}
