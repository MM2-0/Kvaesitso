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
package com.vitorpamplona.amethyst.ui.tor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.tor_connection_failed_body
import com.vitorpamplona.amethyst.commons.resources.tor_connection_failed_title
import com.vitorpamplona.amethyst.commons.resources.tor_continue_without_for_session
import com.vitorpamplona.amethyst.commons.resources.tor_keep_waiting
import com.vitorpamplona.amethyst.commons.resources.tor_restart
import com.vitorpamplona.amethyst.commons.ui.stringRes

/**
 * Sticky alert that appears when Tor has been stuck connecting longer than
 * [TorManager.BOOTSTRAP_TIMEOUT_MS]. The user can:
 *
 *  - Use a regular (non-Tor) connection for the rest of this session, with the choice
 *    remembered for [TorManager.APPROVAL_REMEMBER_MS] so subsequent failures within the
 *    window auto-fall-back without re-prompting.
 *  - Restart Tor — drops the stuck client (see [TorManager.restartTor]) and starts a fresh
 *    attempt, which gets its own full timeout before the dialog can return.
 *  - Keep waiting — the dialog hides until the next connecting span ends and a fresh
 *    timeout fires.
 */
@Composable
fun TorConnectionFailureDialog(torManager: TorManager) {
    val failure by torManager.connectionFailure.collectAsStateWithLifecycle()
    val bypass by torManager.sessionBypass.collectAsStateWithLifecycle()

    var dismissedForCurrentSpan by remember { mutableStateOf(false) }

    // Reset the per-span dismissal whenever the failure flag goes back to false (a new
    // Connecting span starts). That way "Keep waiting" only suppresses the current span.
    if (!failure && dismissedForCurrentSpan) {
        dismissedForCurrentSpan = false
    }

    if (!failure || bypass || dismissedForCurrentSpan) return

    AlertDialog(
        onDismissRequest = { /* sticky — user must pick a button */ },
        title = { Text(stringRes(Res.string.tor_connection_failed_title)) },
        text = { Text(stringRes(Res.string.tor_connection_failed_body)) },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { torManager.approveBypassForOneHour() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringRes(Res.string.tor_continue_without_for_session))
                }
                OutlinedButton(
                    onClick = { torManager.restartTor() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringRes(Res.string.tor_restart))
                }
                OutlinedButton(
                    onClick = { dismissedForCurrentSpan = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringRes(Res.string.tor_keep_waiting))
                }
            }
        },
    )
}
