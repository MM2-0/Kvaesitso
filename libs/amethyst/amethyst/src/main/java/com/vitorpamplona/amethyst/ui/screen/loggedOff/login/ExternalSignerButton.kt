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
package com.vitorpamplona.amethyst.ui.screen.loggedOff.login

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import coil3.compose.rememberAsyncImagePainter
import com.vitorpamplona.amethyst.commons.account.ui.login.LoginViewModel
import com.vitorpamplona.amethyst.commons.account.ui.login.SignerAppLoginButton
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.error_opening_external_signer
import com.vitorpamplona.amethyst.commons.resources.select_signer
import com.vitorpamplona.amethyst.commons.resources.sign_request_rejected2
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent
import com.vitorpamplona.quartz.nip42RelayAuth.RelayAuthEvent
import com.vitorpamplona.quartz.nip55AndroidSigner.api.CommandType
import com.vitorpamplona.quartz.nip55AndroidSigner.api.PubKeyResult
import com.vitorpamplona.quartz.nip55AndroidSigner.api.SignerResult
import com.vitorpamplona.quartz.nip55AndroidSigner.api.permission.Permission
import com.vitorpamplona.quartz.nip55AndroidSigner.client.ExternalSignerLogin
import com.vitorpamplona.quartz.nip55AndroidSigner.client.getExternalSignersInstalled
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun ExternalSignerButton(loginViewModel: LoginViewModel) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val installedSigners = remember(context) { getExternalSignersInstalled(context) }
    var shouldSelectSigner by remember { mutableStateOf(false) }

    val launcher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            scope.launch {
                val resultData = result.data
                if (result.resultCode == Activity.RESULT_OK && resultData != null) {
                    val loginInfo = ExternalSignerLogin.parseResult(resultData)
                    if (loginInfo is SignerResult.RequestAddressed.Successful<PubKeyResult>) {
                        loginViewModel.updateKey(TextFieldValue(loginInfo.result.pubkey), false)
                        loginViewModel.loginWithExternalSigner(loginInfo.result.packageName)
                    }
                } else {
                    loginViewModel.signerErrorManager.error(Res.string.sign_request_rejected2)
                }
            }
        }

    if (shouldSelectSigner) {
        Dialog(
            onDismissRequest = {
                shouldSelectSigner = false
            },
            content = {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            modifier = Modifier.padding(8.dp),
                            text = stringRes(Res.string.select_signer),
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        LazyColumn {
                            items(installedSigners) {
                                val appName =
                                    remember(it) {
                                        it.activityInfo.applicationInfo
                                            .loadLabel(context.packageManager)
                                            .toString()
                                    }
                                // A fixed size: an icon with no intrinsic size (-1) throws without one.
                                val iconBitmap = remember(it) { it.loadIcon(context.packageManager).toBitmap(SIGNER_ICON_PX, SIGNER_ICON_PX) }

                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp, end = 16.dp, bottom = 8.dp, top = 8.dp)
                                        .clickable {
                                            if (!loginViewModel.acceptedTerms) {
                                                loginViewModel.termsAcceptanceIsRequiredError = true
                                            } else {
                                                try {
                                                    launcher.launch(ExternalSignerLogin.createIntent(DefaultSignerPermissions, it.activityInfo.packageName))
                                                } catch (e: Exception) {
                                                    if (e is CancellationException) throw e
                                                    Log.e("ExternalSigner", "Error opening Signer app", e)
                                                    loginViewModel.signerErrorManager.error(Res.string.error_opening_external_signer)
                                                } finally {
                                                    shouldSelectSigner = false
                                                }
                                            }
                                        },
                                ) {
                                    val painter =
                                        rememberAsyncImagePainter(
                                            iconBitmap,
                                        )

                                    Image(
                                        painter = painter,
                                        contentDescription = appName,
                                        modifier =
                                            Modifier
                                                .size(48.dp)
                                                .padding(end = 16.dp),
                                    )
                                    Column {
                                        Text(appName)
                                        Text(
                                            it.activityInfo.packageName,
                                            fontSize = 14.sp,
                                            color = Color.Gray,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
        )
    }

    // The one signer installed is named and drawn as itself; several share a generic label and
    // the chooser above.
    val onlySigner = installedSigners.singleOrNull()
    // The app's name: a signer may label its signing activity something else ("Sign request").
    val onlySignerName =
        remember(onlySigner) {
            onlySigner
                ?.activityInfo
                ?.applicationInfo
                ?.loadLabel(context.packageManager)
                ?.toString()
        }
    val onlySignerIcon =
        remember(onlySigner) {
            onlySigner?.loadIcon(context.packageManager)?.toBitmap(SIGNER_ICON_PX, SIGNER_ICON_PX)?.asImageBitmap()
        }

    SignerAppLoginButton(
        appName = onlySignerName,
        appIcon = onlySignerIcon?.let { icon -> { Image(icon, contentDescription = null, modifier = Modifier.size(24.dp)) } },
        enabled = loginViewModel.acceptedTerms,
        onClick = {
            if (!loginViewModel.acceptedTerms) {
                loginViewModel.termsAcceptanceIsRequiredError = true
            } else {
                try {
                    if (installedSigners.size == 1) {
                        launcher.launch(ExternalSignerLogin.createIntent(DefaultSignerPermissions))
                    } else {
                        shouldSelectSigner = true
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e("ExternalSigner", "Error opening Signer app", e)
                    loginViewModel.signerErrorManager.error(Res.string.error_opening_external_signer)
                }
            }
        },
    )
}

// The signer's launcher icon, at the button's 24dp on the densest screens.
private const val SIGNER_ICON_PX = 96

/** What Amethyst asks a NIP-55 signer app to pre-approve when the user logs in with it. */
val DefaultSignerPermissions =
    listOf(
        Permission(CommandType.SIGN_EVENT, RelayAuthEvent.KIND),
        Permission(CommandType.SIGN_EVENT, DraftWrapEvent.KIND),
        Permission(CommandType.NIP04_ENCRYPT),
        Permission(CommandType.NIP04_DECRYPT),
        Permission(CommandType.NIP44_DECRYPT),
        Permission(CommandType.NIP44_DECRYPT),
        Permission(CommandType.DECRYPT_ZAP_EVENT),
    )
