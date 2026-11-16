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
import androidx.compose.runtime.compositionLocalOf
import com.vitorpamplona.amethyst.commons.privacylock.PrivacyLockSettings

/**
 * How the privacy lock asks the user to prove it is them. Android prompts for biometrics or the
 * device credential; the desktop asks for an app password ([usesPassword]), hashed and kept in the
 * lock's settings. See [rememberPrivacyLockPrompter].
 */
interface CredentialPrompter {
    /** True when the platform credential surface can be invoked. */
    val available: Boolean

    /**
     * Prompt the user for a credential. Returns one of [PromptResult].
     * Hardcoded localized reason on Kotlin side — never accept user-supplied
     * strings (security finding #14: avoid OS-prompt spoofing surface).
     */
    suspend fun prompt(): PromptResult

    /** True when the user unlocks by typing the app password rather than through a platform prompt. */
    val usesPassword: Boolean get() = false

    /** Checks [password] against the stored app password; password mode only. */
    suspend fun verifyPassword(password: CharArray): PromptResult = PromptResult.Unavailable

    /**
     * Replaces the app password with [new], or removes it when [new] is null, after checking
     * [current] against the stored one when there is one. Password mode only.
     */
    suspend fun changePassword(
        current: CharArray?,
        new: CharArray?,
    ): PromptResult = PromptResult.Unavailable
}

enum class PromptResult {
    /** User authenticated successfully. */
    Success,

    /** User dismissed the prompt or pressed cancel. Stay locked, no toast. */
    UserCanceled,

    /** Temporary lockout (e.g. too many biometric attempts). */
    TemporaryLockout,

    /**
     * Credential surface permanently unavailable on this device — caller
     * should invoke [com.vitorpamplona.amethyst.commons.privacylock.PrivacyLockState.onCredentialUnavailable].
     */
    Unavailable,

    /** Wrong password / authentication failed. */
    Failed,
}

val LocalCredentialPrompter =
    compositionLocalOf<CredentialPrompter> {
        error("LocalCredentialPrompter not provided — wrap App() with platform CredentialPrompter")
    }

/** The platform's way to unlock the privacy lock whose settings are [settings]. */
@Composable
expect fun rememberPrivacyLockPrompter(settings: PrivacyLockSettings): CredentialPrompter
