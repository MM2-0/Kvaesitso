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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.qrcode

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.navigation.uri.uriToRoute
import com.vitorpamplona.amethyst.commons.qrcode.ScannedPayload
import com.vitorpamplona.amethyst.commons.qrcode.classifyScannedPayload
import com.vitorpamplona.amethyst.commons.ui.platform.QrCodeScannerDialog
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.qrcode.ScanOutcome
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException

/**
 * Scans a QR code and navigates wherever it points.
 *
 * A payload we decode but cannot route no longer closes the scanner: it stays open and explains
 * itself (see `ScanOutcomeSheet`), because "that is a Lightning invoice, not a profile" and "the
 * camera never read anything" used to look identical from the outside.
 *
 * [onScan] is called exactly once: with the route, or with `null` when the user backed out.
 */
@Composable
fun NIP19QrCodeScanner(
    accountViewModel: AccountViewModel,
    onScan: (Route?) -> Unit,
) {
    // The dialog closes itself after a handled scan by calling onDismiss, which would otherwise
    // report "the user backed out" right after the result it just delivered.
    var delivered by remember { mutableStateOf(false) }

    QrCodeScannerDialog(
        onDismiss = { if (!delivered) onScan(null) },
        onScan = { contents ->
            val route = routeFor(contents, accountViewModel)
            if (route != null) {
                delivered = true
                onScan(route)
                ScanOutcome.Handled
            } else {
                ScanOutcome.NotSupported
            }
        },
    )
}

/**
 * The route a classified scan opens, or null when nothing here can open it. The one place every
 * scan entry point (camera, picked image, shared image) decides this, so none can skip the rule
 * below.
 *
 * A bare 64-character hex key is deliberately NOT routed. It could as easily be a private key as
 * a public one, and opening a private key as a profile sends it to relays as an author filter. It
 * returns null, so the outcome sheet asks, and the npub comes back through here only once the
 * user says it is a public key (issue #417 is still served, one tap later).
 *
 * Routes the trimmed payload: a code ending in a newline classified fine but then failed to
 * parse, which read as "can't open this" for a perfectly good wallet-connect URI.
 */
fun routeForScannedPayload(
    payload: ScannedPayload,
    account: Account,
): Route? = if (payload is ScannedPayload.HexKey) null else uriToRoute(payload.raw, account)

/** [routeForScannedPayload] for a raw scan, never throwing and never logging the payload. */
private fun routeFor(
    contents: String,
    accountViewModel: AccountViewModel,
): Route? =
    try {
        routeForScannedPayload(classifyScannedPayload(contents), accountViewModel.account)
    } catch (e: Throwable) {
        if (e is CancellationException) throw e
        // The payload itself never reaches the log. A QR code is as likely to hold an nsec, a
        // wallet-connect secret or a Cashu token as a profile link, and logcat is readable over
        // adb and swept up by device bug reports — the same material ScannedPayload.containsSecret
        // exists to keep off the screen two files away. The classification and the length say
        // enough to debug a routing failure; classifying again here is wrapped because this is
        // the branch for a payload that already made something throw.
        val kind = runCatching { classifyScannedPayload(contents)::class.simpleName }.getOrNull() ?: "unclassifiable"
        Log.e("NIP19 Scanner", "Could not route a scanned $kind payload of ${contents.length} chars", e)
        // A QR code can hold anything at all. Never let one throw.
        null
    }

/**
 * Scans a QR code and hands back whatever it says.
 *
 * For callers that do their own validation — a wallet-connect URI, a `bunker://` offer, a key on
 * the login screen. `null` means the user backed out. Called exactly once: before this latch, a
 * scan delivered the code and then `null`, and a caller that launched work for each (the
 * geocache log) ran the "nothing scanned" path concurrently with the real one.
 */
@Composable
fun SimpleQrCodeScanner(onScan: (String?) -> Unit) {
    var delivered by remember { mutableStateOf(false) }

    QrCodeScannerDialog(
        onDismiss = { if (!delivered) onScan(null) },
        onScan = { contents ->
            delivered = true
            onScan(contents)
            ScanOutcome.Handled
        },
    )
}
