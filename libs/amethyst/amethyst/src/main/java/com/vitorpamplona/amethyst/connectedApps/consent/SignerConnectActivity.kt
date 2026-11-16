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
package com.vitorpamplona.amethyst.connectedApps.consent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.connectedApps.consent.SignerConnectInfo
import com.vitorpamplona.amethyst.commons.connectedApps.signers.AppConnectResult
import com.vitorpamplona.amethyst.commons.connectedApps.signers.AppSignerPolicy
import com.vitorpamplona.amethyst.commons.ui.connectedApps.consent.ConsentInputGuard
import com.vitorpamplona.amethyst.commons.ui.connectedApps.consent.SignerConnectCard
import com.vitorpamplona.amethyst.ui.theme.AmethystTheme

class SignerConnectActivity : ComponentActivity() {
    // The request on screen: cancelled if the activity is finished before the user answers it.
    private var shown: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // The oldest request waiting, whichever one brought the activity up: one arriving while
            // this shows (a NIP-46 client while a site asks) is answered next instead of lost.
            val pending by SignerConnectCoordinator.pending.collectAsStateWithLifecycle()
            val request = pending.firstOrNull()
            LaunchedEffect(request == null) { if (request == null) finish() }
            if (request != null) {
                SideEffect { shown = request.token }
                key(request.token) {
                    // The account the app asks to connect to, which need not be the logged-in one.
                    AmethystTheme(accountPubKey = request.info.accountPubKey) {
                        SignerConnectScreen(
                            info = request.info,
                            onConnect = { policy -> SignerConnectCoordinator.complete(request.token, AppConnectResult.Connected(policy)) },
                            onBlock = { SignerConnectCoordinator.complete(request.token, AppConnectResult.Blocked) },
                            onCancel = { SignerConnectCoordinator.complete(request.token, AppConnectResult.Cancelled) },
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        // Torn down before the user chose: fail closed. A rotation keeps the request.
        if (isFinishing) shown?.let(SignerConnectCoordinator::cancel)
        super.onDestroy()
    }
}

@Composable
private fun SignerConnectScreen(
    info: SignerConnectInfo,
    onConnect: (AppSignerPolicy) -> Unit,
    onBlock: () -> Unit,
    onCancel: () -> Unit,
) {
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.9f

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .heightIn(max = maxHeight),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            ConsentInputGuard { SignerConnectCard(info, onConnect, onBlock, onCancel) }
        }
    }
}
