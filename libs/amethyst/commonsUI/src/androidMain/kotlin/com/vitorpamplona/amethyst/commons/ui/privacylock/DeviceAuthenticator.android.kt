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

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.app_name
import com.vitorpamplona.amethyst.commons.resources.biometric_authentication_failed
import com.vitorpamplona.amethyst.commons.resources.biometric_authentication_failed_explainer
import com.vitorpamplona.amethyst.commons.resources.biometric_authentication_failed_explainer_with_error
import com.vitorpamplona.amethyst.commons.ui.stringRes

/** The action waiting on the keyguard activity, which only its result may run or replace. */
private class PendingApproval {
    var action: (() -> Unit)? = null
}

@Composable
actual fun rememberDeviceAuthenticator(): DeviceAuthenticator {
    val context = LocalContext.current
    val labels = rememberAuthPromptLabels()
    val pending = remember { PendingApproval() }

    val keyguardLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            val action = pending.action
            pending.action = null
            if (result.resultCode == Activity.RESULT_OK) {
                action?.invoke()
            }
        }

    return remember(context, labels, keyguardLauncher) {
        DeviceAuthenticator { title, onApproved, onError ->
            pending.action = onApproved
            authenticate(
                title = title,
                context = context,
                labels = labels,
                keyguardLauncher = keyguardLauncher,
                onApproved = {
                    pending.action = null
                    onApproved()
                },
                onError = onError,
            )
        }
    }
}

private fun Context.findFragmentActivity(): FragmentActivity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is FragmentActivity) return current
        current = current.baseContext
    }
    return null
}

/**
 * Labels the device-credential and biometric prompts need. Resolved in
 * composition and passed down because [authenticate] runs from an onClick
 * lambda and from Android's own biometric callbacks, neither of which can
 * reach a Compose resource.
 */
@Immutable
private data class AuthPromptLabels(
    val appName: String,
    val failedTitle: String,
    val failedExplainer: String,
    val failedExplainerWithError: String,
)

@Composable
private fun rememberAuthPromptLabels(): AuthPromptLabels =
    AuthPromptLabels(
        appName = stringRes(Res.string.app_name),
        failedTitle = stringRes(Res.string.biometric_authentication_failed),
        failedExplainer = stringRes(Res.string.biometric_authentication_failed_explainer),
        failedExplainerWithError = stringRes(Res.string.biometric_authentication_failed_explainer_with_error),
    )

private fun authenticate(
    title: String,
    context: Context,
    labels: AuthPromptLabels,
    keyguardLauncher: ManagedActivityResultLauncher<Intent, ActivityResult>,
    onApproved: () -> Unit,
    onError: (String, String) -> Unit,
) {
    val fragmentContext = context.findFragmentActivity() ?: return onApproved()
    val keyguardManager =
        fragmentContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

    if (!keyguardManager.isDeviceSecure) {
        onApproved()
        return
    }

    @Suppress("DEPRECATION")
    fun keyguardPrompt() {
        val intent =
            keyguardManager.createConfirmDeviceCredentialIntent(
                labels.appName,
                title,
            )

        keyguardLauncher.launch(intent)
    }

    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
        keyguardPrompt()
        return
    }

    val biometricManager = BiometricManager.from(context)
    val authenticators =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

    val promptInfo =
        BiometricPrompt.PromptInfo
            .Builder()
            .setTitle(labels.appName)
            .setSubtitle(title)
            .setAllowedAuthenticators(authenticators)
            .build()

    val biometricPrompt =
        BiometricPrompt(
            fragmentContext,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence,
                ) {
                    super.onAuthenticationError(errorCode, errString)

                    when (errorCode) {
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON -> {
                            keyguardPrompt()
                        }

                        BiometricPrompt.ERROR_LOCKOUT -> {
                            keyguardPrompt()
                        }

                        else -> {
                            onError(
                                labels.failedTitle,
                                labels.failedExplainerWithError.format(errString.toString()),
                            )
                        }
                    }
                }

                // One unrecognised finger: the prompt stays open and says so itself. Reporting it as
                // an error would end the caller's wait, and the next good touch would go unheard.
                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onApproved()
                }
            },
        )

    when (biometricManager.canAuthenticate(authenticators)) {
        BiometricManager.BIOMETRIC_SUCCESS -> biometricPrompt.authenticate(promptInfo)
        else -> keyguardPrompt()
    }
}
