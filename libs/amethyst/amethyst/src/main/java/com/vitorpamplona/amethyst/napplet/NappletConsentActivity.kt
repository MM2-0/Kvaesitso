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
package com.vitorpamplona.amethyst.napplet

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.napplet.NappletConsentInfo
import com.vitorpamplona.amethyst.commons.napplet.permissions.GrantState
import com.vitorpamplona.amethyst.commons.napplet.ui.NappletConsentCard
import com.vitorpamplona.amethyst.commons.ui.connectedApps.consent.ConsentInputGuard
import com.vitorpamplona.amethyst.ui.theme.AmethystTheme

/**
 * The capability-consent dialog, shown in the **main** process (the only place trusted to
 * make a grant). It renders the [NappletConsentInfo] for a pending request and reports the
 * user's [GrantState] back through [NappletConsentCoordinator]. The untrusted applet never
 * sees or drives this UI.
 */
class NappletConsentActivity : ComponentActivity() {
    // The question on screen: dismissed if the activity is finished before the user answers it.
    private var shown: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // The oldest question waiting, whichever request brought the activity up: a second one
            // arriving while this shows is answered next instead of lost, and a question that timed out
            // leaves with the queue.
            val pending by NappletConsentCoordinator.pending.collectAsStateWithLifecycle()
            val request = pending.firstOrNull()
            LaunchedEffect(request == null) { if (request == null) finish() }
            if (request != null) {
                SideEffect { shown = request.token }
                key(request.token) {
                    AmethystTheme {
                        NappletConsentDialog(
                            info = request.info,
                            onDecision = { grant -> NappletConsentCoordinator.complete(request.token, grant) },
                            onDismiss = { NappletConsentCoordinator.cancel(request.token) },
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        // Torn down before the user chose: fail closed. A rotation keeps the question.
        if (isFinishing) shown?.let(NappletConsentCoordinator::cancel)
        super.onDestroy()
    }
}

@Composable
private fun NappletConsentDialog(
    info: NappletConsentInfo,
    onDecision: (GrantState) -> Unit,
    onDismiss: () -> Unit,
) {
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.85f

    Dialog(
        onDismissRequest = onDismiss,
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
            ConsentInputGuard { NappletConsentCard(info, onDecision) }
        }
    }
}
