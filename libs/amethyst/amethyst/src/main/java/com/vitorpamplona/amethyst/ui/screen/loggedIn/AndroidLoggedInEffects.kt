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
package com.vitorpamplona.amethyst.ui.screen.loggedIn

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.commons.model.innermostSigner
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.error_opening_external_signer
import com.vitorpamplona.amethyst.commons.resources.error_opening_external_signer_description
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.service.notifications.PushNotificationUtils
import com.vitorpamplona.quartz.nip55AndroidSigner.client.IActivityLauncher
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** What only Android does for the logged-in account: push registration and the NIP-55 signer app's launcher. */
@Composable
fun AndroidLoggedInEffects(accountViewModel: AccountViewModel) {
    // Listens to Amber
    ListenToExternalSignerIfNeeded(accountViewModel)

    // Register token with the Push Notification Provider.
    NotificationRegistration(accountViewModel)
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun NotificationRegistration(accountViewModel: AccountViewModel) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val scope = rememberCoroutineScope()

        val notificationPermissionState = rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)

        if (notificationPermissionState.status.isGranted) {
            LifecycleResumeEffect(key1 = accountViewModel, notificationPermissionState.status.isGranted) {
                Log.d("RegisterAccounts", "Registering for push notifications")
                scope.launch(Dispatchers.IO) {
                    PushNotificationUtils.checkAndInit(
                        LocalPreferences.allSavedAccounts(),
                        accountViewModel.httpClientBuilder::okHttpClientForPushRegistration,
                    )
                }

                onPauseOrDispose {}
            }
        }
    } else {
        val scope = rememberCoroutineScope()
        // no need for push permissions before 33
        LifecycleResumeEffect(key1 = accountViewModel) {
            Log.d("RegisterAccounts", "Registering for push notifications")
            scope.launch(Dispatchers.IO) {
                PushNotificationUtils.checkAndInit(
                    LocalPreferences.allSavedAccounts(),
                    accountViewModel.httpClientBuilder::okHttpClientForPushRegistration,
                )
            }

            onPauseOrDispose {}
        }
    }
}

@Composable
private fun ListenToExternalSignerIfNeeded(accountViewModel: AccountViewModel) {
    val externalSignerLauncher = accountViewModel.account.signer.innermostSigner() as? IActivityLauncher

    if (externalSignerLauncher != null) {
        val launcher =
            rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult(),
                onResult = { result ->
                    if (result.resultCode == Activity.RESULT_OK) {
                        result.data?.let {
                            accountViewModel.runOnIO {
                                externalSignerLauncher.newResponse(it)
                            }
                        }
                    }
                },
            )

        DisposableEffect(accountViewModel, accountViewModel.account, launcher) {
            val intentLauncher: (Intent) -> Unit = { intent ->
                try {
                    launcher.launch(intent)
                } catch (e: ActivityNotFoundException) {
                    accountViewModel.toastManager.toast(
                        Res.string.error_opening_external_signer,
                        Res.string.error_opening_external_signer_description,
                    )
                    throw e
                }
            }

            externalSignerLauncher.registerForegroundLauncher(intentLauncher)
            onDispose {
                externalSignerLauncher.unregisterForegroundLauncher(intentLauncher)
            }
        }
    }
}
