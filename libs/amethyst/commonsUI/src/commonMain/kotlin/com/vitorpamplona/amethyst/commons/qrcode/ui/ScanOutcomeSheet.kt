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
package com.vitorpamplona.amethyst.commons.qrcode.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.qrcode.ScannedPayload
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.qr_scanner_copy
import com.vitorpamplona.amethyst.commons.resources.qr_scanner_kind_bunker
import com.vitorpamplona.amethyst.commons.resources.qr_scanner_kind_cashu
import com.vitorpamplona.amethyst.commons.resources.qr_scanner_kind_hex_key
import com.vitorpamplona.amethyst.commons.resources.qr_scanner_kind_lightning
import com.vitorpamplona.amethyst.commons.resources.qr_scanner_kind_nostr_unsupported
import com.vitorpamplona.amethyst.commons.resources.qr_scanner_kind_nsec
import com.vitorpamplona.amethyst.commons.resources.qr_scanner_kind_signer
import com.vitorpamplona.amethyst.commons.resources.qr_scanner_kind_text
import com.vitorpamplona.amethyst.commons.resources.qr_scanner_kind_wallet
import com.vitorpamplona.amethyst.commons.resources.qr_scanner_kind_web
import com.vitorpamplona.amethyst.commons.resources.qr_scanner_open_as_profile
import com.vitorpamplona.amethyst.commons.resources.qr_scanner_open_link
import com.vitorpamplona.amethyst.commons.resources.qr_scanner_try_again
import com.vitorpamplona.amethyst.commons.resources.qr_scanner_unsupported_secret
import com.vitorpamplona.amethyst.commons.resources.qr_scanner_unsupported_title
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.quartz.nip19Bech32.entities.NSec

/**
 * Explains a code we read but could not act on.
 *
 * This sheet is the whole point of classifying payloads. The old scanner collapsed "you
 * cancelled", "the camera never read anything" and "that scanned perfectly but Amethyst has no
 * screen for it" into the same silent return to the previous screen (issue #417), which trains
 * people to believe the reader is broken when it is working exactly as designed.
 *
 * [onOpenProfile] receives the npub of a [ScannedPayload.HexKey] once the user confirms it is a
 * public key. A raw hex key is never opened without that confirmation, because a private key
 * looks exactly the same and opening it as a profile would send it to relays.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanOutcomeSheet(
    payload: ScannedPayload,
    onDismiss: () -> Unit,
    onOpenLink: (String) -> Unit,
    onCopy: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
) {
    val sheetState =
        rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        )

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringRes(Res.string.qr_scanner_unsupported_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = explain(payload),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // A QR code is scanned in public. Key material and pairing secrets never get echoed
            // back onto the screen, no matter how useful it would be for debugging.
            if (!payload.containsSecret) {
                Text(
                    text = payload.raw,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (payload is ScannedPayload.HexKey) {
                Button(onClick = { onOpenProfile(payload.npub) }) {
                    Text(stringRes(Res.string.qr_scanner_open_as_profile))
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (payload is ScannedPayload.Web) {
                    Button(onClick = { onOpenLink(payload.url) }) {
                        Text(stringRes(Res.string.qr_scanner_open_link))
                    }
                }
                if (!payload.containsSecret) {
                    TextButton(onClick = { onCopy(payload.raw) }) {
                        Text(stringRes(Res.string.qr_scanner_copy))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringRes(Res.string.qr_scanner_try_again))
                }
            }
        }
    }
}

@Composable
private fun explain(payload: ScannedPayload): String {
    val kind =
        when (payload) {
            is ScannedPayload.Nostr ->
                if (payload.entity is NSec) {
                    stringRes(Res.string.qr_scanner_kind_nsec)
                } else {
                    stringRes(Res.string.qr_scanner_kind_nostr_unsupported)
                }

            is ScannedPayload.WalletConnect -> stringRes(Res.string.qr_scanner_kind_wallet)
            // Split deliberately: Amethyst PUBLISHES bunker:// addresses (it is the signer) and has no
            // screen that consumes one, so pointing the user at the signer screen -- as this used to --
            // sends them somewhere that cannot accept it.
            is ScannedPayload.Bunker -> stringRes(Res.string.qr_scanner_kind_bunker)
            is ScannedPayload.NostrConnect -> stringRes(Res.string.qr_scanner_kind_signer)
            is ScannedPayload.PrivateKey -> stringRes(Res.string.qr_scanner_kind_nsec)
            is ScannedPayload.Lightning -> stringRes(Res.string.qr_scanner_kind_lightning)
            is ScannedPayload.Cashu -> stringRes(Res.string.qr_scanner_kind_cashu)
            is ScannedPayload.Web -> stringRes(Res.string.qr_scanner_kind_web)
            is ScannedPayload.HexKey -> stringRes(Res.string.qr_scanner_kind_hex_key)
            is ScannedPayload.Unknown -> stringRes(Res.string.qr_scanner_kind_text)
        }

    // A hex key explains its own hiding: the generic "carries a private key or a pairing secret"
    // line would claim to know which kind of key it is.
    val explainSecret = payload.containsSecret && payload !is ScannedPayload.HexKey
    return if (explainSecret) kind + "\n\n" + stringRes(Res.string.qr_scanner_unsupported_secret) else kind
}
