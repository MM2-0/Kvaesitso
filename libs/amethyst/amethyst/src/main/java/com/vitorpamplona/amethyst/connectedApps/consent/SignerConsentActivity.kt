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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.connectedApps.consent.PendingConsent
import com.vitorpamplona.amethyst.commons.connectedApps.consent.SignerConsentInfo
import com.vitorpamplona.amethyst.commons.connectedApps.consent.oldestAccountGroup
import com.vitorpamplona.amethyst.commons.connectedApps.signers.SignerOpGrant
import com.vitorpamplona.amethyst.commons.service.call.CallSessionBridge
import com.vitorpamplona.amethyst.commons.ui.connectedApps.consent.BatchedConsentCard
import com.vitorpamplona.amethyst.commons.ui.connectedApps.consent.ConsentInputGuard
import com.vitorpamplona.amethyst.commons.ui.connectedApps.consent.SignerConsentCard
import com.vitorpamplona.amethyst.ui.theme.AmethystTheme

class SignerConsentActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // The signer services requests concurrently, so more than one may await consent. We never
            // mix accounts in one sheet: render only the requests for the OLDEST-pending account as a
            // group. When that account's group clears, the next account's requests render (a fresh
            // per-account sheet). One request → the rich dialog; several → a batched list. When the
            // whole queue empties, close.
            val pending by SignerConsentCoordinator.pending.collectAsStateWithLifecycle()
            val group = pending.oldestAccountGroup()
            val groupAccount = group.firstOrNull()?.info?.accountPubKey

            // The sheet wears the color of the account it signs for, which need not be the logged-in one.
            AmethystTheme(accountPubKey = groupAccount) {
                LaunchedEffect(pending.isEmpty()) { if (pending.isEmpty()) finish() }
                when {
                    group.isEmpty() -> Unit
                    group.size == 1 -> {
                        val p = group.first()
                        // Keyed by the request: when one times out and the next takes its place, a tap
                        // already on its way can't land on a dialog it was never meant for.
                        key(p.token) {
                            SignerConsentDialog(
                                info = p.info,
                                onGrant = { SignerConsentCoordinator.complete(p.token, it) },
                                onDismiss = { SignerConsentCoordinator.complete(p.token, SignerOpGrant.DenyOnce) },
                            )
                        }
                    }
                    else ->
                        BatchedConsentDialog(
                            pending = group,
                            onResolve = { tokens, grant -> SignerConsentCoordinator.completeAll(tokens, grant) },
                            // Dismissing denies only THIS account's group; other accounts' requests stay
                            // pending and render next as their own sheet.
                            onDismiss = { SignerConsentCoordinator.completeAll(group.map { it.token }, SignerOpGrant.DenyOnce) },
                        )
                }
            }
        }
    }
    // Dismissal is failed-closed at the source: each dialog's onDismissRequest (back / tap-outside)
    // denies its own request(s). We deliberately do NOT deny-all in onDestroy — a request arriving as
    // this Activity finishes is owned by a freshly-launched instance, and denying it here would race
    // that instance and reject a legitimate request. A process kill falls back to the bridge's 120s
    // timeout, which also fails closed.
}

/**
 * Hosts a consent card in a dialog. Only Back and the buttons decide: a stray tap beside the sheet
 * must not deny the request.
 */
@Composable
private fun ConsentSheet(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.85f
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
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
            ConsentInputGuard(content)
        }
    }
}

@Composable
private fun SignerConsentDialog(
    info: SignerConsentInfo,
    onGrant: (SignerOpGrant) -> Unit,
    onDismiss: () -> Unit,
) {
    // Best-effort note preview: if the main Activity is gone (only the foreground signer service
    // alive) the card shows the event JSON instead.
    val accountViewModel = remember { CallSessionBridge.accountViewModel }
    ConsentSheet(onDismiss) { SignerConsentCard(info, accountViewModel, onGrant) }
}

@Composable
private fun BatchedConsentDialog(
    pending: List<PendingConsent>,
    onResolve: (tokens: List<String>, grant: SignerOpGrant) -> Unit,
    onDismiss: () -> Unit,
) {
    val accountViewModel = remember { CallSessionBridge.accountViewModel }
    ConsentSheet(onDismiss) { BatchedConsentCard(pending, accountViewModel, onResolve) }
}
