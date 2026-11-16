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

import androidx.compose.runtime.Composable

/** Asks the user to prove it is them (biometrics or the device PIN) before a sensitive action. */
fun interface DeviceAuthenticator {
    /**
     * Prompts with [title] and runs [onApproved] once the user passes. A device without a screen
     * lock approves at once. [onError] gets a title and a message for a failed attempt.
     */
    fun authenticate(
        title: String,
        onApproved: () -> Unit,
        onError: (title: String, message: String) -> Unit,
    )
}

/**
 * The platform's device-credential prompt. Android shows BiometricPrompt (strong biometrics or
 * the device credential) and falls back to the keyguard screen; desktop and iOS have no prompt
 * wired yet and approve at once, as Android does on a device without a screen lock.
 */
@Composable
expect fun rememberDeviceAuthenticator(): DeviceAuthenticator
