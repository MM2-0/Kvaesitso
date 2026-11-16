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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.qrcode

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.qrcode.ImageCodesOutcome
import com.vitorpamplona.amethyst.commons.qrcode.ScannedPayload
import com.vitorpamplona.amethyst.commons.qrcode.classifyScannedPayload
import com.vitorpamplona.amethyst.commons.qrcode.ui.QrImageCodeChooser
import com.vitorpamplona.amethyst.commons.qrcode.ui.ScanOutcomeSheet
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.qr_scanner_no_code_in_image
import com.vitorpamplona.amethyst.commons.resources.qr_scanner_try_again
import com.vitorpamplona.amethyst.commons.resources.qr_scanner_unavailable
import com.vitorpamplona.amethyst.commons.resources.scan_qr
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.qrcode.routeForScannedPayload
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.qrcode.scanner.QrImageImport
import com.vitorpamplona.amethyst.ui.screen.loggedIn.qrcode.scanner.ZxingCppBarcodeDecoder
import com.vitorpamplona.amethyst.ui.screen.loggedIn.qrcode.scanner.copyToClipboard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What happened to the image the user shared in. */
private sealed interface ImageScanState {
    data object Working : ImageScanState

    /** Decoded, but nothing here can open it — the outcome sheet explains which. */
    data class Unsupported(
        val payload: ScannedPayload,
    ) : ImageScanState

    /** No QR code in the picture at all, or the decoder could not start. */
    data class Failed(
        val message: String,
    ) : ImageScanState

    /**
     * The picture held more than one code, or one next to part of a multi-part code, so the
     * reader says which one they meant. [note] mentions the partial code.
     */
    data class Choosing(
        val codes: List<ScannedPayload>,
        val note: String?,
    ) : ImageScanState
}

/**
 * Reads a QR code out of a picture the user shared into Amethyst, and goes where it points.
 *
 * The share sheet is how most QR codes actually arrive — a screenshot, or a photo someone sent in
 * a chat. Before this the only way to use one was to put it on a second screen and photograph it.
 *
 * Failure is explicit here for the same reason it is in the live scanner: "that picture has no QR
 * code in it" and "that code is something Amethyst can't open" are different problems, and
 * collapsing them into a silent bounce is what made the old reader feel broken.
 */
@Composable
fun ScanQrImageScreen(
    uri: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val decoder = remember { runCatching { ZxingCppBarcodeDecoder() }.getOrNull() }
    var state by remember { mutableStateOf<ImageScanState>(ImageScanState.Working) }

    val noCodeFound = stringRes(Res.string.qr_scanner_no_code_in_image)
    val decoderUnavailable = stringRes(Res.string.qr_scanner_unavailable)
    val context = LocalContext.current

    // Navigating or explaining, for one chosen code. Shared by the single-code path and the
    // chooser, so both treat a hex key and an unsupported payload the same way.
    val resolve: (ScannedPayload) -> Unit = { payload ->
        // Never opens a bare hex key directly (it may be a private key): the outcome sheet asks,
        // and comes back here with the npub.
        val route = runCatching { routeForScannedPayload(payload, accountViewModel.account) }.getOrNull()

        if (route != null) {
            nav.newStack(route)
        } else {
            state = ImageScanState.Unsupported(payload)
        }
    }

    LaunchedEffect(uri, decoder) {
        if (decoder == null) {
            state = ImageScanState.Failed(decoderUnavailable)
            return@LaunchedEffect
        }

        val outcome = withContext(Dispatchers.IO) { QrImageImport.outcome(context, uri.toUri(), decoder) }

        when (outcome) {
            ImageCodesOutcome.NothingFound -> state = ImageScanState.Failed(noCodeFound)
            is ImageCodesOutcome.OnlyPartial -> state = ImageScanState.Failed(QrImageImport.partialProgressText(outcome.captured, outcome.total))
            // One code alone is unambiguous, so asking would only add a tap.
            is ImageCodesOutcome.Open -> resolve(classifyScannedPayload(outcome.text))
            is ImageCodesOutcome.Choose ->
                state =
                    ImageScanState.Choosing(
                        codes = outcome.texts.map(::classifyScannedPayload),
                        note = QrImageImport.partialNote(outcome.partial),
                    )
        }
    }

    Scaffold(topBar = { TopBarWithBackButton(stringRes(Res.string.scan_qr), nav) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (val current = state) {
                is ImageScanState.Working -> CircularProgressIndicator()

                is ImageScanState.Failed -> {
                    Text(
                        text = current.message,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    // The camera is the obvious next move when the picture turned out to hold
                    // nothing, so offer it rather than leaving a dead end.
                    Button(onClick = { nav.newStack(Route.QRDisplay(accountViewModel.userProfile().pubkeyHex, startScanning = true)) }) {
                        Text(stringRes(Res.string.scan_qr))
                    }
                    TextButton(onClick = { nav.popBack() }) {
                        Text(stringRes(Res.string.qr_scanner_try_again))
                    }
                }

                is ImageScanState.Unsupported, is ImageScanState.Choosing -> Unit
            }
        }
    }

    (state as? ImageScanState.Choosing)?.let { choosing ->
        QrImageCodeChooser(
            codes = choosing.codes,
            note = choosing.note,
            onPick = { picked ->
                state = ImageScanState.Working
                resolve(picked)
            },
            onDismiss = { nav.popBack() },
        )
    }

    (state as? ImageScanState.Unsupported)?.let { unsupported ->
        ScanOutcomeSheet(
            payload = unsupported.payload,
            onDismiss = { nav.popBack() },
            onOpenLink = { url ->
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
                nav.popBack()
            },
            onCopy = { text ->
                copyToClipboard(context, text)
                nav.popBack()
            },
            onOpenProfile = { npub ->
                state = ImageScanState.Working
                resolve(classifyScannedPayload(npub))
            },
        )
    }
}
