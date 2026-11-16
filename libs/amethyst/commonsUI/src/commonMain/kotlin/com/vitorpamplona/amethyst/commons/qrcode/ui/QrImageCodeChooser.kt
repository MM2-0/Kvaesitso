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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.qrcode.ScannedPayload
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.qr_scanner_label_bunker
import com.vitorpamplona.amethyst.commons.resources.qr_scanner_label_cashu
import com.vitorpamplona.amethyst.commons.resources.qr_scanner_label_hex_key
import com.vitorpamplona.amethyst.commons.resources.qr_scanner_label_lightning
import com.vitorpamplona.amethyst.commons.resources.qr_scanner_label_nostr
import com.vitorpamplona.amethyst.commons.resources.qr_scanner_label_note
import com.vitorpamplona.amethyst.commons.resources.qr_scanner_label_nsec
import com.vitorpamplona.amethyst.commons.resources.qr_scanner_label_profile
import com.vitorpamplona.amethyst.commons.resources.qr_scanner_label_relay
import com.vitorpamplona.amethyst.commons.resources.qr_scanner_label_signer
import com.vitorpamplona.amethyst.commons.resources.qr_scanner_label_text
import com.vitorpamplona.amethyst.commons.resources.qr_scanner_label_wallet
import com.vitorpamplona.amethyst.commons.resources.qr_scanner_label_web
import com.vitorpamplona.amethyst.commons.resources.qr_scanner_pick_one
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEmbed
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip19Bech32.entities.NRelay
import com.vitorpamplona.quartz.nip19Bech32.entities.NSec

/**
 * Offers the codes found in one picture and lets the reader say which one they meant.
 *
 * The camera already does this: two codes in frame draw their outlines and wait for a tap,
 * because silently taking one of them is how you scan the poster next to the one you wanted. A
 * picture had no such moment — every image path took `firstOrNull()`, so a screenshot of a
 * profile card that also carries a Lightning QR, or a photo of a noticeboard, resolved to
 * whichever code the decoder happened to report first and gave no sign the others existed.
 *
 * There is no image on screen to tap, so the codes are listed by what they are rather than by
 * where they sit. A row that carries a secret shows its kind and nothing else — the same rule
 * the outcome sheet follows, for the same reason: a QR code is scanned in public.
 *
 * [note] is a line under the title for anything else the picture held that cannot be offered as
 * a row, such as part of a multi-part code.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrImageCodeChooser(
    codes: List<ScannedPayload>,
    onPick: (ScannedPayload) -> Unit,
    onDismiss: () -> Unit,
    note: String? = null,
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
                text = stringRes(Res.string.qr_scanner_pick_one),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            if (note != null) {
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            codes.forEachIndexed { index, payload ->
                if (index > 0) HorizontalDivider()

                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(payload) }
                            .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = codeLabel(payload),
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    if (!payload.containsSecret) {
                        Text(
                            text = payload.raw,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/**
 * A short noun for one code in the list.
 *
 * Deliberately not [ScanOutcomeSheet]'s wording: that sheet only ever appears for a payload
 * nothing can open, so every one of its lines ends in an apology ("not one this screen can
 * open", "paste it on the login screen instead"). Most codes offered here are perfectly
 * openable, and the reader is picking between them, not being told off.
 */
@Composable
private fun codeLabel(payload: ScannedPayload): String =
    when (payload) {
        is ScannedPayload.Nostr ->
            when (payload.entity) {
                is NPub, is NProfile -> stringRes(Res.string.qr_scanner_label_profile)
                is NNote, is NEvent, is NAddress, is NEmbed -> stringRes(Res.string.qr_scanner_label_note)
                is NRelay -> stringRes(Res.string.qr_scanner_label_relay)
                is NSec -> stringRes(Res.string.qr_scanner_label_nsec)
                else -> stringRes(Res.string.qr_scanner_label_nostr)
            }

        is ScannedPayload.HexKey -> stringRes(Res.string.qr_scanner_label_hex_key)
        is ScannedPayload.PrivateKey -> stringRes(Res.string.qr_scanner_label_nsec)
        is ScannedPayload.WalletConnect -> stringRes(Res.string.qr_scanner_label_wallet)
        is ScannedPayload.Bunker -> stringRes(Res.string.qr_scanner_label_bunker)
        is ScannedPayload.NostrConnect -> stringRes(Res.string.qr_scanner_label_signer)
        is ScannedPayload.Lightning -> stringRes(Res.string.qr_scanner_label_lightning)
        is ScannedPayload.Cashu -> stringRes(Res.string.qr_scanner_label_cashu)
        is ScannedPayload.Web -> stringRes(Res.string.qr_scanner_label_web)
        is ScannedPayload.Unknown -> stringRes(Res.string.qr_scanner_label_text)
    }
